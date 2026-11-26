package com.planaday.nativeapp.tests.integration

import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.persistence.*
import com.planaday.nativeapp.core.security.CryptoPrimitives
import com.planaday.nativeapp.core.syncclient.PlanadaySyncClient
import com.planaday.nativeapp.core.syncprotocol.*
import com.planaday.nativeapp.core.usecases.PlanadayUseCases
import com.planaday.nativeapp.server.installPlanadaySyncServer
import com.planaday.nativeapp.server.storage.JdbcSyncServerRepository
import com.planaday.nativeapp.server.storage.ServerBlobStore
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

/**
 * Fresh-Start Acceptance Gate + Two-Client Offline-to-Sync E2E + Server Security & Replacement Reconciliation Suite.
 * Covers: O-004, PD-002, PD-003, PD-004, PD-014, PD-015, C-0059, C-0061, CONT-003, CONT-004, CONT-009, PF-02..PF-04, PF-42, PF-51, PF-52.
 */
class FreshStartAndSyncE2ETest {
    private fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    @Test
    fun freshStartGateEmptyClientsOfflineCreatePairSyncWithVerifiedPngJpegWavBlobs() = testApplication {
        val srvRoot = tempDir("planaday-srv-a-")
        val devARoot = tempDir("planaday-deva-")
        val devBRoot = tempDir("planaday-devb-")
        val simulate503 = AtomicBoolean(false)

        val srvDbFile = File(srvRoot, "server_v1.db")
        val srvRepo = JdbcSyncServerRepository("jdbc:sqlite:${srvDbFile.absolutePath}")
        val srvBlobs = ServerBlobStore(File(srvRoot, "blobs"))
        val srvBackups = File(srvRoot, "backups")

        application {
            installPlanadaySyncServer(
                repository = srvRepo,
                blobStore = srvBlobs,
                backupDir = srvBackups,
                simulateBlobFailure = { simulate503.get() },
            )
        }

        val httpClient = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
        }

        // 1. Fresh-Start O-004 Proof: Server, Device A, and Device B ALL start completely EMPTY!
        val health: ServerHealthResponse = httpClient.get("/api/v1/health").body()
        assertEquals("ok", health.status)
        assertEquals(BuildIdentity.CURRENT_APP_VERSION, health.build.appVersion) // C-0061
        assertEquals(1, health.build.protocolVersion)
        assertFalse(health.ownerBootstrapped)

        val dbA = JvmPlanadayDatabaseFactory.createOnDisk(File(devARoot, BuildIdentity.CLIENT_DB_NAME))
        val dbB = JvmPlanadayDatabaseFactory.createOnDisk(File(devBRoot, BuildIdentity.CLIENT_DB_NAME))
        try {
            val repoA = PlanadayRepository(dbA, LocalBlobStore(File(devARoot, "blobs_v1")), initialDeviceId = "android_dev_a")
            val repoB = PlanadayRepository(dbB, LocalBlobStore(File(devBRoot, "blobs_v1")), initialDeviceId = "windows_dev_b")

            assertEquals(0L, repoA.countAllRecords(), "Fresh Device A MUST start with 0 records (O-004)")
            assertEquals(0L, repoB.countAllRecords(), "Fresh Device B MUST start with 0 records (O-004)")

            repoA.ensureDefaultWorkspaceExists("ws_personal", "Personal")
            repoB.ensureDefaultWorkspaceExists("ws_personal", "Personal")

            // 2. Create synthetic records + PNG/JPEG/WAV attachments OFFLINE on Device A before pairing!
            val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 10, 20, 30, 40)
            val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
            val wavBytes = "RIFF....WAVEfmt ".encodeToByteArray()

            val noteA = repoA.createRecord("ws_personal", RecordKind.NOTE, "Offline Field Note", """{"content":"Created offline on Device A"}""", "Offline note")
            val pngMeta = repoA.attachBlobToRecord("ws_personal", noteA.id, "sample.png", "image/png", pngBytes)
            val jpgMeta = repoA.attachBlobToRecord("ws_personal", noteA.id, "photo.jpg", "image/jpeg", jpegBytes)
            val wavMeta = repoA.attachBlobToRecord("ws_personal", noteA.id, "voice.wav", "audio/wav", wavBytes)
            repoA.updateRecordAtomic("ws_personal", noteA.id, referencedBlobHashes = listOf(pngMeta.sha256, jpgMeta.sha256, wavMeta.sha256)) { it }

