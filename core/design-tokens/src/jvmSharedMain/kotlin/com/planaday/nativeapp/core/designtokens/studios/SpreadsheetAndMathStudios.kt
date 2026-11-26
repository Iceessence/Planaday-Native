package com.planaday.nativeapp.core.designtokens.studios

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.designtokens.*
import com.planaday.nativeapp.core.domain.SystemDomainClock
import com.planaday.nativeapp.core.interchange.ExtendedFormatInterchange
import com.planaday.nativeapp.core.interchange.Rfc4180CsvCodec
import com.planaday.nativeapp.core.math.*
import com.planaday.nativeapp.core.model.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID
import kotlin.math.abs

// ============================================================================
// 1. Real Interactive Multi-Sheet Spreadsheet Application (Update 2 §14)
//    Replaces 4-cell demo completely: persisted workbooks, multi-sheet tabs,
//    scrollable cell grid, formula bar, Undo/Redo, Sort, Chart, Live Data Pull, CSV & XLSX!
// ============================================================================

@Composable
fun RealSpreadsheetStudio(
    state: PlanadayShellState,
    controller: PlanadayWorkspaceController,
    isCompact: Boolean,
) {
    val wbRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.SPREADSHEET, 60) }.collectAsState(emptyList())
    val workbooks = remember(wbRecs) {
        wbRecs.mapNotNull { rec ->
            runCatching { controller.json.decodeFromString<SpreadsheetWorkbook>(rec.payloadJson) }.getOrNull()
        }
    }
    val scope = rememberCoroutineScope()

    var activeWbId by remember { mutableStateOf<String?>(null) }
    var activeWorkbook by remember { mutableStateOf<SpreadsheetWorkbook?>(null) }
    var activeSheetIdx by remember { mutableStateOf(0) }
    var selectedCell by remember { mutableStateOf("A1") }
    var formulaBarText by remember { mutableStateOf("") }
    var newWbTitle by remember { mutableStateOf("") }
    var newSheetName by remember { mutableStateOf("") }
    var visibleRows by remember { mutableStateOf(20) }
    var showChart by remember { mutableStateOf(false) }
    var chartCol by remember { mutableStateOf("B") }
    var statusNote by remember { mutableStateOf<String?>(null) }
    val undoRedo = remember(activeWbId) { SnapshotUndoRedoStack<SpreadsheetWorkbook>(40) }

    LaunchedEffect(workbooks) {
        if (activeWbId == null && workbooks.isNotEmpty()) {
            activeWbId = workbooks.first().id
            activeWorkbook = workbooks.first()
        } else if (activeWbId != null) {
            workbooks.firstOrNull { it.id == activeWbId }?.let { fresh ->
                if (activeWorkbook == null || fresh.rev > (activeWorkbook?.rev ?: 0L)) activeWorkbook = fresh
            }
        }
    }

    fun persistWorkbook(next: SpreadsheetWorkbook, pushUndo: Boolean = true) {
        if (pushUndo) activeWorkbook?.let { undoRedo.pushCheckpoint(it) }
        activeWorkbook = next
        activeWbId = next.id
        controller.createOrUpdateRecord(
            kind = RecordKind.SPREADSHEET,
            title = next.title,
            payloadJson = controller.json.encodeToString(next),
            summaryText = "${next.sheets.size} sheet(s), ${next.sheets.sumOf { it.cells.size }} cells",
            existingId = if (workbooks.any { it.id == next.id }) next.id else null,
            recordRevision = true,
        )
    }

    val wb = activeWorkbook
    val evaluatedMap = remember(wb) {
        if (wb == null) emptyMap() else SpreadsheetFormulaEngine.evaluateWorkbook(wb.sheets)
    }
    val currentSheet = wb?.sheets?.getOrNull(activeSheetIdx.coerceIn(0, (wb.sheets.size - 1).coerceAtLeast(0)))

    LaunchedEffect(selectedCell, activeSheetIdx, wb?.id) {
        formulaBarText = currentSheet?.cells?.get(selectedCell)?.raw ?: ""
    }

    val cols = listOf("A", "B", "C", "D", "E", "F", "G", "H")

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Workbook Switcher & Create / Import Bar
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            workbooks.forEach { w ->
                FilterChip(
                    selected = w.id == wb?.id,
                    onClick = { activeWbId = w.id; activeWorkbook = w; activeSheetIdx = 0; selectedCell = "A1" },
                    label = { Text(w.title) },
                )
            }
            OutlinedTextField(value = newWbTitle, onValueChange = { newWbTitle = it }, placeholder = { Text("New workbook…") }, singleLine = true, modifier = Modifier.width(175.dp))
            Button(
                onClick = {
                    val title = newWbTitle.trim().ifEmpty { "Workbook ${workbooks.size + 1}" }
                    val fresh = SpreadsheetWorkbook(
                        id = UUID.randomUUID().toString(),
                        workspaceId = state.activeWorkspaceId,
                        title = title,
                        sheets = listOf(WorksheetModel("sheet_1", "Sheet1", rowCount = 100, colCount = 16)),
                    )
                    persistWorkbook(fresh, pushUndo = false)
                    newWbTitle = ""
                },
                modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = "Create new spreadsheet workbook" },
            ) { Text("+ Workbook") }
            OutlinedButton(
                onClick = {
                    val picked = controller.fileDialogBridge?.pickFileToRead("Import CSV or Excel (.xlsx)", listOf("xlsx", "csv"))
                    if (picked != null) {
                        val (fname, bytes) = picked
                        if (fname.endsWith(".xlsx", ignoreCase = true)) {
                            val res = ExtendedFormatInterchange.importXlsx(bytes, state.activeWorkspaceId, UUID.randomUUID().toString(), fname.substringBeforeLast('.'))
                            persistWorkbook(res.workbook, pushUndo = false)
                            statusNote = res.fidelityReport.joinToString(" · ")
                        } else {
                            val rows = Rfc4180CsvCodec.parse(bytes.decodeToString())
                            val cells = LinkedHashMap<String, SheetCellInput>()
                            rows.forEachIndexed { r, row ->
                                row.forEachIndexed { c, cellVal ->
                                    if (c < 26 && cellVal.isNotEmpty()) {
                                        val colLetter = ('A' + c).toString()
                                        cells["$colLetter${r + 1}"] = SheetCellInput(cellVal)
                                    }
                                }
                            }
                            val imported = SpreadsheetWorkbook(
                                UUID.randomUUID().toString(), state.activeWorkspaceId, fname.substringBeforeLast('.'),
                                listOf(WorksheetModel("sheet_1", "Imported", cells = cells)),
                            )
                            persistWorkbook(imported, pushUndo = false)
                            statusNote = "Imported ${rows.size} rows from CSV"
                        }
                    }
                },
                modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = "Import CSV or XLSX workbook" },
            ) { Text("Import CSV/XLSX") }
        }

        if (wb == null || currentSheet == null) {
            StudioEmptyState(
                title = "No Spreadsheets Yet",
                message = "Create your first multi-sheet workbook above or import an existing CSV or Excel (.xlsx) file.",
                actionLabel = "Create First Workbook",
                onAction = {
                    val fresh = SpreadsheetWorkbook(
                        id = UUID.randomUUID().toString(),
                        workspaceId = state.activeWorkspaceId,
                        title = "Budget & Tracker",
                        sheets = listOf(WorksheetModel("sheet_1", "Sheet1")),
                    )
                    persistWorkbook(fresh, pushUndo = false)
                },
            )
            return
        }

        // Action Toolbar: Formula Bar, Undo/Redo, Sort, Chart, Live Planaday Pull, Export
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { undoRedo.undo(wb)?.let { persistWorkbook(it, pushUndo = false) } }, enabled = undoRedo.canUndo, modifier = Modifier.heightIn(min = 40.dp)) { Text("Undo") }
            OutlinedButton(onClick = { undoRedo.redo(wb)?.let { persistWorkbook(it, pushUndo = false) } }, enabled = undoRedo.canRedo, modifier = Modifier.heightIn(min = 40.dp)) { Text("Redo") }
            OutlinedButton(
                onClick = {
                    // Sort populated rows ascending by selected cell's column!
                    val sortCol = selectedCell.takeWhile { it.isLetter() }.ifEmpty { "A" }
                    val maxR = currentSheet.cells.keys.mapNotNull { it.dropWhile { ch -> ch.isLetter() }.toIntOrNull() }.maxOrNull() ?: 1
                    val dataRows = (2..maxR).map { r ->
                        val rowMap = cols.mapNotNull { c -> currentSheet.cells["$c$r"]?.let { c to it } }.toMap()
                        val keyRaw = evaluatedMap[currentSheet.id]?.get("$sortCol$r")?.value?.displayString() ?: ""
                        Triple(r, keyRaw.toDoubleOrNull(), keyRaw to rowMap)
                    }.sortedWith(compareBy({ it.second ?: Double.MAX_VALUE }, { it.third.first }))
                    val nextCells = currentSheet.cells.filterKeys { it.dropWhile { ch -> ch.isLetter() } == "1" }.toMutableMap()
                    dataRows.forEachIndexed { idx, (_, _, pair) ->
                        val targetRow = idx + 2
                        pair.second.forEach { (c, cellIn) -> nextCells["$c$targetRow"] = cellIn }
                    }
                    val updatedSheets = wb.sheets.mapIndexed { i, s -> if (i == activeSheetIdx) s.copy(cells = nextCells) else s }
                    persistWorkbook(wb.copy(sheets = updatedSheets))
                    statusNote = "Sorted rows 2..$maxR by Column $sortCol"
                },
                modifier = Modifier.heightIn(min = 40.dp),
            ) { Text("Sort Col") }
            OutlinedButton(onClick = { showChart = !showChart }, modifier = Modifier.heightIn(min = 40.dp)) { Text(if (showChart) "Hide Chart" else "Bar Chart") }
            OutlinedButton(
                onClick = {
                    // Live pull of actual saved Expenses from SQLite into current worksheet!
                    scope.launch {
                        val expRecs = controller.repository.listByKind(state.activeWorkspaceId, RecordKind.EXPENSE, limit = 100)
                        val exps = expRecs.mapNotNull { runCatching { controller.json.decodeFromString<ExpenseItem>(it.payloadJson) }.getOrNull() }
                        val cells = currentSheet.cells.toMutableMap()
                        cells["A1"] = SheetCellInput("Date")
                        cells["B1"] = SheetCellInput("Description")
                        cells["C1"] = SheetCellInput("Category")
                        cells["D1"] = SheetCellInput("Amount")
                        exps.forEachIndexed { idx, e ->
                            val r = idx + 2
                            cells["A$r"] = SheetCellInput(e.dateIso)
                            cells["B$r"] = SheetCellInput(e.description)
                            cells["C$r"] = SheetCellInput(e.category)
                            cells["D$r"] = SheetCellInput(e.amount.value)
                        }
                        if (exps.isNotEmpty()) {
                            val totalRow = exps.size + 2
                            cells["C$totalRow"] = SheetCellInput("TOTAL")
                            cells["D$totalRow"] = SheetCellInput("=SUM(D2:D${exps.size + 1})")
                        }
                        val updatedSheets = wb.sheets.mapIndexed { i, s -> if (i == activeSheetIdx) s.copy(cells = cells) else s }
                        persistWorkbook(wb.copy(sheets = updatedSheets))
                        statusNote = "Pulled ${exps.size} live expense record(s) with =SUM total"
                    }
                },
                modifier = Modifier.heightIn(min = 40.dp),
            ) { Text("Pull Live Expenses") }
            OutlinedButton(
                onClick = {
                    val art = ExtendedFormatInterchange.exportXlsx(wb)
                    val saved = controller.fileDialogBridge?.pickFileToSave("Export Excel (.xlsx)", art.filename, art.bytes)
                    statusNote = saved?.let { "Exported XLSX to $it" } ?: "Generated ${art.filename} (${art.bytes.size} bytes)"
                },
                modifier = Modifier.heightIn(min = 40.dp),
            ) { Text("Export .xlsx") }
            OutlinedButton(
                onClick = {
                    val maxR = (currentSheet.cells.keys.mapNotNull { it.dropWhile { ch -> ch.isLetter() }.toIntOrNull() }.maxOrNull() ?: 1).coerceAtLeast(1)
                    val sheetEval = evaluatedMap[currentSheet.id].orEmpty()
                    val matrix = (1..maxR).map { r -> cols.map { c -> sheetEval["$c$r"]?.value?.displayString() ?: "" } }
                    val art = Rfc4180CsvCodec.exportCsvArtifact("${currentSheet.name}.csv", matrix)
                    val saved = controller.fileDialogBridge?.pickFileToSave("Export CSV", art.filename, art.bytes)
                    statusNote = saved?.let { "Exported CSV to $it" } ?: "Generated ${art.filename} (${art.bytes.size} bytes)"
                },
                modifier = Modifier.heightIn(min = 40.dp),
            ) { Text("Export .csv") }
            TextButton(onClick = {
                controller.moveRecordToTrash(wb.id)
                activeWbId = null
                activeWorkbook = null
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }

        // Formula Bar
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StudioStatusPill(selectedCell)
            OutlinedTextField(
                value = formulaBarText,
                onValueChange = { formulaBarText = it },
                placeholder = { Text("Value or formula (e.g. =SUM(A1:A10), =Sheet2!A1*2)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val nextCells = currentSheet.cells.toMutableMap()
                    if (formulaBarText.isBlank()) nextCells.remove(selectedCell) else nextCells[selectedCell] = SheetCellInput(formulaBarText)
                    val updatedSheets = wb.sheets.mapIndexed { i, s -> if (i == activeSheetIdx) s.copy(cells = nextCells) else s }
                    persistWorkbook(wb.copy(sheets = updatedSheets))
                },
                modifier = Modifier.heightIn(min = 50.dp).semantics { contentDescription = "Apply formula to cell $selectedCell" },
            ) { Text("Apply") }
        }

        statusNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

        // Optional Embedded Bar Chart from selected numeric column
        if (showChart) {
            val sheetEval = evaluatedMap[currentSheet.id].orEmpty()
            val colNums = (1..visibleRows).mapNotNull { r ->
                val v = sheetEval["$chartCol$r"]?.value?.displayString()?.toDoubleOrNull()
                if (v != null) "$r" to v else null
            }
            StudioCard(title = "Column $chartCol Chart (${colNums.size} values)") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cols.forEach { c -> FilterChip(selected = chartCol == c, onClick = { chartCol = c }, label = { Text("Col $c") }) }
                }
                val barColor = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))) {
                    if (colNums.isNotEmpty()) {
                        val maxV = colNums.maxOf { abs(it.second) }.coerceAtLeast(1e-6)
                        val barW = (size.width / colNums.size.coerceAtLeast(1)).coerceAtMost(44f)
                        colNums.forEachIndexed { i, (_, v) ->
                            val h = ((abs(v) / maxV) * (size.height - 16f)).toFloat()
                            drawRect(barColor, topLeft = Offset(8f + i * barW, size.height - h - 4f), size = Size((barW - 6f).coerceAtLeast(6f), h))
                        }
                    }
                }
            }
        }

        // Scrollable Interactive Cell Grid (Virtualized Rows via LazyColumn!)
        val sheetEval = evaluatedMap[currentSheet.id].orEmpty()
        val borderColor = LocalPlanadaySpacing.current.semantics.subtleBorder
        val horizScroll = rememberScrollState()
        Surface(
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, borderColor),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxSize()) {
                // Sticky Column Header Row
                Row(Modifier.fillMaxWidth().horizontalScroll(horizScroll).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                    Box(Modifier.width(44.dp).height(34.dp), contentAlignment = Alignment.Center) {
                        Text("#", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    cols.forEach { col ->
                        Box(
                            Modifier.width(104.dp).height(34.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(col, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    }
                }
                HorizontalDivider(color = borderColor)
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items((1..visibleRows).toList()) { rowNum ->
                        Row(Modifier.fillMaxWidth().horizontalScroll(horizScroll)) {
                            Box(
                                Modifier.width(44.dp).height(36.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) { Text("$rowNum", style = MaterialTheme.typography.labelSmall) }
                            cols.forEach { col ->
                                val addr = "$col$rowNum"
                                val isSel = addr == selectedCell
                                val display = sheetEval[addr]?.value?.displayString() ?: currentSheet.cells[addr]?.raw ?: ""
                                Surface(
                                    color = if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(if (isSel) 1.5.dp else 0.5.dp, if (isSel) MaterialTheme.colorScheme.primary else borderColor),
                                    modifier = Modifier.width(104.dp).height(36.dp)
                                        .clickable { selectedCell = addr }
                                        .semantics { contentDescription = "Cell $addr value $display" },
                                ) {
                                    Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                                        Text(display, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        TextButton(onClick = { visibleRows += 20 }, modifier = Modifier.fillMaxWidth()) { Text("+ Load 20 More Rows (currently $visibleRows)") }
                    }
                }
            }
        }

        // Bottom Sheet Tabs Bar (Multi-sheet support!)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            wb.sheets.forEachIndexed { idx, s ->
                FilterChip(selected = idx == activeSheetIdx, onClick = { activeSheetIdx = idx; selectedCell = "A1" }, label = { Text(s.name) })
            }
            OutlinedTextField(value = newSheetName, onValueChange = { newSheetName = it }, placeholder = { Text("New sheet…") }, singleLine = true, modifier = Modifier.width(145.dp))
            OutlinedButton(onClick = {
                val nm = newSheetName.trim().ifEmpty { "Sheet${wb.sheets.size + 1}" }
                val nextSheet = WorksheetModel("sheet_${wb.sheets.size + 1}_${UUID.randomUUID().toString().take(4)}", nm)
                persistWorkbook(wb.copy(sheets = wb.sheets + nextSheet))
                activeSheetIdx = wb.sheets.size
                newSheetName = ""
            }) { Text("+ Sheet") }
        }
    }
}

// ============================================================================
// 2. Full Mathematics, Graphing, Matrix, Statistics & 23-Category Unit Studio (§15)
// ============================================================================

@Serializable
data class SavedCalculationRecord(
    val id: String,
    val expression: String,
    val result: String,
    val angleMode: String,
    val createdAtUtc: String,
)

@Composable
fun MathematicsAndCalculatorStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    val savedRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.CALC_SAVED, 60) }.collectAsState(emptyList())
    val savedCalcs = remember(savedRecs) { savedRecs.mapNotNull { runCatching { controller.json.decodeFromString<SavedCalculationRecord>(it.payloadJson) }.getOrNull() } }

    var tab by remember { mutableStateOf(0) } // 0=Scientific, 1=2D Grapher & Calculus, 2=Matrix & Stats, 3=23 Units
    var expr by remember { mutableStateOf("") }
    var angleMode by remember { mutableStateOf(AngleMode.RAD) }
    var varA by remember { mutableStateOf("10") }
    var varB by remember { mutableStateOf("2.5") }

    val evalCtx = remember(angleMode, varA, varB) {
        MathEvalContext(
            angleMode = angleMode,
            variables = buildMap {
                varA.toDoubleOrNull()?.let { put("a", it) }
                varB.toDoubleOrNull()?.let { put("b", it) }
            },
        )
    }
    val liveResult = remember(expr, evalCtx) {
        if (expr.isBlank()) "0" else runCatching { MathAstEngine.evaluate(expr, evalCtx).toString() }.getOrElse { "Error: ${it.message}" }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StudioTabRow(listOf("Scientific & History", "2D Grapher & Calculus", "Matrix & Statistics", "23-Category Units"), tab, { tab = it })

        when (tab) {
            0 -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Scientific & Reusable Formula Calculator", subtitle = "Result: $liveResult") {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AngleMode.entries.forEach { m -> FilterChip(selected = angleMode == m, onClick = { angleMode = m }, label = { Text(m.name) }) }
                                OutlinedTextField(value = varA, onValueChange = { varA = it }, label = { Text("a =") }, singleLine = true, modifier = Modifier.width(86.dp))
                                OutlinedTextField(value = varB, onValueChange = { varB = it }, label = { Text("b =") }, singleLine = true, modifier = Modifier.width(86.dp))
                            }
                            OutlinedTextField(value = expr, onValueChange = { expr = it }, label = { Text("Enter expression (e.g. sin(pi/6) + a*b, nCr(10,3))") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            // Tactile keypad rows
                            listOf(
                                listOf("7", "8", "9", "/", "sin(", "sqrt("),
                                listOf("4", "5", "6", "*", "cos(", "ln("),
                                listOf("1", "2", "3", "-", "tan(", "^"),
                                listOf("0", ".", "(", ")", "+", "pi"),
                            ).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    row.forEach { k ->
                                        OutlinedButton(onClick = { expr += k }, modifier = Modifier.weight(1f).heightIn(min = 42.dp), contentPadding = PaddingValues(2.dp)) { Text(k) }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    if (expr.isNotBlank()) {
                                        val rec = SavedCalculationRecord(UUID.randomUUID().toString(), expr, liveResult, angleMode.name, SystemDomainClock.nowUtcIso())
                                        controller.createOrUpdateRecord(RecordKind.CALC_SAVED, "$expr = $liveResult", controller.json.encodeToString(rec), angleMode.name)
                                    }
                                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save to History") }
                                OutlinedButton(onClick = { expr = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear") }
                            }
                        }
                    }
                    item { Text("Saved Calculations (${savedCalcs.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                    items(savedCalcs, key = { it.id }) { item ->
                        OutlinedCard(onClick = { expr = item.expression }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${item.expression} = ${item.result}", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { controller.moveRecordToTrash(item.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            1 -> {
                var fx by remember { mutableStateOf("x^2 - 2") }
                var gx by remember { mutableStateOf("sin(x)") }
                var minXStr by remember { mutableStateOf("-5") }
                var maxXStr by remember { mutableStateOf("5") }
                var evalXStr by remember { mutableStateOf("1") }
                val minX = minXStr.toDoubleOrNull() ?: -5.0
                val maxX = (maxXStr.toDoubleOrNull() ?: 5.0).coerceAtLeast(minX + 0.5)
                val evalX = evalXStr.toDoubleOrNull() ?: 1.0
                val roots = remember(fx, minX, maxX) { runCatching { MathAstEngine.findNumericalRoots(fx, minX, maxX) }.getOrDefault(emptyList()) }
                val deriv = remember(fx, evalX) { runCatching { MathAstEngine.numericalDerivative(fx, evalX) }.getOrNull() }
                val integ = remember(fx, minX, maxX) { runCatching { MathAstEngine.numericalIntegral(fx, minX, maxX) }.getOrNull() }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Interactive 2D Function Grapher & Numerical Calculus") {
                            OutlinedTextField(value = fx, onValueChange = { fx = it }, label = { Text("f(x) — primary curve") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = gx, onValueChange = { gx = it }, label = { Text("g(x) — optional overlay") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = minXStr, onValueChange = { minXStr = it }, label = { Text("Min X") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = maxXStr, onValueChange = { maxXStr = it }, label = { Text("Max X") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = evalXStr, onValueChange = { evalXStr = it }, label = { Text("x₀ for f'(x₀)") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Text(
                                "Roots on [$minX, $maxX]: ${roots.joinToString { "%.4f".format(it) }.ifEmpty { "None" }}\n" +
                                    "f'($evalX) = ${deriv?.let { "%.4f".format(it) } ?: "—"} · ∫f(x)dx = ${integ?.let { "%.4f".format(it) } ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            val c1 = MaterialTheme.colorScheme.primary
                            val c2 = MaterialTheme.colorScheme.secondary
                            val err = MaterialTheme.colorScheme.error
                            val axis = MaterialTheme.colorScheme.outline
                            Canvas(Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).semantics { contentDescription = "Interactive 2D mathematical graph canvas" }) {
                                val midY = size.height / 2f
                                val zeroX = (((0.0 - minX) / (maxX - minX)) * size.width).toFloat()
                                drawLine(axis, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.5f)
                                if (zeroX in 0f..size.width) drawLine(axis, Offset(zeroX, 0f), Offset(zeroX, size.height), strokeWidth = 1.5f)
                                fun plot(formula: String, color: androidx.compose.ui.graphics.Color) {
                                    if (formula.isBlank()) return
                                    val path = Path()
                                    var started = false
                                    for (i in 0..160) {
                                        val x = minX + ((maxX - minX) * i / 160.0)
                                        val y = runCatching { MathAstEngine.evaluate(formula, MathEvalContext(variables = mapOf("x" to x))) }.getOrNull()
                                        if (y != null && y.isFinite() && abs(y) <= 20.0) {
                                            val px = (i / 160f) * size.width
                                            val py = (midY - (y.toFloat() / 6f) * (size.height / 2f)).coerceIn(2f, size.height - 2f)
                                            if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
                                        } else started = false
                                    }
                                    if (started) drawPath(path, color, style = Stroke(width = 3f))
                                }
                                plot(fx, c1)
                                plot(gx, c2)
                                roots.forEach { rx ->
                                    val px = (((rx - minX) / (maxX - minX)) * size.width).toFloat()
                                    drawCircle(err, 5.5f, Offset(px, midY))
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                var seriesInput by remember { mutableStateOf("12.1, 12.4, 12.2, 12.5, 12.3") }
                var matInput by remember { mutableStateOf("2 1; 5 3") }
                val nums = remember(seriesInput) { seriesInput.split(",", " ", "\n").mapNotNull { it.trim().toDoubleOrNull() } }
                val rsd = remember(nums) { runCatching { LabAnalyticsEngine.computeRsd(nums) }.getOrNull() }
                val det = remember(matInput) {
                    runCatching {
                        val rows = matInput.split(";").map { r -> r.trim().split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() } }.filter { it.isNotEmpty() }
                        MathAstEngine.matrixDeterminant(rows)
                    }.getOrNull()
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Descriptive Statistics & %RSD") {
                            OutlinedTextField(value = seriesInput, onValueChange = { seriesInput = it }, label = { Text("Numbers (comma or space separated)") }, modifier = Modifier.fillMaxWidth())
                            rsd?.let {
                                Text("n=${it.n} · Mean=${"%.4f".format(it.mean)} · Sample SD=${it.sampleSd?.let { s -> "%.4f".format(s) } ?: "—"} · %RSD=${it.rsdPct?.let { r -> "%.3f%%".format(r) } ?: "—"}", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    item {
                        StudioCard(title = "Square Matrix Determinant") {
                            OutlinedTextField(value = matInput, onValueChange = { matInput = it }, label = { Text("Rows separated by ';' (e.g. 2 1; 5 3)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text("det(A) = ${det?.toString() ?: "Enter a valid N×N matrix"}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            else -> {
                var catId by remember { mutableStateOf("length") }
                val cat = UnitConverter23.categories[catId] ?: UnitConverter23.categories.values.first()
                var fromUnit by remember(catId) { mutableStateOf(cat.units.first().id) }
                var toUnit by remember(catId) { mutableStateOf(cat.units.getOrElse(1) { cat.units.first() }.id) }
                var valStr by remember { mutableStateOf("1") }
                val converted = remember(catId, fromUnit, toUnit, valStr) {
                    val v = valStr.toDoubleOrNull() ?: 0.0
                    runCatching { "%.6f".format(UnitConverter23.convert(cat.id, fromUnit, toUnit, v)) }.getOrElse { "—" }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "23-Category Unit Converter (${UnitConverter23.categories.size} Categories)") {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                UnitConverter23.categories.values.forEach { c ->
                                    FilterChip(selected = c.id == catId, onClick = { catId = c.id }, label = { Text(c.displayName) })
                                }
                            }
                            OutlinedTextField(value = valStr, onValueChange = { valStr = it }, label = { Text("Input value") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text("From:", style = MaterialTheme.typography.labelMedium)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                cat.units.forEach { u -> FilterChip(selected = u.id == fromUnit, onClick = { fromUnit = u.id }, label = { Text("${u.label} (${u.symbol})") }) }
                            }
                            Text("To:", style = MaterialTheme.typography.labelMedium)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                cat.units.forEach { u -> FilterChip(selected = u.id == toUnit, onClick = { toUnit = u.id }, label = { Text("${u.label} (${u.symbol})") }) }
                            }
                            Text("$valStr $fromUnit = $converted $toUnit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
