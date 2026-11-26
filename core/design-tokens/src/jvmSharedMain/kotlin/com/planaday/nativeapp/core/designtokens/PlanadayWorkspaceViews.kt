package com.planaday.nativeapp.core.designtokens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.designtokens.studios.*
import com.planaday.nativeapp.core.model.SaveState

/**
 * Clean, customer-facing responsive workspace stage routing all 54 Planaday modules to real,
 * reactive, SQLite-persisted interactive studios (Update 2 §6–§28).
 * Zero engineering jargon in normal customer UI; technical diagnostics live in Settings.
 */
@Composable
fun PlanadayActiveWorkspaceStage(
    state: PlanadayShellState,
    controller: PlanadayWorkspaceController,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalPlanadaySpacing.current
    Column(modifier = modifier.fillMaxSize().padding(spacing.density.itemPadding)) {
        // Clean Customer Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.activeRoute.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.activeRoute.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            val saveLabel = when (state.saveState) {
                SaveState.EDITING -> "Saving…"
                SaveState.SYNCED -> "Synced"
                SaveState.SYNC_PENDING -> "Sync pending"
                SaveState.SAVE_FAILED, SaveState.CONFLICT -> "Attention"
                else -> "Saved offline"
            }
            AssistChip(
                onClick = { controller.selectRoute(PlanadayRoute.SETTINGS) },
                label = { Text("$saveLabel · ${state.totalActiveRecords}") },
                modifier = Modifier.semantics { contentDescription = "Save status and item count" },
            )
        }

        state.statusBanner?.let { banner ->
            Spacer(Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { controller.setBanner(null) },
                        modifier = Modifier.heightIn(min = 32.dp).semantics { contentDescription = "Dismiss status banner" },
                    ) { Text("Dismiss") }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.activeRoute) {
                PlanadayRoute.TODAY, PlanadayRoute.HOME, PlanadayRoute.QUICK_GLANCE, PlanadayRoute.ALL_IN_ONE ->
                    TodayAndHomeDashboardStudio(state, controller, isCompact)
                PlanadayRoute.FLOW_CALENDAR ->
                    InteractiveFlowCalendarStudio(state, controller, isCompact, initialModePeriod = false)
                PlanadayRoute.PERIOD_CALENDAR ->
                    InteractiveFlowCalendarStudio(state, controller, isCompact, initialModePeriod = true)
                PlanadayRoute.TASK_CALENDAR ->
                    TasksAndKanbanStudio(state, controller, isCompact, initialBoardMode = false)
                PlanadayRoute.TASKS ->
                    TasksAndKanbanStudio(state, controller, isCompact, initialBoardMode = false)
                PlanadayRoute.KANBAN ->
                    TasksAndKanbanStudio(state, controller, isCompact, initialBoardMode = true)
                PlanadayRoute.CHORES, PlanadayRoute.HABITS, PlanadayRoute.GOALS ->
                    ChoresHabitsGoalsStudio(state.activeRoute, state, controller)
                PlanadayRoute.NOTES ->
                    NotesAndKnowledgeStudio(state, controller, isCompact)
                PlanadayRoute.DIARY ->
                    DiaryAndWellnessStudio(state, controller)
                PlanadayRoute.CLOCK_WORLD, PlanadayRoute.ALARMS, PlanadayRoute.TIMERS, PlanadayRoute.STOPWATCH, PlanadayRoute.COUNTDOWNS ->
                    RealTimeSuiteStudio(state.activeRoute, state, controller)
                PlanadayRoute.SPREADSHEETS ->
                    RealSpreadsheetStudio(state, controller, isCompact)
                PlanadayRoute.CALCULATOR ->
                    MathematicsAndCalculatorStudio(state, controller)
                PlanadayRoute.PHYSICAL_PLANNER ->
                    RealPhysicalPlannerStudio(state, controller, isCompact)
                PlanadayRoute.PRESENTATIONS ->
                    RealPresentationsStudio(state, controller, isCompact)
                PlanadayRoute.WHITEBOARD ->
                    RealWhiteboardStudio(state, controller)
                PlanadayRoute.BOOK_STUDIO ->
                    RealBookStudio(state, controller, isCompact)
                PlanadayRoute.GAME_DEV ->
                    RealGameDevStudio(state, controller)
                PlanadayRoute.PROJECTS ->
                    RealProjectsAndCustomSchemasStudio(state, controller)
                PlanadayRoute.CODE_STUDIO ->
                    SafeCodeStudio(state, controller)
                PlanadayRoute.LAB_WORKSPACE ->
                    ProfessionalLaboratoryStudio(state, controller)
                PlanadayRoute.RECIPES, PlanadayRoute.PANTRY, PlanadayRoute.MEAL_PLANNER, PlanadayRoute.GROCERIES, PlanadayRoute.FINANCE ->
                    KitchenAndFinanceStudio(state.activeRoute, state, controller)
                PlanadayRoute.HOUSEHOLD, PlanadayRoute.PEOPLE_GIFTS, PlanadayRoute.HEALTH_MEDS, PlanadayRoute.MEDIA_HOBBIES, PlanadayRoute.TRAVEL, PlanadayRoute.HOME_ASSETS ->
                    PersonalLifeModulesStudio(state.activeRoute, state, controller)
                PlanadayRoute.UNIVERSAL_FILES, PlanadayRoute.PHOTO_STUDIO ->
                    FilesAndPhotoStudio(state.activeRoute, state, controller)
                PlanadayRoute.AUTOMATIONS ->
                    RealAutomationStudio(state, controller)
                PlanadayRoute.SPACED_REVIEW ->
                    SpacedReviewStudio(state, controller)
                PlanadayRoute.TRASH ->
                    TrashRecoveryStudio(state, controller)
                PlanadayRoute.SETTINGS ->
                    SettingsBackupSyncDiagnosticsStudio(state, controller)
                else ->
                    ReportsDecisionsAndGuideStudio(state.activeRoute, state, controller)
            }
        }
    }
}
