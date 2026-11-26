package com.planaday.nativeapp.core.domain

import com.planaday.nativeapp.core.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// ============================================================================
// 5. Laboratory Operations, Pricing, Custody, Capacity & Uncertainty Engine
//    (PF-23, PF-24, PF-25, PF-33, PF-56, N-026..N-030, R-0133..R-0171, R-0330..R-0373)
// ============================================================================

data class LabPricingRule(
    val id: String,
    val clientName: String?, // null = default rate card
    val commodity: String?,
    val methodCode: String,
    val unitPrice: DecimalAmount,
    val turnaroundDays: Int,
)

data class LabQuoteResult(
    val clientName: String,
    val commodity: String,
    val lineItems: List<Pair<String, DecimalAmount>>,
    val totalAmount: DecimalAmount,
    val maxTurnaroundDays: Int,
)

data class UncertaintyComponent(
    val name: String,
    val standardUncertainty: Double,
    val sensitivityCoeff: Double = 1.0,
)

data class UncertaintyBudgetResult(
    val combinedStandardUncertainty: Double,
    val coverageFactorK: Double,
    val expandedUncertainty: Double,
    val methodVersion: String,
    val nonRegulatoryNotice: String,
)

object LaboratoryOperationsEngine {
    const val NON_REGULATORY_NOTICE: String =
        "Operational laboratory tracking & reference worksheet only — NOT an ISO/IEC 17025 or 21 CFR Part 11 accredited LIMS."

    private val ALLOWED_TRANSITIONS: Map<String, Set<String>> = mapOf(
        "received" to setOf("prepared", "in_progress", "subcontracted", "archived"),
        "prepared" to setOf("in_progress", "subcontracted"),
        "in_progress" to setOf("qc_review", "completed", "subcontracted"),
        "subcontracted" to setOf("qc_review", "completed"),
        "qc_review" to setOf("completed", "in_progress"),
        "completed" to setOf("archived"),
    )

    /** Configurable sample workflow transition with immutable append-only custody sign-off (N-028, PF-23). */
    fun transitionSampleWithCustody(
        sample: LabSample,
        targetStatus: String,
        actor: String,
        location: String,
        timestampUtc: String,
        notes: String = "",
    ): LabSample {
        require(actor.isNotBlank()) { "Authorized actor sign-off required for sample custody transition." }
        val allowed = ALLOWED_TRANSITIONS[sample.status].orEmpty()
        require(targetStatus in allowed) {
            "Disallowed sample transition '${sample.status}' -> '$targetStatus' (allowed: $allowed)"
        }
        val event = LabCustodyEvent(
            id = "cust_${sample.custodyEvents.size + 1}_${sample.id.take(8)}",
            timestampUtc = timestampUtc,
            actor = actor,
            action = targetStatus.uppercase(),
            location = location,
            notes = notes,
        )
        return sample.copy(
            status = targetStatus,
            custodyEvents = sample.custodyEvents + event,
            rev = sample.rev + 1L,
        )
    }

    /** Client + Commodity + Method specific pricing resolver using ExactDecimal (R-0143..R-0146, R-0339..R-0344). */
    fun computeClientQuote(
        clientName: String,
        commodity: String,
        methodCodes: List<String>,
        rules: List<LabPricingRule>,
        currency: String = "USD",
    ): LabQuoteResult {
        val items = mutableListOf<Pair<String, DecimalAmount>>()
        var maxTat = 0
        for (method in methodCodes) {
            val matched = rules.firstOrNull {
                it.methodCode.equals(method, true) &&
                    it.clientName.equals(clientName, true) &&
                    it.commodity.equals(commodity, true)
            } ?: rules.firstOrNull {
                it.methodCode.equals(method, true) &&
                    it.clientName.equals(clientName, true) &&
                    it.commodity == null
            } ?: rules.firstOrNull {
                it.methodCode.equals(method, true) &&
                    it.clientName == null &&
                    it.commodity.equals(commodity, true)
            } ?: rules.firstOrNull {
                it.methodCode.equals(method, true) && it.clientName == null && it.commodity == null
            } ?: throw IllegalArgumentException("No pricing rule for method '$method'")

            items.add(method to matched.unitPrice)
            maxTat = max(maxTat, matched.turnaroundDays)
        }
        val total = ExactDecimal.sumAll(items.map { it.second }, currency)
        return LabQuoteResult(clientName, commodity, items, total, maxTat)
    }

