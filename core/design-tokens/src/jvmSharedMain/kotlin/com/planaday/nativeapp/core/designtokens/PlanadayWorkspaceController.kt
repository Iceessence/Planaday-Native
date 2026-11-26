package com.planaday.nativeapp.core.designtokens

import com.planaday.nativeapp.core.documents.DraftSessionManager
import com.planaday.nativeapp.core.domain.ChoreEngine
import com.planaday.nativeapp.core.domain.RoomHealthSummary
import com.planaday.nativeapp.core.domain.SelectiveSyncPolicy
import com.planaday.nativeapp.core.domain.SystemDomainClock
import com.planaday.nativeapp.core.interchange.ArchiveInterchange
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.persistence.SearchHit
import com.planaday.nativeapp.core.persistence.SyncConflictEntity
import com.planaday.nativeapp.core.search.PlanadaySearchService
import com.planaday.nativeapp.core.security.SecureCredentialVault
import com.planaday.nativeapp.core.syncclient.PlanadaySyncClient
import com.planaday.nativeapp.core.syncprotocol.BlobTransferReceipt
import com.planaday.nativeapp.core.syncprotocol.SyncHealthStatus
import com.planaday.nativeapp.core.syncprotocol.SyncStatusSnapshot
import com.planaday.nativeapp.core.usecases.PlanadayUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class PlanadayShellState(
    val initialized: Boolean = false,
    val workspaces: List<Workspace> = emptyList(),
    val activeWorkspaceId: String = "ws_personal",
    val activeRoute: PlanadayRoute = PlanadayRoute.TODAY,
    val openTabs: List<PlanadayRoute> = listOf(PlanadayRoute.TODAY),
    val preferences: UserWorkspacePreferences = UserWorkspacePreferences(),
    val saveState: SaveState = SaveState.IDLE,
    val statusBanner: String? = null,
    val totalActiveRecords: Long = 0L,
    val searchQuery: String = "",
    val searchHits: List<SearchHit> = emptyList(),
    val calendarConfig: CalendarViewConfig = CalendarViewConfig(),
    val syncBearerTokenPresent: Boolean = false,
    val syncSnapshot: SyncStatusSnapshot = SyncStatusSnapshot(
        status = SyncHealthStatus.IDLE_OFFLINE,
        pendingOpsCount = 0,
        pendingBlobUploads = 0,
        pendingBlobDownloads = 0,
        failedBlobTransfers = 0,
        unresolvedConflicts = 0,
    ),
    val unresolvedConflicts: List<SyncConflictEntity> = emptyList(),
    val buildIdentity: BuildIdentity = BuildIdentity(),
    val lastBackupSummary: String? = null,
    val deviceId: String = "",
)

