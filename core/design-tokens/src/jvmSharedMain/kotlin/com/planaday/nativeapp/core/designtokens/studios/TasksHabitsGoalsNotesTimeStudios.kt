package com.planaday.nativeapp.core.designtokens.studios

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.designtokens.*
import com.planaday.nativeapp.core.documents.LargeDocumentAnalyzer
import com.planaday.nativeapp.core.domain.*
import com.planaday.nativeapp.core.interchange.ExtendedFormatInterchange
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.notifications.NotificationScheduleEngine
import com.planaday.nativeapp.core.notifications.QuietHoursPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// 1. Complete Tasks, Kanban Boards & Schedule Assistant (§10)
// ============================================================================

@Composable
fun TasksAndKanbanStudio(
    state: PlanadayShellState,
    controller: PlanadayWorkspaceController,
    isCompact: Boolean,
    initialBoardMode: Boolean = false,
) {
    val taskRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.TASK, limit = 250) }.collectAsState(emptyList())
    val tasks = remember(taskRecs) { taskRecs.mapNotNull { runCatching { controller.json.decodeFromString<TaskItem>(it.payloadJson) }.getOrNull() } }

    var viewMode by remember { mutableStateOf(if (initialBoardMode) 1 else 0) } // 0=List, 1=Board, 2=Smart Schedule
    var filterMode by remember { mutableStateOf("Active") }
    var newTitle by remember { mutableStateOf("") }
    var newPriority by remember { mutableStateOf(Priority.MEDIUM) }
    var newEstimate by remember { mutableStateOf("30") }
    var newDueDate by remember { mutableStateOf("") }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }

    val filteredTasks = remember(tasks, filterMode) {
        val today = SystemDomainClock.todayIso()
        when (filterMode) {
            "Active" -> tasks.filter { it.status == RecordStatus.ACTIVE }
            "Overdue" -> tasks.filter { task -> val due = task.dueDateIso; task.status == RecordStatus.ACTIVE && due != null && due < today }
            "Completed" -> tasks.filter { it.status == RecordStatus.DONE }
            else -> tasks
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StudioTabRow(listOf("List", "Kanban Board", "Smart Schedule"), viewMode, { viewMode = it })

        StudioCard(title = "New Task") {
            OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("What needs to get done?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Priority.entries.filter { it != Priority.NONE }.forEach { p ->
                    FilterChip(selected = newPriority == p, onClick = { newPriority = p }, label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
                OutlinedTextField(value = newEstimate, onValueChange = { newEstimate = it }, label = { Text("Min") }, singleLine = true, modifier = Modifier.width(84.dp))
                OutlinedTextField(value = newDueDate, onValueChange = { newDueDate = it }, label = { Text("Due YYYY-MM-DD") }, singleLine = true, modifier = Modifier.width(150.dp))
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            controller.quickCreateTask(
                                title = newTitle,
                                priority = newPriority,
                                estimateMin = newEstimate.toIntOrNull()?.coerceIn(5, 480) ?: 30,
                                dueDateIso = newDueDate.trim().takeIf { it.length == 10 },
                            )
                            newTitle = ""
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Save Task" },
                ) { Text("Save Task") }
            }
        }

        when (viewMode) {
            0 -> {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Active", "Overdue", "Completed", "All").forEach { f ->
                        FilterChip(selected = filterMode == f, onClick = { filterMode = f }, label = { Text(f) })
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    if (filteredTasks.isEmpty()) {
                        item { StudioEmptyState("No Tasks in '$filterMode'", "Add a task above to start tracking your work.") }
                    }
                    items(filteredTasks, key = { it.id }) { t ->
                        OutlinedCard(onClick = { editingTask = t }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(t.title, fontWeight = FontWeight.SemiBold)
                                    val subDone = t.subtasks.count { it.done }
                                    val subMeta = if (t.subtasks.isNotEmpty()) " · Subtasks $subDone/${t.subtasks.size}" else ""
                                    Text(
                                        "${t.priority.name} · ${t.boardColumn} · ${t.estimateMin ?: 30}m · Due: ${t.dueDateIso ?: "None"}$subMeta",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = { controller.moveTaskColumn(t.id, if (t.status == RecordStatus.DONE) "next" else "done") },
                                        modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = "Toggle completion for ${t.title}" },
                                    ) { Text(if (t.status == RecordStatus.DONE) "Reopen" else "Done") }
                                    TextButton(
                                        onClick = { controller.moveRecordToTrash(t.id) },
                                        modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = "Delete task ${t.title}" },
                                    ) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                val columns = listOf("backlog" to "Backlog", "next" to "Next", "in_progress" to "In Progress", "done" to "Done")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(columns) { (colId, colLabel) ->
                        val itemsInCol = tasks.filter { it.boardColumn == colId }
                        StudioCard(title = "$colLabel (${itemsInCol.size})") {
                            if (itemsInCol.isEmpty()) {
                                Text("No tasks in $colLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                itemsInCol.forEach { t ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(t.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                        val nextCol = columns[(columns.indexOfFirst { it.first == colId } + 1) % columns.size].first
                                        TextButton(onClick = { controller.moveTaskColumn(t.id, nextCol) }) { Text("Move → $nextCol") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                val today = SystemDomainClock.todayIso()
                val eventRecs by remember(state.activeWorkspaceId, today) {
                    controller.observeScheduleWindow(RecordKind.EVENT, today, today, limit = 100)
                }.collectAsState(emptyList())
                val todayEvents = remember(eventRecs) { eventRecs.mapNotNull { runCatching { controller.json.decodeFromString<CalendarEvent>(it.payloadJson) }.getOrNull() } }
                val plan = remember(tasks, todayEvents, today) {
                    TaskSchedulerEngine.buildDaySchedule(today, 8 * 60, "20:00", tasks = tasks.filter { it.status == RecordStatus.ACTIVE }, dayEvents = todayEvents)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(
                            title = "Suggested Schedule for $today",
                            subtitle = "${plan.slots.size} tasks fit around ${todayEvents.size} existing events (${plan.unfitTasks.size} overflow)",
                            headerAction = {
                                if (plan.slots.isNotEmpty()) {
                                    Button(onClick = {
                                        plan.slots.forEach { s ->
                                            controller.scheduleCanonicalTaskOnCalendar(s.task, today, CalendarDateMath.minutesToTime(s.startMin), CalendarDateMath.minutesToTime(s.endMin))
                                        }
                                    }) { Text("Apply All to Calendar") }
                                }
                            },
                        ) {
                            plan.slots.forEach { slot ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${CalendarDateMath.minutesToTime(slot.startMin)}–${CalendarDateMath.minutesToTime(slot.endMin)} · ${slot.task.title}", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        controller.scheduleCanonicalTaskOnCalendar(slot.task, today, CalendarDateMath.minutesToTime(slot.startMin), CalendarDateMath.minutesToTime(slot.endMin))
                                    }) { Text("Apply") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full Task Detail Editor (Subtasks, Notes, Actual Minutes, Priority, Dates)
    editingTask?.let { t ->
        var title by remember(t) { mutableStateOf(t.title) }
        var notes by remember(t) { mutableStateOf(t.notes) }
        var est by remember(t) { mutableStateOf((t.estimateMin ?: 30).toString()) }
        var actual by remember(t) { mutableStateOf(t.actualMinutes?.toString() ?: "") }
        var due by remember(t) { mutableStateOf(t.dueDateIso ?: "") }
        var subDraft by remember { mutableStateOf("") }
        var subtasks by remember(t) { mutableStateOf(t.subtasks) }

        AlertDialog(
            onDismissRequest = { editingTask = null },
            title = { Text("Edit Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = est, onValueChange = { est = it }, label = { Text("Est (m)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = actual, onValueChange = { actual = it }, label = { Text("Actual (m)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = due, onValueChange = { due = it }, label = { Text("Due Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Subtasks (${subtasks.size})", fontWeight = FontWeight.SemiBold)
                    subtasks.forEachIndexed { i, st ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = st.done,
                                onCheckedChange = { checked -> subtasks = subtasks.mapIndexed { idx, x -> if (idx == i) x.copy(done = checked) else x } },
                                modifier = Modifier.semantics { contentDescription = "Toggle subtask ${st.title}" },
                            )
                            Text(st.title, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = subDraft, onValueChange = { subDraft = it }, label = { Text("Add subtask") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            if (subDraft.isNotBlank()) {
                                subtasks = subtasks + SubtaskItem(UUID.randomUUID().toString(), subDraft.trim())
                                subDraft = ""
                            }
                        }) { Text("Add") }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val actMin = actual.toIntOrNull()
                    val logs = if (actMin != null && actMin != t.actualMinutes) {
                        t.completionLogs + DurationLog(UUID.randomUUID().toString(), null, SystemDomainClock.nowUtcIso(), actMin, est.toIntOrNull())
                    } else t.completionLogs
                    controller.updateTask(
                        t.copy(
                            title = title.trim().ifEmpty { t.title },
                            notes = notes,
                            estimateMin = est.toIntOrNull() ?: t.estimateMin,
                            actualMinutes = actMin,
                            dueDateIso = due.trim().takeIf { it.length == 10 },
                            subtasks = subtasks,
                            completionLogs = logs,
                        )
                    )
                    editingTask = null
                }) { Text("Save Changes") }
            },
            dismissButton = { TextButton(onClick = { editingTask = null }) { Text("Cancel") } },
        )
    }
}

// ============================================================================
// 2. Chores, Habits & Goals Studios (§10)
// ============================================================================

@Composable
fun ChoresHabitsGoalsStudio(
    route: PlanadayRoute,
    state: PlanadayShellState,
    controller: PlanadayWorkspaceController,
) {
    when (route) {
        PlanadayRoute.CHORES -> {
            val recs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.CHORE, 150) }.collectAsState(emptyList())
            val chores = remember(recs) { recs.mapNotNull { runCatching { controller.json.decodeFromString<ChoreItem>(it.payloadJson) }.getOrNull() } }
            val roomHealth = remember(chores) { controller.computeRoomHealth(chores) }
            var title by remember { mutableStateOf("") }
            var room by remember { mutableStateOf("Kitchen") }
            var days by remember { mutableStateOf("7") }
            var assignee by remember { mutableStateOf("") }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    StudioCard(title = "Add Chore") {
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Chore name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Room") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("Every N days") }, singleLine = true, modifier = Modifier.width(115.dp))
                            OutlinedTextField(value = assignee, onValueChange = { assignee = it }, label = { Text("Person") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Button(onClick = { controller.createChore(title, room, days.toIntOrNull() ?: 7); title = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save Chore") }
                    }
                }
                items(roomHealth) { rh ->
                    StudioCard(title = "${rh.room} — ${rh.healthPct}% Healthy", subtitle = "${rh.overdueCount} overdue of ${rh.totalChores}") {
                        LinearProgressIndicator(progress = { rh.healthPct / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
                items(chores, key = { it.id }) { ch ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ch.title, fontWeight = FontWeight.SemiBold)
                                Text("${ch.room} · Every ${ch.intervalDays}d · Next due: ${ch.nextDueDateIso} · Completed ${ch.completions.size}x", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(onClick = { controller.completeChoreToday(ch.id, assignee.takeIf { it.isNotBlank() }) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Done") }
                                TextButton(onClick = { controller.moveRecordToTrash(ch.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
        PlanadayRoute.HABITS -> {
            val recs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.HABIT, 100) }.collectAsState(emptyList())
            val habits = remember(recs) { recs.mapNotNull { runCatching { controller.json.decodeFromString<HabitItem>(it.payloadJson) }.getOrNull() } }
            var title by remember { mutableStateOf("") }
            var target by remember { mutableStateOf("1") }
            val today = SystemDomainClock.todayIso()
            val last7 = remember(today) { (6 downTo 0).map { CalendarDateMath.addDays(today, -it) } }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    StudioCard(title = "New Habit") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Habit name") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Daily Target") }, singleLine = true, modifier = Modifier.width(110.dp))
                            Button(onClick = {
                                if (title.isNotBlank()) {
                                    val now = SystemDomainClock.nowUtcIso()
                                    val h = HabitItem(UUID.randomUUID().toString(), state.activeWorkspaceId, title.trim(), targetQuantity = target.toDoubleOrNull() ?: 1.0, createdAtUtc = now, updatedAtUtc = now)
                                    controller.createOrUpdateRecord(RecordKind.HABIT, h.title, controller.json.encodeToString(h), "Target ${h.targetQuantity}/day")
                                    title = ""
                                }
                            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add Habit") }
                        }
                    }
                }
                items(habits, key = { it.id }) { h ->
                    val streak = last7.reversed().takeWhile { d -> (h.logs[d]?.value ?: 0.0) >= h.targetQuantity }.size
                    StudioCard(
                        title = h.title,
                        subtitle = "7-day streak: $streak · Target: ${h.targetQuantity} ${h.unit}",
                        headerAction = { TextButton(onClick = { controller.moveRecordToTrash(h.id) }) { Text("Delete") } },
                    ) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            last7.forEach { d ->
                                val done = (h.logs[d]?.value ?: 0.0) >= h.targetQuantity
                                FilterChip(
                                    selected = done,
                                    onClick = {
                                        val nextLogs = h.logs.toMutableMap()
                                        if (done) nextLogs.remove(d) else nextLogs[d] = HabitLogEntry(d, h.targetQuantity)
                                        val upd = h.copy(logs = nextLogs, updatedAtUtc = SystemDomainClock.nowUtcIso())
                                        controller.createOrUpdateRecord(RecordKind.HABIT, upd.title, controller.json.encodeToString(upd), "Logs: ${upd.logs.size}", existingId = upd.id)
                                    },
                                    label = { Text(d.takeLast(5)) },
                                )
                            }
                        }
                    }
                }
            }
        }
        else -> {
            // GOALS with Milestones & progress calculation
            val recs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.GOAL, 100) }.collectAsState(emptyList())
            val goals = remember(recs) { recs.mapNotNull { runCatching { controller.json.decodeFromString<GoalItem>(it.payloadJson) }.getOrNull() } }
            var title by remember { mutableStateOf("") }
            var area by remember { mutableStateOf("Personal") }
            var milestoneDraft by remember { mutableStateOf("") }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    StudioCard(title = "New Goal") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Goal title") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Area") }, singleLine = true, modifier = Modifier.width(120.dp))
                        }
                        Button(onClick = {
                            if (title.isNotBlank()) {
                                val now = SystemDomainClock.nowUtcIso()
                                val g = GoalItem(UUID.randomUUID().toString(), state.activeWorkspaceId, title.trim(), area = area, createdAtUtc = now, updatedAtUtc = now)
                                controller.createOrUpdateRecord(RecordKind.GOAL, g.title, controller.json.encodeToString(g), area)
                                title = ""
                            }
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save Goal") }
                    }
                }
                items(goals, key = { it.id }) { g ->
                    val pct = if (g.milestones.isEmpty()) 0f else g.milestones.count { it.done }.toFloat() / g.milestones.size.toFloat()
                    StudioCard(
                        title = g.title,
                        subtitle = "${g.area} · ${(pct * 100).toInt()}% complete (${g.milestones.count { it.done }}/${g.milestones.size} milestones)",
                        headerAction = { TextButton(onClick = { controller.moveRecordToTrash(g.id) }) { Text("Delete") } },
                    ) {
                        LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
                        g.milestones.forEachIndexed { idx, ms ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = ms.done,
                                    onCheckedChange = { checked ->
                                        val nextMs = g.milestones.mapIndexed { i, m -> if (i == idx) m.copy(done = checked) else m }
                                        val upd = g.copy(milestones = nextMs, updatedAtUtc = SystemDomainClock.nowUtcIso())
                                        controller.createOrUpdateRecord(RecordKind.GOAL, upd.title, controller.json.encodeToString(upd), "${upd.area}", existingId = upd.id)
                                    },
                                    modifier = Modifier.semantics { contentDescription = "Toggle milestone ${ms.title}" },
                                )
                                Text(ms.title)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = milestoneDraft, onValueChange = { milestoneDraft = it }, label = { Text("Add milestone") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                if (milestoneDraft.isNotBlank()) {
                                    val upd = g.copy(milestones = g.milestones + GoalMilestone(UUID.randomUUID().toString(), milestoneDraft.trim()), updatedAtUtc = SystemDomainClock.nowUtcIso())
                                    controller.createOrUpdateRecord(RecordKind.GOAL, upd.title, controller.json.encodeToString(upd), upd.area, existingId = upd.id)
                                    milestoneDraft = ""
                                }
                            }) { Text("Add") }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. Complete Notes & Knowledge Studio + Diary & Wellness Studio (§11)
// ============================================================================

@Composable
fun NotesAndKnowledgeStudio(
    state: PlanadayShellState,
    controller: PlanadayWorkspaceController,
    isCompact: Boolean,
) {
    val noteRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.NOTE, limit = 200) }.collectAsState(emptyList())
    val notebookRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.NOTEBOOK, limit = 50) }.collectAsState(emptyList())
    val drafts by remember(state.activeWorkspaceId) { controller.observeDirtyDrafts() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var selectedNotebookId by remember { mutableStateOf("all") }
    var activeNoteId by remember { mutableStateOf<String?>(null) }
    var editorTitle by remember { mutableStateOf("") }
    var editorBody by remember { mutableStateOf("") }
    var showDetailCompact by remember { mutableStateOf(false) }
    var showOutlineAndLinks by remember { mutableStateOf(false) }
    var showRevisions by remember { mutableStateOf(false) }
    var revisions by remember { mutableStateOf<List<RevisionSnapshot>>(emptyList()) }
    var newNotebookName by remember { mutableStateOf("") }
    val undoRedo = remember(activeNoteId) { SnapshotUndoRedoStack<String>(40) }
    val sessionId = remember(activeNoteId) { "sess_${UUID.randomUUID().toString().take(8)}" }

    val parsedNotes = remember(noteRecs) {
        noteRecs.map { rec ->
            val model = runCatching { controller.json.decodeFromString<NoteItem>(rec.payloadJson) }.getOrNull()
                ?: NoteItem(rec.id, rec.workspaceId, "nb_default", title = rec.title, content = rec.summaryText, createdAtUtc = rec.createdAtUtc, updatedAtUtc = rec.updatedAtUtc)
            rec to model
        }
    }
    val visibleNotes = remember(parsedNotes, selectedNotebookId) {
        if (selectedNotebookId == "all") parsedNotes else parsedNotes.filter { it.second.notebookId == selectedNotebookId }
    }

    val stats = remember(editorBody) { LargeDocumentAnalyzer.computeStatsSinglePass(editorBody) }
    val wikiLinks = remember(editorBody) { LargeDocumentAnalyzer.extractWikiLinks(editorBody) }
    val outlineHeadings = remember(editorBody) {
        editorBody.lineSequence().filter { it.trimStart().startsWith("#") }.take(60).toList()
    }
    val backlinks = remember(parsedNotes, editorTitle) {
        if (editorTitle.isBlank()) emptyList()
        else parsedNotes.filter { (_, n) -> n.id != activeNoteId && n.content.contains("[[$editorTitle]]", ignoreCase = true) }
    }

    fun openNote(rec: CanonicalRecord, model: NoteItem) {
        activeNoteId = rec.id
        editorTitle = model.title
        editorBody = model.content
        showDetailCompact = true
        scope.launch {
            controller.draftManager.openSession(state.activeWorkspaceId, rec.id, RecordKind.NOTE, sessionId, model.title, model.content, rec.rev)
        }
    }

    StudioMasterDetailPane(
        isCompact = isCompact,
        showDetailOnCompact = showDetailCompact,
        onBackCompact = { showDetailCompact = false },
        masterTitle = "Notes",
        masterContent = {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            activeNoteId = null
                            editorTitle = ""
                            editorBody = ""
                            showDetailCompact = true
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics { contentDescription = "New Document" },
                    ) { Text("+ New Note") }
                    OutlinedButton(
                        onClick = {
                            val picked = controller.fileDialogBridge?.pickFileToRead("Import Markdown Note", listOf("md", "txt"))
                            if (picked != null) {
                                val (meta, body) = ExtendedFormatInterchange.parsePortableMarkdownNote(picked.second.decodeToString())
                                val t = meta["title"] ?: picked.first.substringBeforeLast('.')
                                controller.saveNote(t, body, notebookId = if (selectedNotebookId == "all") "nb_default" else selectedNotebookId)
                            }
                        },
                        modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = "Import Markdown note" },
                    ) { Text("Import") }
                }

                // Notebooks Filter & Creation
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selectedNotebookId == "all", onClick = { selectedNotebookId = "all" }, label = { Text("All (${parsedNotes.size})") })
                    FilterChip(selected = selectedNotebookId == "nb_default", onClick = { selectedNotebookId = "nb_default" }, label = { Text("General") })
                    notebookRecs.forEach { nb ->
                        FilterChip(selected = selectedNotebookId == nb.id, onClick = { selectedNotebookId = nb.id }, label = { Text(nb.title) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newNotebookName, onValueChange = { newNotebookName = it }, label = { Text("New notebook") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        if (newNotebookName.isNotBlank()) {
                            controller.createOrUpdateRecord(RecordKind.NOTEBOOK, newNotebookName.trim(), "{}")
                            newNotebookName = ""
                        }
                    }) { Text("+") }
                }

                // Draft Recovery Inbox
                if (drafts.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Uncommitted Drafts (${drafts.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            drafts.take(3).forEach { d ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(d.titleDraft.ifBlank { "Untitled draft" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                    TextButton(onClick = {
                                        activeNoteId = d.recordId
                                        editorTitle = d.titleDraft
                                        editorBody = d.bodyDraft
                                        showDetailCompact = true
                                    }) { Text("Restore") }
                                }
                            }
                        }
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    if (visibleNotes.isEmpty()) {
                        item { StudioEmptyState("No Notes Yet", "Create a new note or import a Markdown document.") }
                    }
                    items(visibleNotes, key = { it.first.id }) { (rec, model) ->
                        OutlinedCard(
                            onClick = { openNote(rec, model) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(model.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(model.content.take(90).replace('\n', ' '), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                        }
                    }
                }
            }
        },
        detailContent = {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Formatting & Action Toolbar
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            val nb = if (selectedNotebookId == "all") "nb_default" else selectedNotebookId
                            controller.saveNote(editorTitle, editorBody, existingNoteId = activeNoteId, notebookId = nb)
                            activeNoteId?.let { id ->
                                scope.launch { controller.repository.deleteDraft(state.activeWorkspaceId, id, sessionId) }
                            }
                        },
                        modifier = Modifier.heightIn(min = 42.dp).semantics { contentDescription = "Save Document" },
                    ) { Text("Save") }
                    OutlinedButton(onClick = { undoRedo.undo(editorBody)?.let { editorBody = it } }, enabled = undoRedo.canUndo, modifier = Modifier.heightIn(min = 42.dp)) { Text("Undo") }
                    OutlinedButton(onClick = { undoRedo.redo(editorBody)?.let { editorBody = it } }, enabled = undoRedo.canRedo, modifier = Modifier.heightIn(min = 42.dp)) { Text("Redo") }
                    listOf("H1" to "\n# ", "H2" to "\n## ", "Bold" to "**text**", "List" to "\n- ", "Checklist" to "\n- [ ] ", "Table" to "\n| Col A | Col B |\n|---|---|\n| 1 | 2 |\n").forEach { (lbl, snippet) ->
                        OutlinedButton(
                            onClick = {
                                undoRedo.pushCheckpoint(editorBody)
                                editorBody += snippet
                            },
                            modifier = Modifier.heightIn(min = 42.dp),
                        ) { Text(lbl) }
                    }
                    OutlinedButton(onClick = { showOutlineAndLinks = !showOutlineAndLinks }, modifier = Modifier.heightIn(min = 42.dp)) { Text("Outline & Links") }
                    if (activeNoteId != null) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    revisions = controller.repository.listRevisions(state.activeWorkspaceId, activeNoteId!!)
                                    showRevisions = true
                                }
                            },
                            modifier = Modifier.heightIn(min = 42.dp),
                        ) { Text("History") }
                        OutlinedButton(
                            onClick = {
                                val art = ExtendedFormatInterchange.exportPortableMarkdownNote(
                                    NoteItem(activeNoteId!!, state.activeWorkspaceId, "nb_default", title = editorTitle, content = editorBody, createdAtUtc = "", updatedAtUtc = ""),
                                )
                                controller.fileDialogBridge?.pickFileToSave("Export Markdown", art.filename, art.bytes)
                                controller.setBanner("Prepared Markdown export (${art.filename})")
                            },
                            modifier = Modifier.heightIn(min = 42.dp),
                        ) { Text("Export .md") }
                        TextButton(onClick = {
                            controller.moveRecordToTrash(activeNoteId!!)
                            activeNoteId = null
                            editorTitle = ""
                            editorBody = ""
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }

                OutlinedTextField(
                    value = editorTitle,
                    onValueChange = { editorTitle = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = editorBody,
                    onValueChange = { next ->
                        if (kotlin.math.abs(next.length - editorBody.length) >= 12) undoRedo.pushCheckpoint(editorBody)
                        editorBody = next
                        activeNoteId?.let { id ->
                            scope.launch { controller.draftManager.updateDraft(state.activeWorkspaceId, id, sessionId, editorTitle, next) }
                        }
                    },
                    label = { Text("Write Markdown, outlines, tables, or [[WikiLinks]]…") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                Text(
                    "${stats.wordCount} words · ${stats.paragraphCount} paragraphs · ${stats.charCount} characters" +
                        if (wikiLinks.isNotEmpty()) " · Links: ${wikiLinks.joinToString()}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (showOutlineAndLinks) {
                    StudioCard(title = "Document Outline & Backlinks") {
                        Text("Headings (${outlineHeadings.size}): ${outlineHeadings.take(8).joinToString(" | ").ifEmpty { "None" }}", style = MaterialTheme.typography.bodySmall)
                        Text("Backlinks (${backlinks.size}): ${backlinks.joinToString { it.second.title }.ifEmpty { "No notes link here yet" }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    )

    if (showRevisions && activeNoteId != null) {
        AlertDialog(
            onDismissRequest = { showRevisions = false },
            title = { Text("Revision History (${revisions.size})") },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(revisions, key = { it.id }) { rev ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("v${rev.rev} · ${rev.checkpointLabel ?: "Saved"}", fontWeight = FontWeight.Medium)
                                    Text(rev.createdAtUtc, style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        val restored = controller.repository.restoreRevision(state.activeWorkspaceId, activeNoteId!!, rev.id)
                                        val model = runCatching { controller.json.decodeFromString<NoteItem>(restored.payloadJson) }.getOrNull()
                                        if (model != null) {
                                            editorTitle = model.title
                                            editorBody = model.content
                                        }
                                        showRevisions = false
                                    }
                                }) { Text("Restore") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRevisions = false }) { Text("Close") } },
        )
    }
}

@Composable
fun DiaryAndWellnessStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    val recs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.DIARY, 120) }.collectAsState(emptyList())
    val entries = remember(recs) { recs.mapNotNull { runCatching { controller.json.decodeFromString<DiaryEntry>(it.payloadJson) }.getOrNull() } }
    val trends = remember(entries) { LifeWellnessAndLearningEngines.analyzeWellnessTrends(entries) }

    var editingId by remember { mutableStateOf<String?>(null) }
    var dateIso by remember { mutableStateOf(SystemDomainClock.todayIso()) }
    var mood by remember { mutableStateOf(7f) }
    var energy by remember { mutableStateOf(7f) }
    var stress by remember { mutableStateOf(3f) }
    var sleep by remember { mutableStateOf("7.5") }
    var water by remember { mutableStateOf("8") }
    var urinations by remember { mutableStateOf(0) }
    var bowels by remember { mutableStateOf(0) }
    var narrative by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioMetricTile("Entries", trends.entryCount.toString(), modifier = Modifier.weight(1f))
                StudioMetricTile("Avg Mood", trends.metricAverages["mood"]?.let { "%.1f/10".format(it) } ?: "—", modifier = Modifier.weight(1f))
                StudioMetricTile("Avg Sleep", trends.avgSleepHours?.let { "%.1fh".format(it) } ?: "—", modifier = Modifier.weight(1f))
            }
        }
        item {
            StudioCard(title = if (editingId == null) "Daily Journal & Wellness" else "Editing Diary ($dateIso)") {
                OutlinedTextField(value = dateIso, onValueChange = { dateIso = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Mood: ${mood.toInt()}/10 · Energy: ${energy.toInt()}/10 · Stress: ${stress.toInt()}/10")
                Slider(value = mood, onValueChange = { mood = it }, valueRange = 1f..10f, steps = 8)
                Slider(value = energy, onValueChange = { energy = it }, valueRange = 1f..10f, steps = 8)
                Slider(value = stress, onValueChange = { stress = it }, valueRange = 1f..10f, steps = 8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sleep, onValueChange = { sleep = it }, label = { Text("Sleep (hrs)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = water, onValueChange = { water = it }, label = { Text("Water (cups)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { urinations++ }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Urination +1 ($urinations)") }
                    OutlinedButton(onClick = { bowels++ }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Bowel +1 ($bowels)") }
                }
                OutlinedTextField(value = narrative, onValueChange = { narrative = it }, label = { Text("Daily reflection") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val now = SystemDomainClock.nowUtcIso()
                            val id = editingId ?: UUID.randomUUID().toString()
                            val entry = DiaryEntry(
                                id = id,
                                workspaceId = state.activeWorkspaceId,
                                dateIso = dateIso,
                                ratings = mapOf("mood" to mood.toDouble(), "energy" to energy.toDouble(), "stress" to stress.toDouble()),
                                sleepHours = sleep.toDoubleOrNull(),
                                waterCups = water.toIntOrNull(),
                                summary = narrative.take(120),
                                narrative = narrative,
                                bathroomUrinations = urinations,
                                bathroomBowelMovements = bowels,
                                createdAtUtc = now,
                                updatedAtUtc = now,
                            )
                            controller.createOrUpdateRecord(RecordKind.DIARY, "Diary $dateIso", controller.json.encodeToString(entry), "Mood ${mood.toInt()}/10 · Sleep ${sleep}h", existingId = editingId, scheduleStartIso = dateIso, scheduleEndIso = dateIso)
                            editingId = null
                            narrative = ""
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Save Diary Entry") }
                    if (editingId != null) {
                        TextButton(onClick = { editingId = null; narrative = "" }) { Text("New Entry") }
                    }
                }
            }
        }
        items(entries, key = { it.id }) { e ->
            OutlinedCard(
                onClick = {
                    editingId = e.id
                    dateIso = e.dateIso
                    mood = (e.ratings["mood"] ?: 7.0).toFloat()
                    energy = (e.ratings["energy"] ?: 7.0).toFloat()
                    stress = (e.ratings["stress"] ?: 3.0).toFloat()
                    sleep = e.sleepHours?.toString() ?: ""
                    water = e.waterCups?.toString() ?: ""
                    urinations = e.bathroomUrinations
                    bowels = e.bathroomBowelMovements
                    narrative = e.narrative
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("${e.dateIso} · Mood ${e.ratings["mood"]?.toInt() ?: "—"}/10 · Sleep ${e.sleepHours ?: "—"}h", fontWeight = FontWeight.Medium)
                        Text(e.narrative.ifBlank { "(No reflection text)" }, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    TextButton(onClick = { controller.moveRecordToTrash(e.id) }) { Text("Delete") }
                }
            }
        }
    }
}

// ============================================================================
// 4. REAL LIVE TIME SUITE (Update 2 §13 — Replaces simulated +1m completely!)
// ============================================================================

@Serializable
data class PersistedRunningTimer(
    val id: String,
    val label: String,
    val totalDurationMs: Long,
    val remainingAtPauseMs: Long,
    val targetEpochMs: Long? = null, // Non-null while actively ticking! Survives app restarts!
    val isPomodoro: Boolean = false,
)

@Serializable
data class PersistedCountdown(
    val id: String,
    val title: String,
    val targetDateIso: String,
)

@Composable
fun RealTimeSuiteStudio(
    route: PlanadayRoute,
    state: PlanadayShellState,
    controller: PlanadayWorkspaceController,
) {
    // REAL LIVE WALL-CLOCK & MONOTONIC TICKER (zero simulated time!)
    var wallNowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var monoNowNs by remember { mutableStateOf(System.nanoTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            wallNowEpochMs = System.currentTimeMillis()
            monoNowNs = System.nanoTime()
            delay(200L)
        }
    }

    var activeTab by remember(route) {
        mutableStateOf(
            when (route) {
                PlanadayRoute.CLOCK_WORLD -> 0
                PlanadayRoute.TIMERS -> 1
                PlanadayRoute.STOPWATCH -> 2
                PlanadayRoute.ALARMS -> 3
                PlanadayRoute.COUNTDOWNS -> 4
                else -> 1
            }
        )
    }

    val timerRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.TIMER, 50) }.collectAsState(emptyList())
    val alarmRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.ALARM, 50) }.collectAsState(emptyList())
    val countdownRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.COUNTDOWN, 50) }.collectAsState(emptyList())
    val ledgerRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.TIME_LEDGER, 50) }.collectAsState(emptyList())

    val timers = remember(timerRecs) { timerRecs.mapNotNull { runCatching { controller.json.decodeFromString<PersistedRunningTimer>(it.payloadJson) }.getOrNull() } }
    val alarms = remember(alarmRecs) { alarmRecs.mapNotNull { runCatching { controller.json.decodeFromString<AlarmItem>(it.payloadJson) }.getOrNull() } }
    val countdowns = remember(countdownRecs) { countdownRecs.mapNotNull { runCatching { controller.json.decodeFromString<PersistedCountdown>(it.payloadJson) }.getOrNull() } }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StudioTabRow(listOf("World Clock", "Timers & Pomodoro", "Stopwatch & Ledger", "Alarms", "Countdowns"), activeTab, { activeTab = it })

        when (activeTab) {
            0 -> {
                var clockStyle by remember { mutableStateOf(ClockDesign.ANALOG_TRADITIONAL) }
                val localNow = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(wallNowEpochMs), ZoneId.systemDefault())
                val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
                val cities = listOf("Local" to ZoneId.systemDefault().id, "UTC" to "UTC", "New York" to "America/New_York", "Los Angeles" to "America/Los_Angeles", "London" to "Europe/London", "Tokyo" to "Asia/Tokyo")

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Live Clock · ${localNow.format(timeFmt)}", subtitle = localNow.zone.id) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ClockDesign.entries.forEach { d ->
                                    FilterChip(selected = clockStyle == d, onClick = { clockStyle = d }, label = { Text(d.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) })
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                val primary = MaterialTheme.colorScheme.primary
                                val err = MaterialTheme.colorScheme.error
                                val fg = MaterialTheme.colorScheme.onSurface
                                Canvas(Modifier.size(128.dp).semantics { contentDescription = "Live ticking clock face" }) {
                                    val r = size.minDimension / 2f - 4f
                                    val c = center
                                    drawCircle(fg, r, c, style = Stroke(width = 3f))
                                    for (h in 0 until 12) {
                                        val a = Math.toRadians(h * 30.0 - 90.0)
                                        drawLine(fg, Offset(c.x + (r * 0.84f * cos(a)).toFloat(), c.y + (r * 0.84f * sin(a)).toFloat()), Offset(c.x + (r * 0.96f * cos(a)).toFloat(), c.y + (r * 0.96f * sin(a)).toFloat()), strokeWidth = 2f)
                                    }
                                    val secAngle = Math.toRadians(localNow.second * 6.0 - 90.0)
                                    val minAngle = Math.toRadians((localNow.minute + localNow.second / 60.0) * 6.0 - 90.0)
                                    val hrAngle = Math.toRadians(((localNow.hour % 12) + localNow.minute / 60.0) * 30.0 - 90.0)
                                    drawLine(fg, c, Offset(c.x + (r * 0.50f * cos(hrAngle)).toFloat(), c.y + (r * 0.50f * sin(hrAngle)).toFloat()), strokeWidth = 5f)
                                    drawLine(primary, c, Offset(c.x + (r * 0.72f * cos(minAngle)).toFloat(), c.y + (r * 0.72f * sin(minAngle)).toFloat()), strokeWidth = 3.5f)
                                    drawLine(err, c, Offset(c.x + (r * 0.82f * cos(secAngle)).toFloat(), c.y + (r * 0.82f * sin(secAngle)).toFloat()), strokeWidth = 1.5f)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(localNow.format(timeFmt), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Text(localNow.toLocalDate().toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(cities) { (name, zone) ->
                        val zdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(wallNowEpochMs), ZoneId.of(zone))
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(name, fontWeight = FontWeight.SemiBold)
                                    Text(zone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(zdt.format(timeFmt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            1 -> {
                // REAL MULTIPLE SIMULTANEOUS TIMERS & POMODORO (persisted!)
                var timerName by remember { mutableStateOf("Focus Session") }
                var minutesInput by remember { mutableStateOf("25") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Create Live Timer or Pomodoro", subtitle = "Runs in real time and recovers automatically after restart") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = timerName, onValueChange = { timerName = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = minutesInput, onValueChange = { minutesInput = it }, label = { Text("Min") }, singleLine = true, modifier = Modifier.width(88.dp))
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val ms = (minutesInput.toLongOrNull()?.coerceIn(1, 720) ?: 25L) * 60_000L
                                    val t = PersistedRunningTimer(UUID.randomUUID().toString(), timerName.ifBlank { "Timer" }, ms, ms, targetEpochMs = System.currentTimeMillis() + ms, isPomodoro = minutesInput == "25")
                                    controller.createOrUpdateRecord(RecordKind.TIMER, t.label, controller.json.encodeToString(t), "${ms / 60000}m", existingId = null)
                                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Start Timer") }
                                listOf("Pomodoro 25m" to 25L, "Short Break 5m" to 5L, "Long Break 15m" to 15L).forEach { (lbl, m) ->
                                    OutlinedButton(onClick = {
                                        val ms = m * 60_000L
                                        val t = PersistedRunningTimer(UUID.randomUUID().toString(), lbl, ms, ms, targetEpochMs = System.currentTimeMillis() + ms, isPomodoro = true)
                                        controller.createOrUpdateRecord(RecordKind.TIMER, t.label, controller.json.encodeToString(t), "${m}m")
                                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(lbl) }
                                }
                            }
                        }
                    }
                    if (timers.isEmpty()) {
                        item { StudioEmptyState("No Active Timers", "Start a focus session or custom timer above.") }
                    }
                    items(timers, key = { it.id }) { t ->
                        val remainingMs = if (t.targetEpochMs != null) (t.targetEpochMs - wallNowEpochMs).coerceAtLeast(0L) else t.remainingAtPauseMs
                        val isRunning = t.targetEpochMs != null && remainingMs > 0L
                        val finished = t.targetEpochMs != null && remainingMs == 0L
                        val secs = remainingMs / 1000L
                        val formatted = "%02d:%02d:%02d".format(secs / 3600, (secs % 3600) / 60, secs % 60)
                        val progress = if (t.totalDurationMs > 0) 1f - (remainingMs.toFloat() / t.totalDurationMs.toFloat()) else 1f

                        StudioCard(
                            title = "${t.label} ${if (finished) "— Completed!" else ""}",
                            subtitle = if (isRunning) "Running live" else if (finished) "Finished — log to Time Ledger" else "Paused",
                        ) {
                            Text(formatted, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (finished) LocalPlanadaySpacing.current.semantics.success else MaterialTheme.colorScheme.primary)
                            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val upd = if (isRunning) {
                                        t.copy(remainingAtPauseMs = remainingMs, targetEpochMs = null)
                                    } else {
                                        val rem = if (remainingMs == 0L) t.totalDurationMs else remainingMs
                                        t.copy(remainingAtPauseMs = rem, targetEpochMs = System.currentTimeMillis() + rem)
                                    }
                                    controller.createOrUpdateRecord(RecordKind.TIMER, upd.label, controller.json.encodeToString(upd), formatted, existingId = upd.id)
                                }) { Text(if (isRunning) "Pause" else "Start") }
                                OutlinedButton(onClick = {
                                    val upd = t.copy(remainingAtPauseMs = t.totalDurationMs, targetEpochMs = null)
                                    controller.createOrUpdateRecord(RecordKind.TIMER, upd.label, controller.json.encodeToString(upd), "Reset", existingId = upd.id)
                                }) { Text("Reset") }
                                OutlinedButton(onClick = {
                                    val elapsedSec = ((t.totalDurationMs - remainingMs) / 1000L).coerceAtLeast(1L)
                                    val entry = TimeLedgerEntry(UUID.randomUUID().toString(), state.activeWorkspaceId, if (t.isPomodoro) "pomodoro" else "timer", t.label, null, SystemDomainClock.nowUtcIso(), SystemDomainClock.nowUtcIso(), elapsedSec, SystemDomainClock.todayIso())
                                    controller.createOrUpdateRecord(RecordKind.TIME_LEDGER, "${t.label} (${elapsedSec}s)", controller.json.encodeToString(entry), "Logged ${elapsedSec}s")
                                }) { Text("Log Session") }
                                TextButton(onClick = { controller.moveRecordToTrash(t.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            2 -> {
                // REAL MONOTONIC STOPWATCH + LAPS + TIME LEDGER
                var swRunning by remember { mutableStateOf(false) }
                var swStartMonoNs by remember { mutableStateOf(0L) }
                var swAccumulatedMs by remember { mutableStateOf(0L) }
                var laps by remember { mutableStateOf<List<Long>>(emptyList()) }
                val elapsedMs = swAccumulatedMs + if (swRunning) ((monoNowNs - swStartMonoNs) / 1_000_000L).coerceAtLeast(0L) else 0L
                val swDisplay = "%02d:%02d.%02d".format(elapsedMs / 60_000, (elapsedMs / 1000) % 60, (elapsedMs % 1000) / 10)

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Monotonic Stopwatch", subtitle = "Backed by monotonic system clock — immune to wall-clock jumps") {
                            Text(swDisplay, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    if (swRunning) {
                                        swAccumulatedMs = elapsedMs
                                        swRunning = false
                                    } else {
                                        swStartMonoNs = System.nanoTime()
                                        swRunning = true
                                    }
                                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (swRunning) "Pause" else "Start") }
                                OutlinedButton(onClick = { if (elapsedMs > 0) laps = laps + elapsedMs }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Lap") }
                                OutlinedButton(onClick = {
                                    if (elapsedMs > 0) {
                                        val entry = TimeLedgerEntry(UUID.randomUUID().toString(), state.activeWorkspaceId, "stopwatch", "Stopwatch Session", null, SystemDomainClock.nowUtcIso(), SystemDomainClock.nowUtcIso(), elapsedMs / 1000L, SystemDomainClock.todayIso(), laps)
                                        controller.createOrUpdateRecord(RecordKind.TIME_LEDGER, "Stopwatch ($swDisplay)", controller.json.encodeToString(entry), "${laps.size} laps")
                                    }
                                    swRunning = false; swAccumulatedMs = 0L; laps = emptyList()
                                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save & Reset") }
                            }
                            if (laps.isNotEmpty()) {
                                Text("Laps: " + laps.mapIndexed { i, ms -> "#${i + 1}: ${ms / 1000.0}s" }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item { Text("Time Ledger History (${ledgerRecs.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                    items(ledgerRecs, key = { it.id }) { rec ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(rec.title, fontWeight = FontWeight.Medium)
                                Text(rec.updatedAtUtc.take(16), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            3 -> {
                // REAL PERSISTED ALARMS + QUIET HOURS + HONEST OS DIAGNOSTICS
                var label by remember { mutableStateOf("Morning Routine") }
                var timeStr by remember { mutableStateOf("07:00") }
                var quietEnabled by remember { mutableStateOf(true) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(
                            title = "Alarms & Quiet Hours",
                            subtitle = "Platform notice: OS battery savers or revoked notification permissions can defer alarms when the app process is restricted.",
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Alarm label") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = timeStr, onValueChange = { timeStr = it }, label = { Text("HH:MM") }, singleLine = true, modifier = Modifier.width(105.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = quietEnabled, onCheckedChange = { quietEnabled = it }, modifier = Modifier.semantics { contentDescription = "Quiet hours 22:00 to 07:00" })
                                Text("Respect quiet hours (22:00–07:00)")
                            }
                            Button(onClick = {
                                val a = AlarmItem(UUID.randomUUID().toString(), state.activeWorkspaceId, label.ifBlank { "Alarm" }, timeStr, ZoneId.systemDefault().id)
                                controller.createOrUpdateRecord(RecordKind.ALARM, "${a.timeLocal} · ${a.label}", controller.json.encodeToString(a), a.timeZone)
                            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save Alarm") }
                        }
                    }
                    items(alarms, key = { it.id }) { a ->
                        val next = NotificationScheduleEngine.computeNextAlarmOccurrence(a, SystemDomainClock.todayIso(), "12:00", QuietHoursPolicy(quietEnabled, "22:00", "07:00"))
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${a.timeLocal} · ${a.label}", fontWeight = FontWeight.SemiBold)
                                    Text("Next: ${next?.fireDateIso ?: "—"} ${next?.fireTimeLocal ?: ""} ${if (next?.deferredByQuietHours == true) "(Deferred by quiet hours)" else ""}", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = a.enabled,
                                    onCheckedChange = { on ->
                                        val upd = a.copy(enabled = on)
                                        controller.createOrUpdateRecord(RecordKind.ALARM, "${upd.timeLocal} · ${upd.label}", controller.json.encodeToString(upd), upd.timeZone, existingId = upd.id)
                                    },
                                )
                                TextButton(onClick = { controller.moveRecordToTrash(a.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            else -> {
                // REAL LIVE COUNTDOWNS
                var title by remember { mutableStateOf("") }
                var targetDate by remember { mutableStateOf(CalendarDateMath.addDays(SystemDomainClock.todayIso(), 30)) }
                val nowDt = LocalDateTime.now()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "New Countdown") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Event or target") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = targetDate, onValueChange = { targetDate = it }, label = { Text("YYYY-MM-DD") }, singleLine = true, modifier = Modifier.width(140.dp))
                            }
                            Button(onClick = {
                                if (title.isNotBlank()) {
                                    val c = PersistedCountdown(UUID.randomUUID().toString(), title.trim(), targetDate)
                                    controller.createOrUpdateRecord(RecordKind.COUNTDOWN, c.title, controller.json.encodeToString(c), c.targetDateIso, scheduleStartIso = c.targetDateIso, scheduleEndIso = c.targetDateIso)
                                    title = ""
                                }
                            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add Countdown") }
                        }
                    }
                    items(countdowns, key = { it.id }) { c ->
                        val rem = runCatching {
                            val t = LocalDate.parse(c.targetDateIso).atStartOfDay()
                            val d = Duration.between(nowDt, t)
                            if (d.isNegative) "Reached (${c.targetDateIso})" else "${d.toDays()}d ${d.toHoursPart()}h ${d.toMinutesPart()}m ${d.toSecondsPart()}s"
                        }.getOrDefault(c.targetDateIso)
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.title, fontWeight = FontWeight.SemiBold)
                                    Text("Target: ${c.targetDateIso}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(rem, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                TextButton(onClick = { controller.moveRecordToTrash(c.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}
