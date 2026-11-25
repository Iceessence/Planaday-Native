package com.planaday.nativeapp.core.syncclient

import com.planaday.nativeapp.core.domain.DomainClock
import com.planaday.nativeapp.core.domain.SystemDomainClock
import com.planaday.nativeapp.core.model.CanonicalRecord
import com.planaday.nativeapp.core.model.RecordKind
import com.planaday.nativeapp.core.persistence.*
import com.planaday.nativeapp.core.security.CryptoPrimitives
import com.planaday.nativeapp.core.syncprotocol.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Bidirectional Offline-First Sync Client (PF-02, PF-03, PF-04, PF-51, PD-002, PD-003, CONT-004).
 * - Uploads referenced blobs with server SHA-256 verification BEFORE/WITH records.
 * - Downloads remote blobs referenced by pulled records with client SHA-256 verification (PD-002).
 * - Strictly sets SyncHealthStatus.ERROR whenever any blob transfer fails — NEVER reports SYNCED with 0 pending (CONT-004).
 * - Detects server replacement or backup restore via (boundServerId, boundSyncEpoch) and enters RECONCILIATION_REQUIRED (PD-003).
 */
class PlanadaySyncClient(
    private val repository: PlanadayRepository,
    private val httpClient: HttpClient,
    private val serverBaseUrl: String,
    private val bearerTokenProvider: () -> String?,
    private val clock: DomainClock = SystemDomainClock,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun baseUrl(): String = serverBaseUrl.trimEnd('/')

    suspend fun fetchHealth(): ServerHealthResponse =
        httpClient.get("${baseUrl()}/api/v1/health").body()

    suspend fun redeemPairingCode(pairingCode: String, deviceName: String): AuthSessionResponse =
        httpClient.post("${baseUrl()}/api/v1/auth/pair") {
            contentType(ContentType.Application.Json)
            setBody(RedeemPairingRequest(pairingCode, repository.deviceId, deviceName))
        }.body()

    /**
     * Executes a full bidirectional sync cycle for [workspaceId]:
     * 1. Check server identity/epoch against bound cursor & backup-restore flag (PD-003).
     * 2. Upload pending local blobs; record explicit BlobTransferReceipt on success or failure (CONT-004).
     * 3. Push pending outbox ops idempotently; record server conflicts without discarding local edits.
     * 4. Pull remote changes since last cursor; download & SHA-256 verify all referenced blobs (PD-002).
     * 5. Advance cursor ONLY after local DB + required blobs succeed.
     */
    suspend fun syncWorkspace(
        workspaceId: String,
        allowReplacementReconcile: Boolean = false,
    ): SyncStatusSnapshot {
        val token = bearerTokenProvider()
            ?: return SyncStatusSnapshot(
                status = SyncHealthStatus.IDLE_OFFLINE,
                pendingOpsCount = repository.db.syncDao().countPendingOutbox(workspaceId),
                pendingBlobUploads = 0,
                pendingBlobDownloads = 0,
                failedBlobTransfers = 0,
                unresolvedConflicts = repository.db.syncDao().listUnresolvedConflicts(workspaceId).size,
                lastError = "Not paired / no auth token",
            )

        val health = fetchHealth()
        val existingCursor = repository.db.syncDao().getCursor(workspaceId)
        val restoredFlag = existingCursor?.restoredFromBackupAtUtc != null
        val serverMismatch = existingCursor?.boundServerId != null &&
            (existingCursor.boundServerId != health.serverId || existingCursor.boundSyncEpoch != health.syncEpoch)

        if ((restoredFlag || serverMismatch) && !allowReplacementReconcile) {
            return buildStatusSnapshot(
                workspaceId = workspaceId,
                forcedStatus = SyncHealthStatus.RECONCILIATION_REQUIRED,
                lastError = "Server identity/epoch changed or client restored from backup; explicit reconciliation required (PD-003).",
            )
        }

        // If reconciling against an empty replacement server (PD-003), queue all local workspace records & blobs!
        if ((restoredFlag || serverMismatch) && allowReplacementReconcile) {
            requeueAllLocalForReconciliation(workspaceId)
            repository.db.syncDao().saveCursor(
                SyncCursorEntity(
                    workspaceId = workspaceId,
                    boundServerId = health.serverId,
                    boundSyncEpoch = health.syncEpoch,
                    lastCursor = 0L,
                    lastSyncAtUtc = existingCursor?.lastSyncAtUtc,
                    restoredFromBackupAtUtc = null,
                )
            )
        }

        val effectiveCursor = repository.db.syncDao().getCursor(workspaceId)
        var failedBlobs = 0
        var lastError: String? = null

        // Step A: Upload pending local blobs referenced by pending outbox or blob receipts
        val pendingOps = repository.db.syncDao().pendingOutbox(workspaceId, limit = 250)
        val uploadHashes = LinkedHashSet<String>()
        for (op in pendingOps) {
            uploadHashes.addAll(runCatching { json.decodeFromString<List<String>>(op.referencedBlobHashesJson) }.getOrDefault(emptyList()))
        }
        for (r in repository.listBlobReceipts(workspaceId)) {
            if (r.direction == BlobTransferDirection.UPLOAD && r.status != BlobTransferStatus.VERIFIED) {
                uploadHashes.add(r.sha256)
            }
        }

        for (hash in uploadHashes) {
            val bytes = repository.blobStore.getVerified(hash) ?: continue
            val meta = repository.db.attachmentDao().getAttachment(hash)
            val mime = meta?.mimeType ?: "application/octet-stream"
            val now = clock.nowUtcIso()
            try {
                val resp = httpClient.put("${baseUrl()}/api/v1/blobs/$workspaceId/$hash") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, mime)
                    setBody(bytes)
                }
                if (resp.status.isSuccess()) {
                    repository.saveBlobReceipt(
                        BlobTransferReceipt(hash, workspaceId, BlobTransferDirection.UPLOAD, BlobTransferStatus.VERIFIED, bytes.size.toLong(), 1, null, now)
                    )
                } else {
                    failedBlobs++
                    lastError = "Blob upload HTTP ${resp.status.value} for $hash"
                    repository.saveBlobReceipt(
                        BlobTransferReceipt(hash, workspaceId, BlobTransferDirection.UPLOAD, BlobTransferStatus.FAILED, bytes.size.toLong(), 1, lastError, now)
                    )
                }
            } catch (t: Throwable) {
                failedBlobs++
                lastError = "Blob upload error: ${t.message}"
                repository.saveBlobReceipt(
                    BlobTransferReceipt(hash, workspaceId, BlobTransferDirection.UPLOAD, BlobTransferStatus.FAILED, bytes.size.toLong(), 1, lastError, now)
                )
            }
        }

        // Step B: Push pending outbox ops (skip records whose required blobs failed upload!)
        val failedHashSet = repository.listBlobReceipts(workspaceId)
            .filter { it.direction == BlobTransferDirection.UPLOAD && it.status == BlobTransferStatus.FAILED }
            .map { it.sha256 }
            .toSet()

        val readyOps = pendingOps.filter { op ->
            val refs = runCatching { json.decodeFromString<List<String>>(op.referencedBlobHashesJson) }.getOrDefault(emptyList())
            refs.none { it in failedHashSet }
        }

        if (readyOps.isNotEmpty()) {
            val envelopes = readyOps.map { e ->
                OutboxOpEnvelope(
                    opId = e.opId,
                    workspaceId = e.workspaceId,
                    recordId = e.recordId,
                    recordKind = RecordKind.valueOf(e.recordKind),
                    operation = SyncOpKind.valueOf(e.operation),
                    baseRev = e.baseRev,
                    schemaVersion = e.schemaVersion,
                    record = e.recordJson?.let { json.decodeFromString<CanonicalRecord>(it) },
                    referencedBlobHashes = runCatching { json.decodeFromString<List<String>>(e.referencedBlobHashesJson) }.getOrDefault(emptyList()),
                    deviceId = e.deviceId,
                    clientTimestampUtc = e.createdAtUtc,
                )
            }
            val pushResp: SyncPushResponse = httpClient.post("${baseUrl()}/api/v1/sync/push") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    SyncPushRequest(
                        deviceId = repository.deviceId,
                        boundServerId = effectiveCursor?.boundServerId,
                        boundSyncEpoch = effectiveCursor?.boundSyncEpoch,
                        ops = envelopes,
                    )
                )
            }.body()

            if (pushResp.reconciliationRequired) {
                return buildStatusSnapshot(workspaceId, SyncHealthStatus.RECONCILIATION_REQUIRED, "Server epoch changed during push")
            }

            for (receipt in pushResp.receipts) {
                when (receipt.status) {
                    OpApplyStatus.APPLIED, OpApplyStatus.DUPLICATE_IGNORED -> {
                        repository.db.syncDao().removeOutboxOp(receipt.opId)
                    }
                    OpApplyStatus.CONFLICT -> {
                        val localOp = readyOps.firstOrNull { it.opId == receipt.opId }
                        if (localOp != null && receipt.serverRecord != null) {
                            repository.db.syncDao().insertConflict(
                                SyncConflictEntity(
                                    id = UUID.randomUUID().toString(),
                                    workspaceId = workspaceId,
                                    recordId = receipt.recordId,
                                    recordKind = localOp.recordKind,
                                    localRecordJson = localOp.recordJson ?: "{}",
                                    remoteRecordJson = json.encodeToString(receipt.serverRecord),
                                    reason = receipt.message ?: "Server revision conflict",
                                    createdAtUtc = clock.nowUtcIso(),
                                    resolved = false,
                                )
                            )
                            repository.db.syncDao().removeOutboxOp(receipt.opId)
                        }
                    }
                    OpApplyStatus.FORBIDDEN, OpApplyStatus.INVALID -> {
                        lastError = receipt.message ?: "Push rejected (${receipt.status})"
                    }
                }
            }
        }

        // Step C: Pull remote changes & download missing referenced blobs with client SHA-256 check (PD-002)
        val startCursor = effectiveCursor?.lastCursor ?: 0L
        val pullResp: SyncPullResponse = httpClient.get("${baseUrl()}/api/v1/sync/pull") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("workspaceId", workspaceId)
            parameter("cursor", startCursor)
        }.body()

        var allPullBlobsOk = true
        for (change in pullResp.changes) {
            val pulledRec = change.record
            if (change.operation == SyncOpKind.DELETE) {
                repository.db.recordDao().hardDelete(workspaceId, change.recordId)
            } else if (pulledRec != null) {
                repository.applyPulledRecordFromServer(pulledRec)
            }
            for (blobHash in change.referencedBlobHashes) {
                if (!repository.blobStore.exists(blobHash)) {
                    val now = clock.nowUtcIso()
                    try {
                        val dlResp = httpClient.get("${baseUrl()}/api/v1/blobs/$workspaceId/$blobHash") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                        }
                        if (dlResp.status.isSuccess()) {
                            val rawBytes = dlResp.readRawBytes()
                            val computed = CryptoPrimitives.sha256Hex(rawBytes)
                            if (!computed.equals(blobHash, ignoreCase = true)) {
                                throw IllegalStateException("Downloaded blob SHA-256 mismatch")
                            }
                            repository.blobStore.putVerified(rawBytes, expectedSha256 = blobHash)
                            val mime = dlResp.headers[HttpHeaders.ContentType] ?: "application/octet-stream"
                            repository.db.attachmentDao().insertAttachment(
                                AttachmentEntity(
                                    sha256 = blobHash,
                                    mimeType = mime,
                                    byteSize = rawBytes.size.toLong(),
                                    originalFilename = "$blobHash.bin",
                                    createdAtUtc = now,
                                    localStorageRelPath = repository.blobStore.relativePathFor(blobHash),
                                    thumbnailRelPath = null,
                                )
                            )
                            repository.db.attachmentDao().insertRef(
                                AttachmentRefEntity(
                                    id = "${workspaceId}_${change.recordId}_${blobHash}_synced",
                                    workspaceId = workspaceId,
                                    recordId = change.recordId,
                                    sha256 = blobHash,
                                    role = "synced_attachment",
                                    createdAtUtc = now,
                                )
                            )
                            repository.saveBlobReceipt(
                                BlobTransferReceipt(blobHash, workspaceId, BlobTransferDirection.DOWNLOAD, BlobTransferStatus.VERIFIED, rawBytes.size.toLong(), 1, null, now)
                            )
                        } else {
                            allPullBlobsOk = false
                            failedBlobs++
                            lastError = "Blob download HTTP ${dlResp.status.value} for $blobHash"
                            repository.saveBlobReceipt(
                                BlobTransferReceipt(blobHash, workspaceId, BlobTransferDirection.DOWNLOAD, BlobTransferStatus.FAILED, 0L, 1, lastError, now)
                            )
                        }
                    } catch (t: Throwable) {
                        allPullBlobsOk = false
                        failedBlobs++
                        lastError = "Blob download failed: ${t.message}"
                        repository.saveBlobReceipt(
                            BlobTransferReceipt(blobHash, workspaceId, BlobTransferDirection.DOWNLOAD, BlobTransferStatus.FAILED, 0L, 1, lastError, now)
                        )
                    }
                }
            }
        }

        // Advance cursor only when pull blobs verified cleanly
        val newCursor = if (allPullBlobsOk) pullResp.nextCursor else startCursor
        repository.db.syncDao().saveCursor(
            SyncCursorEntity(
                workspaceId = workspaceId,
                boundServerId = pullResp.serverId,
                boundSyncEpoch = pullResp.syncEpoch,
                lastCursor = newCursor,
                lastSyncAtUtc = clock.nowUtcIso(),
                restoredFromBackupAtUtc = null,
            )
        )

        val forced = when {
            failedBlobs > 0 || lastError != null -> SyncHealthStatus.ERROR
            else -> null
        }
        return buildStatusSnapshot(workspaceId, forced, lastError)
    }

    private suspend fun requeueAllLocalForReconciliation(workspaceId: String) {
        val now = clock.nowUtcIso()
        val allActive = repository.db.recordDao().listActive(workspaceId, limit = 10_000, offset = 0)
        for (entity in allActive) {
            val refs = repository.db.attachmentDao().refsForRecord(workspaceId, entity.id).map { it.sha256 }
            val rec = repository.getRecord(workspaceId, entity.id) ?: continue
            repository.db.syncDao().enqueueOutbox(
                OutboxOpEntity(
                    opId = UUID.randomUUID().toString(),
                    workspaceId = workspaceId,
                    recordId = rec.id,
                    recordKind = rec.kind.name,
                    operation = SyncOpKind.UPSERT.name,
                    baseRev = 0L,
                    schemaVersion = rec.schemaVersion,
                    recordJson = json.encodeToString(rec),
                    referencedBlobHashesJson = json.encodeToString(refs),
                    deviceId = repository.deviceId,
                    createdAtUtc = now,
                )
            )
            for (h in refs) {
                val bytes = repository.blobStore.getVerified(h)
                if (bytes != null) {
                    repository.saveBlobReceipt(
                        BlobTransferReceipt(h, workspaceId, BlobTransferDirection.UPLOAD, BlobTransferStatus.PENDING, bytes.size.toLong(), 0, null, now)
                    )
                }
            }
        }
    }

    suspend fun buildStatusSnapshot(
        workspaceId: String,
        forcedStatus: SyncHealthStatus? = null,
        lastError: String? = null,
    ): SyncStatusSnapshot {
        val pendingOps = repository.db.syncDao().countPendingOutbox(workspaceId)
        val receipts = repository.listBlobReceipts(workspaceId)
        val pendingUp = receipts.count { it.direction == BlobTransferDirection.UPLOAD && it.status == BlobTransferStatus.PENDING }
        val pendingDown = receipts.count { it.direction == BlobTransferDirection.DOWNLOAD && it.status == BlobTransferStatus.PENDING }
        val failed = receipts.count { it.status == BlobTransferStatus.FAILED }
        val conflicts = repository.db.syncDao().listUnresolvedConflicts(workspaceId).size
        val cursor = repository.db.syncDao().getCursor(workspaceId)

        // CONT-004 strict invariant: NEVER return SYNCED when any blob failed or is pending!
        val derivedStatus = forcedStatus ?: when {
            failed > 0 || lastError != null -> SyncHealthStatus.ERROR
            pendingUp > 0 || pendingDown > 0 -> SyncHealthStatus.PENDING_ATTACHMENTS
            pendingOps > 0 -> SyncHealthStatus.SYNCING
            else -> SyncHealthStatus.SYNCED
        }
        return SyncStatusSnapshot(
            status = derivedStatus,
            pendingOpsCount = pendingOps,
            pendingBlobUploads = pendingUp,
            pendingBlobDownloads = pendingDown,
            failedBlobTransfers = failed,
            unresolvedConflicts = conflicts,
            lastSyncAtUtc = cursor?.lastSyncAtUtc,
            boundServerId = cursor?.boundServerId,
            boundSyncEpoch = cursor?.boundSyncEpoch,
            lastError = lastError,
            blobReceipts = receipts,
        )
    }

    companion object {
        fun createDefaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
        }
    }
}
