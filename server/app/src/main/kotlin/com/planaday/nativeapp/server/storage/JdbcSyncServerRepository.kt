package com.planaday.nativeapp.server.storage

import com.planaday.nativeapp.core.model.CanonicalRecord
import com.planaday.nativeapp.core.model.RecordKind
import com.planaday.nativeapp.core.model.WorkspaceRole
import com.planaday.nativeapp.core.security.CryptoPrimitives
import com.planaday.nativeapp.core.syncprotocol.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class AuthenticatedPrincipal(
    val tokenId: String,
    val role: WorkspaceRole,
    val authorizedWorkspaceIds: Set<String>, // empty set + OWNER means all workspaces
    val deviceId: String,
    val deviceName: String,
) {
    fun canAccessWorkspace(workspaceId: String, writeRequired: Boolean): Boolean {
        val scopeMatch = (role == WorkspaceRole.OWNER && authorizedWorkspaceIds.isEmpty()) ||
            workspaceId in authorizedWorkspaceIds
        if (!scopeMatch) return false
        return if (writeRequired) role == WorkspaceRole.OWNER || role == WorkspaceRole.EDITOR else true
    }
}

/**
 * JDBC Server Repository supporting PostgreSQL 17 (production Docker/TrueNAS) and isolated SQLite (tests/dev).
 * Starts completely empty (Owner Decision O-004) with persistent `server_id` and `sync_epoch` (PD-003).
 */
