package com.planaday.nativeapp.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class RecurrenceFreq {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

@Serializable
enum class Feb29Policy {
    FEB28,
    MAR1,
    SKIP,
}

@Serializable
enum class RecurrenceMode {
    CALENDAR,
    COMPLETION,
}

@Serializable
data class RecurrenceRule(
    val freq: RecurrenceFreq,
    val interval: Int = 1,
    val byWeekday: List<Int> = emptyList(), // 0=Sun..6=Sat
    val bySetPos: Int? = null,
    val firstBusinessDay: Boolean = false,
    val skipWeekends: Boolean = false,
    val skipHolidays: Boolean = false,
    val untilDateIso: String? = null, // YYYY-MM-DD
    val count: Int? = null,
    val exceptionsIso: List<String> = emptyList(),
    val feb29Policy: Feb29Policy = Feb29Policy.FEB28,
    val mode: RecurrenceMode = RecurrenceMode.CALENDAR,
)

@Serializable
enum class CalendarOverlapPolicy {
    ALLOW,
    SIDE_BY_SIDE,
    AUTO_RESIZE,
    PREVENT,
    WARN,
    STACK,
    CONDENSED,
}

@Serializable
data class CalendarLayer(
    val id: String,
    val workspaceId: String,
    val name: String,
    val colorHex: String,
    val visible: Boolean = true,
)

@Serializable
data class CalendarViewConfig(
    val intervalMin: Int = 30, // supports 1,2,5,10,15,20,30,45,60,120,180,240 + custom
    val majorGridIntervalMin: Int = 60,
    val showMajorGrid: Boolean = true,
    val showMinorGrid: Boolean = true,
    val adaptiveText: Boolean = true,
    val visibleStartHour: Int = 6,
    val visibleEndHour: Int = 22,
    val overlapPolicy: CalendarOverlapPolicy = CalendarOverlapPolicy.SIDE_BY_SIDE,
)

@Serializable
data class EventBuffers(
    val prepMin: Int = 0,
    val travelMin: Int = 0,
    val decompressMin: Int = 0,
)

@Serializable
data class CalendarEvent(
    val id: String,
    val workspaceId: String,
    val calendarId: String,
    val title: String,
    val description: String = "",
    val startDateIso: String, // YYYY-MM-DD
    val endDateIso: String, // YYYY-MM-DD
    val startTimeLocal: String? = null, // HH:MM (null if allDay)
    val endTimeLocal: String? = null, // HH:MM
    val allDay: Boolean = false,
    val floating: Boolean = false,
    val timeZone: String = "UTC",
    val eventStatus: String = "confirmed", // confirmed | tentative | canceled
    val location: String? = null,
    val url: String? = null,
    val recurrence: RecurrenceRule? = null,
    val linkedActionId: String? = null,
    val icsUid: String? = null,
    val buffers: EventBuffers = EventBuffers(),
    val remindersMin: List<Int> = emptyList(),
    val rev: Long = 1L,
    val createdAtUtc: String,
    val updatedAtUtc: String,
)

@Serializable
data class SubtaskItem(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val children: List<SubtaskItem> = emptyList(),
)

@Serializable
data class DurationLog(
    val id: String,
    val startedAtUtc: String? = null,
    val completedAtUtc: String,
    val actualMinutes: Int,
    val estimatedMinutes: Int? = null,
    val breakMinutes: Int = 0,
    val distracted: Boolean = false,
    val distractionMinutes: Int = 0,
    val distractionNote: String? = null,
    val isEstimatedStart: Boolean = false,
)

@Serializable
data class SchedulingContext(
    val id: String,
    val workspaceId: String,
    val name: String,
    val daysOfWeek: List<Int>, // 0=Sun..6=Sat
    val startTimeLocal: String, // HH:MM
    val endTimeLocal: String, // HH:MM
)

@Serializable
data class TaskItem(
    val id: String,
    val workspaceId: String,
    val title: String,
    val notes: String = "",
    val status: RecordStatus = RecordStatus.ACTIVE,
    val priority: Priority = Priority.NONE,
    val customPriorityRank: Int? = null,
    val doDateIso: String? = null, // Planned work date
    val dueDateIso: String? = null, // Hard deadline
    val originalDueDateIso: String? = null,
    val rescheduleCount: Int = 0,
    val estimateMin: Int? = null,
    val actualMinutes: Int? = null,
    val energy: EnergyLevel = EnergyLevel.MEDIUM,
    val isFavorite: Boolean = false,
    val favoriteOrder: Int? = null,
    val sortOrder: Int = 0,
    val taskType: String? = null,
    val contextId: String? = null,
    val blockedByIds: List<String> = emptyList(),
    val manualStartTimeLocal: String? = null,
    val boardColumn: String = "backlog",
    val subtasks: List<SubtaskItem> = emptyList(),
    val completionLogs: List<DurationLog> = emptyList(),
    val recurrence: RecurrenceRule? = null,
    val startedAtUtc: String? = null,
    val completedAtUtc: String? = null,
    val rev: Long = 1L,
    val createdAtUtc: String,
    val updatedAtUtc: String,
)

@Serializable
enum class ChoreMode {
    AFTER_COMPLETION,
    FIXED,
    WEEKDAYS,
    MONTHLY_NTH,
    USAGE_COUNTER,
    ELAPSED_HOURS,
    SEASONAL,
}

@Serializable
data class ChoreCompletion(
    val id: String,
    val occurrenceId: String, // Deterministic hash(choreId, completedDateIso) for sync idempotency
    val completedAtUtc: String,
    val completedDateIso: String,
    val scheduledDueDateIso: String? = null,
    val wasOnTime: Boolean = true,
    val daysDelta: Int = 0,
    val assigneeId: String? = null,
    val minutesSpent: Int? = null,
    val distracted: Boolean = false,
    val notes: String? = null,
)

@Serializable
data class ChoreItem(
    val id: String,
    val workspaceId: String,
    val title: String,
    val room: String = "General",
    val mode: ChoreMode = ChoreMode.AFTER_COMPLETION,
    val intervalDays: Int = 7,
    val difficulty: Int = 2,
    val nextDueDateIso: String,
    val lastCompletedDateIso: String? = null,
    val snoozedUntilIso: String? = null,
    val paused: Boolean = false,
    val activeSeasons: List<String> = emptyList(),
    val elapsedHoursTarget: Int? = null,
    val assigneeId: String? = null,
    val completions: List<ChoreCompletion> = emptyList(),
    val rev: Long = 1L,
    val createdAtUtc: String,
    val updatedAtUtc: String,
)

@Serializable
data class HabitLogEntry(
    val dateIso: String,
    val value: Double,
    val excused: Boolean = false,
    val note: String? = null,
)

@Serializable
data class HabitItem(
    val id: String,
    val workspaceId: String,
    val title: String,
    val scheduleType: String = "daily", // daily | selected_days | weekly_target
    val selectedDays: List<Int> = emptyList(),
    val weeklyTarget: Int = 7,
    val unit: String = "times",
    val targetQuantity: Double = 1.0,
    val logs: Map<String, HabitLogEntry> = emptyMap(),
    val rev: Long = 1L,
    val createdAtUtc: String,
    val updatedAtUtc: String,
)

@Serializable
data class GoalMilestone(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val dueDateIso: String? = null,
)

@Serializable
data class GoalItem(
    val id: String,
    val workspaceId: String,
    val title: String,
    val description: String = "",
    val area: String = "Personal",
    val targetDateIso: String? = null,
    val milestones: List<GoalMilestone> = emptyList(),
    val linkedTaskIds: List<String> = emptyList(),
    val status: RecordStatus = RecordStatus.ACTIVE,
    val rev: Long = 1L,
    val createdAtUtc: String,
    val updatedAtUtc: String,
)
