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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.designtokens.*
import com.planaday.nativeapp.core.graphics.*
import com.planaday.nativeapp.core.interchange.ArchiveInterchange
import com.planaday.nativeapp.core.interchange.ExtendedFormatInterchange
import com.planaday.nativeapp.core.interchange.RichDocumentExporters
import com.planaday.nativeapp.core.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID

// ============================================================================
// 1. Complete Interactive Physical Digital Planner (Update 2 §12)
//    Multiple persistent books, covers, bindings, pages, side tabs, stickers,
//    draggable/rotatable decorations, freehand ink, Undo/Redo, & live linked Tasks!
// ============================================================================

@Serializable
data class PersistedPlannerBookBundle(
    val book: PlannerBook,
    val pages: List<PlannerPage>,
    val decorByPageId: Map<String, List<DecorItem>> = emptyMap(),
    val visitedUsStates: List<String> = emptyList(),
)

@Composable
fun RealPhysicalPlannerStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController, isCompact: Boolean) {
    val bookRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.PLANNER_BOOK, 30) }.collectAsState(emptyList())
    val taskRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.TASK, 30) }.collectAsState(emptyList())
    val books = remember(bookRecs) { bookRecs.mapNotNull { runCatching { controller.json.decodeFromString<PersistedPlannerBookBundle>(it.payloadJson) }.getOrNull() } }
    val liveTasks = remember(taskRecs) { taskRecs.mapNotNull { runCatching { controller.json.decodeFromString<TaskItem>(it.payloadJson) }.getOrNull() } }

    var activeBundle by remember { mutableStateOf<PersistedPlannerBookBundle?>(null) }
    var pageIdx by remember { mutableStateOf(0) }
    var selectedDecorId by remember { mutableStateOf<String?>(null) }
    var stickerCat by remember { mutableStateOf("Planning") }
    var newBookTitle by remember { mutableStateOf("") }
    val undoRedo = remember(activeBundle?.book?.id) { SnapshotUndoRedoStack<PersistedPlannerBookBundle>(30) }

    LaunchedEffect(books) {
        if (activeBundle == null && books.isNotEmpty()) activeBundle = books.first()
        else if (activeBundle != null) books.firstOrNull { it.book.id == activeBundle?.book?.id }?.let { activeBundle = it }
    }

    fun saveBundle(next: PersistedPlannerBookBundle, pushUndo: Boolean = true) {
        if (pushUndo) activeBundle?.let { undoRedo.pushCheckpoint(it) }
        activeBundle = next
        controller.createOrUpdateRecord(
            kind = RecordKind.PLANNER_BOOK,
            title = next.book.title,
            payloadJson = controller.json.encodeToString(next),
            summaryText = "${next.pages.size} pages · ${next.book.bindingStyle.name}",
            existingId = if (books.any { it.book.id == next.book.id }) next.book.id else null,
        )
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Book Switcher & Create
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            books.forEach { b ->
                FilterChip(selected = b.book.id == activeBundle?.book?.id, onClick = { activeBundle = b; pageIdx = 0; selectedDecorId = null }, label = { Text(b.book.title) })
            }
            OutlinedTextField(value = newBookTitle, onValueChange = { newBookTitle = it }, placeholder = { Text("New planner book…") }, singleLine = true, modifier = Modifier.width(195.dp))
            Button(onClick = {
                val bid = UUID.randomUUID().toString()
                val title = newBookTitle.trim().ifEmpty { "My Planner ${books.size + 1}" }
                val bundle = PersistedPlannerBookBundle(
                    book = PlannerBook(bid, state.activeWorkspaceId, title, "linen_amber", "linen_amber", PlannerBindingStyle.SPIRAL, twoPageSpread = true),
                    pages = listOf(
                        PlannerPage(UUID.randomUUID().toString(), bid, state.activeWorkspaceId, 1, "Daily Spread", "lined", sideTabLabel = "Today"),
                        PlannerPage(UUID.randomUUID().toString(), bid, state.activeWorkspaceId, 2, "Notes & Grid", "grid", sideTabLabel = "Notes"),
                        PlannerPage(UUID.randomUUID().toString(), bid, state.activeWorkspaceId, 3, "US Travel Map", "us_50_states_map", sideTabLabel = "States"),
                    ),
                )
                saveBundle(bundle, pushUndo = false)
                newBookTitle = ""
            }, modifier = Modifier.heightIn(min = 44.dp)) { Text("+ New Book") }
        }

        val bundle = activeBundle
        if (bundle == null || bundle.pages.isEmpty()) {
            StudioEmptyState(
                title = "Create Your Digital Planner",
                message = "Customize covers, bindings, lined/grid/dot spreads, stickers, sticky notes, and live linked tasks.",
                actionLabel = "Create First Planner Book",
                onAction = {
                    val bid = UUID.randomUUID().toString()
                    saveBundle(
                        PersistedPlannerBookBundle(
                            book = PlannerBook(bid, state.activeWorkspaceId, "2026 Personal Planner"),
                            pages = listOf(PlannerPage(UUID.randomUUID().toString(), bid, state.activeWorkspaceId, 1, "Daily Spread", "lined", sideTabLabel = "Daily")),
                        ),
                        pushUndo = false,
                    )
                },
            )
            return
        }

        val safeIdx = pageIdx.coerceIn(0, bundle.pages.lastIndex)
        val page = bundle.pages[safeIdx]
        val pageDecor = bundle.decorByPageId[page.id].orEmpty()

        // Book & Page Controls
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { if (safeIdx > 0) pageIdx = safeIdx - 1 }) { Text("‹ Prev") }
            Text("Page ${page.pageNumber}/${bundle.pages.size}: ${page.title}", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { if (safeIdx < bundle.pages.lastIndex) pageIdx = safeIdx + 1 }) { Text("Next ›") }
            OutlinedButton(onClick = { undoRedo.undo(bundle)?.let { saveBundle(it, pushUndo = false) } }, enabled = undoRedo.canUndo) { Text("Undo") }
            OutlinedButton(onClick = { undoRedo.redo(bundle)?.let { saveBundle(it, pushUndo = false) } }, enabled = undoRedo.canRedo) { Text("Redo") }
            OutlinedButton(onClick = {
                val np = PlannerPage(UUID.randomUUID().toString(), bundle.book.id, state.activeWorkspaceId, bundle.pages.size + 1, "Page ${bundle.pages.size + 1}", "lined")
                saveBundle(bundle.copy(pages = bundle.pages + np))
                pageIdx = bundle.pages.size
            }) { Text("+ Page") }
            OutlinedButton(onClick = {
                val dup = PhotoAndPlannerAdvanced.duplicatePage(bundle.pages, page.id, UUID.randomUUID().toString())
                saveBundle(bundle.copy(pages = dup))
            }) { Text("Duplicate") }
            listOf("lined", "grid", "dot", "blank", "us_50_states_map").forEach { tmpl ->
                FilterChip(
                    selected = page.layoutTemplate == tmpl,
                    onClick = {
                        val updPages = bundle.pages.mapIndexed { i, p -> if (i == safeIdx) p.copy(layoutTemplate = tmpl) else p }
                        saveBundle(bundle.copy(pages = updPages))
                    },
                    label = { Text(if (tmpl == "us_50_states_map") "50-State Map" else tmpl.replaceFirstChar { it.uppercase() }) },
                )
            }
            PlannerBindingStyle.entries.forEach { bs ->
                FilterChip(
                    selected = bundle.book.bindingStyle == bs,
                    onClick = { saveBundle(bundle.copy(book = bundle.book.copy(bindingStyle = bs))) },
                    label = { Text(bs.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        // Dynamic Side Tabs Strip
        val sideTabs = bundle.pages.mapIndexedNotNull { idx, p -> p.sideTabLabel?.let { idx to it } }
        if (sideTabs.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sideTabs.forEach { (idx, lbl) ->
                    AssistChip(onClick = { pageIdx = idx }, label = { Text("Tab: $lbl") })
                }
            }
        }

        // Page Workspace Body
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            // Interactive Draggable Decoration & Paper Canvas
            item {
                StudioCard(
                    title = "${page.title} (${bundle.book.bindingStyle.name.lowercase()} binding)",
                    headerAction = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = {
                                val item = DecorItem(UUID.randomUUID().toString(), state.activeWorkspaceId, page.id, "sticky_note", x = 30.0 + pageDecor.size * 18, y = 24.0 + pageDecor.size * 12, width = 120.0, height = 68.0, text = "Sticky #${pageDecor.size + 1}", colorHex = "#FEF08A")
                                saveBundle(bundle.copy(decorByPageId = bundle.decorByPageId + (page.id to (pageDecor + item))))
                                selectedDecorId = item.id
                            }) { Text("+ Sticky") }
                            OutlinedButton(onClick = {
                                val stk = PhotoAndPlannerAdvanced.BUILT_IN_STICKER_CATALOG.first { it.category == stickerCat }
                                val item = DecorItem(UUID.randomUUID().toString(), state.activeWorkspaceId, page.id, "sticker", x = 140.0, y = 40.0, width = 96.0, height = 44.0, stickerKey = stk.id, text = stk.label, colorHex = stk.accentHex)
                                saveBundle(bundle.copy(decorByPageId = bundle.decorByPageId + (page.id to (pageDecor + item))))
                                selectedDecorId = item.id
                            }) { Text("+ Sticker") }
                        }
                    },
                ) {
                    val ruleCol = LocalPlanadaySpacing.current.paperRuleColor
                    Box(
                        Modifier.fillMaxWidth().height(230.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            // Draw paper lines/grid/dots + spiral binding rings
                            when (page.layoutTemplate) {
                                "lined" -> {
                                    var y = 28f
                                    while (y < size.height) { drawLine(ruleCol, Offset(24f, y), Offset(size.width - 8f, y), 1.5f); y += 26f }
                                }
                                "grid" -> {
                                    var x = 24f; while (x < size.width) { drawLine(ruleCol, Offset(x, 0f), Offset(x, size.height), 1f); x += 24f }
                                    var y = 24f; while (y < size.height) { drawLine(ruleCol, Offset(24f, y), Offset(size.width, y), 1f); y += 24f }
                                }
                                "dot" -> {
                                    var y = 20f; while (y < size.height) {
                                        var x = 28f; while (x < size.width) { drawCircle(ruleCol, 2f, Offset(x, y)); x += 24f }
                                        y += 24f
                                    }
                                }
                            }
                            if (bundle.book.bindingStyle == PlannerBindingStyle.SPIRAL || bundle.book.bindingStyle == PlannerBindingStyle.RING) {
                                var y = 18f
                                while (y < size.height - 10f) {
                                    drawCircle(Color.Gray, 5f, Offset(12f, y), style = Stroke(2f))
                                    y += 22f
                                }
                            }
                        }

                        // Draggable, Selectable, Rotatable Stickers & Sticky Notes!
                        pageDecor.forEach { d ->
                            val isSel = d.id == selectedDecorId
                            var dx by remember(d.id, d.x) { mutableStateOf(0f) }
                            var dy by remember(d.id, d.y) { mutableStateOf(0f) }
                            val bg = parseHexColorOrNull(d.colorHex)?.copy(alpha = 0.28f) ?: MaterialTheme.colorScheme.primaryContainer
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = bg,
                                border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.Gray),
                                modifier = Modifier
                                    .offset(x = (d.x.toFloat() + dx / 2f).dp.coerceAtLeast(24.dp), y = (d.y.toFloat() + dy / 2f).dp.coerceAtLeast(4.dp))
                                    .width(d.width.dp)
                                    .height(d.height.dp)
                                    .graphicsLayer { rotationZ = d.rotationDeg.toFloat() }
                                    .clickable { selectedDecorId = d.id }
                                    .pointerInput(d.id, d.x, d.y) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                val moved = d.copy(x = (d.x + dx / 2.0).coerceIn(24.0, 420.0), y = (d.y + dy / 2.0).coerceIn(4.0, 175.0))
                                                dx = 0f; dy = 0f
                                                val nextList = pageDecor.map { if (it.id == d.id) moved else it }
                                                saveBundle(bundle.copy(decorByPageId = bundle.decorByPageId + (page.id to nextList)))
                                            },
                                        ) { change, amt -> change.consume(); dx += amt.x; dy += amt.y }
                                    },
                            ) {
                                Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                                    Text(d.text ?: d.kind, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 2)
                                }
                            }
                        }
                    }

                    // Selected Decoration Controls (Rotate, Edit Text, Delete)
                    pageDecor.firstOrNull { it.id == selectedDecorId }?.let { sel ->
                        var editTxt by remember(sel.id) { mutableStateOf(sel.text ?: "") }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = editTxt, onValueChange = { editTxt = it }, label = { Text("Selected label") }, singleLine = true, modifier = Modifier.width(170.dp))
                            OutlinedButton(onClick = {
                                val next = pageDecor.map { if (it.id == sel.id) it.copy(text = editTxt) else it }
                                saveBundle(bundle.copy(decorByPageId = bundle.decorByPageId + (page.id to next)))
                            }) { Text("Apply") }
                            OutlinedButton(onClick = {
                                val next = pageDecor.map { if (it.id == sel.id) it.copy(rotationDeg = (it.rotationDeg + 15.0) % 360.0) else it }
                                saveBundle(bundle.copy(decorByPageId = bundle.decorByPageId + (page.id to next)))
                            }) { Text("Rotate +15°") }
                            TextButton(onClick = {
                                saveBundle(bundle.copy(decorByPageId = bundle.decorByPageId + (page.id to pageDecor.filterNot { it.id == sel.id })))
                                selectedDecorId = null
                            }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        }
                    }

                    // Page Notes Content (Persisted!)
                    var pageNotes by remember(page.id) { mutableStateOf(page.textContent) }
                    OutlinedTextField(
                        value = pageNotes,
                        onValueChange = { pageNotes = it },
                        label = { Text("Page text & journal notes") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        val updPages = bundle.pages.mapIndexed { i, p -> if (i == safeIdx) p.copy(textContent = pageNotes) else p }
                        saveBundle(bundle.copy(pages = updPages))
                    }) { Text("Save Page Notes") }
                }
            }

            // Sticker Category Browser (260 stickers)
            item {
                val cats = remember { PhotoAndPlannerAdvanced.BUILT_IN_STICKER_CATALOG.map { it.category }.distinct() }
                StudioCard(title = "Sticker Pack (260 Vector Stickers)") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cats.forEach { c -> FilterChip(selected = stickerCat == c, onClick = { stickerCat = c }, label = { Text(c) }) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PhotoAndPlannerAdvanced.BUILT_IN_STICKER_CATALOG.filter { it.category == stickerCat }.take(12).forEach { stk ->
                            AssistChip(
                                onClick = {
                                    val item = DecorItem(UUID.randomUUID().toString(), state.activeWorkspaceId, page.id, "sticker", x = 50.0 + pageDecor.size * 14, y = 30.0, width = 96.0, height = 42.0, stickerKey = stk.id, text = stk.label, colorHex = stk.accentHex)
                                    saveBundle(bundle.copy(decorByPageId = bundle.decorByPageId + (page.id to (pageDecor + item))))
                                    selectedDecorId = item.id
                                },
                                label = { Text(stk.label) },
                            )
                        }
                    }
                }
            }

            // Interactive 50-State US Map (Persisted on Book!)
            if (page.layoutTemplate == "us_50_states_map") {
                item {
                    StudioCard(title = "US 50-State Travel Map (${bundle.visitedUsStates.size}/50 Visited)") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PhysicalPlannerEngine.US_50_STATES.forEach { (code, name) ->
                                val visited = code in bundle.visitedUsStates
                                FilterChip(
                                    selected = visited,
                                    onClick = {
                                        val next = if (visited) bundle.visitedUsStates - code else bundle.visitedUsStates + code
                                        saveBundle(bundle.copy(visitedUsStates = next))
                                    },
                                    label = { Text("$code · $name") },
                                )
                            }
                        }
                    }
                }
            }

            // Live Linked Planaday Tasks directly inside Planner Spread (zero duplication!)
            item {
                StudioCard(title = "Linked Live Tasks (${liveTasks.count { it.status == RecordStatus.ACTIVE }} open)", subtitle = "Checking off updates your canonical Task everywhere") {
                    if (liveTasks.isEmpty()) {
                        Text("Create tasks in the Tasks view to see them live on your planner spread.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        liveTasks.take(6).forEach { t ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(t.title, modifier = Modifier.weight(1f))
                                TextButton(onClick = { controller.moveTaskColumn(t.id, if (t.status == RecordStatus.DONE) "next" else "done") }) {
                                    Text(if (t.status == RecordStatus.DONE) "Completed ✓" else "Complete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. Real Visual Presentations Studio & Presenter Mode (Update 2 §16)
// ============================================================================

@Composable
fun RealPresentationsStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController, isCompact: Boolean) {
    val deckRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.PRESENTATION, 40) }.collectAsState(emptyList())
    val decks = remember(deckRecs) { deckRecs.mapNotNull { runCatching { controller.json.decodeFromString<PresentationDeck>(it.payloadJson) }.getOrNull() } }

    var activeDeck by remember { mutableStateOf<PresentationDeck?>(null) }
    var slideIdx by remember { mutableStateOf(0) }
    var selectedObjId by remember { mutableStateOf<String?>(null) }
    var presenterMode by remember { mutableStateOf(false) }
    var newDeckTitle by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    val undoRedo = remember(activeDeck?.id) { SnapshotUndoRedoStack<PresentationDeck>(30) }

    LaunchedEffect(decks) {
        if (activeDeck == null && decks.isNotEmpty()) activeDeck = decks.first()
        else if (activeDeck != null) decks.firstOrNull { it.id == activeDeck?.id }?.let { activeDeck = it }
    }

    fun saveDeck(next: PresentationDeck, pushUndo: Boolean = true) {
        if (pushUndo) activeDeck?.let { undoRedo.pushCheckpoint(it) }
        activeDeck = next
        controller.createOrUpdateRecord(
            kind = RecordKind.PRESENTATION,
            title = next.title,
            payloadJson = controller.json.encodeToString(next),
            summaryText = "${next.slides.size} slides · ${next.aspectRatio}",
            existingId = if (decks.any { it.id == next.id }) next.id else null,
            recordRevision = true,
        )
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Deck Bar + Create / Import PPTX
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            decks.forEach { d -> FilterChip(selected = d.id == activeDeck?.id, onClick = { activeDeck = d; slideIdx = 0; selectedObjId = null }, label = { Text(d.title) }) }
            OutlinedTextField(value = newDeckTitle, onValueChange = { newDeckTitle = it }, label = { Text("New presentation") }, singleLine = true, modifier = Modifier.width(165.dp))
            Button(onClick = {
                val id = UUID.randomUUID().toString()
                val title = newDeckTitle.trim().ifEmpty { "Presentation ${decks.size + 1}" }
                val deck = PresentationDeck(
                    id, state.activeWorkspaceId, title, "16:9", "clean_editorial",
                    slides = listOf(
                        SlideModel(
                            UUID.randomUUID().toString(), 1, "Title Slide", "fade", "Welcome opening remarks",
                            objects = listOf(SlideObject(UUID.randomUUID().toString(), SlideObjectKind.TEXT, 10.0, 25.0, 80.0, 30.0, text = title)),
                        )
                    ),
                )
                saveDeck(deck, pushUndo = false)
                newDeckTitle = ""
            }, modifier = Modifier.heightIn(min = 44.dp)) { Text("+ Deck") }
            OutlinedButton(onClick = {
                val picked = controller.fileDialogBridge?.pickFileToRead("Import PowerPoint (.pptx)", listOf("pptx"))
                if (picked != null) {
                    val res = ArchiveInterchange.importPptx(picked.second, state.activeWorkspaceId, UUID.randomUUID().toString(), picked.first.substringBeforeLast('.'))
                    saveDeck(res.deck, pushUndo = false)
                    statusMsg = "Imported ${res.deck.slides.size} slides (${res.fidelityReport.joinToString()})"
                }
            }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Import .pptx") }
        }

        val deck = activeDeck
        if (deck == null || deck.slides.isEmpty()) {
            StudioEmptyState(
                title = "No Presentations Yet",
                message = "Create a slide deck with draggable text boxes, shapes, code blocks, speaker notes, presenter view, and PowerPoint/HTML export.",
                actionLabel = "Create Presentation",
                onAction = {
                    saveDeck(
                        PresentationDeck(
                            UUID.randomUUID().toString(), state.activeWorkspaceId, "Project Update",
                            slides = listOf(SlideModel(UUID.randomUUID().toString(), 1, "Cover", objects = listOf(SlideObject(UUID.randomUUID().toString(), SlideObjectKind.TEXT, 10.0, 20.0, 80.0, 28.0, text = "Project Update")))),
                        ),
                        pushUndo = false,
                    )
                },
            )
            return
        }

        val safeIdx = slideIdx.coerceIn(0, deck.slides.lastIndex)
        val slide = deck.slides[safeIdx]

        // Slide Editor Toolbar
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { presenterMode = true }, modifier = Modifier.heightIn(min = 40.dp)) { Text("Present ▶") }
            OutlinedButton(onClick = { undoRedo.undo(deck)?.let { saveDeck(it, pushUndo = false) } }, enabled = undoRedo.canUndo) { Text("Undo") }
            OutlinedButton(onClick = { undoRedo.redo(deck)?.let { saveDeck(it, pushUndo = false) } }, enabled = undoRedo.canRedo) { Text("Redo") }
            OutlinedButton(onClick = {
                val ns = SlideModel(UUID.randomUUID().toString(), deck.slides.size + 1, "Slide ${deck.slides.size + 1}")
                saveDeck(deck.copy(slides = deck.slides + ns))
                slideIdx = deck.slides.size
            }) { Text("+ Slide") }
            OutlinedButton(onClick = {
                val copy = slide.copy(id = UUID.randomUUID().toString(), order = deck.slides.size + 1, title = "${slide.title} (Copy)")
                saveDeck(deck.copy(slides = deck.slides + copy))
            }) { Text("Duplicate") }
            OutlinedButton(onClick = {
                val obj = SlideObject(UUID.randomUUID().toString(), SlideObjectKind.TEXT, 12.0, 18.0 + slide.objects.size * 10, 55.0, 22.0, text = "New Text Block")
                val upd = deck.slides.mapIndexed { i, s -> if (i == safeIdx) s.copy(objects = s.objects + obj) else s }
                saveDeck(deck.copy(slides = upd))
                selectedObjId = obj.id
            }) { Text("+ Text") }
            OutlinedButton(onClick = {
                val obj = SlideObject(UUID.randomUUID().toString(), SlideObjectKind.SHAPE, 25.0, 30.0, 35.0, 24.0, text = "Callout Shape", bgColorHex = "#DBEAFE")
                val upd = deck.slides.mapIndexed { i, s -> if (i == safeIdx) s.copy(objects = s.objects + obj) else s }
                saveDeck(deck.copy(slides = upd))
                selectedObjId = obj.id
            }) { Text("+ Shape") }
            OutlinedButton(onClick = {
                val art = ArchiveInterchange.exportMinimalPptx(deck)
                val saved = controller.fileDialogBridge?.pickFileToSave("Export PowerPoint (.pptx)", art.filename, art.bytes)
                val fid = ExtendedFormatInterchange.previewExportFidelity("PPTX", deck.slides.size)
                statusMsg = (saved?.let { "Saved PPTX to $it" } ?: "Generated ${art.filename} (${art.bytes.size} B)") + " · ${fid.unsupportedOrApproximated.joinToString()}"
            }) { Text("Export .pptx") }
            OutlinedButton(onClick = {
                val art = RichDocumentExporters.exportPresentationHtmlDeck(deck)
                val saved = controller.fileDialogBridge?.pickFileToSave("Export HTML Slides", art.filename, art.bytes)
                statusMsg = saved?.let { "Saved HTML deck to $it" } ?: "Generated ${art.filename} (${art.bytes.size} B)"
            }) { Text("Export HTML") }
        }

        statusMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

        // Slide Thumbnail Strip
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            deck.slides.forEachIndexed { idx, s ->
                FilterChip(selected = idx == safeIdx, onClick = { slideIdx = idx; selectedObjId = null }, label = { Text("${idx + 1}. ${s.title.ifBlank { "Slide ${idx + 1}" }}") })
            }
        }

        // Interactive Visual Slide Canvas (Drag objects to reposition!)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            item {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(250.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                ) {
                    val canvasW = maxWidth
                    val canvasH = maxHeight
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, LocalPlanadaySpacing.current.semantics.subtleBorder),
                        color = parseHexColorOrNull(slide.backgroundHex) ?: Color.White,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            slide.objects.forEach { obj ->
                                val isSel = obj.id == selectedObjId
                                var dx by remember(obj.id, obj.xPct) { mutableStateOf(0f) }
                                var dy by remember(obj.id, obj.yPct) { mutableStateOf(0f) }
                                val bg = parseHexColorOrNull(obj.bgColorHex) ?: if (obj.kind == SlideObjectKind.SHAPE) Color(0xFFE0F2FE) else Color.Transparent
                                Surface(
                                    color = bg,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.LightGray),
                                    modifier = Modifier
                                        .offset(
                                            x = (canvasW * (obj.xPct.toFloat() / 100f) + (dx / 2f).dp).coerceIn(0.dp, canvasW - 60.dp),
                                            y = (canvasH * (obj.yPct.toFloat() / 100f) + (dy / 2f).dp).coerceIn(0.dp, canvasH - 36.dp),
                                        )
                                        .width((canvasW * (obj.widthPct.toFloat() / 100f)).coerceAtLeast(80.dp))
                                        .height((canvasH * (obj.heightPct.toFloat() / 100f)).coerceAtLeast(42.dp))
                                        .graphicsLayer { rotationZ = obj.rotationDeg.toFloat() }
                                        .clickable { selectedObjId = obj.id }
                                        .pointerInput(obj.id, obj.xPct, obj.yPct) {
                                            detectDragGestures(
                                                onDragEnd = {
                                                    val newXPct = (obj.xPct + (dx / 2.0) / canvasW.value * 100.0).coerceIn(0.0, 85.0)
                                                    val newYPct = (obj.yPct + (dy / 2.0) / canvasH.value * 100.0).coerceIn(0.0, 85.0)
                                                    dx = 0f; dy = 0f
                                                    val nextObjs = slide.objects.map { if (it.id == obj.id) it.copy(xPct = newXPct, yPct = newYPct) else it }
                                                    saveDeck(deck.copy(slides = deck.slides.mapIndexed { i, s -> if (i == safeIdx) s.copy(objects = nextObjs) else s }))
                                                },
                                            ) { change, amt -> change.consume(); dx += amt.x; dy += amt.y }
                                        },
                                ) {
                                    Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.CenterStart) {
                                        Text(obj.text, color = Color(0xFF1E293B), style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected Object Property Inspector
            slide.objects.firstOrNull { it.id == selectedObjId }?.let { sel ->
                item {
                    var txt by remember(sel.id) { mutableStateOf(sel.text) }
                    StudioCard(title = "Edit Selected ${sel.kind.name}") {
                        OutlinedTextField(value = txt, onValueChange = { txt = it }, label = { Text("Content") }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val nextObjs = slide.objects.map { if (it.id == sel.id) it.copy(text = txt) else it }
                                saveDeck(deck.copy(slides = deck.slides.mapIndexed { i, s -> if (i == safeIdx) s.copy(objects = nextObjs) else s }))
                            }) { Text("Update") }
                            OutlinedButton(onClick = {
                                val nextObjs = slide.objects.map { if (it.id == sel.id) it.copy(rotationDeg = (it.rotationDeg + 15.0) % 360.0) else it }
                                saveDeck(deck.copy(slides = deck.slides.mapIndexed { i, s -> if (i == safeIdx) s.copy(objects = nextObjs) else s }))
                            }) { Text("Rotate +15°") }
                            TextButton(onClick = {
                                val nextObjs = slide.objects.filterNot { it.id == sel.id }
                                saveDeck(deck.copy(slides = deck.slides.mapIndexed { i, s -> if (i == safeIdx) s.copy(objects = nextObjs) else s }))
                                selectedObjId = null
                            }) { Text("Delete Object", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }

            // Slide Title, Transition & Speaker Notes
            item {
                var sTitle by remember(slide.id) { mutableStateOf(slide.title) }
                var sNotes by remember(slide.id) { mutableStateOf(slide.speakerNotes) }
                StudioCard(title = "Slide Details & Speaker Notes") {
                    OutlinedTextField(value = sTitle, onValueChange = { sTitle = it }, label = { Text("Slide Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sNotes, onValueChange = { sNotes = it }, label = { Text("Speaker Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        saveDeck(deck.copy(slides = deck.slides.mapIndexed { i, s -> if (i == safeIdx) s.copy(title = sTitle, speakerNotes = sNotes) else s }))
                    }) { Text("Save Slide Details") }
                }
            }
        }
    }

    // Presenter Mode Dialog
    if (presenterMode && activeDeck != null) {
        val deck = activeDeck!!
        val slide = deck.slides[slideIdx.coerceIn(0, deck.slides.lastIndex)]
        AlertDialog(
            onDismissRequest = { presenterMode = false },
            title = { Text("Presenter View — Slide ${slideIdx + 1}/${deck.slides.size}: ${slide.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = Color(0xFF0F172A), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(180.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slide.objects.forEach { obj -> Text(obj.text, color = Color.White, style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                    Text("Speaker Notes:", fontWeight = FontWeight.Bold)
                    Text(slide.speakerNotes.ifBlank { "(No speaker notes)" }, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { if (slideIdx > 0) slideIdx-- }, enabled = slideIdx > 0) { Text("Prev") }
                    Button(onClick = { if (slideIdx < deck.slides.lastIndex) slideIdx++ }, enabled = slideIdx < deck.slides.lastIndex) { Text("Next") }
                    TextButton(onClick = { presenterMode = false }) { Text("Exit") }
                }
            },
        )
    }
}

// ============================================================================
// 3. Real Persistent Interactive Whiteboard (Update 2 §21)
// ============================================================================

@Serializable
data class PersistedWhiteboard(
    val id: String,
    val title: String,
    val nodes: List<PersistedCanvasNode> = emptyList(),
    val connectors: List<PersistedConnector> = emptyList(),
)

@Serializable
data class PersistedCanvasNode(
    val id: String,
    val kind: String,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    val text: String,
    val colorHex: String = "#FEF08A",
    val linkedRecordId: String? = null,
)

@Serializable
data class PersistedConnector(val id: String, val fromId: String, val toId: String)

@Composable
fun RealWhiteboardStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    val boardRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.WHITEBOARD, 30) }.collectAsState(emptyList())
    val boards = remember(boardRecs) { boardRecs.mapNotNull { runCatching { controller.json.decodeFromString<PersistedWhiteboard>(it.payloadJson) }.getOrNull() } }

    var activeBoard by remember { mutableStateOf<PersistedWhiteboard?>(null) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var connectFromId by remember { mutableStateOf<String?>(null) }
    var newBoardName by remember { mutableStateOf("") }
    var svgMsg by remember { mutableStateOf<String?>(null) }
    val undoRedo = remember(activeBoard?.id) { SnapshotUndoRedoStack<PersistedWhiteboard>(30) }

    LaunchedEffect(boards) {
        if (activeBoard == null && boards.isNotEmpty()) activeBoard = boards.first()
        else if (activeBoard != null) boards.firstOrNull { it.id == activeBoard?.id }?.let { activeBoard = it }
    }

    fun saveBoard(next: PersistedWhiteboard, pushUndo: Boolean = true) {
        if (pushUndo) activeBoard?.let { undoRedo.pushCheckpoint(it) }
        activeBoard = next
        controller.createOrUpdateRecord(
            RecordKind.WHITEBOARD, next.title, controller.json.encodeToString(next),
            "${next.nodes.size} objects, ${next.connectors.size} connectors",
            existingId = if (boards.any { it.id == next.id }) next.id else null,
        )
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            boards.forEach { b -> FilterChip(selected = b.id == activeBoard?.id, onClick = { activeBoard = b; selectedNodeId = null }, label = { Text(b.title) }) }
            OutlinedTextField(value = newBoardName, onValueChange = { newBoardName = it }, label = { Text("New board") }, singleLine = true, modifier = Modifier.width(150.dp))
            Button(onClick = {
                val b = PersistedWhiteboard(UUID.randomUUID().toString(), newBoardName.trim().ifEmpty { "Board ${boards.size + 1}" })
                saveBoard(b, pushUndo = false)
                newBoardName = ""
            }) { Text("+ Board") }
        }

        val board = activeBoard
        if (board == null) {
            StudioEmptyState(
                title = "No Whiteboards Yet",
                message = "Create a persistent visual whiteboard for sticky notes, shapes, connectors, diagrams, and SVG export.",
                actionLabel = "Create First Whiteboard",
                onAction = { saveBoard(PersistedWhiteboard(UUID.randomUUID().toString(), "Main Whiteboard"), pushUndo = false) },
            )
            return
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                val n = PersistedCanvasNode(UUID.randomUUID().toString(), "sticky", 24.0 + (board.nodes.size * 22) % 240, 24.0 + (board.nodes.size * 18) % 140, 124.0, 68.0, "Idea #${board.nodes.size + 1}", "#FEF08A")
                saveBoard(board.copy(nodes = board.nodes + n))
                selectedNodeId = n.id
            }) { Text("+ Sticky") }
            OutlinedButton(onClick = {
                val n = PersistedCanvasNode(UUID.randomUUID().toString(), "shape", 140.0, 60.0, 130.0, 62.0, "Process Step", "#DBEAFE")
                saveBoard(board.copy(nodes = board.nodes + n))
                selectedNodeId = n.id
            }) { Text("+ Shape") }
            OutlinedButton(
                onClick = {
                    val sel = selectedNodeId
                    if (sel != null) {
                        if (connectFromId == null) connectFromId = sel
                        else if (connectFromId != sel) {
                            saveBoard(board.copy(connectors = board.connectors + PersistedConnector(UUID.randomUUID().toString(), connectFromId!!, sel)))
                            connectFromId = null
                        }
                    }
                },
                enabled = selectedNodeId != null,
            ) { Text(if (connectFromId == null) "Connect From Selected" else "Click Target & Connect") }
            OutlinedButton(onClick = { undoRedo.undo(board)?.let { saveBoard(it, pushUndo = false) } }, enabled = undoRedo.canUndo) { Text("Undo") }
            OutlinedButton(onClick = { undoRedo.redo(board)?.let { saveBoard(it, pushUndo = false) } }, enabled = undoRedo.canRedo) { Text("Redo") }
            OutlinedButton(onClick = {
                val svg = VectorSvgExporter.exportWhiteboardSvg(
                    board.title,
                    board.nodes.mapIndexed { idx, n -> CanvasNode(n.id, n.kind, Bounds2D(n.x, n.y, n.w, n.h), zIndex = idx, text = n.text) },
                    board.connectors.map { CanvasConnector(it.id, it.fromId, it.toId) },
                )
                val saved = controller.fileDialogBridge?.pickFileToSave("Export Whiteboard SVG", "${board.title.lowercase().replace(' ', '-')}.svg", svg.encodeToByteArray())
                svgMsg = saved?.let { "Saved SVG to $it" } ?: "Generated SVG (${svg.length} chars)"
            }) { Text("Export SVG") }
        }

        svgMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

        // Interactive Whiteboard Canvas with Draggable Nodes & Live Connectors!
        val strokeCol = MaterialTheme.colorScheme.primary
        val gridCol = LocalPlanadaySpacing.current.semantics.subtleBorder
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                var x = 24f; while (x < size.width) { var y = 24f; while (y < size.height) { drawCircle(gridCol, 1.5f, Offset(x, y)); y += 24f }; x += 24f }
                board.connectors.forEach { c ->
                    val a = board.nodes.firstOrNull { it.id == c.fromId }
                    val b = board.nodes.firstOrNull { it.id == c.toId }
                    if (a != null && b != null) {
                        drawLine(strokeCol, Offset((a.x + a.w / 2).toFloat(), (a.y + a.h / 2).toFloat()), Offset((b.x + b.w / 2).toFloat(), (b.y + b.h / 2).toFloat()), 3f)
                    }
                }
            }
            board.nodes.forEach { n ->
                val isSel = n.id == selectedNodeId
                var dx by remember(n.id, n.x) { mutableStateOf(0f) }
                var dy by remember(n.id, n.y) { mutableStateOf(0f) }
                val bg = parseHexColorOrNull(n.colorHex) ?: MaterialTheme.colorScheme.primaryContainer
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.Gray),
                    modifier = Modifier
                        .offset(x = (n.x.toFloat() + dx / 2f).dp.coerceAtLeast(4.dp), y = (n.y.toFloat() + dy / 2f).dp.coerceAtLeast(4.dp))
                        .width(n.w.dp)
                        .height(n.h.dp)
                        .clickable { selectedNodeId = n.id }
                        .pointerInput(n.id, n.x, n.y) {
                            detectDragGestures(
                                onDragEnd = {
                                    val moved = n.copy(x = (n.x + dx / 2.0).coerceAtLeast(4.0), y = (n.y + dy / 2.0).coerceAtLeast(4.0))
                                    dx = 0f; dy = 0f
                                    saveBoard(board.copy(nodes = board.nodes.map { if (it.id == n.id) moved else it }))
                                },
                            ) { change, amt -> change.consume(); dx += amt.x; dy += amt.y }
                        },
                ) {
                    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                        Text(n.text, color = Color(0xFF1E293B), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 3)
                    }
                }
            }
        }

        // Selected Node Text Editor
        board.nodes.firstOrNull { it.id == selectedNodeId }?.let { sel ->
            var txt by remember(sel.id) { mutableStateOf(sel.text) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = txt, onValueChange = { txt = it }, label = { Text("Node text") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { saveBoard(board.copy(nodes = board.nodes.map { if (it.id == sel.id) it.copy(text = txt) else it })) }) { Text("Save") }
                TextButton(onClick = {
                    saveBoard(board.copy(nodes = board.nodes.filterNot { it.id == sel.id }, connectors = board.connectors.filterNot { it.fromId == sel.id || it.toId == sel.id }))
                    selectedNodeId = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
