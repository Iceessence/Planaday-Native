package com.planaday.nativeapp.core.designtokens.studios

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.designtokens.*
import com.planaday.nativeapp.core.domain.*
import com.planaday.nativeapp.core.graphics.PhotoAndPlannerAdvanced
import com.planaday.nativeapp.core.interchange.DocumentAnnotation
import com.planaday.nativeapp.core.interchange.ReviewedDocumentModel
import com.planaday.nativeapp.core.interchange.Rfc4180CsvCodec
import com.planaday.nativeapp.core.math.LabAnalyticsEngine
import com.planaday.nativeapp.core.model.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.util.UUID

// ============================================================================
// 1. Professional Laboratory Management Suite (All 15 Submodules — Update 2 §19)
// ============================================================================

@Composable
fun ProfessionalLaboratoryStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    val labTabs = listOf(
        "Overview", "Samples & Custody", "Subcontracts", "Inventory & Reagents", "Purchasing & Budgets",
        "Client Pricing", "Method Research", "Email Drafts", "Lab Tasks", "Lab Meetings",
        "Calibration & QC", "Wet Chem vs NIR", "Work Hours", "QMS / SOP / CAPA", "Uncertainty Workbook",
    )
    var tab by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    val sampleRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.LAB_SAMPLE, 150) }.collectAsState(emptyList())
    val invRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.LAB_INVENTORY, 150) }.collectAsState(emptyList())
    val calRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.LAB_CALIBRATION, 100) }.collectAsState(emptyList())
    val wetNirRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.LAB_WET_NIR, 200) }.collectAsState(emptyList())
    val meetingRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.LAB_MEETING, 80) }.collectAsState(emptyList())

    val samples = remember(sampleRecs) { sampleRecs.mapNotNull { runCatching { controller.json.decodeFromString<LabSample>(it.payloadJson) }.getOrNull() } }
    val inventory = remember(invRecs) { invRecs.mapNotNull { runCatching { controller.json.decodeFromString<LabInventoryItem>(it.payloadJson) }.getOrNull() } }
    val calibrations = remember(calRecs) { calRecs.mapNotNull { runCatching { controller.json.decodeFromString<LabCalibrationRecord>(it.payloadJson) }.getOrNull() } }
    val wetNirPairs = remember(wetNirRecs) { wetNirRecs.mapNotNull { runCatching { controller.json.decodeFromString<LabWetNirPair>(it.payloadJson) }.getOrNull() } }
    val meetings = remember(meetingRecs) { meetingRecs.mapNotNull { runCatching { controller.json.decodeFromString<LabMeeting>(it.payloadJson) }.getOrNull() } }
    val reorders = remember(inventory) { LaboratoryOperationsEngine.auditLabInventoryReorders(inventory, SystemDomainClock.todayIso()) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(LabAnalyticsEngine.NON_LIMS_DISCLAIMER, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
        }
        StudioTabRow(labTabs.mapIndexed { i, l -> "${i + 1}. $l" }, tab, { tab = it })

        when (tab) {
            0 -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudioMetricTile("Active Samples", samples.count { it.status != "archived" }.toString(), modifier = Modifier.weight(1f))
                            StudioMetricTile("Reorder Alerts", reorders.size.toString(), accent = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                            StudioMetricTile("QC Logs", calibrations.size.toString(), accent = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                            StudioMetricTile("Wet/NIR Pairs", wetNirPairs.size.toString(), modifier = Modifier.weight(1f))
                        }
                    }
                    item {
                        StudioCard(title = "Recent Samples (${samples.size})", headerAction = { TextButton(onClick = { tab = 1 }) { Text("Manage →") } }) {
                            if (samples.isEmpty()) Text("Register your first sample in '2. Samples & Custody'.", style = MaterialTheme.typography.bodySmall)
                            else samples.take(6).forEach { s -> Text("• ${s.sampleCode} (${s.clientName} · ${s.commodity}) — ${s.status.uppercase()} · Due ${s.dueDateIso}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            1 -> {
                // Real Sample Registration & Signed Chain-of-Custody Workflow!
                var code by remember { mutableStateOf("") }
                var client by remember { mutableStateOf("") }
                var commodity by remember { mutableStateOf("Grain") }
                var methods by remember { mutableStateOf("Moisture, Protein") }
                var due by remember { mutableStateOf(CalendarDateMath.addDays(SystemDomainClock.todayIso(), 3)) }
                var actor by remember { mutableStateOf("Lead Analyst") }
                var loc by remember { mutableStateOf("Prep Bench") }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Register Sample") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Sample ID") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = client, onValueChange = { client = it }, label = { Text("Client") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = commodity, onValueChange = { commodity = it }, label = { Text("Commodity / Matrix") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = methods, onValueChange = { methods = it }, label = { Text("Methods (comma sep)") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = due, onValueChange = { due = it }, label = { Text("Due YYYY-MM-DD") }, singleLine = true, modifier = Modifier.width(140.dp))
                            }
                            Button(onClick = {
                                if (code.isNotBlank() && client.isNotBlank()) {
                                    val today = SystemDomainClock.todayIso()
                                    val s = LabSample(
                                        UUID.randomUUID().toString(), state.activeWorkspaceId, code.trim(), client.trim(), commodity.trim(),
                                        methods.split(",").map { it.trim() }.filter { it.isNotEmpty() }, today, due, "received",
                                        custodyEvents = listOf(LabCustodyEvent("cust_0", SystemDomainClock.nowUtcIso(), actor, "RECEIVED", loc, "Registered")),
                                    )
                                    controller.createOrUpdateRecord(RecordKind.LAB_SAMPLE, "${s.sampleCode} · ${s.clientName}", controller.json.encodeToString(s), "${s.commodity} (${s.status})", scheduleStartIso = today, scheduleEndIso = due)
                                    code = ""
                                }
                            }) { Text("Register Sample") }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = actor, onValueChange = { actor = it }, label = { Text("Custody Sign-Off Actor") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = loc, onValueChange = { loc = it }, label = { Text("Location") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                    items(samples, key = { it.id }) { s ->
                        val nextStates = when (s.status) {
                            "received" -> listOf("prepared", "in_progress", "subcontracted")
                            "prepared" -> listOf("in_progress", "subcontracted")
                            "in_progress" -> listOf("qc_review", "completed")
                            "qc_review" -> listOf("completed", "in_progress")
                            "completed" -> listOf("archived")
                            else -> emptyList()
                        }
                        StudioCard(
                            title = "${s.sampleCode} — ${s.clientName} (${s.commodity})",
                            subtitle = "Status: ${s.status.uppercase()} · Methods: ${s.analysisMethods.joinToString()} · Due: ${s.dueDateIso} · Custody log: ${s.custodyEvents.size} event(s)",
                        ) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                nextStates.forEach { target ->
                                    OutlinedButton(onClick = {
                                        val transitioned = LaboratoryOperationsEngine.transitionSampleWithCustody(s, target, actor.ifBlank { "Analyst" }, loc.ifBlank { "Lab" }, SystemDomainClock.nowUtcIso())
                                        controller.createOrUpdateRecord(RecordKind.LAB_SAMPLE, "${transitioned.sampleCode} · ${transitioned.clientName}", controller.json.encodeToString(transitioned), "${transitioned.commodity} (${transitioned.status})", existingId = transitioned.id, recordRevision = true, checkpointLabel = "Custody -> $target")
                                    }) { Text("→ ${target.replace('_', ' ')}") }
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                // Real Lab Inventory & Reagents + Reorder Audit
                var name by remember { mutableStateOf("") }
                var cat by remember { mutableStateOf("reagent_lot") }
                var qty by remember { mutableStateOf("5") }
                var thresh by remember { mutableStateOf("2") }
                var unit by remember { mutableStateOf("L") }
                var exp by remember { mutableStateOf("") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Add Inventory Item / Reagent Lot (${reorders.size} reorder alert(s))") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item / Reagent") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, singleLine = true, modifier = Modifier.width(80.dp))
                                OutlinedTextField(value = thresh, onValueChange = { thresh = it }, label = { Text("Min") }, singleLine = true, modifier = Modifier.width(80.dp))
                                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.width(75.dp))
                            }
                            OutlinedTextField(value = exp, onValueChange = { exp = it }, label = { Text("Expiry Date YYYY-MM-DD (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                if (name.isNotBlank()) {
                                    val item = LabInventoryItem(UUID.randomUUID().toString(), state.activeWorkspaceId, cat, name.trim(), null, qty.toDoubleOrNull() ?: 1.0, thresh.toDoubleOrNull() ?: 1.0, unit, expiryDateIso = exp.trim().takeIf { it.length == 10 })
                                    controller.createOrUpdateRecord(RecordKind.LAB_INVENTORY, item.name, controller.json.encodeToString(item), "${item.quantityOnHand} ${item.unit}")
                                    name = ""
                                }
                            }) { Text("Save Inventory Item") }
                        }
                    }
                    items(inventory, key = { it.id }) { item ->
                        val low = item.id in reorders.map { it.id }
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${item.name} — ${item.quantityOnHand} ${item.unit} ${if (low) "⚠ Reorder/Expiring" else ""}", fontWeight = FontWeight.Medium)
                                    Text("Min threshold: ${item.reorderThreshold} · Expiry: ${item.expiryDateIso ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(onClick = {
                                    val upd = item.copy(quantityOnHand = (item.quantityOnHand - 1.0).coerceAtLeast(0.0))
                                    controller.createOrUpdateRecord(RecordKind.LAB_INVENTORY, upd.name, controller.json.encodeToString(upd), "${upd.quantityOnHand} ${upd.unit}", existingId = upd.id)
                                }) { Text("-1 Use") }
                            }
                        }
                    }
                }
            }
            9 -> {
                // Lab Meetings -> Promote Action Item to Canonical Lab Task!
                var title by remember { mutableStateOf("") }
                var actionItem by remember { mutableStateOf("") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Record Lab Meeting & Promote Action Items") {
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Meeting title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = actionItem, onValueChange = { actionItem = it }, label = { Text("Action item (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                if (title.isNotBlank()) {
                                    val m = LabMeeting(UUID.randomUUID().toString(), state.activeWorkspaceId, title.trim(), "weekly_analyst", SystemDomainClock.todayIso(), listOf("Lab Team"), "", listOfNotNull(actionItem.trim().takeIf { it.isNotEmpty() }))
                                    controller.createOrUpdateRecord(RecordKind.LAB_MEETING, m.title, controller.json.encodeToString(m), m.dateIso)
                                    title = ""; actionItem = ""
                                }
                            }) { Text("Save Meeting") }
                        }
                    }
                    items(meetings, key = { it.id }) { m ->
                        StudioCard(title = "${m.title} (${m.dateIso})") {
                            m.actionItems.forEach { act ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("• $act", modifier = Modifier.weight(1f))
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            controller.useCases.promoteLabMeetingActionToTask(state.activeWorkspaceId, m.id, act, CalendarDateMath.addDays(SystemDomainClock.todayIso(), 3))
                                            controller.setBanner("Promoted '$act' to Lab Task")
                                        }
                                    }) { Text("→ Create Task") }
                                }
                            }
                        }
                    }
                }
            }
            10 -> {
                // Immutable Calibration & QC Log + Append-Only Corrections!
                var inst by remember { mutableStateOf("NIR-01") }
                var rawVal by remember { mutableStateOf("100.002") }
                var refVal by remember { mutableStateOf("100.000") }
                var corrReason by remember { mutableStateOf("") }
                var corrVal by remember { mutableStateOf("") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Log Calibration / QC Check (Immutable Raw Values)") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = inst, onValueChange = { inst = it }, label = { Text("Instrument") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = rawVal, onValueChange = { rawVal = it }, label = { Text("Raw Value") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = refVal, onValueChange = { refVal = it }, label = { Text("Ref Std") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Button(onClick = {
                                val c = LabCalibrationRecord(UUID.randomUUID().toString(), state.activeWorkspaceId, inst, "calibration", SystemDomainClock.nowUtcIso(), CalendarDateMath.addDays(SystemDomainClock.todayIso(), 30), "Analyst", rawVal, refVal, "g", true)
                                controller.createOrUpdateRecord(RecordKind.LAB_CALIBRATION, "${c.instrumentId} QC (${c.rawMeasurementValue})", controller.json.encodeToString(c), "Ref ${c.referenceStandardValue}", recordRevision = true, scheduleStartIso = c.nextDueDateIso, scheduleEndIso = c.nextDueDateIso)
                            }) { Text("Record Immutable QC Entry") }
                        }
                    }
                    items(calibrations, key = { it.id }) { c ->
                        StudioCard(title = "${c.instrumentId} · Raw: ${c.rawMeasurementValue} ${c.unit} (Ref ${c.referenceStandardValue})", subtitle = "Next due: ${c.nextDueDateIso} · Append-only corrections: ${c.corrections.size}") {
                            c.corrections.forEach { ce -> Text("Correction (${ce.actor}): ${ce.correctedValue} ${ce.unit} — ${ce.reason}", style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = corrVal, onValueChange = { corrVal = it }, label = { Text("Corrected val") }, singleLine = true, modifier = Modifier.width(115.dp))
                                OutlinedTextField(value = corrReason, onValueChange = { corrReason = it }, label = { Text("Reason") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = {
                                    if (corrVal.isNotBlank() && corrReason.isNotBlank()) {
                                        scope.launch {
                                            controller.useCases.recordLabCalibrationCorrection(state.activeWorkspaceId, c.id, "QA Reviewer", corrReason, corrVal, c.unit)
                                            corrVal = ""; corrReason = ""
                                        }
                                    }
                                }) { Text("Append") }
                            }
                        }
                    }
                }
            }
            11 -> {
                // Wet Chemistry vs NIR Statistical Suite + Live Scatter Plot + Outlier Toggle + CSV Export
                var sampleId by remember { mutableStateOf("") }
                var wetStr by remember { mutableStateOf("") }
                var nirStr by remember { mutableStateOf("") }
                var unitStr by remember { mutableStateOf("%") }
                val summary = remember(wetNirPairs) { LabAnalyticsEngine.compareWetVsNir(wetNirPairs) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(
                            title = "Wet Chemistry vs. NIR Paired Analysis",
                            headerAction = {
                                OutlinedButton(onClick = {
                                    val rows = listOf(listOf("SampleID", "Wet", "NIR", "Unit", "Excluded")) + wetNirPairs.map { listOf(it.sampleId, it.wetResult.toString(), it.nirResult.toString(), it.unit, it.excluded.toString()) }
                                    val art = Rfc4180CsvCodec.exportCsvArtifact("wet-vs-nir.csv", rows)
                                    controller.fileDialogBridge?.pickFileToSave("Export Wet vs NIR CSV", art.filename, art.bytes)
                                    controller.setBanner("Prepared Wet vs NIR CSV (${wetNirPairs.size} pairs)")
                                }) { Text("Export CSV") }
                            },
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(value = sampleId, onValueChange = { sampleId = it }, label = { Text("Sample ID") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = wetStr, onValueChange = { wetStr = it }, label = { Text("Wet") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = nirStr, onValueChange = { nirStr = it }, label = { Text("NIR") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = unitStr, onValueChange = { unitStr = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.width(68.dp))
                            }
                            Button(onClick = {
                                val w = wetStr.toDoubleOrNull(); val n = nirStr.toDoubleOrNull()
                                if (w != null && n != null) {
                                    controller.addLabWetNirPair(sampleId, w, n, unitStr.ifBlank { "%" })
                                    sampleId = ""; wetStr = ""; nirStr = ""
                                }
                            }) { Text("Add Analytical Pair") }
                            Text(
                                "n=${summary.nIncluded} (excl=${summary.nExcluded}) · Bias=${summary.bias?.let { "%.4f".format(it) } ?: "—"} · " +
                                    "SEP(n-1)=${summary.sdDiffSep?.let { "%.4f".format(it) } ?: "—"} · RMSE=${summary.rmse?.let { "%.4f".format(it) } ?: "—"} · " +
                                    "Slope=${summary.slope?.let { "%.4f".format(it) } ?: "—"} · R²=${summary.rSquared?.let { "%.4f".format(it) } ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            val axis = MaterialTheme.colorScheme.outline
                            val idLine = MaterialTheme.colorScheme.secondary
                            val dot = MaterialTheme.colorScheme.primary
                            Canvas(Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))) {
                                val pad = 18f
                                drawLine(axis, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), 2f)
                                drawLine(axis, Offset(pad, pad), Offset(pad, size.height - pad), 2f)
                                drawLine(idLine, Offset(pad, size.height - pad), Offset(size.width - pad, pad), 2f)
                                val pts = summary.includedPoints
                                if (pts.isNotEmpty()) {
                                    val all = pts.flatMap { listOf(it.wetMean, it.nirMean) }
                                    val lo = (all.minOrNull() ?: 0.0) - 1.0
                                    val hi = (all.maxOrNull() ?: 10.0) + 1.0
                                    val span = (hi - lo).coerceAtLeast(1e-6)
                                    pts.forEach { p ->
                                        val x = pad + (((p.wetMean - lo) / span) * (size.width - pad * 2)).toFloat()
                                        val y = size.height - pad - (((p.nirMean - lo) / span) * (size.height - pad * 2)).toFloat()
                                        drawCircle(dot, 6f, Offset(x, y))
                                    }
                                }
                            }
                        }
                    }
                    items(wetNirPairs, key = { it.id }) { p ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${p.sampleId}: Wet=${p.wetResult} NIR=${p.nirResult} ${p.unit} ${if (p.excluded) "(Excluded)" else ""}", modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    val upd = p.copy(excluded = !p.excluded, excludeReason = if (!p.excluded) "Analyst outlier toggle" else null)
                                    controller.createOrUpdateRecord(RecordKind.LAB_WET_NIR, "${upd.sampleId} (${upd.analysis})", controller.json.encodeToString(upd), "Wet=${upd.wetResult} NIR=${upd.nirResult}", existingId = upd.id)
                                }) { Text(if (p.excluded) "Include" else "Exclude") }
                                TextButton(onClick = { controller.moveRecordToTrash(p.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            14 -> {
                // Configurable GUM Uncertainty & Analytical Chemistry Reference Workbook
                var c1 by remember { mutableStateOf("10") }; var c2 by remember { mutableStateOf("2.5") }; var v2 by remember { mutableStateOf("100") }
                var u1 by remember { mutableStateOf("0.08") }; var u2 by remember { mutableStateOf("0.06") }; var kStr by remember { mutableStateOf("2.0") }
                val v1 = remember(c1, c2, v2) { runCatching { LabAnalyticsEngine.dilutionVolumeV1(c1.toDouble(), c2.toDouble(), v2.toDouble()) }.getOrNull() }
                val gum = remember(u1, u2, kStr) {
                    runCatching {
                        LaboratoryOperationsEngine.computeUncertaintyBudget(
                            listOf(UncertaintyComponent("Type A Repeatability", u1.toDouble()), UncertaintyComponent("Reference Standard", u2.toDouble())),
                            coverageFactorK = kStr.toDouble(),
                            methodVersion = "User Worksheet",
                        )
                    }.getOrNull()
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Dilution Calculator (C1·V1 = C2·V2)") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = c1, onValueChange = { c1 = it }, label = { Text("Stock C1") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = c2, onValueChange = { c2 = it }, label = { Text("Target C2") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = v2, onValueChange = { v2 = it }, label = { Text("Final V2") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Text("Required Stock Volume V1 = ${v1?.let { "%.4f".format(it) } ?: "—"}", fontWeight = FontWeight.Bold)
                        }
                    }
                    item {
                        StudioCard(title = "GUM Measurement Uncertainty Worksheet") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = u1, onValueChange = { u1 = it }, label = { Text("u(Repeat)") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = u2, onValueChange = { u2 = it }, label = { Text("u(Std)") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = kStr, onValueChange = { kStr = it }, label = { Text("k") }, singleLine = true, modifier = Modifier.width(80.dp))
                            }
                            gum?.let { Text("Combined uc = ${"%.4f".format(it.combinedStandardUncertainty)} · Expanded U(k=${it.coverageFactorK}) = ${"%.4f".format(it.expandedUncertainty)}", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            else -> {
                // Dedicated persisted CRUD for remaining Lab submodules (Subcontracts, Purchasing/Budgets, Pricing, Research, Email Drafts, Lab Tasks, Work Hours, QMS/SOP/CAPA)
                val kind = when (tab) {
                    2 -> RecordKind.LAB_SUBCONTRACT
                    4 -> RecordKind.LAB_ORDER
                    5 -> RecordKind.LAB_PRICE_ENTRY
                    6 -> RecordKind.LAB_RESEARCH
                    7 -> RecordKind.LAB_EMAIL_DRAFT
                    8 -> RecordKind.LAB_TASK
                    12 -> RecordKind.LAB_WORK_HOUR
                    else -> RecordKind.LAB_QMS_ITEM
                }
                val itemsList by remember(state.activeWorkspaceId, kind) { controller.observeKind(kind, 120) }.collectAsState(emptyList())
                var title by remember { mutableStateOf("") }
                var detail by remember { mutableStateOf("") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = labTabs[tab]) {
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title / Identifier") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = detail, onValueChange = { detail = it }, label = { Text("Details, pricing, hours, or SOP/CAPA notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                if (title.isNotBlank()) {
                                    controller.createOrUpdateRecord(kind, title.trim(), controller.json.encodeToString(mapOf("details" to detail)), detail)
                                    title = ""; detail = ""
                                }
                            }) { Text("Save ${labTabs[tab]} Entry") }
                        }
                    }
                    items(itemsList, key = { it.id }) { rec ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(rec.title, fontWeight = FontWeight.Medium)
                                    Text(rec.summaryText, style = MaterialTheme.typography.bodySmall)
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
// 2. Complete Kitchen OS & Exact-Decimal Personal Finance (§20)
// ============================================================================

@Composable
fun KitchenAndFinanceStudio(route: PlanadayRoute, state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    var tab by remember(route) {
        mutableStateOf(
            when (route) {
                PlanadayRoute.RECIPES -> 0
                PlanadayRoute.PANTRY -> 1
                PlanadayRoute.MEAL_PLANNER -> 2
                PlanadayRoute.GROCERIES -> 3
                PlanadayRoute.FINANCE -> 4
                else -> 0
            }
        )
    }
    val scope = rememberCoroutineScope()

    val recipeRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.RECIPE, 100) }.collectAsState(emptyList())
    val pantryRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.PANTRY_LOT, 150) }.collectAsState(emptyList())
    val groceryRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.GROCERY_ITEM, 150) }.collectAsState(emptyList())
    val foodRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.FOOD_LOG, 100) }.collectAsState(emptyList())
    val expenseRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.EXPENSE, 150) }.collectAsState(emptyList())
    val envRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.BUDGET_ENVELOPE, 50) }.collectAsState(emptyList())

    val recipes = remember(recipeRecs) { recipeRecs.mapNotNull { runCatching { controller.json.decodeFromString<RecipeItem>(it.payloadJson) }.getOrNull() } }
    val pantry = remember(pantryRecs) { pantryRecs.mapNotNull { runCatching { controller.json.decodeFromString<PantryLot>(it.payloadJson) }.getOrNull() } }
    val groceries = remember(groceryRecs) { groceryRecs.mapNotNull { runCatching { controller.json.decodeFromString<GroceryItem>(it.payloadJson) }.getOrNull() } }
    val expenses = remember(expenseRecs) { expenseRecs.mapNotNull { runCatching { controller.json.decodeFromString<ExpenseItem>(it.payloadJson) }.getOrNull() } }
    val envelopes = remember(envRecs) { envRecs.mapNotNull { runCatching { controller.json.decodeFromString<BudgetEnvelope>(it.payloadJson) }.getOrNull() } }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StudioTabRow(listOf("Recipes (${recipes.size})", "Pantry (${pantry.size})", "Meals & Food Log", "Groceries (${groceries.size})", "Finance & Budgets"), tab, { tab = it })

        when (tab) {
            0 -> {
                var rTitle by remember { mutableStateOf("") }
                var rServings by remember { mutableStateOf("4") }
                var rIngName by remember { mutableStateOf("") }
                var rIngQty by remember { mutableStateOf("2") }
                var rIngUnit by remember { mutableStateOf("cup") }
                var scaleMap by remember { mutableStateOf(mapOf<String, Double>()) }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Add Recipe") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = rTitle, onValueChange = { rTitle = it }, label = { Text("Recipe name") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = rServings, onValueChange = { rServings = it }, label = { Text("Servings") }, singleLine = true, modifier = Modifier.width(95.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = rIngName, onValueChange = { rIngName = it }, label = { Text("Main ingredient") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = rIngQty, onValueChange = { rIngQty = it }, label = { Text("Qty") }, singleLine = true, modifier = Modifier.width(75.dp))
                                OutlinedTextField(value = rIngUnit, onValueChange = { rIngUnit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.width(75.dp))
                            }
                            Button(onClick = {
                                if (rTitle.isNotBlank()) {
                                    val ings = if (rIngName.isNotBlank()) listOf(RecipeIngredient(UUID.randomUUID().toString(), rIngQty.toDoubleOrNull() ?: 1.0, rIngUnit, rIngName.trim(), rIngName.trim().lowercase())) else emptyList()
                                    val rec = RecipeItem(UUID.randomUUID().toString(), state.activeWorkspaceId, rTitle.trim(), rServings.toDoubleOrNull() ?: 4.0, ingredients = ings)
                                    controller.createOrUpdateRecord(RecordKind.RECIPE, rec.title, controller.json.encodeToString(rec), "${rec.servings} servings")
                                    rTitle = ""; rIngName = ""
                                }
                            }) { Text("Save Recipe") }
                        }
                    }
                    items(recipes, key = { it.id }) { rec ->
                        val targetServ = scaleMap[rec.id] ?: rec.servings
                        val scaled = KitchenAndFinanceEngines.scaleRecipe(rec, targetServ)
                        val (_, shortages) = KitchenAndFinanceEngines.computeRecipeShortagesFefo(scaled, pantry, SystemDomainClock.todayIso())
                        StudioCard(title = "${rec.title} (${targetServ} servings)", subtitle = scaled.ingredients.joinToString { "${it.quantity} ${it.unit} ${it.name}" }) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { scaleMap = scaleMap + (rec.id to (targetServ * 2.0)) }) { Text("2× Servings") }
                                OutlinedButton(onClick = { scaleMap = scaleMap + (rec.id to rec.servings) }) { Text("Reset") }
                                if (shortages.isNotEmpty()) {
                                    Button(onClick = {
                                        shortages.forEach { sh ->
                                            val g = GroceryItem(UUID.randomUUID().toString(), state.activeWorkspaceId, sh.canonicalName, sh.displayName, sh.shortageQty, sh.unit, sh.aisle)
                                            controller.createOrUpdateRecord(RecordKind.GROCERY_ITEM, "${g.displayName} (${g.quantity} ${g.unit})", controller.json.encodeToString(g), g.aisle)
                                        }
                                        controller.setBanner("Added ${shortages.size} shortage item(s) to Groceries")
                                    }) { Text("Add ${shortages.size} Shortage(s) to Groceries") }
                                } else {
                                    StudioStatusPill("All ingredients in Pantry")
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                var name by remember { mutableStateOf("") }
                var qty by remember { mutableStateOf("2") }
                var unit by remember { mutableStateOf("cup") }
                var exp by remember { mutableStateOf(CalendarDateMath.addDays(SystemDomainClock.todayIso(), 7)) }
                val expiring = remember(pantry) { KitchenAndFinanceEngines.expiringLots(pantry, SystemDomainClock.todayIso(), 5) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Add Pantry Lot (FEFO Expiry Tracking · ${expiring.size} expiring soon)") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ingredient") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, singleLine = true, modifier = Modifier.width(75.dp))
                                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.width(75.dp))
                            }
                            OutlinedTextField(value = exp, onValueChange = { exp = it }, label = { Text("Expiry Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                if (name.isNotBlank()) {
                                    val lot = PantryLot(UUID.randomUUID().toString(), state.activeWorkspaceId, name.trim(), name.trim().lowercase(), qty.toDoubleOrNull() ?: 1.0, unit, expiryDateIso = exp.trim().takeIf { it.length == 10 })
                                    controller.createOrUpdateRecord(RecordKind.PANTRY_LOT, "${lot.ingredientName} (${lot.quantity} ${lot.unit})", controller.json.encodeToString(lot), "Exp: ${lot.expiryDateIso ?: "None"}")
                                    name = ""
                                }
                            }) { Text("Save Pantry Lot") }
                        }
                    }
                    items(pantry, key = { it.id }) { lot ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${lot.ingredientName}: ${lot.quantity} ${lot.unit} · Exp ${lot.expiryDateIso ?: "N/A"}", modifier = Modifier.weight(1f))
                                TextButton(onClick = { controller.moveRecordToTrash(lot.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            2 -> {
                var mealName by remember { mutableStateOf("") }
                var cat by remember { mutableStateOf("lunch") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Log Meal to Unified Food History") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("breakfast", "lunch", "dinner", "snack").forEach { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = mealName, onValueChange = { mealName = it }, label = { Text("Meal / Food") }, singleLine = true, modifier = Modifier.weight(1f))
                                Button(onClick = { if (mealName.isNotBlank()) { controller.logMeal(mealName, cat); mealName = "" } }) { Text("Log") }
                            }
                        }
                    }
                    items(foodRecs, key = { it.id }) { f ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${f.title} (${f.summaryText})", modifier = Modifier.weight(1f))
                                TextButton(onClick = { controller.moveRecordToTrash(f.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            3 -> {
                var itemName by remember { mutableStateOf("") }
                var priceStr by remember { mutableStateOf("5.99") }
                var aisle by remember { mutableStateOf("Produce") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Grocery List (Checking Off Restocks Pantry & Logs Expense)") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("Grocery item") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = priceStr, onValueChange = { priceStr = it }, label = { Text("USD") }, singleLine = true, modifier = Modifier.width(88.dp))
                                OutlinedTextField(value = aisle, onValueChange = { aisle = it }, label = { Text("Aisle") }, singleLine = true, modifier = Modifier.width(105.dp))
                            }
                            Button(onClick = {
                                if (itemName.isNotBlank()) {
                                    val g = GroceryItem(UUID.randomUUID().toString(), state.activeWorkspaceId, itemName.trim().lowercase(), itemName.trim(), 1.0, "unit", aisle, unitPrice = DecimalAmount(priceStr.ifBlank { "0.00" }, "USD"))
                                    controller.createOrUpdateRecord(RecordKind.GROCERY_ITEM, g.displayName, controller.json.encodeToString(g), "$aisle · $${g.unitPrice?.value}")
                                    itemName = ""
                                }
                            }) { Text("Add to Grocery List") }
                        }
                    }
                    items(groceries, key = { it.id }) { g ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${g.displayName} (${g.quantity} ${g.unit})", fontWeight = FontWeight.Medium)
                                    Text("Aisle: ${g.aisle} · Est: $${g.unitPrice?.value ?: "0.00"}", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = {
                                    scope.launch {
                                        controller.useCases.reconcileGroceryPurchase(state.activeWorkspaceId, g.copy(checked = true), null)
                                        controller.moveRecordToTrash(g.id)
                                        controller.setBanner("Purchased '${g.displayName}' -> restocked Pantry & logged Expense")
                                    }
                                }) { Text("Buy & Restock") }
                            }
                        }
                    }
                }
            }
            else -> {
                // EXACT-DECIMAL FINANCE, BUDGET ENVELOPES & FORECASTING
                var envName by remember { mutableStateOf("Groceries") }
                var envLimit by remember { mutableStateOf("450.00") }
                var expDesc by remember { mutableStateOf("") }
                var expAmt by remember { mutableStateOf("") }
                var expCat by remember { mutableStateOf("Groceries") }
                val report = remember(envelopes, expenses) {
                    KitchenAndFinanceEngines.forecastMonthlyBudget(envelopes, expenses, "USD", java.time.LocalDate.now().dayOfMonth, 30)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudioMetricTile("Budget Limit", "$${report.totalBudgetLimit.value}", modifier = Modifier.weight(1f))
                            StudioMetricTile("Spent", "$${report.totalSpentSoFar.value}", accent = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                            StudioMetricTile("Month-End Proj.", "$${report.projectedMonthEndSpend.value}", accent = if (report.projectedOverrun) MaterialTheme.colorScheme.error else LocalPlanadaySpacing.current.semantics.success, modifier = Modifier.weight(1f))
                        }
                    }
                    item {
                        StudioCard(title = "Add Budget Envelope") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = envName, onValueChange = { envName = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = envLimit, onValueChange = { envLimit = it }, label = { Text("Monthly Limit USD") }, singleLine = true, modifier = Modifier.width(145.dp))
                                Button(onClick = {
                                    val clean = runCatching { ExactDecimal.toScaledUnits(envLimit); envLimit }.getOrDefault("100.00")
                                    val e = BudgetEnvelope(UUID.randomUUID().toString(), state.activeWorkspaceId, envName.ifBlank { "General" }, envName.ifBlank { "General" }, DecimalAmount(clean, "USD"))
                                    controller.createOrUpdateRecord(RecordKind.BUDGET_ENVELOPE, "${e.name} ($${e.limitAmount.value})", controller.json.encodeToString(e), "Monthly")
                                }) { Text("Save") }
                            }
                        }
                    }
                    item {
                        StudioCard(title = "Record Expense") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = expDesc, onValueChange = { expDesc = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = expAmt, onValueChange = { expAmt = it }, label = { Text("Amount USD") }, singleLine = true, modifier = Modifier.width(120.dp))
                                OutlinedTextField(value = expCat, onValueChange = { expCat = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.width(120.dp))
                            }
                            Button(onClick = {
                                if (expDesc.isNotBlank() && expAmt.isNotBlank()) {
                                    val clean = runCatching { ExactDecimal.toScaledUnits(expAmt); expAmt }.getOrNull() ?: return@Button
                                    val today = SystemDomainClock.todayIso()
                                    val e = ExpenseItem(UUID.randomUUID().toString(), state.activeWorkspaceId, today, expDesc.trim(), DecimalAmount(clean, "USD"), expCat.ifBlank { "General" })
                                    controller.createOrUpdateRecord(RecordKind.EXPENSE, "${e.description} ($${e.amount.value})", controller.json.encodeToString(e), e.category, scheduleStartIso = today, scheduleEndIso = today)
                                    expDesc = ""; expAmt = ""
                                }
                            }) { Text("Save Expense") }
                        }
                    }
                    items(expenses, key = { it.id }) { e ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${e.dateIso} · ${e.description} [${e.category}]", modifier = Modifier.weight(1f))
                                Text("$${e.amount.value}", fontWeight = FontWeight.Bold)
                                TextButton(onClick = { controller.moveRecordToTrash(e.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. Household, Health/Meds, Travel, People, Media & HomeOS (§20)
// ============================================================================

@Composable
fun PersonalLifeModulesStudio(route: PlanadayRoute, state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    val kind = PlanadayWorkspaceController.routeToPrimaryKind(route)
    val recs by remember(state.activeWorkspaceId, kind) { controller.observeKind(kind, 120) }.collectAsState(emptyList())
    val choresRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.CHORE, 100) }.collectAsState(emptyList())
    val chores = remember(choresRecs) { choresRecs.mapNotNull { runCatching { controller.json.decodeFromString<ChoreItem>(it.payloadJson) }.getOrNull() } }

    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        if (route == PlanadayRoute.HEALTH_MEDS) {
            item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Personal organizational aid only — not medical advice, diagnosis, or automated dosing guidance.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(10.dp))
                }
            }
        }
        if (route == PlanadayRoute.HOUSEHOLD) {
            val fairness = LifeWellnessAndLearningEngines.computeHouseholdFairness(chores)
            item {
                StudioCard(title = "Household Chore Balance") {
                    if (fairness.isEmpty()) Text("Complete chores with assigned household members to track weighted fairness shares.", style = MaterialTheme.typography.bodySmall)
                    else fairness.forEach { f -> Text("${f.memberId}: ${f.completedCount} completed · ${f.weightedDifficultyPoints} pts (${"%.1f".format(f.sharePct)}%)") }
                }
            }
        }
        item {
            StudioCard(title = "Add ${route.title} Item") {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Name / Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = detail, onValueChange = { detail = it }, label = { Text("Schedule, notes, rating, dates, or warranty details") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    if (title.isNotBlank()) {
                        controller.createOrUpdateRecord(kind, title.trim(), controller.json.encodeToString(mapOf("details" to detail)), detail)
                        title = ""; detail = ""
                    }
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save") }
            }
        }
        items(recs, key = { it.id }) { rec ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rec.title, fontWeight = FontWeight.SemiBold)
                        Text(rec.summaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { controller.moveRecordToTrash(rec.id) }) { Text("Delete") }
                }
            }
        }
    }
}