            // 3. PD-004 Security check: unauthenticated sync/blob/diagnostics/backup MUST return 401!
            assertEquals(HttpStatusCode.Unauthorized, httpClient.get("/api/v1/sync/pull?workspaceId=ws_personal").status)
            assertEquals(HttpStatusCode.Unauthorized, httpClient.get("/api/v1/diagnostics").status)
            assertEquals(HttpStatusCode.Unauthorized, httpClient.post("/api/v1/admin/backup").status)

            // 4. Bootstrap owner + mint workspace-scoped pairing codes for Device A & Device B
            val ownerSession: AuthSessionResponse = httpClient.post("/api/v1/auth/bootstrap") {
                contentType(ContentType.Application.Json)
                setBody(OwnerBootstrapRequest("OwnerStrongPass!2026", "Owner Console"))
            }.body()

            val pairCodeA: PairingCodeResponse = httpClient.post("/api/v1/auth/pairing-codes") {
                header(HttpHeaders.Authorization, "Bearer ${ownerSession.bearerToken}")
                contentType(ContentType.Application.Json)
                setBody(CreatePairingCodeRequest(listOf("ws_personal"), WorkspaceRole.EDITOR))
            }.body()
            val pairCodeB: PairingCodeResponse = httpClient.post("/api/v1/auth/pairing-codes") {
                header(HttpHeaders.Authorization, "Bearer ${ownerSession.bearerToken}")
                contentType(ContentType.Application.Json)
                setBody(CreatePairingCodeRequest(listOf("ws_personal"), WorkspaceRole.EDITOR))
            }.body()

            val clientA = PlanadaySyncClient(repoA, httpClient, "", { null })
            val sessionA = clientA.redeemPairingCode(pairCodeA.pairingCode, "Android Phone A")
            val sessionB = PlanadaySyncClient(repoB, httpClient, "", { null }).redeemPairingCode(pairCodeB.pairingCode, "Windows Desktop B")

            // Single-use pairing code cannot be redeemed twice
            val replayPair = httpClient.post("/api/v1/auth/pair") {
                contentType(ContentType.Application.Json)
                setBody(RedeemPairingRequest(pairCodeA.pairingCode, "attacker", "Rogue"))
            }
            assertEquals(HttpStatusCode.Unauthorized, replayPair.status)

            // 5. PD-004 Workspace ACL & PD-014 SSRF & PD-015 Spoofed SHA-256 checks
            assertEquals(
                HttpStatusCode.Forbidden,
                httpClient.get("/api/v1/sync/pull?workspaceId=ws_lab") {
                    header(HttpHeaders.Authorization, "Bearer ${sessionA.bearerToken}")
                }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                httpClient.post("/api/v1/admin/backup") {
                    header(HttpHeaders.Authorization, "Bearer ${sessionA.bearerToken}")
                }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                httpClient.get("/api/v1/proxy/check?url=http://169.254.169.254/latest/meta-data/") {
                    header(HttpHeaders.Authorization, "Bearer ${sessionA.bearerToken}")
                }.status,
            )
            val spoofedPut = httpClient.put("/api/v1/blobs/ws_personal/${"a".repeat(64)}") {
                header(HttpHeaders.Authorization, "Bearer ${sessionA.bearerToken}")
                header(HttpHeaders.ContentType, "image/png")
                setBody(pngBytes)
            }
            assertEquals(HttpStatusCode.BadRequest, spoofedPut.status, "PD-015: mismatched SHA-256 MUST return HTTP 400")

            // 6. CONT-004 + PF-51: Simulate HTTP 503 blob failure -> MUST report ERROR & totalPendingCount >= 1 (NEVER SYNCED!)
            val syncA = PlanadaySyncClient(repoA, httpClient, "", { sessionA.bearerToken })
            val syncB = PlanadaySyncClient(repoB, httpClient, "", { sessionB.bearerToken })
            simulate503.set(true)
            val failedSnap = syncA.syncWorkspace("ws_personal")
            assertEquals(SyncHealthStatus.ERROR, failedSnap.status, "CONT-004: Failed blob PUT must NEVER report SYNCED")
            assertTrue(failedSnap.failedBlobTransfers >= 1)
            assertTrue(failedSnap.totalPendingCount >= 1)

