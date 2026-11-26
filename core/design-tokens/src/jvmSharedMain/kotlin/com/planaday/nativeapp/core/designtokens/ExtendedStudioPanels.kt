package com.planaday.nativeapp.core.designtokens

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.automation.AutomationDomainEvent
import com.planaday.nativeapp.core.automation.AutomationEngine
import com.planaday.nativeapp.core.automation.AutomationRuleDef
import com.planaday.nativeapp.core.domain.*
import com.planaday.nativeapp.core.graphics.*
import com.planaday.nativeapp.core.interchange.ExtendedFormatInterchange
import com.planaday.nativeapp.core.interchange.RichDocumentExporters
import com.planaday.nativeapp.core.math.MathAstEngine
import com.planaday.nativeapp.core.math.MathEvalContext
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.notifications.NotificationScheduleEngine
import com.planaday.nativeapp.core.notifications.QuietHoursPolicy
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// 1. Time Suite (6 Clock Designs, Smart Alarms, Quiet Hours, Monotonic Timers, Stopwatch, Countdowns)
// ============================================================================

@Composable
fun TimeSuitePanel(route: PlanadayRoute, state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    var clockDesign by remember { mutableStateOf(ClockDesign.ANALOG_TRADITIONAL) }
    var alarmTime by remember { mutableStateOf("23:15") }
    var quietEnabled by remember { mutableStateOf(true) }
    var timerSec by remember { mutableStateOf(1500L) }
    var timerState by remember { mutableStateOf(NotificationScheduleEngine.MonotonicTimerState("tm_focus", "Pomodoro", 1500_000L)) }
    var nowMs by remember { mutableStateOf(100_000L) }

    val alarmPreview = remember(alarmTime, quietEnabled) {
        NotificationScheduleEngine.computeNextAlarmOccurrence(
            AlarmItem("a_prev", state.activeWorkspaceId, "Smart Alarm", alarmTime, "UTC"),
            SystemDomainClock.todayIso(),
            "16:00",
            QuietHoursPolicy(enabled = quietEnabled, startLocal = "22:00", endLocal = "07:00"),
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("6 Clock Designs & World Clock (C-0030, R-0268)", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ClockDesign.entries.forEach { d ->
                            FilterChip(selected = clockDesign == d, onClick = { clockDesign = d }, label = { Text(d.name) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val accent = MaterialTheme.colorScheme.primary
                        val outline = MaterialTheme.colorScheme.onSurface
                        Canvas(modifier = Modifier.size(96.dp).semantics { contentDescription = "Analog clock preview ${clockDesign.name}" }) {
                            val r = size.minDimension / 2f - 4f
                            val c = center
                            drawCircle(color = outline, radius = r, center = c, style = Stroke(width = 3f))
                            for (h in 0 until 12) {
                                val a = Math.toRadians(h * 30.0 - 90.0)
                                drawLine(
                                    color = outline,
                                    start = Offset(c.x + (r * 0.82f * cos(a)).toFloat(), c.y + (r * 0.82f * sin(a)).toFloat()),
                                    end = Offset(c.x + (r * 0.96f * cos(a)).toFloat(), c.y + (r * 0.96f * sin(a)).toFloat()),
                                    strokeWidth = 2f,
                                )
                            }
                            drawLine(color = outline, start = c, end = Offset(c.x + r * 0.45f, c.y - r * 0.25f), strokeWidth = 4f)
                            drawLine(color = accent, start = c, end = Offset(c.x, c.y - r * 0.72f), strokeWidth = 3f)
                        }
                        Column {
                            Text("Style: ${clockDesign.name}", fontWeight = FontWeight.Bold)
                            Text("UTC · America/Los_Angeles · Europe/London · Asia/Tokyo", style = MaterialTheme.typography.bodySmall)
                            Text("Route context: ${route.title}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Smart Alarms, Quiet Hours & Monotonic Timers (C-0031..C-0034, PF-46)", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = alarmTime, onValueChange = { alarmTime = it }, label = { Text("Alarm HH:MM") }, modifier = Modifier.width(140.dp), singleLine = true)
                        Checkbox(
                            checked = quietEnabled,
                            onCheckedChange = { quietEnabled = it },
                            modifier = Modifier.semantics { contentDescription = "Toggle quiet hours 22:00 to 07:00" },
                        )
                        Text("Quiet 22:00–07:00")
                    }
                    alarmPreview?.let {
                        Text("Next fire: ${it.fireDateIso} ${it.fireTimeLocal} (${if (it.deferredByQuietHours) "Deferred by Quiet Hours" else "On-time"})", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { timerState = timerState.start(nowMs); nowMs += 60_000L },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Start or advance monotonic focus timer" },
                        ) { Text("Start/Tick +1m (${timerState.remainingMs(nowMs) / 1000}s left)") }
                        OutlinedButton(
                            onClick = { timerState = NotificationScheduleEngine.MonotonicTimerState("tm_focus", "Pomodoro", timerSec * 1000L) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Reset focus timer" },
                        ) { Text("Reset") }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. Infinite Whiteboard Vector Canvas + SpatialIndex2D + SVG Export (C-0019, PF-31)
// ============================================================================

@Composable
fun WhiteboardCanvasPanel() {
    val spatial = remember {
        SpatialIndex2D(256.0).apply {
            upsert(CanvasNode("n1", "sticky", Bounds2D(20.0, 20.0, 120.0, 70.0), zIndex = 1, text = "Research"))
            upsert(CanvasNode("n2", "ellipse", Bounds2D(190.0, 40.0, 120.0, 70.0), zIndex = 2, text = "Prototype"))
        }
    }
    var version by remember { mutableStateOf(0) }
    var svgPreview by remember { mutableStateOf<String?>(null) }
    val nodes = remember(version) { spatial.allNodes() }
    val connectors = remember(nodes) {
        if (nodes.size >= 2) listOf(CanvasConnector("conn_1", nodes[0].id, nodes[1].id)) else emptyList()
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Infinite Vector Whiteboard · ${nodes.size} Spatial Nodes (C-0019, R-0485..R-0494)", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val idx = spatial.size() + 1
                                spatial.upsert(CanvasNode("n$idx", "sticky", Bounds2D((idx * 35 % 240).toDouble(), (idx * 25 % 110).toDouble(), 90.0, 50.0), zIndex = idx, text = "Note #$idx"))
                                version++
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add sticky note to whiteboard" },
                        ) { Text("+ Sticky") }
                        OutlinedButton(
                            onClick = {
                                svgPreview = VectorSvgExporter.exportWhiteboardSvg("Planaday Board", nodes, connectors).take(320)
                            },
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Export whiteboard SVG" },
                        ) { Text("Export SVG (C-0066)") }
                    }
                    val fillCol = MaterialTheme.colorScheme.primaryContainer
                    val strokeCol = MaterialTheme.colorScheme.primary
                    Canvas(
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .semantics { contentDescription = "Interactive whiteboard vector canvas" }
                    ) {
                        connectors.forEach { c ->
                            val a = spatial.get(c.fromNodeId)
                            val b = spatial.get(c.toNodeId)
                            if (a != null && b != null) {
                                drawLine(
                                    color = strokeCol,
                                    start = Offset((a.bounds.x + a.bounds.width / 2).toFloat(), (a.bounds.y + a.bounds.height / 2).toFloat()),
                                    end = Offset((b.bounds.x + b.bounds.width / 2).toFloat(), (b.bounds.y + b.bounds.height / 2).toFloat()),
                                    strokeWidth = 3f,
                                )
                            }
                        }
                        nodes.forEach { n ->
                            drawRect(
                                color = fillCol,
                                topLeft = Offset(n.bounds.x.toFloat().coerceAtMost(size.width - 40f), n.bounds.y.toFloat().coerceAtMost(size.height - 30f)),
                                size = Size(n.bounds.width.toFloat().coerceAtMost(110f), n.bounds.height.toFloat().coerceAtMost(60f)),
                            )
                        }
                    }
                    svgPreview?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

// ============================================================================
// 3. Diary & Private Wellness (1-10 Scales, Private Bathroom Counters, Trends) + Thought Inbox
// ============================================================================

@Composable
fun DiaryAndWellnessPanel(state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    var moodRating by remember { mutableStateOf(8f) }
    var energyRating by remember { mutableStateOf(7f) }
    var sleepHours by remember { mutableStateOf("7.5") }
    var urinations by remember { mutableStateOf(0) }
    var bowels by remember { mutableStateOf(0) }
    var reflection by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Daily Diary, 1–10 Wellness Scales & Optional Private Counters (C-0028, PF-17)", fontWeight = FontWeight.SemiBold)
                    Text("Mood: ${moodRating.toInt()}/10 · Energy: ${energyRating.toInt()}/10")
                    Slider(value = moodRating, onValueChange = { moodRating = it }, valueRange = 1f..10f, steps = 8)
                    Slider(value = energyRating, onValueChange = { energyRating = it }, valueRange = 1f..10f, steps = 8)
                    OutlinedTextField(value = sleepHours, onValueChange = { sleepHours = it }, label = { Text("Sleep hours") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { urinations++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Urination +1 ($urinations)") }
                        OutlinedButton(onClick = { bowels++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Bowel +1 ($bowels)") }
                    }
                    OutlinedTextField(value = reflection, onValueChange = { reflection = it }, label = { Text("Diary reflection") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            controller.addRecordForRoute(PlanadayRoute.DIARY, "Diary ${SystemDomainClock.todayIso()}", "Mood=${moodRating.toInt()}/10 Energy=${energyRating.toInt()}/10 Sleep=${sleepHours}h $reflection")
                            reflection = ""
                        },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Save daily diary entry" },
                    ) { Text("Save Diary Entry") }
                }
            }
        }
    }
}

// ============================================================================
// 4. Kitchen (Recipes Scaling, FEFO Pantry, Grocery -> Expense Reconciliation) & Finance Forecast
// ============================================================================

@Composable
fun KitchenAndFinancePanel(route: PlanadayRoute, state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    var targetServings by remember { mutableStateOf("6") }
    var itemName by remember { mutableStateOf("Rolled Oats") }
    var itemCost by remember { mutableStateOf("6.49") }
    var envelopeLimit by remember { mutableStateOf("450.00") }
    var forecastText by remember { mutableStateOf<String?>(null) }

    val sampleRecipe = remember {
        RecipeItem(
            id = "rec_oats", workspaceId = state.activeWorkspaceId, title = "Warm Berry Oats", servings = 2.0,
            ingredients = listOf(
                RecipeIngredient("i1", 1.0, "cup", "Rolled Oats", "oats", "Dry Goods"),
                RecipeIngredient("i2", 2.0, "cup", "Milk", "milk", "Dairy"),
            ),
        )
    }
    val scaled = remember(targetServings) {
        KitchenAndFinanceEngines.scaleRecipe(sampleRecipe, targetServings.toDoubleOrNull()?.coerceAtLeast(0.5) ?: 2.0)
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kitchen OS: Recipe Scaling, FEFO Pantry & Grocery-to-Expense Reconciliation (PF-15, INT-013)", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = targetServings, onValueChange = { targetServings = it }, label = { Text("Scale Servings (Base 2)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Scaled Ingredients: ${scaled.ingredients.joinToString { "${it.quantity} ${it.unit} ${it.name}" }}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("Purchased item") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = itemCost, onValueChange = { itemCost = it }, label = { Text("USD") }, modifier = Modifier.width(100.dp), singleLine = true)
                    }
                    Button(
                        onClick = { controller.addRecordForRoute(route, itemName, "Exact decimal cost: $itemCost USD") },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Save kitchen or finance record" },
                    ) { Text("Record ${route.shortLabel} Entry") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Exact-Decimal Budget Envelope Forecasting (PF-16, N-022)", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = envelopeLimit, onValueChange = { envelopeLimit = it }, label = { Text("Monthly Budget Limit (USD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            val lim = runCatching { ExactDecimal.toScaledUnits(envelopeLimit); envelopeLimit }.getOrDefault("450.00")
                            val report = KitchenAndFinanceEngines.forecastMonthlyBudget(
                                envelopes = listOf(BudgetEnvelope("e1", state.activeWorkspaceId, "Groceries", "Groceries", DecimalAmount(lim, "USD"))),
                                expenses = state.expenses,
                                currency = "USD",
                                dayOfMonth = 15,
                                daysInMonth = 30,
                            )
                            forecastText = "Spent: ${report.totalSpentSoFar.value} USD · Remaining: ${report.remainingBudget.value} USD · Projected Month-End: ${report.projectedMonthEndSpend.value} USD (Overrun=${report.projectedOverrun})"
                        },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Compute monthly budget forecast" },
                    ) { Text("Compute Exact-Decimal Forecast") }
                    forecastText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

// ============================================================================
// 5. Projects Gantt + Custom Schema Builder + Game Dev + Book Studio + Presentations + Photos
// ============================================================================

@Composable
fun CreativeAndProjectsPanel(route: PlanadayRoute, state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    var output by remember { mutableStateOf("Interactive ${route.title} engine ready (100% offline).") }
    var simSeed by remember { mutableStateOf("42") }
    var photoRot by remember { mutableStateOf(0) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${route.title} Interactive Engine (${route.requirementRef})", fontWeight = FontWeight.SemiBold)
                    when (route) {
                        PlanadayRoute.PROJECTS -> {
                            Button(
                                onClick = {
                                    val gantt = ProjectGanttCriticalPathEngine.analyze(
                                        listOf(
                                            ProjectTaskNode("A", "Schema Design", 3),
                                            ProjectTaskNode("B", "Sync Protocol", 4, listOf("A")),
                                            ProjectTaskNode("C", "UI Polish", 2, listOf("A")),
                                            ProjectTaskNode("D", "Release Gate", 2, listOf("B", "C")),
                                        )
                                    )
                                    output = "Critical Path: ${gantt.criticalPathIds.joinToString(" -> ")} | Total=${gantt.totalDurationDays}d | Peak=${gantt.peakDailyHours}h/d"
                                },
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Compute project Gantt critical path" },
                            ) { Text("Compute Gantt Critical Path (PF-13 / N-031)") }
                            OutlinedButton(
                                onClick = {
                                    val schema = CustomSchemaDef(
                                        "cs1", state.activeWorkspaceId, "Experiment",
                                        fields = listOf(
                                            CustomFieldDef("mass", "Mass (g)", CustomFieldType.NUMBER, required = true, minNumber = 0.0),
                                            CustomFieldDef("vol", "Volume (mL)", CustomFieldType.NUMBER, required = true, minNumber = 0.01),
                                            CustomFieldDef("density", "Density", CustomFieldType.COMPUTED_FORMULA, formulaExpression = "mass / vol"),
                                        ),
                                    )
                                    val res = CustomSchemaEngine.validateAndCompute(schema, mapOf("mass" to "25.0", "vol" to "10.0")) { expr, vars ->
                                        MathAstEngine.evaluate(expr, MathEvalContext(variables = vars))
                                    }
                                    output = "Custom Schema Valid=${res.valid} | Computed=${res.computedValues}"
                                },
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Validate custom schema computed field" },
                            ) { Text("Test Declarative Custom Schema (PF-36 / N-002)") }
                        }
                        PlanadayRoute.GAME_DEV -> {
                            OutlinedTextField(value = simSeed, onValueChange = { simSeed = it }, label = { Text("Deterministic Seed (N-032)") }, singleLine = true)
                            Button(
                                onClick = {
                                    val res = GameBalanceSimulatorEngine.simulateCombat(
                                        CombatantStats("Hero", 120.0, 22.0, 8.0, 0.15, 1.8),
                                        CombatantStats("Boss", 150.0, 18.0, 6.0, 0.10, 1.5),
                                        trials = 200,
                                        seed = simSeed.toLongOrNull() ?: 42L,
                                    )
                                    output = "Seed ${res.seed} (${res.trials} trials): WinRate=${"%.1f".format(res.attackerWinRate * 100)}% | AvgTurns=${"%.1f".format(res.avgTurnsToDefeat)}"
                                },
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Run seeded game balance simulation" },
                            ) { Text("Run Seeded Combat Balance Simulator (PF-26)") }
                        }
                        PlanadayRoute.BOOK_STUDIO -> {
                            Button(
                                onClick = {
                                    val book = BookProject("b1", state.activeWorkspaceId, title = "Sample Manuscript")
                                    val chs = listOf(BookChapter("c1", "b1", state.activeWorkspaceId, 1, "Opening", manuscriptText = "Elena arrived at dawn."))
                                    val epub = ExtendedFormatInterchange.exportBookEpub(book, chs)
                                    val docx = ExtendedFormatInterchange.exportBookDocx(book, chs)
                                    output = "Generated valid ${epub.filename} (${epub.bytes.size}B) & ${docx.filename} (${docx.bytes.size}B)"
                                },
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Generate DOCX and EPUB book export" },
                            ) { Text("Generate Structured DOCX & EPUB (N-018)") }
                        }
                        PlanadayRoute.PRESENTATIONS -> {
                            Button(
                                onClick = {
                                    val preview = ExtendedFormatInterchange.previewExportFidelity("PPTX", 3, hasMacros = false)
                                    output = "Fidelity Preview (${preview.targetFormat}): Supported=${preview.supportedFeatures.joinToString()} | Notes=${preview.unsupportedOrApproximated.joinToString()}"
                                },
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Preview presentation export fidelity" },
                            ) { Text("Preview PPTX/HTML Export Fidelity (R-0455 / PF-54)") }
                        }
                        PlanadayRoute.PHOTO_STUDIO -> {
                            Button(
                                onClick = {
                                    photoRot = (photoRot + 90) % 360
                                    val asset = PhotoAsset("ph1", state.activeWorkspaceId, "a".repeat(64), "Lab Photo", createdAtUtc = SystemDomainClock.nowUtcIso())
                                    val (_, desc) = PhotoAndPlannerAdvanced.applyPhotoEditRecipe(asset, 1920, 1080, PhotoEditRecipe(rotationDeg = photoRot, brightnessDelta = 0.1))
                                    output = "Non-destructive recipe: ${desc.outputWidthPx}x${desc.outputHeightPx} rot=${desc.rotationDeg}° (originalUntouched=${desc.originalUntouched})"
                                },
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Rotate photo non-destructively" },
                            ) { Text("Rotate +90° Non-Destructive (PF-32)") }
                        }
                        PlanadayRoute.SPACED_REVIEW -> {
                            var card by remember { mutableStateOf(SpacedReviewCardState("sr1", "What denominator does sample SD/SEP use?", "n - 1 (Bessel's correction)", nextDueDateIso = SystemDomainClock.todayIso())) }
                            Text("Q: ${card.prompt}", fontWeight = FontWeight.Medium)
                            Text("A: ${card.answer}", style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = {
                                    card = LifeWellnessAndLearningEngines.reviewCardSm2(card, 5, SystemDomainClock.todayIso())
                                    output = "SM-2 Updated: interval=${card.intervalDays}d, EF=${"%.2f".format(card.easeFactor)}, nextDue=${card.nextDueDateIso}"
                                },
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Rate flashcard easy" },
                            ) { Text("Rate 5 (Easy) -> SM-2 Schedule (PF-21 / N-025)") }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    val pivots = UniversalPivotReportEngine.pivotByKindAndStatus(state.notes + state.currentRouteRecords, state.activeWorkspaceId)
                                    val autoPreview = AutomationEngine().preview(
                                        listOf(AutomationRuleDef("r1", state.activeWorkspaceId, "Follow-up", "TASK_COMPLETED", null, "LOG_JOURNAL", "Review outcome", requiresHumanApproval = true)),
                                        AutomationDomainEvent("demo_evt", state.activeWorkspaceId, "TASK_COMPLETED", "t1", RecordKind.TASK),
                                    )
                                    output = "Pivot Buckets: ${pivots.size} | Automation Dry-Run: ${autoPreview.firstOrNull()?.description ?: "None"}"
                                },
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Run pivot report and automation dry run" },
                            ) { Text("Run Pivot Report & Automation Dry-Run (N-007 / N-035)") }
                        }
                    }
                    Text(output, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ============================================================================
// 6. Household Fairness, Health/Meds, Travel Offline Packs, Media/Hobbies, People & HomeOS
//    (C-0041..C-0047, PF-18..PF-20, N-024)
// ============================================================================

@Composable
fun LifeAndHouseholdPanel(route: PlanadayRoute, state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    val fairness = remember(state.chores) { LifeWellnessAndLearningEngines.computeHouseholdFairness(state.chores) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        if (route == PlanadayRoute.HEALTH_MEDS) {
            item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Organizational personal tracking aid ONLY — NOT medical advice, clinical diagnosis, or automated dosing instruction.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }

        if (route == PlanadayRoute.HOUSEHOLD) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Household Chore Fairness Breakdown (PF-18)", fontWeight = FontWeight.SemiBold)
                        if (fairness.isEmpty()) {
                            Text("Complete chores with assigned family members to compute weighted fairness shares.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            fairness.forEach { score ->
                                Text("${score.memberId}: ${score.completedCount} done · ${score.weightedDifficultyPoints} pts (${"%.1f".format(score.sharePct)}%)")
                            }
                        }
                    }
                }
            }
        }

        if (route == PlanadayRoute.TRAVEL) {
            item {
                val samplePack = remember {
                    OfflineTravelPack(
                        tripId = "trip_demo",
                        destination = "Kyoto, Japan",
                        homeTimeZone = "America/Los_Angeles",
                        destinationTimeZone = "Asia/Tokyo",
                        departureDateIso = SystemDomainClock.todayIso(),
                        returnDateIso = CalendarDateMath.addDays(SystemDomainClock.todayIso(), 7),
                        packingChecklist = listOf("Passport" to true, "Adapter Type A" to false, "Offline Maps" to true),
                        offlineGeneratedAtUtc = SystemDomainClock.nowUtcIso(),
                    )
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Offline Travel Pack & Time Zone Card (PF-19, C-0046)", fontWeight = FontWeight.SemiBold)
                        Text("${samplePack.destination} · ${samplePack.departureDateIso} -> ${samplePack.returnDateIso}")
                        Text("Home TZ: ${samplePack.homeTimeZone} · Dest TZ: ${samplePack.destinationTimeZone}", style = MaterialTheme.typography.bodySmall)
                        Text("Checklist: ${samplePack.packingChecklist.joinToString { (k, done) -> if (done) "[x] $k" else "[ ] $k" }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add ${route.title} Item (${route.requirementRef})", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Name / Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = detail, onValueChange = { detail = it }, label = { Text("Details, rating, schedule, or notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { controller.addRecordForRoute(route, title, detail); title = ""; detail = "" },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Save ${route.title} record" },
                    ) { Text("Save ${route.shortLabel} Record") }
                }
            }
        }
        items(state.currentRouteRecords) { rec ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${rec.title} (rev ${rec.rev})", fontWeight = FontWeight.Medium)
                        Text(rec.summaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2)
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

// ============================================================================
// 7. Universal Files & PDF Review, Custom Spaces, Rituals, Insights & 54-Module Offline Guide
//    (C-0011, C-0025, C-0048..C-0050, C-0052, PF-22, PF-44, PF-49, N-005, N-013, N-017)
// ============================================================================

@Composable
fun FilesInsightsAndGuidePanel(route: PlanadayRoute, state: PlanadayUiState, controller: PlanadayWorkspaceController) {
    var guideFilter by remember { mutableStateOf("") }
    var docNote by remember { mutableStateOf("Verify method uncertainty k=2 on page 4.") }
    val annotations = remember { mutableStateListOf<com.planaday.nativeapp.core.interchange.DocumentAnnotation>() }
    val calibration = remember(state.tasks) { WhatIfScheduleAndCalibrationEngine.computeFocusCalibration(state.tasks) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        when (route) {
            PlanadayRoute.UNIVERSAL_FILES -> {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Universal Files & Structured PDF/Document Review (PF-22, N-017, C-0025)", fontWeight = FontWeight.SemiBold)
                            Text("Content-addressed SHA-256 blobs + page bookmarks + local OCR/manual excerpt annotations.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(value = docNote, onValueChange = { docNote = it }, label = { Text("Annotation comment (Page 1)") }, modifier = Modifier.fillMaxWidth())
                            Button(
                                onClick = {
                                    annotations.add(
                                        com.planaday.nativeapp.core.interchange.DocumentAnnotation(
                                            id = "ann_${annotations.size + 1}",
                                            pageNumber = 1,
                                            quotedExcerpt = "Selected document passage",
                                            comment = docNote,
                                            createdAtUtc = SystemDomainClock.nowUtcIso(),
                                        )
                                    )
                                },
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add PDF review annotation" },
                            ) { Text("Add Page Annotation (${annotations.size})") }
                            annotations.forEach { a ->
                                Text("p.${a.pageNumber}: ${a.comment}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            PlanadayRoute.HISTORY_REVIEWS, PlanadayRoute.INSIGHTS -> {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Personal Focus Calibration & Permission-Controlled Timeline (PF-39, PF-49, N-013)", fontWeight = FontWeight.SemiBold)
                            Text("Total focus sessions: ${calibration.totalSessions} (undistracted=${calibration.undistractedSessions}, distracted=${calibration.distractedSessions})")
                            Text("Total distraction minutes: ${calibration.totalDistractionMin}m · Workspace records: ${state.totalActiveRecords}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            PlanadayRoute.GUIDE -> {
                item {
                    OutlinedTextField(
                        value = guideFilter,
                        onValueChange = { guideFilter = it },
                        label = { Text("Search all 54 Planaday Native modules & shortcuts (C-0052)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val filtered = PlanadayRoute.entries.filter {
                    guideFilter.isBlank() || it.title.contains(guideFilter, true) || it.description.contains(guideFilter, true) || it.requirementRef.contains(guideFilter, true)
                }
                items(filtered) { r ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${r.title} (${r.requirementRef})", fontWeight = FontWeight.Medium)
                                Text(r.description, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(
                                onClick = { controller.selectRoute(r) },
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Open ${r.title}" },
                            ) { Text("Open") }
                        }
                    }
                }
            }
            else -> {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${route.title} — Declarative Workspace Profile & Contexts (PF-14, PF-44, N-005)", fontWeight = FontWeight.SemiBold)
                            Text("Active Workspace: ${state.activeWorkspaceId} · Theme: ${state.themeMode.displayName} · Density: ${state.density.displayName}", style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = {
                                    controller.addRecordForRoute(route, "${route.shortLabel} Profile ${SystemDomainClock.todayIso()}", "Theme=${state.themeMode.name}, Density=${state.density.name}")
                                },
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Save layout profile snapshot" },
                            ) { Text("Save Layout Profile Snapshot") }
                        }
                    }
                }
            }
        }
    }
}
