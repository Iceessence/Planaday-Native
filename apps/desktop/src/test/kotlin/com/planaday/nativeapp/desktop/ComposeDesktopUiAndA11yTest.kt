package com.planaday.nativeapp.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.designtokens.PlanadayActiveWorkspaceStage
import com.planaday.nativeapp.core.designtokens.PlanadayNativeTheme
import com.planaday.nativeapp.core.designtokens.PlanadayRoute
import com.planaday.nativeapp.core.designtokens.PlanadayWorkspaceController
import com.planaday.nativeapp.core.model.BuildIdentity
import com.planaday.nativeapp.core.persistence.JvmPlanadayDatabaseFactory
import com.planaday.nativeapp.core.persistence.LocalBlobStore
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.search.PlanadaySearchService
import com.planaday.nativeapp.core.usecases.PlanadayUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real Headless Compose Desktop UI, Accessibility (PD-013), and 320dp Compact Responsive Layout (PD-011, PD-012, PD-019) Tests.
 * Renders real Compose trees via Skiko/Compose UI Test — zero fake mocks!
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
    fun desktopShellRendersInteractivelyAndOpensCommandPalette() {
        val dir = Files.createTempDirectory("planaday-ui-desktop-").toFile()
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
            composeRule.onNodeWithText("Fresh Empty Planaday Native Database (Owner Decision O-004)").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Open command palette Ctrl+K").assertIsDisplayed().performClick()
            assertTrue(paletteOpen, "Command palette button click must open command palette")

            // Audit zero unnamed clickable controls across the rendered desktop shell (PD-013)
            val rootNode = composeRule.onRoot(useUnmergedTree = false).fetchSemanticsNode()
            val unnamed = collectUnnamedClickableControls(rootNode)
            assertTrue(unnamed.isEmpty(), "PD-013 violation — unnamed clickable controls found: $unnamed")
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun compact320dpViewportRendersKeyStudiosWithZeroHorizontalClipAndAccessibleLabels() {
        val dir = Files.createTempDirectory("planaday-ui-320dp-").toFile()
        val db = JvmPlanadayDatabaseFactory.createOnDisk(File(dir, BuildIdentity.CLIENT_DB_NAME))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = PlanadayRepository(db, LocalBlobStore(File(dir, "blobs_v1")))
            val ctrl = PlanadayWorkspaceController(repo, PlanadayUseCases(repo), PlanadaySearchService(repo), scope, "compact-320dp-test")
            runBlocking { ctrl.bootstrapFreshSuspend() }

            composeRule.setContent {
                val uiState by ctrl.state.collectAsState()
                PlanadayNativeTheme(themeMode = uiState.themeMode, density = uiState.density) {
                    // Enforce strict 320.dp phone viewport constraint (PD-011, PD-012, PD-019, PF-06)
                    Box(modifier = Modifier.size(width = 320.dp, height = 640.dp)) {
                        PlanadayActiveWorkspaceStage(state = uiState, controller = ctrl, isCompact = true)
                    }
                }
            }

            val routesToAudit = listOf(
                PlanadayRoute.TODAY,
                PlanadayRoute.FLOW_CALENDAR,
                PlanadayRoute.TASKS,
                PlanadayRoute.NOTES,
                PlanadayRoute.DIARY,
                PlanadayRoute.CLOCK_WORLD,
                PlanadayRoute.WHITEBOARD,
                PlanadayRoute.RECIPES,
                PlanadayRoute.LAB_WORKSPACE,
                PlanadayRoute.CALCULATOR,
                PlanadayRoute.SPREADSHEETS,
                PlanadayRoute.PHYSICAL_PLANNER,
                PlanadayRoute.CODE_STUDIO,
                PlanadayRoute.PROJECTS,
                PlanadayRoute.GAME_DEV,
                PlanadayRoute.BOOK_STUDIO,
                PlanadayRoute.PHOTO_STUDIO,
                PlanadayRoute.SPACED_REVIEW,
                PlanadayRoute.HOUSEHOLD,
                PlanadayRoute.HEALTH_MEDS,
                PlanadayRoute.TRAVEL,
                PlanadayRoute.UNIVERSAL_FILES,
                PlanadayRoute.GUIDE,
                PlanadayRoute.SETTINGS,
            )

            for (route in routesToAudit) {
                ctrl.selectRoute(route)
                composeRule.waitForIdle()

                val root = composeRule.onRoot(useUnmergedTree = false).fetchSemanticsNode()
                val unnamed = collectUnnamedClickableControls(root)
                assertTrue(unnamed.isEmpty(), "PD-013 violation on route ${route.name}: $unnamed")
                // Verify rendered root fits within 320dp container width
                assertTrue(root.boundsInRoot.width <= 325f, "320dp viewport overflow on ${route.name}: width=${root.boundsInRoot.width}")
            }

            // Interactive UI smoke check inside 320dp Calculator panel
            ctrl.selectRoute(PlanadayRoute.CALCULATOR)
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Result = 512.5").assertIsDisplayed()
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