// ============================================================================
// 4. Native Files, PDF Review & Non-Destructive Photo Studio (§22 — Real User Files!)
// ============================================================================

@Composable
fun FilesAndPhotoStudio(route: PlanadayRoute, state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    val attachments by remember(state.activeWorkspaceId) { controller.observeAttachments(150) }.collectAsState(emptyList())
    val photoRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.PHOTO_ASSET, 100) }.collectAsState(emptyList())
    val reviewRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.DOCUMENT_REVIEW, 80) }.collectAsState(emptyList())
    val photos = remember(photoRecs) { photoRecs.mapNotNull { runCatching { controller.json.decodeFromString<PhotoAsset>(it.payloadJson) }.getOrNull() } }
    val reviews = remember(reviewRecs) { reviewRecs.mapNotNull { runCatching { controller.json.decodeFromString<ReviewedDocumentModel>(it.payloadJson) }.getOrNull() } }
    val scope = rememberCoroutineScope()

    var selectedPhoto by remember { mutableStateOf<PhotoAsset?>(null) }
    var albumName by remember { mutableStateOf("All Photos") }
    var annotationText by remember { mutableStateOf("") }

    if (route == PlanadayRoute.PHOTO_STUDIO) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            item {
                StudioCard(
                    title = "Photo Library (${photos.size})",
                    subtitle = "Import your real photos with deduplicated SHA-256 storage and non-destructive editing recipes",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = albumName, onValueChange = { albumName = it }, label = { Text("Target Album") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                val picked = controller.fileDialogBridge?.pickFileToRead("Import Photo", listOf("png", "jpg", "jpeg", "webp"))
                                if (picked != null) {
                                    val (fname, bytes) = picked
                                    scope.launch {
                                        val mime = if (fname.endsWith(".png", true)) "image/png" else "image/jpeg"
                                        val recId = UUID.randomUUID().toString()
                                        val meta = controller.repository.attachBlobToRecord(state.activeWorkspaceId, recId, fname, mime, bytes, "photo_original")
                                        val asset = PhotoAsset(recId, state.activeWorkspaceId, meta.sha256, fname, albumName.ifBlank { "All Photos" }, "", PhotoEditRecipe(), 1L, SystemDomainClock.nowUtcIso())
                                        controller.createOrUpdateRecord(RecordKind.PHOTO_ASSET, asset.title, controller.json.encodeToString(asset), "${asset.album} · ${bytes.size} B", existingId = null)
                                        selectedPhoto = asset
                                    }
                                } else {
                                    controller.setBanner("Select an image file using the system file dialog to import.")
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Import real photo file" },
                        ) { Text("+ Import Photo") }
                    }
                }
            }

            selectedPhoto?.let { ph ->
                item {
                    val (_, desc) = PhotoAndPlannerAdvanced.applyPhotoEditRecipe(ph, 1920, 1080, ph.editRecipe)
                    StudioCard(
                        title = "Non-Destructive Editor: ${ph.title}",
                        subtitle = "Original SHA-256 ${ph.originalBlobSha256.take(12)}… untouched · Output ${desc.outputWidthPx}×${desc.outputHeightPx} · Rot ${desc.rotationDeg}°",
                    ) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val next = ph.copy(editRecipe = ph.editRecipe.copy(rotationDeg = (ph.editRecipe.rotationDeg + 90) % 360))
                                selectedPhoto = next
                                controller.createOrUpdateRecord(RecordKind.PHOTO_ASSET, next.title, controller.json.encodeToString(next), "Rot ${next.editRecipe.rotationDeg}°", existingId = next.id)
                            }) { Text("Rotate +90°") }
                            OutlinedButton(onClick = {
                                val next = ph.copy(editRecipe = ph.editRecipe.copy(cropW = if (ph.editRecipe.cropW > 0.8) 0.75 else 1.0, cropH = if (ph.editRecipe.cropH > 0.8) 0.75 else 1.0))
                                selectedPhoto = next
                                controller.createOrUpdateRecord(RecordKind.PHOTO_ASSET, next.title, controller.json.encodeToString(next), "Crop ${(next.editRecipe.cropW * 100).toInt()}%", existingId = next.id)
                            }) { Text("Toggle 75% Crop") }
                            OutlinedButton(onClick = {
                                val next = ph.copy(editRecipe = PhotoEditRecipe())
                                selectedPhoto = next
                                controller.createOrUpdateRecord(RecordKind.PHOTO_ASSET, next.title, controller.json.encodeToString(next), "Reset to original", existingId = next.id)
                            }) { Text("Reset to Original") }
                        }
                        Text("Brightness: ${"%.2f".format(ph.editRecipe.brightnessDelta)} · Contrast: ${"%.2f".format(ph.editRecipe.contrastFactor)}")
                        Slider(
                            value = ph.editRecipe.brightnessDelta.toFloat(),
                            onValueChange = { b ->
                                val next = ph.copy(editRecipe = ph.editRecipe.copy(brightnessDelta = b.toDouble()))
                                selectedPhoto = next
                            },
                            onValueChangeFinished = {
                                selectedPhoto?.let { next ->
                                    controller.createOrUpdateRecord(RecordKind.PHOTO_ASSET, next.title, controller.json.encodeToString(next), "Adjusted tone", existingId = next.id)
                                }
                            },
                            valueRange = -0.5f..0.5f,
                        )
                    }
                }
            }

            if (photos.isEmpty()) {
                item { StudioEmptyState("No Imported Photos", "Click '+ Import Photo' to select real image files from your device.") }
            }
            items(photos, key = { it.id }) { ph ->
                OutlinedCard(onClick = { selectedPhoto = ph }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(ph.title, fontWeight = FontWeight.Medium)
                            Text("Album: ${ph.album} · Rot ${ph.editRecipe.rotationDeg}° · SHA ${ph.originalBlobSha256.take(10)}…", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { controller.moveRecordToTrash(ph.id) }) { Text("Delete") }
                    }
                }
            }
        }
    } else {
        // Universal Files & PDF/Document Review
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            item {
                StudioCard(title = "Universal Files & Document Review (${attachments.size} stored files)") {
                    Button(
                        onClick = {
                            val picked = controller.fileDialogBridge?.pickFileToRead("Import File or PDF")
                            if (picked != null) {
                                val (fname, bytes) = picked
                                scope.launch {
                                    val rec = controller.repository.createRecord(state.activeWorkspaceId, RecordKind.FILE_FOLDER, fname, """{"filename":"$fname"}""", "${bytes.size} bytes")
                                    val meta = controller.repository.attachBlobToRecord(state.activeWorkspaceId, rec.id, fname, if (fname.endsWith(".pdf", true)) "application/pdf" else "application/octet-stream", bytes)
                                    val doc = ReviewedDocumentModel(UUID.randomUUID().toString(), state.activeWorkspaceId, fname, meta.sha256, 1)
                                    controller.createOrUpdateRecord(RecordKind.DOCUMENT_REVIEW, fname, controller.json.encodeToString(doc), "SHA ${meta.sha256.take(10)}…")
                                    controller.setBanner("Imported '$fname' (${bytes.size} bytes, SHA-256 verified)")
                                }
                            } else {
                                controller.setBanner("Use the system file picker to import files.")
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("+ Import File / PDF") }
                }
            }
            items(reviews, key = { it.id }) { doc ->
                StudioCard(title = doc.title, subtitle = "Annotations: ${doc.annotations.size} · SHA-256: ${doc.blobSha256.take(12)}…") {
                    doc.annotations.forEach { a -> Text("p.${a.pageNumber}: ${a.comment}", style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = annotationText, onValueChange = { annotationText = it }, label = { Text("Add annotation") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            if (annotationText.isNotBlank()) {
                                val upd = doc.copy(annotations = doc.annotations + DocumentAnnotation(UUID.randomUUID().toString(), 1, "", annotationText.trim(), createdAtUtc = SystemDomainClock.nowUtcIso()))
                                controller.createOrUpdateRecord(RecordKind.DOCUMENT_REVIEW, upd.title, controller.json.encodeToString(upd), "${upd.annotations.size} notes", existingId = upd.id)
                                annotationText = ""
                            }
                        }) { Text("Annotate") }
                    }
                }
            }
            items(attachments, key = { it.sha256 }) { att ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(att.originalFilename, fontWeight = FontWeight.Medium)
                            Text("${att.mimeType} · ${att.byteSize} bytes · SHA ${att.sha256.take(12)}…", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = {
                            val bytes = controller.repository.blobStore.getVerified(att.sha256)
                            if (bytes != null) controller.fileDialogBridge?.pickFileToSave("Export ${att.originalFilename}", att.originalFilename, bytes)
                        }) { Text("Export") }
                    }
                }
            }
        }
    }
}