/**
 * Scalable, reactive, route-scoped state controller (Update 2 §6, §26, §28).
 *
 * CRITICAL ARCHITECTURE FIX:
 * - Completely eliminates global `refreshWorkspace()` fan-out!
 * - Subscribes ONLY to lightweight workspace metadata (workspaces, active count, pending outbox,
 *   unresolved conflicts, persisted preferences) at shell scope.
 * - Each feature view subscribes directly to its own bounded Room `Flow` (`observeKind`,
 *   `observeScheduleWindow`, `observeTrash`, `observeDirtyDrafts`, `observeAttachments`), so editing
 *   a single task triggers ZERO reloads of notes, expenses, lab records, food logs, or other domains!
 * - All open Desktop windows sharing ONE `PlanadayRepository` stay live-synchronized via Room Flows!
 * - Persists user preferences in SQLite and encrypts sync bearer tokens via `SecureCredentialVault`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanadayWorkspaceController(
    val repository: PlanadayRepository,
    val useCases: PlanadayUseCases,
    val searchService: PlanadaySearchService,
    private val scope: CoroutineScope,
    platformLabel: String,
    val credentialVault: SecureCredentialVault? = null,
    val fileDialogBridge: NativeFileDialogBridge? = null,
) {
    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val draftManager = DraftSessionManager(repository) { SystemDomainClock.nowUtcIso() }

    private val _activeWorkspaceId = MutableStateFlow("ws_personal")
    private val _state = MutableStateFlow(
        PlanadayShellState(buildIdentity = BuildIdentity(platform = platformLabel))
    )
    val state: StateFlow<PlanadayShellState> = _state.asStateFlow()

    @Volatile
    private var cachedSyncToken: String? = null
    private var searchJob: Job? = null
    private var observersStarted = false

    // --- Feature-Scoped Reactive Flows (ZERO global reload fan-out!) ---

    fun observeKind(kind: RecordKind, limit: Int = 200): Flow<List<CanonicalRecord>> =
        _activeWorkspaceId.flatMapLatest { wsId ->
            repository.observeRecordsByKind(wsId, kind, limit = limit)
        }

    fun observeScheduleWindow(kind: RecordKind, startIso: String, endIso: String, limit: Int = 500): Flow<List<CanonicalRecord>> =
        _activeWorkspaceId.flatMapLatest { wsId ->
            repository.observeScheduleWindow(wsId, kind, startIso, endIso, limit = limit)
        }

    fun observeTrash(limit: Int = 250): Flow<List<CanonicalRecord>> =
        _activeWorkspaceId.flatMapLatest { wsId -> repository.observeTrash(wsId, limit) }

    fun observeDirtyDrafts(): Flow<List<DraftRecord>> =
        _activeWorkspaceId.flatMapLatest { wsId -> repository.observeDirtyDrafts(wsId) }

    fun observeAttachments(limit: Int = 200): Flow<List<AttachmentMeta>> =
        _activeWorkspaceId.flatMapLatest { wsId -> repository.observeWorkspaceAttachments(wsId, limit) }

    fun observeAutomationRuns(limit: Int = 100) =
        _activeWorkspaceId.flatMapLatest { wsId -> repository.observeAutomationRuns(wsId, limit) }

    fun bootstrapFresh() = scope.launch { bootstrapFreshSuspend() }

    suspend fun bootstrapFreshSuspend() {
        val devId = repository.ensurePersistentDeviceId()
        val personal = repository.ensureDefaultWorkspaceExists("ws_personal", "Personal")
        val existing = repository.listWorkspaces()
        if (existing.none { it.id == "ws_lab" }) {
            val now = SystemDomainClock.nowUtcIso()
            repository.createWorkspace(
                Workspace("ws_lab", "Laboratory", WorkspaceScope.LABORATORY, "#0F766E", "flask", now, now, isDefault = false)
            )
        }

        // Load persisted preferences & OS-protected sync token
        val savedPrefs = repository.getPreference(PREF_USER_SETTINGS)?.let {
            runCatching { json.decodeFromString<UserWorkspacePreferences>(it) }.getOrNull()
        } ?: UserWorkspacePreferences()

        val savedCalCfg = repository.getPreference(PREF_CALENDAR_CONFIG)?.let {
            runCatching { json.decodeFromString<CalendarViewConfig>(it) }.getOrNull()
        } ?: CalendarViewConfig()

        cachedSyncToken = credentialVault?.let { repository.readProtectedSecret(it, SECRET_SYNC_TOKEN) }?.takeIf { it.isNotBlank() }

        _activeWorkspaceId.value = personal.id
        _state.update {
            it.copy(
                initialized = true,
                workspaces = repository.listWorkspaces(),
                activeWorkspaceId = personal.id,
                preferences = savedPrefs,
                calendarConfig = savedCalCfg,
                syncBearerTokenPresent = !cachedSyncToken.isNullOrBlank(),
                totalActiveRecords = repository.db.recordDao().countActive(personal.id),
                deviceId = devId,
            )
        }
        startLightweightShellObservers()
    }

    private fun startLightweightShellObservers() {
        if (observersStarted) return
        observersStarted = true

        // 1. Live workspaces across all windows
        scope.launch {
            repository.observeWorkspaces().collect { wsList ->
                _state.update { it.copy(workspaces = wsList) }
            }
        }
        // 2. Live persisted preferences across all windows
        scope.launch {
            repository.observePreference(PREF_USER_SETTINGS).collect { prefJson ->
                if (!prefJson.isNullOrBlank()) {
                    runCatching { json.decodeFromString<UserWorkspacePreferences>(prefJson) }.onSuccess { prefs ->
                        _state.update { it.copy(preferences = prefs) }
                    }
                }
            }
        }
        // 3. Active workspace lightweight scalar counters (NO bulk entity loads!)
        scope.launch {
            _activeWorkspaceId.flatMapLatest { wsId -> repository.observeActiveCount(wsId) }.collect { count ->
                _state.update { it.copy(totalActiveRecords = count) }
            }
        }
        scope.launch {
            _activeWorkspaceId.flatMapLatest { wsId -> repository.observePendingOutboxCount(wsId) }.collect { pending ->
                _state.update { it.copy(syncSnapshot = it.syncSnapshot.copy(pendingOpsCount = pending)) }
            }
        }
        scope.launch {
            _activeWorkspaceId.flatMapLatest { wsId -> repository.observeUnresolvedConflicts(wsId) }.collect { list ->
                _state.update {
                    it.copy(
                        unresolvedConflicts = list,
                        syncSnapshot = it.syncSnapshot.copy(unresolvedConflicts = list.size),
                    )
                }
            }
        }
        scope.launch {
            _activeWorkspaceId.flatMapLatest { wsId -> repository.observeBlobReceipts(wsId) }.collect { receipts ->
                _state.update {
                    it.copy(
                        syncSnapshot = it.syncSnapshot.copy(
                            blobReceipts = receipts,
                            failedBlobTransfers = receipts.count { r -> r.status == com.planaday.nativeapp.core.syncprotocol.BlobTransferStatus.FAILED },
                        )
                    )
                }
            }
        }
    }

    // --- Workspace, Tabs & Preferences ---

    fun selectWorkspace(workspaceId: String) {
        _activeWorkspaceId.value = workspaceId
        _state.update { it.copy(activeWorkspaceId = workspaceId, statusBanner = null) }
    }

    fun createWorkspace(name: String, scopeType: WorkspaceScope, accentHex: String = "#0F766E") {
        if (name.isBlank()) return
        scope.launch {
            val now = SystemDomainClock.nowUtcIso()
            val id = "ws_${UUID.randomUUID().toString().take(8)}"
            repository.createWorkspace(Workspace(id, name.trim(), scopeType, accentHex, "folder", now, now, isDefault = false))
            selectWorkspace(id)
            setBanner("Created workspace '${name.trim()}'")
        }
    }

    fun selectRoute(route: PlanadayRoute) {
        _state.update { cur ->
            val tabs = if (route in cur.openTabs) cur.openTabs else (cur.openTabs + route).takeLast(12)
            cur.copy(activeRoute = route, openTabs = tabs)
        }
    }

    fun closeTab(route: PlanadayRoute) {
        _state.update { cur ->
            val remaining = cur.openTabs.filterNot { it == route }
            val nextTabs = remaining.ifEmpty { listOf(PlanadayRoute.TODAY) }
            val nextActive = if (cur.activeRoute == route) nextTabs.last() else cur.activeRoute
            cur.copy(openTabs = nextTabs, activeRoute = nextActive)
        }
    }

    fun updatePreferences(transform: (UserWorkspacePreferences) -> UserWorkspacePreferences) {
        val next = transform(_state.value.preferences)
        _state.update { it.copy(preferences = next) }
        scope.launch {
            repository.savePreference(PREF_USER_SETTINGS, json.encodeToString(next))
        }
    }

    fun setThemeMode(mode: PlanadayThemeMode) = updatePreferences { it.copy(themeMode = mode) }
    fun setDensity(density: PlanadayDensity) = updatePreferences { it.copy(density = density) }
    fun setAccentHex(hex: String) = updatePreferences { it.copy(accentHex = hex) }
    fun setFontScale(scale: Float) = updatePreferences { it.copy(fontScale = scale.coerceIn(0.85f, 1.35f)) }

    fun updateCalendarConfig(transform: (CalendarViewConfig) -> CalendarViewConfig) {
        val next = transform(_state.value.calendarConfig)
        _state.update { it.copy(calendarConfig = next) }
        scope.launch { repository.savePreference(PREF_CALENDAR_CONFIG, json.encodeToString(next)) }
    }

    fun setBanner(msg: String?) {
        _state.update { it.copy(statusBanner = msg) }
    }

    // --- Domain Actions (Single-Record Atomic Writes — Room Flows Auto-Update Active Views!) ---

    fun createOrUpdateRecord(
        kind: RecordKind,
        title: String,
        payloadJson: String,
        summaryText: String = "",
        tags: List<String> = emptyList(),
        existingId: String? = null,
        recordRevision: Boolean = false,
        checkpointLabel: String? = null,
        status: RecordStatus = RecordStatus.ACTIVE,
        scheduleStartIso: String? = null,
        scheduleEndIso: String? = null,
        onSaved: ((CanonicalRecord) -> Unit)? = null,
    ) {
        if (title.isBlank()) return
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            val saved = if (existingId != null) {
                repository.updateRecordAtomic(
                    workspaceId = wsId,
                    recordId = existingId,
                    recordRevision = recordRevision,
                    checkpointLabel = checkpointLabel,
                    scheduleStartIso = scheduleStartIso,
                    scheduleEndIso = scheduleEndIso,
                ) {
                    it.copy(
                        title = title.trim(),
                        payloadJson = payloadJson,
                        summaryText = summaryText,
                        tags = tags,
                        status = status,
                    )
                }
            } else {
                repository.createRecord(
                    workspaceId = wsId,
                    kind = kind,
                    title = title.trim(),
                    payloadJson = payloadJson,
                    summaryText = summaryText,
                    tags = tags,
                    status = status,
                    recordRevision = recordRevision,
                    scheduleStartIso = scheduleStartIso,
                    scheduleEndIso = scheduleEndIso,
                )
            }
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Saved '${saved.title}'") }
            onSaved?.invoke(saved)
        }
    }

    fun quickCreateTask(
        title: String,
        priority: Priority = Priority.MEDIUM,
        estimateMin: Int = 30,
        doDateIso: String? = SystemDomainClock.todayIso(),
        dueDateIso: String? = null,
        notes: String = "",
        tags: List<String> = listOf("task"),
    ) = scope.launch { quickCreateTaskSuspend(title, priority, estimateMin, doDateIso, dueDateIso, notes, tags) }

    suspend fun quickCreateTaskSuspend(
        title: String,
        priority: Priority = Priority.MEDIUM,
        estimateMin: Int = 30,
        doDateIso: String? = SystemDomainClock.todayIso(),
        dueDateIso: String? = null,
        notes: String = "",
        tags: List<String> = listOf("task"),
    ): CanonicalRecord? {
        if (title.isBlank()) return null
        val wsId = _state.value.activeWorkspaceId
        val now = SystemDomainClock.nowUtcIso()
        val task = TaskItem(
            id = UUID.randomUUID().toString(),
            workspaceId = wsId,
            title = title.trim(),
            notes = notes,
            priority = priority,
            doDateIso = doDateIso,
            dueDateIso = dueDateIso,
            estimateMin = estimateMin,
            createdAtUtc = now,
            updatedAtUtc = now,
        )
        val rec = repository.createRecord(
            wsId, RecordKind.TASK, task.title, json.encodeToString(task),
            notes.ifBlank { "${priority.name} · ${estimateMin}m" },
            tags, id = task.id,
            scheduleStartIso = doDateIso ?: dueDateIso,
            scheduleEndIso = dueDateIso ?: doDateIso,
        )
        _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Saved task '${task.title}'") }
        return rec
    }

    fun updateTask(task: TaskItem) {
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            repository.updateRecordAtomic(
                wsId, task.id,
                scheduleStartIso = task.doDateIso ?: task.dueDateIso,
                scheduleEndIso = task.dueDateIso ?: task.doDateIso,
            ) {
                it.copy(
                    title = task.title,
                    status = task.status,
                    payloadJson = json.encodeToString(task),
                    summaryText = task.notes.ifBlank { "${task.priority.name} · ${task.estimateMin ?: 30}m" },
                )
            }
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Updated '${task.title}'") }
        }
    }

    fun moveTaskColumn(taskId: String, column: String) {
        scope.launch {
            useCases.moveTaskKanbanColumn(_state.value.activeWorkspaceId, taskId, column)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Moved task to $column") }
        }
    }

    fun scheduleCanonicalTaskOnCalendar(task: TaskItem, dateIso: String, startTime: String, endTime: String) {
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            val updatedTask = task.copy(doDateIso = dateIso, manualStartTimeLocal = startTime)
            repository.updateRecordAtomic(wsId, task.id, scheduleStartIso = dateIso, scheduleEndIso = task.dueDateIso ?: dateIso) {
                it.copy(payloadJson = json.encodeToString(updatedTask))
            }
            val now = SystemDomainClock.nowUtcIso()
            val ev = CalendarEvent(
                id = "ev_task_${task.id}",
                workspaceId = wsId,
                calendarId = "cal_tasks",
                title = task.title,
                startDateIso = dateIso,
                endDateIso = dateIso,
                startTimeLocal = startTime,
                endTimeLocal = endTime,
                linkedActionId = task.id,
                createdAtUtc = now,
                updatedAtUtc = now,
            )
            val existingEv = repository.getRecord(wsId, ev.id)
            if (existingEv != null) {
                repository.updateRecordAtomic(wsId, ev.id, scheduleStartIso = dateIso, scheduleEndIso = dateIso) {
                    it.copy(title = ev.title, payloadJson = json.encodeToString(ev), summaryText = "$dateIso $startTime–$endTime")
                }
            } else {
                repository.createRecord(
                    wsId, RecordKind.EVENT, ev.title, json.encodeToString(ev), "$dateIso $startTime–$endTime",
                    listOf("event", "scheduled-task"), id = ev.id, scheduleStartIso = dateIso, scheduleEndIso = dateIso,
                )
                repository.linkRecords(wsId, ev.id, RecordKind.EVENT, task.id, RecordKind.TASK, RelationKind.SCHEDULED_FROM)
            }
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Scheduled '${task.title}' for $dateIso $startTime–$endTime") }
        }
    }

    fun saveCalendarEvent(event: CalendarEvent, isNew: Boolean = true) {
        if (event.title.isBlank()) return
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            val summary = if (event.allDay) "${event.startDateIso} (All Day)" else "${event.startDateIso} ${event.startTimeLocal}–${event.endTimeLocal}"
            if (isNew) {
                repository.createRecord(
                    wsId, RecordKind.EVENT, event.title.trim(), json.encodeToString(event), summary,
                    listOf("event", event.calendarId), id = event.id,
                    scheduleStartIso = event.startDateIso, scheduleEndIso = event.endDateIso,
                )
            } else {
                repository.updateRecordAtomic(
                    wsId, event.id, scheduleStartIso = event.startDateIso, scheduleEndIso = event.endDateIso,
                ) {
                    it.copy(title = event.title.trim(), payloadJson = json.encodeToString(event), summaryText = summary)
                }
            }
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Saved event '${event.title.trim()}'") }
        }
    }

    fun createCalendarEvent(title: String, startTime: String, endTime: String, dateIso: String = SystemDomainClock.todayIso(), calendarId: String = "cal_default", allDay: Boolean = false) {
        if (title.isBlank()) return
        val now = SystemDomainClock.nowUtcIso()
        val ev = CalendarEvent(
            id = UUID.randomUUID().toString(),
            workspaceId = _state.value.activeWorkspaceId,
            calendarId = calendarId,
            title = title.trim(),
            startDateIso = dateIso,
            endDateIso = dateIso,
            startTimeLocal = if (allDay) null else startTime,
            endTimeLocal = if (allDay) null else endTime,
            allDay = allDay,
            createdAtUtc = now,
            updatedAtUtc = now,
        )
        saveCalendarEvent(ev, isNew = true)
    }

    fun createChore(title: String, room: String, intervalDays: Int, difficulty: Int = 2) {
        if (title.isBlank()) return
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            val now = SystemDomainClock.nowUtcIso()
            val today = SystemDomainClock.todayIso()
            val chore = ChoreItem(
                id = UUID.randomUUID().toString(),
                workspaceId = wsId,
                title = title.trim(),
                room = room.ifBlank { "General" },
                intervalDays = intervalDays.coerceAtLeast(1),
                difficulty = difficulty.coerceIn(1, 5),
                nextDueDateIso = today,
                createdAtUtc = now,
                updatedAtUtc = now,
            )
            repository.createRecord(
                wsId, RecordKind.CHORE, chore.title, json.encodeToString(chore), chore.room,
                listOf("chore", chore.room), id = chore.id, scheduleStartIso = today, scheduleEndIso = today,
            )
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Added chore '${chore.title}'") }
        }
    }

    fun completeChoreToday(choreId: String, assigneeId: String? = null) {
        scope.launch {
            useCases.completeChore(_state.value.activeWorkspaceId, choreId, SystemDomainClock.todayIso(), assigneeId = assigneeId)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Completed chore") }
        }
    }

    fun saveNote(title: String, body: String, existingNoteId: String? = null, notebookId: String = "nb_default", checkpointLabel: String? = "Saved") {
        if (title.isBlank() && body.isBlank()) return
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            val now = SystemDomainClock.nowUtcIso()
            val cleanTitle = title.ifBlank { "Untitled Note" }
            val noteModel = NoteItem(
                id = existingNoteId ?: UUID.randomUUID().toString(),
                workspaceId = wsId,
                notebookId = notebookId,
                title = cleanTitle,
                content = body,
                createdAtUtc = now,
                updatedAtUtc = now,
            )
            val payload = json.encodeToString(noteModel)
            if (existingNoteId != null) {
                repository.updateRecordAtomic(wsId, existingNoteId, recordRevision = true, checkpointLabel = checkpointLabel) {
                    it.copy(title = cleanTitle, payloadJson = payload, summaryText = body.take(240))
                }
            } else {
                repository.createRecord(wsId, RecordKind.NOTE, cleanTitle, payload, body.take(240), listOf("note"), id = noteModel.id, recordRevision = true)
            }
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Saved '$cleanTitle'") }
        }
    }

    fun logMeal(foodName: String, category: String = "lunch") {
        if (foodName.isBlank()) return
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            useCases.logMealFromToday(
                wsId,
                FoodLogItem(UUID.randomUUID().toString(), wsId, SystemDomainClock.todayIso(), null, category, foodName.trim()),
            )
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Logged meal '${foodName.trim()}'") }
        }
    }

    fun addLabWetNirPair(sampleId: String, wet: Double, nir: Double, unit: String = "%", commodity: String = "Grain", analysis: String = "Moisture") {
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            val pair = LabWetNirPair(
                id = UUID.randomUUID().toString(),
                workspaceId = wsId,
                sampleId = sampleId.ifBlank { "SMP-${System.currentTimeMillis() % 1000}" },
                commodity = commodity,
                analysis = analysis,
                wetMethod = "Reference Method",
                wetResult = wet,
                nirResult = nir,
                unit = unit,
                instrumentId = "NIR-01",
                calibrationModel = "Cal-v1",
                dateTestedIso = SystemDomainClock.todayIso(),
                analyst = "Analyst",
            )
            repository.createRecord(wsId, RecordKind.LAB_WET_NIR, "${pair.sampleId} (${pair.analysis})", json.encodeToString(pair), "Wet=$wet NIR=$nir $unit", listOf("lab", "wet-nir"), id = pair.id)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Added pair ${pair.sampleId}") }
        }
    }

    fun moveRecordToTrash(recordId: String) {
        scope.launch {
            repository.moveToTrash(_state.value.activeWorkspaceId, recordId)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Moved to Trash") }
        }
    }

    fun restoreFromTrash(recordId: String) {
        scope.launch {
            repository.restoreFromTrash(_state.value.activeWorkspaceId, recordId)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Restored from Trash") }
        }
    }

    fun purgeFromTrash(recordId: String) {
        scope.launch {
            repository.purgeFromTrash(_state.value.activeWorkspaceId, recordId)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Permanently deleted item") }
        }
    }

    fun runSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = scope.launch {
            val hits = if (query.isBlank()) emptyList() else searchService.search(_state.value.activeWorkspaceId, query)
            _state.update { it.copy(searchHits = hits) }
        }
    }

    fun optInSeedSyntheticDemo() {
        scope.launch {
            useCases.seedOptInSyntheticDemo(_state.value.activeWorkspaceId)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Loaded sample demonstration workspace") }
        }
    }

    // --- Backup & Restoration Workflow (§27) ---

    fun exportBackupToDisk(passphrase: String? = null) {
        scope.launch {
            try {
                val artifact = useCases.exportVerifiedBackup(_state.value.activeWorkspaceId, passphrase?.takeIf { it.isNotBlank() })
                val verified = ArchiveInterchange.verifyAndReadBackup(artifact.bytes, passphrase?.takeIf { it.isNotBlank() })
                val savedPath = fileDialogBridge?.pickFileToSave("Save Planaday Backup", artifact.filename, artifact.bytes)
                val destNote = savedPath?.let { "Saved to $it" } ?: "Verified in memory (${artifact.bytes.size} B)"
                _state.update {
                    it.copy(
                        lastBackupSummary = "$destNote · ${verified.manifest.recordCount} records · SHA-256 ${verified.manifest.recordsJsonSha256.take(12)}…",
                        statusBanner = "Backup created and verified",
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(statusBanner = "Backup failed: ${t.message}") }
            }
        }
    }

    fun previewAndRestoreBackupFromBytes(bytes: ByteArray, passphrase: String? = null, commitRestore: Boolean = false, onPreview: ((String) -> Unit)? = null) {
        scope.launch {
            try {
                val verified = ArchiveInterchange.verifyAndReadBackup(bytes, passphrase?.takeIf { it.isNotBlank() })
                val summary = "Valid backup (${verified.manifest.formatId}): ${verified.manifest.recordCount} records, ${verified.manifest.blobCount} attachments, SHA-256 verified."
                onPreview?.invoke(summary)
                if (commitRestore) {
                    useCases.restoreVerifiedBackup(bytes, passphrase?.takeIf { it.isNotBlank() })
                    _state.update { it.copy(statusBanner = "Restored ${verified.manifest.recordCount} records cleanly") }
                } else {
                    _state.update { it.copy(lastBackupSummary = summary, statusBanner = "Backup verified — ready to restore") }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(statusBanner = "Restore check failed: ${t.message}") }
            }
        }
    }

    // --- Optional Self-Hosted Sync with Persistent Encrypted Credentials (§28) ---

    fun pairAndSyncWithServer(pairingCode: String, deviceName: String, allowReplacementReconcile: Boolean = false) {
        scope.launch {
            val prefs = _state.value.preferences
            val client = PlanadaySyncClient(
                repository = repository,
                httpClient = PlanadaySyncClient.createDefaultHttpClient(),
                serverBaseUrl = prefs.syncServerUrl,
                bearerTokenProvider = { cachedSyncToken },
                allowDevelopmentHttp = prefs.allowDevelopmentHttp,
            )
            try {
                if (pairingCode.isNotBlank()) {
                    val session = client.redeemPairingCode(pairingCode.trim(), deviceName)
                    cachedSyncToken = session.bearerToken
                    credentialVault?.let { repository.saveProtectedSecret(it, SECRET_SYNC_TOKEN, session.bearerToken) }
                    _state.update { it.copy(syncBearerTokenPresent = true) }
                }
                val policy = SelectiveSyncPolicy(
                    syncedWorkspaceIds = prefs.selectiveSyncWorkspaceIds,
                    maxAutoDownloadAttachmentBytes = prefs.maxAutoBlobMb.toLong() * 1024L * 1024L,
                )
                val snap = client.syncWorkspace(
                    workspaceId = _state.value.activeWorkspaceId,
                    allowReplacementReconcile = allowReplacementReconcile,
                    selectivePolicy = policy,
                )
                _state.update {
                    it.copy(
                        syncSnapshot = snap,
                        saveState = if (snap.status == SyncHealthStatus.SYNCED) SaveState.SYNCED else SaveState.SYNC_PENDING,
                        statusBanner = "Sync: ${snap.status.name.lowercase().replace('_', ' ')}",
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        statusBanner = "Offline mode active (sync unreachable): ${t.message}",
                        syncSnapshot = it.syncSnapshot.copy(status = SyncHealthStatus.ERROR, lastError = t.message),
                    )
                }
            }
        }
    }

    fun forgetSyncCredential() {
        scope.launch {
            cachedSyncToken = null
            repository.clearProtectedSecret(SECRET_SYNC_TOKEN)
            _state.update { it.copy(syncBearerTokenPresent = false, statusBanner = "Disconnected sync credential from this device") }
        }
    }

    fun resolveConflict(conflictId: String, mode: String) {
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            when (mode) {
                "KEEP_BOTH" -> repository.resolveConflictKeepBoth(wsId, conflictId)
                "ACCEPT_REMOTE" -> repository.resolveConflictAcceptRemote(wsId, conflictId)
                "KEEP_LOCAL" -> repository.resolveConflictKeepLocal(wsId, conflictId)
            }
            _state.update { it.copy(statusBanner = "Conflict resolved ($mode)") }
        }
    }

    fun computeRoomHealth(chores: List<ChoreItem>): List<RoomHealthSummary> =
        ChoreEngine.computeRoomHealth(chores, SystemDomainClock.todayIso())

    companion object {
        const val PREF_USER_SETTINGS: String = "planaday.user_preferences.v2"
        const val PREF_CALENDAR_CONFIG: String = "planaday.calendar_config.v2"
        const val SECRET_SYNC_TOKEN: String = "sync_bearer_token"

        fun routeToPrimaryKind(route: PlanadayRoute): RecordKind = when (route) {
            PlanadayRoute.TASKS, PlanadayRoute.KANBAN, PlanadayRoute.TASK_CALENDAR -> RecordKind.TASK
            PlanadayRoute.CHORES -> RecordKind.CHORE
            PlanadayRoute.FLOW_CALENDAR, PlanadayRoute.PERIOD_CALENDAR -> RecordKind.EVENT
            PlanadayRoute.GOALS -> RecordKind.GOAL
            PlanadayRoute.HABITS -> RecordKind.HABIT
            PlanadayRoute.NOTES -> RecordKind.NOTE
            PlanadayRoute.THOUGHT_INBOX -> RecordKind.THOUGHT
            PlanadayRoute.DIARY -> RecordKind.DIARY
            PlanadayRoute.LIFE_EVENTS -> RecordKind.LIFE_EVENT
            PlanadayRoute.SPACED_REVIEW -> RecordKind.REVIEW_CARD
            PlanadayRoute.ALARMS -> RecordKind.ALARM
            PlanadayRoute.TIMERS -> RecordKind.TIMER
            PlanadayRoute.STOPWATCH -> RecordKind.TIME_LEDGER
            PlanadayRoute.COUNTDOWNS -> RecordKind.COUNTDOWN
            PlanadayRoute.PHYSICAL_PLANNER -> RecordKind.PLANNER_PAGE
            PlanadayRoute.WHITEBOARD -> RecordKind.WHITEBOARD
            PlanadayRoute.RECIPES -> RecordKind.RECIPE
            PlanadayRoute.PANTRY -> RecordKind.PANTRY_LOT
            PlanadayRoute.MEAL_PLANNER -> RecordKind.MEAL_PLAN
            PlanadayRoute.GROCERIES -> RecordKind.GROCERY_ITEM
            PlanadayRoute.HOUSEHOLD -> RecordKind.HOUSEHOLD_MEMBER
            PlanadayRoute.PEOPLE_GIFTS -> RecordKind.PERSON
            PlanadayRoute.HEALTH_MEDS -> RecordKind.MEDICATION
            PlanadayRoute.FINANCE -> RecordKind.EXPENSE
            PlanadayRoute.MEDIA_HOBBIES -> RecordKind.MEDIA_ITEM
            PlanadayRoute.TRAVEL -> RecordKind.TRIP
            PlanadayRoute.HOME_ASSETS -> RecordKind.HOME_ASSET
            PlanadayRoute.LAB_WORKSPACE -> RecordKind.LAB_SAMPLE
            PlanadayRoute.CALCULATOR -> RecordKind.CALC_SAVED
            PlanadayRoute.SPREADSHEETS -> RecordKind.SPREADSHEET
            PlanadayRoute.PRESENTATIONS -> RecordKind.PRESENTATION
            PlanadayRoute.UNIVERSAL_FILES -> RecordKind.FILE_FOLDER
            PlanadayRoute.PHOTO_STUDIO -> RecordKind.PHOTO_ASSET
            PlanadayRoute.PROJECTS -> RecordKind.PROJECT
            PlanadayRoute.GAME_DEV -> RecordKind.GAME_PROJECT
            PlanadayRoute.BOOK_STUDIO -> RecordKind.BOOK_PROJECT
            PlanadayRoute.CODE_STUDIO -> RecordKind.CODE_PROJECT
            PlanadayRoute.REPORTS -> RecordKind.DECISION_JOURNAL
            PlanadayRoute.AUTOMATIONS -> RecordKind.AUTOMATION_RULE
            PlanadayRoute.CUSTOM_SPACES -> RecordKind.CUSTOM_SPACE
            PlanadayRoute.RITUALS -> RecordKind.CONTEXT_MODE
            else -> RecordKind.NOTE
        }
    }
}
