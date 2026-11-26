package com.planaday.nativeapp.core.model

import kotlinx.serialization.Serializable

/**
 * Authoritative single-source release and protocol metadata shared across Android,
 * Windows Desktop, and the optional Ktor sync server (closes C-0061, PD-017, Update 2 §36).
 * Populated directly from live Git commit SHA via Gradle build metadata generation.
 */
@Serializable
data class BuildIdentity(
    val productName: String = "Planaday Native",
    val appVersion: String = CURRENT_APP_VERSION,
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION,
    val schemaVersion: Int = CURRENT_CLIENT_SCHEMA_VERSION,
    val gitCommitSha: String = GeneratedBuildMeta.GIT_COMMIT_SHA,
    val gitFullSha: String = GeneratedBuildMeta.GIT_FULL_SHA,
    val buildTimestampUtc: String = GeneratedBuildMeta.BUILD_TIMESTAMP_UTC,
    val platform: String = "multiplatform",
    val freshStartOnlyO004: Boolean = true,
) {
    companion object {
        const val CURRENT_APP_VERSION: String = "1.0.0-alpha.2"
        const val CURRENT_PROTOCOL_VERSION: Int = 1
        const val CURRENT_CLIENT_SCHEMA_VERSION: Int = 2
        const val BACKUP_FORMAT_ID: String = "planaday-native-backup-v1"
        const val CLIENT_DB_NAME: String = "planaday_native_v1.db"
        const val ANDROID_APPLICATION_ID: String = "com.planaday.nativeapp"
    }
}
