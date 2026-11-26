package com.planaday.nativeapp.core.domain

import com.planaday.nativeapp.core.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ============================================================================
// 1. Project Gantt, Dependencies & Critical Path Engine (PF-13, N-031, R-0288)
// ============================================================================

data class ProjectTaskNode(
    val id: String,
    val title: String,
    val durationDays: Int,
    val dependsOnIds: List<String> = emptyList(),
    val assignedHoursPerDay: Double = 4.0,
)

data class GanttScheduledNode(
    val id: String,
    val title: String,
    val earliestStartDay: Int,
    val earliestFinishDay: Int,
    val latestStartDay: Int,
    val latestFinishDay: Int,
    val slackDays: Int,
    val isCritical: Boolean,
)

data class GanttScheduleAnalysis(
    val totalDurationDays: Int,
    val criticalPathIds: List<String>,
    val nodes: List<GanttScheduledNode>,
    val peakDailyHours: Double,
    val overCapacityDays: List<Int>,
)

object ProjectGanttCriticalPathEngine {
    fun analyze(nodes: List<ProjectTaskNode>, dailyCapacityHours: Double = 8.0): GanttScheduleAnalysis {
        val byId = nodes.associateBy { it.id }
        val edges = nodes.associate { it.id to it.dependsOnIds.filter { dep -> dep in byId } }
        require(!HierarchyGraphEngine.hasDependencyCycle(edges)) {
            "Project dependency cycle detected — cannot compute critical path."
        }

        // Topological sort (Kahn)
        val inDegree = nodes.associate { it.id to edges[it.id].orEmpty().size }.toMutableMap()
        val dependents = mutableMapOf<String, MutableList<String>>()
        for ((id, deps) in edges) {
            for (d in deps) dependents.getOrPut(d) { mutableListOf() }.add(id)
        }
        val queue = ArrayDeque<String>()
        inDegree.filterValues { it == 0 }.keys.sorted().forEach { queue.addLast(it) }
        val topo = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            topo.add(u)
            for (v in dependents[u].orEmpty()) {
                val next = (inDegree[v] ?: 1) - 1
                inDegree[v] = next
                if (next == 0) queue.addLast(v)
            }
        }

        val es = mutableMapOf<String, Int>()
        val ef = mutableMapOf<String, Int>()
        for (id in topo) {
            val n = byId[id]!!
            val start = edges[id].orEmpty().maxOfOrNull { ef[it] ?: 0 } ?: 0
            es[id] = start
            ef[id] = start + max(1, n.durationDays)
        }
        val projectDuration = ef.values.maxOrNull() ?: 0

        val ls = mutableMapOf<String, Int>()
        val lf = mutableMapOf<String, Int>()
        for (id in topo.asReversed()) {
            val n = byId[id]!!
            val finish = dependents[id].orEmpty().minOfOrNull { ls[it] ?: projectDuration } ?: projectDuration
            lf[id] = finish
            ls[id] = finish - max(1, n.durationDays)
        }

        val dailyLoad = mutableMapOf<Int, Double>()
        val scheduled = topo.map { id ->
            val n = byId[id]!!
            val eStart = es[id]!!
            val eFinish = ef[id]!!
            val lStart = ls[id]!!
            val lFinish = lf[id]!!
            val slack = lStart - eStart
            for (day in eStart until eFinish) {
                dailyLoad[day] = (dailyLoad[day] ?: 0.0) + n.assignedHoursPerDay
            }
            GanttScheduledNode(id, n.title, eStart, eFinish, lStart, lFinish, slack, slack == 0)
        }

        return GanttScheduleAnalysis(
            totalDurationDays = projectDuration,
            criticalPathIds = scheduled.filter { it.isCritical }.map { it.id },
            nodes = scheduled,
            peakDailyHours = dailyLoad.values.maxOrNull() ?: 0.0,
            overCapacityDays = dailyLoad.filterValues { it > dailyCapacityHours }.keys.sorted(),
        )
    }
}

// ============================================================================
// 2. Shared Availability, What-If Schedules & Estimate Calibration (PF-12, PF-49, N-012, N-013)
// ============================================================================

data class ResourceAvailabilityWindow(
    val resourceId: String,
    val label: String,
    val busyIntervalsMin: List<IntRange>,
)

