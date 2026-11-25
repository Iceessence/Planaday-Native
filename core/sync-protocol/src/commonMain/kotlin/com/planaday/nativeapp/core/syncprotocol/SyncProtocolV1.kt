package com.planaday.nativeapp.core.syncprotocol

import com.planaday.nativeapp.core.model.BuildIdentity
import com.planaday.nativeapp.core.model.CanonicalRecord
import com.planaday.nativeapp.core.model.RecordKind
import com.planaday.nativeapp.core.model.WorkspaceRole
import kotlinx.serialization.Serializable

@Serializable
data class ServerHealthResponse(
    val status: String, // ok | degraded
    val build: BuildIdentity,
    val serverId: String,
    val syncEpoch: Long,
    val ownerBootstrapped: Boolean,
    val databaseEngine: String,
)

@Serializable
data class ServerCapabilitiesResponse(
    val protocolVersion: Int = BuildIdentity.CURRENT_PROTOCOL_VERSION,
    val supportedProtocolVersions: List<Int> = listOf(1),
    val maxPushBatchOps: Int = 250,
    val maxBlobBytes: Long = 50L * 1024L * 1024L,
    val requiresAuthentication: Boolean = true,
)

@Serializable
data class OwnerBootstrapRequest(
    val passphrase: String,
    val deviceName: String = "Owner Device",
)

@Serializable
data class OwnerLoginRequest(
    val passphrase: String,
    val deviceName: String = "Owner Device",
)

@Serializable
data class AuthSessionResponse(
    val tokenId: String,
    val bearerToken: String,
    val role: WorkspaceRole,
    val authorizedWorkspaceIds: List<String>,
    val serverId: String,
    val syncEpoch: Long,
)

@Serializable
data class CreatePairingCodeRequest(
    val workspaceIds: List<String>,
    val role: WorkspaceRole = WorkspaceRole.EDITOR,
    val ttlSeconds: Long = 600L,
)

@Serializable
data class PairingCodeResponse(
    val pairingCode: String,
    val expiresAtUtc: String,
    val workspaceIds: List<String>,
    val role: WorkspaceRole,
)

@Serializable
data class RedeemPairingRequest(
    val pairingCode: String,
    val deviceId: String,
    val deviceName: String,
)

@Serializable
enum class SyncOpKind {
    UPSERT,
    DELETE,
}

@Serializable
data class OutboxOpEnvelope(
    val opId: String, // Client UUID idempotency key (C-0059)
    val workspaceId: String,
    val recordId: String,
    val recordKind: RecordKind,
    val operation: SyncOpKind,
    val baseRev: Long,
    val schemaVersion: Int = BuildIdentity.CURRENT_CLIENT_SCHEMA_VERSION,
    val record: CanonicalRecord? = null,
    val referencedBlobHashes: List<String> = emptyList(),
    val deviceId: String,
    val clientTimestampUtc: String,
)

@Serializable
data class SyncPushRequest(
    val deviceId: String,
    val boundServerId: String? = null,
    val boundSyncEpoch: Long? = null,
    val ops: List<OutboxOpEnvelope>,
)

@Serializable
enum class OpApplyStatus {
    APPLIED,
    DUPLICATE_IGNORED,
    CONFLICT,
    FORBIDDEN,
    INVALID,
}

@Serializable
data class OpApplyReceipt(
    val opId: String,
    val recordId: String,
    val status: OpApplyStatus,
    val serverCursor: Long? = null,
    val newRev: Long? = null,
    val serverRecord: CanonicalRecord? = null,
    val message: String? = null,
)

@Serializable
data class SyncPushResponse(
    val serverId: String,
    val syncEpoch: Long,
    val reconciliationRequired: Boolean = false,
    val receipts: List<OpApplyReceipt>,
    val latestCursor: Long,
)

@Serializable
data class ServerChangeEntry(
    val cursor: Long,
    val opId: String,
    val workspaceId: String,
    val recordId: String,
    val recordKind: RecordKind,
    val operation: SyncOpKind,
    val rev: Long,
    val record: CanonicalRecord?,
    val referencedBlobHashes: List<String> = emptyList(),
    val serverTimestampUtc: String,
)

@Serializable
data class SyncPullResponse(
    val serverId: String,
    val syncEpoch: Long,
    val workspaceId: String,
    val changes: List<ServerChangeEntry>,
    val nextCursor: Long,
    val hasMore: Boolean,
)

@Serializable
enum class BlobTransferDirection {
    UPLOAD,
    DOWNLOAD,
}

@Serializable
enum class BlobTransferStatus {
    PENDING,
    VERIFIED,
    FAILED,
}

@Serializable
data class BlobTransferReceipt(
    val sha256: String,
    val workspaceId: String,
    val direction: BlobTransferDirection,
    val status: BlobTransferStatus,
    val byteSize: Long,
    val attempts: Int = 1,
    val lastError: String? = null,
    val updatedAtUtc: String,
)

@Serializable
enum class SyncHealthStatus {
    IDLE_OFFLINE,
    SYNCING,
    SYNCED,
    RECONCILIATION_REQUIRED,
    PENDING_ATTACHMENTS,
    ERROR,
}

@Serializable
data class SyncStatusSnapshot(
    val status: SyncHealthStatus,
    val pendingOpsCount: Int,
    val pendingBlobUploads: Int,
    val pendingBlobDownloads: Int,
    val failedBlobTransfers: Int,
    val unresolvedConflicts: Int,
    val lastSyncAtUtc: String? = null,
    val boundServerId: String? = null,
    val boundSyncEpoch: Long? = null,
    val lastError: String? = null,
    val blobReceipts: List<BlobTransferReceipt> = emptyList(),
) {
    /** CONT-004 invariant: total pending count is NEVER 0 while failed/pending blob transfers exist! */
    val totalPendingCount: Int
        get() = pendingOpsCount + pendingBlobUploads + pendingBlobDownloads + failedBlobTransfers
}

@Serializable
data class ServerBackupReceiptResponse(
    val backupId: String,
    val createdAtUtc: String,
    val serverId: String,
    val syncEpoch: Long,
    val recordCount: Int,
    val blobCount: Int,
    val manifestSha256: String,
    val backupPath: String,
)

@Serializable
data class ServerDiagnosticsResponse(
    val build: BuildIdentity,
    val serverId: String,
    val syncEpoch: Long,
    val totalRecords: Long,
    val totalBlobs: Long,
    val totalBlobBytes: Long,
    val activeDeviceTokens: Int,
    val lastBackupReceipt: ServerBackupReceiptResponse? = null,
)
