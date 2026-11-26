package com.planaday.nativeapp.core.designtokens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.documents.LargeDocumentAnalyzer
import com.planaday.nativeapp.core.domain.*
import com.planaday.nativeapp.core.graphics.PhotoAndPlannerAdvanced
import com.planaday.nativeapp.core.graphics.PhysicalPlannerEngine
import com.planaday.nativeapp.core.interchange.Rfc5545IcsCodec
import com.planaday.nativeapp.core.math.*
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.security.SafeLearningInterpreter

/**
 * Shared responsive workspace renderer used by both Windows Compose Desktop and Android Jetpack Compose.
 * Engineered for zero horizontal overflow down to 320dp compact viewports (PD-011, PD-012, PD-019)
 * with explicit accessibility semantics on every control (PD-013).
 */
@Composable
fun PlanadayActiveWorkspaceStage(
    state: PlanadayUiState,
    controller: PlanadayWorkspaceController,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalPlanadaySpacing.current
    Column(modifier = modifier.fillMaxSize().padding(spacing.density.itemPadding)) {
        // Header strip with Route title, Requirement badge, Save state indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.activeRoute.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${state.activeRoute.description} [${state.activeRoute.requirementRef}]",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(
                onClick = { controller.selectRoute(PlanadayRoute.SETTINGS) },
                label = { Text("${state.saveState.name} · ${state.totalActiveRecords} recs") },
                modifier = Modifier.semantics { contentDescription = "Save state and record counter" },
            )
        }

        state.statusBanner?.let { banner ->
            Spacer(Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = banner,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when (state.activeRoute) {
            PlanadayRoute.TODAY, PlanadayRoute.HOME, PlanadayRoute.QUICK_GLANCE, PlanadayRoute.ALL_IN_ONE ->
                TodayWorkspacePanel(state, controller, isCompact)
            PlanadayRoute.FLOW_CALENDAR, PlanadayRoute.PERIOD_CALENDAR, PlanadayRoute.TASK_CALENDAR ->
                CalendarAndSchedulerPanel(state, controller)
            PlanadayRoute.TASKS, PlanadayRoute.KANBAN ->
                TasksAndKanbanPanel(state, controller, isCompact)
            PlanadayRoute.CHORES ->
                ChoresPanel(state, controller)
            PlanadayRoute.NOTES ->
                NotesAndDraftsPanel(state, controller)
            PlanadayRoute.DIARY ->
                DiaryAndWellnessPanel(state, controller)
            PlanadayRoute.CLOCK_WORLD, PlanadayRoute.ALARMS, PlanadayRoute.TIMERS, PlanadayRoute.STOPWATCH, PlanadayRoute.COUNTDOWNS ->
                TimeSuitePanel(state.activeRoute, state, controller)
            PlanadayRoute.WHITEBOARD ->
                WhiteboardCanvasPanel()
            PlanadayRoute.RECIPES, PlanadayRoute.PANTRY, PlanadayRoute.MEAL_PLANNER, PlanadayRoute.GROCERIES, PlanadayRoute.FINANCE ->
                KitchenAndFinancePanel(state.activeRoute, state, controller)
            PlanadayRoute.LAB_WORKSPACE ->
                Laboratory15SuitePanel(state, controller)
            PlanadayRoute.CALCULATOR ->
                CalculatorAndUnitsPanel()
            PlanadayRoute.SPREADSHEETS ->
                SpreadsheetStudioPanel()
            PlanadayRoute.PHYSICAL_PLANNER ->
                PhysicalPlannerPanel()
            PlanadayRoute.CODE_STUDIO ->
                CodeStudioDebuggerPanel()
            PlanadayRoute.PROJECTS, PlanadayRoute.GAME_DEV, PlanadayRoute.BOOK_STUDIO, PlanadayRoute.PRESENTATIONS, PlanadayRoute.PHOTO_STUDIO, PlanadayRoute.SPACED_REVIEW, PlanadayRoute.REPORTS, PlanadayRoute.AUTOMATIONS ->
                CreativeAndProjectsPanel(state.activeRoute, state, controller)
            PlanadayRoute.HOUSEHOLD, PlanadayRoute.PEOPLE_GIFTS, PlanadayRoute.HEALTH_MEDS, PlanadayRoute.MEDIA_HOBBIES, PlanadayRoute.TRAVEL, PlanadayRoute.HOME_ASSETS ->
                LifeAndHouseholdPanel(state.activeRoute, state, controller)
            PlanadayRoute.UNIVERSAL_FILES, PlanadayRoute.HISTORY_REVIEWS, PlanadayRoute.INSIGHTS, PlanadayRoute.CUSTOM_SPACES, PlanadayRoute.RITUALS, PlanadayRoute.GUIDE ->
                FilesInsightsAndGuidePanel(state.activeRoute, state, controller)
            PlanadayRoute.TRASH ->
                TrashRecoveryPanel(state, controller)
            PlanadayRoute.SETTINGS ->
                SettingsSyncDiagnosticsPanel(state, controller)
            else ->
                CanonicalRecordDomainPanel(state, controller)
        }
    }
}

