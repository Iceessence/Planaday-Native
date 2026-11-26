package com.planaday.nativeapp.core.designtokens.studios

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.automation.AutomationDomainEvent
import com.planaday.nativeapp.core.automation.AutomationEngine
import com.planaday.nativeapp.core.automation.AutomationRuleDef
import com.planaday.nativeapp.core.designtokens.*
import com.planaday.nativeapp.core.domain.*
import com.planaday.nativeapp.core.model.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.util.UUID

// ============================================================================
// 1. Real User-Configurable Automation Builder (Update 2 §25)
// ============================================================================

@Composable
fun RealAutomationStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    val ruleRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.AUTOMATION_RULE, 80) }.collectAsState(emptyList())
    val runHistory by remember(state.activeWorkspaceId) { controller.observeAutomationRuns(50) }.collectAsState(emptyList())
    val rules = remember(ruleRecs) { ruleRecs.mapNotNull { runCatching { controller.json.decodeFromString<AutomationRuleDef>(it.payloadJson) }.getOrNull() } }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("TASK_COMPLETED") }
    var action by remember { mutableStateOf("CREATE_TASK") }
    var actionPayload by remember { mutableStateOf("") }
    var requireApproval by remember { mutableStateOf(true) }
    var previewOutput by remember { mutableStateOf<String?>(null) }

    val triggers = listOf("TASK_COMPLETED", "CHORE_COMPLETED", "NOTE_CREATED", "EXPENSE_LOGGED", "LAB_SAMPLE_UPDATED")
    val actions = listOf("CREATE_TASK", "LOG_JOURNAL", "NOTIFY")

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            StudioCard(title = "Create Automation Rule", subtitle = "No-code trigger → action rules with dry-run preview, loop guard, and approval protection") {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Rule name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("When this happens:", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    triggers.forEach { tr -> FilterChip(selected = trigger == tr, onClick = { trigger = tr }, label = { Text(tr.lowercase().replace('_', ' ')) }) }
                }
                Text("Perform this action:", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    actions.forEach { ac -> FilterChip(selected = action == ac, onClick = { action = ac }, label = { Text(ac.lowercase().replace('_', ' ')) }) }
                }
                OutlinedTextField(value = actionPayload, onValueChange = { actionPayload = it }, label = { Text("Action text (e.g. Follow-up task title or journal note)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = requireApproval, onCheckedChange = { requireApproval = it }, modifier = Modifier.semantics { contentDescription = "Require human approval before executing" })
                    Text("Require confirmation before running")
                }
                Button(onClick = {
                    if (name.isNotBlank() && actionPayload.isNotBlank()) {
                        val r = AutomationRuleDef(UUID.randomUUID().toString(), state.activeWorkspaceId, name.trim(), trigger, null, action, actionPayload.trim(), requiresHumanApproval = requireApproval, enabled = true)
                        controller.createOrUpdateRecord(RecordKind.AUTOMATION_RULE, r.name, controller.json.encodeToString(r), "${r.triggerEventKind} → ${r.actionKind}")
                        name = ""; actionPayload = ""
                    }
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save Automation Rule") }
            }
        }

        if (rules.isEmpty()) {
            item { StudioEmptyState("No Automation Rules Yet", "Create a workflow rule above to automate repetitive follow-ups safely.") }
        }
        items(rules, key = { it.id }) { rule ->
            StudioCard(
                title = rule.name,
                subtitle = "When ${rule.triggerEventKind.lowercase().replace('_', ' ')} → ${rule.actionKind.lowercase().replace('_', ' ')}: \"${rule.actionTargetLabel}\" · ${if (rule.enabled) "Active" else "Paused"}",
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val dry = AutomationEngine().preview(listOf(rule), AutomationDomainEvent("preview_${UUID.randomUUID().toString().take(6)}", state.activeWorkspaceId, rule.triggerEventKind, "live_check", RecordKind.TASK))
                        previewOutput = "Dry-Run Preview (${rule.name}): ${dry.joinToString { "${it.description} (approvalRequired=${it.requiresApproval})" }.ifEmpty { "Rule paused or condition unmatched" }}"
                    }) { Text("Dry-Run Preview") }
                    Button(onClick = {
                        scope.launch {
                            val evt = AutomationDomainEvent("manual_${UUID.randomUUID().toString().take(8)}", state.activeWorkspaceId, rule.triggerEventKind, rule.id, RecordKind.AUTOMATION_RULE)
                            controller.useCases.evaluateAndRunAutomations(listOf(rule.copy(requiresHumanApproval = false)), evt) { res ->
                                if (rule.actionKind == "CREATE_TASK") controller.quickCreateTask(rule.actionTargetLabel, Priority.MEDIUM, 20)
                                previewOutput = "Executed '${rule.name}': ${res.description}"
                            }
                        }
                    }) { Text("Run Now") }
                    OutlinedButton(onClick = {
                        val toggled = rule.copy(enabled = !rule.enabled)
                        controller.createOrUpdateRecord(RecordKind.AUTOMATION_RULE, toggled.name, controller.json.encodeToString(toggled), if (toggled.enabled) "Active" else "Paused", existingId = toggled.id)
                    }) { Text(if (rule.enabled) "Pause" else "Enable") }
                    TextButton(onClick = { controller.moveRecordToTrash(rule.id) }) { Text("Delete") }
                }
            }
        }
        previewOutput?.let { out ->
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(out, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                }
            }
        }
        if (runHistory.isNotEmpty()) {
            item { Text("Execution History (${runHistory.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            items(runHistory, key = { it.idempotencyKey }) { run ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Rule ${run.ruleId.take(8)} · ${run.status}", style = MaterialTheme.typography.bodySmall)
                        Text(run.executedAtUtc.take(19), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. Real Spaced Review Study Deck (SM-2 Persisted Flashcards)
// ============================================================================

@Composable
fun SpacedReviewStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    val cardRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.REVIEW_CARD, 200) }.collectAsState(emptyList())
    val cards = remember(cardRecs) { cardRecs.mapNotNull { runCatching { controller.json.decodeFromString<SpacedReviewCardState>(it.payloadJson) }.getOrNull() } }
    val today = SystemDomainClock.todayIso()
    val dueCards = remember(cards, today) { cards.filter { it.nextDueDateIso <= today } }

    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var revealAnswer by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            StudioCard(title = "Add Study Card (${dueCards.size} due today / ${cards.size} total)") {
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Question / Front") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("Answer / Back") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    if (prompt.isNotBlank() && answer.isNotBlank()) {
                        val c = SpacedReviewCardState(UUID.randomUUID().toString(), prompt.trim(), answer.trim(), nextDueDateIso = today)
                        controller.createOrUpdateRecord(RecordKind.REVIEW_CARD, c.prompt.take(60), controller.json.encodeToString(c), "Due $today")
                        prompt = ""; answer = ""
                    }
                }) { Text("Save Flashcard") }
            }
        }
        val activeCard = dueCards.firstOrNull()
        if (activeCard != null) {
            item {
                StudioCard(title = "Review Queue (${dueCards.size} remaining)") {
                    Text(activeCard.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (revealAnswer) {
                        HorizontalDivider()
                        Text(activeCard.answer, style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Again (1)" to 1, "Hard (3)" to 3, "Good (4)" to 4, "Easy (5)" to 5).forEach { (lbl, q) ->
                                Button(onClick = {
                                    val next = LifeWellnessAndLearningEngines.reviewCardSm2(activeCard, q, today)
                                    controller.createOrUpdateRecord(RecordKind.REVIEW_CARD, next.prompt.take(60), controller.json.encodeToString(next), "Next due: ${next.nextDueDateIso}", existingId = next.cardId)
                                    revealAnswer = false
                                }) { Text(lbl) }
                            }
                        }
                    } else {
                        Button(onClick = { revealAnswer = true }) { Text("Reveal Answer") }
                    }
                }
            }
        } else {
            item { StudioEmptyState("All Reviews Complete", "Create more flashcards above or return tomorrow when scheduled cards become due.") }
        }
    }
}

