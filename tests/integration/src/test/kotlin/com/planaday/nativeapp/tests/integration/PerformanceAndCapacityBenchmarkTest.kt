package com.planaday.nativeapp.tests.integration

import com.planaday.nativeapp.core.documents.LargeDocumentAnalyzer
import com.planaday.nativeapp.core.documents.LargeDocumentBuffer
import com.planaday.nativeapp.core.domain.HierarchyGraphEngine
import com.planaday.nativeapp.core.graphics.Bounds2D
import com.planaday.nativeapp.core.graphics.CanvasNode
import com.planaday.nativeapp.core.graphics.SpatialIndex2D
import com.planaday.nativeapp.core.math.SpreadsheetFormulaEngine
import com.planaday.nativeapp.core.model.BuildIdentity
import com.planaday.nativeapp.core.model.RecordKind
import com.planaday.nativeapp.core.model.SheetCellInput
import com.planaday.nativeapp.core.model.WorksheetModel
import com.planaday.nativeapp.core.persistence.JvmPlanadayDatabaseFactory
import com.planaday.nativeapp.core.persistence.LocalBlobStore
import com.planaday.nativeapp.core.persistence.PlannerBookmarkEntity
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.search.PlanadaySearchService
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.system.measureTimeMillis
import kotlin.test.*

/**
 * Real measured performance & capacity benchmarks on actual compiled engines and Bundled SQLite
 * (Master §9, R-0072, R-0233, R-0436, PD-010, PF-07, PF-10, PF-31).
 */
