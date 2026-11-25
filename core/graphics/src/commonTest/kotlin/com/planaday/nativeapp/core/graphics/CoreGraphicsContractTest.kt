package com.planaday.nativeapp.core.graphics

import com.planaday.nativeapp.core.model.PlannerBookmark
import com.planaday.nativeapp.core.model.PlannerPage
import kotlin.test.*

class CoreGraphicsContractTest {
    @Test
    fun tenThousandObjectSpatialIndexHitTestAndSvgExport() {
        val index = SpatialIndex2D(cellSize = 256.0)
        for (i in 0 until 10_000) {
            val col = i % 100
            val row = i / 100
            index.upsert(
                CanvasNode(
                    id = "node_$i",
                    kind = if (i % 2 == 0) "sticky" else "ellipse",
                    bounds = Bounds2D(col * 120.0, row * 120.0, 90.0, 70.0),
                    zIndex = i,
                    text = "Item $i",
                )
            )
        }
        assertEquals(10_000, index.size())
        val hit = index.hitTest(worldX = 5 * 120.0 + 30.0, worldY = 10 * 120.0 + 25.0, zoomScale = 0.5)
        assertEquals("node_${10 * 100 + 5}", hit?.id)

        val viewport = index.queryViewport(Bounds2D(0.0, 0.0, 300.0, 300.0))
        assertTrue(viewport.size in 4..16, "Viewport culling should return small subset, got ${viewport.size}")

        val svg = VectorSvgExporter.exportWhiteboardSvg(
            title = "Architecture Board",
            nodes = viewport.take(2),
            connectors = listOf(CanvasConnector("c1", viewport[0].id, viewport[1].id)),
        )
        assertTrue(svg.contains("<svg"))
        assertTrue(svg.contains("<line"))
        assertTrue(svg.contains("</svg>"))
    }

    @Test
    fun r0072FiveThousandBookmarksAndDynamicPlannerSideTabsAnd50States() {
        assertEquals(50, PhysicalPlannerEngine.US_50_STATES.size)

        val pages = (1..10).map { n ->
            PlannerPage("p$n", "book1", "ws1", n, "Page $n", sideTabLabel = if (n in listOf(2, 5, 9)) "Tab $n" else null)
        }
        val tabsAtSpread5 = PhysicalPlannerEngine.computeDynamicSideTabs(pages, currentSpreadLeftPageNum = 5)
        assertEquals(listOf("LEFT", "LEFT", "RIGHT"), tabsAtSpread5.map { it.side })

        // R-0072: 5,000 bookmarks paginated cleanly
        val bookmarks = (1..5_000).map { i ->
            PlannerBookmark("bm_$i", "book1", "ws1", "p${(i % 10) + 1}", "Milestone Bookmark #$i", sortOrder = i)
        }
        val slice = PhysicalPlannerEngine.sliceBookmarks(bookmarks, query = "4999", offset = 0, limit = 10)
        assertEquals(1, slice.size)
        assertEquals("bm_4999", slice.single().id)
    }
}
