package com.planaday.nativeapp.core.designtokens.studios

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.designtokens.*
import com.planaday.nativeapp.core.documents.LargeDocumentAnalyzer
import com.planaday.nativeapp.core.domain.*
import com.planaday.nativeapp.core.interchange.ExtendedFormatInterchange
import com.planaday.nativeapp.core.interchange.RichDocumentExporters
import com.planaday.nativeapp.core.math.MathAstEngine
import com.planaday.nativeapp.core.math.MathEvalContext
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.security.SafeLearningInterpreter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID

// ============================================================================
// 1. Complete Book Studio (Update 2 §17 — Replaces hardcoded sample manuscript!)
// ============================================================================

@Serializable
data class PersistedBookBundle(
    val project: BookProject,
    val chapters: List<BookChapter> = emptyList(),
    val worldEntities: List<BookWorldEntity> = emptyList(),
)

@Serializable
data class BookWorldEntity(
    val id: String,
    val kind: String, // Character | Location | Lore | Plot Arc | Timeline
    val name: String,
    val notes: String = "",
)

@Composable
fun RealBookStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController, isCompact: Boolean) {
    val bookRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.BOOK_PROJECT, 40) }.collectAsState(emptyList())
    val books = remember(bookRecs) { bookRecs.mapNotNull { runCatching { controller.json.decodeFromString<PersistedBookBundle>(it.payloadJson) }.getOrNull() } }

    var activeBook by remember { mutableStateOf<PersistedBookBundle?>(null) }
    var tab by remember { mutableStateOf(0) } // 0=Manuscript, 1=Worldbuilding, 2=Continuity & Export
    var selectedChapterId by remember { mutableStateOf<String?>(null) }
    var newBookTitle by remember { mutableStateOf("") }
    var newTargetWords by remember { mutableStateOf("50000") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(books) {
        if (activeBook == null && books.isNotEmpty()) activeBook = books.first()
        else if (activeBook != null) books.firstOrNull { it.project.id == activeBook?.project?.id }?.let { activeBook = it }
    }

    fun saveBook(next: PersistedBookBundle) {
        activeBook = next
        val totalWords = next.chapters.sumOf { LargeDocumentAnalyzer.computeStatsSinglePass(it.manuscriptText).wordCount }
        controller.createOrUpdateRecord(
            kind = RecordKind.BOOK_PROJECT,
            title = next.project.title,
            payloadJson = controller.json.encodeToString(next),
            summaryText = "${next.chapters.size} chapters · $totalWords words",
            existingId = if (books.any { it.project.id == next.project.id }) next.project.id else null,
            recordRevision = true,
        )
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            books.forEach { b -> FilterChip(selected = b.project.id == activeBook?.project?.id, onClick = { activeBook = b; selectedChapterId = b.chapters.firstOrNull()?.id }, label = { Text(b.project.title) }) }
            OutlinedTextField(value = newBookTitle, onValueChange = { newBookTitle = it }, label = { Text("New book title") }, singleLine = true, modifier = Modifier.width(160.dp))
            OutlinedTextField(value = newTargetWords, onValueChange = { newTargetWords = it }, label = { Text("Word goal") }, singleLine = true, modifier = Modifier.width(105.dp))
            Button(onClick = {
                val bid = UUID.randomUUID().toString()
                val title = newBookTitle.trim().ifEmpty { "Untitled Book ${books.size + 1}" }
                val ch1 = BookChapter(UUID.randomUUID().toString(), bid, state.activeWorkspaceId, 1, "Chapter 1")
                val bundle = PersistedBookBundle(BookProject(bid, state.activeWorkspaceId, title = title, targetWordCount = newTargetWords.toIntOrNull() ?: 50_000), listOf(ch1))
                saveBook(bundle)
                selectedChapterId = ch1.id
                newBookTitle = ""
            }, modifier = Modifier.heightIn(min = 44.dp)) { Text("+ Book") }
        }

        val bundle = activeBook
        if (bundle == null) {
            StudioEmptyState(
                title = "Start Writing a Book",
                message = "Create your own book project with chapters, scenes, characters, locations, continuity checks, and DOCX/EPUB/Markdown export.",
                actionLabel = "Create First Book",
                onAction = {
                    val bid = UUID.randomUUID().toString()
                    val ch1 = BookChapter(UUID.randomUUID().toString(), bid, state.activeWorkspaceId, 1, "Chapter 1")
                    saveBook(PersistedBookBundle(BookProject(bid, state.activeWorkspaceId, title = "My Novel"), listOf(ch1)))
                    selectedChapterId = ch1.id
                },
            )
            return
        }

        val totalWords = remember(bundle.chapters) { bundle.chapters.sumOf { LargeDocumentAnalyzer.computeStatsSinglePass(it.manuscriptText).wordCount } }
        val progress = (totalWords.toFloat() / bundle.project.targetWordCount.coerceAtLeast(1)).coerceIn(0f, 1f)
        Text("${bundle.project.title} · $totalWords / ${bundle.project.targetWordCount} words (${(progress * 100).toInt()}%)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

        StudioTabRow(listOf("Manuscript (${bundle.chapters.size} Ch)", "Worldbuilding (${bundle.worldEntities.size})", "Continuity & Export"), tab, { tab = it })

        when (tab) {
            0 -> {
                val activeCh = bundle.chapters.firstOrNull { it.id == selectedChapterId } ?: bundle.chapters.firstOrNull()
                var chTitle by remember(activeCh?.id) { mutableStateOf(activeCh?.title ?: "") }
                var chText by remember(activeCh?.id) { mutableStateOf(activeCh?.manuscriptText ?: "") }

                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    bundle.chapters.sortedBy { it.chapterNumber }.forEach { ch ->
                        FilterChip(selected = ch.id == activeCh?.id, onClick = { selectedChapterId = ch.id }, label = { Text("${ch.chapterNumber}. ${ch.title}") })
                    }
                    OutlinedButton(onClick = {
                        val n = bundle.chapters.size + 1
                        val nc = BookChapter(UUID.randomUUID().toString(), bundle.project.id, state.activeWorkspaceId, n, "Chapter $n")
                        saveBook(bundle.copy(chapters = bundle.chapters + nc))
                        selectedChapterId = nc.id
                    }) { Text("+ Chapter") }
                }
                if (activeCh != null) {
                    val chWords = remember(chText) { LargeDocumentAnalyzer.computeStatsSinglePass(chText).wordCount }
                    OutlinedTextField(value = chTitle, onValueChange = { chTitle = it }, label = { Text("Chapter ${activeCh.chapterNumber} Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = chText, onValueChange = { chText = it }, label = { Text("Manuscript text ($chWords words)") }, modifier = Modifier.fillMaxWidth().weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val nextChs = bundle.chapters.map { if (it.id == activeCh.id) it.copy(title = chTitle, manuscriptText = chText) else it }
                            saveBook(bundle.copy(chapters = nextChs))
                        }) { Text("Save Chapter") }
                        if (activeCh.chapterNumber > 1) {
                            OutlinedButton(onClick = {
                                val sorted = bundle.chapters.sortedBy { it.chapterNumber }.toMutableList()
                                val i = sorted.indexOfFirst { it.id == activeCh.id }
                                if (i > 0) {
                                    val tmp = sorted.removeAt(i); sorted.add(i - 1, tmp)
                                    saveBook(bundle.copy(chapters = sorted.mapIndexed { idx, c -> c.copy(chapterNumber = idx + 1) }))
                                }
                            }) { Text("Move Earlier") }
                        }
                        if (bundle.chapters.size > 1) {
                            TextButton(onClick = {
                                val rem = bundle.chapters.filterNot { it.id == activeCh.id }.mapIndexed { idx, c -> c.copy(chapterNumber = idx + 1) }
                                saveBook(bundle.copy(chapters = rem))
                                selectedChapterId = rem.firstOrNull()?.id
                            }) { Text("Delete Chapter", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            1 -> {
                var entKind by remember { mutableStateOf("Character") }
                var entName by remember { mutableStateOf("") }
                var entNotes by remember { mutableStateOf("") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Add Worldbuilding Entity") {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Character", "Location", "Lore", "Plot Arc", "Timeline").forEach { k ->
                                    FilterChip(selected = entKind == k, onClick = { entKind = k }, label = { Text(k) })
                                }
                            }
                            OutlinedTextField(value = entName, onValueChange = { entName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = entNotes, onValueChange = { entNotes = it }, label = { Text("Traits, backstory, or continuity notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                if (entName.isNotBlank()) {
                                    val e = BookWorldEntity(UUID.randomUUID().toString(), entKind, entName.trim(), entNotes)
                                    saveBook(bundle.copy(worldEntities = bundle.worldEntities + e))
                                    entName = ""; entNotes = ""
                                }
                            }) { Text("Add to Bible") }
                        }
                    }
                    items(bundle.worldEntities, key = { it.id }) { e ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("${e.name} (${e.kind})", fontWeight = FontWeight.SemiBold)
                                    Text(e.notes.ifBlank { "No notes yet" }, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { saveBook(bundle.copy(worldEntities = bundle.worldEntities.filterNot { it.id == e.id })) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
            else -> {
                val charNames = bundle.worldEntities.filter { it.kind == "Character" }.map { it.name }.toSet()
                val continuityFindings = remember(bundle.chapters, charNames) {
                    controller.useCases.checkBookContinuity(bundle.chapters, charNames)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Live Continuity Check (${continuityFindings.size} finding(s))", subtitle = "Scanned ${bundle.chapters.size} chapter(s) against ${charNames.size} character(s)") {
                            if (continuityFindings.isEmpty()) {
                                Text("No continuity issues detected across your manuscript.", color = LocalPlanadaySpacing.current.semantics.success)
                            } else {
                                continuityFindings.forEach { f -> Text("• [${f.severity}] ${f.description}", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    item {
                        StudioCard(title = "Export Your Manuscript") {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val art = ExtendedFormatInterchange.exportBookDocx(bundle.project, bundle.chapters)
                                    val saved = controller.fileDialogBridge?.pickFileToSave("Export Word (.docx)", art.filename, art.bytes)
                                    statusMsg = saved?.let { "Saved DOCX to $it" } ?: "Generated ${art.filename} (${art.bytes.size} B)"
                                }) { Text("Export .docx") }
                                Button(onClick = {
                                    val art = ExtendedFormatInterchange.exportBookEpub(bundle.project, bundle.chapters)
                                    val saved = controller.fileDialogBridge?.pickFileToSave("Export eBook (.epub)", art.filename, art.bytes)
                                    statusMsg = saved?.let { "Saved EPUB to $it" } ?: "Generated ${art.filename} (${art.bytes.size} B)"
                                }) { Text("Export .epub") }
                                OutlinedButton(onClick = {
                                    val art = RichDocumentExporters.exportBookMarkdown(bundle.project, bundle.chapters)
                                    val saved = controller.fileDialogBridge?.pickFileToSave("Export Markdown", art.filename, art.bytes)
                                    statusMsg = saved?.let { "Saved Markdown to $it" } ?: "Generated ${art.filename} (${art.bytes.size} B)"
                                }) { Text("Export .md") }
                            }
                            statusMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. Complete Game Development Studio (Update 2 §18 — Replaces hardcoded stats!)
// ============================================================================

@Serializable
data class PersistedGameEntity(
    val id: String,
    val category: String, // Character | Enemy | Item | Skill | Recipe | Loot Table | System Doc
    val name: String,
    val hp: Double = 100.0,
    val attack: Double = 15.0,
    val defense: Double = 5.0,
    val critChance: Double = 0.1,
    val critMult: Double = 1.5,
    val notes: String = "",
)

@Serializable
data class PersistedGameBundle(
    val project: GameProject,
    val entities: List<PersistedGameEntity> = emptyList(),
)

@Composable
fun RealGameDevStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    val recs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.GAME_PROJECT, 40) }.collectAsState(emptyList())
    val games = remember(recs) { recs.mapNotNull { runCatching { controller.json.decodeFromString<PersistedGameBundle>(it.payloadJson) }.getOrNull() } }

    var activeGame by remember { mutableStateOf<PersistedGameBundle?>(null) }
    var tab by remember { mutableStateOf(0) } // 0=Entities & Systems, 1=Balance Simulator, 2=Progression Curve
    var newGameTitle by remember { mutableStateOf("") }

    LaunchedEffect(games) {
        if (activeGame == null && games.isNotEmpty()) activeGame = games.first()
        else if (activeGame != null) games.firstOrNull { it.project.id == activeGame?.project?.id }?.let { activeGame = it }
    }

    fun saveGame(next: PersistedGameBundle) {
        activeGame = next
        controller.createOrUpdateRecord(
            RecordKind.GAME_PROJECT, next.project.title, controller.json.encodeToString(next),
            "${next.entities.size} entities",
            existingId = if (games.any { it.project.id == next.project.id }) next.project.id else null,
        )
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            games.forEach { g -> FilterChip(selected = g.project.id == activeGame?.project?.id, onClick = { activeGame = g }, label = { Text(g.project.title) }) }
            OutlinedTextField(value = newGameTitle, onValueChange = { newGameTitle = it }, label = { Text("New game project") }, singleLine = true, modifier = Modifier.width(165.dp))
            Button(onClick = {
                val p = GameProject(UUID.randomUUID().toString(), state.activeWorkspaceId, newGameTitle.trim().ifEmpty { "Game ${games.size + 1}" })
                saveGame(PersistedGameBundle(p))
                newGameTitle = ""
            }) { Text("+ Game") }
        }

        val bundle = activeGame
        if (bundle == null) {
            StudioEmptyState(
                title = "No Game Projects Yet",
                message = "Create a game design project to author characters, enemies, skills, crafting recipes, loot tables, and run balance simulations on your own entities.",
                actionLabel = "Create First Game Project",
                onAction = { saveGame(PersistedGameBundle(GameProject(UUID.randomUUID().toString(), state.activeWorkspaceId, "Untitled Game"))) },
            )
            return
        }

        StudioTabRow(listOf("Design Entities (${bundle.entities.size})", "Balance Simulator", "XP & Economy Curve"), tab, { tab = it })

        when (tab) {
            0 -> {
                var cat by remember { mutableStateOf("Character") }
                var name by remember { mutableStateOf("") }
                var hp by remember { mutableStateOf("100") }
                var atk by remember { mutableStateOf("18") }
                var def by remember { mutableStateOf("6") }
                var crit by remember { mutableStateOf("0.15") }
                var notes by remember { mutableStateOf("") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Create Entity or System Element") {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Character", "Enemy", "Item", "Skill", "Recipe", "Loot Table", "System Doc").forEach { c ->
                                    FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) })
                                }
                            }
                            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("$cat Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            if (cat == "Character" || cat == "Enemy") {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(value = hp, onValueChange = { hp = it }, label = { Text("HP") }, singleLine = true, modifier = Modifier.weight(1f))
                                    OutlinedTextField(value = atk, onValueChange = { atk = it }, label = { Text("ATK") }, singleLine = true, modifier = Modifier.weight(1f))
                                    OutlinedTextField(value = def, onValueChange = { def = it }, label = { Text("DEF") }, singleLine = true, modifier = Modifier.weight(1f))
                                    OutlinedTextField(value = crit, onValueChange = { crit = it }, label = { Text("Crit") }, singleLine = true, modifier = Modifier.weight(1f))
                                }
                            }
                            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Abilities, crafting inputs, drops, or rules") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                if (name.isNotBlank()) {
                                    val ent = PersistedGameEntity(UUID.randomUUID().toString(), cat, name.trim(), hp.toDoubleOrNull() ?: 100.0, atk.toDoubleOrNull() ?: 15.0, def.toDoubleOrNull() ?: 5.0, crit.toDoubleOrNull() ?: 0.1, 1.5, notes)
                                    saveGame(bundle.copy(entities = bundle.entities + ent))
                                    name = ""; notes = ""
                                }
                            }) { Text("Save $cat") }
                        }
                    }
                    items(bundle.entities, key = { it.id }) { e ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("${e.name} [${e.category}]", fontWeight = FontWeight.SemiBold)
                                    val statsLine = if (e.category == "Character" || e.category == "Enemy") "HP ${e.hp} · ATK ${e.attack} · DEF ${e.defense} · Crit ${(e.critChance * 100).toInt()}%" else e.notes
                                    Text(statsLine, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { saveGame(bundle.copy(entities = bundle.entities.filterNot { it.id == e.id })) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            1 -> {
                val combatants = bundle.entities.filter { it.category == "Character" || it.category == "Enemy" }
                var attackerId by remember(combatants) { mutableStateOf(combatants.firstOrNull()?.id) }
                var defenderId by remember(combatants) { mutableStateOf(combatants.getOrNull(1)?.id ?: combatants.firstOrNull()?.id) }
                var seedStr by remember { mutableStateOf("42") }
                var trialsStr by remember { mutableStateOf("500") }
                var simResult by remember { mutableStateOf<BalanceSimulationResult?>(null) }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Configurable Balance Simulator (Uses Your Created Entities)") {
                            if (combatants.size < 2) {
                                Text("Add at least 2 Characters or Enemies in the Design Entities tab to simulate matchups between them.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Attacker:", style = MaterialTheme.typography.labelMedium)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    combatants.forEach { c -> FilterChip(selected = c.id == attackerId, onClick = { attackerId = c.id }, label = { Text(c.name) }) }
                                }
                                Text("Defender:", style = MaterialTheme.typography.labelMedium)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    combatants.forEach { c -> FilterChip(selected = c.id == defenderId, onClick = { defenderId = c.id }, label = { Text(c.name) }) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = seedStr, onValueChange = { seedStr = it }, label = { Text("Seed") }, singleLine = true, modifier = Modifier.weight(1f))
                                    OutlinedTextField(value = trialsStr, onValueChange = { trialsStr = it }, label = { Text("Trials") }, singleLine = true, modifier = Modifier.weight(1f))
                                }
                                Button(onClick = {
                                    val a = combatants.firstOrNull { it.id == attackerId } ?: return@Button
                                    val d = combatants.firstOrNull { it.id == defenderId } ?: return@Button
                                    simResult = GameBalanceSimulatorEngine.simulateCombat(
                                        CombatantStats(a.name, a.hp, a.attack, a.defense, a.critChance, a.critMult),
                                        CombatantStats(d.name, d.hp, d.attack, d.defense, d.critChance, d.critMult),
                                        trials = trialsStr.toIntOrNull()?.coerceIn(10, 10_000) ?: 500,
                                        seed = seedStr.toLongOrNull() ?: 42L,
                                    )
                                }) { Text("Run Deterministic Simulation") }
                                simResult?.let { r ->
                                    Text("Seed ${r.seed} (${r.trials} trials): Attacker Win Rate = ${"%.1f".format(r.attackerWinRate * 100)}% · Avg Turns = ${"%.2f".format(r.avgTurnsToDefeat)} · Avg Remaining HP = ${"%.1f".format(r.avgRemainingHp)}", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                var baseXp by remember { mutableStateOf("100") }
                var expStr by remember { mutableStateOf("1.5") }
                val table = remember(baseXp, expStr) {
                    GameBalanceSimulatorEngine.xpCurveTable(15, baseXp.toDoubleOrNull() ?: 100.0, expStr.toDoubleOrNull() ?: 1.5)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Progression / Economy Curve") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = baseXp, onValueChange = { baseXp = it }, label = { Text("Base XP") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = expStr, onValueChange = { expStr = it }, label = { Text("Exponent") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Text(table.joinToString(" · ") { (lvl, xp) -> "L$lvl:$xp" }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. Complete Projects, User Gantt Critical Path & Custom Database Builder (§21 & §26)
// ============================================================================

@Serializable
data class PersistedProjectPlan(
    val id: String,
    val title: String,
    val dailyCapacityHours: Double = 8.0,
    val tasks: List<PersistedGanttTask> = emptyList(),
)

@Serializable
data class PersistedGanttTask(
    val id: String,
    val title: String,
    val durationDays: Int,
    val dependsOnIds: List<String> = emptyList(),
    val hoursPerDay: Double = 4.0,
)

@Serializable
data class PersistedCustomEntityEntry(
    val id: String,
    val schemaId: String,
    val values: Map<String, String>,
    val computed: Map<String, Double> = emptyMap(),
)

@Composable
fun RealProjectsAndCustomSchemasStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    var tab by remember { mutableStateOf(0) } // 0=Projects & Gantt, 1=Custom Databases & Schemas
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StudioTabRow(listOf("Projects & Gantt", "Custom Databases & Schemas"), tab, { tab = it })

        if (tab == 0) {
            val projRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.PROJECT, 40) }.collectAsState(emptyList())
            val projects = remember(projRecs) { projRecs.mapNotNull { runCatching { controller.json.decodeFromString<PersistedProjectPlan>(it.payloadJson) }.getOrNull() } }
            var activeProj by remember { mutableStateOf<PersistedProjectPlan?>(null) }
            var newProjTitle by remember { mutableStateOf("") }
            var taskName by remember { mutableStateOf("") }
            var taskDays by remember { mutableStateOf("3") }
            var depId by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(projects) {
                if (activeProj == null && projects.isNotEmpty()) activeProj = projects.first()
                else if (activeProj != null) projects.firstOrNull { it.id == activeProj?.id }?.let { activeProj = it }
            }

            fun saveProj(p: PersistedProjectPlan) {
                activeProj = p
                controller.createOrUpdateRecord(RecordKind.PROJECT, p.title, controller.json.encodeToString(p), "${p.tasks.size} tasks", existingId = if (projects.any { it.id == p.id }) p.id else null)
            }

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                projects.forEach { p -> FilterChip(selected = p.id == activeProj?.id, onClick = { activeProj = p; depId = null }, label = { Text(p.title) }) }
                OutlinedTextField(value = newProjTitle, onValueChange = { newProjTitle = it }, label = { Text("New project") }, singleLine = true, modifier = Modifier.width(155.dp))
                Button(onClick = {
                    val p = PersistedProjectPlan(UUID.randomUUID().toString(), newProjTitle.trim().ifEmpty { "Project ${projects.size + 1}" })
                    saveProj(p); newProjTitle = ""
                }) { Text("+ Project") }
            }

            val proj = activeProj
            if (proj == null) {
                StudioEmptyState("No Projects Yet", "Create a project to add tasks, dependencies, and compute live Gantt timelines & critical paths.")
            } else {
                val analysis = remember(proj.tasks, proj.dailyCapacityHours) {
                    if (proj.tasks.isEmpty()) null
                    else runCatching {
                        ProjectGanttCriticalPathEngine.analyze(
                            proj.tasks.map { ProjectTaskNode(it.id, it.title, it.durationDays, it.dependsOnIds, it.hoursPerDay) },
                            proj.dailyCapacityHours,
                        )
                    }.getOrNull()
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    item {
                        StudioCard(title = "Add Task to '${proj.title}'") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = taskName, onValueChange = { taskName = it }, label = { Text("Task name") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = taskDays, onValueChange = { taskDays = it }, label = { Text("Days") }, singleLine = true, modifier = Modifier.width(84.dp))
                            }
                            if (proj.tasks.isNotEmpty()) {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Depends on:", style = MaterialTheme.typography.labelSmall)
                                    FilterChip(selected = depId == null, onClick = { depId = null }, label = { Text("None") })
                                    proj.tasks.forEach { pt -> FilterChip(selected = depId == pt.id, onClick = { depId = pt.id }, label = { Text(pt.title) }) }
                                }
                            }
                            Button(onClick = {
                                if (taskName.isNotBlank()) {
                                    val t = PersistedGanttTask("T${proj.tasks.size + 1}", taskName.trim(), taskDays.toIntOrNull()?.coerceIn(1, 365) ?: 2, listOfNotNull(depId))
                                    saveProj(proj.copy(tasks = proj.tasks + t))
                                    taskName = ""; depId = null
                                }
                            }) { Text("Add Project Task") }
                        }
                    }
                    analysis?.let { g ->
                        item {
                            StudioCard(
                                title = "Live Gantt Timeline (${g.totalDurationDays} days total)",
                                subtitle = "Critical Path: ${g.criticalPathIds.joinToString(" → ")} · Peak load: ${g.peakDailyHours}h/day",
                            ) {
                                val critColor = MaterialTheme.colorScheme.error
                                val normalColor = MaterialTheme.colorScheme.primary
                                val maxD = g.totalDurationDays.coerceAtLeast(1)
                                g.nodes.forEach { node ->
                                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                        Text("${node.title} (Day ${node.earliestStartDay}–${node.earliestFinishDay}, slack ${node.slackDays}d)${if (node.isCritical) " ★ Critical" else ""}", style = MaterialTheme.typography.bodySmall, fontWeight = if (node.isCritical) FontWeight.Bold else FontWeight.Normal)
                                        Canvas(Modifier.fillMaxWidth().height(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                                            val startX = (node.earliestStartDay.toFloat() / maxD) * size.width
                                            val barW = ((node.earliestFinishDay - node.earliestStartDay).toFloat() / maxD * size.width).coerceAtLeast(10f)
                                            drawRect(if (node.isCritical) critColor else normalColor, topLeft = Offset(startX, 2f), size = Size(barW, size.height - 4f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // REAL USER CUSTOM DATABASE / SCHEMA BUILDER & RECORD ENTRY
            val schemaRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.CUSTOM_SCHEMA, 30) }.collectAsState(emptyList())
            val entityRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.CUSTOM_ENTITY, 150) }.collectAsState(emptyList())
            val schemas = remember(schemaRecs) { schemaRecs.mapNotNull { runCatching { controller.json.decodeFromString<CustomSchemaDef>(it.payloadJson) }.getOrNull() } }
            val entries = remember(entityRecs) { entityRecs.mapNotNull { runCatching { controller.json.decodeFromString<PersistedCustomEntityEntry>(it.payloadJson) }.getOrNull() } }

            var activeSchema by remember { mutableStateOf<CustomSchemaDef?>(null) }
            var newSchemaName by remember { mutableStateOf("") }
            var fieldKey by remember { mutableStateOf("") }
            var fieldLabel by remember { mutableStateOf("") }
            var fieldType by remember { mutableStateOf(CustomFieldType.TEXT) }
            var fieldFormula by remember { mutableStateOf("") }
            val entryInputs = remember(activeSchema?.id) { mutableStateMapOf<String, String>() }
            var validationMsg by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(schemas) {
                if (activeSchema == null && schemas.isNotEmpty()) activeSchema = schemas.first()
                else if (activeSchema != null) schemas.firstOrNull { it.id == activeSchema?.id }?.let { activeSchema = it }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        schemas.forEach { s -> FilterChip(selected = s.id == activeSchema?.id, onClick = { activeSchema = s; entryInputs.clear() }, label = { Text(s.name) }) }
                        OutlinedTextField(value = newSchemaName, onValueChange = { newSchemaName = it }, label = { Text("New custom table") }, singleLine = true, modifier = Modifier.width(165.dp))
                        Button(onClick = {
                            if (newSchemaName.isNotBlank()) {
                                val s = CustomSchemaDef(UUID.randomUUID().toString(), state.activeWorkspaceId, newSchemaName.trim(), fields = listOf(CustomFieldDef("name", "Name", CustomFieldType.TEXT, required = true)))
                                controller.createOrUpdateRecord(RecordKind.CUSTOM_SCHEMA, s.name, controller.json.encodeToString(s), "1 field")
                                activeSchema = s; newSchemaName = ""
                            }
                        }) { Text("+ Schema") }
                    }
                }
                activeSchema?.let { sch ->
                    item {
                        StudioCard(title = "Schema Fields for '${sch.name}' (${sch.fields.size})") {
                            Text(sch.fields.joinToString(" · ") { "${it.label} (${it.fieldType.name})" }, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(value = fieldKey, onValueChange = { fieldKey = it }, label = { Text("Key (e.g. price)") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = fieldLabel, onValueChange = { fieldLabel = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(CustomFieldType.TEXT, CustomFieldType.NUMBER, CustomFieldType.BOOLEAN, CustomFieldType.COMPUTED_FORMULA).forEach { ft ->
                                    FilterChip(selected = fieldType == ft, onClick = { fieldType = ft }, label = { Text(ft.name) })
                                }
                            }
                            if (fieldType == CustomFieldType.COMPUTED_FORMULA) {
                                OutlinedTextField(value = fieldFormula, onValueChange = { fieldFormula = it }, label = { Text("Formula (e.g. mass / volume)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                            OutlinedButton(onClick = {
                                if (fieldKey.isNotBlank()) {
                                    val f = CustomFieldDef(fieldKey.trim(), fieldLabel.ifBlank { fieldKey.trim() }, fieldType, formulaExpression = fieldFormula.takeIf { it.isNotBlank() })
                                    val upd = sch.copy(fields = sch.fields + f)
                                    controller.createOrUpdateRecord(RecordKind.CUSTOM_SCHEMA, upd.name, controller.json.encodeToString(upd), "${upd.fields.size} fields", existingId = upd.id)
                                    activeSchema = upd; fieldKey = ""; fieldLabel = ""; fieldFormula = ""
                                }
                            }) { Text("+ Add Field") }
                        }
                    }
                    item {
                        StudioCard(title = "Add Record to '${sch.name}'") {
                            sch.fields.filter { it.fieldType != CustomFieldType.COMPUTED_FORMULA }.forEach { f ->
                                OutlinedTextField(
                                    value = entryInputs[f.key] ?: "",
                                    onValueChange = { entryInputs[f.key] = it },
                                    label = { Text("${f.label}${if (f.required) " *" else ""}") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Button(onClick = {
                                val res = CustomSchemaEngine.validateAndCompute(sch, entryInputs.toMap()) { ex, vars ->
                                    MathAstEngine.evaluate(ex, MathEvalContext(variables = vars))
                                }
                                if (res.valid) {
                                    val entry = PersistedCustomEntityEntry(UUID.randomUUID().toString(), sch.id, entryInputs.toMap(), res.computedValues)
                                    val title = entryInputs.values.firstOrNull { it.isNotBlank() } ?: "${sch.name} Record"
                                    controller.createOrUpdateRecord(RecordKind.CUSTOM_ENTITY, title, controller.json.encodeToString(entry), res.computedValues.toString())
                                    entryInputs.clear()
                                    validationMsg = "Saved record (computed=${res.computedValues})"
                                } else {
                                    validationMsg = "Validation errors: ${res.errors.joinToString("; ")}"
                                }
                            }) { Text("Validate & Save Record") }
                            validationMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    items(entries.filter { it.schemaId == sch.id }, key = { it.id }) { e ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.values.entries.joinToString(" · ") { "${it.key}=${it.value}" }, fontWeight = FontWeight.Medium)
                                    if (e.computed.isNotEmpty()) Text("Computed: ${e.computed}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
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
// 4. Safe Multi-File Code Studio (Update 2 §23 — Host Execution Disabled by Default)
// ============================================================================

@Composable
fun SafeCodeStudio(state: PlanadayShellState, controller: PlanadayWorkspaceController) {
    val projRecs by remember(state.activeWorkspaceId) { controller.observeKind(RecordKind.CODE_PROJECT, 30) }.collectAsState(emptyList())
    val projects = remember(projRecs) { projRecs.mapNotNull { runCatching { controller.json.decodeFromString<CodeProject>(it.payloadJson) }.getOrNull() } }

    var activeProj by remember { mutableStateOf<CodeProject?>(null) }
    var activeFilePath by remember { mutableStateOf<String?>(null) }
    var newProjName by remember { mutableStateOf("") }
    var newFileName by remember { mutableStateOf("") }
    var breakpointLines by remember { mutableStateOf("2") }
    var debugOutput by remember { mutableStateOf("Run the Safe Learning Debugger or export your project bundle.") }

    LaunchedEffect(projects) {
        if (activeProj == null && projects.isNotEmpty()) {
            activeProj = projects.first()
            activeFilePath = projects.first().files.firstOrNull()?.path
        } else if (activeProj != null) {
            projects.firstOrNull { it.id == activeProj?.id }?.let { activeProj = it }
        }
    }

    fun saveProj(p: CodeProject) {
        activeProj = p
        controller.createOrUpdateRecord(RecordKind.CODE_PROJECT, p.title, controller.json.encodeToString(p), "${p.files.size} files (${p.primaryLanguage})", existingId = if (projects.any { it.id == p.id }) p.id else null)
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Explicit Security Policy Banner (§23)
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Security Notice: Unrestricted host OS code execution is disabled until verified OS-level sandboxing is active. Edit multi-file projects, run static checks, step-debug in the safe learning interpreter, or export a portable ZIP for your local IDE/compiler.",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(10.dp),
            )
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            projects.forEach { p -> FilterChip(selected = p.id == activeProj?.id, onClick = { activeProj = p; activeFilePath = p.files.firstOrNull()?.path }, label = { Text(p.title) }) }
            OutlinedTextField(value = newProjName, onValueChange = { newProjName = it }, label = { Text("New code project") }, singleLine = true, modifier = Modifier.width(160.dp))
            Button(onClick = {
                val p = CodeProject(
                    UUID.randomUUID().toString(), state.activeWorkspaceId, newProjName.trim().ifEmpty { "Project ${projects.size + 1}" }, "learning",
                    files = listOf(CodeFile("main.planaday", "learning", "let x = 12\nlet y = x * 3 + 4\nprint y\nassert_eq y, 40")),
                )
                saveProj(p); activeFilePath = "main.planaday"; newProjName = ""
            }) { Text("+ Project") }
        }

        val proj = activeProj
        if (proj == null) {
            StudioEmptyState(
                title = "No Code Projects Yet",
                message = "Create a multi-file code project to organize snippets, check syntax, step-debug safely, or export to your external compiler.",
                actionLabel = "Create First Code Project",
                onAction = {
                    val p = CodeProject(UUID.randomUUID().toString(), state.activeWorkspaceId, "Algorithms", "learning", listOf(CodeFile("main.script", "learning", "let a = 5\nlet b = a * 2\nprint b")))
                    saveProj(p); activeFilePath = "main.script"
                },
            )
            return
        }

        val activeFile = proj.files.firstOrNull { it.path == activeFilePath } ?: proj.files.firstOrNull()
        var codeText by remember(proj.id, activeFile?.path) { mutableStateOf(activeFile?.content ?: "") }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            proj.files.forEach { f -> FilterChip(selected = f.path == activeFile?.path, onClick = { activeFilePath = f.path }, label = { Text(f.path) }) }
            OutlinedTextField(value = newFileName, onValueChange = { newFileName = it }, label = { Text("New file") }, singleLine = true, modifier = Modifier.width(120.dp))
            OutlinedButton(onClick = {
                if (newFileName.isNotBlank()) {
                    val nf = CodeFile(newFileName.trim(), proj.primaryLanguage, "")
                    saveProj(proj.copy(files = proj.files + nf))
                    activeFilePath = nf.path; newFileName = ""
                }
            }) { Text("+ File") }
        }

        OutlinedTextField(
            value = codeText,
            onValueChange = { codeText = it },
            label = { Text(activeFile?.path ?: "Source code") },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (activeFile != null) {
                    val upd = proj.copy(files = proj.files.map { if (it.path == activeFile.path) it.copy(content = codeText) else it })
                    saveProj(upd)
                }
            }) { Text("Save File") }
            OutlinedTextField(value = breakpointLines, onValueChange = { breakpointLines = it }, label = { Text("Breakpoints") }, singleLine = true, modifier = Modifier.width(120.dp))
            Button(onClick = {
                val bps = breakpointLines.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                val res = SafeLearningInterpreter.executeWithDebugger(codeText, breakpoints = bps)
                debugOutput = if (res.succeeded) {
                    val bp = res.trace.firstOrNull { it.hitBreakpoint }
                    "PASS (${res.stepsExecuted} steps) · stdout=${res.stdout}" + (bp?.let { " · Breakpoint L${it.lineNumber} vars=${it.variables}" } ?: "")
                } else "Error: ${res.error}"
            }) { Text("Run Safe Debugger") }
        }
        Text(debugOutput, style = MaterialTheme.typography.bodySmall)
    }
}
