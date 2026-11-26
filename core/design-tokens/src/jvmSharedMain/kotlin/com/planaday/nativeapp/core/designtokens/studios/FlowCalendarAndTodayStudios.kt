package com.planaday.nativeapp.core.designtokens.studios

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.designtokens.*
import com.planaday.nativeapp.core.domain.*
import com.planaday.nativeapp.core.interchange.Rfc5545IcsCodec
import com.planaday.nativeapp.core.model.*
import java.time.LocalTime
import java.util.UUID
import kotlin.math.roundToInt

// ============================================================================
// 1. Interactive Flow Calendar (Day / 3-Day / Week / Month / Year / Agenda)
//    Real scrollable time grid, zoom, layers, drag-move & resize, canonical tasks, ICS import/export (§8)
// ============================================================================

private enum class CalendarViewMode(val label: String, val spanDays: Int) {
    DAY("Day", 1),
    THREE_DAY("3-Day", 3),
    WEEK("Week", 7),
    MONTH("Month", 31),
    YEAR("Year", 365),
    AGENDA("Agenda", 14),
}

private val CalendarLayerPalette: Map<String, Pair<String, Color>> = mapOf(
    "cal_default" to ("Personal" to Color(0xFF9A6525)),
    "cal_work" to ("Work" to Color(0xFF1D4ED8)),
    "cal_tasks" to ("Scheduled Tasks" to Color(0xFF0F766E)),
    "cal_lab" to ("Laboratory" to Color(0xFF7E22CE)),
)

