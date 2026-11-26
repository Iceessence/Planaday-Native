package com.planaday.nativeapp.core.persistence

import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import com.planaday.nativeapp.core.documents.DraftPersistenceSink
import com.planaday.nativeapp.core.domain.DomainClock
import com.planaday.nativeapp.core.domain.SystemDomainClock
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.security.CryptoPrimitives
import com.planaday.nativeapp.core.syncprotocol.BlobTransferDirection
import com.planaday.nativeapp.core.syncprotocol.BlobTransferReceipt
import com.planaday.nativeapp.core.syncprotocol.BlobTransferStatus
import com.planaday.nativeapp.core.syncprotocol.SyncOpKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class OptimisticConflictException(message: String) : IllegalStateException(message)

data class SearchHit(
    val recordId: String,
    val workspaceId: String,
    val kind: RecordKind,
    val title: String,
    val summaryText: String,
)

/**
 * Transactional, concurrency-safe repository over Room KMP + Bundled SQLite (PD-001, PD-016, PF-01, O-004).
 * - Per-record async Mutex + Room immediate SQLite write transaction guarantees zero lost disjoint edits.
 * - Atomically writes record + outbox_ops in the SAME SQLite transaction (rollback leaves zero orphan outbox op).
 * - Enforces workspace isolation on every query, search hit, attachment ref, and trash action.
 */