    /** Identifies low-stock and soon-expiring lab reagents/consumables (N-027, PF-25). */
    fun auditLabInventoryReorders(items: List<LabInventoryItem>, todayIso: String, expiryWarnDays: Int = 30): List<LabInventoryItem> {
        val cutoff = CalendarDateMath.addDays(todayIso, expiryWarnDays)
        return items.filter { item ->
            val exp = item.expiryDateIso
            item.quantityOnHand <= item.reorderThreshold ||
                (exp != null && exp <= cutoff)
        }
    }

    /** GUM-style combined & expanded uncertainty worksheet reference calculator (PF-24, PF-56, N-026). */
    fun computeUncertaintyBudget(
        components: List<UncertaintyComponent>,
        coverageFactorK: Double = 2.0,
        methodVersion: String,
    ): UncertaintyBudgetResult {
        require(components.isNotEmpty()) { "At least one uncertainty component required" }
        val sumSq = components.sumOf {
            val contrib = it.sensitivityCoeff * it.standardUncertainty
            contrib * contrib
        }
        val uc = sqrt(sumSq)
        return UncertaintyBudgetResult(
            combinedStandardUncertainty = uc,
            coverageFactorK = coverageFactorK,
            expandedUncertainty = uc * coverageFactorK,
            methodVersion = methodVersion,
            nonRegulatoryNotice = NON_REGULATORY_NOTICE,
        )
    }
}

// ============================================================================
// 6. Game Dev Deterministic Balance Simulator & Asset License Registry (PF-26, PF-27, N-032, N-034)
// ============================================================================

data class CombatantStats(
    val name: String,
    val maxHp: Double,
    val attack: Double,
    val defense: Double,
    val critChance: Double = 0.1,
    val critMultiplier: Double = 1.5,
)

data class BalanceSimulationResult(
    val seed: Long,
    val trials: Int,
    val attackerWinRate: Double,
    val avgTurnsToDefeat: Double,
    val avgRemainingHp: Double,
)

data class AssetLicenseEntry(
    val assetId: String,
    val name: String,
    val sha256: String,
    val author: String,
    val licenseSpdx: String, // e.g. CC0-1.0, MIT, Proprietary-Owned
    val sourceUrl: String? = null,
    val commercialAllowed: Boolean,
)

object GameBalanceSimulatorEngine {
    /** Deterministic XorShift64* PRNG guaranteeing identical simulation output across Android & Windows for the same seed. */
    private class SeededRng(seedIn: Long) {
        private var state: Long = if (seedIn == 0L) -0x61c8864680b583ebL else seedIn
        fun nextDouble(): Double {
            var x = state
            x = x xor (x ushr 12)
            x = x xor (x shl 25)
            x = x xor (x ushr 27)
            state = x
            val raw = (x * 0x2545F4914F6CDD1DL) ushr 11
            return raw.toDouble() / (1L shl 53).toDouble()
        }
    }

    fun simulateCombat(attacker: CombatantStats, defender: CombatantStats, trials: Int = 200, seed: Long = 42L): BalanceSimulationResult {
        val rng = SeededRng(seed)
        var wins = 0
        var totalTurns = 0
        var remHpSum = 0.0
        repeat(trials) {
            var aHp = attacker.maxHp
            var dHp = defender.maxHp
            var turns = 0
            while (aHp > 0.0 && dHp > 0.0 && turns < 200) {
                turns++
                val aCrit = if (rng.nextDouble() < attacker.critChance) attacker.critMultiplier else 1.0
                val dCrit = if (rng.nextDouble() < defender.critChance) defender.critMultiplier else 1.0
                val aDmg = max(1.0, (attacker.attack * aCrit) - defender.defense * 0.5)
                dHp -= aDmg
                if (dHp <= 0.0) break
                val dDmg = max(1.0, (defender.attack * dCrit) - attacker.defense * 0.5)
                aHp -= dDmg
            }
            if (dHp <= 0.0) {
                wins++
                remHpSum += max(0.0, aHp)
            }
            totalTurns += turns
        }
        return BalanceSimulationResult(
            seed = seed,
            trials = trials,
            attackerWinRate = wins.toDouble() / trials.toDouble(),
            avgTurnsToDefeat = totalTurns.toDouble() / trials.toDouble(),
            avgRemainingHp = if (wins > 0) remHpSum / wins else 0.0,
        )
    }

    fun xpCurveTable(levels: Int, baseXp: Double = 100.0, exponent: Double = 1.5): List<Pair<Int, Long>> =
        (1..levels).map { lvl -> lvl to (baseXp * lvl.toDouble().pow(exponent)).toLong() }
}

