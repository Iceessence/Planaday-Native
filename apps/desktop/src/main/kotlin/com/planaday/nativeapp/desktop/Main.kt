package com.planaday.nativeapp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.planaday.nativeapp.core.designtokens.*
import com.planaday.nativeapp.core.model.BuildIdentity
import com.planaday.nativeapp.core.persistence.JvmPlanadayDatabaseFactory
import com.planaday.nativeapp.core.persistence.LocalBlobStore
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.search.PlanadaySearchService
import com.planaday.nativeapp.core.security.JvmProtectedCredentialVault
import com.planaday.nativeapp.core.usecases.PlanadayUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Native Windows Swing/AWT file picker bridge for real file imports & backup/document exports (§7, §22, §27).
 */
class JvmNativeFileDialogBridge : NativeFileDialogBridge {
    override fun pickFileToRead(title: String, extensions: List<String>): Pair<String, ByteArray>? {
        val dlg = FileDialog(null as Frame?, title, FileDialog.LOAD)
        if (extensions.isNotEmpty()) {
            dlg.setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
        }
        dlg.isVisible = true
        val dir = dlg.directory ?: return null
        val file = dlg.file ?: return null
        val f = File(dir, file)
        return if (f.exists() && f.isFile) f.name to f.readBytes() else null
    }

    override fun pickFileToSave(title: String, suggestedFilename: String, bytes: ByteArray): String? {
        val dlg = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dlg.file = suggestedFilename
        dlg.isVisible = true
        val dir = dlg.directory ?: return null
        val file = dlg.file ?: return null
        val f = File(dir, file)
        f.writeBytes(bytes)
        return f.absolutePath
    }
}

/**
 * Commercial Windows Compose Desktop Application (Update 2 §7 & §32):
 * - Resizable sidebar splitter + collapsible sidebar & right inspector persisted across restarts.
 * - Multi-tab open workspace bar (`Ctrl+W` close tab) + Distraction-Free Zen Mode (`F11`).
 * - True multi-window (`Ctrl+Shift+W`) sharing ONE reactive Room SQLite Flow bus so edits sync live across windows!
 * - OS-protected Windows NTFS-ACL AES-256-GCM Credential Vault for persistent encrypted sync tokens.
 * - Native file dialogs for CSV, XLSX, ICS, PPTX, DOCX, EPUB, Photos, Files, and Encrypted Backups.
 */
fun main() = application {
    val dataDir = JvmPlanadayDatabaseFactory.defaultWindowsDataDir()
    val dbFile = File(dataDir, BuildIdentity.CLIENT_DB_NAME)
    val blobDir = File(dataDir, "blobs_v1")

    val database = remember { JvmPlanadayDatabaseFactory.createOnDisk(dbFile) }
    val repository = remember { PlanadayRepository(database, LocalBlobStore(blobDir)) }
    val useCases = remember { PlanadayUseCases(repository) }
    val searchService = remember { PlanadaySearchService(repository) }
    val credentialVault = remember { JvmProtectedCredentialVault(dataDir) }
    val fileBridge = remember { JvmNativeFileDialogBridge() }
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    val windowIds = remember { mutableStateListOf(1) }

    for (winId in windowIds) {
        val controller = remember(winId) {
            PlanadayWorkspaceController(
                repository = repository,
                useCases = useCases,
                searchService = searchService,
                scope = appScope,
                platformLabel = "windows-desktop",
                credentialVault = credentialVault,
                fileDialogBridge = fileBridge,
            ).apply { bootstrapFresh() }
        }
        var showCommandPalette by remember(winId) { mutableStateOf(false) }

        Window(
            onCloseRequest = {
                windowIds.remove(winId)
                if (windowIds.isEmpty()) {
                    database.close()
                    exitApplication()
                }
            },
            title = if (windowIds.size > 1) "Planaday — Window #$winId" else "Planaday",
            state = rememberWindowState(width = 1380.dp, height = 880.dp),
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        event.key == Key.F11 -> {
                            controller.updatePreferences { it.copy(zenMode = !it.zenMode) }
                            true
                        }
                        event.isCtrlPressed && event.key == Key.K -> {
                            showCommandPalette = !showCommandPalette
                            true
                        }
                        event.isCtrlPressed && event.isShiftPressed && event.key == Key.W -> {
                            windowIds.add((windowIds.maxOrNull() ?: 1) + 1)
                            true
                        }
                        event.isCtrlPressed && !event.isShiftPressed && event.key == Key.W -> {
                            controller.closeTab(controller.state.value.activeRoute)
                            true
                        }
                        else -> false
                    }
                } else false
            },
        ) {
            DesktopShellRoot(
                controller = controller,
                showCommandPalette = showCommandPalette,
                onDismissCommandPalette = { showCommandPalette = false },
                onOpenCommandPalette = { showCommandPalette = true },
                onOpenNewWindow = { windowIds.add((windowIds.maxOrNull() ?: 1) + 1) },
            )
        }
    }
}