class PlanadayRepository(
    val db: PlanadayDatabase,
    val blobStore: LocalBlobStore,
    val deviceId: String = "dev_${UUID.randomUUID().toString().take(8)}",
    private val clock: DomainClock = SystemDomainClock,
) : DraftPersistenceSink {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val recordLocks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(recordId: String): Mutex = recordLocks.computeIfAbsent(recordId) { Mutex() }

    /**
     * Fresh-start bootstrap (O-004): creates ONLY an empty default workspace if the DB is completely brand new.
     * NEVER seeds personal/legacy records; synthetic demo records are strictly opt-in.
     */
    suspend fun ensureDefaultWorkspaceExists(workspaceId: String = "ws_personal", name: String = "Personal"): Workspace {
        val existing = db.workspaceDao().getById(workspaceId)
        if (existing != null) return existing.toModel()
        val now = clock.nowUtcIso()
        val ws = Workspace(
            id = workspaceId,
            name = name,
            scope = WorkspaceScope.PERSONAL,
            accentHex = "#A97434",
            iconKey = "home",
            createdAtUtc = now,
            updatedAtUtc = now,
            isDefault = true,
        )
        db.workspaceDao().upsert(ws.toEntity())
        return ws
    }

    suspend fun createWorkspace(ws: Workspace): Workspace {
        db.workspaceDao().upsert(ws.toEntity())
        return ws
    }

    suspend fun listWorkspaces(): List<Workspace> = db.workspaceDao().listAll().map { it.toModel() }

    fun observeRecordsByKind(workspaceId: String, kind: RecordKind, limit: Int = 200): Flow<List<CanonicalRecord>> =
        db.recordDao().observeByKind(workspaceId, kind.name, limit = limit).map { list -> list.map { it.toModel(json) } }

    suspend fun getRecord(workspaceId: String, recordId: String): CanonicalRecord? =
        db.recordDao().getByIdInWorkspace(workspaceId, recordId)?.toModel(json)

    suspend fun listByKind(workspaceId: String, kind: RecordKind): List<CanonicalRecord> =
        db.recordDao().listByKind(workspaceId, kind.name).map { it.toModel(json) }

    suspend fun countAllRecords(): Long = db.recordDao().countAllTotal()

    /**
     * Atomically creates a new canonical record AND queues its sync outbox operation in ONE SQLite transaction.
     */
    suspend fun createRecord(
        workspaceId: String,
        kind: RecordKind,
        title: String,
        payloadJson: String = "{}",
        summaryText: String = "",
        tags: List<String> = emptyList(),
        parentId: String? = null,
        sortOrder: Int = 0,
        id: String = UUID.randomUUID().toString(),
        status: RecordStatus = RecordStatus.ACTIVE,
        referencedBlobHashes: List<String> = emptyList(),
        isSyntheticDemo: Boolean = false,
        recordRevision: Boolean = false,
    ): CanonicalRecord = lockFor(id).withLock {
        val now = clock.nowUtcIso()
        val rec = CanonicalRecord(
            id = id,
            workspaceId = workspaceId,
            kind = kind,
            title = title,
            status = status,
            rev = 1L,
            schemaVersion = BuildIdentity.CURRENT_CLIENT_SCHEMA_VERSION,
            createdAtUtc = now,
            updatedAtUtc = now,
            deletedAtUtc = null,
            deviceId = deviceId,
            tags = tags,
            summaryText = summaryText,
            payloadJson = payloadJson,
            isSyntheticDemo = isSyntheticDemo,
        )
        val entity = rec.toEntity(json, parentId = parentId, sortOrder = sortOrder)
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                db.recordDao().insertNew(entity)
                if (recordRevision) {
                    db.draftAndRevisionDao().insertRevision(
                        RevisionEntity(
                            id = UUID.randomUUID().toString(),
                            workspaceId = workspaceId,
                            recordId = id,
                            recordKind = kind.name,
                            rev = 1L,
                            title = title,
                            contentSnapshot = payloadJson,
                            checkpointLabel = "Initial",
                            createdAtUtc = now,
                            deviceId = deviceId,
                            isImmutableEvidence = kind == RecordKind.LAB_CALIBRATION || kind == RecordKind.EVIDENCE_SNAPSHOT,
                        )
                    )
                }
                enqueueOutboxCoalesced(rec, SyncOpKind.UPSERT, baseRev = 0L, referencedBlobHashes = referencedBlobHashes, now = now)
            }
        }
        rec
    }

    /**
     * PD-001 + PF-01: Per-record locked, transactional read-modify-write with optimistic `rev` guard.
     * If `mutator` throws, SQLite transaction rolls back completely — zero partial mutation or stale outbox entry!
     */
    suspend fun updateRecordAtomic(
        workspaceId: String,
        recordId: String,
        referencedBlobHashes: List<String> = emptyList(),
        recordRevision: Boolean = false,
        checkpointLabel: String? = null,
        mutator: (CanonicalRecord) -> CanonicalRecord,
    ): CanonicalRecord = lockFor(recordId).withLock {
        var result: CanonicalRecord? = null
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val existingEntity = db.recordDao().getByIdInWorkspace(workspaceId, recordId)
                    ?: throw NoSuchElementException("Record $recordId not found in workspace $workspaceId")
                val existing = existingEntity.toModel(json)
                val mutated = mutator(existing)
                val now = clock.nowUtcIso()
                val nextRev = existing.rev + 1L
                val updated = mutated.copy(
                    id = existing.id,
                    workspaceId = existing.workspaceId,
                    kind = existing.kind,
                    createdAtUtc = existing.createdAtUtc,
                    rev = nextRev,
                    updatedAtUtc = now,
                    deviceId = deviceId,
                )
                val rows = db.recordDao().updateIfRevMatches(
                    id = existing.id,
                    workspaceId = existing.workspaceId,
                    expectedRev = existing.rev,
                    newRev = nextRev,
                    title = updated.title,
                    status = updated.status.name,
                    updatedAtUtc = now,
                    deletedAtUtc = updated.deletedAtUtc,
                    parentId = existingEntity.parentId,
                    sortOrder = existingEntity.sortOrder,
                    tagsJson = json.encodeToString(updated.tags),
                    summaryText = updated.summaryText,
                    payloadJson = updated.payloadJson,
                    extraFieldsJson = updated.extraFieldsJson,
                    deviceId = deviceId,
                )
                if (rows != 1) {
                    throw OptimisticConflictException("Revision conflict updating $recordId (expected rev=${existing.rev})")
                }
                if (recordRevision) {
                    db.draftAndRevisionDao().insertRevision(
                        RevisionEntity(
                            id = UUID.randomUUID().toString(),
                            workspaceId = workspaceId,
                            recordId = recordId,
                            recordKind = updated.kind.name,
                            rev = nextRev,
                            title = updated.title,
                            contentSnapshot = updated.payloadJson,
                            checkpointLabel = checkpointLabel,
                            createdAtUtc = now,
                            deviceId = deviceId,
                            isImmutableEvidence = updated.kind == RecordKind.LAB_CALIBRATION,
                        )
                    )
                }
                enqueueOutboxCoalesced(updated, SyncOpKind.UPSERT, baseRev = existing.rev, referencedBlobHashes = referencedBlobHashes, now = now)
                result = updated
            }
        }
        result!!
    }

    /** Soft-deletes into 30-day Trash (recoverable) and queues sync update atomically. */
    suspend fun moveToTrash(workspaceId: String, recordId: String): CanonicalRecord =
        updateRecordAtomic(workspaceId, recordId) { it.copy(deletedAtUtc = clock.nowUtcIso()) }

    /** Restores a soft-deleted record from 30-day Trash. */
    suspend fun restoreFromTrash(workspaceId: String, recordId: String): CanonicalRecord =
        updateRecordAtomic(workspaceId, recordId) { it.copy(deletedAtUtc = null) }

    suspend fun listTrash(workspaceId: String): List<CanonicalRecord> =
        db.recordDao().listTrash(workspaceId).map { it.toModel(json) }

    /** Confirmed permanent purge from Trash with reference-counted blob cleanup. */
    suspend fun purgeFromTrash(workspaceId: String, recordId: String) = lockFor(recordId).withLock {
        val now = clock.nowUtcIso()
        val orphanHashes = mutableListOf<String>()
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val existing = db.recordDao().getByIdInWorkspace(workspaceId, recordId) ?: return@immediateTransaction
                val refs = db.attachmentDao().refsForRecord(workspaceId, recordId)
                db.attachmentDao().deleteRefsForRecord(workspaceId, recordId)
                for (r in refs) {
                    if (db.attachmentDao().countGlobalRefs(r.sha256) == 0) {
                        db.attachmentDao().deleteAttachmentMeta(r.sha256)
                        orphanHashes.add(r.sha256)
                    }
                }
                db.recordDao().hardDelete(workspaceId, recordId)
                enqueueOutboxCoalesced(
                    existing.toModel(json),
                    SyncOpKind.DELETE,
                    baseRev = existing.rev,
                    referencedBlobHashes = emptyList(),
                    now = now,
                )
            }
        }
        for (h in orphanHashes) blobStore.deleteUnreferenced(h)
    }

    /** Stores binary attachment bytes with SHA-256 verification and creates workspace-scoped reference. */
    suspend fun attachBlobToRecord(
        workspaceId: String,
        recordId: String,
        filename: String,
        mimeType: String,
        bytes: ByteArray,
        role: String = "attachment",
    ): AttachmentMeta {
        val sha256 = blobStore.putVerified(bytes)
        val now = clock.nowUtcIso()
        val meta = AttachmentMeta(
            sha256 = sha256,
            mimeType = mimeType,
            byteSize = bytes.size.toLong(),
            originalFilename = blobStore.safeFilename(filename),
            createdAtUtc = now,
            localStorageRelPath = blobStore.relativePathFor(sha256),
        )
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                db.attachmentDao().insertAttachment(
                    AttachmentEntity(
                        sha256 = meta.sha256,
                        mimeType = meta.mimeType,
                        byteSize = meta.byteSize,
                        originalFilename = meta.originalFilename,
                        createdAtUtc = meta.createdAtUtc,
                        localStorageRelPath = meta.localStorageRelPath,
                        thumbnailRelPath = null,
                    )
                )
                db.attachmentDao().insertRef(
                    AttachmentRefEntity(
                        id = "${workspaceId}_${recordId}_${sha256}_$role",
                        workspaceId = workspaceId,
                        recordId = recordId,
                        sha256 = sha256,
                        role = role,
                        createdAtUtc = now,
                    )
                )
                db.syncDao().upsertBlobReceipt(
                    BlobTransferReceiptEntity(
                        sha256 = sha256,
                        workspaceId = workspaceId,
                        direction = BlobTransferDirection.UPLOAD.name,
                        status = BlobTransferStatus.PENDING.name,
                        byteSize = meta.byteSize,
                        attempts = 0,
                        lastError = null,
                        updatedAtUtc = now,
                    )
                )
            }
        }
        return meta
    }

    suspend fun canWorkspaceAccessBlob(workspaceId: String, sha256: String): Boolean =
        db.attachmentDao().countWorkspaceRefs(workspaceId, sha256) > 0

    suspend fun linkRecords(
        workspaceId: String,
        sourceId: String,
        sourceKind: RecordKind,
        targetId: String,
        targetKind: RecordKind,
        relationKind: RelationKind,
        label: String? = null,
    ): RecordRelation {
        val rel = RecordRelation(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            sourceId = sourceId,
            sourceKind = sourceKind,
            targetId = targetId,
            targetKind = targetKind,
            relationKind = relationKind,
            label = label,
            createdAtUtc = clock.nowUtcIso(),
        )
        db.relationDao().upsert(
            RelationEntity(
                id = rel.id,
                workspaceId = rel.workspaceId,
                sourceId = rel.sourceId,
                sourceKind = rel.sourceKind.name,
                targetId = rel.targetId,
                targetKind = rel.targetKind.name,
                relationKind = rel.relationKind.name,
                sortOrder = rel.sortOrder,
                label = rel.label,
                createdAtUtc = rel.createdAtUtc,
            )
        )
        return rel
    }

    suspend fun outgoingLinks(workspaceId: String, sourceId: String): List<RecordRelation> =
        db.relationDao().outgoing(workspaceId, sourceId).map { it.toModel() }

    suspend fun incomingLinks(workspaceId: String, targetId: String): List<RecordRelation> =
        db.relationDao().incoming(workspaceId, targetId).map { it.toModel() }

    /** Creates an immutable SHA-256 evidence snapshot with provenance (PF-40, N-020). */
    suspend fun createEvidenceSnapshot(
        workspaceId: String,
        sourceRecordId: String,
        title: String,
        provenanceNote: String,
    ): EvidenceSnapshot {
        val src = getRecord(workspaceId, sourceRecordId)
            ?: throw NoSuchElementException("Source record $sourceRecordId not found in $workspaceId")
        val contentJson = src.payloadJson
        val sha = CryptoPrimitives.sha256Hex(contentJson.encodeToByteArray())
        val snap = EvidenceSnapshot(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            sourceRecordId = src.id,
            sourceKind = src.kind,
            sourceRev = src.rev,
            title = title,
            contentJson = contentJson,
            contentSha256 = sha,
            provenanceNote = provenanceNote,
            createdAtUtc = clock.nowUtcIso(),
        )
        db.draftAndRevisionDao().insertEvidenceSnapshot(
            EvidenceSnapshotEntity(
                id = snap.id,
                workspaceId = snap.workspaceId,
                sourceRecordId = snap.sourceRecordId,
                sourceKind = snap.sourceKind.name,
                sourceRev = snap.sourceRev,
                title = snap.title,
                contentJson = snap.contentJson,
                contentSha256 = snap.contentSha256,
                provenanceNote = snap.provenanceNote,
                createdAtUtc = snap.createdAtUtc,
            )
        )
        return snap
    }

    /**
     * Indexed SQLite FTS5 search strictly filtered by [workspaceId] (PF-10, N-006, INT-014).
     * Never leaks records from unauthorized workspaces or soft-deleted trash items.
     */
    suspend fun searchAuthorizedFts5(workspaceId: String, rawQuery: String, limit: Int = 50): List<SearchHit> {
        val tokens = rawQuery.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val ftsMatch = tokens.joinToString(" AND ") { tok ->
            val clean = tok.replace(Regex("[^\\p{L}\\p{N}_-]"), "")
            if (clean.isEmpty()) "" else "\"$clean\"*"
        }.trim()
        if (ftsMatch.isEmpty()) return emptyList()

        return db.useReaderConnection { conn ->
            conn.usePrepared(
                "SELECT record_id, workspace_id, kind, title, summary_text FROM record_fts " +
                    "WHERE record_fts MATCH ? AND workspace_id = ? LIMIT ?"
            ) { stmt ->
                stmt.bindText(1, ftsMatch)
                stmt.bindText(2, workspaceId)
                stmt.bindLong(3, limit.toLong())
                val hits = mutableListOf<SearchHit>()
                while (stmt.step()) {
                    hits.add(
                        SearchHit(
                            recordId = stmt.getText(0),
                            workspaceId = stmt.getText(1),
                            kind = RecordKind.valueOf(stmt.getText(2)),
                            title = stmt.getText(3),
                            summaryText = stmt.getText(4),
                        )
                    )
                }
                hits
            }
        }
    }

    /** Applies an authoritative record pulled from the sync server without echoing back into outbox. */
    suspend fun applyPulledRecordFromServer(record: CanonicalRecord) = lockFor(record.id).withLock {
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                ensureWorkspaceExistsInsideTx(record.workspaceId)
                db.recordDao().upsertForce(record.toEntity(json))
            }
        }
    }

    private suspend fun ensureWorkspaceExistsInsideTx(workspaceId: String) {
        if (db.workspaceDao().getById(workspaceId) == null) {
            val now = clock.nowUtcIso()
            db.workspaceDao().upsert(
                WorkspaceEntity(
                    id = workspaceId,
                    name = workspaceId.removePrefix("ws_").replaceFirstChar { it.uppercase() },
                    scope = if (workspaceId.contains("lab")) WorkspaceScope.LABORATORY.name else WorkspaceScope.PERSONAL.name,
                    accentHex = "#0F766E",
                    iconKey = "folder",
                    createdAtUtc = now,
                    updatedAtUtc = now,
                    isDefault = false,
                )
            )
        }
    }

    private suspend fun enqueueOutboxCoalesced(
        record: CanonicalRecord,
        operation: SyncOpKind,
        baseRev: Long,
        referencedBlobHashes: List<String>,
        now: String,
    ) {
        // Coalesce pending unpushed ops for the same record while preserving earliest baseRev!
        val existingOps = db.syncDao().pendingOutbox(record.workspaceId, limit = 1000)
            .filter { it.recordId == record.id }
        val effectiveBaseRev = existingOps.minOfOrNull { it.baseRev } ?: baseRev
        val mergedBlobs = (existingOps.flatMap { json.decodeFromString<List<String>>(it.referencedBlobHashesJson) } + referencedBlobHashes).distinct()
        if (existingOps.isNotEmpty()) {
            db.syncDao().removeOutboxForRecord(record.workspaceId, record.id)
        }
        db.syncDao().enqueueOutbox(
            OutboxOpEntity(
                opId = UUID.randomUUID().toString(),
                workspaceId = record.workspaceId,
                recordId = record.id,
                recordKind = record.kind.name,
                operation = operation.name,
                baseRev = effectiveBaseRev,
                schemaVersion = record.schemaVersion,
                recordJson = if (operation == SyncOpKind.UPSERT) json.encodeToString(record) else null,
                referencedBlobHashesJson = json.encodeToString(mergedBlobs),
                deviceId = deviceId,
                createdAtUtc = now,
            )
        )
    }

    // --- DraftPersistenceSink implementation (PD-016 + PF-55) ---
    override suspend fun saveDraft(draft: DraftRecord) {
        db.draftAndRevisionDao().saveDraft(
            DraftEntity(
                sessionId = draft.sessionId,
                workspaceId = draft.workspaceId,
                recordId = draft.recordId,
                recordKind = draft.recordKind.name,
                titleDraft = draft.titleDraft,
                bodyDraft = draft.bodyDraft,
                baseRev = draft.baseRev,
                updatedAtUtc = draft.updatedAtUtc,
                dirty = draft.dirty,
            )
        )
    }

    override suspend fun deleteDraft(workspaceId: String, recordId: String, sessionId: String) {
        db.draftAndRevisionDao().deleteDraft(workspaceId, recordId, sessionId)
    }

    override suspend fun listDrafts(workspaceId: String): List<DraftRecord> =
        db.draftAndRevisionDao().listDirtyDrafts(workspaceId).map {
            DraftRecord(
                sessionId = it.sessionId,
                workspaceId = it.workspaceId,
                recordId = it.recordId,
                recordKind = RecordKind.valueOf(it.recordKind),
                titleDraft = it.titleDraft,
                bodyDraft = it.bodyDraft,
                baseRev = it.baseRev,
                updatedAtUtc = it.updatedAtUtc,
                dirty = it.dirty,
            )
        }

    suspend fun saveBlobReceipt(receipt: BlobTransferReceipt) {
        db.syncDao().upsertBlobReceipt(
            BlobTransferReceiptEntity(
                sha256 = receipt.sha256,
                workspaceId = receipt.workspaceId,
                direction = receipt.direction.name,
                status = receipt.status.name,
                byteSize = receipt.byteSize,
                attempts = receipt.attempts,
                lastError = receipt.lastError,
                updatedAtUtc = receipt.updatedAtUtc,
            )
        )
    }

    suspend fun listBlobReceipts(workspaceId: String): List<BlobTransferReceipt> =
        db.syncDao().listBlobReceipts(workspaceId).map {
            BlobTransferReceipt(
                sha256 = it.sha256,
                workspaceId = it.workspaceId,
                direction = BlobTransferDirection.valueOf(it.direction),
                status = BlobTransferStatus.valueOf(it.status),
                byteSize = it.byteSize,
                attempts = it.attempts,
                lastError = it.lastError,
                updatedAtUtc = it.updatedAtUtc,
            )
        }

    /** Lists all unresolved sync conflicts preserving both local & remote copies (N-042). */
    suspend fun listUnresolvedConflicts(workspaceId: String): List<SyncConflictEntity> =
        db.syncDao().listUnresolvedConflicts(workspaceId)

    /**
     * Resolves a sync conflict with explicit user choice — NEVER silently discards data (N-042):
     * - ACCEPT_REMOTE: adopts remote record locally and marks conflict resolved.
     * - KEEP_BOTH: accepts remote record under original ID AND saves local version as a brand-new linked copy!
     */
    suspend fun resolveConflictKeepBoth(workspaceId: String, conflictId: String): CanonicalRecord {
        val conflict = db.syncDao().listUnresolvedConflicts(workspaceId).firstOrNull { it.id == conflictId }
            ?: throw NoSuchElementException("Conflict $conflictId not found")
        val localRec = json.decodeFromString<CanonicalRecord>(conflict.localRecordJson)
        val remoteRec = json.decodeFromString<CanonicalRecord>(conflict.remoteRecordJson)
        applyPulledRecordFromServer(remoteRec)
        val preservedCopy = createRecord(
            workspaceId = workspaceId,
            kind = localRec.kind,
            title = "${localRec.title} (Conflict Copy)",
            payloadJson = localRec.payloadJson,
            summaryText = localRec.summaryText,
            tags = (localRec.tags + "conflict-copy").distinct(),
            recordRevision = true,
        )
        linkRecords(workspaceId, preservedCopy.id, preservedCopy.kind, remoteRec.id, remoteRec.kind, RelationKind.DERIVED_FROM, "Conflict copy")
        db.syncDao().markConflictResolved(conflictId)
        return preservedCopy
    }

    suspend fun resolveConflictAcceptRemote(workspaceId: String, conflictId: String) {
        val conflict = db.syncDao().listUnresolvedConflicts(workspaceId).firstOrNull { it.id == conflictId }
            ?: throw NoSuchElementException("Conflict $conflictId not found")
        val remoteRec = json.decodeFromString<CanonicalRecord>(conflict.remoteRecordJson)
        applyPulledRecordFromServer(remoteRec)
        db.syncDao().markConflictResolved(conflictId)
    }

    companion object {
        private fun WorkspaceEntity.toModel() = Workspace(
            id = id,
            name = name,
            scope = WorkspaceScope.valueOf(scope),
            accentHex = accentHex,
            iconKey = iconKey,
            createdAtUtc = createdAtUtc,
            updatedAtUtc = updatedAtUtc,
            isDefault = isDefault,
        )

        private fun Workspace.toEntity() = WorkspaceEntity(
            id = id,
            name = name,
            scope = scope.name,
            accentHex = accentHex,
            iconKey = iconKey,
            createdAtUtc = createdAtUtc,
            updatedAtUtc = updatedAtUtc,
            isDefault = isDefault,
        )

        private fun CanonicalRecordEntity.toModel(json: Json) = CanonicalRecord(
            id = id,
            workspaceId = workspaceId,
            kind = RecordKind.valueOf(kind),
            title = title,
            status = RecordStatus.valueOf(status),
            rev = rev,
            schemaVersion = schemaVersion,
            createdAtUtc = createdAtUtc,
            updatedAtUtc = updatedAtUtc,
            deletedAtUtc = deletedAtUtc,
            deviceId = deviceId,
            ownerProfileId = ownerProfileId,
            sharedWithAll = sharedWithAll,
            sharedWithProfileIds = runCatching { json.decodeFromString<List<String>>(sharedProfileIdsJson) }.getOrDefault(emptyList()),
            tags = runCatching { json.decodeFromString<List<String>>(tagsJson) }.getOrDefault(emptyList()),
            summaryText = summaryText,
            payloadJson = payloadJson,
            extraFieldsJson = extraFieldsJson,
            isSyntheticDemo = isSyntheticDemo,
        )

        private fun CanonicalRecord.toEntity(json: Json, parentId: String? = null, sortOrder: Int = 0) = CanonicalRecordEntity(
            id = id,
            workspaceId = workspaceId,
            kind = kind.name,
            title = title,
            status = status.name,
            rev = rev,
            schemaVersion = schemaVersion,
            createdAtUtc = createdAtUtc,
            updatedAtUtc = updatedAtUtc,
            deletedAtUtc = deletedAtUtc,
            deviceId = deviceId,
            parentId = parentId,
            sortOrder = sortOrder,
            ownerProfileId = ownerProfileId,
            sharedWithAll = sharedWithAll,
            sharedProfileIdsJson = json.encodeToString(sharedWithProfileIds),
            tagsJson = json.encodeToString(tags),
            summaryText = summaryText,
            payloadJson = payloadJson,
            extraFieldsJson = extraFieldsJson,
            isSyntheticDemo = isSyntheticDemo,
        )

        private fun RelationEntity.toModel() = RecordRelation(
            id = id,
            workspaceId = workspaceId,
            sourceId = sourceId,
            sourceKind = RecordKind.valueOf(sourceKind),
            targetId = targetId,
            targetKind = RecordKind.valueOf(targetKind),
            relationKind = RelationKind.valueOf(relationKind),
            sortOrder = sortOrder,
            label = label,
            createdAtUtc = createdAtUtc,
        )
    }
}
