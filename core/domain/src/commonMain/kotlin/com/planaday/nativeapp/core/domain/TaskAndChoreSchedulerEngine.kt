package com.planaday.nativeapp.core.domain

import com.planaday.nativeapp.core.model.*
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ScheduledBlockKind {
    COMPLETED_RECORDED,
    COMPLETED_ESTIMATED,
    PLANNED_MANUAL,
    PLANNED_AUTO,
}

data class ScheduledTaskSlot(
    val task: TaskItem,
    val startMin: Int,
    val endMin: Int,
    val kind: ScheduledBlockKind,
    val contextName: String? = null,
)

data class UnfitTaskExplanation(
    val task: TaskItem,
    val reason: String,
)

data class SchedulePlanResult(
    val dateIso: String,
    val slots: List<ScheduledTaskSlot>,
    val unfitTasks: List<UnfitTaskExplanation>,
    val learnedAveragesMin: Map<String, Int>,
)

data class TaskAgeInfo(
    val ageDays: Int,
    val overdueDays: Int,
    val rescheduleCount: Int,
)

/**
 * Constraint-aware Intelligent Task Calendar Engine (R-0027..R-0054, PF-11, PF-12, PF-49, N-011):
 * - Preserves original creation dates on carryover & tracks overdue days and reschedule count.
 * - Computes learned undistracted average durations per task type.
 * - Never auto-deletes conflicting user calendar events; explains infeasible tasks explicitly.
 * - Supports scheduling contexts (Work/Home/Lab/custom), travel buffers, manual locks, and preferred daily end time.
 */
object TaskSchedulerEngine {
    fun computeLearnedUndistractedAverages(tasks: List<TaskItem>): Map<String, Int> {
        val buckets = mutableMapOf<String, MutableList<Int>>()
        for (task in tasks) {
            val key = task.taskType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: continue
            val undistracted = task.completionLogs.filter { it.actualMinutes > 0 && !it.distracted }
            if (undistracted.isNotEmpty()) {
                buckets.getOrPut(key) { mutableListOf() }.addAll(undistracted.map { it.actualMinutes })
            } else if (task.completionLogs.isEmpty() && (task.actualMinutes ?: 0) > 0) {
                buckets.getOrPut(key) { mutableListOf() }.add(task.actualMinutes!!)
            }
        }
        return buckets.mapValues { (_, values) -> (values.sum().toDouble() / values.size).roundToInt() }
    }

    fun computeTaskAge(task: TaskItem, todayIso: String): TaskAgeInfo {
        val createdDate = task.createdAtUtc.take(10)
        val ageDays = max(0, CalendarDateMath.diffDays(createdDate, todayIso))
        val effectiveDue = task.dueDateIso ?: task.originalDueDateIso
        val overdue = if (task.status == RecordStatus.ACTIVE && effectiveDue != null && effectiveDue < todayIso) {
            max(0, CalendarDateMath.diffDays(effectiveDue, todayIso))
        } else 0
        return TaskAgeInfo(ageDays = ageDays, overdueDays = overdue, rescheduleCount = task.rescheduleCount)
    }

    /**
     * Carries over incomplete tasks whose doDate < todayIso to todayIso while strictly preserving
     * createdAtUtc and originalDueDateIso, and incrementing rescheduleCount (R-0038, R-0039).
     */
    fun carryOverIncompleteTasks(tasks: List<TaskItem>, todayIso: String, nowUtc: String): List<TaskItem> {
        return tasks.map { task ->
            val taskDo = task.doDateIso
            if (task.status == RecordStatus.ACTIVE && taskDo != null && taskDo < todayIso) {
                task.copy(
                    doDateIso = todayIso,
                    originalDueDateIso = task.originalDueDateIso ?: task.dueDateIso,
                    rescheduleCount = task.rescheduleCount + 1,
                    updatedAtUtc = nowUtc,
                )
            } else task
        }
    }