@Composable
fun DesktopShellRoot(
    controller: PlanadayWorkspaceController,
    showCommandPalette: Boolean,
    onDismissCommandPalette: () -> Unit,
    onOpenCommandPalette: () -> Unit,
    onOpenNewWindow: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val prefs = state.preferences
    var routeFilter by remember { mutableStateOf("") }
    var sidebarWidthDp by remember(prefs.sidebarWidthDp) { mutableStateOf(prefs.sidebarWidthDp.coerceIn(200f, 380f)) }

    PlanadayNativeTheme(
        themeMode = prefs.themeMode,
        density = prefs.density,
        accentHex = prefs.accentHex,
        fontScale = prefs.fontScale,
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // Clean Commercial Desktop Top Bar (hidden in Zen Mode except Exit Zen pill)
                if (!prefs.zenMode) {
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { controller.updatePreferences { it.copy(showSidebar = !it.showSidebar) } },
                                modifier = Modifier.heightIn(min = 38.dp).semantics { contentDescription = "Toggle navigation sidebar" },
                            ) { Text(if (prefs.showSidebar) "Hide Menu" else "Menu") }

                            Text("Planaday", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.workspaces.forEach { ws ->
                                    FilterChip(
                                        selected = ws.id == state.activeWorkspaceId,
                                        onClick = { controller.selectWorkspace(ws.id) },
                                        label = { Text(ws.name) },
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            OutlinedButton(
                                onClick = onOpenCommandPalette,
                                modifier = Modifier.heightIn(min = 38.dp).semantics { contentDescription = "Open command palette Ctrl+K" },
                            ) { Text("Search & Jump (Ctrl+K)") }

                            OutlinedButton(
                                onClick = onOpenNewWindow,
                                modifier = Modifier.heightIn(min = 38.dp).semantics { contentDescription = "Open new synchronized window" },
                            ) { Text("New Window") }

                            OutlinedButton(
                                onClick = { controller.updatePreferences { it.copy(showInspector = !it.showInspector) } },
                                modifier = Modifier.heightIn(min = 38.dp).semantics { contentDescription = "Toggle search inspector pane" },
                            ) { Text(if (prefs.showInspector) "Hide Search" else "Search Pane") }

                            TextButton(
                                onClick = { controller.updatePreferences { it.copy(zenMode = true) } },
                                modifier = Modifier.heightIn(min = 38.dp).semantics { contentDescription = "Enter distraction-free Zen mode" },
                            ) { Text("Zen (F11)") }
                        }
                    }
                    HorizontalDivider(color = LocalPlanadaySpacing.current.semantics.subtleBorder)

                    // Multi-Tab Open Workspaces Bar
                    if (state.openTabs.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.openTabs.forEach { tabRoute ->
                                InputChip(
                                    selected = tabRoute == state.activeRoute,
                                    onClick = { controller.selectRoute(tabRoute) },
                                    label = { Text(tabRoute.title) },
                                    trailingIcon = {
                                        TextButton(
                                            onClick = { controller.closeTab(tabRoute) },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(22.dp).semantics { contentDescription = "Close tab ${tabRoute.title}" },
                                        ) { Text("×") }
                                    },
                                )
                            }
                        }
                        HorizontalDivider(color = LocalPlanadaySpacing.current.semantics.subtleBorder)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { controller.updatePreferences { it.copy(zenMode = false) } }) { Text("Exit Zen Mode (F11)") }
                    }
                }

                // Multi-Pane Desktop Body with Draggable Sidebar Splitter
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (prefs.showSidebar && !prefs.zenMode) {
                        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(sidebarWidthDp.dp).fillMaxHeight()) {
                            Column(Modifier.fillMaxSize().padding(8.dp)) {
                                OutlinedTextField(
                                    value = routeFilter,
                                    onValueChange = { routeFilter = it },
                                    label = { Text("Filter modules…") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(6.dp))
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxSize()) {
                                    PlanadayRouteGroup.entries.forEach { group ->
                                        val routes = PlanadayRoute.entries.filter {
                                            it.group == group && it.id !in prefs.hiddenRouteIds &&
                                                (routeFilter.isBlank() || it.title.contains(routeFilter, ignoreCase = true))
                                        }
                                        if (routes.isNotEmpty()) {
                                            item {
                                                Text(
                                                    text = group.title,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 8.dp),
                                                )
                                            }
                                            items(routes, key = { it.id }) { route ->
                                                NavigationDrawerItem(
                                                    label = { Text(route.title, maxLines = 1) },
                                                    selected = state.activeRoute == route,
                                                    onClick = { controller.selectRoute(route) },
                                                    modifier = Modifier.height(38.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Draggable Resizable Sidebar Splitter
                        Box(
                            modifier = Modifier.width(6.dp).fillMaxHeight()
                                .background(LocalPlanadaySpacing.current.semantics.subtleBorder.copy(alpha = 0.7f))
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = { controller.updatePreferences { it.copy(sidebarWidthDp = sidebarWidthDp) } },
                                    ) { change, dragAmount ->
                                        change.consume()
                                        sidebarWidthDp = (sidebarWidthDp + dragAmount / 2f).coerceIn(200f, 380f)
                                    }
                                }
                        )
                    }

                    // Active Workspace Stage
                    PlanadayActiveWorkspaceStage(
                        state = state,
                        controller = controller,
                        isCompact = false,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )

                    // Optional Context-Sensitive Search Inspector Pane
                    if (prefs.showInspector && !prefs.zenMode) {
                        VerticalDivider(color = LocalPlanadaySpacing.current.semantics.subtleBorder)
                        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(300.dp).fillMaxHeight()) {
                            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Search", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = state.searchQuery,
                                    onValueChange = { controller.runSearch(it) },
                                    label = { Text("Search current workspace…") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                    items(state.searchHits, key = { it.recordId }) { hit ->
                                        OutlinedCard(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(8.dp)) {
                                                Text(hit.title, fontWeight = FontWeight.Medium)
                                                Text("${hit.kind.name.lowercase().replace('_', ' ')} · ${hit.summaryText}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Command Palette (Ctrl+K) — Jump to any of 54 modules OR Search Live Records
            if (showCommandPalette) {
                var query by remember { mutableStateOf("") }
                LaunchedEffect(query) { controller.runSearch(query) }
                val routeMatches = remember(query) {
                    PlanadayRoute.entries.filter {
                        query.isBlank() || it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
                    }.take(10)
                }
                AlertDialog(
                    onDismissRequest = onDismissCommandPalette,
                    title = { Text("Command Palette") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("Jump to module or search records…") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(routeMatches, key = { it.id }) { route ->
                                    TextButton(
                                        onClick = {
                                            controller.selectRoute(route)
                                            onDismissCommandPalette()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text(route.title, modifier = Modifier.fillMaxWidth()) }
                                }
                                if (state.searchHits.isNotEmpty()) {
                                    item { HorizontalDivider(); Text("Matching Records", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) }
                                    items(state.searchHits.take(6), key = { it.recordId }) { hit ->
                                        Text("• ${hit.title} (${hit.kind.name.lowercase()})", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = onDismissCommandPalette) { Text("Close") } },
                )
            }
        }
    }
}