class PerformanceAndCapacityBenchmarkTest {
    @Test
    fun benchmarkCoreEnginesAndCapacityGates() = runTest {
        // 1. PD-010: 100,000-word multiline document stats & localized edit
        val largeDoc = buildString(700_000) {
            repeat(20_000) { append("alpha beta gamma delta epsilon\n") }
        }
        var wordCount = 0
        val statsMs = measureTimeMillis {
            wordCount = LargeDocumentAnalyzer.computeStatsSinglePass(largeDoc).wordCount
        }
        val buf = LargeDocumentBuffer(largeDoc)
        val editMs = measureTimeMillis {
            buf.insertAt(300_000, "LIVE_IME_EDIT ")
        }
        assertEquals(100_000, wordCount)
        assertTrue(statsMs < 250, "100k-word O(n) stats took ${statsMs}ms (budget <250ms)")
        assertTrue(editMs < 50, "100k-word localized edit took ${editMs}ms (budget <50ms)")

        // 2. R-0436: 10,000 sparse spreadsheet cells + aggregate formulas
        val cells = LinkedHashMap<String, SheetCellInput>(10_010)
        for (r in 1..2_000) {
            cells["A$r"] = SheetCellInput(r.toString())
            cells["B$r"] = SheetCellInput((r * 2).toString())
            cells["C$r"] = SheetCellInput("=A$r+B$r")
            cells["D$r"] = SheetCellInput("=C$r*2")
            cells["E$r"] = SheetCellInput("=D$r-A$r")
        }
        cells["F1"] = SheetCellInput("=SUM(A1:A2000)")
        val sheetMs = measureTimeMillis {
            val out = SpreadsheetFormulaEngine.evaluateWorkbook(listOf(WorksheetModel("s1", "Large", 2000, 10, cells = cells)))
            assertEquals("2001000", out["s1"]!!["F1"]!!.value.displayString())
        }
        assertTrue(sheetMs < 5000, "10k sparse cells eval took ${sheetMs}ms")

        // 3. PF-31: 10,000 canvas objects SpatialIndex2D build + 100 hit tests
        val spatial = SpatialIndex2D(256.0)
        val buildCanvasMs = measureTimeMillis {
            for (i in 0 until 10_000) {
                val col = i % 100
                val row = i / 100
                spatial.upsert(CanvasNode("c_$i", "sticky", Bounds2D(col * 120.0, row * 120.0, 90.0, 70.0), zIndex = i))
            }
        }
        val hit100Ms = measureTimeMillis {
            repeat(100) { k ->
                val hit = spatial.hitTest((k % 100) * 120.0 + 20.0, (k / 100) * 120.0 + 20.0, zoomScale = 1.0)
                assertNotNull(hit)
            }
        }
        assertTrue(hit100Ms < 200, "100 spatial hit tests over 10k objects took ${hit100Ms}ms")

        // 4. R-0233: 64-level deep hierarchy & 10,000-node tree traversal
        val parents = LinkedHashMap<String, String?>(10_005)
        parents["root"] = null
        for (d in 1..64) parents["chain_$d"] = if (d == 1) "root" else "chain_${d - 1}"
        for (i in 65..10_000) parents["leaf_$i"] = "chain_${(i % 64) + 1}"
        val treeMs = measureTimeMillis {
            assertEquals(65, HierarchyGraphEngine.breadcrumbPath("chain_64", parents).size)
            assertTrue(HierarchyGraphEngine.wouldCreateCycle("chain_1", "chain_64", parents))
            assertEquals(10_001, HierarchyGraphEngine.collectSubtreeIds("root", parents).size)
        }
        assertTrue(treeMs < 500, "10k-node / 64-depth tree ops took ${treeMs}ms")

        // 5. R-0072 + PF-10: 5,000 relational SQLite planner bookmarks + 2,500 FTS5 indexed records search
        val dir = Files.createTempDirectory("planaday-bench-").toFile()
        val db = JvmPlanadayDatabaseFactory.createOnDisk(File(dir, BuildIdentity.CLIENT_DB_NAME))
        try {
            val repo = PlanadayRepository(db, LocalBlobStore(File(dir, "blobs_v1")))
            val search = PlanadaySearchService(repo)
            repo.ensureDefaultWorkspaceExists("ws_bench", "Bench")

            val bmList = (1..5_000).map { i ->
                PlannerBookmarkEntity("bm_$i", "book_1", "ws_bench", "p_${i % 100}", "Bookmark Section $i", "#A97434", i)
            }
            val bmInsertMs = measureTimeMillis { db.plannerBookmarkDao().upsertAll(bmList) }
            val bmQueryMs = measureTimeMillis {
                val hits = db.plannerBookmarkDao().queryPage("ws_bench", "book_1", "%4995%", 25, 0)
                assertEquals(1, hits.size)
            }
            assertEquals(5_000, db.plannerBookmarkDao().countForBook("ws_bench", "book_1"))
            assertTrue(bmQueryMs < 100, "5,000 bookmarks query took ${bmQueryMs}ms (budget <100ms)")

            val ftsSeedMs = measureTimeMillis {
                for (i in 1..1_000) {
                    val label = if (i == 777) "Spectrometer NIR Calibration Target 777" else "Record Item #$i"
                    db.recordDao().insertNew(
                        com.planaday.nativeapp.core.persistence.CanonicalRecordEntity(
                            id = "rec_$i", workspaceId = "ws_bench", kind = RecordKind.NOTE.name,
                            title = label, status = "ACTIVE", rev = 1L, schemaVersion = 1,
                            createdAtUtc = "2026-11-25T00:00:00Z", updatedAtUtc = "2026-11-25T00:00:00Z",
                            deletedAtUtc = null, deviceId = "bench", summaryText = "Indexed searchable content $i",
                        )
                    )
                }
            }
            val ftsQueryMs = measureTimeMillis {
                val hits = search.search("ws_bench", "Spectrometer Calibration")
                assertEquals(1, hits.size)
                assertEquals("rec_777", hits.single().recordId)
            }
            assertTrue(ftsQueryMs < 300, "FTS5 indexed search took ${ftsQueryMs}ms (budget <300ms)")

            println(
                "BENCHMARK_METRICS: " +
                    "100k_word_stats_ms=$statsMs, 100k_word_edit_ms=$editMs, " +
                    "10k_sparse_sheet_ms=$sheetMs, 10k_canvas_build_ms=$buildCanvasMs, 100_canvas_hittests_ms=$hit100Ms, " +
                    "64depth_10k_tree_ms=$treeMs, 5k_bookmarks_insert_ms=$bmInsertMs, 5k_bookmarks_query_ms=$bmQueryMs, " +
                    "1k_fts5_seed_ms=$ftsSeedMs, fts5_query_ms=$ftsQueryMs"
            )
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
