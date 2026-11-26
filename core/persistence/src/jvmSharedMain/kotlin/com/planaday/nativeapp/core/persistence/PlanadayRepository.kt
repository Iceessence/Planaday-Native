package com.planaday.nativeapp.core.persistence

import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import com.planaday.nativeapp.core.documents.DraftPersistenceSink
import com.planaday.nativeapp.core.domain.DomainClock
import com.planaday.nativeapp.core.domain.SystemDomainClock
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.security.CryptoPrimitives
import com.planaday.nativeapp.core.security.SecureCredentialVault
import com.planaday.nativeapp.core.syncprotocol.BlobTransferDirection
import com.planaday.nativeapp.core.syncprotocol.BlobTransferReceipt
import com.planaday.nativeapp.core.syncprotocol.BlobTransferStatus
import com.planaday.nativeapp.core.syncprotocol.SyncOpKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class OptimisticConflictException(message: String) : IllegalStateException(message)

data class SearchHit(
    val recordId: String,
    val workspaceId: String,
    val kind: RecordKind,
    val title: String,
    val summaryText: String,
)

data class RecordMutationEvent(
    val workspaceId: String,
    val recordId: String,
    val kind: RecordKind,
    val rev: Long,
)

data class KeysetPage(
    val records: List<CanonicalRecord>,
    val nextCursorUpdatedAtUtc: String?,
    val nextCursorId: String?,
    val hasMore: Boolean,
)

/**
 * Tracks repository read counts per RecordKind so tests and diagnostics can verify
 * zero unrelated collection fan-out after single-record edits (Update 2 §6).
 */
class RepositoryQueryTelemetry {
    private val readsByKind = ConcurrentHashMap<String, AtomicLong>()
    val totalListQueries = AtomicLong(0L)

    fun recordKindRead(kind: String) {
        totalListQueries.incrementAndGet()
        readsByKind.computeIfAbsent(kind) { AtomicLong(0L) }.incrementAndGet()
    }

    fun countFor(kind: RecordKind): Long = readsByKind[kind.name]?.get() ?: 0L
    fun snapshot(): Map<String, Long> = readsByKind.mapValues { it.value.get() }
    fun reset() {
        readsByKind.clear()
        totalListQueries.set(0L)
    }
}

/**
 * Transactional, concurrency-safe, reactive repository over Room KMP + Bundled SQLite
 * (PD-001, PD-016, PF-01, O-004, Update 2 §6).
 * - Per-record async Mutex + Room immediate SQLite write transaction guarantees zero lost disjoint edits.
 * - Atomically writes record + outbox_ops in the SAME SQLite transaction.
 * - Exposes bounded reactive Kotlin Flows, O(log N) keyset pagination, and indexed calendar date-range queries.
 * - Persists stable deviceId, user preferences, and OS-vault-encrypted credentials across restarts.
 */