class JdbcSyncServerRepository(
    private val jdbcUrl: String,
    private val dbUser: String? = null,
    private val dbPassword: String? = null,
    val databaseEngine: String = if (jdbcUrl.startsWith("jdbc:postgresql")) "postgresql" else "sqlite",
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    init {
        initSchema()
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        val conn = if (dbUser != null) {
            DriverManager.getConnection(jdbcUrl, dbUser, dbPassword ?: "")
        } else {
            DriverManager.getConnection(jdbcUrl)
        }
        return conn.use { c ->
            if (databaseEngine == "sqlite") {
                c.createStatement().use { st ->
                    st.execute("PRAGMA foreign_keys = ON;")
                    st.execute("PRAGMA journal_mode = WAL;")
                    st.execute("PRAGMA busy_timeout = 5000;")
                }
            }
            block(c)
        }
    }

    private fun initSchema() = synchronized(lock) {
        withConnection { c ->
            val autoInc = if (databaseEngine == "postgresql") "BIGSERIAL PRIMARY KEY" else "INTEGER PRIMARY KEY AUTOINCREMENT"
            c.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS sync_server_meta (
                        singleton_id INTEGER PRIMARY KEY,
                        server_id TEXT NOT NULL,
                        sync_epoch BIGINT NOT NULL,
                        owner_pass_salt_b64 TEXT,
                        owner_pass_hash_b64 TEXT,
                        created_at_utc TEXT NOT NULL
                    );
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS auth_tokens (
                        token_id TEXT PRIMARY KEY,
                        token_sha256 TEXT UNIQUE NOT NULL,
                        role TEXT NOT NULL,
                        workspace_ids_json TEXT NOT NULL,
                        device_id TEXT NOT NULL,
                        device_name TEXT NOT NULL,
                        created_at_utc TEXT NOT NULL,
                        revoked_at_utc TEXT
                    );
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS pairing_codes (
                        code_sha256 TEXT PRIMARY KEY,
                        workspace_ids_json TEXT NOT NULL,
                        role TEXT NOT NULL,
                        expires_at_epoch_sec BIGINT NOT NULL,
                        redeemed_at_utc TEXT
                    );
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS sync_records (
                        workspace_id TEXT NOT NULL,
                        record_id TEXT NOT NULL,
                        record_kind TEXT NOT NULL,
                        rev BIGINT NOT NULL,
                        deleted INTEGER NOT NULL,
                        record_json TEXT,
                        referenced_blobs_json TEXT NOT NULL,
                        updated_at_utc TEXT NOT NULL,
                        PRIMARY KEY (workspace_id, record_id)
                    );
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS sync_change_log (
                        cursor_id $autoInc,
                        op_id TEXT UNIQUE NOT NULL,
                        workspace_id TEXT NOT NULL,
                        record_id TEXT NOT NULL,
                        record_kind TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        rev BIGINT NOT NULL,
                        record_json TEXT,
                        referenced_blobs_json TEXT NOT NULL,
                        server_timestamp_utc TEXT NOT NULL
                    );
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS workspace_blob_acl (
                        workspace_id TEXT NOT NULL,
                        sha256 TEXT NOT NULL,
                        mime_type TEXT NOT NULL,
                        byte_size BIGINT NOT NULL,
                        uploaded_at_utc TEXT NOT NULL,
                        PRIMARY KEY (workspace_id, sha256)
                    );
                    """.trimIndent()
                )
            }
            // Ensure persistent server identity row exists on fresh empty server
            c.prepareStatement("SELECT server_id FROM sync_server_meta WHERE singleton_id = 1").use { ps ->
                val rs = ps.executeQuery()
                if (!rs.next()) {
                    c.prepareStatement(
                        "INSERT INTO sync_server_meta(singleton_id, server_id, sync_epoch, created_at_utc) VALUES (1, ?, 1, ?)"
                    ).use { ins ->
                        ins.setString(1, "srv_${UUID.randomUUID()}")
                        ins.setString(2, Instant.now().toString())
                        ins.executeUpdate()
                    }
                }
            }
        }
    }

    fun getServerIdentity(): Triple<String, Long, Boolean> = synchronized(lock) {
        withConnection { c ->
            c.prepareStatement("SELECT server_id, sync_epoch, owner_pass_hash_b64 FROM sync_server_meta WHERE singleton_id = 1").use { ps ->
                val rs = ps.executeQuery()
                rs.next()
                Triple(rs.getString(1), rs.getLong(2), rs.getString(3) != null)
            }
        }
    }

    fun bootstrapOwner(passphrase: String, deviceName: String): AuthSessionResponse = synchronized(lock) {
        require(passphrase.length >= 8) { "Owner passphrase must be at least 8 characters" }
        withConnection { c ->
            val (serverId, syncEpoch, bootstrapped) = getServerIdentity()
            if (bootstrapped) throw IllegalStateException("Server owner is already bootstrapped")
            val salt = CryptoPrimitives.randomBytes(16)
            val hash = CryptoPrimitives.pbkdf2Sha256(passphrase, salt, iterations = 150_000)
            c.prepareStatement("UPDATE sync_server_meta SET owner_pass_salt_b64 = ?, owner_pass_hash_b64 = ? WHERE singleton_id = 1").use { ps ->
                ps.setString(1, Base64.getEncoder().encodeToString(salt))
                ps.setString(2, Base64.getEncoder().encodeToString(hash))
                ps.executeUpdate()
            }
            mintTokenLocked(c, WorkspaceRole.OWNER, emptyList(), "owner_dev_${UUID.randomUUID().toString().take(8)}", deviceName, serverId, syncEpoch)
        }
    }

    fun loginOwner(passphrase: String, deviceName: String): AuthSessionResponse? = synchronized(lock) {
        withConnection { c ->
            c.prepareStatement("SELECT server_id, sync_epoch, owner_pass_salt_b64, owner_pass_hash_b64 FROM sync_server_meta WHERE singleton_id = 1").use { ps ->
                val rs = ps.executeQuery()
                if (!rs.next()) return@withConnection null
                val saltB64 = rs.getString(3) ?: return@withConnection null
                val expectedHashB64 = rs.getString(4) ?: return@withConnection null
                val salt = Base64.getDecoder().decode(saltB64)
                val expected = Base64.getDecoder().decode(expectedHashB64)
                val computed = CryptoPrimitives.pbkdf2Sha256(passphrase, salt, iterations = 150_000)
                if (!CryptoPrimitives.constantTimeEquals(expected, computed)) return@withConnection null
                mintTokenLocked(c, WorkspaceRole.OWNER, emptyList(), "owner_dev_${UUID.randomUUID().toString().take(8)}", deviceName, rs.getString(1), rs.getLong(2))
            }
        }
    }

    fun createPairingCode(workspaceIds: List<String>, role: WorkspaceRole, ttlSeconds: Long): PairingCodeResponse = synchronized(lock) {
        val rawCode = "PN-${CryptoPrimitives.randomHexToken(4).uppercase()}"
        val codeSha = CryptoPrimitives.sha256Hex(rawCode.encodeToByteArray())
        val expires = Instant.now().plusSeconds(ttlSeconds.coerceIn(60L, 1800L))
        withConnection { c ->
            c.prepareStatement(
                "INSERT INTO pairing_codes(code_sha256, workspace_ids_json, role, expires_at_epoch_sec) VALUES (?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, codeSha)
                ps.setString(2, json.encodeToString(workspaceIds))
                ps.setString(3, role.name)
                ps.setLong(4, expires.epochSecond)
                ps.executeUpdate()
            }
        }
        PairingCodeResponse(rawCode, expires.toString(), workspaceIds, role)
    }

    fun redeemPairingCode(code: String, deviceId: String, deviceName: String): AuthSessionResponse? = synchronized(lock) {
        val codeSha = CryptoPrimitives.sha256Hex(code.trim().encodeToByteArray())
        val now = Instant.now()
        withConnection { c ->
            val (serverId, syncEpoch, _) = getServerIdentity()
            c.prepareStatement("SELECT workspace_ids_json, role, expires_at_epoch_sec, redeemed_at_utc FROM pairing_codes WHERE code_sha256 = ?").use { ps ->
                ps.setString(1, codeSha)
                val rs = ps.executeQuery()
                if (!rs.next()) return@withConnection null
                if (rs.getString(4) != null) return@withConnection null // Single-use enforced
                if (now.epochSecond > rs.getLong(3)) return@withConnection null // Expired
                val wsIds = json.decodeFromString<List<String>>(rs.getString(1))
                val role = WorkspaceRole.valueOf(rs.getString(2))
                c.prepareStatement("UPDATE pairing_codes SET redeemed_at_utc = ? WHERE code_sha256 = ?").use { up ->
                    up.setString(1, now.toString())
                    up.setString(2, codeSha)
                    up.executeUpdate()
                }
                mintTokenLocked(c, role, wsIds, deviceId, deviceName, serverId, syncEpoch)
            }
        }
    }

    fun authenticateBearer(bearerToken: String): AuthenticatedPrincipal? = synchronized(lock) {
        val tokenSha = CryptoPrimitives.sha256Hex(bearerToken.encodeToByteArray())
        withConnection { c ->
            c.prepareStatement("SELECT token_id, role, workspace_ids_json, device_id, device_name, revoked_at_utc FROM auth_tokens WHERE token_sha256 = ?").use { ps ->
                ps.setString(1, tokenSha)
                val rs = ps.executeQuery()
                if (!rs.next()) return@withConnection null
                if (rs.getString(6) != null) return@withConnection null // Revoked
                val wsIds = json.decodeFromString<List<String>>(rs.getString(3)).toSet()
                AuthenticatedPrincipal(
                    tokenId = rs.getString(1),
                    role = WorkspaceRole.valueOf(rs.getString(2)),
                    authorizedWorkspaceIds = wsIds,
                    deviceId = rs.getString(4),
                    deviceName = rs.getString(5),
                )
            }
        }
    }

    fun revokeToken(tokenId: String): Boolean = synchronized(lock) {
        withConnection { c ->
            c.prepareStatement("UPDATE auth_tokens SET revoked_at_utc = ? WHERE token_id = ? AND revoked_at_utc IS NULL").use { ps ->
                ps.setString(1, Instant.now().toString())
                ps.setString(2, tokenId)
                ps.executeUpdate() > 0
            }
        }
    }

    fun applyPushBatch(principal: AuthenticatedPrincipal, req: SyncPushRequest): SyncPushResponse = synchronized(lock) {
        withConnection { c ->
            val (serverId, syncEpoch, _) = getServerIdentity()
            if (req.boundServerId != null && (req.boundServerId != serverId || req.boundSyncEpoch != syncEpoch)) {
                return@withConnection SyncPushResponse(
                    serverId = serverId,
                    syncEpoch = syncEpoch,
                    reconciliationRequired = true,
                    receipts = emptyList(),
                    latestCursor = latestCursorLocked(c),
                )
            }

            val receipts = mutableListOf<OpApplyReceipt>()
            val now = Instant.now().toString()
            c.autoCommit = false
            try {
                for (op in req.ops) {
                    if (!principal.canAccessWorkspace(op.workspaceId, writeRequired = true)) {
                        receipts.add(OpApplyReceipt(op.opId, op.recordId, OpApplyStatus.FORBIDDEN, message = "Unauthorized workspace '${op.workspaceId}'"))
                        continue
                    }
                    // Idempotency check (C-0059)
                    val existingCursor = findAppliedOpCursorLocked(c, op.opId)
                    if (existingCursor != null) {
                        receipts.add(OpApplyReceipt(op.opId, op.recordId, OpApplyStatus.DUPLICATE_IGNORED, serverCursor = existingCursor))
                        continue
                    }

                    // Current server rev check
                    var curRev = 0L
                    var curRecJson: String? = null
                    c.prepareStatement("SELECT rev, record_json FROM sync_records WHERE workspace_id = ? AND record_id = ?").use { ps ->
                        ps.setString(1, op.workspaceId)
                        ps.setString(2, op.recordId)
                        val rs = ps.executeQuery()
                        if (rs.next()) {
                            curRev = rs.getLong(1)
                            curRecJson = rs.getString(2)
                        }
                    }

                    if (curRev > 0L && op.baseRev < curRev) {
                        val srvRec = curRecJson?.let { json.decodeFromString<CanonicalRecord>(it) }
                        receipts.add(
                            OpApplyReceipt(
                                opId = op.opId,
                                recordId = op.recordId,
                                status = OpApplyStatus.CONFLICT,
                                newRev = curRev,
                                serverRecord = srvRec,
                                message = "Optimistic conflict: baseRev=${op.baseRev} < serverRev=$curRev",
                            )
                        )
                        continue
                    }

                    val newRev = maxOf(curRev + 1L, op.record?.rev ?: (curRev + 1L))
                    val storedRecord = op.record?.copy(rev = newRev, workspaceId = op.workspaceId)
                    val storedJson = storedRecord?.let { json.encodeToString(it) }
                    val blobsJson = json.encodeToString(op.referencedBlobHashes)

                    if (curRev == 0L) {
                        c.prepareStatement(
                            "INSERT INTO sync_records(workspace_id, record_id, record_kind, rev, deleted, record_json, referenced_blobs_json, updated_at_utc) VALUES (?,?,?,?,?,?,?,?)"
                        ).use { ins ->
                            ins.setString(1, op.workspaceId)
                            ins.setString(2, op.recordId)
                            ins.setString(3, op.recordKind.name)
                            ins.setLong(4, newRev)
                            ins.setInt(5, if (op.operation == SyncOpKind.DELETE) 1 else 0)
                            ins.setString(6, storedJson)
                            ins.setString(7, blobsJson)
                            ins.setString(8, now)
                            ins.executeUpdate()
                        }
                    } else {
                        c.prepareStatement(
                            "UPDATE sync_records SET rev=?, deleted=?, record_json=?, referenced_blobs_json=?, updated_at_utc=? WHERE workspace_id=? AND record_id=?"
                        ).use { up ->
                            up.setLong(1, newRev)
                            up.setInt(2, if (op.operation == SyncOpKind.DELETE) 1 else 0)
                            up.setString(3, storedJson)
                            up.setString(4, blobsJson)
                            up.setString(5, now)
                            up.setString(6, op.workspaceId)
                            up.setString(7, op.recordId)
                            up.executeUpdate()
                        }
                    }

                    c.prepareStatement(
                        "INSERT INTO sync_change_log(op_id, workspace_id, record_id, record_kind, operation, rev, record_json, referenced_blobs_json, server_timestamp_utc) VALUES (?,?,?,?,?,?,?,?,?)"
                    ).use { logPs ->
                        logPs.setString(1, op.opId)
                        logPs.setString(2, op.workspaceId)
                        logPs.setString(3, op.recordId)
                        logPs.setString(4, op.recordKind.name)
                        logPs.setString(5, op.operation.name)
                        logPs.setLong(6, newRev)
                        logPs.setString(7, storedJson)
                        logPs.setString(8, blobsJson)
                        logPs.setString(9, now)
                        logPs.executeUpdate()
                    }
                    val assignedCursor = findAppliedOpCursorLocked(c, op.opId) ?: latestCursorLocked(c)
                    receipts.add(OpApplyReceipt(op.opId, op.recordId, OpApplyStatus.APPLIED, serverCursor = assignedCursor, newRev = newRev))
                }
                c.commit()
                SyncPushResponse(serverId, syncEpoch, false, receipts, latestCursorLocked(c))
            } catch (t: Throwable) {
                c.rollback()
                throw t
            } finally {
                c.autoCommit = true
            }
        }
    }

    fun pullChanges(workspaceId: String, afterCursor: Long, limit: Int = 200): SyncPullResponse = synchronized(lock) {
        withConnection { c ->
            val (serverId, syncEpoch, _) = getServerIdentity()
            val changes = mutableListOf<ServerChangeEntry>()
            var lastCursor = afterCursor
            c.prepareStatement(
                "SELECT cursor_id, op_id, workspace_id, record_id, record_kind, operation, rev, record_json, referenced_blobs_json, server_timestamp_utc " +
                    "FROM sync_change_log WHERE workspace_id = ? AND cursor_id > ? ORDER BY cursor_id ASC LIMIT ?"
            ).use { ps ->
                ps.setString(1, workspaceId)
                ps.setLong(2, afterCursor)
                ps.setInt(3, limit + 1)
                val rs = ps.executeQuery()
                while (rs.next()) {
                    val cur = rs.getLong(1)
                    if (changes.size < limit) {
                        val recJson = rs.getString(8)
                        changes.add(
                            ServerChangeEntry(
                                cursor = cur,
                                opId = rs.getString(2),
                                workspaceId = rs.getString(3),
                                recordId = rs.getString(4),
                                recordKind = RecordKind.valueOf(rs.getString(5)),
                                operation = SyncOpKind.valueOf(rs.getString(6)),
                                rev = rs.getLong(7),
                                record = recJson?.let { json.decodeFromString<CanonicalRecord>(it) },
                                referencedBlobHashes = json.decodeFromString<List<String>>(rs.getString(9)),
                                serverTimestampUtc = rs.getString(10),
                            )
                        )
                        lastCursor = cur
                    } else {
                        return@withConnection SyncPullResponse(serverId, syncEpoch, workspaceId, changes, lastCursor, hasMore = true)
                    }
                }
            }
            SyncPullResponse(serverId, syncEpoch, workspaceId, changes, lastCursor, hasMore = false)
        }
    }

    fun registerWorkspaceBlobAcl(workspaceId: String, sha256: String, mimeType: String, byteSize: Long) = synchronized(lock) {
        withConnection { c ->
            c.prepareStatement("DELETE FROM workspace_blob_acl WHERE workspace_id = ? AND sha256 = ?").use { del ->
                del.setString(1, workspaceId)
                del.setString(2, sha256.lowercase())
                del.executeUpdate()
            }
            c.prepareStatement(
                "INSERT INTO workspace_blob_acl(workspace_id, sha256, mime_type, byte_size, uploaded_at_utc) VALUES (?,?,?,?,?)"
            ).use { ins ->
                ins.setString(1, workspaceId)
                ins.setString(2, sha256.lowercase())
                ins.setString(3, mimeType)
                ins.setLong(4, byteSize)
                ins.setString(5, Instant.now().toString())
                ins.executeUpdate()
            }
        }
    }

    fun lookupWorkspaceBlobMime(workspaceId: String, sha256: String): String? = synchronized(lock) {
        withConnection { c ->
            c.prepareStatement("SELECT mime_type FROM workspace_blob_acl WHERE workspace_id = ? AND sha256 = ?").use { ps ->
                ps.setString(1, workspaceId)
                ps.setString(2, sha256.lowercase())
                val rs = ps.executeQuery()
                if (rs.next()) rs.getString(1) else null
            }
        }
    }

    fun createVerifiedServerBackupManifest(backupDir: File, blobStore: ServerBlobStore): ServerBackupReceiptResponse = synchronized(lock) {
        backupDir.mkdirs()
        val (serverId, syncEpoch, _) = getServerIdentity()
        val stamp = Instant.now().toString()
        val backupId = "srvbkp_${UUID.randomUUID().toString().take(8)}"
        val exportedRecords = mutableListOf<String>()
        withConnection { c ->
            c.prepareStatement("SELECT record_json FROM sync_records WHERE record_json IS NOT NULL ORDER BY workspace_id, record_id").use { ps ->
                val rs = ps.executeQuery()
                while (rs.next()) exportedRecords.add(rs.getString(1))
            }
        }
        val (blobCount, _) = blobStore.totalStats()
        val snapshotContent = """{"backupId":"$backupId","serverId":"$serverId","syncEpoch":$syncEpoch,"createdAtUtc":"$stamp","recordCount":${exportedRecords.size},"blobCount":$blobCount,"records":[${exportedRecords.joinToString(",")}]}"""
        val snapshotBytes = snapshotContent.encodeToByteArray()
        val manifestSha = CryptoPrimitives.sha256Hex(snapshotBytes)
        val outFile = File(backupDir, "$backupId.manifest.json")
        outFile.writeBytes(snapshotBytes)
        ServerBackupReceiptResponse(
            backupId = backupId,
            createdAtUtc = stamp,
            serverId = serverId,
            syncEpoch = syncEpoch,
            recordCount = exportedRecords.size,
            blobCount = blobCount.toInt(),
            manifestSha256 = manifestSha,
            backupPath = outFile.name,
        )
    }

    fun diagnosticsCounts(): Pair<Long, Int> = synchronized(lock) {
        withConnection { c ->
            val recCount = c.createStatement().use { st ->
                val rs = st.executeQuery("SELECT COUNT(*) FROM sync_records")
                if (rs.next()) rs.getLong(1) else 0L
            }
            val tokCount = c.createStatement().use { st ->
                val rs = st.executeQuery("SELECT COUNT(*) FROM auth_tokens WHERE revoked_at_utc IS NULL")
                if (rs.next()) rs.getInt(1) else 0
            }
            recCount to tokCount
        }
    }

    private fun mintTokenLocked(
        c: Connection,
        role: WorkspaceRole,
        wsIds: List<String>,
        deviceId: String,
        deviceName: String,
        serverId: String,
        syncEpoch: Long,
    ): AuthSessionResponse {
        val tokenId = "tok_${UUID.randomUUID().toString().take(12)}"
        val rawToken = "pn_${CryptoPrimitives.randomHexToken(32)}"
        val tokenSha = CryptoPrimitives.sha256Hex(rawToken.encodeToByteArray())
        c.prepareStatement(
            "INSERT INTO auth_tokens(token_id, token_sha256, role, workspace_ids_json, device_id, device_name, created_at_utc) VALUES (?,?,?,?,?,?,?)"
        ).use { ps ->
            ps.setString(1, tokenId)
            ps.setString(2, tokenSha)
            ps.setString(3, role.name)
            ps.setString(4, json.encodeToString(wsIds))
            ps.setString(5, deviceId)
            ps.setString(6, deviceName)
            ps.setString(7, Instant.now().toString())
            ps.executeUpdate()
        }
        return AuthSessionResponse(tokenId, rawToken, role, wsIds, serverId, syncEpoch)
    }

    private fun findAppliedOpCursorLocked(c: Connection, opId: String): Long? {
        c.prepareStatement("SELECT cursor_id FROM sync_change_log WHERE op_id = ?").use { ps ->
            ps.setString(1, opId)
            val rs = ps.executeQuery()
            return if (rs.next()) rs.getLong(1) else null
        }
    }

    private fun latestCursorLocked(c: Connection): Long {
        c.createStatement().use { st ->
            val rs = st.executeQuery("SELECT COALESCE(MAX(cursor_id), 0) FROM sync_change_log")
            return if (rs.next()) rs.getLong(1) else 0L
        }
    }
}