// ============================================================================
// 7. Custom Schema Validator, Universal Templates, Pivot Reports, Relationship Explorer,
//    Opt-In Local Assistance, Extension Capabilities & Selective Sync Policy
//    (PF-14, PF-35, PF-36, PF-39, PF-41, PF-43, PF-44, PF-45, N-002..N-047)
// ============================================================================

data class CustomRecordValidationResult(
    val valid: Boolean,
    val errors: List<String>,
    val computedValues: Map<String, Double>,
)

object CustomSchemaEngine {
    fun validateAndCompute(
        schema: CustomSchemaDef,
        values: Map<String, String>,
        formulaEvaluator: (String, Map<String, Double>) -> Double,
    ): CustomRecordValidationResult {
        val errors = mutableListOf<String>()
        val numericVars = mutableMapOf<String, Double>()

        for (field in schema.fields) {
            val raw = values[field.key]?.trim().orEmpty()
            if (field.required && raw.isEmpty() && field.fieldType != CustomFieldType.COMPUTED_FORMULA) {
                errors.add("Required field '${field.label}' is missing.")
                continue
            }
            if (raw.isEmpty()) continue
            when (field.fieldType) {
                CustomFieldType.NUMBER, CustomFieldType.DECIMAL -> {
                    val num = raw.toDoubleOrNull()
                    if (num == null) {
                        errors.add("Field '${field.label}' must be numeric.")
                    } else {
                        field.minNumber?.let { if (num < it) errors.add("Field '${field.label}' below minimum $it.") }
                        field.maxNumber?.let { if (num > it) errors.add("Field '${field.label}' above maximum $it.") }
                        numericVars[field.key] = num
                    }
                }
                CustomFieldType.SINGLE_SELECT -> {
                    if (field.options.isNotEmpty() && raw !in field.options) {
                        errors.add("Field '${field.label}' value '$raw' not in allowed options.")
                    }
                }
                CustomFieldType.BOOLEAN -> {
                    if (raw.lowercase() !in setOf("true", "false")) errors.add("Field '${field.label}' must be true/false.")
                }
                else -> Unit
            }
        }

        val computed = mutableMapOf<String, Double>()
        for (field in schema.fields.filter { it.fieldType == CustomFieldType.COMPUTED_FORMULA }) {
            val expr = field.formulaExpression ?: continue
            runCatching { formulaEvaluator(expr, numericVars) }
                .onSuccess {
                    computed[field.key] = it
                    numericVars[field.key] = it
                }
                .onFailure { errors.add("Computed field '${field.label}' error: ${it.message}") }
        }
        return CustomRecordValidationResult(errors.isEmpty(), errors, computed)
    }
}

data class UniversalTemplatePackage(
    val id: String,
    val name: String,
    val author: String,
    val schemaVersion: Int = 1,
    val parameters: List<String>,
    val titlePattern: String,
    val bodyPattern: String,
    val targetKind: RecordKind,
)

object UniversalTemplateEngine {
    fun instantiateSafe(template: UniversalTemplatePackage, params: Map<String, String>): Pair<String, String> {
        require(template.schemaVersion == 1) { "Unsupported template schema v${template.schemaVersion}" }
        var title = template.titlePattern
        var body = template.bodyPattern
        for (p in template.parameters) {
            val safeVal = (params[p] ?: "").replace("<script", "[blocked]", ignoreCase = true)
            title = title.replace("{{$p}}", safeVal)
            body = body.replace("{{$p}}", safeVal)
        }
        return title.trim() to body
    }
}

data class PivotBucket(
    val dimensionValue: String,
    val count: Int,
    val numericSum: Double,
    val numericAvg: Double?,
    val drillDownRecordIds: List<String>,
)

object UniversalPivotReportEngine {
    fun pivotByKindAndStatus(
        records: List<CanonicalRecord>,
        authorizedWorkspaceId: String,
        numericExtractor: (CanonicalRecord) -> Double? = { null },
    ): List<PivotBucket> {
        val scoped = records.filter { it.workspaceId == authorizedWorkspaceId && it.deletedAtUtc == null }
        return scoped.groupBy { "${it.kind.name}:${it.status.name}" }
            .entries
            .sortedBy { it.key }
            .map { (key, list) ->
                val nums = list.mapNotNull(numericExtractor)
                PivotBucket(
                    dimensionValue = key,
                    count = list.size,
                    numericSum = nums.sum(),
                    numericAvg = if (nums.isNotEmpty()) nums.average() else null,
                    drillDownRecordIds = list.map { it.id },
                )
            }
    }
}

