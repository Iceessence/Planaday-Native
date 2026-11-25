package com.planaday.nativeapp.server

import com.planaday.nativeapp.core.model.BuildIdentity
import com.planaday.nativeapp.core.model.WorkspaceRole
import com.planaday.nativeapp.core.security.OutboundUrlDecision
import com.planaday.nativeapp.core.security.OutboundUrlPolicy
import com.planaday.nativeapp.core.syncprotocol.*
import com.planaday.nativeapp.server.storage.AuthenticatedPrincipal
import com.planaday.nativeapp.server.storage.JdbcSyncServerRepository
import com.planaday.nativeapp.server.storage.ServerBlobStore
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@Serializable
private data class ErrorBody(val error: String)

@Serializable
private data class RevokeBody(val revoked: Boolean, val tokenId: String)

@Serializable
private data class BlobStoredBody(val sha256: String, val size: Long, val mimeType: String)

@Serializable
private data class ProxyCheckBody(val allowed: Boolean, val host: String? = null, val reason: String? = null)

/**
 * Planaday Native Optional Self-Hosted Sync Server (Ktor 3 + PostgreSQL/SQLite).
 * Enforces PD-004 deny-by-default auth, PD-015 staged SHA-256 blobs, CONT-003 ordinary MIME types,
 * PD-014 SSRF outbound policy, C-0061 unified BuildIdentity, and O-004 fresh independent storage.
 */