@Composable
fun InteractiveFlowCalendarStudio(
    state: PlanadayShellState,
    controller: PlanadayWorkspaceController,
    isCompact: Boolean,
    initialModePeriod: Boolean = false,
) {
    var mode by remember { mutableStateOf(if (initialModePeriod) CalendarViewMode.MONTH else CalendarViewMode.DAY) }
    var anchorDateIso by remember { mutableStateOf(SystemDomainClock.todayIso()) }
    var visibleLayers by remember { mutableStateOf(CalendarLayerPalette.keys) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var creatingNew by remember { mutableStateOf(false) }
    var draftStartTime by remember { mutableStateOf("09:00") }
    var draftEndTime by remember { mutableStateOf("10:00") }
    var draftDateIso by remember { mutableStateOf(anchorDateIso) }
    var overlapAlert by remember { mutableStateOf<String?>(null) }
    var showIcsDialog by remember { mutableStateOf(false) }

    val rangeEndIso = remember(anchorDateIso, mode) {
        CalendarDateMath.addDays(anchorDateIso, mode.spanDays)
    }

    // Indexed date-range Room Flow queries — NEVER loads entire historical database! (§6)
    val eventRecords by remember(state.activeWorkspaceId, anchorDateIso, rangeEndIso) {
        controller.observeScheduleWindow(RecordKind.EVENT, CalendarDateMath.addDays(anchorDateIso, -7), rangeEndIso, limit = 500)
    }.collectAsState(initial = emptyList())

    val taskRecords by remember(state.activeWorkspaceId) {
        controller.observeKind(RecordKind.TASK, limit = 150)
    }.collectAsState(initial = emptyList())

    val events = remember(eventRecords, visibleLayers) {
        eventRecords.mapNotNull { runCatching { controller.json.decodeFromString<CalendarEvent>(it.payloadJson) }.getOrNull() }
            .filter { it.calendarId in visibleLayers }
    }
    val tasks = remember(taskRecords) {
        taskRecords.mapNotNull { runCatching { controller.json.decodeFromString<TaskItem>(it.payloadJson) }.getOrNull() }
    }

    val cfg = state.calendarConfig
    val hourHeightDp = remember(cfg.intervalMin) {
        when {
            cfg.intervalMin <= 15 -> 112.dp
            cfg.intervalMin <= 30 -> 78.dp
            cfg.intervalMin <= 60 -> 58.dp
            else -> 42.dp
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Top Calendar Control Bar
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { anchorDateIso = CalendarDateMath.addDays(anchorDateIso, -mode.spanDays.coerceAtMost(30)) },
                modifier = Modifier.heightIn(min = 42.dp).semantics { contentDescription = "Previous calendar period" },
            ) { Text("‹") }
            FilledTonalButton(
                onClick = { anchorDateIso = SystemDomainClock.todayIso() },
                modifier = Modifier.heightIn(min = 42.dp).semantics { contentDescription = "Jump to today" },
            ) { Text("Today · $anchorDateIso") }
            OutlinedButton(
                onClick = { anchorDateIso = CalendarDateMath.addDays(anchorDateIso, mode.spanDays.coerceAtMost(30)) },
                modifier = Modifier.heightIn(min = 42.dp).semantics { contentDescription = "Next calendar period" },
            ) { Text("›") }

            CalendarViewMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { mode = m },
                    label = { Text(m.label) },
                )
            }

            Button(
                onClick = {
                    draftDateIso = anchorDateIso
                    draftStartTime = "09:00"
                    draftEndTime = "10:00"
                    creatingNew = true
                },
                modifier = Modifier.heightIn(min = 42.dp).semantics { contentDescription = "Create new calendar event" },
            ) { Text("+ Event") }

            OutlinedButton(
                onClick = { showIcsDialog = true },
                modifier = Modifier.heightIn(min = 42.dp).semantics { contentDescription = "Import or export ICS calendar" },
            ) { Text("ICS Import / Export") }
        }

        // Zoom + Calendar Layers Strip
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Zoom:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(15, 30, 60, 120).forEach { interval ->
                FilterChip(
                    selected = cfg.intervalMin == interval,
                    onClick = { controller.updateCalendarConfig { it.copy(intervalMin = interval) } },
                    label = { Text(if (interval < 60) "${interval}m" else "${interval / 60}h") },
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("Layers:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CalendarLayerPalette.forEach { (id, info) ->
                val enabled = id in visibleLayers
                FilterChip(
                    selected = enabled,
                    onClick = { visibleLayers = if (enabled) visibleLayers - id else visibleLayers + id },
                    label = { Text(info.first) },
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("Overlap:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(CalendarOverlapPolicy.SIDE_BY_SIDE, CalendarOverlapPolicy.WARN, CalendarOverlapPolicy.PREVENT).forEach { p ->
                FilterChip(
                    selected = cfg.overlapPolicy == p,
                    onClick = { controller.updateCalendarConfig { it.copy(overlapPolicy = p) } },
                    label = { Text(p.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        overlapAlert?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
            }
        }

        // Main Calendar Viewport + Unscheduled Canonical Tasks Rail (Desktop split / Compact bottom strip)
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (mode) {
                    CalendarViewMode.DAY, CalendarViewMode.THREE_DAY, CalendarViewMode.WEEK -> {
                        val days = (0 until mode.spanDays).map { CalendarDateMath.addDays(anchorDateIso, it) }
                        InteractiveTimeGrid(
                            daysIso = days,
                            events = events,
                            startHour = cfg.visibleStartHour,
                            endHour = cfg.visibleEndHour,
                            hourHeightDp = hourHeightDp,
                            onSlotClick = { day, hour ->
                                draftDateIso = day
                                draftStartTime = "%02d:00".format(hour)
                                draftEndTime = "%02d:00".format((hour + 1).coerceAtMost(23))
                                creatingNew = true
                            },
                            onEventClick = { editingEvent = it },
                            onEventMoveOrResize = { movedEvent ->
                                val s = CalendarDateMath.timeToMinutes(movedEvent.startTimeLocal ?: "09:00")
                                val e = CalendarDateMath.timeToMinutes(movedEvent.endTimeLocal ?: "10:00")
                                val peers = events.filter { it.id != movedEvent.id && it.startDateIso == movedEvent.startDateIso }
                                when (val v = CalendarOverlapEngine.validateNewOrMovedEvent(s, e, peers, cfg.overlapPolicy)) {
                                    is OverlapValidationResult.Prevented ->
                                        overlapAlert = "Move prevented: overlaps ${v.conflictingIds.size} existing event(s)."
                                    is OverlapValidationResult.Warning -> {
                                        overlapAlert = "Overlap notice: overlaps ${v.conflictingIds.size} event(s)."
                                        controller.saveCalendarEvent(movedEvent, isNew = false)
                                    }
                                    OverlapValidationResult.Allowed -> {
                                        overlapAlert = null
                                        controller.saveCalendarEvent(movedEvent, isNew = false)
                                    }
                                }
                            },
                        )
                    }
                    CalendarViewMode.MONTH -> {
                        MonthGridCalendarView(
                            anchorDateIso = anchorDateIso,
                            events = events,
                            tasks = tasks,
                            onSelectDay = { day ->
                                anchorDateIso = day
                                mode = CalendarViewMode.DAY
                            },
                        )
                    }
                    CalendarViewMode.YEAR -> {
                        YearOverviewCalendarView(
                            year = anchorDateIso.take(4).toIntOrNull() ?: 2026,
                            events = events,
                            onSelectMonthStart = { mStart ->
                                anchorDateIso = mStart
                                mode = CalendarViewMode.MONTH
                            },
                        )
                    }
                    CalendarViewMode.AGENDA -> {
                        AgendaCalendarView(
                            events = events.sortedWith(compareBy({ it.startDateIso }, { it.startTimeLocal ?: "" })),
                            tasks = tasks.filter { it.status == RecordStatus.ACTIVE },
                            onEventClick = { editingEvent = it },
                            onScheduleTask = { t ->
                                controller.scheduleCanonicalTaskOnCalendar(t, anchorDateIso, "10:00", CalendarDateMath.minutesToTime(10 * 60 + (t.estimateMin ?: 30)))
                            },
                        )
                    }
                }
            }

            // Desktop Right Rail: Real Canonical Tasks ready to schedule onto Calendar (zero duplication!)
            if (!isCompact) {
                val activeTasks = tasks.filter { it.status == RecordStatus.ACTIVE }
                Surface(
                    modifier = Modifier.width(248.dp).fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, LocalPlanadaySpacing.current.semantics.subtleBorder),
                ) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tasks to Schedule (${activeTasks.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        val autoPlan = remember(activeTasks, events, anchorDateIso) {
                            TaskSchedulerEngine.buildDaySchedule(
                                dateIso = anchorDateIso,
                                nowMin = 8 * 60,
                                preferredEndTimeLocal = "20:00",
                                tasks = activeTasks,
                                dayEvents = events.filter { it.startDateIso == anchorDateIso },
                            )
                        }
                        if (autoPlan.slots.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    autoPlan.slots.forEach { slot ->
                                        controller.scheduleCanonicalTaskOnCalendar(
                                            slot.task,
                                            anchorDateIso,
                                            CalendarDateMath.minutesToTime(slot.startMin),
                                            CalendarDateMath.minutesToTime(slot.endMin),
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp).semantics { contentDescription = "Auto-schedule suggested task slots" },
                            ) { Text("Auto-Place ${autoPlan.slots.size} Tasks") }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                            items(activeTasks, key = { it.id }) { t ->
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(t.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${t.priority.name} · ${t.estimateMin ?: 30}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TextButton(
                                            onClick = {
                                                val dur = (t.estimateMin ?: 30).coerceIn(15, 240)
                                                val slot = autoPlan.slots.firstOrNull { it.task.id == t.id }
                                                val sMin = slot?.startMin ?: (9 * 60)
                                                val eMin = slot?.endMin ?: (sMin + dur)
                                                controller.scheduleCanonicalTaskOnCalendar(
                                                    t, anchorDateIso,
                                                    CalendarDateMath.minutesToTime(sMin),
                                                    CalendarDateMath.minutesToTime(eMin),
                                                )
                                            },
                                            modifier = Modifier.heightIn(min = 36.dp).semantics { contentDescription = "Schedule task ${t.title} onto calendar" },
                                        ) { Text("Place on $anchorDateIso") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Event Create / Edit Dialog
    if (creatingNew || editingEvent != null) {
        val base = editingEvent
        var title by remember(base) { mutableStateOf(base?.title ?: "") }
        var date by remember(base) { mutableStateOf(base?.startDateIso ?: draftDateIso) }
        var start by remember(base) { mutableStateOf(base?.startTimeLocal ?: draftStartTime) }
        var end by remember(base) { mutableStateOf(base?.endTimeLocal ?: draftEndTime) }
        var layer by remember(base) { mutableStateOf(base?.calendarId ?: "cal_default") }
        var allDay by remember(base) { mutableStateOf(base?.allDay ?: false) }
        var notes by remember(base) { mutableStateOf(base?.description ?: "") }

        AlertDialog(
            onDismissRequest = { creatingNew = false; editingEvent = null },
            title = { Text(if (base == null) "New Event" else "Edit Event") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allDay, onCheckedChange = { allDay = it }, modifier = Modifier.semantics { contentDescription = "All day event toggle" })
                        Text("All-day event")
                    }
                    if (!allDay) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start HH:MM") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End HH:MM") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CalendarLayerPalette.forEach { (id, info) ->
                            FilterChip(selected = layer == id, onClick = { layer = id }, label = { Text(info.first) })
                        }
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes / Location") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        val now = SystemDomainClock.nowUtcIso()
                        val ev = (base ?: CalendarEvent(
                            id = UUID.randomUUID().toString(),
                            workspaceId = state.activeWorkspaceId,
                            calendarId = layer,
                            title = title.trim(),
                            startDateIso = date,
                            endDateIso = date,
                            createdAtUtc = now,
                            updatedAtUtc = now,
                        )).copy(
                            title = title.trim(),
                            calendarId = layer,
                            startDateIso = date,
                            endDateIso = date,
                            allDay = allDay,
                            startTimeLocal = if (allDay) null else start,
                            endTimeLocal = if (allDay) null else end,
                            description = notes,
                            updatedAtUtc = now,
                        )
                        controller.saveCalendarEvent(ev, isNew = base == null)
                        creatingNew = false
                        editingEvent = null
                    }
                }) { Text("Save Event") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (base != null) {
                        TextButton(onClick = {
                            controller.moveRecordToTrash(base.id)
                            editingEvent = null
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { creatingNew = false; editingEvent = null }) { Text("Cancel") }
                }
            },
        )
    }

    // Real RFC 5545 ICS Import/Export Dialog
    if (showIcsDialog) {
        var icsText by remember { mutableStateOf(Rfc5545IcsCodec.exportCalendar(events, "Planaday Calendar").bytes.decodeToString()) }
        var icsStatus by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showIcsDialog = false },
            title = { Text("Calendar Import & Export (ICS)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val art = Rfc5545IcsCodec.exportCalendar(events, "Planaday")
                            val saved = controller.fileDialogBridge?.pickFileToSave("Export Calendar (.ics)", art.filename, art.bytes)
                            icsStatus = saved?.let { "Saved to $it" } ?: "Ready to copy (${art.bytes.size} bytes)"
                        }) { Text("Save .ics File") }
                        OutlinedButton(onClick = {
                            val picked = controller.fileDialogBridge?.pickFileToRead("Import Calendar (.ics)", listOf("ics"))
                            if (picked != null) {
                                icsText = picked.second.decodeToString()
                                icsStatus = "Loaded ${picked.first}"
                            }
                        }) { Text("Open .ics File") }
                    }
                    OutlinedTextField(value = icsText, onValueChange = { icsText = it }, label = { Text("ICS Content") }, minLines = 6, maxLines = 10, modifier = Modifier.fillMaxWidth())
                    icsStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val imported = Rfc5545IcsCodec.parseCalendar(icsText, state.activeWorkspaceId, "cal_default", SystemDomainClock.nowUtcIso())
                    imported.forEach { controller.saveCalendarEvent(it, isNew = true) }
                    icsStatus = "Imported ${imported.size} event(s)."
                    if (imported.isNotEmpty()) showIcsDialog = false
                }) { Text("Import Events") }
            },
            dismissButton = { TextButton(onClick = { showIcsDialog = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun InteractiveTimeGrid(
    daysIso: List<String>,
    events: List<CalendarEvent>,
    startHour: Int,
    endHour: Int,
    hourHeightDp: androidx.compose.ui.unit.Dp,
    onSlotClick: (String, Int) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onEventMoveOrResize: (CalendarEvent) -> Unit,
) {
    val totalHours = (endHour - startHour).coerceAtLeast(8)
    val gridHeight = hourHeightDp * totalHours
    val scrollState = rememberScrollState()
    val nowTime = remember { LocalTime.now() }
    val nowMinutes = nowTime.hour * 60 + nowTime.minute
    val gridLineColor = LocalPlanadaySpacing.current.semantics.subtleBorder
    val nowLineColor = MaterialTheme.colorScheme.error

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, gridLineColor),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Day Header Row
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(vertical = 8.dp)) {
                Spacer(Modifier.width(56.dp))
                daysIso.forEach { day ->
                    val dayAllDay = events.filter { it.startDateIso == day && it.allDay }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (day == SystemDomainClock.todayIso()) "$day (Today)" else day,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (day == SystemDomainClock.todayIso()) FontWeight.Bold else FontWeight.Medium,
                            color = if (day == SystemDomainClock.todayIso()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        dayAllDay.take(2).forEach { ad ->
                            StudioStatusPill(ad.title)
                        }
                    }
                }
            }
            HorizontalDivider(color = gridLineColor)

            // Scrollable Interactive Time Grid
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState)) {
                Row(Modifier.fillMaxWidth().height(gridHeight)) {
                    // Left Hour Gutter
                    Column(Modifier.width(56.dp).fillMaxHeight()) {
                        for (h in startHour until endHour) {
                            Box(Modifier.height(hourHeightDp).fillMaxWidth().padding(end = 6.dp, top = 2.dp), contentAlignment = Alignment.TopEnd) {
                                Text("%02d:00".format(h), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Day Columns with Major/Minor Gridlines, Now Indicator & Draggable/Resizable Events
                    daysIso.forEach { day ->
                        val dayTimed = events.filter { it.startDateIso == day && !it.allDay }
                        val placed = remember(dayTimed) { CalendarOverlapEngine.layoutDayEvents(dayTimed, CalendarOverlapPolicy.SIDE_BY_SIDE) }

                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                            val colWidth = maxWidth
                            // Gridlines & click-to-create slots
                            Canvas(Modifier.fillMaxSize()) {
                                val hourPx = size.height / totalHours.toFloat()
                                for (i in 0..totalHours) {
                                    val y = i * hourPx
                                    drawLine(gridLineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                                    if (i < totalHours) {
                                        drawLine(gridLineColor.copy(alpha = 0.45f), Offset(0f, y + hourPx / 2f), Offset(size.width, y + hourPx / 2f), strokeWidth = 1f)
                                    }
                                }
                                drawLine(gridLineColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
                                if (day == SystemDomainClock.todayIso() && nowMinutes in (startHour * 60)..(endHour * 60)) {
                                    val frac = (nowMinutes - startHour * 60).toFloat() / (totalHours * 60f)
                                    val y = frac * size.height
                                    drawLine(nowLineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.5f)
                                    drawCircle(nowLineColor, radius = 5f, center = Offset(5f, y))
                                }
                            }

                            // Clickable empty hour slots
                            Column(Modifier.fillMaxSize()) {
                                for (h in startHour until endHour) {
                                    Box(
                                        Modifier.fillMaxWidth().height(hourHeightDp)
                                            .clickable { onSlotClick(day, h) }
                                            .semantics { contentDescription = "Create event on $day at $h:00" }
                                    )
                                }
                            }

                            // Placed draggable & resizable event blocks
                            placed.forEach { block ->
                                val ev = block.event
                                val startOffsetMin = (block.startMin - startHour * 60).coerceAtLeast(0)
                                val durMin = (block.endMin - block.startMin).coerceAtLeast(15)
                                val topDp = hourHeightDp * (startOffsetMin / 60f)
                                val heightDp = (hourHeightDp * (durMin / 60f)).coerceAtLeast(28.dp)
                                val slotW = colWidth / block.laneCount.coerceAtLeast(1)
                                val leftDp = slotW * block.laneIndex
                                val layerColor = CalendarLayerPalette[ev.calendarId]?.second ?: MaterialTheme.colorScheme.primary

                                var dragDeltaY by remember(ev.id, ev.rev) { mutableStateOf(0f) }
                                var resizeDeltaY by remember(ev.id, ev.rev) { mutableStateOf(0f) }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = layerColor.copy(alpha = 0.16f),
                                    border = BorderStroke(1.5.dp, layerColor),
                                    modifier = Modifier
                                        .offset(x = leftDp + 2.dp, y = topDp + (dragDeltaY / 2f).dp)
                                        .width((slotW - 4.dp).coerceAtLeast(40.dp))
                                        .height((heightDp + (resizeDeltaY / 2f).dp).coerceAtLeast(28.dp))
                                        .pointerInput(ev.id, ev.rev) {
                                            detectDragGestures(
                                                onDragEnd = {
                                                    val deltaMin = ((dragDeltaY / hourHeightDp.toPx()) * 60f / 15f).roundToInt() * 15
                                                    dragDeltaY = 0f
                                                    if (deltaMin != 0) {
                                                        val newStart = (block.startMin + deltaMin).coerceIn(0, 23 * 60)
                                                        val newEnd = (newStart + durMin).coerceAtMost(23 * 60 + 55)
                                                        onEventMoveOrResize(
                                                            ev.copy(
                                                                startTimeLocal = CalendarDateMath.minutesToTime(newStart),
                                                                endTimeLocal = CalendarDateMath.minutesToTime(newEnd),
                                                            )
                                                        )
                                                    }
                                                },
                                                onDragCancel = { dragDeltaY = 0f },
                                            ) { change, dragAmount ->
                                                change.consume()
                                                dragDeltaY += dragAmount.y
                                            }
                                        }
                                        .clickable { onEventClick(ev) }
                                        .semantics { contentDescription = "Event ${ev.title} ${ev.startTimeLocal} to ${ev.endTimeLocal}" },
                                ) {
                                    Box(Modifier.fillMaxSize()) {
                                        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                            Text(ev.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = layerColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${ev.startTimeLocal}–${ev.endTimeLocal}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                        // Bottom Drag-to-Resize Handle
                                        Box(
                                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(8.dp)
                                                .background(layerColor.copy(alpha = 0.25f))
                                                .pointerInput(ev.id, ev.rev, "resize") {
                                                    detectDragGestures(
                                                        onDragEnd = {
                                                            val deltaMin = ((resizeDeltaY / hourHeightDp.toPx()) * 60f / 15f).roundToInt() * 15
                                                            resizeDeltaY = 0f
                                                            if (deltaMin != 0) {
                                                                val newEnd = (block.endMin + deltaMin).coerceIn(block.startMin + 15, 23 * 60 + 55)
                                                                onEventMoveOrResize(ev.copy(endTimeLocal = CalendarDateMath.minutesToTime(newEnd)))
                                                            }
                                                        },
                                                        onDragCancel = { resizeDeltaY = 0f },
                                                    ) { change, dragAmount ->
                                                        change.consume()
                                                        resizeDeltaY += dragAmount.y
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGridCalendarView(
    anchorDateIso: String,
    events: List<CalendarEvent>,
    tasks: List<TaskItem>,
    onSelectDay: (String) -> Unit,
) {
    val ym = anchorDateIso.take(7)
    val days = remember(ym) { (1..30).map { "$ym-%02d".format(it) } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
        items(days.chunked(5)) { weekRow ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                weekRow.forEach { d ->
                    val evCount = events.count { it.startDateIso == d }
                    val tCount = tasks.count { it.doDateIso == d || it.dueDateIso == d }
                    OutlinedCard(
                        onClick = { onSelectDay(d) },
                        modifier = Modifier.weight(1f).heightIn(min = 82.dp).semantics { contentDescription = "Open calendar day $d" },
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(d.takeLast(2), fontWeight = FontWeight.Bold, color = if (d == SystemDomainClock.todayIso()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            if (evCount > 0) StudioStatusPill("$evCount event${if (evCount > 1) "s" else ""}")
                            if (tCount > 0) Text("$tCount task${if (tCount > 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearOverviewCalendarView(
    year: Int,
    events: List<CalendarEvent>,
    onSelectMonthStart: (String) -> Unit,
) {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items((0 until 12).chunked(3)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { idx ->
                    val prefix = "$year-%02d".format(idx + 1)
                    val count = events.count { it.startDateIso.startsWith(prefix) }
                    OutlinedCard(
                        onClick = { onSelectMonthStart("$prefix-01") },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Open ${months[idx]} $year" },
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${months[idx]} $year", fontWeight = FontWeight.Bold)
                            Text("$count scheduled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaCalendarView(
    events: List<CalendarEvent>,
    tasks: List<TaskItem>,
    onEventClick: (CalendarEvent) -> Unit,
    onScheduleTask: (TaskItem) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        if (events.isEmpty()) {
            item { StudioEmptyState("No Upcoming Events", "Create an event or schedule a task from the controls above.") }
        }
        items(events, key = { it.id }) { ev ->
            OutlinedCard(onClick = { onEventClick(ev) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ev.title, fontWeight = FontWeight.SemiBold)
                        Text("${ev.startDateIso} · ${if (ev.allDay) "All day" else "${ev.startTimeLocal}–${ev.endTimeLocal}"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StudioStatusPill(CalendarLayerPalette[ev.calendarId]?.first ?: "Calendar")
                }
            }
        }
        if (tasks.isNotEmpty()) {
            item { Text("Active Tasks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
            items(tasks.take(10), key = { it.id }) { t ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontWeight = FontWeight.Medium)
                            Text("Due: ${t.dueDateIso ?: t.doDateIso ?: "Unscheduled"} · ${t.estimateMin ?: 30}m", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onScheduleTask(t) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Schedule") }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. Customizable Today Spread & Home Dashboard (§9 — 100% live reactive Flows)
// ============================================================================

private val AllDashboardWidgetDefs: List<Pair<String, String>> = listOf(
    "quick_capture" to "Quick Capture",
    "schedule" to "Today's Schedule",
    "tasks" to "Priority Tasks",
    "habits" to "Habits",
    "chores" to "Chores & Rooms",
    "notes" to "Recent Notes",
    "goals" to "Active Goals",
    "finance" to "Budget & Expenses",
    "lab" to "Lab Samples",
    "kitchen" to "Meals & Groceries",
)

@Composable
fun TodayAndHomeDashboardStudio(
    state: PlanadayShellState,
    controller: PlanadayWorkspaceController,
    isCompact: Boolean,
) {
    val todayIso = SystemDomainClock.todayIso()
    val enabledWidgets = state.preferences.enabledDashboardWidgets
    var configuringWidgets by remember { mutableStateOf(false) }
    var captureMode by remember { mutableStateOf("Task") }
    var captureText by remember { mutableStateOf("") }

    // Subscribe ONLY to bounded slices needed by enabled widgets! (§6)
    val taskRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.TASK, limit = 30) }.collectAsState(emptyList())
    val eventRecs by remember(state.activeWorkspaceId, todayIso) {
        controller.observeScheduleWindow(RecordKind.EVENT, todayIso, CalendarDateMath.addDays(todayIso, 7), limit = 30)
    }.collectAsState(emptyList())
    val choreRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.CHORE, limit = 20) }.collectAsState(emptyList())
    val habitRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.HABIT, limit = 20) }.collectAsState(emptyList())
    val noteRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.NOTE, limit = 10) }.collectAsState(emptyList())
    val goalRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.GOAL, limit = 10) }.collectAsState(emptyList())
    val expenseRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.EXPENSE, limit = 30) }.collectAsState(emptyList())
    val labRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.LAB_SAMPLE, limit = 15) }.collectAsState(emptyList())
    val foodRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.FOOD_LOG, limit = 10) }.collectAsState(emptyList())

    val tasks = remember(taskRecs) { taskRecs.mapNotNull { runCatching { controller.json.decodeFromString<TaskItem>(it.payloadJson) }.getOrNull() } }
    val events = remember(eventRecs) { eventRecs.mapNotNull { runCatching { controller.json.decodeFromString<CalendarEvent>(it.payloadJson) }.getOrNull() } }
    val chores = remember(choreRecs) { choreRecs.mapNotNull { runCatching { controller.json.decodeFromString<ChoreItem>(it.payloadJson) }.getOrNull() } }
    val expenses = remember(expenseRecs) { expenseRecs.mapNotNull { runCatching { controller.json.decodeFromString<ExpenseItem>(it.payloadJson) }.getOrNull() } }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        // Dashboard Profile & Customize Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("Personal", "Laboratory", "Creative", "Executive").forEach { prof ->
                    FilterChip(
                        selected = state.preferences.dashboardProfile == prof,
                        onClick = {
                            val preset = when (prof) {
                                "Laboratory" -> listOf("quick_capture", "lab", "schedule", "tasks", "notes")
                                "Creative" -> listOf("quick_capture", "notes", "goals", "tasks", "schedule")
                                "Executive" -> listOf("schedule", "tasks", "goals", "finance", "notes")
                                else -> AllDashboardWidgetDefs.map { it.first }
                            }
                            controller.updatePreferences { it.copy(dashboardProfile = prof, enabledDashboardWidgets = preset) }
                        },
                        label = { Text(prof) },
                    )
                }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(
                    onClick = { configuringWidgets = !configuringWidgets },
                    modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = "Customize dashboard widgets" },
                ) { Text(if (configuringWidgets) "Done Customizing" else "Customize Widgets") }
            }
        }

        if (configuringWidgets) {
            item {
                StudioCard(title = "Visible Dashboard Widgets", subtitle = "Toggle or reorder widgets. Saved automatically across restarts.") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AllDashboardWidgetDefs.forEach { (id, label) ->
                            val on = id in enabledWidgets
                            FilterChip(
                                selected = on,
                                onClick = {
                                    controller.updatePreferences { p ->
                                        val next = if (on) p.enabledDashboardWidgets - id else p.enabledDashboardWidgets + id
                                        p.copy(enabledDashboardWidgets = next)
                                    }
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }

        // Metric Summary Strip
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioMetricTile("Open Tasks", tasks.count { it.status == RecordStatus.ACTIVE }.toString(), modifier = Modifier.weight(1f))
                StudioMetricTile("Upcoming", events.size.toString(), accent = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                StudioMetricTile("Notes", noteRecs.size.toString(), accent = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                if (!isCompact) {
                    val totalSpend = ExactDecimal.sumAll(expenses.map { it.amount }, "USD")
                    StudioMetricTile("Expenses", "$${totalSpend.value}", modifier = Modifier.weight(1f))
                }
            }
        }

        if (state.totalActiveRecords == 0L) {
            item {
                StudioEmptyState(
                    title = "Welcome to Planaday",
                    message = "Your new workspace is clean and ready offline. Capture your first task, schedule an event, or write a note below — or optionally explore with sample items.",
                    actionLabel = "Try Sample Workspace",
                    onAction = { controller.optInSeedSyntheticDemo() },
                )
            }
        }

        if ("quick_capture" in enabledWidgets) {
            item {
                StudioCard(title = "Quick Capture", subtitle = "Add directly into your active workspace") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Task", "Event", "Note", "Meal").forEach { m ->
                            FilterChip(selected = captureMode == m, onClick = { captureMode = m }, label = { Text(m) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = captureText,
                            onValueChange = { captureText = it },
                            label = { Text("New $captureMode…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                if (captureText.isNotBlank()) {
                                    when (captureMode) {
                                        "Task" -> controller.quickCreateTask(captureText, Priority.HIGH, 30)
                                        "Event" -> controller.createCalendarEvent(captureText, "10:00", "11:00", todayIso)
                                        "Note" -> controller.saveNote(captureText, "")
                                        "Meal" -> controller.logMeal(captureText, "lunch")
                                    }
                                    captureText = ""
                                }
                            },
                            modifier = Modifier.heightIn(min = 52.dp).semantics { contentDescription = "Save quick capture $captureMode" },
                        ) { Text("Save $captureMode") }
                    }
                }
            }
        }

        if ("schedule" in enabledWidgets) {
            item {
                StudioCard(
                    title = "Schedule ($todayIso)",
                    headerAction = {
                        TextButton(onClick = { controller.selectRoute(PlanadayRoute.FLOW_CALENDAR) }) { Text("Open Calendar →") }
                    },
                ) {
                    if (events.isEmpty()) {
                        Text("No events scheduled for the next 7 days.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        events.take(5).forEach { ev ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(ev.title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text("${ev.startDateIso} ${ev.startTimeLocal ?: "All day"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if ("tasks" in enabledWidgets) {
            item {
                val open = tasks.filter { it.status == RecordStatus.ACTIVE }
                StudioCard(
                    title = "Priority Tasks (${open.size})",
                    headerAction = { TextButton(onClick = { controller.selectRoute(PlanadayRoute.TASKS) }) { Text("All Tasks →") } },
                ) {
                    if (open.isEmpty()) {
                        Text("All caught up — no active tasks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        open.sortedByDescending { it.priority.rank }.take(6).forEach { t ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, fontWeight = FontWeight.Medium)
                                    Text("${t.priority.name} · ${t.dueDateIso ?: t.doDateIso ?: "Anytime"} · ${t.estimateMin ?: 30}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(
                                    onClick = { controller.moveTaskColumn(t.id, "done") },
                                    modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = "Complete task ${t.title}" },
                                ) { Text("Done") }
                            }
                        }
                    }
                }
            }
        }

        if ("habits" in enabledWidgets && habitRecs.isNotEmpty()) {
            item {
                StudioCard(title = "Habits (${habitRecs.size})", headerAction = { TextButton(onClick = { controller.selectRoute(PlanadayRoute.HABITS) }) { Text("Manage →") } }) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        habitRecs.take(8).forEach { h ->
                            AssistChip(onClick = { controller.selectRoute(PlanadayRoute.HABITS) }, label = { Text(h.title) })
                        }
                    }
                }
            }
        }

        if ("chores" in enabledWidgets && chores.isNotEmpty()) {
            item {
                StudioCard(title = "Chores Due", headerAction = { TextButton(onClick = { controller.selectRoute(PlanadayRoute.CHORES) }) { Text("Rooms →") } }) {
                    chores.take(4).forEach { ch ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${ch.title} (${ch.room}) · due ${ch.nextDueDateIso}", modifier = Modifier.weight(1f))
                            TextButton(onClick = { controller.completeChoreToday(ch.id) }) { Text("Done") }
                        }
                    }
                }
            }
        }

        if ("notes" in enabledWidgets && noteRecs.isNotEmpty()) {
            item {
                StudioCard(title = "Recent Notes", headerAction = { TextButton(onClick = { controller.selectRoute(PlanadayRoute.NOTES) }) { Text("Open Notes →") } }) {
                    noteRecs.take(4).forEach { n ->
                        Text("• ${n.title}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if ("goals" in enabledWidgets && goalRecs.isNotEmpty()) {
            item {
                StudioCard(title = "Active Goals", headerAction = { TextButton(onClick = { controller.selectRoute(PlanadayRoute.GOALS) }) { Text("Goals →") } }) {
                    goalRecs.take(4).forEach { g -> Text("• ${g.title}", fontWeight = FontWeight.Medium) }
                }
            }
        }

        if ("lab" in enabledWidgets && labRecs.isNotEmpty()) {
            item {
                StudioCard(title = "Laboratory Samples (${labRecs.size})", headerAction = { TextButton(onClick = { controller.selectRoute(PlanadayRoute.LAB_WORKSPACE) }) { Text("Open Lab →") } }) {
                    labRecs.take(4).forEach { s -> Text("• ${s.title} — ${s.summaryText}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        if ("kitchen" in enabledWidgets && foodRecs.isNotEmpty()) {
            item {
                StudioCard(title = "Recent Food History", headerAction = { TextButton(onClick = { controller.selectRoute(PlanadayRoute.MEAL_PLANNER) }) { Text("Meals →") } }) {
                    Text(foodRecs.take(5).joinToString(" · ") { it.title }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