    fun buildDaySchedule(
        dateIso: String,
        nowMin: Int,
        preferredEndTimeLocal: String,
        allowPastEndTime: Boolean = false,
        tasks: List<TaskItem>,
        dayEvents: List<CalendarEvent>,
        contexts: List<SchedulingContext> = emptyList(),
        activeContextId: String? = null,
    ): SchedulePlanResult {
        val weekday = CalendarDateMath.weekdaySun0(dateIso)
        val endCutoff = if (allowPastEndTime) 1440 else max(nowMin + 15, CalendarDateMath.timeToMinutes(preferredEndTimeLocal))
        val learned = computeLearnedUndistractedAverages(tasks)
        val ctxMap = contexts.associateBy { it.id }

        val busy = mutableListOf<IntRange>()
        for (ev in dayEvents) {
            if (ev.allDay || ev.eventStatus == "canceled") continue
            val s = max(0, CalendarDateMath.timeToMinutes(ev.startTimeLocal ?: "09:00") - ev.buffers.prepMin - ev.buffers.travelMin)
            val e = min(1440, max(s + 15, CalendarDateMath.timeToMinutes(ev.endTimeLocal ?: "10:00") + ev.buffers.decompressMin))
            busy.add(s until e)
        }

        val slots = mutableListOf<ScheduledTaskSlot>()
        val unfit = mutableListOf<UnfitTaskExplanation>()

        // 1. Preserve completed tasks at historical placement
        val completedIds = tasks.filter { it.status == RecordStatus.DONE }.map { it.id }.toSet()
        for (t in tasks.filter { it.status == RecordStatus.DONE && (it.completedAtUtc?.startsWith(dateIso) == true || it.doDateIso == dateIso) }) {
            val dur = max(5, t.completionLogs.lastOrNull()?.actualMinutes ?: t.actualMinutes ?: t.estimateMin ?: 25)
            val startMin = 9 * 60
            val endMin = min(1440, startMin + dur)
            slots.add(
                ScheduledTaskSlot(
                    task = t,
                    startMin = startMin,
                    endMin = endMin,
                    kind = if (t.startedAtUtc != null) ScheduledBlockKind.COMPLETED_RECORDED else ScheduledBlockKind.COMPLETED_ESTIMATED,
                    contextName = t.contextId?.let { ctxMap[it]?.name },
                )
            )
            busy.add(startMin until endMin)
        }

        // 2. Sort active eligible tasks by priority, custom order, createdAt
        val eligible = tasks.filter { t ->
            val tDo = t.doDateIso
            t.status == RecordStatus.ACTIVE &&
                (activeContextId == null || t.contextId == null || t.contextId == activeContextId) &&
                (tDo == null || tDo <= dateIso)
        }.sortedWith(
            compareByDescending<TaskItem> { it.customPriorityRank ?: (it.priority.rank * 20) }
                .thenBy { it.sortOrder }
                .thenBy { it.createdAtUtc }
        )

        fun findSlot(earliest: Int, latestEnd: Int, duration: Int): Int? {
            val sortedBusy = busy.sortedBy { it.first }
            var cursor = ((earliest + 4) / 5) * 5
            for (b in sortedBusy) {
                if (b.last + 1 <= cursor) continue
                if (cursor + duration <= b.first) {
                    return if (cursor + duration <= latestEnd) cursor else null
                }
                cursor = max(cursor, ((b.last + 1 + 4) / 5) * 5)
            }
            return if (cursor + duration <= latestEnd) cursor else null
        }

        for (task in eligible) {
            // Respect incomplete dependencies (R-0036)
            val unresolvedDeps = task.blockedByIds.filter { it !in completedIds }
            if (unresolvedDeps.isNotEmpty()) {
                unfit.add(UnfitTaskExplanation(task, "Blocked by incomplete dependencies: ${unresolvedDeps.joinToString()}"))
                continue
            }

            val typeKey = task.taskType?.trim()?.lowercase()
            val duration = max(5, task.estimateMin ?: typeKey?.let { learned[it] } ?: 25)
            val ctx = task.contextId?.let { ctxMap[it] }

            val manualStart = task.manualStartTimeLocal
            if (manualStart != null) {
                val s = CalendarDateMath.timeToMinutes(manualStart)
                val e = min(1440, s + duration)
                slots.add(ScheduledTaskSlot(task, s, e, ScheduledBlockKind.PLANNED_MANUAL, ctx?.name))
                busy.add(s until e)
                continue
            }

            var winStart = nowMin
            var winEnd = endCutoff
            if (ctx != null) {
                if (ctx.daysOfWeek.isNotEmpty() && weekday !in ctx.daysOfWeek) {
                    unfit.add(UnfitTaskExplanation(task, "Context '${ctx.name}' is not active on this day of week"))
                    continue
                }
                winStart = max(winStart, CalendarDateMath.timeToMinutes(ctx.startTimeLocal))
                winEnd = min(winEnd, CalendarDateMath.timeToMinutes(ctx.endTimeLocal))
            }

            if (winStart + duration > winEnd) {
                unfit.add(UnfitTaskExplanation(task, "Insufficient remaining window before preferred end time ($preferredEndTimeLocal)"))
                continue
            }

            val chosen = findSlot(winStart, winEnd, duration)
            if (chosen == null) {
                unfit.add(UnfitTaskExplanation(task, "No contiguous $duration-min free gap available without conflicting with events"))
                continue
            }
            slots.add(ScheduledTaskSlot(task, chosen, chosen + duration, ScheduledBlockKind.PLANNED_AUTO, ctx?.name))
            busy.add(chosen until (chosen + duration))
        }

        return SchedulePlanResult(
            dateIso = dateIso,
            slots = slots.sortedBy { it.startMin },
            unfitTasks = unfit,
            learnedAveragesMin = learned,
        )
    }
}