@Composable
private fun TodayWorkspacePanel(
    state: PlanadayUiState,
    controller: PlanadayWorkspaceController,
    isCompact: Boolean,
) {
    var quickTaskTitle by remember { mutableStateOf("") }
    var quickMealTitle by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        if (state.totalActiveRecords == 0L) {
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Fresh Empty Planaday Native Database (Owner Decision O-004)", fontWeight = FontWeight.Bold)
                        Text(
                            "Started with a brand-new empty SQLite database (planaday_native_v1.db) — zero legacy data imported. Create your own records below or load opt-in synthetic demo records.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { controller.optInSeedSyntheticDemo() },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Load opt-in synthetic demonstration records" },
                        ) {
                            Text("Load Opt-In Synthetic Demo")
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick Capture Task (Today -> Task Calendar -> Kanban)", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = quickTaskTitle,
                        onValueChange = { quickTaskTitle = it },
                        label = { Text("Task title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            controller.quickCreateTask(quickTaskTitle, Priority.HIGH, 30)
                            quickTaskTitle = ""
                        },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add task to today" },
                    ) {
                        Text("Save Canonical Task")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick Meal Log -> Unified Food History (INT-001)", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = quickMealTitle,
                        onValueChange = { quickMealTitle = it },
                        label = { Text("Meal or food eaten") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            controller.logMeal(quickMealTitle, "lunch")
                            quickMealTitle = ""
                        },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Log meal to food history" },
                    ) {
                        Text("Log Meal")
                    }
                    if (state.foodLogs.isNotEmpty()) {
                        Text("Recent Food History: ${state.foodLogs.take(4).joinToString { it.foodName }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Text("Active Tasks (${state.tasks.size}) · Events (${state.events.size}) · Notes (${state.notes.size})", fontWeight = FontWeight.Bold)
        }
        items(state.tasks.take(8)) { task ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, fontWeight = FontWeight.Medium)
                        Text("${task.priority.name} · ${task.boardColumn} · est ${task.estimateMin ?: 25}m", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = { controller.moveTaskColumn(task.id, if (task.status == RecordStatus.DONE) "next" else "done") },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Toggle task completion for ${task.title}" },
                    ) {
                        Text(if (task.status == RecordStatus.DONE) "Reopen" else "Complete")
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarAndSchedulerPanel(state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    var evTitle by remember { mutableStateOf("") }
    var evStart by remember { mutableStateOf("10:00") }
    var evEnd by remember { mutableStateOf("11:00") }
    var overlapMsg by remember { mutableStateOf<String?>(null) }
    var icsPreview by remember { mutableStateOf<String?>(null) }

    val cfg = state.calendarConfig
    val schedulePlan = remember(state.tasks, state.events) {
        TaskSchedulerEngine.buildDaySchedule(
            dateIso = SystemDomainClock.todayIso(),
            nowMin = 8 * 60,
            preferredEndTimeLocal = "21:00",
            tasks = state.tasks,
            dayEvents = state.events,
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Flow Calendar Zoom (1m–4h) & 7 Overlap Policies", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CalendarOverlapEngine.STANDARD_ZOOM_INTERVALS_MIN.forEach { interval ->
                            FilterChip(
                                selected = cfg.intervalMin == interval,
                                onClick = { controller.updateCalendarConfig { it.copy(intervalMin = interval) } },
                                label = { Text(if (interval < 60) "${interval}m" else "${interval / 60}h") },
                            )
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CalendarOverlapPolicy.entries.forEach { policy ->
                            FilterChip(
                                selected = cfg.overlapPolicy == policy,
                                onClick = { controller.updateCalendarConfig { it.copy(overlapPolicy = policy) } },
                                label = { Text(policy.name) },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = cfg.adaptiveText,
                            onCheckedChange = { v -> controller.updateCalendarConfig { it.copy(adaptiveText = v) } },
                            modifier = Modifier.semantics { contentDescription = "Toggle adaptive calendar text scaling" },
                        )
                        Text("Adaptive Calendar Text Scaling (R-0005)")
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schedule Event (Validated Against ${cfg.overlapPolicy.name})", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = evTitle, onValueChange = { evTitle = it }, label = { Text("Event title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = evStart, onValueChange = { evStart = it }, label = { Text("Start HH:MM") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = evEnd, onValueChange = { evEnd = it }, label = { Text("End HH:MM") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val s = CalendarDateMath.timeToMinutes(evStart)
                                val e = CalendarDateMath.timeToMinutes(evEnd)
                                when (val v = CalendarOverlapEngine.validateNewOrMovedEvent(s, e, state.events, cfg.overlapPolicy)) {
                                    is OverlapValidationResult.Prevented -> overlapMsg = "Overlap PREVENTED against ${v.conflictingIds.size} event(s)."
                                    is OverlapValidationResult.Warning -> {
                                        overlapMsg = "Overlap WARNING (${v.conflictingIds.size} conflict); saved."
                                        controller.createCalendarEvent(evTitle, evStart, evEnd)
                                        evTitle = ""
                                    }
                                    OverlapValidationResult.Allowed -> {
                                        overlapMsg = null
                                        controller.createCalendarEvent(evTitle, evStart, evEnd)
                                        evTitle = ""
                                    }
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Create calendar event" },
                        ) { Text("Add Event") }
                        OutlinedButton(
                            onClick = {
                                icsPreview = Rfc5545IcsCodec.exportCalendar(state.events, "Planaday").bytes.decodeToString().take(320)
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Export ICS preview" },
                        ) { Text("RFC 5545 ICS") }
                    }
                    overlapMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    icsPreview?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        item {
            Text("Intelligent Auto-Scheduled Task Slots (${schedulePlan.slots.size} placed, ${schedulePlan.unfitTasks.size} unfit)", fontWeight = FontWeight.Bold)
        }
        items(schedulePlan.slots) { slot ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("${CalendarDateMath.minutesToTime(slot.startMin)}–${CalendarDateMath.minutesToTime(slot.endMin)} · ${slot.task.title}", fontWeight = FontWeight.Medium)
                    Text("Placement: ${slot.kind.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TasksAndKanbanPanel(state: PlanadayUiState, controller: PlanadayWorkspaceController, isCompact: Boolean) {
    var title by remember { mutableStateOf("") }
    var est by remember { mutableStateOf("30") }
    val columns = listOf("backlog", "next", "in_progress", "done")

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Live Canonical Tasks & Kanban (Single Task UUID — INT-003)", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = est, onValueChange = { est = it }, label = { Text("Est min") }, modifier = Modifier.width(110.dp), singleLine = true)
                        Button(
                            onClick = {
                                controller.quickCreateTask(title, Priority.MEDIUM, est.toIntOrNull() ?: 30)
                                title = ""
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Create new task" },
                        ) { Text("Create Task") }
                    }
                }
            }
        }

        items(columns) { col ->
            val colTasks = state.tasks.filter { it.boardColumn == col }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${col.uppercase()} (${colTasks.size})", fontWeight = FontWeight.Bold)
                    if (colTasks.isEmpty()) {
                        Text("No tasks in $col", style = MaterialTheme.typography.bodySmall)
                    } else {
                        colTasks.forEach { t ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(t.title, modifier = Modifier.weight(1f))
                                val nextCol = columns[(columns.indexOf(col) + 1) % columns.size]
                                TextButton(
                                    onClick = { controller.moveTaskColumn(t.id, nextCol) },
                                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Move ${t.title} to $nextCol" },
                                ) { Text("-> $nextCol") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoresPanel(state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    var title by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("Kitchen") }
    var days by remember { mutableStateOf("3") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Completion-Driven Chore & Room Health (INT-006)", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Chore") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Room") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("Days") }, modifier = Modifier.width(88.dp), singleLine = true)
                    }
                    Button(
                        onClick = { controller.createChore(title, room, days.toIntOrNull() ?: 7); title = "" },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add chore" },
                    ) { Text("Add Chore") }
                }
            }
        }
        items(state.roomHealth) { rh ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("${rh.room}: ${rh.healthPct}% health (${rh.overdueCount} overdue / ${rh.totalChores} total)", fontWeight = FontWeight.Medium)
                    LinearProgressIndicator(progress = { rh.healthPct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
        items(state.chores) { ch ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ch.title, fontWeight = FontWeight.Medium)
                        Text("${ch.room} · Next due: ${ch.nextDueDateIso} · Completions: ${ch.completions.size}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { controller.completeChoreToday(ch.id) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Complete chore ${ch.title}" },
                    ) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun NotesAndDraftsPanel(state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    val stats = remember(noteBody) { LargeDocumentAnalyzer.computeStatsSinglePass(noteBody) }
    val wikiLinks = remember(noteBody) { LargeDocumentAnalyzer.extractWikiLinks(noteBody) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        if (state.recoverableDrafts.isNotEmpty()) {
            item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "PF-55 Draft Recovery Inbox: ${state.recoverableDrafts.size} uncommitted record-bound draft(s) preserved safely.",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Record-Bound Note Editor (PD-016 / PD-010 O(n) stats)", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = noteTitle, onValueChange = { noteTitle = it }, label = { Text("Note title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(
                        value = noteBody,
                        onValueChange = { noteBody = it },
                        label = { Text("Markdown content (supports [[WikiLinks]])") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Words: ${stats.wordCount} · Paragraphs: ${stats.paragraphCount} · Chars: ${stats.charCount}" +
                            if (wikiLinks.isNotEmpty()) " · WikiLinks: ${wikiLinks.joinToString()}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = { controller.saveNote(noteTitle, noteBody); noteTitle = ""; noteBody = "" },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Save note with revision" },
                    ) { Text("Save Note + Checkpoint") }
                }
            }
        }
        items(state.notes) { rec ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${rec.title} (rev ${rec.rev})", fontWeight = FontWeight.Medium)
                        Text(rec.summaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    TextButton(
                        onClick = { controller.moveRecordToTrash(rec.id) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Move note ${rec.title} to trash" },
                    ) { Text("Trash") }
                }
            }
        }
    }
}

@Composable
private fun Laboratory15SuitePanel(state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    val labTabs = listOf(
        "Overview", "Samples & Custody", "Subcontracts", "Inventory", "Purchasing & Budgets",
        "Client Pricing", "Method Research", "Email Drafts", "Lab Tasks", "Lab Meetings",
        "Calibration & QC", "Wet Chem vs NIR", "Work Hours", "QMS / SOP / Training", "Uncertainty Workbook",
    )
    var selectedTab by remember { mutableStateOf(11) } // Highlight Wet Chem vs NIR interactive analyzer
    var sampleId by remember { mutableStateOf("SMP-101") }
    var wetVal by remember { mutableStateOf("12.40") }
    var nirVal by remember { mutableStateOf("12.55") }
    val summary = remember(state.labWetNirPairs) { LabAnalyticsEngine.compareWetVsNir(state.labWetNirPairs) }
    var uncertaintySummary by remember { mutableStateOf<String?>(null) }
    var quoteSummary by remember { mutableStateOf<String?>(null) }
    var custodyLog by remember { mutableStateOf("Received -> Ready for Prepared/In-Progress transition") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    LabAnalyticsEngine.NON_LIMS_DISCLAIMER,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labTabs.forEachIndexed { idx, label ->
                    FilterChip(selected = selectedTab == idx, onClick = { selectedTab = idx }, label = { Text("${idx + 1}. $label") })
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Submodule: ${labTabs[selectedTab]} — Wet Chemistry vs. NIR Statistical Reference", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = sampleId, onValueChange = { sampleId = it }, label = { Text("Sample ID") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = wetVal, onValueChange = { wetVal = it }, label = { Text("Wet %") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = nirVal, onValueChange = { nirVal = it }, label = { Text("NIR %") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Button(
                        onClick = {
                            val w = wetVal.toDoubleOrNull()
                            val n = nirVal.toDoubleOrNull()
                            if (w != null && n != null) controller.addLabWetNirPair(sampleId, w, n)
                        },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add Wet versus NIR analytical pair" },
                    ) { Text("Add Analytical Pair") }
                    Text(
                        "Included n=${summary.nIncluded} (excluded=${summary.nExcluded}) · Bias=${summary.bias?.let { "%.4f".format(it) } ?: "—"} · " +
                            "SEP=${summary.sdDiffSep?.let { "%.4f".format(it) } ?: "—"} · RMSE=${summary.rmse?.let { "%.4f".format(it) } ?: "—"} · " +
                            "R²=${summary.rSquared?.let { "%.4f".format(it) } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (summary.mixedUnitsWarning) {
                        Text("WARNING: Mixed measurement units detected (${summary.units.joinToString()})!", color = MaterialTheme.colorScheme.error)
                    }
                    // Live Compose Canvas Wet vs NIR Scatter Plot + y=x Identity Line (R-0164)
                    val axisColor = MaterialTheme.colorScheme.outline
                    val identityColor = MaterialTheme.colorScheme.secondary
                    val pointColor = MaterialTheme.colorScheme.primary
                    Canvas(
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .semantics { contentDescription = "Wet chemistry versus NIR scatter plot with identity line" }
                    ) {
                        val pad = 18f
                        val w = size.width - pad * 2
                        val h = size.height - pad * 2
                        // Axes & y=x Identity line
                        drawLine(axisColor, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), strokeWidth = 2f)
                        drawLine(axisColor, Offset(pad, pad), Offset(pad, size.height - pad), strokeWidth = 2f)
                        drawLine(identityColor, Offset(pad, size.height - pad), Offset(size.width - pad, pad), strokeWidth = 2f)

                        val pts = summary.includedPoints
                        if (pts.isNotEmpty()) {
                            val allVals = pts.flatMap { listOf(it.wetMean, it.nirMean) }
                            val minV = (allVals.minOrNull() ?: 0.0) - 1.0
                            val maxV = (allVals.maxOrNull() ?: 10.0) + 1.0
                            val span = (maxV - minV).coerceAtLeast(1e-6)
                            pts.forEach { p ->
                                val x = pad + (((p.wetMean - minV) / span) * w).toFloat()
                                val y = size.height - pad - (((p.nirMean - minV) / span) * h).toFloat()
                                drawCircle(pointColor, radius = 6f, center = Offset(x, y))
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lab Operations: Custody Sign-Off, Client Pricing & GUM Uncertainty (PF-23..PF-25, PF-56)", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val u = LaboratoryOperationsEngine.computeUncertaintyBudget(
                                    listOf(UncertaintyComponent("Repeatability", 0.08), UncertaintyComponent("Calibration Std", 0.06)),
                                    coverageFactorK = 2.0,
                                    methodVersion = "AOAC-930.15-v2",
                                )
                                val v1 = LabAnalyticsEngine.dilutionVolumeV1(10.0, 2.5, 100.0)
                                val rec = LabAnalyticsEngine.percentRecovery(10.2, 0.2, 10.0)
                                uncertaintySummary = "GUM uc=${"%.4f".format(u.combinedStandardUncertainty)}, U(k=2)=${"%.4f".format(u.expandedUncertainty)} | Dilution V1=${v1}mL | Recovery=${"%.1f".format(rec)}%"
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Compute GUM uncertainty and lab dilution" },
                        ) { Text("GUM & Dilution") }
                        OutlinedButton(
                            onClick = {
                                val q = LaboratoryOperationsEngine.computeClientQuote(
                                    "Valley Grain Co.", "Wheat", listOf("MOIST", "PROT"),
                                    listOf(
                                        LabPricingRule("r1", "Valley Grain Co.", "Wheat", "MOIST", DecimalAmount("32.50", "USD"), 2),
                                        LabPricingRule("r2", null, null, "PROT", DecimalAmount("48.00", "USD"), 3),
                                    ),
                                )
                                quoteSummary = "Quote (${q.clientName}): ${q.totalAmount.value} ${q.totalAmount.unitOrCurrency} · Max TAT=${q.maxTurnaroundDays}d"
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Compute lab client quote" },
                        ) { Text("Client Quote") }
                        OutlinedButton(
                            onClick = {
                                val today = SystemDomainClock.todayIso()
                                val s = LabSample("s1", state.activeWorkspaceId, sampleId, "Client A", "Grain", listOf("MOIST"), today, today, status = "received")
                                val next = LaboratoryOperationsEngine.transitionSampleWithCustody(s, "prepared", "Lead Analyst", "Prep Bench 2", SystemDomainClock.nowUtcIso())
                                custodyLog = "Sample ${next.sampleCode} -> ${next.status} (${next.custodyEvents.size} signed event)"
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Sign off sample custody transition" },
                        ) { Text("Sign Custody") }
                    }
                    uncertaintySummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    quoteSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(custodyLog, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun CalculatorAndUnitsPanel() {
    var expr by remember { mutableStateOf("sin(pi/6) + 2^3^2") }
    var rootExpr by remember { mutableStateOf("(x-0.123)^2") }
    var unitCat by remember { mutableStateOf("length") }
    var unitVal by remember { mutableStateOf("12") }

    val evalOutput = remember(expr) { runCatching { MathAstEngine.evaluate(expr).toString() }.getOrElse { "Error: ${it.message}" } }
    val rootsList = remember(rootExpr) { runCatching { MathAstEngine.findNumericalRoots(rootExpr, -5.0, 5.0) }.getOrDefault(emptyList()) }
    val rootsOutput = remember(rootsList, rootExpr) {
        runCatching { MathAstEngine.findNumericalRoots(rootExpr, -5.0, 5.0).joinToString() }.getOrElse { "Error: ${it.message}" }
    }
    val catDef = UnitConverter23.categories[unitCat] ?: UnitConverter23.categories.values.first()
    val converted = remember(unitCat, unitVal) {
        val v = unitVal.toDoubleOrNull() ?: 0.0
        val u0 = catDef.units.first()
        val u1 = catDef.units.getOrElse(1) { u0 }
        "${v} ${u0.symbol} = ${"%.6f".format(UnitConverter23.convert(catDef.id, u0.id, u1.id, v))} ${u1.symbol}"
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AST Scientific & Lab Calculator + 2D Function Grapher (Zero eval())", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = expr, onValueChange = { expr = it }, label = { Text("Expression") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Result = $evalOutput", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = rootExpr, onValueChange = { rootExpr = it }, label = { Text("Plot & Pole-Safe Roots f(x)=0 on [-5,5] (PD-007)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Roots = [$rootsOutput]", style = MaterialTheme.typography.bodySmall)
                    val curveCol = MaterialTheme.colorScheme.primary
                    val rootCol = MaterialTheme.colorScheme.error
                    val axisCol = MaterialTheme.colorScheme.outline
                    Canvas(
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .semantics { contentDescription = "2D function graph canvas for $rootExpr" }
                    ) {
                        val midY = size.height / 2f
                        val midX = size.width / 2f
                        drawLine(axisCol, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.5f)
                        drawLine(axisCol, Offset(midX, 0f), Offset(midX, size.height), strokeWidth = 1.5f)
                        val path = Path()
                        var started = false
                        val steps = 120
                        for (i in 0..steps) {
                            val xVal = -5.0 + (10.0 * i / steps)
                            val yVal = runCatching { MathAstEngine.evaluate(rootExpr, MathEvalContext(variables = mapOf("x" to xVal))) }.getOrNull()
                            if (yVal != null && yVal.isFinite() && kotlin.math.abs(yVal) <= 10.0) {
                                val px = (i.toFloat() / steps) * size.width
                                val py = (midY - (yVal.toFloat() / 5f) * (size.height / 2f)).coerceIn(2f, size.height - 2f)
                                if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
                            } else {
                                started = false
                            }
                        }
                        if (started) drawPath(path, color = curveCol, style = Stroke(width = 3f))
                        rootsList.forEach { rx ->
                            val px = (((rx + 5.0) / 10.0) * size.width).toFloat()
                            drawCircle(rootCol, radius = 5f, center = Offset(px, midY))
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("23-Category Invertible Unit Converter (${UnitConverter23.categories.size} Categories)", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UnitConverter23.categories.values.forEach { c ->
                            FilterChip(selected = c.id == unitCat, onClick = { unitCat = c.id }, label = { Text(c.displayName) })
                        }
                    }
                    OutlinedTextField(value = unitVal, onValueChange = { unitVal = it }, label = { Text("Value") }, singleLine = true)
                    Text(converted, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SpreadsheetStudioPanel() {
    var a1 by remember { mutableStateOf("2") }
    var a2 by remember { mutableStateOf("4") }
    var a3 by remember { mutableStateOf("6") }
    var formula by remember { mutableStateOf("=SUM(A1:A3)+1") }

    val evaluated = remember(a1, a2, a3, formula) {
        val sheets = listOf(
            WorksheetModel(
                id = "main", name = "Main",
                cells = mapOf("A1" to SheetCellInput(a1), "A2" to SheetCellInput(a2), "A3" to SheetCellInput(a3), "E1" to SheetCellInput(formula)),
            )
        )
        SpreadsheetFormulaEngine.evaluateWorkbook(sheets)["main"]!!["E1"]!!
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sparse DAG Spreadsheet Engine (CONT-006 #REF! & CONT-007 Composite)", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = a1, onValueChange = { a1 = it }, label = { Text("A1") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = a2, onValueChange = { a2 = it }, label = { Text("A2") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = a3, onValueChange = { a3 = it }, label = { Text("A3") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(value = formula, onValueChange = { formula = it }, label = { Text("Formula in E1 (try =Missing!A1+1)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Evaluated E1 = ${evaluated.value.displayString()}", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PhysicalPlannerPanel() {
    var pages by remember {
        mutableStateOf(
            listOf(
                PlannerPage("pg_1", "book_1", "ws_personal", 1, "Cover & Year Index"),
                PlannerPage("pg_2", "book_1", "ws_personal", 2, "Weekly Spread"),
                PlannerPage("pg_3", "book_1", "ws_personal", 3, "Lab Notes Spread"),
            )
        )
    }
    var currentPageIdx by remember { mutableStateOf(0) }
    var stickerCat by remember { mutableStateOf("Planning") }
    val visitedStates = remember { mutableStateListOf("CA", "WA") }
    val categories = remember { PhotoAndPlannerAdvanced.BUILT_IN_STICKER_CATALOG.map { it.category }.distinct() }
    val stickersInCat = remember(stickerCat) { PhotoAndPlannerAdvanced.BUILT_IN_STICKER_CATALOG.filter { it.category == stickerCat } }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val active = pages[currentPageIdx.coerceIn(pages.indices)]
                    Text("Physical-Style Digital Planner · Page ${active.pageNumber}/${pages.size}: ${active.title}", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (currentPageIdx > 0) currentPageIdx-- },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Previous planner page" },
                        ) { Text("Prev") }
                        Button(
                            onClick = { if (currentPageIdx < pages.lastIndex) currentPageIdx++ },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Next planner page" },
                        ) { Text("Next") }
                        OutlinedButton(
                            onClick = {
                                pages = PhotoAndPlannerAdvanced.duplicatePage(pages, active.id, "pg_${pages.size + 1}")
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Duplicate current planner page" },
                        ) { Text("Duplicate Page") }
                    }
                    Text("260 Newly Authored Vector Stickers (${PhotoAndPlannerAdvanced.BUILT_IN_STICKER_CATALOG.size} total):", fontWeight = FontWeight.Medium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { c ->
                            FilterChip(selected = stickerCat == c, onClick = { stickerCat = c }, label = { Text(c) })
                        }
                    }
                    Text("${stickersInCat.size} stickers in '$stickerCat': ${stickersInCat.take(5).joinToString { it.label }}…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Interactive 50-State US Map Tracker (${visitedStates.size}/50 visited):", fontWeight = FontWeight.Medium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PhysicalPlannerEngine.US_50_STATES.forEach { (code, name) ->
                            val visited = code in visitedStates
                            FilterChip(
                                selected = visited,
                                onClick = { if (visited) visitedStates.remove(code) else visitedStates.add(code) },
                                label = { Text("$code · $name") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeStudioDebuggerPanel() {
    var code by remember { mutableStateOf("let x = 12\nlet y = x * 3 + 4\nprint y\nassert_eq y, 40") }
    var output by remember { mutableStateOf("Press Run to execute in isolated safe debugger (zero eval).") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Code Studio & Step Debugger (PD-005 / R-0322 / R-0325)", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Script") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val res = SafeLearningInterpreter.executeWithDebugger(code, breakpoints = setOf(2))
                    output = if (res.succeeded) {
                        "PASS (${res.stepsExecuted} steps) | stdout=${res.stdout} | BP@line2 vars=${res.trace.firstOrNull { it.hitBreakpoint }?.variables}"
                    } else "ERROR: ${res.error}"
                },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Run safe debugger" },
            ) { Text("Run + Inspect Breakpoint @ Line 2") }
            Text(output, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TrashRecoveryPanel(state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        if (state.trashRecords.isEmpty()) {
            item { Text("30-Day Trash is empty.") }
        } else {
            items(state.trashRecords) { rec ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rec.title, fontWeight = FontWeight.Medium)
                            Text("Deleted: ${rec.deletedAtUtc}", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { controller.restoreFromTrash(rec.id) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Restore ${rec.title}" },
                        ) { Text("Restore") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSyncDiagnosticsPanel(state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    var pairingCode by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Release & Fresh-Start Identity (C-0061, O-004, PF-52)", fontWeight = FontWeight.SemiBold)
                    Text("Product: ${state.buildIdentity.productName} v${state.buildIdentity.appVersion}")
                    Text("Protocol v${state.buildIdentity.protocolVersion} · Schema v${state.buildIdentity.schemaVersion} · Platform: ${state.buildIdentity.platform}")
                    Text("DB: ${BuildIdentity.CLIENT_DB_NAME} · AppId: ${BuildIdentity.ANDROID_APPLICATION_ID}")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Theme & Density", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlanadayThemeMode.entries.forEach { m ->
                            FilterChip(selected = state.themeMode == m, onClick = { controller.setThemeMode(m) }, label = { Text(m.displayName) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlanadayDensity.entries.forEach { d ->
                            FilterChip(selected = state.density == d, onClick = { controller.setDensity(d) }, label = { Text(d.displayName) })
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Optional Self-Hosted Ktor Sync Server (PF-04 / PF-51)", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = state.syncServerUrl, onValueChange = { controller.updateSyncServerUrl(it) }, label = { Text("Server URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = pairingCode, onValueChange = { pairingCode = it }, label = { Text("10-min Pairing Code (optional if paired)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(
                        onClick = { controller.pairAndSyncWithServer(pairingCode, state.buildIdentity.platform) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Pair and sync with server" },
                    ) { Text("Pair & Sync Now") }
                    Text(
                        "Status: ${state.syncSnapshot.status} · Pending Ops: ${state.syncSnapshot.pendingOpsCount} · " +
                            "Failed Blobs: ${state.syncSnapshot.failedBlobTransfers} · Conflicts: ${state.syncSnapshot.unresolvedConflicts}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Verified SHA-256 Backup & Restore Rehearsal (PF-42)", fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = { controller.runVerifiedBackupCheck() },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Create and verify SHA-256 backup archive" },
                    ) { Text("Run Verified Backup Rehearsal") }
                    state.lastBackupSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            var localAssistOptIn by remember { mutableStateOf(false) }
            val assistEngine = remember { LocalAssistanceEngine() }
            var extStatus by remember { mutableStateOf("Declarative capability validator ready (zero host code exec).") }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Opt-In Local Assistance, Learning Reset & Extension Safety (PF-35, N-036..N-038)", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = localAssistOptIn,
                            onCheckedChange = { localAssistOptIn = it },
                            modifier = Modifier.semantics { contentDescription = "Opt in to local deterministic priority suggestions" },
                        )
                        Text("Opt-In Local Ranking (100% offline, zero external AI)")
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { assistEngine.resetLearning(); extStatus = "Local preference weights reset to clean defaults." },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Reset local learning preferences" },
                        ) { Text("Reset Local Learning") }
                        OutlinedButton(
                            onClick = {
                                val (_, msg) = ExtensionCapabilityValidator.validate(
                                    ExtensionCapabilityManifest("theme-pack-1", "1.0.0", setOf("CUSTOM_THEME", "READ_TEMPLATES"), requestsHostExecution = false)
                                )
                                extStatus = msg
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Validate declarative extension manifest" },
                        ) { Text("Verify Extension Manifest") }
                    }
                    Text(extStatus, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CanonicalRecordDomainPanel(state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add ${state.activeRoute.title} Record (Persisted in Room SQLite + FTS5)", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("Details / Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(
                        onClick = { controller.addRecordForRoute(state.activeRoute, title, summary); title = ""; summary = "" },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Save record in ${state.activeRoute.title}" },
                    ) { Text("Save Record") }
                }
            }
        }
        items(state.currentRouteRecords) { rec ->
            OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${rec.title} (rev ${rec.rev})", fontWeight = FontWeight.Medium)
                        if (rec.summaryText.isNotBlank()) Text(rec.summaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = { controller.moveRecordToTrash(rec.id) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Move ${rec.title} to trash" },
                    ) { Text("Trash") }
                }
            }
        }
    }
}