// ============================================================================
// 3. Reports, Decision Journal, Insights, Custom Spaces, Rituals & Help Guide
// ============================================================================

@Composable
fun ReportsDecisionsAndGuideStudio(route: PlanadayRoute, state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    when (route) {
        PlanadayRoute.GUIDE -> {
            var q by remember { mutableStateOf("") }
            val filtered = PlanadayRoute.entries.filter {
                q.isBlank() || it.title.contains(q, true) || it.description.contains(q, true)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    StudioCard(title = "Keyboard Shortcuts (Windows Desktop)") {
                        Text("Ctrl+K: Command Palette & Search · Ctrl+Shift+W: New Synchronized Window · Ctrl+W: Close Active Tab · F11: Distraction-Free Zen Mode", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Search all 54 Planaday modules…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                items(filtered, key = { it.id }) { r ->
                    OutlinedCard(onClick = { controller.selectRoute(r) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, fontWeight = FontWeight.SemiBold)
                                Text(r.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Open →", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        PlanadayRoute.REPORTS -> {
            val decRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.DECISION_JOURNAL, 80) }.collectAsState(emptyList())
            val taskRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.TASK, 200) }.collectAsState(emptyList())
            val noteRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.NOTE, 200) }.collectAsState(emptyList())
            val decisions = remember(decRecs) { decRecs.mapNotNull { runCatching { controller.json.decodeFromString<DecisionJournalEntry>(it.payloadJson) }.getOrNull() } }
            val pivots = remember(taskRecs, noteRecs, decRecs) {
                UniversalPivotReportEngine.pivotByKindAndStatus(taskRecs + noteRecs + decRecs, state.activeWorkspaceId)
            }
            var title by remember { mutableStateOf("") }
            var chosen by remember { mutableStateOf("") }
            var expected by remember { mutableStateOf("") }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    StudioCard(title = "Workspace Pivot Breakdown (${pivots.sumOf { it.count }} sampled records)") {
                        if (pivots.isEmpty()) Text("Create records across modules to inspect live status breakdowns.", style = MaterialTheme.typography.bodySmall)
                        else pivots.forEach { b -> Text("• ${b.dimensionValue}: ${b.count} record(s)", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                item {
                    StudioCard(title = "Decision Journal") {
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Decision title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = chosen, onValueChange = { chosen = it }, label = { Text("Chosen option") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = expected, onValueChange = { expected = it }, label = { Text("Expected outcome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            if (title.isNotBlank()) {
                                val d = DecisionJournalEntry(UUID.randomUUID().toString(), state.activeWorkspaceId, title.trim(), "", listOf(chosen), chosen, expected, createdAtUtc = SystemDomainClock.nowUtcIso())
                                controller.createOrUpdateRecord(RecordKind.DECISION_JOURNAL, d.title, controller.json.encodeToString(d), "Chosen: $chosen")
                                title = ""; chosen = ""; expected = ""
                            }
                        }) { Text("Log Decision") }
                    }
                }
                items(decisions, key = { it.id }) { d ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(d.title, fontWeight = FontWeight.SemiBold)
                            Text("Chosen: ${d.chosenOption} · Expected: ${d.expectedOutcome}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        else -> {
            // Insights, History, Custom Spaces, Rituals, Thought Inbox, Life Events
            val kind = PlanadayWorkspaceController.routeToPrimaryKind(route)
            val recs by remember(state.activeWorkspaceId, kind) { controller.observeKind(kind, 120) }.collectAsState(emptyList())
            val taskRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.TASK, 100) }.collectAsState(emptyList())
            val tasks = remember(taskRecs) { taskRecs.mapNotNull { runCatching { controller.json.decodeFromString<TaskItem>(it.payloadJson) }.getOrNull() } }
            val cal = remember(tasks) { WhatIfScheduleAndCalibrationEngine.computeFocusCalibration(tasks) }
            var title by remember { mutableStateOf("") }
            var details by remember { mutableStateOf("") }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                if (route == PlanadayRoute.INSIGHTS || route == PlanadayRoute.HISTORY_REVIEWS) {
                    item {
                        StudioCard(title = "Focus Estimate Calibration") {
                            Text("Logged focus sessions: ${cal.totalSessions} (undistracted: ${cal.undistractedSessions}, distracted: ${cal.distractedSessions})")
                            Text("Average estimated: ${cal.avgEstimatedMin?.let { "%.1fm".format(it) } ?: "—"} · Average actual: ${cal.avgActualMin?.let { "%.1fm".format(it) } ?: "—"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    StudioCard(title = "Add ${route.title} Entry") {
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("Details / Context") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            if (title.isNotBlank()) {
                                controller.createOrUpdateRecord(kind, title.trim(), controller.json.encodeToString(mapOf("details" to details)), details)
                                title = ""; details = ""
                            }
                        }) { Text("Save") }
                    }
                }
                items(recs, key = { it.id }) { rec ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(rec.title, fontWeight = FontWeight.Medium)
                                Text(rec.summaryText, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (route == PlanadayRoute.THOUGHT_INBOX) {
                                    TextButton(onClick = {
                                        controller.quickCreateTask(rec.title, Priority.MEDIUM, 30, notes = rec.summaryText)
                                        controller.moveRecordToTrash(rec.id)
                                    }) { Text("→ Task") }
                                }
                                TextButton(onClick = { controller.moveRecordToTrash(rec.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. Trash Recovery & Complete Settings, Backup/Restore, Persistent Sync & Diagnostics
//    (Update 2 §7, §26, §27, §28, §29)
// ============================================================================

@Composable
fun TrashRecoveryStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    val trash by remember(state.activeWorkspaceId) { controller.observeTrash(250) }.collectAsState(emptyList())
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        if (trash.isEmpty()) {
            item { StudioEmptyState("Trash Is Empty", "Deleted items remain recoverable here for 30 days before permanent removal.") }
        } else {
            items(trash, key = { it.id }) { rec ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rec.title, fontWeight = FontWeight.SemiBold)
                            Text("${rec.kind.name.lowercase().replace('_', ' ')} · Deleted ${rec.deletedAtUtc?.take(16) ?: ""}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { controller.restoreFromTrash(rec.id) }) { Text("Restore") }
                            TextButton(onClick = { controller.purgeFromTrash(rec.id) }) { Text("Purge", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsBackupSyncDiagnosticsStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    val prefs = state.preferences
    var newWsName by remember { mutableStateOf("") }
    var newWsScope by remember { mutableStateOf(WorkspaceScope.OFFICE) }
    var backupPassphrase by remember { mutableStateOf("") }
    var restorePreviewText by remember { mutableStateOf<String?>(null) }
    var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pairingCode by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        // 1. Appearance & Visual Customization (Persisted across restarts!)
        item {
            StudioCard(title = "Appearance & Customization", subtitle = "All appearance and workspace settings persist automatically across restarts") {
                Text("Theme", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlanadayThemeMode.entries.forEach { m ->
                        FilterChip(selected = prefs.themeMode == m, onClick = { controller.setThemeMode(m) }, label = { Text(m.displayName) })
                    }
                }
                Text("Accent Color", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlanadayAccentPresets.forEach { (label, hex) ->
                        val c = parseHexColorOrNull(hex) ?: MaterialTheme.colorScheme.primary
                        val sel = prefs.accentHex.equals(hex, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(if (sel) 2.5.dp else 1.dp, if (sel) MaterialTheme.colorScheme.onSurface else Color.Transparent),
                            color = c.copy(alpha = 0.14f),
                            modifier = Modifier.clickable { controller.setAccentHex(hex) }.semantics { contentDescription = "Select accent color $label" },
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(14.dp).clip(CircleShape).background(c))
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Text("Interface Density", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlanadayDensity.entries.forEach { d ->
                        FilterChip(selected = prefs.density == d, onClick = { controller.setDensity(d) }, label = { Text(d.displayName) })
                    }
                }
                Text("Text Size: ${(prefs.fontScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = prefs.fontScale, onValueChange = { controller.setFontScale(it) }, valueRange = 0.85f..1.30f, steps = 8)
            }
        }

        // 2. Workspace Management
        item {
            StudioCard(title = "Workspaces (${state.workspaces.size})", subtitle = "Separate personal, laboratory, business, or creative records cleanly") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WorkspaceScope.entries.forEach { s ->
                        FilterChip(selected = newWsScope == s, onClick = { newWsScope = s }, label = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newWsName, onValueChange = { newWsName = it }, label = { Text("New workspace name") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        if (newWsName.isNotBlank()) {
                            controller.createWorkspace(newWsName, newWsScope)
                            newWsName = ""
                        }
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Create Workspace") }
                }
            }
        }

        // 3. Complete Backup & Restoration Workflow (§27)
        item {
            StudioCard(title = "Backup & Restore", subtitle = "Verified portable archives with optional AES-256-GCM encryption") {
                OutlinedTextField(
                    value = backupPassphrase,
                    onValueChange = { backupPassphrase = it },
                    label = { Text("Optional encryption passphrase (leave blank for standard ZIP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { controller.exportBackupToDisk(backupPassphrase) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Create & Save Backup") }
                    OutlinedButton(onClick = {
                        val picked = controller.fileDialogBridge?.pickFileToRead("Open Planaday Backup", listOf("zip", "pnbak"))
                        if (picked != null) {
                            pendingRestoreBytes = picked.second
                            controller.previewAndRestoreBackupFromBytes(picked.second, backupPassphrase, commitRestore = false) { summary ->
                                restorePreviewText = summary
                            }
                        } else {
                            controller.setBanner("Select a Planaday backup file to preview and verify before restoring.")
                        }
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Choose Backup to Preview") }
                    if (pendingRestoreBytes != null) {
                        Button(onClick = {
                            controller.previewAndRestoreBackupFromBytes(pendingRestoreBytes!!, backupPassphrase, commitRestore = true)
                            pendingRestoreBytes = null
                            restorePreviewText = null
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Confirm Restore") }
                    }
                }
                restorePreviewText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                state.lastBackupSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        // 4. Optional Self-Hosted Sync + Encrypted Credential Vault + Conflict Resolution (§28)
        item {
            StudioCard(
                title = "Optional Self-Hosted Sync",
                subtitle = "Planaday works 100% offline. Connect your own server only if you want multi-device sync.",
            ) {
                OutlinedTextField(
                    value = prefs.syncServerUrl,
                    onValueChange = { u -> controller.updatePreferences { it.copy(syncServerUrl = u) } },
                    label = { Text("Sync Server URL (HTTPS required for remote servers)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = prefs.allowDevelopmentHttp,
                        onCheckedChange = { v -> controller.updatePreferences { it.copy(allowDevelopmentHttp = v) } },
                        modifier = Modifier.semantics { contentDescription = "Allow local network development HTTP" },
                    )
                    Text("Allow development HTTP on trusted local network")
                }
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it },
                    label = { Text("10-minute Pairing Code (stored encrypted on this device)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { controller.pairAndSyncWithServer(pairingCode, state.buildIdentity.platform); pairingCode = "" },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(if (state.syncBearerTokenPresent) "Sync Now" else "Pair & Sync") }
                    if (state.syncBearerTokenPresent) {
                        OutlinedButton(onClick = { controller.forgetSyncCredential() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Disconnect Device") }
                    }
                }
                Text(
                    "Status: ${state.syncSnapshot.status.name.lowercase().replace('_', ' ')} · " +
                        "Pending changes: ${state.syncSnapshot.pendingOpsCount} · " +
                        "Credential vault: ${controller.credentialVault?.backendDescription ?: "In-memory test mode"} (${if (state.syncBearerTokenPresent) "Paired" else "Not paired"})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Conflict Resolution Inspector (Visible whenever conflicts exist — zero silent overwrite!)
        if (state.unresolvedConflicts.isNotEmpty()) {
            item {
                StudioCard(title = "Sync Conflicts Requiring Review (${state.unresolvedConflicts.size})", subtitle = "Both versions are preserved safely until you choose") {
                    state.unresolvedConflicts.forEach { c ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Record ${c.recordId.take(10)} (${c.recordKind}) — ${c.reason}", fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = { controller.resolveConflict(c.id, "KEEP_BOTH") }) { Text("Keep Both") }
                                    OutlinedButton(onClick = { controller.resolveConflict(c.id, "KEEP_LOCAL") }) { Text("Keep Mine") }
                                    OutlinedButton(onClick = { controller.resolveConflict(c.id, "ACCEPT_REMOTE") }) { Text("Use Server") }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Developer & Build Diagnostics (Kept cleanly out of customer main views — Update 2 §7 & §36)
        item {
            StudioCard(
                title = "Developer & Storage Diagnostics",
                headerAction = {
                    Switch(
                        checked = prefs.showDeveloperDiagnostics,
                        onCheckedChange = { v -> controller.updatePreferences { it.copy(showDeveloperDiagnostics = v) } },
                    )
                },
            ) {
                if (prefs.showDeveloperDiagnostics) {
                    val b = state.buildIdentity
                    Text("Version: ${b.productName} ${b.appVersion} (Commit: ${b.gitCommitSha})", style = MaterialTheme.typography.bodySmall)
                    Text("Built: ${b.buildTimestampUtc} · Protocol v${b.protocolVersion} · Schema v${b.schemaVersion}", style = MaterialTheme.typography.bodySmall)
                    Text("Platform: ${b.platform} · Device ID: ${state.deviceId}", style = MaterialTheme.typography.bodySmall)
                    Text("Query Telemetry Snapshot: ${controller.repository.telemetry.snapshot()}", style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("Toggle on to inspect Git commit SHA, database schema version, device ID, and reactive query telemetry.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
