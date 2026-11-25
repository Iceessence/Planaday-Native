package com.planaday.nativeapp.core.designtokens

import com.planaday.nativeapp.core.documents.DraftSessionManager
import com.planaday.nativeapp.core.domain.CalendarDateMath
import com.planaday.nativeapp.core.domain.ChoreEngine
import com.planaday.nativeapp.core.domain.ExactDecimal
import com.planaday.nativeapp.core.domain.RoomHealthSummary
import com.planaday.nativeapp.core.domain.SystemDomainClock
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.persistence.SearchHit
import com.planaday.nativeapp.core.search.PlanadaySearchService
import com.planaday.nativeapp.core.syncclient.PlanadaySyncClient
import com.planaday.nativeapp.core.syncprotocol.SyncHealthStatus
import com.planaday.nativeapp.core.syncprotocol.SyncStatusSnapshot
import com.planaday.nativeapp.core.usecases.PlanadayUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class PlanadayUiState(
    val initialized: Boolean = false,
    val workspaces: List<Workspace> = emptyList(),
    val activeWorkspaceId: String = "ws_personal",
    val activeRoute: PlanadayRoute = PlanadayRoute.TODAY,
    val themeMode: PlanadayThemeMode = PlanadayThemeMode.WARM_PAPER_LIGHT,
    val density: PlanadayDensity = PlanadayDensity.STANDARD,
    val saveState: SaveState = SaveState.IDLE,
    val statusBanner: String? = null,
    val totalActiveRecords: Long = 0L,
    val tasks: List<TaskItem> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val chores: List<ChoreItem> = emptyList(),
    val roomHealth: List<RoomHealthSummary> = emptyList(),
    val notes: List<CanonicalRecord> = emptyList(),
    val foodLogs: List<FoodLogItem> = emptyList(),
    val labWetNirPairs: List<LabWetNirPair> = emptyList(),
    val expenses: List<ExpenseItem> = emptyList(),
    val trashRecords: List<CanonicalRecord> = emptyList(),
    val recoverableDrafts: List<DraftRecord> = emptyList(),
    val currentRouteRecords: List<CanonicalRecord> = emptyList(),
    val searchQuery: String = "",
    val searchHits: List<SearchHit> = emptyList(),
    val calendarConfig: CalendarViewConfig = CalendarViewConfig(),
    val syncServerUrl: String = "http://127.0.0.1:8420",
    val syncBearerToken: String? = null,
    val syncSnapshot: SyncStatusSnapshot = SyncStatusSnapshot(
        status = SyncHealthStatus.IDLE_OFFLINE,
        pendingOpsCount = 0,
        pendingBlobUploads = 0,
        pendingBlobDownloads = 0,
        failedBlobTransfers = 0,
        unresolvedConflicts = 0,
    ),
    val buildIdentity: BuildIdentity = BuildIdentity(),
    val lastBackupSummary: String? = null,
)

/**
 * Scoped reactive state controller bridging Room KMP + Bundled SQLite, DraftSessionManager,
 * FTS5 search, cross-feature PlanadayUseCases, and optional Ktor Sync Client.
 * Replaces legacy monolithic global AppContext with clean, workspace-scoped reactive snapshots.
 */