data class WhatIfScheduleComparison(
    val scenarioAPlaced: Int,
    val scenarioBPlaced: Int,
    val scenarioAUnfit: Int,
    val scenarioBUnfit: Int,
    val newlyPlacedInB: List<String>,
    val droppedInB: List<String>,
)

data class FocusCalibrationSummary(
    val totalSessions: Int,
    val undistractedSessions: Int,
    val distractedSessions: Int,
    val avgEstimatedMin: Double?,
    val avgActualMin: Double?,
    val meanEstimationBiasMin: Double?, // actual - estimated (+ means underestimating)
    val totalDistractionMin: Int,
)

object WhatIfScheduleAndCalibrationEngine {
    fun findSharedFreeSlots(
        resources: List<ResourceAvailabilityWindow>,
        dayStartMin: Int = 8 * 60,
        dayEndMin: Int = 18 * 60,
        requiredDurationMin: Int,
        travelBufferMin: Int = 0,
    ): List<IntRange> {
        val allBusy = resources.flatMap { r ->
            r.busyIntervalsMin.map { b ->
                max(dayStartMin, b.first - travelBufferMin) until min(dayEndMin, b.last + 1 + travelBufferMin)
            }
        }.filter { !it.isEmpty() }.sortedBy { it.first }

        val merged = mutableListOf<IntRange>()
        for (b in allBusy) {
            if (merged.isEmpty() || b.first > merged.last().last + 1) {
                merged.add(b)
            } else {
                val prev = merged.removeAt(merged.lastIndex)
                merged.add(prev.first..max(prev.last, b.last))
            }
        }

        val free = mutableListOf<IntRange>()
        var cursor = dayStartMin
        for (b in merged) {
            if (b.first - cursor >= requiredDurationMin) {
                free.add(cursor until b.first)
            }
            cursor = max(cursor, b.last + 1)
        }
        if (dayEndMin - cursor >= requiredDurationMin) {
            free.add(cursor until dayEndMin)
        }
        return free
    }

    fun compareWhatIf(planA: SchedulePlanResult, planB: SchedulePlanResult): WhatIfScheduleComparison {
        val idsA = planA.slots.map { it.task.id }.toSet()
        val idsB = planB.slots.map { it.task.id }.toSet()
        return WhatIfScheduleComparison(
            scenarioAPlaced = planA.slots.size,
            scenarioBPlaced = planB.slots.size,
            scenarioAUnfit = planA.unfitTasks.size,
            scenarioBUnfit = planB.unfitTasks.size,
            newlyPlacedInB = (idsB - idsA).sorted(),
            droppedInB = (idsA - idsB).sorted(),
        )
    }

    fun computeFocusCalibration(tasks: List<TaskItem>): FocusCalibrationSummary {
        val logs = tasks.flatMap { it.completionLogs }
        if (logs.isEmpty()) return FocusCalibrationSummary(0, 0, 0, null, null, null, 0)
        val undistracted = logs.filter { !it.distracted }
        val distracted = logs.filter { it.distracted }
        val withEst = logs.filter { (it.estimatedMinutes ?: 0) > 0 }
        val avgEst = if (withEst.isNotEmpty()) withEst.map { it.estimatedMinutes!!.toDouble() }.average() else null
        val avgAct = logs.map { it.actualMinutes.toDouble() }.average()
        val bias = if (withEst.isNotEmpty()) withEst.map { (it.actualMinutes - it.estimatedMinutes!!).toDouble() }.average() else null
        return FocusCalibrationSummary(
            totalSessions = logs.size,
            undistractedSessions = undistracted.size,
            distractedSessions = distracted.size,
            avgEstimatedMin = avgEst,
            avgActualMin = avgAct,
            meanEstimationBiasMin = bias,
            totalDistractionMin = logs.sumOf { it.distractionMinutes },
        )
    }
}

// ============================================================================
// 3. Kitchen (Scaling, FIFO/FEFO Pantry, Shared Shopping Deltas) & Finance Forecast
//    (PF-15, PF-16, N-021, N-022, N-023)
// ============================================================================

data class IngredientShortage(
    val canonicalName: String,
    val displayName: String,
    val requiredQty: Double,
    val availableQty: Double,
    val shortageQty: Double,
    val unit: String,
    val aisle: String,
)