fun Application.installPlanadaySyncServer(
    repository: JdbcSyncServerRepository,
    blobStore: ServerBlobStore,
    backupDir: File,
    allowedOutboundHosts: Set<String> = emptySet(),
    simulateBlobFailure: () -> Boolean = { false },
) {
    val lastBackupRef = AtomicReference<ServerBackupReceiptResponse?>(null)
    val serverBuild = BuildIdentity(platform = "ktor-jvm-server")

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorBody(cause.message ?: "Bad request"))
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorBody(cause.message ?: "Conflict"))
        }
    }

    suspend fun ApplicationCall.requireAuth(): AuthenticatedPrincipal? {
        val header = request.headers[HttpHeaders.Authorization]
        if (header == null || !header.startsWith("Bearer ")) {
            respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing Bearer token"))
            return null
        }
        val token = header.removePrefix("Bearer ").trim()
        val principal = repository.authenticateBearer(token)
        if (principal == null) {
            respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or revoked token"))
            return null
        }
        return principal
    }

    routing {
        // C-0061 & PF-52: Authoritative release + server identity + syncEpoch
        get("/api/v1/health") {
            val (serverId, syncEpoch, bootstrapped) = repository.getServerIdentity()
            call.respond(
                ServerHealthResponse(
                    status = "ok",
                    build = serverBuild,
                    serverId = serverId,
                    syncEpoch = syncEpoch,
                    ownerBootstrapped = bootstrapped,
                    databaseEngine = repository.databaseEngine,
                )
            )
        }

        get("/api/v1/capabilities") {
            call.respond(ServerCapabilitiesResponse())
        }

        // PD-004 & PF-04: Owner auth & 10-min single-use workspace-scoped pairing
        post("/api/v1/auth/bootstrap") {
            val req = call.receive<OwnerBootstrapRequest>()
            val session = repository.bootstrapOwner(req.passphrase, req.deviceName)
            call.respond(HttpStatusCode.Created, session)
        }

        post("/api/v1/auth/login") {
            val req = call.receive<OwnerLoginRequest>()
            val session = repository.loginOwner(req.passphrase, req.deviceName)
            if (session == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid passphrase"))
            } else {
                call.respond(session)
            }
        }

        post("/api/v1/auth/pairing-codes") {
            val principal = call.requireAuth() ?: return@post
            if (principal.role != WorkspaceRole.OWNER) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Owner role required"))
                return@post
            }
            val req = call.receive<CreatePairingCodeRequest>()
            call.respond(HttpStatusCode.Created, repository.createPairingCode(req.workspaceIds, req.role, req.ttlSeconds))
        }

        post("/api/v1/auth/pair") {
            val req = call.receive<RedeemPairingRequest>()
            val session = repository.redeemPairingCode(req.pairingCode, req.deviceId, req.deviceName)
            if (session == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid, expired, or already redeemed pairing code"))
            } else {
                call.respond(session)
            }
        }

        post("/api/v1/auth/tokens/{tokenId}/revoke") {
            val principal = call.requireAuth() ?: return@post
            if (principal.role != WorkspaceRole.OWNER) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Owner role required"))
                return@post
            }
            val tokenId = call.parameters["tokenId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val revoked = repository.revokeToken(tokenId)
            if (revoked) call.respond(RevokeBody(revoked = true, tokenId = tokenId))
            else call.respond(HttpStatusCode.NotFound, ErrorBody("Token not found"))
        }

        // Sync Push & Pull (idempotent, optimistic rev, workspace ACL, epoch-checked)
        post("/api/v1/sync/push") {
            val principal = call.requireAuth() ?: return@post
            val req = call.receive<SyncPushRequest>()
            if (req.ops.size > 250) {
                call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "Batch exceeds max 250 operations"))
                return@post
            }
            call.respond(repository.applyPushBatch(principal, req))
        }

        get("/api/v1/sync/pull") {
            val principal = call.requireAuth() ?: return@get
            val workspaceId = call.request.queryParameters["workspaceId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "workspaceId required"))
            if (!principal.canAccessWorkspace(workspaceId, writeRequired = false)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied to workspace '$workspaceId'"))
                return@get
            }
            val cursor = call.request.queryParameters["cursor"]?.toLongOrNull() ?: 0L
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 200
            call.respond(repository.pullChanges(workspaceId, cursor, limit))
        }

        // CONT-003 + PD-015 + TM-02: Streaming binary blobs with ALL ordinary MIME types, SHA-256 verification & workspace ACL
        put("/api/v1/blobs/{workspaceId}/{sha256}") {
            val principal = call.requireAuth() ?: return@put
            val workspaceId = call.parameters["workspaceId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val sha256 = call.parameters["sha256"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            if (!principal.canAccessWorkspace(workspaceId, writeRequired = true)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Write denied for workspace '$workspaceId'"))
                return@put
            }
            if (simulateBlobFailure()) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Simulated storage failure"))
                return@put
            }
            val bytes = call.receive<ByteArray>()
            if (bytes.size.toLong() > 50L * 1024L * 1024L) {
                call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "Blob exceeds 50 MB limit"))
                return@put
            }
            val contentTypeHeader = call.request.headers[HttpHeaders.ContentType] ?: "application/octet-stream"
            val (verifiedHash, byteSize) = blobStore.storeStagedVerified(sha256, bytes)
            repository.registerWorkspaceBlobAcl(workspaceId, verifiedHash, contentTypeHeader, byteSize)
            call.respond(
                HttpStatusCode.OK,
                BlobStoredBody(sha256 = verifiedHash, size = byteSize, mimeType = contentTypeHeader),
            )
        }

        get("/api/v1/blobs/{workspaceId}/{sha256}") {
            val principal = call.requireAuth() ?: return@get
            val workspaceId = call.parameters["workspaceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val sha256 = call.parameters["sha256"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            if (!principal.canAccessWorkspace(workspaceId, writeRequired = false)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied to workspace '$workspaceId'"))
                return@get
            }
            // TM-02: Knowing a SHA-256 hash NEVER grants access unless workspace holds an authorized ACL ref!
            val mime = repository.lookupWorkspaceBlobMime(workspaceId, sha256)
            if (mime == null) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Blob not authorized for workspace '$workspaceId'"))
                return@get
            }
            val bytes = blobStore.readVerified(sha256)
            if (bytes == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Blob bytes missing"))
                return@get
            }
            call.response.header(HttpHeaders.CacheControl, "private, max-age=3600")
            if (mime.contains("svg", ignoreCase = true) || mime.contains("html", ignoreCase = true)) {
                call.response.header("Content-Security-Policy", "default-src 'none'; sandbox")
            }
            val ct = runCatching { ContentType.parse(mime) }.getOrDefault(ContentType.Application.OctetStream)
            call.respondBytes(bytes, contentType = ct)
        }

        // PD-014: Outbound SSRF policy enforcement endpoint
        get("/api/v1/proxy/check") {
            call.requireAuth() ?: return@get
            val targetUrl = call.request.queryParameters["url"] ?: ""
            when (val dec = OutboundUrlPolicy.validateUrl(targetUrl, allowedHostOverrides = allowedOutboundHosts)) {
                is OutboundUrlDecision.Allowed -> call.respond(ProxyCheckBody(allowed = true, host = dec.host))
                is OutboundUrlDecision.Blocked -> call.respond(HttpStatusCode.Forbidden, ProxyCheckBody(allowed = false, reason = dec.reason))
            }
        }

        // Owner diagnostics & verified server backup (PF-42, PF-47, PF-52)
        get("/api/v1/diagnostics") {
            val principal = call.requireAuth() ?: return@get
            if (principal.role != WorkspaceRole.OWNER) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Owner role required"))
                return@get
            }
            val (serverId, syncEpoch, _) = repository.getServerIdentity()
            val (recCount, tokCount) = repository.diagnosticsCounts()
            val (blobCount, blobBytes) = blobStore.totalStats()
            call.respond(
                ServerDiagnosticsResponse(
                    build = serverBuild,
                    serverId = serverId,
                    syncEpoch = syncEpoch,
                    totalRecords = recCount,
                    totalBlobs = blobCount,
                    totalBlobBytes = blobBytes,
                    activeDeviceTokens = tokCount,
                    lastBackupReceipt = lastBackupRef.get(),
                )
            )
        }

        post("/api/v1/admin/backup") {
            val principal = call.requireAuth() ?: return@post
            if (principal.role != WorkspaceRole.OWNER) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Owner role required"))
                return@post
            }
            val receipt = repository.createVerifiedServerBackupManifest(backupDir, blobStore)
            lastBackupRef.set(receipt)
            call.respond(HttpStatusCode.Created, receipt)
        }
    }
}

fun main() {
    val port = System.getenv("PLANADAY_PORT")?.toIntOrNull() ?: 8420
    val dataRoot = File(System.getenv("PLANADAY_DATA_DIR") ?: "./planaday-native-server-data")
    val blobDir = File(System.getenv("PLANADAY_BLOB_DIR") ?: File(dataRoot, "blobs").absolutePath)
    val backupDir = File(System.getenv("PLANADAY_BACKUP_DIR") ?: File(dataRoot, "backups").absolutePath)
    val jdbcUrl = System.getenv("PLANADAY_JDBC_URL")
        ?: "jdbc:sqlite:${File(dataRoot, "planaday_native_server_v1.db").apply { parentFile?.mkdirs() }.absolutePath}"
    val dbUser = System.getenv("PLANADAY_DB_USER")
    val dbPass = System.getenv("PLANADAY_DB_PASSWORD")
    val allowedHosts = System.getenv("PLANADAY_ALLOWED_OUTBOUND_HOSTS")
        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    val repo = JdbcSyncServerRepository(jdbcUrl, dbUser, dbPass)
    val blobs = ServerBlobStore(blobDir)

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        installPlanadaySyncServer(repo, blobs, backupDir, allowedHosts)
    }.start(wait = true)
}
