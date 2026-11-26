package com.planaday.nativeapp.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val scope: String,
    @ColumnInfo(name = "accent_hex") val accentHex: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: String,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: String,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
)

@Entity(
    tableName = "record_index",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "kind", "updated_at_utc"]),
        Index(value = ["workspace_id", "kind", "deleted_at_utc", "updated_at_utc", "id"]),
        Index(value = ["workspace_id", "kind", "schedule_start_iso", "schedule_end_iso"]),
        Index(value = ["workspace_id", "deleted_at_utc"]),
        Index(value = ["workspace_id", "status", "kind"]),
        Index(value = ["workspace_id", "parent_id", "sort_order"]),
    ],
)
data class CanonicalRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    val kind: String,
    val title: String,
    val status: String,
    val rev: Long,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: String,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: String,
    @ColumnInfo(name = "deleted_at_utc") val deletedAtUtc: String?,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "owner_profile_id") val ownerProfileId: String? = null,
    @ColumnInfo(name = "shared_with_all") val sharedWithAll: Boolean = false,
    @ColumnInfo(name = "shared_profile_ids_json") val sharedProfileIdsJson: String = "[]",
    @ColumnInfo(name = "tags_json") val tagsJson: String = "[]",
    @ColumnInfo(name = "summary_text") val summaryText: String = "",
    @ColumnInfo(name = "payload_json") val payloadJson: String = "{}",
    @ColumnInfo(name = "extra_fields_json") val extraFieldsJson: String = "{}",
    @ColumnInfo(name = "is_synthetic_demo") val isSyntheticDemo: Boolean = false,
    @ColumnInfo(name = "schedule_start_iso") val scheduleStartIso: String? = null,
    @ColumnInfo(name = "schedule_end_iso") val scheduleEndIso: String? = null,
)

@Entity(
    tableName = "relations",
    indices = [
        Index(value = ["workspace_id", "source_id", "relation_kind"]),
        Index(value = ["workspace_id", "target_id", "relation_kind"]),
    ],
)
data class RelationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "target_id") val targetId: String,
    @ColumnInfo(name = "target_kind") val targetKind: String,
    @ColumnInfo(name = "relation_kind") val relationKind: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val label: String?,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: String,
)

/** Record-bound durable drafts (eliminates PD-016 & powers PF-55 Draft Recovery Inbox). */
@Entity(
    tableName = "drafts",
    primaryKeys = ["workspace_id", "record_id", "session_id"],
    indices = [Index(value = ["workspace_id", "dirty"])],
)
data class DraftEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "record_kind") val recordKind: String,
    @ColumnInfo(name = "title_draft") val titleDraft: String,
    @ColumnInfo(name = "body_draft") val bodyDraft: String,
    @ColumnInfo(name = "base_rev") val baseRev: Long,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: String,
    val dirty: Boolean,
)

@Entity(
    tableName = "revisions",
    indices = [Index(value = ["workspace_id", "record_id", "created_at_utc"])],
)
data class RevisionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "record_kind") val recordKind: String,
    val rev: Long,
    val title: String,
    @ColumnInfo(name = "content_snapshot") val contentSnapshot: String,
    @ColumnInfo(name = "checkpoint_label") val checkpointLabel: String?,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "is_immutable_evidence") val isImmutableEvidence: Boolean,
)

@Entity(
    tableName = "evidence_snapshots",
    indices = [Index(value = ["workspace_id", "source_record_id"])],
)
data class EvidenceSnapshotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "source_record_id") val sourceRecordId: String,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "source_rev") val sourceRev: Long,
    val title: String,
    @ColumnInfo(name = "content_json") val contentJson: String,
    @ColumnInfo(name = "content_sha256") val contentSha256: String,
    @ColumnInfo(name = "provenance_note") val provenanceNote: String,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: String,
)

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val sha256: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "original_filename") val originalFilename: String,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: String,
    @ColumnInfo(name = "local_storage_rel_path") val localStorageRelPath: String,
    @ColumnInfo(name = "thumbnail_rel_path") val thumbnailRelPath: String?,
)

@Entity(
    tableName = "attachment_refs",
    foreignKeys = [
        ForeignKey(
            entity = AttachmentEntity::class,
            parentColumns = ["sha256"],
            childColumns = ["sha256"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["workspace_id", "record_id"]),
        Index(value = ["workspace_id", "sha256"]),
        Index(value = ["sha256"]),
    ],
)
data class AttachmentRefEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    val sha256: String,
    val role: String,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: String,
)

@Entity(
    tableName = "planner_bookmarks",
    indices = [Index(value = ["workspace_id", "book_id", "sort_order"])],
)
data class PlannerBookmarkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "page_id") val pageId: String,
    val label: String,
    @ColumnInfo(name = "color_hex") val colorHex: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "outbox_ops",
    indices = [
        Index(value = ["workspace_id", "created_at_utc"]),
        Index(value = ["record_id"]),
    ],
)
data class OutboxOpEntity(
    @PrimaryKey @ColumnInfo(name = "op_id") val opId: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "record_kind") val recordKind: String,
    val operation: String, // UPSERT | DELETE
    @ColumnInfo(name = "base_rev") val baseRev: Long,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "record_json") val recordJson: String?,
    @ColumnInfo(name = "referenced_blob_hashes_json") val referencedBlobHashesJson: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: String,
    val attempts: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)

@Entity(
    tableName = "conflicts",
    indices = [Index(value = ["workspace_id", "resolved"])],
)
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "record_kind") val recordKind: String,
    @ColumnInfo(name = "local_record_json") val localRecordJson: String,
    @ColumnInfo(name = "remote_record_json") val remoteRecordJson: String,
    val reason: String,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: String,
    val resolved: Boolean = false,
)

@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "bound_server_id") val boundServerId: String?,
    @ColumnInfo(name = "bound_sync_epoch") val boundSyncEpoch: Long?,
    @ColumnInfo(name = "last_cursor") val lastCursor: Long,
    @ColumnInfo(name = "last_sync_at_utc") val lastSyncAtUtc: String?,
    @ColumnInfo(name = "restored_from_backup_at_utc") val restoredFromBackupAtUtc: String?,
)

@Entity(
    tableName = "blob_transfer_receipts",
    primaryKeys = ["workspace_id", "sha256", "direction"],
)
data class BlobTransferReceiptEntity(
    val sha256: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    val direction: String, // UPLOAD | DOWNLOAD
    val status: String, // PENDING | VERIFIED | FAILED
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    val attempts: Int,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: String,
)

@Entity(tableName = "automation_runs")
data class AutomationRunEntity(
    @PrimaryKey @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "rule_id") val ruleId: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "executed_at_utc") val executedAtUtc: String,
    val status: String,
)

@Entity(tableName = "app_preferences")
data class AppPreferenceEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "value_json") val valueJson: String,
    val rev: Long,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: String,
)