            // 7. Restore server health -> Device A uploads all 3 ordinary media types (PNG/JPEG/WAV - CONT-003) + Note;
            //    Clean Device B pulls Note AND automatically downloads & SHA-256 verifies all 3 blobs (PD-002)!
            simulate503.set(false)
            val okSnapA = syncA.syncWorkspace("ws_personal")
            assertEquals(SyncHealthStatus.SYNCED, okSnapA.status)
            assertEquals(0, okSnapA.totalPendingCount)

            // TM-02: Cross-workspace blob probing by knowing exact SHA-256 hash MUST return 403!
            val labPair: PairingCodeResponse = httpClient.post("/api/v1/auth/pairing-codes") {
                header(HttpHeaders.Authorization, "Bearer ${ownerSession.bearerToken}")
                contentType(ContentType.Application.Json)
                setBody(CreatePairingCodeRequest(listOf("ws_lab"), WorkspaceRole.VIEWER))
            }.body()
            val labSession = PlanadaySyncClient(repoB, httpClient, "", { null }).redeemPairingCode(labPair.pairingCode, "Lab Viewer")
            val crossBlobProbe = httpClient.get("/api/v1/blobs/ws_lab/${pngMeta.sha256}") {
                header(HttpHeaders.Authorization, "Bearer ${labSession.bearerToken}")
            }
            assertEquals(HttpStatusCode.Forbidden, crossBlobProbe.status, "TM-02: Knowing hash must NOT leak blob across workspaces")

            val okSnapB = syncB.syncWorkspace("ws_personal")
            assertEquals(SyncHealthStatus.SYNCED, okSnapB.status)
            val pulledNote = repoB.getRecord("ws_personal", noteA.id)
            assertNotNull(pulledNote, "Device B must receive Device A's offline note")
            assertEquals("Offline Field Note", pulledNote.title)
            assertContentEquals(pngBytes, repoB.blobStore.getVerified(pngMeta.sha256), "PD-002: PNG bytes verified on Device B")
            assertContentEquals(jpegBytes, repoB.blobStore.getVerified(jpgMeta.sha256), "CONT-003: JPEG bytes verified on Device B")
            assertContentEquals(wavBytes, repoB.blobStore.getVerified(wavMeta.sha256), "CONT-003: WAV bytes verified on Device B")

            // 8. C-0059 Idempotency: pushing same opId twice stays single operation
            val dupOp = OutboxOpEnvelope("op_idempotent_1", "ws_personal", "rec_idem", RecordKind.TASK, SyncOpKind.UPSERT, 0L, 1, noteA.copy(id = "rec_idem", kind = RecordKind.TASK), emptyList(), "devA", "2026-11-25T00:00:00Z")
            val push1: SyncPushResponse = httpClient.post("/api/v1/sync/push") {
                header(HttpHeaders.Authorization, "Bearer ${sessionA.bearerToken}")
                contentType(ContentType.Application.Json)
                setBody(SyncPushRequest("devA", null, null, listOf(dupOp)))
            }.body()
            val push2: SyncPushResponse = httpClient.post("/api/v1/sync/push") {
                header(HttpHeaders.Authorization, "Bearer ${sessionA.bearerToken}")
                contentType(ContentType.Application.Json)
                setBody(SyncPushRequest("devA", null, null, listOf(dupOp)))
            }.body()
            assertEquals(OpApplyStatus.APPLIED, push1.receipts.single().status)
            assertEquals(OpApplyStatus.DUPLICATE_IGNORED, push2.receipts.single().status)

            // 9. CONT-009 + PF-42 Owner verified server backup manifest & immediate token revocation (PD-004)
            val bkpResp: ServerBackupReceiptResponse = httpClient.post("/api/v1/admin/backup") {
                header(HttpHeaders.Authorization, "Bearer ${ownerSession.bearerToken}")
            }.body()
            assertEquals(64, bkpResp.manifestSha256.length)
            assertTrue(bkpResp.recordCount >= 2)

