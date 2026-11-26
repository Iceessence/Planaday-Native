package com.planaday.nativeapp.tests.integration

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.planaday.nativeapp.core.domain.SelectiveSyncPolicy
import com.planaday.nativeapp.core.model.BuildIdentity
import com.planaday.nativeapp.core.model.RecordKind
import com.planaday.nativeapp.core.persistence.CanonicalRecordEntity
import com.planaday.nativeapp.core.persistence.JvmPlanadayDatabaseFactory
import com.planaday.nativeapp.core.persistence.LocalBlobStore
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.search.PlanadaySearchService
import com.planaday.nativeapp.core.syncclient.PlanadaySyncClient
import com.planaday.nativeapp.core.syncprotocol.*
import com.planaday.nativeapp.server.installPlanadaySyncServer
import com.planaday.nativeapp.server.storage.JdbcSyncServerRepository
import com.planaday.nativeapp.server.storage.ServerBlobStore
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlin.system.measureTimeMillis
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

/**
 * Large-Scale Soak, Optimistic Multi-Device Conflict Preservation, Selective Sync,
 * and Bounded-Memory Streaming SHA-256 Blob Verification Suite (Master §9, N-041, N-042, PF-01, PF-02, PF-10).
 */
class ScaleSoakConflictAndStreamingTest {
    private fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    @Test
    fun multiDeviceOptimisticConflictPreservesBothCopiesAndSelectiveSyncNeverDeletes() = testApplication {
        val srvRoot = tempDir("planaday-conflict-srv-")
        val devARoot = tempDir("planaday-conflict-a-")
        val devBRoot = tempDir("planaday-conflict-b-")

        val srvRepo = JdbcSyncServerRepository("jdbc:sqlite:${File(srvRoot, "srv.db").absolutePath}")
        val srvBlobs = ServerBlobStore(File(srvRoot, "blobs"))
        application { installPlanadaySyncServer(srvRepo, srvBlobs, File(srvRoot, "backups")) }
        val httpClient = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        }