data class ChoreFreshnessState(
    val freshnessPct: Int,
    val status: String, // fresh | due_soon | due_today | overdue | paused
    val effectiveDueDateIso: String,
)

data class RoomHealthSummary(
    val room: String,
    val healthPct: Int,
    val overdueCount: Int,
    val dueTodayCount: Int,
    val totalChores: Int,
)

/**
 * Completion-driven chore recurrence, freshness replay, room health & fairness engine (R-0214, R-0177).
 */
object ChoreEngine {
    fun seasonForDate(dateIso: String): String = when (LocalDate.parse(dateIso).monthNumber) {
        3, 4, 5 -> "spring"
        6, 7, 8 -> "summer"
        9, 10, 11 -> "fall"
        else -> "winter"
    }

    fun computeNextDueDate(chore: ChoreItem, completedDateIso: String): String {
        val interval = chore.intervalDays.coerceIn(1, 3650)
        return when (chore.mode) {
            ChoreMode.AFTER_COMPLETION, ChoreMode.USAGE_COUNTER ->
                CalendarDateMath.addDays(completedDateIso, interval)
            ChoreMode.FIXED -> {
                var next = CalendarDateMath.addDays(chore.nextDueDateIso, interval)
                while (next <= completedDateIso) next = CalendarDateMath.addDays(next, interval)
                next
            }
            ChoreMode.WEEKDAYS -> {
                var cand = CalendarDateMath.addDays(completedDateIso, 1)
                while (CalendarDateMath.weekdaySun0(cand) in setOf(0, 6)) {
                    cand = CalendarDateMath.addDays(cand, 1)
                }
                cand
            }
            ChoreMode.MONTHLY_NTH -> {
                val d = LocalDate.parse(completedDateIso)
                val nextM = if (d.monthNumber == 12) 1 else d.monthNumber + 1
                val nextY = if (d.monthNumber == 12) d.year + 1 else d.year
                RecurrenceEngine.nthWeekdayOfMonth(nextY, nextM, 6, -1)
                    ?: CalendarDateMath.addDays(completedDateIso, 30)
            }
            ChoreMode.ELAPSED_HOURS -> {
                val days = max(1, ceil((chore.elapsedHoursTarget ?: 48) / 24.0).toInt())
                CalendarDateMath.addDays(completedDateIso, days)
            }
            ChoreMode.SEASONAL -> {
                var cand = CalendarDateMath.addDays(completedDateIso, interval)
                if (chore.activeSeasons.isNotEmpty()) {
                    for (i in 0 until 366) {
                        if (seasonForDate(cand) in chore.activeSeasons) break
                        cand = CalendarDateMath.addDays(cand, 1)
                    }
                }
                cand
            }
        }
    }

    fun freshnessAtDate(chore: ChoreItem, asOfDateIso: String): ChoreFreshnessState {
        if (chore.paused) return ChoreFreshnessState(100, "paused", chore.nextDueDateIso)
        val prior = chore.completions
            .filter { it.completedDateIso <= asOfDateIso }
            .maxByOrNull { it.completedDateIso }
        val lastDone = prior?.completedDateIso ?: chore.lastCompletedDateIso
        val interval = max(1, chore.intervalDays)
        var effectiveDue = if (lastDone != null && chore.mode == ChoreMode.AFTER_COMPLETION) {
            CalendarDateMath.addDays(lastDone, interval)
        } else {
            chore.nextDueDateIso
        }
        val snoozed = chore.snoozedUntilIso
        if (snoozed != null && snoozed > effectiveDue) {
            effectiveDue = snoozed
        }
        val daysUntil = CalendarDateMath.diffDays(asOfDateIso, effectiveDue)
        return when {
            daysUntil < 0 -> {
                val frac = min(1.0, abs(daysUntil).toDouble() / interval)
                ChoreFreshnessState(max(0, (25.0 * (1.0 - frac)).roundToInt()), "overdue", effectiveDue)
            }
            daysUntil == 0 -> ChoreFreshnessState(30, "due_today", effectiveDue)
            else -> {
                val pct = ((daysUntil.toDouble() / interval) * 100.0).roundToInt().coerceIn(35, 100)
                ChoreFreshnessState(pct, if (daysUntil <= 1) "due_soon" else "fresh", effectiveDue)
            }
        }
    }

    fun computeRoomHealth(chores: List<ChoreItem>, asOfDateIso: String): List<RoomHealthSummary> {
        return chores.groupBy { it.room.ifBlank { "General" } }.map { (room, list) ->
            val states = list.map { freshnessAtDate(it, asOfDateIso) }
            RoomHealthSummary(
                room = room,
                healthPct = if (states.isEmpty()) 100 else (states.sumOf { it.freshnessPct }.toDouble() / states.size).roundToInt(),
                overdueCount = states.count { it.status == "overdue" },
                dueTodayCount = states.count { it.status == "due_today" },
                totalChores = list.size,
            )
        }.sortedBy { it.healthPct }
    }
}
