package com.planaday.nativeapp.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
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
import com.planaday.nativeapp.core.usecases.PlanadayUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Native Windows Compose Desktop Shell (ADR-001, PF-38, N-004, O-004).
 * - Multi-pane layout: Collapsible grouped Sidebar + Active Workspace Stage + Toggleable FTS5 Inspector.
 * - Multi-window support (`Ctrl+Shift+W`) sharing ONE live Room Bundled SQLite store so edits sync instantly across windows.
 * - Global Command Palette (`Ctrl+K`) searching all 54 routes and FTS5 records.
 * - Fresh independent Windows storage in `%LOCALAPPDATA%\PlanadayNative\planaday_native_v1.db`.
 */
fun main() = application {
    val dataDir = JvmPlanadayDatabaseFactory.defaultWindowsDataDir()
    val dbFile = File(dataDir, BuildIdentity.CLIENT_DB_NAME)
    val blobDir = File(dataDir, "blobs_v1")

    val database = remember { JvmPlanadayDatabaseFactory.createOnDisk(dbFile) }
    val repository = remember { PlanadayRepository(database, LocalBlobStore(blobDir)) }
    val useCases = remember { PlanadayUseCases(repository) }
    val searchService = remember { PlanadaySearchService(repository) }
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    val windowIds = remember { mutableStateListOf(1) }

    for (winId in windowIds) {
        val controller = remember(winId) {
            PlanadayWorkspaceController(
                repository = repository,
                useCases = useCases,
                searchService = searchService,
                scope = appScope,
                platformLabel = "windows-compose-desktop",
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
            title = "Planaday Native (Window #$winId) — v${BuildIdentity.CURRENT_APP_VERSION}",
            state = rememberWindowState(width = 1360.dp, height = 860.dp),
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                    when (event.key) {
                        Key.K -> { showCommandPalette = !showCommandPalette; true }
                        Key.W -> if (event.isShiftPressed) {
                            windowIds.add((windowIds.maxOrNull() ?: 1) + 1)
                            true
                        } else false
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
    var showSidebar by remember { mutableStateOf(true) }
    var showInspector by remember { mutableStateOf(true) }
    var routeFilter by remember { mutableStateOf("") }

    PlanadayNativeTheme(themeMode = state.themeMode, density = state.density) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // Desktop Toolbar
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showSidebar = !showSidebar },
                            modifier = Modifier.semantics { contentDescription = "Toggle navigation sidebar" },
                        ) { Text(if (showSidebar) "Hide Nav" else "Show Nav") }

                        Text("Planaday Native", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                        // Workspace Switcher
                        state.workspaces.forEach { ws ->
                            FilterChip(
                                selected = ws.id == state.activeWorkspaceId,
                                onClick = { controller.selectWorkspace(ws.id) },
                                label = { Text(ws.name) },
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        OutlinedButton(
                            onClick = onOpenCommandPalette,
                            modifier = Modifier.semantics { contentDescription = "Open command palette Ctrl+K" },
                        ) { Text("Command Palette (Ctrl+K)") }

                        OutlinedButton(
                            onClick = onOpenNewWindow,
                            modifier = Modifier.semantics { contentDescription = "Open second synchronized window" },
                        ) { Text("New Window") }

                        OutlinedButton(
                            onClick = { showInspector = !showInspector },
                            modifier = Modifier.semantics { contentDescription = "Toggle right inspector pane" },
                        ) { Text(if (showInspector) "Hide Inspector" else "Inspector") }
                    }
                }

                // Multi-pane body
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (showSidebar) {
                        Surface(tonalElevation = 1.dp, modifier = Modifier.width(268.dp).fillMaxHeight()) {
                            Column(Modifier.fillMaxSize().padding(8.dp)) {
                                OutlinedTextField(
                                    value = routeFilter,
                                    onValueChange = { routeFilter = it },
                                    label = { Text("Filter 54 workspaces…") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(6.dp))
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxSize()) {
                                    PlanadayRouteGroup.entries.forEach { group ->
                                        val routes = PlanadayRoute.entries.filter {
                                            it.group == group && (routeFilter.isBlank() || it.title.contains(routeFilter, ignoreCase = true))
                                        }
                                        if (routes.isNotEmpty()) {
                                            item {
                                                Text(
                                                    text = group.title,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 6.dp),
                                                )
                                            }
                                            items(routes) { route ->
                                                NavigationDrawerItem(
                                                    label = { Text(route.title, maxLines = 1) },
                                                    selected = state.activeRoute == route,
                                                    onClick = { controller.selectRoute(route) },
                                                    modifier = Modifier.height(40.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        VerticalDivider()
                    }

                    // Primary Workspace Stage
                    PlanadayActiveWorkspaceStage(
                        state = state,
                        controller = controller,
                        isCompact = false,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )

                    // Optional Right FTS5 Search & Diagnostics Inspector Pane
                    if (showInspector) {
                        VerticalDivider()
                        Surface(tonalElevation = 1.dp, modifier = Modifier.width(300.dp).fillMaxHeight()) {
                            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Inspector & Global FTS5 Search", fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = state.searchQuery,
                                    onValueChange = { controller.runSearch(it) },
                                    label = { Text("Search authorized workspace") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                    items(state.searchHits) { hit ->
                                        OutlinedCard(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(8.dp)) {
                                                Text(hit.title, fontWeight = FontWeight.Medium)
                                                Text("${hit.kind.name} · ${hit.summaryText}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider()
                                Text(
                                    "Sync: ${state.syncSnapshot.status} | Pending: ${state.syncSnapshot.totalPendingCount} | Drafts: ${state.recoverableDrafts.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }

            if (showCommandPalette) {
                var query by remember { mutableStateOf("") }
                val matches = remember(query) {
                    PlanadayRoute.entries.filter {
                        query.isBlank() || it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
                    }.take(12)
                }
                AlertDialog(
                    onDismissRequest = onDismissCommandPalette,
                    title = { Text("Command Palette (Ctrl+K)") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("Jump to any of 54 Planaday modules…") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(matches) { route ->
                                    TextButton(
                                        onClick = {
                                            controller.selectRoute(route)
                                            onDismissCommandPalette()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("${route.title} (${route.requirementRef})", modifier = Modifier.fillMaxWidth())
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
