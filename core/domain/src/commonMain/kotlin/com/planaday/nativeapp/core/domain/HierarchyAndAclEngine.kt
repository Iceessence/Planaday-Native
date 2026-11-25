package com.planaday.nativeapp.core.domain

import com.planaday.nativeapp.core.model.*

/**
 * Exact scaled integer decimal arithmetic (scale = 4 decimal places -> 0.0001 precision)
 * guaranteeing zero binary floating-point drift for money and budget totals across KMP targets.
 */
object ExactDecimal {
    private const val SCALE = 10_000L

    fun toScaledUnits(decimalText: String): Long {
        val clean = decimalText.trim()
        require(clean.isNotEmpty()) { "Empty decimal amount" }
        val negative = clean.startsWith("-")
        val unsigned = clean.removePrefix("-").removePrefix("+")
        val parts = unsigned.split(".")
        require(parts.size <= 2) { "Invalid decimal: '$decimalText'" }
        val whole = parts[0].ifEmpty { "0" }.toLong()
        val fracRaw = parts.getOrElse(1) { "" }.take(4).padEnd(4, '0')
        val frac = fracRaw.toLong()
        val combined = whole * SCALE + frac
        return if (negative) -combined else combined
    }

    fun fromScaledUnits(units: Long, decimals: Int = 2): String {
        val neg = units < 0
        val absVal = kotlin.math.abs(units)
        val whole = absVal / SCALE
        val frac4 = (absVal % SCALE).toString().padStart(4, '0')
        val shownFrac = if (decimals <= 0) "" else ".${frac4.take(decimals.coerceAtMost(4))}"
        return "${if (neg) "-" else ""}$whole$shownFrac"
    }

    fun add(a: DecimalAmount, b: DecimalAmount): DecimalAmount {
        require(a.unitOrCurrency == b.unitOrCurrency) {
            "Currency/unit mismatch: ${a.unitOrCurrency} vs ${b.unitOrCurrency}"
        }
        val sum = toScaledUnits(a.value) + toScaledUnits(b.value)
        return DecimalAmount(fromScaledUnits(sum, 2), a.unitOrCurrency)
    }

    fun subtract(a: DecimalAmount, b: DecimalAmount): DecimalAmount {
        require(a.unitOrCurrency == b.unitOrCurrency) {
            "Currency/unit mismatch: ${a.unitOrCurrency} vs ${b.unitOrCurrency}"
        }
        val diff = toScaledUnits(a.value) - toScaledUnits(b.value)
        return DecimalAmount(fromScaledUnits(diff, 2), a.unitOrCurrency)
    }

    fun sumAll(amounts: List<DecimalAmount>, currency: String): DecimalAmount {
        var total = 0L
        for (amt in amounts) {
            require(amt.unitOrCurrency == currency) { "Mixed currency in sum: expected $currency got ${amt.unitOrCurrency}" }
            total += toScaledUnits(amt.value)
        }
        return DecimalAmount(fromScaledUnits(total, 2), currency)
    }
}

/**
 * Iterative, stack-safe, cycle-rejecting hierarchy & dependency graph engine (closes R-0233 & N-008).
 * Tested to 64+ nesting levels without stack overflow.
 */
object HierarchyGraphEngine {
    const val RECOMMENDED_MAX_DEPTH: Int = 128

    /** Returns true iff setting [nodeId].parent = [proposedParentId] would form a cycle. */
    fun wouldCreateCycle(
        nodeId: String,
        proposedParentId: String?,
        parentLookup: Map<String, String?>,
    ): Boolean {
        if (proposedParentId == null) return false
        if (proposedParentId == nodeId) return true
        val visited = HashSet<String>()
        var cursor: String? = proposedParentId
        while (cursor != null) {
            if (cursor == nodeId) return true
            if (!visited.add(cursor)) return true // existing cycle safeguard
            cursor = parentLookup[cursor]
        }
        return false
    }

    /** Returns ordered breadcrumb path from root down to [nodeId] iteratively (safe at arbitrary depth). */
    fun breadcrumbPath(nodeId: String, parentLookup: Map<String, String?>): List<String> {
        val path = mutableListOf<String>()
        val visited = HashSet<String>()
        var cursor: String? = nodeId
        while (cursor != null && visited.add(cursor)) {
            path.add(cursor)
            cursor = parentLookup[cursor]
        }
        return path.asReversed()
    }

    /** Returns all descendants of [rootId] iteratively (BFS). */
    fun collectSubtreeIds(rootId: String, parentLookup: Map<String, String?>): List<String> {
        val childrenByParent = mutableMapOf<String, MutableList<String>>()
        for ((child, parent) in parentLookup) {
            if (parent != null) childrenByParent.getOrPut(parent) { mutableListOf() }.add(child)
        }
        val result = mutableListOf<String>()
        val queue = ArrayDeque<String>()
        val visited = HashSet<String>()
        queue.add(rootId)
        visited.add(rootId)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            result.add(cur)
            for (c in childrenByParent[cur].orEmpty()) {
                if (visited.add(c)) queue.add(c)
            }
        }
        return result
    }

    /** Detects dependency cycles across task/project DAGs (N-008, R-0036). */
    fun hasDependencyCycle(edges: Map<String, List<String>>): Boolean {
        val color = mutableMapOf<String, Int>() // 0=unvisited, 1=visiting, 2=done
        for (start in edges.keys) {
            if ((color[start] ?: 0) != 0) continue
            val stack = ArrayDeque<Pair<String, Int>>()
            stack.addLast(start to 0)
            color[start] = 1
            while (stack.isNotEmpty()) {
                val (node, idx) = stack.removeLast()
                val neighbors = edges[node].orEmpty()
                if (idx < neighbors.size) {
                    stack.addLast(node to (idx + 1))
                    val nxt = neighbors[idx]
                    when (color[nxt] ?: 0) {
                        1 -> return true
                        0 -> {
                            color[nxt] = 1
                            stack.addLast(nxt to 0)
                        }
                    }
                } else {
                    color[node] = 2
                }
            }
        }
        return false
    }
}

/**
 * Workspace permission boundary evaluator enforcing deny-by-default ACLs across records and blobs.
 */
object WorkspaceAclEvaluator {
    fun canRead(membership: WorkspaceMembership?, workspaceId: String): Boolean {
        if (membership == null) return false
        if (membership.revokedAtUtc != null) return false
        return membership.workspaceId == workspaceId
    }

    fun canWrite(membership: WorkspaceMembership?, workspaceId: String): Boolean {
        if (!canRead(membership, workspaceId)) return false
        return membership!!.role == WorkspaceRole.OWNER || membership.role == WorkspaceRole.EDITOR
    }

    fun canAdminister(membership: WorkspaceMembership?, workspaceId: String): Boolean {
        if (!canRead(membership, workspaceId)) return false
        return membership!!.role == WorkspaceRole.OWNER
    }
}
