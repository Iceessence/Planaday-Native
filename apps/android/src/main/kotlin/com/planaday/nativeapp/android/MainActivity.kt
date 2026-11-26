package com.planaday.nativeapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.planaday.nativeapp.core.model.Priority
import com.planaday.nativeapp.core.persistence.AndroidPlanadayDatabaseFactory
import com.planaday.nativeapp.core.persistence.LocalBlobStore
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.search.PlanadaySearchService
import com.planaday.nativeapp.core.security.AndroidKeystoreCredentialVault
import com.planaday.nativeapp.core.usecases.PlanadayUseCases
import kotlinx.coroutines.launch

/**
 * Genuine Phone-First & Tablet-Adaptive Native Android App (API 29–36, Update 2 §7 & §31).
 * - One-handed bottom navigation (`Today`, `Calendar`, `Tasks`, `Notes`, `Apps`) + contextual Quick Capture FAB.
 * - Native Android BackHandler support, edge-to-edge system bar + keyboard IME insets, and >=48dp touch targets.
 * - Hardware/TEE-backed AndroidKeyStore AES-256-GCM Credential Vault protecting optional sync tokens at rest.
 * - Adaptive permanent split-pane layout on tablets and unfolded foldables (>= 840dp).
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
        val keystoreVault = AndroidKeystoreCredentialVault()

        controller = PlanadayWorkspaceController(
            repository = repository,
            useCases = useCases,
            searchService = searchService,
            scope = lifecycleScope,
            platformLabel = "android-api36",
            credentialVault = keystoreVault,
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
    val prefs = state.preferences
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showQuickCaptureSheet by remember { mutableStateOf(false) }
    val primary4Slots = listOf(PlanadayRoute.TODAY, PlanadayRoute.FLOW_CALENDAR, PlanadayRoute.TASKS, PlanadayRoute.NOTES)

    // Proper Android back-button behavior: close drawer first, otherwise return to Today
    BackHandler(enabled = drawerState.isOpen || state.activeRoute != PlanadayRoute.TODAY) {
        if (drawerState.isOpen) scope.launch { drawerState.close() }
        else controller.selectRoute(PlanadayRoute.TODAY)
    }

    PlanadayNativeTheme(
        themeMode = prefs.themeMode,
        density = PlanadayDensity.COMFORTABLE,
        accentHex = prefs.accentHex,
        fontScale = prefs.fontScale,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            val isExpandedTablet = maxWidth >= 840.dp
            val isCompactPhone = maxWidth < 600.dp

            if (isExpandedTablet) {
                Row(Modifier.fillMaxSize()) {
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(268.dp).fillMaxHeight()) {
                        AndroidAllModulesDrawerContent(
                            state = state,
                            onSelectRoute = { controller.selectRoute(it) },
                            onSelectWorkspace = { controller.selectWorkspace(it) },
                        )
                    }
                    VerticalDivider(color = LocalPlanadaySpacing.current.semantics.subtleBorder)
                    PlanadayActiveWorkspaceStage(
                        state = state,
                        controller = controller,
                        isCompact = false,
                        modifier = Modifier.weight(1f).fillMaxHeight().imePadding(),
                    )
                }
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(modifier = Modifier.widthIn(max = 310.dp)) {
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
                                title = { Text("Planaday", maxLines = 1, fontWeight = FontWeight.Bold) },
                                navigationIcon = {
                                    TextButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Open workspaces and modules menu" },
                                    ) { Text("Menu") }
                                },
                                actions = {
                                    TextButton(
                                        onClick = { controller.selectRoute(PlanadayRoute.SETTINGS) },
                                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Open settings" },
                                    ) { Text("Settings") }
                                },
                            )
                        },
                        floatingActionButton = {
                            ExtendedFloatingActionButton(
                                onClick = { showQuickCaptureSheet = true },
                                modifier = Modifier.semantics { contentDescription = "Quick capture task, event, or note" },
                            ) { Text("+ Add") }
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
                                    icon = { Text("All", fontWeight = FontWeight.Bold) },
                                    label = { Text("Apps", maxLines = 1) },
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

            // Touch-first Quick Capture Dialog Sheet
            if (showQuickCaptureSheet) {
                var kind by remember { mutableStateOf("Task") }
                var text by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showQuickCaptureSheet = false },
                    title = { Text("Quick Add") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Task", "Event", "Note", "Meal").forEach { k ->
                                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k) })
                                }
                            }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = { Text("New $kind…") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (text.isNotBlank()) {
                                    when (kind) {
                                        "Task" -> controller.quickCreateTask(text, Priority.HIGH, 30)
                                        "Event" -> controller.createCalendarEvent(text, "10:00", "11:00")
                                        "Note" -> controller.saveNote(text, "")
                                        "Meal" -> controller.logMeal(text, "lunch")
                                    }
                                    showQuickCaptureSheet = false
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Save $kind") }
                    },
                    dismissButton = { TextButton(onClick = { showQuickCaptureSheet = false }) { Text("Cancel") } },
                )
            }
        }
    }
}

@Composable
private fun AndroidAllModulesDrawerContent(
    state: PlanadayShellState,
    onSelectRoute: (PlanadayRoute) -> Unit,
    onSelectWorkspace: (String) -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Workspaces", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.workspaces.forEach { ws ->
                FilterChip(
                    selected = ws.id == state.activeWorkspaceId,
                    onClick = { onSelectWorkspace(ws.id) },
                    label = { Text(ws.name, maxLines = 1) },
                )
            }
        }
        OutlinedTextField(value = filter, onValueChange = { filter = it }, label = { Text("Find module…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            PlanadayRouteGroup.entries.forEach { grp ->
                val routes = PlanadayRoute.entries.filter { it.group == grp && (filter.isBlank() || it.title.contains(filter, true)) }
                if (routes.isNotEmpty()) {
                    item {
                        Text(
                            grp.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(routes, key = { it.id }) { route ->
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
}