data class BudgetForecastReport(
    val currency: String,
    val totalBudgetLimit: DecimalAmount,
    val totalSpentSoFar: DecimalAmount,
    val projectedMonthEndSpend: DecimalAmount,
    val remainingBudget: DecimalAmount,
    val projectedOverrun: Boolean,
)

object KitchenAndFinanceEngines {
    fun scaleRecipe(recipe: RecipeItem, targetServings: Double): RecipeItem {
        require(recipe.servings > 0.0 && targetServings > 0.0) { "Servings must be positive" }
        val factor = targetServings / recipe.servings
        return recipe.copy(
            servings = targetServings,
            ingredients = recipe.ingredients.map { it.copy(quantity = (it.quantity * factor * 100.0).roundToInt() / 100.0) },
        )
    }

    /** Allocates pantry lots strictly by FEFO (First Expired, First Out) and returns shortages for Groceries. */
    fun computeRecipeShortagesFefo(
        recipe: RecipeItem,
        pantryLots: List<PantryLot>,
        todayIso: String,
    ): Pair<List<Pair<PantryLot, Double>>, List<IngredientShortage>> {
        val allocations = mutableListOf<Pair<PantryLot, Double>>()
        val shortages = mutableListOf<IngredientShortage>()

        for (ing in recipe.ingredients) {
            val matching = pantryLots
                .filter { it.canonicalName.equals(ing.canonicalName, ignoreCase = true) && it.quantity > 0.0 }
                .filter { lot -> val exp = lot.expiryDateIso; exp == null || exp >= todayIso }
                .sortedBy { it.expiryDateIso ?: "9999-12-31" } // FEFO order

            var needed = ing.quantity
            var available = 0.0
            for (lot in matching) {
                available += lot.quantity
                if (needed > 0.0) {
                    val take = min(needed, lot.quantity)
                    allocations.add(lot to take)
                    needed -= take
                }
            }
            if (needed > 1e-6) {
                shortages.add(
                    IngredientShortage(
                        canonicalName = ing.canonicalName,
                        displayName = ing.name,
                        requiredQty = ing.quantity,
                        availableQty = available,
                        shortageQty = (needed * 100.0).roundToInt() / 100.0,
                        unit = ing.unit,
                        aisle = ing.aisle,
                    )
                )
            }
        }
        return allocations to shortages
    }

    /** Finds pantry lots expiring within [withinDays] (N-023). */
    fun expiringLots(pantryLots: List<PantryLot>, todayIso: String, withinDays: Int = 5): List<PantryLot> {
        val cutoff = CalendarDateMath.addDays(todayIso, withinDays)
        return pantryLots
            .filter { lot -> val exp = lot.expiryDateIso; exp != null && exp <= cutoff && lot.quantity > 0.0 }
            .sortedBy { it.expiryDateIso }
    }

    /** Conflict-safe household shopping quantity merge using delta additions relative to common ancestor (N-021). */
    fun mergeSharedShoppingQuantity(baseQty: Double, localQty: Double, remoteQty: Double): Double {
        val localDelta = localQty - baseQty
        val remoteDelta = remoteQty - baseQty
        return max(0.0, baseQty + localDelta + remoteDelta)
    }

    /** Exact-decimal budget burn-rate & month-end forecast (PF-16, N-022). */
    fun forecastMonthlyBudget(
        envelopes: List<BudgetEnvelope>,
        expenses: List<ExpenseItem>,
        currency: String,
        dayOfMonth: Int,
        daysInMonth: Int,
    ): BudgetForecastReport {
        val limit = ExactDecimal.sumAll(envelopes.filter { it.limitAmount.unitOrCurrency == currency }.map { it.limitAmount }, currency)
        val spent = ExactDecimal.sumAll(expenses.filter { it.amount.unitOrCurrency == currency }.map { it.amount }, currency)
        val spentScaled = ExactDecimal.toScaledUnits(spent.value)
        val effDay = dayOfMonth.coerceIn(1, max(1, daysInMonth))
        val projectedScaled = (spentScaled * daysInMonth.toLong()) / effDay.toLong()
        val projected = DecimalAmount(ExactDecimal.fromScaledUnits(projectedScaled, 2), currency)
        val remaining = ExactDecimal.subtract(limit, spent)
        val overrun = projectedScaled > ExactDecimal.toScaledUnits(limit.value)
        return BudgetForecastReport(currency, limit, spent, projected, remaining, overrun)
    }
}

