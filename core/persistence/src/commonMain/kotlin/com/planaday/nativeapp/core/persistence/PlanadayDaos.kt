package com.planaday.nativeapp.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY is_default DESC, name ASC")
    fun observeAll(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces ORDER BY is_default DESC, name ASC")
    suspend fun listAll(): List<WorkspaceEntity>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workspace: WorkspaceEntity)
}

@Dao
interface RecordDao {
    @Query(
        "SELECT * FROM record_index WHERE workspace_id = :workspaceId AND kind = :kind AND deleted_at_utc IS NULL " +
            "ORDER BY updated_at_utc DESC LIMIT :limit OFFSET :offset"
    )
    fun observeByKind(workspaceId: String, kind: String, limit: Int = 200, offset: Int = 0): Flow<List<CanonicalRecordEntity>>

    @Query(
        "SELECT * FROM record_index WHERE workspace_id = :workspaceId AND deleted_at_utc IS NULL " +
            "ORDER BY updated_at_utc DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun listActive(workspaceId: String, limit: Int = 500, offset: Int = 0): List<CanonicalRecordEntity>

    @Query("SELECT * FROM record_index WHERE workspace_id = :workspaceId AND kind = :kind AND deleted_at_utc IS NULL ORDER BY sort_order ASC, updated_at_utc DESC")
    suspend fun listByKind(workspaceId: String, kind: String): List<CanonicalRecordEntity>

    @Query("SELECT * FROM record_index WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CanonicalRecordEntity?

    @Query("SELECT * FROM record_index WHERE id = :id AND workspace_id = :workspaceId LIMIT 1")
    suspend fun getByIdInWorkspace(workspaceId: String, id: String): CanonicalRecordEntity?

    @Query("SELECT * FROM record_index WHERE workspace_id = :workspaceId AND deleted_at_utc IS NOT NULL ORDER BY deleted_at_utc DESC")
    suspend fun listTrash(workspaceId: String): List<CanonicalRecordEntity>

    @Query("SELECT COUNT(*) FROM record_index WHERE workspace_id = :workspaceId AND deleted_at_utc IS NULL")
    suspend fun countActive(workspaceId: String): Long

    @Query("SELECT COUNT(*) FROM record_index")
    suspend fun countAllTotal(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNew(record: CanonicalRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForce(record: CanonicalRecordEntity)

    @Query(
        "UPDATE record_index SET title = :title, status = :status, rev = :newRev, updated_at_utc = :updatedAtUtc, " +
            "deleted_at_utc = :deletedAtUtc, parent_id = :parentId, sort_order = :sortOrder, " +
            "tags_json = :tagsJson, summary_text = :summaryText, payload_json = :payloadJson, " +
            "extra_fields_json = :extraFieldsJson, device_id = :deviceId " +
            "WHERE id = :id AND workspace_id = :workspaceId AND rev = :expectedRev"
    )
    suspend fun updateIfRevMatches(
        id: String,
        workspaceId: String,
        expectedRev: Long,
        newRev: Long,
        title: String,
        status: String,
        updatedAtUtc: String,
        deletedAtUtc: String?,
        parentId: String?,
        sortOrder: Int,
        tagsJson: String,
        summaryText: String,
        payloadJson: String,
        extraFieldsJson: String,
        deviceId: String,
    ): Int

    @Query("DELETE FROM record_index WHERE id = :id AND workspace_id = :workspaceId")
    suspend fun hardDelete(workspaceId: String, id: String): Int

    @Query("SELECT * FROM record_index WHERE workspace_id = :workspaceId AND deleted_at_utc IS NULL AND (title LIKE :pattern OR summary_text LIKE :pattern OR tags_json LIKE :pattern) ORDER BY updated_at_utc DESC LIMIT :limit")
    suspend fun searchFallbackLike(workspaceId: String, pattern: String, limit: Int = 50): List<CanonicalRecordEntity>
}

@Dao
interface RelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(relation: RelationEntity)

    @Query("SELECT * FROM relations WHERE workspace_id = :workspaceId AND source_id = :sourceId ORDER BY sort_order ASC")
    suspend fun outgoing(workspaceId: String, sourceId: String): List<RelationEntity>

    @Query("SELECT * FROM relations WHERE workspace_id = :workspaceId AND target_id = :targetId ORDER BY sort_order ASC")
    suspend fun incoming(workspaceId: String, targetId: String): List<RelationEntity>

    @Query("SELECT * FROM relations WHERE workspace_id = :workspaceId")
    suspend fun listAllInWorkspace(workspaceId: String): List<RelationEntity>

    @Query("DELETE FROM relations WHERE id = :id AND workspace_id = :workspaceId")
    suspend fun deleteById(workspaceId: String, id: String)
}

@Dao
interface DraftAndRevisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE workspace_id = :workspaceId AND record_id = :recordId AND session_id = :sessionId")
    suspend fun deleteDraft(workspaceId: String, recordId: String, sessionId: String)

    @Query("SELECT * FROM drafts WHERE workspace_id = :workspaceId AND dirty = 1 ORDER BY updated_at_utc DESC")
    suspend fun listDirtyDrafts(workspaceId: String): List<DraftEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: RevisionEntity)

    @Query("SELECT * FROM revisions WHERE workspace_id = :workspaceId AND record_id = :recordId ORDER BY rev DESC")
    suspend fun listRevisions(workspaceId: String, recordId: String): List<RevisionEntity>

    @Query("SELECT * FROM revisions WHERE workspace_id = :workspaceId")
    suspend fun listAllRevisions(workspaceId: String): List<RevisionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvidenceSnapshot(snapshot: EvidenceSnapshotEntity)

    @Query("SELECT * FROM evidence_snapshots WHERE workspace_id = :workspaceId AND source_record_id = :sourceId ORDER BY created_at_utc DESC")
    suspend fun listSnapshotsForSource(workspaceId: String, sourceId: String): List<EvidenceSnapshotEntity>
}

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttachment(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getAttachment(sha256: String): AttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRef(ref: AttachmentRefEntity)

    @Query("SELECT * FROM attachment_refs WHERE workspace_id = :workspaceId AND record_id = :recordId")
    suspend fun refsForRecord(workspaceId: String, recordId: String): List<AttachmentRefEntity>

    @Query("SELECT COUNT(*) FROM attachment_refs WHERE workspace_id = :workspaceId AND sha256 = :sha256")
    suspend fun countWorkspaceRefs(workspaceId: String, sha256: String): Int

    @Query("SELECT COUNT(*) FROM attachment_refs WHERE sha256 = :sha256")
    suspend fun countGlobalRefs(sha256: String): Int

    @Query("DELETE FROM attachment_refs WHERE workspace_id = :workspaceId AND record_id = :recordId")
    suspend fun deleteRefsForRecord(workspaceId: String, recordId: String)

    @Query("DELETE FROM attachments WHERE sha256 = :sha256")
    suspend fun deleteAttachmentMeta(sha256: String)
}

@Dao
interface PlannerBookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PlannerBookmarkEntity>)

    @Query("SELECT * FROM planner_bookmarks WHERE workspace_id = :workspaceId AND book_id = :bookId AND label LIKE :queryPattern ORDER BY sort_order ASC LIMIT :limit OFFSET :offset")
    suspend fun queryPage(workspaceId: String, bookId: String, queryPattern: String, limit: Int, offset: Int): List<PlannerBookmarkEntity>

    @Query("SELECT COUNT(*) FROM planner_bookmarks WHERE workspace_id = :workspaceId AND book_id = :bookId")
    suspend fun countForBook(workspaceId: String, bookId: String): Int
}

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueOutbox(op: OutboxOpEntity)

    @Query("SELECT * FROM outbox_ops WHERE workspace_id = :workspaceId ORDER BY created_at_utc ASC LIMIT :limit")
    suspend fun pendingOutbox(workspaceId: String, limit: Int = 250): List<OutboxOpEntity>

    @Query("SELECT COUNT(*) FROM outbox_ops WHERE workspace_id = :workspaceId")
    suspend fun countPendingOutbox(workspaceId: String): Int

    @Query("DELETE FROM outbox_ops WHERE op_id = :opId")
    suspend fun removeOutboxOp(opId: String)

    @Query("DELETE FROM outbox_ops WHERE record_id = :recordId AND workspace_id = :workspaceId")
    suspend fun removeOutboxForRecord(workspaceId: String, recordId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: SyncConflictEntity)

    @Query("SELECT * FROM conflicts WHERE workspace_id = :workspaceId AND resolved = 0 ORDER BY created_at_utc DESC")
    suspend fun listUnresolvedConflicts(workspaceId: String): List<SyncConflictEntity>

    @Query("UPDATE conflicts SET resolved = 1 WHERE id = :id")
    suspend fun markConflictResolved(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCursor(cursor: SyncCursorEntity)

    @Query("SELECT * FROM sync_cursors WHERE workspace_id = :workspaceId LIMIT 1")
    suspend fun getCursor(workspaceId: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlobReceipt(receipt: BlobTransferReceiptEntity)

    @Query("SELECT * FROM blob_transfer_receipts WHERE workspace_id = :workspaceId")
    suspend fun listBlobReceipts(workspaceId: String): List<BlobTransferReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordAutomationRun(run: AutomationRunEntity): Long

    @Query("SELECT COUNT(*) FROM automation_runs WHERE idempotency_key = :key")
    suspend fun hasAutomationRun(key: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreference(pref: AppPreferenceEntity)

    @Query("SELECT * FROM app_preferences WHERE key = :key LIMIT 1")
    suspend fun getPreference(key: String): AppPreferenceEntity?
}
