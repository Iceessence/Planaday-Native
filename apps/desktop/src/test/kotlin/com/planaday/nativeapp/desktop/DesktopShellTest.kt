package com.planaday.nativeapp.desktop

import com.planaday.nativeapp.core.designtokens.PlanadayRoute
import com.planaday.nativeapp.core.designtokens.PlanadayThemeMode
import com.planaday.nativeapp.core.designtokens.PlanadayWorkspaceController
import com.planaday.nativeapp.core.model.BuildIdentity
import com.planaday.nativeapp.core.model.Priority
import com.planaday.nativeapp.core.persistence.JvmPlanadayDatabaseFactory
import com.planaday.nativeapp.core.persistence.LocalBlobStore
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.search.PlanadaySearchService
import com.planaday.nativeapp.core.usecases.PlanadayUseCases
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class DesktopShellTest {
    @Test
    fun all54NamedPlanadayRoutesPresentAndFreshDesktopControllerStartsEmpty() = runTest {
        // Verify all 54 original C-0001..C-0054 named workspaces exist cleanly in the native route catalog
        assertEquals(54, PlanadayRoute.entries.size)
        val refs = PlanadayRoute.entries.map { it.requirementRef }.toSet()
        for (i in 1..54) {
            val expected = "C-${i.toString().padStart(4, '0')}"
            assertTrue(expected in refs, "Missing named workspace route for $expected")
        }

        // Verify fresh Windows Desktop controller initializes empty DB (O-004) and handles real CRUD + FTS5 search
        val dir = Files.createTempDirectory("planaday-desktop-test-").toFile()
        val db = JvmPlanadayDatabaseFactory.createOnDisk(File(dir, BuildIdentity.CLIENT_DB_NAME))
        try {
            val repo = PlanadayRepository(db, LocalBlobStore(File(dir, "blobs_v1")))
            val ctrl = PlanadayWorkspaceController(repo, PlanadayUseCases(repo), PlanadaySearchService(repo), this, "windows-test")
            ctrl.bootstrapFreshSuspend()

            assertEquals(0L, ctrl.state.value.totalActiveRecords, "Fresh desktop install MUST start with 0 user records (O-004)")
            assertEquals(2, ctrl.state.value.workspaces.size)

            ctrl.quickCreateTaskSuspend("Prepare Windows MSI Package", Priority.HIGH, 45)
            assertEquals(1L, ctrl.state.value.totalActiveRecords)
            assertEquals("Prepare Windows MSI Package", ctrl.state.value.tasks.single().title)

            ctrl.setThemeMode(PlanadayThemeMode.HIGH_CONTRAST_AAA)
            assertEquals(PlanadayThemeMode.HIGH_CONTRAST_AAA, ctrl.state.value.themeMode)

            ctrl.selectRoute(PlanadayRoute.LAB_WORKSPACE)
            assertEquals(PlanadayRoute.LAB_WORKSPACE, ctrl.state.value.activeRoute)

            // Verify FTS5 search finds the task created via the desktop controller
            val hits = PlanadaySearchService(repo).search("ws_personal", "Windows MSI")
            assertEquals(1, hits.size)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
