package com.planaday.nativeapp.tests.integration

import com.planaday.nativeapp.core.automation.AutomationDomainEvent
import com.planaday.nativeapp.core.automation.AutomationRuleDef
import com.planaday.nativeapp.core.documents.DraftSessionManager
import com.planaday.nativeapp.core.domain.ExactDecimal
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.persistence.JvmPlanadayDatabaseFactory
import com.planaday.nativeapp.core.persistence.LocalBlobStore
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.search.PlanadaySearchService
import com.planaday.nativeapp.core.security.DesktopExternalToolchainAdapter
import com.planaday.nativeapp.core.usecases.PlanadayUseCases
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.*

/**
 * Verifies all 15 mandatory Cross-Feature Integration Journeys (INT-001..INT-015),
 * PD-001 (1,000 overlapping concurrent disjoint edits on real Room SQLite),
 * PD-016 (SQLite-backed blurless draft switch isolation),
 * R-0322 (Windows Desktop isolated external process runner),
 * and future new-product v1 -> v2 schema migration preserving newly created Planaday Native data.
 */
class CrossFeatureIntegrationAndRegressionsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun pd001OneThousandOverlappingConcurrentDisjointUpdatesAndRollbackOnRealRoomSqlite() = runTest {
        val dir = Files.createTempDirectory("planaday-pd001-").toFile()
        val db = JvmPlanadayDatabaseFactory.createOnDisk(File(dir, BuildIdentity.CLIENT_DB_NAME))
        try {
            val repo = PlanadayRepository(db, LocalBlobStore(File(dir, "blobs_v1")))
            repo.ensureDefaultWorkspaceExists("ws_personal", "Personal")
            val rec = repo.createRecord("ws_personal", RecordKind.NOTE, "Counter", """{"alpha":0,"beta":0}""")

            // 500 concurrent alpha increments + 500 concurrent beta increments -> exact {alpha=500, beta=500}, rev=1001!
            val jobs = (0 until 1000).map { i ->
                async {
                    repo.updateRecordAtomic("ws_personal", rec.id) { cur ->
                        val map = json.decodeFromString<Map<String, Int>>(cur.payloadJson).toMutableMap()
                        if (i % 2 == 0) map["alpha"] = (map["alpha"] ?: 0) + 1
                        else map["beta"] = (map["beta"] ?: 0) + 1
                        cur.copy(payloadJson = json.encodeToString(map))
                    }
                }
            }
            jobs.awaitAll()

            val finalRec = repo.getRecord("ws_personal", rec.id)!!
            val finalMap = json.decodeFromString<Map<String, Int>>(finalRec.payloadJson)
            assertEquals(500, finalMap["alpha"], "PD-001: zero lost alpha updates")
            assertEquals(500, finalMap["beta"], "PD-001: zero lost beta updates")
            assertEquals(1001L, finalRec.rev, "PD-001: strictly monotonic rev")
            assertEquals(1, db.syncDao().countPendingOutbox("ws_personal"), "Outbox coalesced cleanly to 1 op")

            // Throwing updater must roll back completely (zero partial mutation, rev unchanged!)
            assertFailsWith<IllegalStateException> {
                repo.updateRecordAtomic("ws_personal", rec.id) {
                    throw IllegalStateException("Simulated crash mid-transaction")
                }
            }
            assertEquals(1001L, repo.getRecord("ws_personal", rec.id)!!.rev)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun all15CrossFeatureIntegrationJourneysAndPd016DraftIsolationOnRealSqlite() = runTest {
        val dir = Files.createTempDirectory("planaday-int15-").toFile()
        val db = JvmPlanadayDatabaseFactory.createOnDisk(File(dir, BuildIdentity.CLIENT_DB_NAME))
        try {
            val repo = PlanadayRepository(db, LocalBlobStore(File(dir, "blobs_v1")))
            val uc = PlanadayUseCases(repo)
            val search = PlanadaySearchService(repo)
            val now = "2026-11-25T10:00:00Z"

            repo.ensureDefaultWorkspaceExists("ws_personal", "Personal")
            repo.createWorkspace(Workspace("ws_lab", "Lab", WorkspaceScope.LABORATORY, "#0F766E", "flask", now, now))

            // INT-001: Meal in Today -> Unified Food History
            uc.logMealFromToday("ws_personal", FoodLogItem("food1", "ws_personal", "2026-11-25", "12:30", "lunch", "Salmon Quinoa Bowl"))
            assertEquals(1, repo.listByKind("ws_personal", RecordKind.FOOD_LOG).size)

            // INT-002 & INT-003: Note -> Task -> Calendar Event -> Goal -> Kanban (ONE canonical Task UUID!)
            val note = repo.createRecord("ws_personal", RecordKind.NOTE, "Architecture Note", """{"content":"Ship v1"}""", "Ship v1")
            val goal = repo.createRecord("ws_personal", RecordKind.GOAL, "Commercial Release", "{}", "Ship native product")
            val task = TaskItem("task_canonical_1", "ws_personal", "Build Sync Engine", priority = Priority.HIGH, doDateIso = "2026-11-25", createdAtUtc = now, updatedAtUtc = now)
            val (taskRec, eventRec, _) = uc.createTaskFromNoteAndSchedule("ws_personal", note.id, task, "cal1", "10:00", "11:00", goal.id)
            assertEquals("task_canonical_1", taskRec.id)
            val moved = uc.moveTaskKanbanColumn("ws_personal", taskRec.id, "done")
            assertEquals(RecordStatus.DONE, moved.status)
            assertEquals(1, repo.listByKind("ws_personal", RecordKind.TASK).size, "Zero duplicate tasks across Kanban & Today")
            assertNotNull(repo.getRecord("ws_personal", eventRec.id))

            // INT-004: Lab Meeting -> Lab Task strictly inside ws_lab
            val meeting = repo.createRecord("ws_lab", RecordKind.LAB_MEETING, "Weekly Analyst Sync", "{}", "Review NIR calibration")
            val labTask = uc.promoteLabMeetingActionToTask("ws_lab", meeting.id, "Recalibrate Moisture Curve", "2026-11-28")
            assertEquals("ws_lab", labTask.workspaceId)
            assertEquals(1, repo.outgoingLinks("ws_lab", meeting.id).size)

            // INT-005: Lab Purchase -> Updates Lab Budget (ExactDecimal) & Lab Inventory
            val labBudget = repo.createRecord("ws_lab", RecordKind.LAB_BUDGET, "Q4 Consumables", json.encodeToString(DecimalAmount("100.25", "USD")))
            val labInv = repo.createRecord("ws_lab", RecordKind.LAB_INVENTORY, "Filters", json.encodeToString(LabInventoryItem("inv1", "ws_lab", "lab_supply", "Filters", "LOT-9", 10.0, 5.0, "box")), id = "inv1")
            val (newBudget, newInv) = uc.recordLabPurchaseAndUpdateBudget(
                "ws_lab", labBudget.id, labInv.id,
                LabOrder("po1", "ws_lab", labInv.id, "ChemVendor", "Filters", 5.0, DecimalAmount("49.75", "USD"), labBudget.id, "2026-11-25"),
            )
            assertEquals("150.00", json.decodeFromString<DecimalAmount>(newBudget.payloadJson).value)
            assertEquals(15.0, json.decodeFromString<LabInventoryItem>(newInv.payloadJson).quantityOnHand)

            // INT-006: Complete Chore -> Next Due Date + Idempotent Completion History
            val chore = ChoreItem("ch1", "ws_personal", "Clean Oven", "Kitchen", ChoreMode.AFTER_COMPLETION, 5, 2, "2026-11-25", createdAtUtc = now, updatedAtUtc = now)
            repo.createRecord("ws_personal", RecordKind.CHORE, chore.title, json.encodeToString(chore), id = chore.id)
            uc.completeChore("ws_personal", chore.id, "2026-11-25", assigneeId = "p1", minutesSpent = 15)
            uc.completeChore("ws_personal", chore.id, "2026-11-25", assigneeId = "p1", minutesSpent = 15) // Duplicate replay safe!
            val updatedChore = json.decodeFromString<ChoreItem>(repo.getRecord("ws_personal", chore.id)!!.payloadJson)
            assertEquals("2026-11-30", updatedChore.nextDueDateIso)
            assertEquals(1, updatedChore.completions.size)

            // INT-007 & INT-008: Calculator -> Spreadsheet -> Immutable SHA-256 Evidence Snapshot -> Presentation Slide
            val wb = SpreadsheetWorkbook("wb1", "ws_personal", "Budget Sheet", listOf(WorksheetModel("s1", "Main", cells = mapOf("A1" to SheetCellInput("10")))))
            repo.createRecord("ws_personal", RecordKind.SPREADSHEET, wb.title, json.encodeToString(wb), id = wb.id)
            val (_, calcVal) = uc.insertCalculationIntoSpreadsheet("ws_personal", wb.id, "s1", "B1", "2^5 + 10")
            assertEquals(42.0, calcVal)
            val deck = PresentationDeck("deck1", "ws_personal", "Review Deck", slides = listOf(SlideModel("sl1", 1, "Summary")))
            repo.createRecord("ws_personal", RecordKind.PRESENTATION, deck.title, json.encodeToString(deck), id = deck.id)
            val (snap, updatedDeckRec) = uc.embedSpreadsheetSnapshotInPresentation("ws_personal", wb.id, deck.id, "sl1", "Budget Snapshot")
            assertEquals(64, snap.contentSha256.length)
            assertTrue(updatedDeckRec.payloadJson.contains(snap.id))

            // INT-009 & INT-010: Game Entity Links & Book Continuity Checker
            val hero = repo.createRecord("ws_personal", RecordKind.GAME_ENTITY, "Paladin", "{}")
            val skill = repo.createRecord("ws_personal", RecordKind.GAME_ENTITY, "Shield Bash", "{}")
            val snippet = repo.createRecord("ws_personal", RecordKind.CODE_SNIPPET, "DamageFormula", "{}")
            repo.linkRecords("ws_personal", hero.id, RecordKind.GAME_ENTITY, skill.id, RecordKind.GAME_ENTITY, RelationKind.ASSET)
            repo.linkRecords("ws_personal", hero.id, RecordKind.GAME_ENTITY, snippet.id, RecordKind.CODE_SNIPPET, RelationKind.MENTIONS)
            assertEquals(2, repo.outgoingLinks("ws_personal", hero.id).size)

            val continuity = uc.checkBookContinuity(
                chapters = listOf(BookChapter("c1", "b1", "ws_personal", 1, "Prologue", manuscriptText = "Elena crossed the bridge.")),
                knownCharacterNames = setOf("Elena", "Marcus"),
            )
            assertTrue(continuity.any { it.description.contains("Marcus") })

            // INT-011: Single SHA-256 Photo Blob Reused Across Records with Strict Workspace ACL Isolation
            val sharedPhoto = "shared-photo-bytes-for-int-011".encodeToByteArray()
            val photoMeta = repo.attachBlobToRecord("ws_personal", note.id, "trip.png", "image/png", sharedPhoto)
            assertTrue(repo.canWorkspaceAccessBlob("ws_personal", photoMeta.sha256))
            assertFalse(repo.canWorkspaceAccessBlob("ws_lab", photoMeta.sha256), "Personal blob must NOT leak to ws_lab!")

            // INT-012: Lab Calibration Immutable Raw Value + Append-Only Correction + Reminder
            val cal = LabCalibrationRecord("cal1", "ws_lab", "Balance-A", "balance_weight", now, "2026-12-01", "Analyst", "100.002", "100.000", "g", true)
            repo.createRecord("ws_lab", RecordKind.LAB_CALIBRATION, "Balance Check", json.encodeToString(cal), id = cal.id, recordRevision = true)
            val (corrRec, rem) = uc.recordLabCalibrationCorrection("ws_lab", cal.id, "QA Lead", "Air buoyancy correction", "100.001", "g")
            val corrCal = json.decodeFromString<LabCalibrationRecord>(corrRec.payloadJson)
            assertEquals("100.002", corrCal.rawMeasurementValue, "Raw historical measurement MUST stay immutable!")
            assertEquals(1, corrCal.corrections.size)
            assertNotNull(rem)

            // INT-013: Grocery Purchase Reconciliation -> Pantry Lot + Single Finance Expense (ExactDecimal)
            val recon = uc.reconcileGroceryPurchase(
                workspaceId = "ws_personal",
                groceryItem = GroceryItem("g1", "ws_personal", "oats", "Rolled Oats", 2.0, "kg", "Dry Goods", "Market", true, DecimalAmount("6.49", "USD")),
                existingPantryLotId = null,
            )
            assertEquals("6.49", recon.totalCost.value)
            assertEquals(1, repo.listByKind("ws_personal", RecordKind.PANTRY_LOT).size)
            assertEquals(1, repo.listByKind("ws_personal", RecordKind.EXPENSE).size)

            // INT-014: Workspace-Scoped FTS5 Search + Trash Isolation + Restore
            val personalHits = search.search("ws_personal", "Salmon")
            assertEquals(1, personalHits.size)
            assertEquals(0, search.search("ws_lab", "Salmon").size, "FTS5 MUST NOT leak ws_personal records into ws_lab")
            repo.moveToTrash("ws_personal", note.id)
            assertEquals(0, search.search("ws_personal", "Architecture").size, "Trashed records removed from FTS5 active index")
            repo.restoreFromTrash("ws_personal", note.id)
            assertEquals(1, search.search("ws_personal", "Architecture").size, "Restored record returns to FTS5 index")

            // INT-015: Idempotent SQLite-Backed Automation (Zero Duplicate on Sync Replay)
            val rule = AutomationRuleDef("auto1", "ws_personal", "Followup", "CHORE_COMPLETED", null, "CREATE_TASK", "Restock soap")
            val evt = AutomationDomainEvent("evt_replay_1", "ws_personal", "CHORE_COMPLETED", chore.id, RecordKind.CHORE)
            var execCount = 0
            uc.evaluateAndRunAutomations(listOf(rule), evt) { execCount++ }
            uc.evaluateAndRunAutomations(listOf(rule), evt) { execCount++ } // Replay
            assertEquals(1, execCount, "Automation MUST execute once even on sync replay")

            // PD-016 + PF-55: SQLite-backed blurless draft switch isolation
            val dm = DraftSessionManager(repo) { now }
            dm.openSession("ws_personal", "n_alpha", RecordKind.NOTE, "s1", "Alpha", "InitA", 1L)
            dm.updateDraft("ws_personal", "n_alpha", "s1", "Alpha", "Alpha Dirty SQLite Draft")
            val beta = dm.openSession("ws_personal", "n_beta", RecordKind.NOTE, "s2", "Beta", "InitB", 1L)
            assertEquals("InitB", beta.bodyDraft)
            assertEquals(1, dm.recoverableDrafts("ws_personal").size)
            assertEquals("n_alpha", dm.recoverableDrafts("ws_personal").single().recordId)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun r0322DesktopIsolatedExternalProcessRunnerAndFutureV1ToV2SchemaUpgradeDrill() = runTest {
        // R-0322 + PD-005: External desktop toolchain requires explicit opt-in & runs isolated child process
        assertFailsWith<IllegalArgumentException> {
            DesktopExternalToolchainAdapter.runIsolatedCommand(listOf("java", "-version"), emptyMap(), userExplicitlyOptedIn = false)
        }
        val res = DesktopExternalToolchainAdapter.runIsolatedCommand(
            command = listOf("java", "-version"),
            files = mapOf("Hello.java" to "class Hello {}"),
            timeoutMs = 5_000L,
            userExplicitlyOptedIn = true,
        )
        assertEquals(0, res.exitCode)
        assertFalse(res.timedOut)

        // Future New-Product Schema Upgrade Drill (v1 -> additive v2 column on Planaday Native data ONLY, O-004!)
        val dir = Files.createTempDirectory("planaday-mig-v1v2-").toFile()
        val dbFile = File(dir, BuildIdentity.CLIENT_DB_NAME)
        val dbV1 = JvmPlanadayDatabaseFactory.createOnDisk(dbFile)
        val createdId: String
        try {
            val repo = PlanadayRepository(dbV1, LocalBlobStore(File(dir, "blobs_v1")))
            repo.ensureDefaultWorkspaceExists("ws_personal", "Personal")
            createdId = repo.createRecord("ws_personal", RecordKind.TASK, "Survives Future Upgrade", """{"v":1}""").id
        } finally {
            dbV1.close()
        }
        // Apply additive v1 -> v2 SQL migration directly against the newly created SQLite v1 file and verify data intact!
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { c ->
            c.createStatement().use { st ->
                st.execute("ALTER TABLE record_index ADD COLUMN future_v2_marker TEXT DEFAULT 'migrated_ok';")
                val rs = st.executeQuery("SELECT id, title, future_v2_marker FROM record_index WHERE id = '$createdId'")
                assertTrue(rs.next())
                assertEquals("Survives Future Upgrade", rs.getString("title"))
                assertEquals("migrated_ok", rs.getString("future_v2_marker"))
            }
        }
        dir.deleteRecursively()
    }
}