class PlanadayRepository(
    val db: PlanadayDatabase,
    val blobStore: LocalBlobStore,
    initialDeviceId: String? = null,
    private val clock: DomainClock = SystemDomainClock,
) : DraftPersistenceSink {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val recordLocks = ConcurrentHashMap<String, Mutex>()
    val telemetry = RepositoryQueryTelemetry()

    @Volatile
    var deviceId: String = initialDeviceId ?: "dev_${UUID.randomUUID().toString().take(8)}"
        private set

    private val _mutations = MutableSharedFlow<RecordMutationEvent>(extraBufferCapacity = 64)
    val mutations: SharedFlow<RecordMutationEvent> = _mutations.asSharedFlow()

    private fun lockFor(recordId: String): Mutex = recordLocks.computeIfAbsent(recordId) { Mutex() }

    /** Loads or creates a durable device identity persisted in SQLite so sync cursors survive restarts (§28). */
    suspend fun ensurePersistentDeviceId(): String {
        val existing = db.syncDao().getPreference(PREF_DEVICE_ID)?.valueJson?.trim()?.removeSurrounding("\"")
        if (!existing.isNullOrBlank()) {
            deviceId = existing
            return existing
        }
        savePreference(PREF_DEVICE_ID, json.encodeToString(deviceId))
        return deviceId
    }

    // --- Persistent Preferences & Protected Credential Vault (§7, §26, §28) ---
    suspend fun savePreference(key: String, valueJson: String) {
        val cur = db.syncDao().getPreference(key)
        db.syncDao().savePreference(
            AppPreferenceEntity(
                key = key,
                valueJson = valueJson,
                rev = (cur?.rev ?: 0L) + 1L,
                updatedAtUtc = clock.nowUtcIso(),
            )
        )
    }

    suspend fun getPreference(key: String): String? = db.syncDao().getPreference(key)?.valueJson

    fun observePreference(key: String): Flow<String?> = db.syncDao().observePreference(key).map { it?.valueJson }

    suspend fun saveProtectedSecret(vault: SecureCredentialVault, keyAlias: String, plaintext: String) {
        val encrypted = vault.encryptSecret(keyAlias, plaintext)
        savePreference("secret.$keyAlias", json.encodeToString(encrypted))
    }

    suspend fun readProtectedSecret(vault: SecureCredentialVault, keyAlias: String): String? {
        val stored = getPreference("secret.$keyAlias") ?: return null
        val cipherB64 = runCatching { json.decodeFromString<String>(stored) }.getOrNull() ?: return null
        return vault.decryptSecret(keyAlias, cipherB64)
    }

    suspend fun clearProtectedSecret(keyAlias: String) {
        savePreference("secret.$keyAlias", json.encodeToString(""))
    }

    // --- Workspaces ---
    suspend fun ensureDefaultWorkspaceExists(workspaceId: String = "ws_personal", name: String = "Personal"): Workspace {
        ensurePersistentDeviceId()
        val existing = db.workspaceDao().getById(workspaceId)
        if (existing != null) return existing.toModel()
        val now = clock.nowUtcIso()
        val ws = Workspace(
            id = workspaceId,
            name = name,
            scope = WorkspaceScope.PERSONAL,
            accentHex = "#9A6525",
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

    fun observeWorkspaces(): Flow<List<Workspace>> = db.workspaceDao().observeAll().map { list -> list.map { it.toModel() } }

    fun observeActiveCount(workspaceId: String): Flow<Long> = db.recordDao().observeActiveCount(workspaceId)

    // --- Bounded Reactive & Keyset Paginated Queries (§6) ---
    fun observeRecordsByKind(workspaceId: String, kind: RecordKind, limit: Int = 200): Flow<List<CanonicalRecord>> =
        db.recordDao().observeByKind(workspaceId, kind.name, limit = limit)
            .onEach { telemetry.recordKindRead(kind.name) }
            .map { list -> list.map { it.toModel(json) } }

    fun observeScheduleWindow(
        workspaceId: String,
        kind: RecordKind,
        rangeStartIso: String,
        rangeEndIso: String,
        limit: Int = 500,
    ): Flow<List<CanonicalRecord>> =
        db.recordDao().observeScheduleWindow(workspaceId, kind.name, rangeStartIso, rangeEndIso, limit)
            .onEach { telemetry.recordKindRead("${kind.name}:WINDOW") }
            .map { list -> list.map { it.toModel(json) } }

    suspend fun listScheduleWindow(
        workspaceId: String,
        kind: RecordKind,
        rangeStartIso: String,
        rangeEndIso: String,
        limit: Int = 500,
    ): List<CanonicalRecord> {
        telemetry.recordKindRead("${kind.name}:WINDOW")
        return db.recordDao().listScheduleWindow(workspaceId, kind.name, rangeStartIso, rangeEndIso, limit)
            .map { it.toModel(json) }
    }

    /** True O(log N) keyset pagination for large datasets (10k–1M records). */
    suspend fun listByKindKeyset(
        workspaceId: String,
        kind: RecordKind,
        pageSize: Int = 50,
        cursorUpdatedAtUtc: String? = null,
        cursorId: String? = null,
    ): KeysetPage {
        telemetry.recordKindRead(kind.name)
        val fetchLimit = pageSize + 1
        val entities = if (cursorUpdatedAtUtc == null || cursorId == null) {
            db.recordDao().listByKindBounded(workspaceId, kind.name, limit = fetchLimit)
        } else {
            db.recordDao().listByKindKeyset(workspaceId, kind.name, cursorUpdatedAtUtc, cursorId, limit = fetchLimit)
        }
        val hasMore = entities.size > pageSize
        val pageEntities = if (hasMore) entities.take(pageSize) else entities
        val last = pageEntities.lastOrNull()
        return KeysetPage(
            records = pageEntities.map { it.toModel(json) },
            nextCursorUpdatedAtUtc = if (hasMore) last?.updatedAtUtc else null,
            nextCursorId = if (hasMore) last?.id else null,
            hasMore = hasMore,
        )
    }

    suspend fun getRecord(workspaceId: String, recordId: String): CanonicalRecord? =
        db.recordDao().getByIdInWorkspace(workspaceId, recordId)?.toModel(json)

    suspend fun listByKind(workspaceId: String, kind: RecordKind, limit: Int = 500): List<CanonicalRecord> {
        telemetry.recordKindRead(kind.name)
        return db.recordDao().listByKind(workspaceId, kind.name, limit = limit).map { it.toModel(json) }
    }

    suspend fun countAllRecords(): Long = db.recordDao().countAllTotal()

    fun observeTrash(workspaceId: String, limit: Int = 250): Flow<List<CanonicalRecord>> =
        db.recordDao().observeTrash(workspaceId, limit).map { list -> list.map { it.toModel(json) } }

    fun observeDirtyDrafts(workspaceId: String): Flow<List<DraftRecord>> =
        db.draftAndRevisionDao().observeDirtyDrafts(workspaceId).map { list -> list.map { it.toDraftModel() } }

    fun observePendingOutboxCount(workspaceId: String): Flow<Int> =
        db.syncDao().observePendingOutboxCount(workspaceId)

    fun observeUnresolvedConflicts(workspaceId: String): Flow<List<SyncConflictEntity>> =
        db.syncDao().observeUnresolvedConflicts(workspaceId)

    fun observeBlobReceipts(workspaceId: String): Flow<List<BlobTransferReceipt>> =
        db.syncDao().observeBlobReceipts(workspaceId).map { list -> list.map { it.toReceiptModel() } }

    fun observeAutomationRuns(workspaceId: String, limit: Int = 100): Flow<List<AutomationRunEntity>> =
        db.syncDao().observeAutomationRuns(workspaceId, limit)

    fun observeWorkspaceAttachments(workspaceId: String, limit: Int = 200): Flow<List<AttachmentMeta>> =
        db.attachmentDao().observeWorkspaceAttachments(workspaceId, limit).map { list -> list.map { it.toMetaModel() } }

    suspend fun listRevisions(workspaceId: String, recordId: String): List<RevisionSnapshot> =
        db.draftAndRevisionDao().listRevisions(workspaceId, recordId).map {
            RevisionSnapshot(
                id = it.id,
                workspaceId = it.workspaceId,
                recordId = it.recordId,
                recordKind = RecordKind.valueOf(it.recordKind),
                rev = it.rev,
                title = it.title,
                contentSnapshot = it.contentSnapshot,
                checkpointLabel = it.checkpointLabel,
                createdAtUtc = it.createdAtUtc,
                deviceId = it.deviceId,
                isImmutableEvidence = it.isImmutableEvidence,
            )
        }

    suspend fun restoreRevision(workspaceId: String, recordId: String, revisionId: String): CanonicalRecord {
        val revSnap = listRevisions(workspaceId, recordId).firstOrNull { it.id == revisionId }
            ?: throw NoSuchElementException("Revision $revisionId not found")
        return updateRecordAtomic(workspaceId, recordId, recordRevision = true, checkpointLabel = "Restored rev ${revSnap.rev}") {
            it.copy(title = revSnap.title, payloadJson = revSnap.contentSnapshot)
        }
    }

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
        scheduleStartIso: String? = null,
        scheduleEndIso: String? = null,
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
        val (inferredStart, inferredEnd) = extractScheduleDates(kind, payloadJson)
        val entity = rec.toEntity(
            json = json,
            parentId = parentId,
            sortOrder = sortOrder,
            scheduleStartIso = scheduleStartIso ?: inferredStart,
            scheduleEndIso = scheduleEndIso ?: inferredEnd,
        )
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
        _mutations.tryEmit(RecordMutationEvent(workspaceId, rec.id, rec.kind, rec.rev))
        rec
    }

    /**
     * PD-001 + PF-01: Per-record locked, transactional read-modify-write with optimistic `rev` guard.
     */
    suspend fun updateRecordAtomic(
        workspaceId: String,
        recordId: String,
        referencedBlobHashes: List<String> = emptyList(),
        recordRevision: Boolean = false,
        checkpointLabel: String? = null,
        scheduleStartIso: String? = null,
        scheduleEndIso: String? = null,
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
                val (inferredStart, inferredEnd) = extractScheduleDates(updated.kind, updated.payloadJson)
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
                    scheduleStartIso = scheduleStartIso ?: inferredStart ?: existingEntity.scheduleStartIso,
                    scheduleEndIso = scheduleEndIso ?: inferredEnd ?: existingEntity.scheduleEndIso,
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
        val finalRec = result!!
        _mutations.tryEmit(RecordMutationEvent(workspaceId, finalRec.id, finalRec.kind, finalRec.rev))
        finalRec
    }

    suspend fun moveToTrash(workspaceId: String, recordId: String): CanonicalRecord =
        updateRecordAtomic(workspaceId, recordId) { it.copy(deletedAtUtc = clock.nowUtcIso()) }

    suspend fun restoreFromTrash(workspaceId: String, recordId: String): CanonicalRecord =
        updateRecordAtomic(workspaceId, recordId) { it.copy(deletedAtUtc = null) }

    suspend fun listTrash(workspaceId: String): List<CanonicalRecord> =
        db.recordDao().listTrash(workspaceId).map { it.toModel(json) }

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

    suspend fun applyPulledRecordFromServer(record: CanonicalRecord) = lockFor(record.id).withLock {
        val (inferredStart, inferredEnd) = extractScheduleDates(record.kind, record.payloadJson)
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                ensureWorkspaceExistsInsideTx(record.workspaceId)
                db.recordDao().upsertForce(record.toEntity(json, scheduleStartIso = inferredStart, scheduleEndIso = inferredEnd))
            }
        }
        _mutations.tryEmit(RecordMutationEvent(record.workspaceId, record.id, record.kind, record.rev))
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
        db.draftAndRevisionDao().listDirtyDrafts(workspaceId).map { it.toDraftModel() }

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
        db.syncDao().listBlobReceipts(workspaceId).map { it.toReceiptModel() }

    suspend fun listUnresolvedConflicts(workspaceId: String): List<SyncConflictEntity> =
        db.syncDao().listUnresolvedConflicts(workspaceId)

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

    suspend fun resolveConflictKeepLocal(workspaceId: String, conflictId: String): CanonicalRecord {
        val conflict = db.syncDao().listUnresolvedConflicts(workspaceId).firstOrNull { it.id == conflictId }
            ?: throw NoSuchElementException("Conflict $conflictId not found")
        val localRec = json.decodeFromString<CanonicalRecord>(conflict.localRecordJson)
        val remoteRec = json.decodeFromString<CanonicalRecord>(conflict.remoteRecordJson)
        applyPulledRecordFromServer(remoteRec)
        val reapplied = updateRecordAtomic(workspaceId, remoteRec.id, recordRevision = true, checkpointLabel = "Conflict resolved (keep local)") {
            it.copy(title = localRec.title, payloadJson = localRec.payloadJson, summaryText = localRec.summaryText, tags = localRec.tags)
        }
        db.syncDao().markConflictResolved(conflictId)
        return reapplied
    }

    /** Automatically extracts ISO date range bounds for indexed calendar queries without extra table scans. */
    private fun extractScheduleDates(kind: RecordKind, payloadJson: String): Pair<String?, String?> = runCatching {
        val obj = json.parseToJsonElement(payloadJson).jsonObject
        fun str(key: String): String? = obj[key]?.jsonPrimitive?.content?.takeIf { it.length >= 10 && it[4] == '-' }
        when (kind) {
            RecordKind.EVENT -> str("startDateIso") to (str("endDateIso") ?: str("startDateIso"))
            RecordKind.TASK -> (str("doDateIso") ?: str("dueDateIso")) to (str("dueDateIso") ?: str("doDateIso"))
            RecordKind.CHORE -> str("nextDueDateIso") to str("nextDueDateIso")
            RecordKind.DIARY, RecordKind.FOOD_LOG, RecordKind.MEAL_PLAN, RecordKind.EXPENSE -> {
                val d = str("dateIso")
                d to d
            }
            RecordKind.LAB_SAMPLE -> str("receivedDateIso") to (str("dueDateIso") ?: str("receivedDateIso"))
            RecordKind.LAB_CALIBRATION -> str("nextDueDateIso") to str("nextDueDateIso")
            RecordKind.TRIP -> str("departureDateIso") to (str("returnDateIso") ?: str("departureDateIso"))
            else -> (str("dateIso") ?: str("targetDateIso") ?: str("startDateIso")) to (str("endDateIso") ?: str("dateIso") ?: str("targetDateIso"))
        }
    }.getOrDefault(null to null)

    companion object {
        const val PREF_DEVICE_ID: String = "planaday.device_id"

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

        private fun CanonicalRecord.toEntity(
            json: Json,
            parentId: String? = null,
            sortOrder: Int = 0,
            scheduleStartIso: String? = null,
            scheduleEndIso: String? = null,
        ) = CanonicalRecordEntity(
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
            scheduleStartIso = scheduleStartIso,
            scheduleEndIso = scheduleEndIso,
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

        private fun DraftEntity.toDraftModel() = DraftRecord(
            sessionId = sessionId,
            workspaceId = workspaceId,
            recordId = recordId,
            recordKind = RecordKind.valueOf(recordKind),
            titleDraft = titleDraft,
            bodyDraft = bodyDraft,
            baseRev = baseRev,
            updatedAtUtc = updatedAtUtc,
            dirty = dirty,
        )

        private fun BlobTransferReceiptEntity.toReceiptModel() = BlobTransferReceipt(
            sha256 = sha256,
            workspaceId = workspaceId,
            direction = BlobTransferDirection.valueOf(direction),
            status = BlobTransferStatus.valueOf(status),
            byteSize = byteSize,
            attempts = attempts,
            lastError = lastError,
            updatedAtUtc = updatedAtUtc,
        )

        private fun AttachmentEntity.toMetaModel() = AttachmentMeta(
            sha256 = sha256,
            mimeType = mimeType,
            byteSize = byteSize,
            originalFilename = originalFilename,
            createdAtUtc = createdAtUtc,
            localStorageRelPath = localStorageRelPath,
            thumbnailRelPath = thumbnailRelPath,
        )
    }
}