object RelationshipGraphExplorer {
    /** Computes N-hop authorized relationship neighborhood strictly within [authorizedWorkspaceId] (PF-43). */
    fun exploreNeighborhood(
        startRecordId: String,
        authorizedWorkspaceId: String,
        relations: List<RecordRelation>,
        maxHops: Int = 2,
    ): Set<String> {
        val scoped = relations.filter { it.workspaceId == authorizedWorkspaceId }
        val adj = mutableMapOf<String, MutableList<String>>()
        for (r in scoped) {
            adj.getOrPut(r.sourceId) { mutableListOf() }.add(r.targetId)
            adj.getOrPut(r.targetId) { mutableListOf() }.add(r.sourceId)
        }
        val visited = LinkedHashSet<String>()
        val queue = ArrayDeque<Pair<String, Int>>()
        visited.add(startRecordId)
        queue.addLast(startRecordId to 0)
        while (queue.isNotEmpty()) {
            val (cur, dist) = queue.removeFirst()
            if (dist >= maxHops) continue
            for (nxt in adj[cur].orEmpty()) {
                if (visited.add(nxt)) queue.addLast(nxt to (dist + 1))
            }
        }
        return visited
    }
}

data class LocalAssistanceSuggestion(
    val id: String,
    val title: String,
    val explanation: String,
    val requiresUserApproval: Boolean = true, // NEVER auto-commits consequential writes!
)

/**
 * Owner-controlled local assistance & privacy-preserving ranking engine (PF-35, N-036, N-037).
 * Strictly opt-in, 100% local deterministic fallback, zero external AI required, 1-click reset.
 */
class LocalAssistanceEngine {
    private val topicWeights = mutableMapOf<String, Int>()

    fun recordUserPreferenceSignal(tag: String, optInEnabled: Boolean) {
        if (!optInEnabled) return
        val clean = tag.trim().lowercase()
        if (clean.isNotEmpty()) topicWeights[clean] = (topicWeights[clean] ?: 0) + 1
    }

    fun resetLearning() {
        topicWeights.clear()
    }

    fun suggestDailyPriorities(tasks: List<TaskItem>, optInEnabled: Boolean): List<LocalAssistanceSuggestion> {
        if (!optInEnabled) return emptyList()
        return tasks.filter { it.status == RecordStatus.ACTIVE }
            .sortedByDescending { t ->
                val base = t.priority.rank * 10
                val learned = t.taskType?.lowercase()?.let { topicWeights[it] ?: 0 } ?: 0
                base + learned
            }
            .take(3)
            .map { t ->
                LocalAssistanceSuggestion(
                    id = "sug_${t.id}",
                    title = "Prioritize: ${t.title}",
                    explanation = "Ranked locally by priority=${t.priority.name} and local preference weight (no external AI).",
                    requiresUserApproval = true,
                )
            }
    }
}

data class ExtensionCapabilityManifest(
    val extensionId: String,
    val version: String,
    val declaredCapabilities: Set<String>, // e.g. READ_TEMPLATES, EXPORT_FORMAT — NEVER HOST_CODE_EXEC!
    val requestsHostExecution: Boolean = false,
)

object ExtensionCapabilityValidator {
    private val ALLOWED_CAPABILITIES = setOf("READ_TEMPLATES", "CUSTOM_THEME", "EXPORT_FORMAT", "REPORT_WIDGET", "CALENDAR_FEED_READ")

    fun validate(manifest: ExtensionCapabilityManifest): Pair<Boolean, String> {
        if (manifest.requestsHostExecution) {
            return false to "Rejected: arbitrary host code execution is prohibited (N-038)."
        }
        val invalid = manifest.declaredCapabilities - ALLOWED_CAPABILITIES
        if (invalid.isNotEmpty()) {
            return false to "Rejected undeclared/unsafe capabilities: ${invalid.joinToString()}"
        }
        return true to "Valid declarative extension manifest (${manifest.extensionId}@${manifest.version})"
    }
}

data class SelectiveSyncPolicy(
    val syncedWorkspaceIds: Set<String>,
    val maxAutoDownloadAttachmentBytes: Long = 10L * 1024L * 1024L,
) {
    /** N-041 invariant: filtering out a workspace or large blob NEVER generates a DELETE tombstone! */
    fun shouldSyncWorkspace(workspaceId: String): Boolean = syncedWorkspaceIds.isEmpty() || workspaceId in syncedWorkspaceIds
    fun shouldAutoDownloadBlob(byteSize: Long): Boolean = byteSize <= maxAutoDownloadAttachmentBytes
}
