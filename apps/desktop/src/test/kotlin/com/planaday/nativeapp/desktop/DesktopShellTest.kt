package com.planaday.nativeapp.desktop

import com.planaday.nativeapp.core.designtokens.*
import com.planaday.nativeapp.core.model.BuildIdentity
import com.planaday.nativeapp.core.model.Priority
import com.planaday.nativeapp.core.model.RecordKind
import com.planaday.nativeapp.core.persistence.JvmPlanadayDatabaseFactory
import com.planaday.nativeapp.core.persistence.LocalBlobStore
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.search.PlanadaySearchService
import com.planaday.nativeapp.core.security.JvmProtectedCredentialVault
import com.planaday.nativeapp.core.usecases.PlanadayUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class DesktopShellTest {
    @Test
    fun all54RoutesPresentRealGitShaPreferencesPersistAndMultiWindowSyncsLive() = runTest {
        // 1. All 54 named workspaces intact & BuildIdentity uses real Git SHA (never 'greenfield-dev')
        assertEquals(54, PlanadayRoute.entries.size)
        val refs = PlanadayRoute.entries.map { it.requirementRef }.toSet()
        for (i in 1..54) {
            assertTrue("C-${i.toString().padStart(4, '0')}" in refs)
        }
        val buildId = BuildIdentity()
        assertNotEquals("greenfield-dev", buildId.gitCommitSha, "BuildIdentity must use real Git commit SHA (§36)")
        assertTrue(buildId.gitCommitSha.length >= 7)

        val dir = Files.createTempDirectory("planaday-desktop-u2-").toFile()
        val dbFile = File(dir, BuildIdentity.CLIENT_DB_NAME)
        val vault = JvmProtectedCredentialVault(dir)
        var db1 = JvmPlanadayDatabaseFactory.createOnDisk(dbFile)
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstDeviceId: String
        try {
            val repo = PlanadayRepository(db1, LocalBlobStore(File(dir, "blobs_v1")))
            val ctrlWin1 = PlanadayWorkspaceController(repo, PlanadayUseCases(repo), PlanadaySearchService(repo), scope1, "windows-win1", vault)
            val ctrlWin2 = PlanadayWorkspaceController(repo, PlanadayUseCases(repo), PlanadaySearchService(repo), scope1, "windows-win2", vault)
            ctrlWin1.bootstrapFreshSuspend()
            ctrlWin2.bootstrapFreshSuspend()
            firstDeviceId = ctrlWin1.state.value.deviceId
            assertTrue(firstDeviceId.isNotBlank())
            assertEquals(0L, ctrlWin1.state.value.totalActiveRecords, "Fresh install must start with 0 user records (O-004)")

            // 2. Multi-Window Live Consistency (§32): Window 1 creates a Task -> Window 2 Flow receives it!
            ctrlWin1.quickCreateTaskSuspend("Multi-Window Sync Verification", Priority.HIGH, 45)
            val win2Tasks = ctrlWin2.observeKind(RecordKind.TASK, 50).first { it.isNotEmpty() }
            assertEquals("Multi-Window Sync Verification", win2Tasks.single().title)

            // 3. Persist appearance customization & OS-protected sync bearer token across restart (§7, §28)
            ctrlWin1.setThemeMode(PlanadayThemeMode.DEEP_SLATE_DARK)
            ctrlWin1.setAccentHex("#0F766E")
            ctrlWin1.setFontScale(1.15f)
            repo.saveProtectedSecret(vault, PlanadayWorkspaceController.SECRET_SYNC_TOKEN, "super_secret_bearer_token_2026")
            delay(150)

            // Verify raw SQLite preference value NEVER stores plaintext token!
            val rawPref = repo.getPreference("secret.${PlanadayWorkspaceController.SECRET_SYNC_TOKEN}")!!
            assertFalse(rawPref.contains("super_secret_bearer_token_2026"), "SQLite MUST NEVER hold plaintext bearer token!")
        } finally {
            scope1.cancel()
            db1.close()
        }

        // 4. Simulate full Windows application restart -> reopen same DB & vault -> verify persistence!
        val db2 = JvmPlanadayDatabaseFactory.createOnDisk(dbFile)
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo2 = PlanadayRepository(db2, LocalBlobStore(File(dir, "blobs_v1")))
            val ctrlRestart = PlanadayWorkspaceController(repo2, PlanadayUseCases(repo2), PlanadaySearchService(repo2), scope2, "windows-restart", vault)
            ctrlRestart.bootstrapFreshSuspend()

            assertEquals(firstDeviceId, ctrlRestart.state.value.deviceId, "Persistent deviceId must survive restart")
            assertEquals(PlanadayThemeMode.DEEP_SLATE_DARK, ctrlRestart.state.value.preferences.themeMode)
            assertEquals("#0F766E", ctrlRestart.state.value.preferences.accentHex)
            assertEquals(1.15f, ctrlRestart.state.value.preferences.fontScale, 0.01f)
            assertTrue(ctrlRestart.state.value.syncBearerTokenPresent, "Encrypted sync token must survive restart")
            assertEquals("super_secret_bearer_token_2026", repo2.readProtectedSecret(vault, PlanadayWorkspaceController.SECRET_SYNC_TOKEN))

            val hits = PlanadaySearchService(repo2).search("ws_personal", "Multi-Window")
            assertEquals(1, hits.size)
        } finally {
            scope2.cancel()
            db2.close()
            dir.deleteRecursively()
        }
    }
}
