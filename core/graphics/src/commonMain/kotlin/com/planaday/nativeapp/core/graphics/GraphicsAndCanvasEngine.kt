package com.planaday.nativeapp.core.graphics

import com.planaday.nativeapp.core.model.PlannerBookmark
import com.planaday.nativeapp.core.model.PlannerPage
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class Bounds2D(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    val maxX: Double get() = x + width
    val maxY: Double get() = y + height

    fun containsPoint(px: Double, py: Double, tolerance: Double = 0.0): Boolean =
        px >= (x - tolerance) && px <= (maxX + tolerance) && py >= (y - tolerance) && py <= (maxY + tolerance)

    fun intersects(other: Bounds2D): Boolean =
        x <= other.maxX && maxX >= other.x && y <= other.maxY && maxY >= other.y
}

data class CanvasNode(
    val id: String,
    val kind: String, // sticky | text | rect | ellipse | frame | sticker | photo | ink | linked_record
    val bounds: Bounds2D,
    val zIndex: Int = 0,
    val rotationDeg: Double = 0.0,
    val opacity: Double = 1.0,
    val locked: Boolean = false,
    val fillHex: String = "#FFF8E7",
    val strokeHex: String = "#334155",
    val text: String = "",
    val linkedRecordId: String? = null,
    val parentFrameId: String? = null,
    val inkPoints: List<Pair<Double, Double>> = emptyList(),
)

data class CanvasConnector(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val label: String = "",
    val strokeHex: String = "#475569",
)

/**
 * Uniform-grid 2D spatial index supporting fast (<2ms) viewport culling and zoom-independent hit testing
 * over 10,000+ vector canvas or planner decoration objects (R-0073..R-0080, R-0485..R-0494, PF-31).
 */
class SpatialIndex2D(private val cellSize: Double = 256.0) {
    private val nodesById = LinkedHashMap<String, CanvasNode>()
    private val buckets = HashMap<Long, MutableSet<String>>()

    private fun packCell(cx: Int, cy: Int): Long = (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFFFFFFL)

    private fun coveredCells(b: Bounds2D): List<Long> {
        val minCx = floor(b.x / cellSize).toInt()
        val maxCx = floor(b.maxX / cellSize).toInt()
        val minCy = floor(b.y / cellSize).toInt()
        val maxCy = floor(b.maxY / cellSize).toInt()
        val cells = ArrayList<Long>((maxCx - minCx + 1) * (maxCy - minCy + 1))
        for (cx in minCx..maxCx) {
            for (cy in minCy..maxCy) {
                cells.add(packCell(cx, cy))
            }
        }
        return cells
    }

    fun upsert(node: CanvasNode) {
        remove(node.id)
        nodesById[node.id] = node
        for (cell in coveredCells(node.bounds)) {
            buckets.getOrPut(cell) { LinkedHashSet() }.add(node.id)
        }
    }

    fun remove(id: String) {
        val existing = nodesById.remove(id) ?: return
        for (cell in coveredCells(existing.bounds)) {
            buckets[cell]?.remove(id)
        }
    }

    fun size(): Int = nodesById.size
    fun allNodes(): List<CanvasNode> = nodesById.values.toList()
    fun get(id: String): CanvasNode? = nodesById[id]

    /** Zoom-independent hit test returning top-most node (highest zIndex) at world (wx, wy). */
    fun hitTest(worldX: Double, worldY: Double, zoomScale: Double = 1.0, screenTolerancePx: Double = 6.0): CanvasNode? {
        val worldTolerance = screenTolerancePx / max(0.05, zoomScale)
        val probe = Bounds2D(
            x = worldX - worldTolerance,
            y = worldY - worldTolerance,
            width = worldTolerance * 2.0,
            height = worldTolerance * 2.0,
        )
        var best: CanvasNode? = null
        val visited = HashSet<String>()
        for (cell in coveredCells(probe)) {
            val ids = buckets[cell] ?: continue
            for (id in ids) {
                if (!visited.add(id)) continue
                val n = nodesById[id] ?: continue
                if (n.bounds.containsPoint(worldX, worldY, worldTolerance)) {
                    if (best == null || n.zIndex >= best.zIndex) best = n
                }
            }
        }
        return best
    }

    /** Fast viewport culling returning only nodes intersecting [viewport] sorted by zIndex. */
    fun queryViewport(viewport: Bounds2D): List<CanvasNode> {
        val seen = HashSet<String>()
        val result = mutableListOf<CanvasNode>()
        for (cell in coveredCells(viewport)) {
            val ids = buckets[cell] ?: continue
            for (id in ids) {
                if (!seen.add(id)) continue
                val n = nodesById[id] ?: continue
                if (n.bounds.intersects(viewport)) result.add(n)
            }
        }
        return result.sortedBy { it.zIndex }
    }
}

/**
 * Pure vector SVG exporter for Whiteboard & Physical Planner canvases (C-0066, R-0494).
 */
