package com.planaday.nativeapp.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class NotePresentationMode {
    SCROLL,
    PAGES,
    SPREAD,
    NOTEBOOK,
    PLANNER,
}

@Serializable
enum class PaperStyle {
    BLANK,
    LINED,
    GRID,
    DOT,
}

@Serializable
data class NotebookItem(
    val id: String,
    val workspaceId: String,
    val title: String,
    val parentId: String? = null,
    val colorHex: String = "#A97434",
    val sortOrder: Int = 0,
    val rev: Long = 1L,
    val createdAtUtc: String,
    val updatedAtUtc: String,
)

@Serializable
data class NoteItem(
    val id: String,
    val workspaceId: String,
    val notebookId: String,
    val parentId: String? = null,
    val isSection: Boolean = false,
    val title: String,
    val content: String = "",
    val writtenForDateIso: String? = null,
    val sortOrder: Int = 0,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val presentationMode: NotePresentationMode = NotePresentationMode.SCROLL,
    val paperStyle: PaperStyle = PaperStyle.BLANK,
    val attachmentHashes: List<String> = emptyList(),
    val rev: Long = 1L,
    val createdAtUtc: String,
    val updatedAtUtc: String,
)

@Serializable
data class BathroomLogEntry(
    val id: String,
    val timeLocal: String,
    val kind: String, // urination | bowel
    val note: String? = null,
)

@Serializable
data class DiaryEntry(
    val id: String,
    val workspaceId: String,
    val dateIso: String,
    val mood: String? = null,
    val ratings: Map<String, Double> = emptyMap(),
    val ratingScaleMin: Int = 1,
    val ratingScaleMax: Int = 10,
    val sleepHours: Double? = null,
    val waterCups: Int? = null,
    val summary: String = "",
    val narrative: String = "",
    val dailyNoteSections: Map<String, String> = emptyMap(),
    val bathroomUrinations: Int = 0,
    val bathroomBowelMovements: Int = 0,
    val bathroomLogs: List<BathroomLogEntry> = emptyList(),
    val photoHashes: List<String> = emptyList(),
    val rev: Long = 1L,
    val createdAtUtc: String,
    val updatedAtUtc: String,
)

@Serializable
enum class ClockDesign {
    ANALOG_TRADITIONAL,
    ANALOG_MINIMAL,
    DIGITAL,
    RETRO_DIGITAL,
    MODERN_MINIMAL,
    DECORATIVE,
}

@Serializable
data class AlarmItem(
    val id: String,
    val workspaceId: String,
    val label: String,
    val timeLocal: String, // HH:MM
    val timeZone: String = "UTC",
    val repeatDays: List<Int> = emptyList(), // 0=Sun..6=Sat
    val enabled: Boolean = true,
    val snoozeMinutes: Int = 10,
    val volumePct: Int = 80,
    val soundKey: String = "default",
    val linkedRecordId: String? = null,
    val rev: Long = 1L,
)

@Serializable
data class TimerItem(
    val id: String,
    val workspaceId: String,
    val label: String,
    val durationSeconds: Long,
    val kind: String = "timer", // timer | pomodoro | interval
    val linkedRecordId: String? = null,
)

@Serializable
data class TimeLedgerEntry(
    val id: String,
    val workspaceId: String,
    val sourceType: String, // timer | pomodoro | interval | stopwatch | manual
    val label: String,
    val linkedRecordId: String? = null,
    val startedAtUtc: String,
    val endedAtUtc: String,
    val activeSeconds: Long,
    val dateIso: String,
    val lapsMs: List<Long> = emptyList(),
)

@Serializable
enum class PlannerBindingStyle {
    SPIRAL,
    RING,
    BOOK,
    NONE,
}

@Serializable
data class PlannerBook(
    val id: String,
    val workspaceId: String,
    val title: String,
    val frontCoverStyle: String = "linen_amber",
    val backCoverStyle: String = "linen_amber",
    val bindingStyle: PlannerBindingStyle = PlannerBindingStyle.SPIRAL,
    val twoPageSpread: Boolean = true,
    val pageWidthPt: Double = 612.0,
    val pageHeightPt: Double = 792.0,
    val rev: Long = 1L,
)

@Serializable
data class PlannerPage(
    val id: String,
    val bookId: String,
    val workspaceId: String,
    val pageNumber: Int,
    val title: String,
    val layoutTemplate: String = "lined", // blank | lined | grid | dot | daily | weekly | us_50_states_map
    val lineSpacingPt: Double = 24.0,
    val sideTabLabel: String? = null,
    val sideTabColorHex: String? = null,
    val textContent: String = "",
    val linkedDateIso: String? = null,
    val rev: Long = 1L,
)

@Serializable
data class PlannerBookmark(
    val id: String,
    val bookId: String,
    val workspaceId: String,
    val pageId: String,
    val label: String,
    val colorHex: String = "#A97434",
    val sortOrder: Int = 0,
)

@Serializable
data class DecorItem(
    val id: String,
    val workspaceId: String,
    val targetPageOrSpreadId: String,
    val kind: String, // sticker | washi | sticky_note | stamp | ink | shape
    val layerAboveText: Boolean = true,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotationDeg: Double = 0.0,
    val opacity: Double = 1.0,
    val zIndex: Int = 0,
    val locked: Boolean = false,
    val stickerKey: String? = null,
    val customBlobSha256: String? = null,
    val text: String? = null,
    val colorHex: String? = null,
    val strokePointsJson: String? = null,
)