class PlanadayWorkspaceController(
    val repository: PlanadayRepository,
    val useCases: PlanadayUseCases,
    val searchService: PlanadaySearchService,
    private val scope: CoroutineScope,
    platformLabel: String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val draftManager = DraftSessionManager(repository) { SystemDomainClock.nowUtcIso() }

    private val _state = MutableStateFlow(
        PlanadayUiState(buildIdentity = BuildIdentity(platform = platformLabel))
    )
    val state: StateFlow<PlanadayUiState> = _state.asStateFlow()

    fun bootstrapFresh() = scope.launch { bootstrapFreshSuspend() }

    suspend fun bootstrapFreshSuspend() {
        val personal = repository.ensureDefaultWorkspaceExists("ws_personal", "Personal")
        val existing = repository.listWorkspaces()
        val labExists = existing.any { it.id == "ws_lab" }
        if (!labExists) {
            val now = SystemDomainClock.nowUtcIso()
            repository.createWorkspace(
                Workspace("ws_lab", "Laboratory Operations", WorkspaceScope.LABORATORY, "#0F766E", "flask", now, now, isDefault = false)
            )
        }
        _state.update { it.copy(initialized = true, activeWorkspaceId = personal.id) }
        refreshWorkspace()
    }

    fun selectWorkspace(workspaceId: String) {
        _state.update { it.copy(activeWorkspaceId = workspaceId, statusBanner = "Switched workspace") }
        scope.launch { refreshWorkspace() }
    }

    fun selectRoute(route: PlanadayRoute) {
        _state.update { it.copy(activeRoute = route) }
        scope.launch { refreshCurrentRouteRecords() }
    }

    fun setThemeMode(mode: PlanadayThemeMode) {
        _state.update { it.copy(themeMode = mode) }
    }

    fun setDensity(density: PlanadayDensity) {
        _state.update { it.copy(density = density) }
    }

    fun updateCalendarConfig(transform: (CalendarViewConfig) -> CalendarViewConfig) {
        _state.update { it.copy(calendarConfig = transform(it.calendarConfig)) }
    }

    fun quickCreateTask(title: String, priority: Priority = Priority.MEDIUM, estimateMin: Int = 30, dueDateIso: String? = null) =
        scope.launch { quickCreateTaskSuspend(title, priority, estimateMin, dueDateIso) }

    suspend fun quickCreateTaskSuspend(title: String, priority: Priority = Priority.MEDIUM, estimateMin: Int = 30, dueDateIso: String? = null) {
        if (title.isBlank()) return
        val wsId = _state.value.activeWorkspaceId
        val now = SystemDomainClock.nowUtcIso()
        val today = SystemDomainClock.todayIso()
        val task = TaskItem(
            id = UUID.randomUUID().toString(),
            workspaceId = wsId,
            title = title.trim(),
            priority = priority,
            doDateIso = today,
            dueDateIso = dueDateIso,
            estimateMin = estimateMin,
            createdAtUtc = now,
            updatedAtUtc = now,
        )
        repository.createRecord(wsId, RecordKind.TASK, task.title, json.encodeToString(task), task.notes, listOf("task"), id = task.id)
        _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Saved task '${task.title}'") }
        refreshWorkspace()
    }

    fun moveTaskColumn(taskId: String, column: String) {
        scope.launch {
            useCases.moveTaskKanbanColumn(_state.value.activeWorkspaceId, taskId, column)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Updated canonical task to $column") }
            refreshWorkspace()
        }
    }

    fun createCalendarEvent(title: String, startTime: String, endTime: String, dateIso: String = SystemDomainClock.todayIso()) {
        if (title.isBlank()) return
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            val now = SystemDomainClock.nowUtcIso()
            val ev = CalendarEvent(
                id = UUID.randomUUID().toString(),
                workspaceId = wsId,
                calendarId = "cal_default",
                title = title.trim(),
                startDateIso = dateIso,
                endDateIso = dateIso,
                startTimeLocal = startTime,
                endTimeLocal = endTime,
                createdAtUtc = now,
                updatedAtUtc = now,
            )
            repository.createRecord(wsId, RecordKind.EVENT, ev.title, json.encodeToString(ev), "$dateIso $startTime-$endTime", listOf("event"), id = ev.id)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Scheduled '${ev.title}'") }
            refreshWorkspace()
        }
    }

    fun createChore(title: String, room: String, intervalDays: Int) {
        if (title.isBlank()) return
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            val now = SystemDomainClock.nowUtcIso()
            val chore = ChoreItem(
                id = UUID.randomUUID().toString(),
                workspaceId = wsId,
                title = title.trim(),
                room = room.ifBlank { "General" },
                intervalDays = intervalDays.coerceAtLeast(1),
                nextDueDateIso = SystemDomainClock.todayIso(),
                createdAtUtc = now,
                updatedAtUtc = now,
            )
            repository.createRecord(wsId, RecordKind.CHORE, chore.title, json.encodeToString(chore), chore.room, listOf("chore", chore.room), id = chore.id)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Added chore '${chore.title}'") }
            refreshWorkspace()
        }
    }

    fun completeChoreToday(choreId: String) {
        scope.launch {
            useCases.completeChore(_state.value.activeWorkspaceId, choreId, SystemDomainClock.todayIso())
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Completed chore & recalculated next due date") }
            refreshWorkspace()
        }
    }

    fun saveNote(title: String, body: String, existingNoteId: String? = null) {
        if (title.isBlank() && body.isBlank()) return
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            val cleanTitle = title.ifBlank { "Untitled Note" }
            val payload = json.encodeToString(mapOf("content" to body))
            if (existingNoteId != null) {
                repository.updateRecordAtomic(wsId, existingNoteId, recordRevision = true, checkpointLabel = "Saved") {
                    it.copy(title = cleanTitle, payloadJson = payload, summaryText = body.take(240))
                }
            } else {
                repository.createRecord(wsId, RecordKind.NOTE, cleanTitle, payload, body.take(240), listOf("note"), recordRevision = true)
            }
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Note '$cleanTitle' saved with revision") }
            refreshWorkspace()
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
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Logged meal '$foodName' to Food History") }
            refreshWorkspace()
        }
    }

    fun addLabWetNirPair(sampleId: String, wet: Double, nir: Double, unit: String = "%") {
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            val pair = LabWetNirPair(
                id = UUID.randomUUID().toString(),
                workspaceId = wsId,
                sampleId = sampleId.ifBlank { "SMP-${System.currentTimeMillis() % 1000}" },
                commodity = "Grain",
                analysis = "Moisture",
                wetMethod = "AOAC",
                wetResult = wet,
                nirResult = nir,
                unit = unit,
                instrumentId = "NIR-01",
                calibrationModel = "Cal-v1",
                dateTestedIso = SystemDomainClock.todayIso(),
                analyst = "Analyst",
            )
            repository.createRecord(wsId, RecordKind.LAB_WET_NIR, "${pair.sampleId} (${pair.analysis})", json.encodeToString(pair), "Wet=$wet NIR=$nir $unit", listOf("lab", "wet-nir"), id = pair.id)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Added Wet vs NIR pair ${pair.sampleId}") }
            refreshWorkspace()
        }
    }

    fun addRecordForRoute(route: PlanadayRoute, title: String, summary: String) {
        if (title.isBlank()) return
        scope.launch {
            val wsId = _state.value.activeWorkspaceId
            val kind = routeToPrimaryKind(route)
            repository.createRecord(wsId, kind, title.trim(), json.encodeToString(mapOf("notes" to summary)), summary.trim(), listOf(route.id))
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Saved ${kind.name} '${title.trim()}'") }
            refreshWorkspace()
        }
    }

    fun moveRecordToTrash(recordId: String) {
        scope.launch {
            repository.moveToTrash(_state.value.activeWorkspaceId, recordId)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Moved to 30-day Trash") }
            refreshWorkspace()
        }
    }

    fun restoreFromTrash(recordId: String) {
        scope.launch {
            repository.restoreFromTrash(_state.value.activeWorkspaceId, recordId)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Restored record from Trash") }
            refreshWorkspace()
        }
    }

    fun runSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
        scope.launch {
            val hits = if (query.isBlank()) emptyList() else searchService.search(_state.value.activeWorkspaceId, query)
            _state.update { it.copy(searchHits = hits) }
        }
    }

    fun optInSeedSyntheticDemo() {
        scope.launch {
            useCases.seedOptInSyntheticDemo(_state.value.activeWorkspaceId)
            _state.update { it.copy(saveState = SaveState.LOCALLY_SAVED, statusBanner = "Loaded opt-in synthetic demo records (O-004)") }
            refreshWorkspace()
        }
    }

    fun runVerifiedBackupCheck() {
        scope.launch {
            try {
                val artifact = useCases.exportVerifiedBackup(_state.value.activeWorkspaceId)
                val verified = com.planaday.nativeapp.core.interchange.ArchiveInterchange.verifyAndReadBackup(artifact.bytes)
                _state.update {
                    it.copy(
                        lastBackupSummary = "Verified ${artifact.filename} (${artifact.bytes.size} B, ${verified.manifest.recordCount} records, SHA-256 ${verified.manifest.recordsJsonSha256.take(12)}…)",
                        statusBanner = "Backup archive created & SHA-256 verified OK",
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(statusBanner = "Backup failed: ${t.message}") }
            }
        }
    }

    fun updateSyncServerUrl(url: String) {
        _state.update { it.copy(syncServerUrl = url) }
    }

    fun pairAndSyncWithServer(pairingCode: String, deviceName: String) {
        scope.launch {
            val client = PlanadaySyncClient(
                repository = repository,
                httpClient = PlanadaySyncClient.createDefaultHttpClient(),
                serverBaseUrl = _state.value.syncServerUrl,
                bearerTokenProvider = { _state.value.syncBearerToken },
            )
            try {
                if (pairingCode.isNotBlank()) {
                    val session = client.redeemPairingCode(pairingCode.trim(), deviceName)
                    _state.update { it.copy(syncBearerToken = session.bearerToken) }
                }
                val snap = client.syncWorkspace(_state.value.activeWorkspaceId)
                _state.update {
                    it.copy(
                        syncSnapshot = snap,
                        saveState = if (snap.status == SyncHealthStatus.SYNCED) SaveState.SYNCED else SaveState.SYNC_PENDING,
                        statusBanner = "Sync status: ${snap.status} (pending=${snap.totalPendingCount})",
                    )
                }
                refreshWorkspace()
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        statusBanner = "Sync unavailable (staying 100% offline-safe): ${t.message}",
                        syncSnapshot = it.syncSnapshot.copy(status = SyncHealthStatus.ERROR, lastError = t.message),
                    )
                }
            }
        }
    }

    suspend fun refreshWorkspace() {
        val wsId = _state.value.activeWorkspaceId
        val workspaces = repository.listWorkspaces()
        val total = repository.db.recordDao().countActive(wsId)
        val taskRecs = repository.listByKind(wsId, RecordKind.TASK)
        val tasks = taskRecs.mapNotNull { runCatching { json.decodeFromString<TaskItem>(it.payloadJson) }.getOrNull() }
        val eventRecs = repository.listByKind(wsId, RecordKind.EVENT)
        val events = eventRecs.mapNotNull { runCatching { json.decodeFromString<CalendarEvent>(it.payloadJson) }.getOrNull() }
        val choreRecs = repository.listByKind(wsId, RecordKind.CHORE)
        val chores = choreRecs.mapNotNull { runCatching { json.decodeFromString<ChoreItem>(it.payloadJson) }.getOrNull() }
        val roomHealth = ChoreEngine.computeRoomHealth(chores, SystemDomainClock.todayIso())
        val notes = repository.listByKind(wsId, RecordKind.NOTE)
        val foodLogs = repository.listByKind(wsId, RecordKind.FOOD_LOG).mapNotNull {
            runCatching { json.decodeFromString<FoodLogItem>(it.payloadJson) }.getOrNull()
        }
        val wetNir = repository.listByKind(wsId, RecordKind.LAB_WET_NIR).mapNotNull {
            runCatching { json.decodeFromString<LabWetNirPair>(it.payloadJson) }.getOrNull()
        }
        val expenses = repository.listByKind(wsId, RecordKind.EXPENSE).mapNotNull {
            runCatching { json.decodeFromString<ExpenseItem>(it.payloadJson) }.getOrNull()
        }
        val trash = repository.listTrash(wsId)
        val drafts = draftManager.recoverableDrafts(wsId)
        val pendingOps = repository.db.syncDao().countPendingOutbox(wsId)
        val receipts = repository.listBlobReceipts(wsId)

        _state.update {
            it.copy(
                workspaces = workspaces,
                totalActiveRecords = total,
                tasks = tasks,
                events = events,
                chores = chores,
                roomHealth = roomHealth,
                notes = notes,
                foodLogs = foodLogs,
                labWetNirPairs = wetNir,
                expenses = expenses,
                trashRecords = trash,
                recoverableDrafts = drafts,
                syncSnapshot = it.syncSnapshot.copy(
                    pendingOpsCount = pendingOps,
                    blobReceipts = receipts,
                ),
            )
        }
        refreshCurrentRouteRecords()
    }

    private suspend fun refreshCurrentRouteRecords() {
        val wsId = _state.value.activeWorkspaceId
        val kind = routeToPrimaryKind(_state.value.activeRoute)
        val recs = repository.listByKind(wsId, kind)
        _state.update { it.copy(currentRouteRecords = recs) }
    }

    companion object {
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
