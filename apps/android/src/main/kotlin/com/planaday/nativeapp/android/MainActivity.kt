package com.planaday.nativeapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.planaday.nativeapp.core.designtokens.*
import com.planaday.nativeapp.core.persistence.AndroidPlanadayDatabaseFactory
import com.planaday.nativeapp.core.persistence.LocalBlobStore
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.search.PlanadaySearchService
import com.planaday.nativeapp.core.usecases.PlanadayUseCases
import kotlinx.coroutines.launch

/**
 * Native Android Jetpack Compose Activity targeting API 36 (minSdk 29 / Android 10).
 * - Touch-first responsive layout from 320dp compact phones (4-slot bottom bar + More drawer + safe IME insets)
 *   up to 840dp+ tablets/foldables (permanent Navigation Rail + split stage).
 * - Zero horizontal overflow (PD-011, PD-012, PD-019) & >=48dp accessible controls (PD-013).
 * - Independent fresh SQLite database (planaday_native_v1.db) via BundledSQLiteDriver (O-004).
 */
class MainActivity : ComponentActivity() {
    private lateinit var controller: PlanadayWorkspaceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AndroidPlanadayDatabaseFactory.create(applicationContext)
        val blobStore = LocalBlobStore(AndroidPlanadayDatabaseFactory.defaultBlobDir(applicationContext))
        val repository = PlanadayRepository(db, blobStore)
        val useCases = PlanadayUseCases(repository)
        val searchService = PlanadaySearchService(repository)
        controller = PlanadayWorkspaceController(
            repository = repository,
            useCases = useCases,
            searchService = searchService,
            scope = lifecycleScope,
            platformLabel = "android-jetpack-compose-api36",
        ).apply { bootstrapFresh() }

        setContent {
            AndroidAdaptiveShell(controller)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidAdaptiveShell(controller: PlanadayWorkspaceController) {
    val state by controller.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val primary4Slots = listOf(PlanadayRoute.TODAY, PlanadayRoute.FLOW_CALENDAR, PlanadayRoute.TASKS, PlanadayRoute.NOTES)

    PlanadayNativeTheme(themeMode = state.themeMode, density = PlanadayDensity.COMFORTABLE) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            val isExpandedTablet = maxWidth >= 840.dp
            val isCompactPhone = maxWidth < 600.dp

            if (isExpandedTablet) {
                // Expanded Tablet / Foldable Permanent Split-Pane Layout
                Row(Modifier.fillMaxSize()) {
                    Surface(tonalElevation = 2.dp, modifier = Modifier.width(260.dp).fillMaxHeight()) {
                        AndroidAllModulesDrawerContent(
                            state = state,
                            onSelectRoute = { controller.selectRoute(it) },
                            onSelectWorkspace = { controller.selectWorkspace(it) },
                        )
                    }
                    VerticalDivider()
                    PlanadayActiveWorkspaceStage(
                        state = state,
                        controller = controller,
                        isCompact = false,
                        modifier = Modifier.weight(1f).fillMaxHeight().imePadding(),
                    )
                }
            } else {
                // Compact / Medium Phone Layout (320dp+ safe, 4-slot Bottom Bar + All-54-Module Drawer)
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(modifier = Modifier.widthIn(max = 300.dp)) {
                            AndroidAllModulesDrawerContent(
                                state = state,
                                onSelectRoute = { route ->
                                    controller.selectRoute(route)
                                    scope.launch { drawerState.close() }
                                },
                                onSelectWorkspace = { wsId -> controller.selectWorkspace(wsId) },
                            )
                        }
                    },
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize().imePadding(),
                        topBar = {
                            TopAppBar(
                                title = { Text("Planaday Native", maxLines = 1) },
                                navigationIcon = {
                                    TextButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Open all 54 workspaces drawer" },
                                    ) { Text("Menu") }
                                },
                                actions = {
                                    TextButton(
                                        onClick = { controller.selectRoute(PlanadayRoute.LAB_WORKSPACE) },
                                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Open laboratory suite" },
                                    ) { Text("Lab") }
                                },
                            )
                        },
                        bottomBar = {
                            NavigationBar {
                                primary4Slots.forEach { slot ->
                                    NavigationBarItem(
                                        selected = state.activeRoute == slot,
                                        onClick = { controller.selectRoute(slot) },
                                        icon = { Text(slot.shortLabel.take(2), fontWeight = FontWeight.Bold) },
                                        label = { Text(slot.shortLabel, maxLines = 1) },
                                    )
                                }
                                NavigationBarItem(
                                    selected = state.activeRoute !in primary4Slots,
                                    onClick = { scope.launch { drawerState.open() } },
                                    icon = { Text("54", fontWeight = FontWeight.Bold) },
                                    label = { Text("More", maxLines = 1) },
                                )
                            }
                        },
                    ) { innerPadding ->
                        PlanadayActiveWorkspaceStage(
                            state = state,
                            controller = controller,
                            isCompact = isCompactPhone,
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidAllModulesDrawerContent(
    state: PlanadayUiState,
    onSelectRoute: (PlanadayRoute) -> Unit,
    onSelectWorkspace: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Workspaces", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.workspaces.forEach { ws ->
                FilterChip(
                    selected = ws.id == state.activeWorkspaceId,
                    onClick = { onSelectWorkspace(ws.id) },
                    label = { Text(ws.name, maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxSize()) {
            PlanadayRouteGroup.entries.forEach { grp ->
                item {
                    Text(
                        grp.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(PlanadayRoute.entries.filter { it.group == grp }) { route ->
                    NavigationDrawerItem(
                        label = { Text(route.title, maxLines = 1) },
                        selected = state.activeRoute == route,
                        onClick = { onSelectRoute(route) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
}