// ============================================================================
// 4. Wellness Trends, Household Fairness, Travel Packs, Spaced Review (SM-2), Media
//    (PF-17, PF-18, PF-19, PF-20, PF-21, N-024, N-025)
// ============================================================================

data class WellnessTrendSummary(
    val entryCount: Int,
    val metricAverages: Map<String, Double>,
    val avgSleepHours: Double?,
    val avgWaterCups: Double?,
    val totalBathroomUrinations: Int,
    val totalBathroomBowel: Int,
)

data class HouseholdFairnessMemberScore(
    val memberId: String,
    val completedCount: Int,
    val weightedDifficultyPoints: Int,
    val sharePct: Double,
)

data class SpacedReviewCardState(
    val cardId: String,
    val prompt: String,
    val answer: String,
    val linkedNoteId: String? = null,
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val nextDueDateIso: String,
)

data class OfflineTravelPack(
    val tripId: String,
    val destination: String,
    val homeTimeZone: String,
    val destinationTimeZone: String,
    val departureDateIso: String,
    val returnDateIso: String,
    val packingChecklist: List<Pair<String, Boolean>>,
    val offlineGeneratedAtUtc: String,
)

object LifeWellnessAndLearningEngines {
    fun analyzeWellnessTrends(entries: List<DiaryEntry>): WellnessTrendSummary {
        if (entries.isEmpty()) return WellnessTrendSummary(0, emptyMap(), null, null, 0, 0)
        val metricBuckets = mutableMapOf<String, MutableList<Double>>()
        for (e in entries) {
            for ((k, v) in e.ratings) {
                metricBuckets.getOrPut(k) { mutableListOf() }.add(v)
            }
        }
        val sleeps = entries.mapNotNull { it.sleepHours }
        val waters = entries.mapNotNull { it.waterCups?.toDouble() }
        return WellnessTrendSummary(
            entryCount = entries.size,
            metricAverages = metricBuckets.mapValues { it.value.average() },
            avgSleepHours = if (sleeps.isNotEmpty()) sleeps.average() else null,
            avgWaterCups = if (waters.isNotEmpty()) waters.average() else null,
            totalBathroomUrinations = entries.sumOf { it.bathroomUrinations },
            totalBathroomBowel = entries.sumOf { it.bathroomBowelMovements },
        )
    }

    fun computeHouseholdFairness(chores: List<ChoreItem>): List<HouseholdFairnessMemberScore> {
        val pointsByMember = mutableMapOf<String, Int>()
        val countByMember = mutableMapOf<String, Int>()
        for (ch in chores) {
            for (comp in ch.completions) {
                val member = comp.assigneeId ?: "unassigned"
                countByMember[member] = (countByMember[member] ?: 0) + 1
                pointsByMember[member] = (pointsByMember[member] ?: 0) + ch.difficulty.coerceIn(1, 5)
            }
        }
        val totalPts = pointsByMember.values.sum().coerceAtLeast(1)
        return pointsByMember.keys.sorted().map { m ->
            val pts = pointsByMember[m] ?: 0
            HouseholdFairnessMemberScore(
                memberId = m,
                completedCount = countByMember[m] ?: 0,
                weightedDifficultyPoints = pts,
                sharePct = (pts.toDouble() / totalPts.toDouble()) * 100.0,
            )
        }
    }

    /** SM-2 spaced repetition review scheduling (PF-21, N-025). Quality: 0..5. */
    fun reviewCardSm2(card: SpacedReviewCardState, quality: Int, todayIso: String): SpacedReviewCardState {
        val q = quality.coerceIn(0, 5)
        val newEf = max(1.3, card.easeFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))
        val (newReps, newInterval) = if (q < 3) {
            0 to 1
        } else {
            val reps = card.repetitions + 1
            val interval = when (reps) {
                1 -> 1
                2 -> 6
                else -> (card.intervalDays * newEf).roundToInt().coerceAtLeast(1)
            }
            reps to interval
        }
        return card.copy(
            easeFactor = newEf,
            intervalDays = newInterval,
            repetitions = newReps,
            nextDueDateIso = CalendarDateMath.addDays(todayIso, newInterval),
        )
    }
}
