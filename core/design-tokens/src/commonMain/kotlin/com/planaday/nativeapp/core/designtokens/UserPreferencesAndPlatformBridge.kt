package com.planaday.nativeapp.core.designtokens

import kotlinx.serialization.Serializable

/**
 * Persisted cross-restart user appearance, workspace layout, dashboard, and sync preferences (Update 2 §7, §26, §28).
 * Stored in SQLite `app_preferences` and observed reactively across all open desktop windows.
 */
@Serializable
data class UserWorkspacePreferences(
    val themeMode: PlanadayThemeMode = PlanadayThemeMode.WARM_PAPER_LIGHT,
    val accentHex: String = "#9A6525",
    val fontScale: Float = 1.0f,
    val density: PlanadayDensity = PlanadayDensity.STANDARD,
    val sidebarWidthDp: Float = 264f,
    val showSidebar: Boolean = true,
    val showInspector: Boolean = false,
    val zenMode: Boolean = false,
    val dashboardProfile: String = "Personal", // Personal | Laboratory | Creative | Executive
    val enabledDashboardWidgets: List<String> = listOf(
        "quick_capture", "schedule", "tasks", "habits", "chores", "notes", "goals", "finance", "lab", "kitchen"
    ),
    val hiddenRouteIds: Set<String> = emptySet(),
    val pinnedRouteIds: List<String> = listOf("today", "calendar", "tasks", "notes", "spreadsheets", "lab"),
    val syncServerUrl: String = "http://127.0.0.1:8420",
    val allowDevelopmentHttp: Boolean = false,
    val autoSyncEnabled: Boolean = false,
    val selectiveSyncWorkspaceIds: Set<String> = emptySet(),
    val maxAutoBlobMb: Int = 15,
    val showDeveloperDiagnostics: Boolean = false,
)

/**
 * Platform file-dialog bridge so both Windows Desktop (Swing/AWT FileDialog) and Android (SAF ActivityResult)
 * perform real user file open/save without hardcoded paths.
 */
interface NativeFileDialogBridge {
    fun pickFileToRead(title: String, extensions: List<String> = emptyList()): Pair<String, ByteArray>?
    fun pickFileToSave(title: String, suggestedFilename: String, bytes: ByteArray): String?
}
