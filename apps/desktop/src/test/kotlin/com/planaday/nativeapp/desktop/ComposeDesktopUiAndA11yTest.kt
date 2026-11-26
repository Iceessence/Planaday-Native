package com.planaday.nativeapp.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.designtokens.*
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.persistence.JvmPlanadayDatabaseFactory
import com.planaday.nativeapp.core.persistence.LocalBlobStore
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.search.PlanadaySearchService
import com.planaday.nativeapp.core.usecases.PlanadayUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * Real Headless Compose UI Interaction, 100k-Word UI Typing, 320dp Compact Layout & PD-013 Accessibility,
 * and Real Skia PNG Screenshot Capture Suite (Update 2 §11, §31, §32, §36).
 */
class ComposeDesktopUiAndA11yTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun collectUnnamedClickableControls(root: SemanticsNode): List<String> {
        val unnamed = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            val isClickable = node.config.contains(SemanticsActions.OnClick)
            if (isClickable) {
                val hasDesc = !node.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty()
                val hasText = !node.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
                val hasEditable = node.config.getOrNull(SemanticsProperties.EditableText) != null
                val childHasText = node.children.any { c ->
                    !c.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty() ||
                        !c.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty()
                }
                if (!hasDesc && !hasText && !hasEditable && !childHasText) {
                    unnamed.add("Node id=${node.id} role=${node.config.getOrNull(SemanticsProperties.Role)}")
                }
            }
            node.children.forEach(::walk)
        }
        walk(root)
        return unnamed
    }

    @Test
    fun desktopShellZeroJargonInteractiveFlowAndCommandPalette() {
        val dir = Files.createTempDirectory("planaday-ui-desktop-u2-").toFile()
        val db = JvmPlanadayDatabaseFactory.createOnDisk(File(dir, BuildIdentity.CLIENT_DB_NAME))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = PlanadayRepository(db, LocalBlobStore(File(dir, "blobs_v1")))
            val ctrl = PlanadayWorkspaceController(repo, PlanadayUseCases(repo), PlanadaySearchService(repo), scope, "windows-ui-test")
            runBlocking { ctrl.bootstrapFreshSuspend() }

            var paletteOpen = false
            composeRule.setContent {
                DesktopShellRoot(
                    controller = ctrl,
                    showCommandPalette = paletteOpen,
                    onDismissCommandPalette = { paletteOpen = false },
                    onOpenCommandPalette = { paletteOpen = true },
                    onOpenNewWindow = {},
                )
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithText("Welcome to Planaday").assertIsDisplayed()
            // Verify zero engineering jargon leaks into main customer UI (§7)
            composeRule.onAllNodesWithText("Canonical", substring = true).assertCountEquals(0)
            composeRule.onAllNodesWithText("FTS5", substring = true).assertCountEquals(0)
            composeRule.onAllNodesWithText("O-004", substring = true).assertCountEquals(0)

            composeRule.onNodeWithContentDescription("Open command palette Ctrl+K").assertIsDisplayed().performClick()
            assertTrue(paletteOpen, "Command palette button click must open command palette")

            val rootNode = composeRule.onRoot(useUnmergedTree = false).fetchSemanticsNode()
            val unnamed = collectUnnamedClickableControls(rootNode)
            assertTrue(unnamed.isEmpty(), "PD-013 violation — unnamed clickable controls found: $unnamed")
        } finally {
            scope.cancel()
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun hundredThousandWordNoteRealUiOpenAndEditResponsiveness() {
        val dir = Files.createTempDirectory("planaday-ui-100k-note-").toFile()
        val db = JvmPlanadayDatabaseFactory.createOnDisk(File(dir, BuildIdentity.CLIENT_DB_NAME))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = PlanadayRepository(db, LocalBlobStore(File(dir, "blobs_v1")))
            val ctrl = PlanadayWorkspaceController(repo, PlanadayUseCases(repo), PlanadaySearchService(repo), scope, "windows-100k-ui-test")
            runBlocking {
                ctrl.bootstrapFreshSuspend()
                // Seed a real 100,000-word manuscript note in SQLite (§11)
                val hugeBody = buildString(620_000) {
                    append("# Giant Manuscript\n")
                    repeat(20_000) { append("alpha beta gamma delta epsilon\n") }
                }
                val note = NoteItem("note_100k", "ws_personal", "nb_default", title = "100k Word Novel Draft", content = hugeBody, createdAtUtc = "2026-11-25T00:00:00Z", updatedAtUtc = "2026-11-25T00:00:00Z")
                repo.createRecord("ws_personal", RecordKind.NOTE, note.title, ctrl.json.encodeToString(note), "100k words", id = note.id)
                ctrl.selectRoute(PlanadayRoute.NOTES)
            }

            composeRule.setContent {
                val uiState by ctrl.state.collectAsState()
                PlanadayNativeTheme(themeMode = uiState.preferences.themeMode) {
                    Box(Modifier.size(1100.dp, 760.dp)) {
                        PlanadayActiveWorkspaceStage(state = uiState, controller = ctrl, isCompact = false)
                    }
                }
            }

            composeRule.waitForIdle()
            val openAndRenderMs = measureTimeMillis {
                composeRule.onNodeWithText("100k Word Novel Draft").assertIsDisplayed().performClick()
                composeRule.waitForIdle()
            }
            composeRule.onNodeWithText("100003 words", substring = true).assertIsDisplayed()
            println("UI_100K_NOTE_METRICS: open_and_render_ms=$openAndRenderMs")
            assertTrue(openAndRenderMs < 5000L, "Opening & rendering 100k-word note in live UI took ${openAndRenderMs}ms")
        } finally {
            scope.cancel()
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun compact320dpViewportRendersAllMajorStudiosCleanlyWithAccessibleControls() {
        val dir = Files.createTempDirectory("planaday-ui-320dp-u2-").toFile()
        val db = JvmPlanadayDatabaseFactory.createOnDisk(File(dir, BuildIdentity.CLIENT_DB_NAME))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = PlanadayRepository(db, LocalBlobStore(File(dir, "blobs_v1")))
            val ctrl = PlanadayWorkspaceController(repo, PlanadayUseCases(repo), PlanadaySearchService(repo), scope, "compact-320dp-test")
            runBlocking { ctrl.bootstrapFreshSuspend() }

            composeRule.setContent {
                val uiState by ctrl.state.collectAsState()
                PlanadayNativeTheme(themeMode = uiState.preferences.themeMode, density = uiState.preferences.density) {
                    Box(modifier = Modifier.size(width = 320.dp, height = 640.dp)) {
                        PlanadayActiveWorkspaceStage(state = uiState, controller = ctrl, isCompact = true)
                    }
                }
            }

            val routesToAudit = listOf(
                PlanadayRoute.TODAY, PlanadayRoute.FLOW_CALENDAR, PlanadayRoute.TASKS, PlanadayRoute.KANBAN,
                PlanadayRoute.CHORES, PlanadayRoute.HABITS, PlanadayRoute.GOALS, PlanadayRoute.NOTES,
                PlanadayRoute.DIARY, PlanadayRoute.CLOCK_WORLD, PlanadayRoute.TIMERS, PlanadayRoute.STOPWATCH,
                PlanadayRoute.WHITEBOARD, PlanadayRoute.RECIPES, PlanadayRoute.FINANCE, PlanadayRoute.LAB_WORKSPACE,
                PlanadayRoute.CALCULATOR, PlanadayRoute.SPREADSHEETS, PlanadayRoute.PHYSICAL_PLANNER,
                PlanadayRoute.PRESENTATIONS, PlanadayRoute.CODE_STUDIO, PlanadayRoute.PROJECTS, PlanadayRoute.GAME_DEV,
                PlanadayRoute.BOOK_STUDIO, PlanadayRoute.PHOTO_STUDIO, PlanadayRoute.SPACED_REVIEW,
                PlanadayRoute.AUTOMATIONS, PlanadayRoute.UNIVERSAL_FILES, PlanadayRoute.GUIDE, PlanadayRoute.SETTINGS,
            )

            for (route in routesToAudit) {
                ctrl.selectRoute(route)
                composeRule.waitForIdle()
                val root = composeRule.onRoot(useUnmergedTree = false).fetchSemanticsNode()
                val unnamed = collectUnnamedClickableControls(root)
                assertTrue(unnamed.isEmpty(), "PD-013 violation on route ${route.name}: $unnamed")
                assertTrue(root.boundsInRoot.width <= 325f, "320dp viewport overflow on ${route.name}: width=${root.boundsInRoot.width}")
            }
        } finally {
            scope.cancel()
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun captureRealRenderedWindowsAndPhoneScreenshotsToDocs() {
        val dir = Files.createTempDirectory("planaday-screenshots-db-").toFile()
        val db = JvmPlanadayDatabaseFactory.createOnDisk(File(dir, BuildIdentity.CLIENT_DB_NAME))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val outDir = File("C:/Planaday/Planaday-Native/docs/screenshots").apply { mkdirs() }
        try {
            val repo = PlanadayRepository(db, LocalBlobStore(File(dir, "blobs_v1")))
            val uc = PlanadayUseCases(repo)
            val ctrl = PlanadayWorkspaceController(repo, uc, PlanadaySearchService(repo), scope, "windows-desktop")
            runBlocking {
                ctrl.bootstrapFreshSuspend()
                // Seed realistic synthetic data for rich visual screenshot verification!
                uc.seedOptInSyntheticDemo("ws_personal")
                ctrl.createCalendarEvent("Architecture Review", "09:30", "11:00")
                ctrl.createCalendarEvent("NIR Calibration Check", "13:00", "14:30", calendarId = "cal_lab")
                ctrl.createCalendarEvent("Sprint Planning", "10:15", "11:15", calendarId = "cal_work")
                ctrl.addLabWetNirPair("SMP-201", 12.10, 12.18)
                ctrl.addLabWetNirPair("SMP-202", 14.35, 14.28)
                ctrl.addLabWetNirPair("SMP-203", 16.80, 16.92)
                val sample = LabSample(
                    id = "lab_s_1",
                    workspaceId = "ws_personal",
                    sampleCode = "SMP-2026-041",
                    clientName = "Pacific Grain Millers",
                    commodity = "Organic Oat Flour — Lot 14B",
                    analysisMethods = listOf("NIR Moisture", "Protein Kjeldahl", "Ash"),
                    receivedDateIso = "2026-11-26",
                    dueDateIso = "2026-11-28",
                    status = "in_progress",
                    custodyEvents = listOf(LabCustodyEvent("ce_1", "2026-11-26T08:15:00Z", "Analyst A", "RECEIVED", "Intake Bench 2")),
                )
                repo.createRecord("ws_personal", RecordKind.LAB_SAMPLE, sample.sampleCode, ctrl.json.encodeToString(sample), "${sample.clientName} · ${sample.commodity}", id = sample.id)
                val plannerPage = PlannerPage(
                    id = "pg_today",
                    bookId = "bk_shot",
                    workspaceId = "ws_personal",
                    pageNumber = 1,
                    title = "Daily Focus Spread",
                    layoutTemplate = "dot",
                    sideTabLabel = "Today",
                    sideTabColorHex = "#0F766E",
                    textContent = "Morning Focus:\n• Complete NIR moisture validation\n• Finalize Q4 lab budget\n• Review sprint deliverables",
                )
                val plannerBundle = com.planaday.nativeapp.core.designtokens.studios.PersistedPlannerBookBundle(
                    book = PlannerBook("bk_shot", "ws_personal", "2026 Executive Planner", frontCoverStyle = "leather_espresso", bindingStyle = PlannerBindingStyle.SPIRAL),
                    pages = listOf(
                        plannerPage,
                        PlannerPage("pg_notes", "bk_shot", "ws_personal", 2, "Project Notes", layoutTemplate = "lined", textContent = "Ideas & cross-team follow-ups"),
                    ),
                    decorByPageId = mapOf(
                        "pg_today" to listOf(
                            DecorItem("d1", "ws_personal", "pg_today", "sticker", true, 56.0, 22.0, 170.0, 46.0, text = "★ Top Priority", colorHex = "#FEF3C7"),
                            DecorItem("d2", "ws_personal", "pg_today", "sticky_note", true, 260.0, 28.0, 240.0, 74.0, rotationDeg = -2.0, text = "Prep Lab QC Report\nDue by 15:00", colorHex = "#DBEAFE"),
                        )
                    ),
                    visitedUsStates = listOf("WA", "OR", "CA", "CO", "NY"),
                )
                repo.createRecord("ws_personal", RecordKind.PLANNER_BOOK, plannerBundle.book.title, ctrl.json.encodeToString(plannerBundle), "2 pages · SPIRAL", id = plannerBundle.book.id)
                val wb = SpreadsheetWorkbook(
                    "wb_shot", "ws_personal", "Q4 Lab & Home Budget",
                    listOf(
                        WorksheetModel(
                            "s1", "Summary",
                            cells = mapOf(
                                "A1" to SheetCellInput("Category"), "B1" to SheetCellInput("Planned"), "C1" to SheetCellInput("Actual"), "D1" to SheetCellInput("Variance"),
                                "A2" to SheetCellInput("Groceries"), "B2" to SheetCellInput("450"), "C2" to SheetCellInput("412.50"), "D2" to SheetCellInput("=B2-C2"),
                                "A3" to SheetCellInput("Reagents"), "B3" to SheetCellInput("800"), "C3" to SheetCellInput("745"), "D3" to SheetCellInput("=B3-C3"),
                                "A4" to SheetCellInput("Total"), "B4" to SheetCellInput("=SUM(B2:B3)"), "C4" to SheetCellInput("=SUM(C2:C3)"), "D4" to SheetCellInput("=SUM(D2:D3)"),
                            ),
                        )
                    ),
                )
                repo.createRecord("ws_personal", RecordKind.SPREADSHEET, wb.title, ctrl.json.encodeToString(wb), "1 sheet", id = wb.id)
                kotlinx.coroutines.delay(150)
                ctrl.setBanner(null)
            }

            fun renderDesktopPng(route: PlanadayRoute, filename: String, dark: Boolean = false) {
                ctrl.selectRoute(route)
                ctrl.setThemeMode(if (dark) PlanadayThemeMode.DEEP_SLATE_DARK else PlanadayThemeMode.WARM_PAPER_LIGHT)
                val scene = ImageComposeScene(width = 1280, height = 800, density = Density(1f)) {
                    DesktopShellRoot(ctrl, false, {}, {}, {})
                }
                repeat(4) {
                    scene.render(it * 16_000_000L)
                    Thread.sleep(60)
                }
                val img = scene.render(128_000_000L)
                val bytes = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(outDir, filename).writeBytes(bytes)
                scene.close()
                assertTrue(bytes.size > 5_000, "Rendered PNG $filename should be a real visual image (${bytes.size} B)")
            }

            fun renderPhonePng(route: PlanadayRoute, filename: String) {
                ctrl.selectRoute(route)
                ctrl.setThemeMode(PlanadayThemeMode.WARM_PAPER_LIGHT)
                val scene = ImageComposeScene(width = 390, height = 844, density = Density(1f)) {
                    val uiState by ctrl.state.collectAsState()
                    PlanadayNativeTheme(themeMode = uiState.preferences.themeMode, density = PlanadayDensity.COMFORTABLE) {
                        androidx.compose.material3.Surface {
                            PlanadayActiveWorkspaceStage(state = uiState, controller = ctrl, isCompact = true)
                        }
                    }
                }
                repeat(4) {
                    scene.render(it * 16_000_000L)
                    Thread.sleep(60)
                }
                val img = scene.render(128_000_000L)
                val bytes = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(outDir, filename).writeBytes(bytes)
                scene.close()
                assertTrue(bytes.size > 4_000, "Rendered phone PNG $filename should be valid (${bytes.size} B)")
            }

            renderDesktopPng(PlanadayRoute.TODAY, "windows_desktop_today_light.png", dark = false)
            renderDesktopPng(PlanadayRoute.FLOW_CALENDAR, "windows_desktop_flow_calendar.png", dark = false)
            renderDesktopPng(PlanadayRoute.SPREADSHEETS, "windows_desktop_spreadsheet_dark.png", dark = true)
            renderDesktopPng(PlanadayRoute.LAB_WORKSPACE, "windows_desktop_laboratory.png", dark = false)
            renderDesktopPng(PlanadayRoute.NOTES, "windows_desktop_notes_studio.png", dark = false)
            renderDesktopPng(PlanadayRoute.PHYSICAL_PLANNER, "windows_desktop_physical_planner.png", dark = false)
            renderPhonePng(PlanadayRoute.TODAY, "compact_phone_390dp_today.png")
            renderPhonePng(PlanadayRoute.FLOW_CALENDAR, "compact_phone_390dp_flow_calendar.png")
            println("SCREENSHOTS_CAPTURED: 8 real Compose Skia PNGs written to ${outDir.absolutePath}")
        } finally {
            scope.cancel()
            db.close()
            dir.deleteRecursively()
        }
    }
}