object VectorSvgExporter {
    private fun escXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun exportWhiteboardSvg(
        title: String,
        nodes: List<CanvasNode>,
        connectors: List<CanvasConnector>,
    ): String {
        val byId = nodes.associateBy { it.id }
        val minX = nodes.minOfOrNull { it.bounds.x }?.let { it - 40.0 } ?: 0.0
        val minY = nodes.minOfOrNull { it.bounds.y }?.let { it - 40.0 } ?: 0.0
        val maxX = nodes.maxOfOrNull { it.bounds.maxX }?.let { it + 40.0 } ?: 800.0
        val maxY = nodes.maxOfOrNull { it.bounds.maxY }?.let { it + 40.0 } ?: 600.0
        val w = max(200.0, maxX - minX)
        val h = max(200.0, maxY - minY)

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="$minX $minY $w $h" width="$w" height="$h">""").append('\n')
        sb.append("  <title>${escXml(title)}</title>\n")
        sb.append("""  <rect x="$minX" y="$minY" width="$w" height="$h" fill="#FAF7F2"/>""").append('\n')

        // Live connectors bound to source/target node centers
        for (conn in connectors) {
            val a = byId[conn.fromNodeId] ?: continue
            val b = byId[conn.toNodeId] ?: continue
            val x1 = a.bounds.x + a.bounds.width / 2.0
            val y1 = a.bounds.y + a.bounds.height / 2.0
            val x2 = b.bounds.x + b.bounds.width / 2.0
            val y2 = b.bounds.y + b.bounds.height / 2.0
            sb.append("""  <line x1="$x1" y1="$y1" x2="$x2" y2="$y2" stroke="${escXml(conn.strokeHex)}" stroke-width="2"/>""").append('\n')
        }

        for (n in nodes.sortedBy { it.zIndex }) {
            val b = n.bounds
            val cx = b.x + b.width / 2.0
            val cy = b.y + b.height / 2.0
            val transform = if (n.rotationDeg != 0.0) """ transform="rotate(${n.rotationDeg} $cx $cy)"""" else ""
            sb.append("""  <g opacity="${n.opacity}"$transform>""").append('\n')
            when (n.kind) {
                "ellipse" -> sb.append("""    <ellipse cx="$cx" cy="$cy" rx="${b.width / 2.0}" ry="${b.height / 2.0}" fill="${escXml(n.fillHex)}" stroke="${escXml(n.strokeHex)}" stroke-width="1.5"/>""").append('\n')
                "ink" -> if (n.inkPoints.size >= 2) {
                    val pts = n.inkPoints.joinToString(" ") { "${it.first},${it.second}" }
                    sb.append("""    <polyline points="$pts" fill="none" stroke="${escXml(n.strokeHex)}" stroke-width="2.5" stroke-linecap="round"/>""").append('\n')
                }
                else -> sb.append("""    <rect x="${b.x}" y="${b.y}" width="${b.width}" height="${b.height}" rx="8" fill="${escXml(n.fillHex)}" stroke="${escXml(n.strokeHex)}" stroke-width="1.5"/>""").append('\n')
            }
            if (n.text.isNotBlank()) {
                sb.append("""    <text x="${b.x + 10}" y="${b.y + 24}" font-family="sans-serif" font-size="14" fill="#1E293B">${escXml(n.text)}</text>""").append('\n')
            }
            sb.append("  </g>\n")
        }
        sb.append("</svg>\n")
        return sb.toString()
    }
}

/**
 * Physical-Style Digital Planner spread navigation, dynamic left/right side tabs,
 * interactive 50-state US map reference, and paginated bookmark navigation (R-0055..R-0072).
 */
object PhysicalPlannerEngine {
    val US_50_STATES: List<Pair<String, String>> = listOf(
        "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas", "CA" to "California",
        "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware", "FL" to "Florida", "GA" to "Georgia",
        "HI" to "Hawaii", "ID" to "Idaho", "IL" to "Illinois", "IN" to "Indiana", "IA" to "Iowa",
        "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana", "ME" to "Maine", "MD" to "Maryland",
        "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota", "MS" to "Mississippi", "MO" to "Missouri",
        "MT" to "Montana", "NE" to "Nebraska", "NV" to "Nevada", "NH" to "New Hampshire", "NJ" to "New Jersey",
        "NM" to "New Mexico", "NY" to "New York", "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio",
        "OK" to "Oklahoma", "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island", "SC" to "South Carolina",
        "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah", "VT" to "Vermont",
        "VA" to "Virginia", "WA" to "Washington", "WV" to "West Virginia", "WI" to "Wisconsin", "WY" to "Wyoming",
    )

    data class DynamicTabPlacement(
        val pageId: String,
        val pageNumber: Int,
        val label: String,
        val side: String, // LEFT | RIGHT
    )

    /**
     * Computes dynamic left/right side tab positions as the user flips through a two-page spread (R-0068):
     * tabs on pages <= currentLeftPage shift to the LEFT edge; tabs on pages > currentLeftPage shift to the RIGHT edge.
     */
    fun computeDynamicSideTabs(
        pages: List<PlannerPage>,
        currentSpreadLeftPageNum: Int,
    ): List<DynamicTabPlacement> {
        return pages
            .filter { !it.sideTabLabel.isNullOrBlank() }
            .sortedBy { it.pageNumber }
            .map { page ->
                DynamicTabPlacement(
                    pageId = page.id,
                    pageNumber = page.pageNumber,
                    label = page.sideTabLabel!!,
                    side = if (page.pageNumber <= currentSpreadLeftPageNum) "LEFT" else "RIGHT",
                )
            }
    }

    /** Virtualized pagination slice supporting 5,000+ planner bookmarks cleanly (R-0072). */
    fun sliceBookmarks(
        bookmarks: List<PlannerBookmark>,
        query: String = "",
        offset: Int = 0,
        limit: Int = 50,
    ): List<PlannerBookmark> {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) bookmarks else bookmarks.filter { it.label.lowercase().contains(q) }
        val start = min(max(0, offset), filtered.size)
        val end = min(filtered.size, start + max(1, limit))
        return filtered.subList(start, end)
    }
}
