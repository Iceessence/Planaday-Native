package com.planaday.nativeapp.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class WorkspaceRole {
    OWNER,
    EDITOR,
    VIEWER,
}

@Serializable
enum class WorkspaceScope {
    PERSONAL,
    HOUSEHOLD,
    LABORATORY,
    CREATIVE,
    OFFICE,
    PRIVATE_DIARY,
    CUSTOM,
}

@Serializable
enum class OperatingMode {
    OFFLINE_LOCAL,
    SELF_HOSTED_SYNC,
}

@Serializable
enum class SaveState {
    IDLE,
    EDITING,
    LOCALLY_SAVED,
    SAVE_FAILED,
    SYNC_PENDING,
    SYNCED,
    CONFLICT,
}

@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val scope: WorkspaceScope = WorkspaceScope.PERSONAL,
    val accentHex: String = "#A97434",
    val iconKey: String = "home",
    val createdAtUtc: String,
    val updatedAtUtc: String,
    val isDefault: Boolean = false,
)

@Serializable
data class WorkspaceMembership(
    val workspaceId: String,
    val principalId: String,
    val displayName: String,
    val role: WorkspaceRole,
    val grantedAtUtc: String,
    val revokedAtUtc: String? = null,
)

@Serializable
data class FamilyProfile(
    val id: String,
    val workspaceId: String,
    val name: String,
    val role: String = "member",
    val accentHex: String = "#3F6212",
)

@Serializable
data class DeviceIdentity(
    val deviceId: String,
    val installationId: String,
    val deviceName: String,
    val platform: String,
    val createdAtUtc: String,
)

@Serializable
data class WorkspaceScheduleRule(
    val id: String,
    val name: String,
    val workspaceId: String,
    val daysOfWeek: List<Int>, // 1=Mon..7=Sun (ISO-8601)
    val startTimeLocal: String, // HH:MM
    val endTimeLocal: String, // HH:MM
    val defaultRoute: String? = null,
    val hiddenRoutes: List<String> = emptyList(),
)

@Serializable
data class AppPreferences(
    val deviceId: String,
    val installationId: String,
    val activeWorkspaceId: String,
    val operatingMode: OperatingMode = OperatingMode.OFFLINE_LOCAL,
    val themeId: String = "warm_paper",
    val colorScheme: String = "system",
    val highContrast: Boolean = false,
    val density: String = "standard",
    val fontPairing: String = "editorial",
    val uiScalePct: Int = 100,
    val reducedMotion: Boolean = false,
    val rtlEnabled: Boolean = false,
    val weekStartsOnIsoDay: Int = 1,
    val defaultTimeZone: String = "UTC",
    val timeFormat24h: Boolean = false,
    val preferredDailyEndTime: String = "21:00",
    val maxFavorites: Int? = 3, // null = unlimited
    val autoCarryOverTasks: Boolean = true,
    val trashRetentionDays: Int = 30,
    val creationComplexity: String = "standard", // simple | standard | advanced
    val mobileBottomBarSlots: List<String> = listOf("today", "calendar", "tasks", "notes"),
    val hiddenRoutes: List<String> = emptyList(),
    val syncServerUrl: String? = null,
    val boundServerId: String? = null,
    val boundSyncEpoch: Long? = null,
    val restoredFromBackupAtUtc: String? = null,
    val onboardingCompleted: Boolean = false,
    val localAssistanceOptIn: Boolean = false,
)