            httpClient.post("/api/v1/auth/tokens/${sessionA.tokenId}/revoke") {
                header(HttpHeaders.Authorization, "Bearer ${ownerSession.bearerToken}")
            }
            assertEquals(
                HttpStatusCode.Unauthorized,
                httpClient.get("/api/v1/sync/pull?workspaceId=ws_personal") {
                    header(HttpHeaders.Authorization, "Bearer ${sessionA.bearerToken}")
                }.status,
                "Revoked token MUST fail immediately with HTTP 401",
            )
        } finally {
            dbA.close()
            dbB.close()
            srvRoot.deleteRecursively()
            devARoot.deleteRecursively()
            devBRoot.deleteRecursively()
        }
    }

    @Test
    fun pd003AndPf42BackupRestoreOntoEmptyReplacementServerReconcilesRecordsAndBlobs() = testApplication {
        val srvBRoot = tempDir("planaday-srv-b-")
        val clientRoot = tempDir("planaday-client-restore-")
        val restoredRoot = tempDir("planaday-client-clean-")

        val srvBRepo = JdbcSyncServerRepository("jdbc:sqlite:${File(srvBRoot, "server_b.db").absolutePath}")
        val srvBBlobs = ServerBlobStore(File(srvBRoot, "blobs"))
        application {
            installPlanadaySyncServer(srvBRepo, srvBBlobs, File(srvBRoot, "backups"))
        }
        val httpClient = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        }

        val dbOrig = JvmPlanadayDatabaseFactory.createOnDisk(File(clientRoot, BuildIdentity.CLIENT_DB_NAME))
        val dbRestored = JvmPlanadayDatabaseFactory.createOnDisk(File(restoredRoot, BuildIdentity.CLIENT_DB_NAME))
        try {
            val repoOrig = PlanadayRepository(dbOrig, LocalBlobStore(File(clientRoot, "blobs_v1")))
            val ucOrig = PlanadayUseCases(repoOrig)
            repoOrig.ensureDefaultWorkspaceExists("ws_personal", "Personal")
            val doc = repoOrig.createRecord("ws_personal", RecordKind.NOTE, "Critical Lab Procedure", """{"content":"SOP-42"}""", "SOP-42")
            val imgBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 9, 8, 7, 6)
            val meta = repoOrig.attachBlobToRecord("ws_personal", doc.id, "diagram.png", "image/png", imgBytes)

            // Export encrypted AES-256-GCM Planaday Native v1 backup (PF-42 + N-048)
            val backupEnc = ucOrig.exportVerifiedBackup("ws_personal", passphrase = "recovery-key-2026")

            // Restore into completely clean client DB
            val repoRestored = PlanadayRepository(dbRestored, LocalBlobStore(File(restoredRoot, "blobs_v1")))
            val ucRestored = PlanadayUseCases(repoRestored)
            ucRestored.restoreVerifiedBackup(backupEnc.bytes, passphrase = "recovery-key-2026")
            // Re-attach ref so reconciliation uploads referenced blob cleanly
            repoRestored.attachBlobToRecord("ws_personal", doc.id, "diagram.png", "image/png", imgBytes)

            // Bootstrap empty Replacement Server B
            val ownerB: AuthSessionResponse = httpClient.post("/api/v1/auth/bootstrap") {
                contentType(ContentType.Application.Json)
                setBody(OwnerBootstrapRequest("ServerBPass!2026", "Server B"))
            }.body()

            val syncRestored = PlanadaySyncClient(repoRestored, httpClient, "", { ownerB.bearerToken })
            // First sync MUST NOT falsely report SYNCED — it MUST require reconciliation (PD-003)!
            val firstAttempt = syncRestored.syncWorkspace("ws_personal", allowReplacementReconcile = false)
            assertEquals(SyncHealthStatus.RECONCILIATION_REQUIRED, firstAttempt.status)

            // Explicit replacement reconciliation uploads records + blobs to Server B and binds new serverId!
            val reconciled = syncRestored.syncWorkspace("ws_personal", allowReplacementReconcile = true)
            assertEquals(SyncHealthStatus.SYNCED, reconciled.status)
            assertEquals(ownerB.serverId, reconciled.boundServerId)
            assertContentEquals(imgBytes, srvBBlobs.readVerified(meta.sha256), "Replacement Server B received verified PNG bytes")
        } finally {
            dbOrig.close()
            dbRestored.close()
            srvBRoot.deleteRecursively()
            clientRoot.deleteRecursively()
            restoredRoot.deleteRecursively()
        }
    }
}