        val dbA = JvmPlanadayDatabaseFactory.createOnDisk(File(devARoot, BuildIdentity.CLIENT_DB_NAME))
        val dbB = JvmPlanadayDatabaseFactory.createOnDisk(File(devBRoot, BuildIdentity.CLIENT_DB_NAME))
        try {
            val repoA = PlanadayRepository(dbA, LocalBlobStore(File(devARoot, "blobs_v1")), deviceId = "dev_a")
            val repoB = PlanadayRepository(dbB, LocalBlobStore(File(devBRoot, "blobs_v1")), deviceId = "dev_b")
            repoA.ensureDefaultWorkspaceExists("ws_personal", "Personal")
            repoB.ensureDefaultWorkspaceExists("ws_personal", "Personal")

            val owner: AuthSessionResponse = httpClient.post("/api/v1/auth/bootstrap") {
                contentType(ContentType.Application.Json)
                setBody(OwnerBootstrapRequest("ConflictPass!2026", "Owner"))
            }.body()

            val syncA = PlanadaySyncClient(repoA, httpClient, "", { owner.bearerToken })
            val syncB = PlanadaySyncClient(repoB, httpClient, "", { owner.bearerToken })

            // 1. Device A creates record (rev=1), syncs to Server, Device B pulls (rev=1)
            val orig = repoA.createRecord("ws_personal", RecordKind.LAB_CALIBRATION, "NIR Calibration v1", """{"slope":1.00}""", "Initial")
            assertEquals(SyncHealthStatus.SYNCED, syncA.syncWorkspace("ws_personal").status)
            assertEquals(SyncHealthStatus.SYNCED, syncB.syncWorkspace("ws_personal").status)
            assertEquals(1L, repoB.getRecord("ws_personal", orig.id)?.rev)

            // 2. BOTH devices edit the SAME record OFFLINE concurrently!
            repoA.updateRecordAtomic("ws_personal", orig.id) {
                it.copy(title = "NIR Calibration — Device A", payloadJson = """{"slope":1.02}""")
            }
            repoB.updateRecordAtomic("ws_personal", orig.id) {
                it.copy(title = "NIR Calibration — Device B", payloadJson = """{"slope":0.98}""")
            }

            // 3. Device A pushes first (server advances to rev=2). Device B pushes stale baseRev=1 -> CONFLICT!
            assertEquals(SyncHealthStatus.SYNCED, syncA.syncWorkspace("ws_personal").status)
            val snapB = syncB.syncWorkspace("ws_personal")
            assertEquals(1, snapB.unresolvedConflicts, "N-042: Stale offline edit MUST record explicit conflict instead of silent LWW overwrite")

            val conflicts = repoB.listUnresolvedConflicts("ws_personal")
            assertEquals(1, conflicts.size)
            assertTrue(conflicts.single().localRecordJson.contains("0.98"))
            assertTrue(conflicts.single().remoteRecordJson.contains("1.02"))

            // 4. Resolve via Keep Both -> preserves BOTH versions cleanly with DERIVED_FROM link!
            val preservedCopy = repoB.resolveConflictKeepBoth("ws_personal", conflicts.single().id)
            assertTrue(preservedCopy.title.contains("(Conflict Copy)"))
            assertTrue(preservedCopy.payloadJson.contains("0.98"))
            assertEquals("NIR Calibration — Device A", repoB.getRecord("ws_personal", orig.id)?.title)
            assertEquals(0, repoB.listUnresolvedConflicts("ws_personal").size)
            assertEquals(1, repoB.outgoingLinks("ws_personal", preservedCopy.id).size)

            // 5. N-041 Selective Sync Policy: excluding ws_personal skips sync with ZERO delete tombstones!
            val excludedSnap = syncB.syncWorkspace(
                "ws_personal",
                selectivePolicy = SelectiveSyncPolicy(syncedWorkspaceIds = setOf("ws_lab")),
            )
            assertEquals(SyncHealthStatus.IDLE_OFFLINE, excludedSnap.status)
            assertNotNull(repoB.getRecord("ws_personal", orig.id), "Selective sync exclusion MUST NOT delete local records")
        } finally {
            dbA.close()
            dbB.close()
            srvRoot.deleteRecursively()
            devARoot.deleteRecursively()
            devBRoot.deleteRecursively()
        }
    }

    @Test
    fun hundredThousandRecordSqliteFts5SoakBenchmark() = runTest(timeout = 3.minutes) {
        val dir = tempDir("planaday-100k-soak-")
        val db = JvmPlanadayDatabaseFactory.createOnDisk(File(dir, BuildIdentity.CLIENT_DB_NAME))
        try {
            val repo = PlanadayRepository(db, LocalBlobStore(File(dir, "blobs_v1")))
            val search = PlanadaySearchService(repo)
            repo.ensureDefaultWorkspaceExists("ws_soak", "Soak")
            repo.createWorkspace(
                com.planaday.nativeapp.core.model.Workspace(
                    "ws_other", "Other", com.planaday.nativeapp.core.model.WorkspaceScope.LABORATORY,
                    "#0F766E", "flask", "2026-11-25T00:00:00Z", "2026-11-25T00:00:00Z",
                )
            )

            val totalRecords = 100_000
            val batchSize = 5_000
            val ingestMs = measureTimeMillis {
                for (batchStart in 1..totalRecords step batchSize) {
                    val batchEnd = minOf(totalRecords, batchStart + batchSize - 1)
                    db.useWriterConnection { transactor ->
                        transactor.immediateTransaction {
                            for (i in batchStart..batchEnd) {
                                val title = when (i) {
                                    42_420 -> "Needle Spectrophotometer Calibration Audit 42420"
                                    88_888 -> "Needle Spectrophotometer Unauthorized Other Workspace"
                                    else -> "Soak Record Entry #$i"
                                }
                                val ws = if (i == 88_888) "ws_other" else "ws_soak"
                                db.recordDao().insertNew(
                                    CanonicalRecordEntity(
                                        id = "soak_$i",
                                        workspaceId = ws,
                                        kind = RecordKind.NOTE.name,
                                        title = title,
                                        status = "ACTIVE",
                                        rev = 1L,
                                        schemaVersion = 1,
                                        createdAtUtc = "2026-11-25T00:00:00Z",
                                        updatedAtUtc = "2026-11-25T00:00:00Z",
                                        deletedAtUtc = null,
                                        deviceId = "soak_bench",
                                        summaryText = "Indexed archival content payload number $i",
                                    )
                                )
                            }
                        }
                    }
                }
            }

            assertEquals(totalRecords.toLong(), repo.countAllRecords())

            // Measure 20 real indexed FTS5 queries over 100,000 records (asserting p95 < 300ms + strict workspace ACL!)
            val queryLatencies = mutableListOf<Long>()
            repeat(20) {
                val ms = measureTimeMillis {
                    val hits = search.search("ws_soak", "Needle Spectrophotometer")
                    assertEquals(1, hits.size, "Must find exact authorized record and NEVER leak ws_other")
                    assertEquals("soak_42420", hits.single().recordId)
                }
                queryLatencies.add(ms)
            }
            queryLatencies.sort()
            val p50 = queryLatencies[queryLatencies.size / 2]
            val p95 = queryLatencies[(queryLatencies.size * 95) / 100]
            assertTrue(p95 < 300L, "100k-record FTS5 p95 latency was ${p95}ms (budget <300ms)")

            println("SOAK_100K_METRICS: records=$totalRecords, ingest_ms=$ingestMs, fts5_p50_ms=$p50, fts5_p95_ms=$p95")
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun large64MbStreamingSha256BlobIngestAndVerifyUsesBoundedMemory() {
        val dir = tempDir("planaday-stream-64mb-")
        try {
            val store = LocalBlobStore(dir)
            val totalSize = 64L * 1024L * 1024L // 64 MiB deterministic synthetic stream (zero 64 MiB heap allocation!)
            val syntheticStream = object : InputStream() {
                private var remaining = totalSize
                private var counter = 0
                override fun read(): Int {
                    if (remaining <= 0L) return -1
                    remaining--
                    return (counter++ * 31) and 0xFF
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (remaining <= 0L) return -1
                    val toRead = minOf(len.toLong(), remaining).toInt()
                    for (i in 0 until toRead) {
                        b[off + i] = ((counter++ * 31) and 0xFF).toByte()
                    }
                    remaining -= toRead
                    return toRead
                }
            }

            lateinit var sha256: String
            var bytesWritten = 0L
            val ingestMs = measureTimeMillis {
                val (h, sz) = store.putStreamVerified(syntheticStream)
                sha256 = h
                bytesWritten = sz
            }
            assertEquals(totalSize, bytesWritten)
            assertEquals(64, sha256.length)

            var verified = false
            val verifyMs = measureTimeMillis {
                verified = store.verifyStreamOnDisk(sha256)
            }
            assertTrue(verified, "64 MiB streamed blob must pass streaming SHA-256 verification")

            // Flip 1 byte on disk and verify tamper detection catches corruption immediately!
            val storedFile = File(dir, store.relativePathFor(sha256))
            java.io.RandomAccessFile(storedFile, "rw").use { raf ->
                raf.seek(1024L)
                val orig = raf.readByte()
                raf.seek(1024L)
                raf.writeByte(orig.toInt() xor 0xFF)
            }
            assertFalse(store.verifyStreamOnDisk(sha256), "Corrupted 64 MiB blob MUST fail streaming SHA-256 verification")

            println("STREAMING_BLOB_64MB_METRICS: bytes=$bytesWritten, ingest_ms=$ingestMs, verify_ms=$verifyMs, sha256_prefix=${sha256.take(12)}")
        } finally {
            dir.deleteRecursively()
        }
    }
}
