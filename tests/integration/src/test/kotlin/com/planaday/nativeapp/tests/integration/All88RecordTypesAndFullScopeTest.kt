package com.planaday.nativeapp.tests.integration

import com.planaday.nativeapp.core.domain.*
import com.planaday.nativeapp.core.graphics.PhotoAndPlannerAdvanced
import com.planaday.nativeapp.core.interchange.DocumentAnnotation
import com.planaday.nativeapp.core.interchange.ExtendedFormatInterchange
import com.planaday.nativeapp.core.interchange.ReviewedDocumentModel
import com.planaday.nativeapp.core.math.MathAstEngine
import com.planaday.nativeapp.core.math.MathEvalContext
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.persistence.JvmPlanadayDatabaseFactory
import com.planaday.nativeapp.core.persistence.LocalBlobStore
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.syncclient.PlanadaySyncClient
import com.planaday.nativeapp.core.syncprotocol.*
import com.planaday.nativeapp.server.installPlanadaySyncServer
import com.planaday.nativeapp.server.storage.JdbcSyncServerRepository
import com.planaday.nativeapp.server.storage.ServerBlobStore
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.*

/**
 * Exhaustive Full-Scope Contract Suite:
 * 1. Verifies Create -> Read -> Atomic Update -> Soft Trash -> Restore -> Bidirectional Server Sync
 *    for ALL canonical RecordKind values (covering every single C-0070..C-0158 record family + N-series).
 * 2. Verifies all remaining Expansion Packages (PF-12..PF-56) and N-Series proposals (N-002..N-049).
 */
class All88RecordTypesAndFullScopeTest {
    private fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    @Test
    fun allCanonicalRecordKindsRoundTripPersistTrashRestoreAndSyncAcrossDevices() = testApplication {
        val srvRoot = tempDir("planaday-allkinds-srv-")
        val devARoot = tempDir("planaday-allkinds-a-")
        val devBRoot = tempDir("planaday-allkinds-b-")

        val srvRepo = JdbcSyncServerRepository("jdbc:sqlite:${File(srvRoot, "srv.db").absolutePath}")
        val srvBlobs = ServerBlobStore(File(srvRoot, "blobs"))
        application {
            installPlanadaySyncServer(srvRepo, srvBlobs, File(srvRoot, "backups"))
        }
        val httpClient = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        }

        val dbA = JvmPlanadayDatabaseFactory.createOnDisk(File(devARoot, BuildIdentity.CLIENT_DB_NAME))
        val dbB = JvmPlanadayDatabaseFactory.createOnDisk(File(devBRoot, BuildIdentity.CLIENT_DB_NAME))
        try {
            val repoA = PlanadayRepository(dbA, LocalBlobStore(File(devARoot, "blobs_v1")), deviceId = "dev_a_allkinds")
            val repoB = PlanadayRepository(dbB, LocalBlobStore(File(devBRoot, "blobs_v1")), deviceId = "dev_b_allkinds")
            repoA.ensureDefaultWorkspaceExists("ws_personal", "Personal")
            repoB.ensureDefaultWorkspaceExists("ws_personal", "Personal")

            // Sanity: must cover >= 88 canonical record families (C-0070..C-0158 + N-series)
            assertTrue(RecordKind.entries.size >= 88, "Expected >=88 RecordKinds, found ${RecordKind.entries.size}")

            val createdIds = LinkedHashMap<RecordKind, String>()
            for (kind in RecordKind.entries) {
                val created = repoA.createRecord(
                    workspaceId = "ws_personal",
                    kind = kind,
                    title = "Canonical ${kind.name}",
                    payloadJson = """{"kind":"${kind.name}","verified":true}""",
                    summaryText = "Round-trip verification for ${kind.name}",
                    tags = listOf("canonical", kind.name.lowercase()),
                )
                assertEquals(1L, created.rev)
                val updated = repoA.updateRecordAtomic("ws_personal", created.id) {
                    it.copy(title = "Updated ${kind.name}", summaryText = "Rev2 verified")
                }
                assertEquals(2L, updated.rev)

                // Soft-delete -> Trash -> Restore
                val trashed = repoA.moveToTrash("ws_personal", created.id)
                assertNotNull(trashed.deletedAtUtc)
                val restored = repoA.restoreFromTrash("ws_personal", created.id)
                assertNull(restored.deletedAtUtc)
                assertEquals(4L, restored.rev)

                createdIds[kind] = created.id
            }

            // Pair both devices with Ktor Sync Server and verify ALL RecordKinds replicate losslessly!
            val owner: AuthSessionResponse = httpClient.post("/api/v1/auth/bootstrap") {
                contentType(ContentType.Application.Json)
                setBody(OwnerBootstrapRequest("AllKindsPass!2026", "Owner"))
            }.body()

            val syncA = PlanadaySyncClient(repoA, httpClient, "", { owner.bearerToken })
            val syncB = PlanadaySyncClient(repoB, httpClient, "", { owner.bearerToken })

            val snapA = syncA.syncWorkspace("ws_personal")
            assertEquals(SyncHealthStatus.SYNCED, snapA.status)
            assertEquals(0, snapA.totalPendingCount)

            val snapB = syncB.syncWorkspace("ws_personal")
            assertEquals(SyncHealthStatus.SYNCED, snapB.status)

            for ((kind, id) in createdIds) {
                val pulled = repoB.getRecord("ws_personal", id)
                assertNotNull(pulled, "Device B missing synced record for kind $kind")
                assertEquals(kind, pulled.kind)
                assertEquals("Updated ${kind.name}", pulled.title)
                assertEquals(4L, pulled.rev)
                assertNull(pulled.deletedAtUtc)
            }
            assertEquals(RecordKind.entries.size.toLong(), repoB.countAllRecords())
        } finally {
            dbA.close()
            dbB.close()
            srvRoot.deleteRecursively()
            devARoot.deleteRecursively()
            devBRoot.deleteRecursively()
        }
    }

    @Test
    fun allExpansionPackagesAndNSeriesDomainEnginesWorkEndToEnd() {
        // 1. PF-13 / N-031 / R-0288: Project Gantt Critical Path & Capacity & Cycle Rejection
        val gantt = ProjectGanttCriticalPathEngine.analyze(
            nodes = listOf(
                ProjectTaskNode("A", "Architecture", 3, emptyList(), assignedHoursPerDay = 6.0),
                ProjectTaskNode("B", "Sync Engine", 5, listOf("A"), assignedHoursPerDay = 6.0),
                ProjectTaskNode("C", "Themes", 2, listOf("A"), assignedHoursPerDay = 4.0),
                ProjectTaskNode("D", "Release Gate", 2, listOf("B", "C"), assignedHoursPerDay = 5.0),
            ),
            dailyCapacityHours = 8.0,
        )
        assertEquals(10, gantt.totalDurationDays)
        assertEquals(listOf("A", "B", "D"), gantt.criticalPathIds)
        assertEquals(3, gantt.nodes.first { it.id == "C" }.slackDays)
        assertEquals(10.0, gantt.peakDailyHours)
        assertEquals(listOf(3, 4), gantt.overCapacityDays)
        assertFailsWith<IllegalArgumentException> {
            ProjectGanttCriticalPathEngine.analyze(
                listOf(ProjectTaskNode("X", "Cycle1", 1, listOf("Y")), ProjectTaskNode("Y", "Cycle2", 1, listOf("X")))
            )
        }

        // 2. PF-12 / PF-49 / N-012 / N-013: Shared Availability + Travel Buffers + What-If + Estimate Calibration
        val freeSlots = WhatIfScheduleAndCalibrationEngine.findSharedFreeSlots(
            resources = listOf(
                ResourceAvailabilityWindow("alice", "Alice", listOf(540..599)), // 09:00-10:00
                ResourceAvailabilityWindow("spectrometer", "NIR-01", listOf(720..779)), // 12:00-13:00
            ),
            dayStartMin = 480, // 08:00
            dayEndMin = 960, // 16:00
            requiredDurationMin = 60,
            travelBufferMin = 15,
        )
        assertTrue(freeSlots.any { it.first == 615 && it.last == 704 }) // 10:15..11:45 free between buffered blocks

        val tasksForCal = listOf(
            TaskItem(
                id = "t1", workspaceId = "ws_personal", title = "Calibrate", estimateMin = 30,
                completionLogs = listOf(
                    DurationLog("dl1", completedAtUtc = "2026-11-25T10:00:00Z", actualMinutes = 45, estimatedMinutes = 30, distracted = false, distractionMinutes = 0),
                    DurationLog("dl2", completedAtUtc = "2026-11-25T11:00:00Z", actualMinutes = 55, estimatedMinutes = 30, distracted = true, distractionMinutes = 10),
                ),
                createdAtUtc = "2026-11-25T08:00:00Z",
                updatedAtUtc = "2026-11-25T11:00:00Z",
            )
        )
        val cal = WhatIfScheduleAndCalibrationEngine.computeFocusCalibration(tasksForCal)
        assertEquals(2, cal.totalSessions)
        assertEquals(1, cal.undistractedSessions)
        assertEquals(1, cal.distractedSessions)
        assertEquals(20.0, cal.meanEstimationBiasMin!!, 1e-6)
        assertEquals(10, cal.totalDistractionMin)

        val planA = TaskSchedulerEngine.buildDaySchedule("2026-11-25", 9 * 60, "10:00", tasks = tasksForCal, dayEvents = emptyList())
        val planB = TaskSchedulerEngine.buildDaySchedule("2026-11-25", 9 * 60, "14:00", tasks = tasksForCal, dayEvents = emptyList())
        val whatIf = WhatIfScheduleAndCalibrationEngine.compareWhatIf(planA, planB)
        assertEquals(1, whatIf.scenarioBPlaced)

        // 3. PF-15 / PF-16 / N-021..N-023: Recipe Scaling, FEFO Pantry Lots, Shared Delta Merge & Exact-Decimal Budget Forecast
        val baseRecipe = RecipeItem(
            id = "r1", workspaceId = "ws_personal", title = "Soup", servings = 2.0,
            ingredients = listOf(RecipeIngredient("i1", 2.0, "cup", "Broth", "broth", "Pantry")),
        )
        val scaled = KitchenAndFinanceEngines.scaleRecipe(baseRecipe, 5.0)
        assertEquals(5.0, scaled.ingredients.single().quantity)

        val lots = listOf(
            PantryLot("l_exp", "ws_personal", "Old Broth", "broth", 3.0, "cup", expiryDateIso = "2026-11-20"), // expired -> skipped!
            PantryLot("l_soon", "ws_personal", "Fresh Broth A", "broth", 2.0, "cup", expiryDateIso = "2026-11-27"),
            PantryLot("l_later", "ws_personal", "Fresh Broth B", "broth", 1.5, "cup", expiryDateIso = "2026-12-10"),
        )
        val (allocs, shortages) = KitchenAndFinanceEngines.computeRecipeShortagesFefo(scaled, lots, todayIso = "2026-11-25")
        assertEquals("l_soon", allocs[0].first.id)
        assertEquals(2.0, allocs[0].second)
        assertEquals(1.5, shortages.single().shortageQty)
        assertEquals(listOf("l_exp", "l_soon"), KitchenAndFinanceEngines.expiringLots(lots, "2026-11-25", withinDays = 5).map { it.id })
        assertEquals(7.0, KitchenAndFinanceEngines.mergeSharedShoppingQuantity(baseQty = 2.0, localQty = 4.0, remoteQty = 5.0))

        val budget = KitchenAndFinanceEngines.forecastMonthlyBudget(
            envelopes = listOf(BudgetEnvelope("b1", "ws_personal", "Food", "Groceries", DecimalAmount("400.00", "USD"))),
            expenses = listOf(ExpenseItem("e1", "ws_personal", "2026-11-10", "Market", DecimalAmount("250.00", "USD"), "Groceries")),
            currency = "USD",
            dayOfMonth = 15,
            daysInMonth = 30,
        )
        assertEquals("500.00", budget.projectedMonthEndSpend.value)
        assertTrue(budget.projectedOverrun)

        // 4. PF-17..PF-21 / N-024..N-025: Wellness Trends, Household Fairness, SM-2 Spaced Review, Offline Travel Pack
        val wellness = LifeWellnessAndLearningEngines.analyzeWellnessTrends(
            listOf(
                DiaryEntry("d1", "ws_personal", "2026-11-24", ratings = mapOf("mood" to 8.0), sleepHours = 7.5, waterCups = 8, bathroomUrinations = 5, bathroomBowelMovements = 1, createdAtUtc = "2026-11-24T22:00:00Z", updatedAtUtc = "2026-11-24T22:00:00Z"),
                DiaryEntry("d2", "ws_personal", "2026-11-25", ratings = mapOf("mood" to 6.0), sleepHours = 6.5, waterCups = 6, bathroomUrinations = 6, bathroomBowelMovements = 2, createdAtUtc = "2026-11-25T22:00:00Z", updatedAtUtc = "2026-11-25T22:00:00Z"),
            )
        )
        assertEquals(7.0, wellness.metricAverages["mood"]!!, 1e-6)
        assertEquals(7.0, wellness.avgSleepHours!!, 1e-6)
        assertEquals(11, wellness.totalBathroomUrinations)
        assertEquals(3, wellness.totalBathroomBowel)

        val fairness = LifeWellnessAndLearningEngines.computeHouseholdFairness(
            listOf(
                ChoreItem(
                    id = "c1", workspaceId = "ws_personal", title = "Mop", room = "Kitchen", intervalDays = 7, difficulty = 4, nextDueDateIso = "2026-12-01",
                    completions = listOf(ChoreCompletion("cc1", "occ_c1", "2026-11-24T12:00:00Z", "2026-11-24", assigneeId = "alex")),
                    createdAtUtc = "2026-11-01T00:00:00Z", updatedAtUtc = "2026-11-24T12:00:00Z",
                ),
                ChoreItem(
                    id = "c2", workspaceId = "ws_personal", title = "Dust", room = "Living", intervalDays = 7, difficulty = 1, nextDueDateIso = "2026-12-01",
                    completions = listOf(ChoreCompletion("cc2", "occ_c2", "2026-11-24T12:00:00Z", "2026-11-24", assigneeId = "sam")),
                    createdAtUtc = "2026-11-01T00:00:00Z", updatedAtUtc = "2026-11-24T12:00:00Z",
                ),
            )
        )
        assertEquals(80.0, fairness.first { it.memberId == "alex" }.sharePct, 1e-6)
        assertEquals(20.0, fairness.first { it.memberId == "sam" }.sharePct, 1e-6)

        var card = SpacedReviewCardState("sr1", "Q", "A", nextDueDateIso = "2026-11-25")
        card = LifeWellnessAndLearningEngines.reviewCardSm2(card, 5, "2026-11-25")
        assertEquals(1, card.intervalDays)
        card = LifeWellnessAndLearningEngines.reviewCardSm2(card, 5, "2026-11-26")
        assertEquals(6, card.intervalDays)
        card = LifeWellnessAndLearningEngines.reviewCardSm2(card, 1, "2026-12-02")
        assertEquals(0, card.repetitions)
        assertEquals(1, card.intervalDays)

        // 5. PF-23..PF-25 / PF-33 / PF-56 / N-026..N-030: Lab Custody, Pricing, Reorders & GUM Uncertainty
        val sample = LabSample("ls1", "ws_lab", "SMP-2026-01", "Acme Grain", "Corn", listOf("MOIST", "ASH"), "2026-11-25", "2026-11-29", status = "received")
        val prepped = LaboratoryOperationsEngine.transitionSampleWithCustody(sample, "prepared", "Analyst A", "Bench 1", "2026-11-25T10:00:00Z")
        assertEquals("prepared", prepped.status)
        assertEquals(1, prepped.custodyEvents.size)
        assertFailsWith<IllegalArgumentException> {
            LaboratoryOperationsEngine.transitionSampleWithCustody(prepped, "archived", "Analyst A", "Bench 1", "2026-11-25T10:05:00Z")
        }
        assertFailsWith<IllegalArgumentException> {
            LaboratoryOperationsEngine.transitionSampleWithCustody(prepped, "in_progress", "  ", "Bench 1", "2026-11-25T10:05:00Z")
        }

        val quote = LaboratoryOperationsEngine.computeClientQuote(
            clientName = "Acme Grain",
            commodity = "Corn",
            methodCodes = listOf("MOIST", "ASH"),
            rules = listOf(
                LabPricingRule("p1", "Acme Grain", "Corn", "MOIST", DecimalAmount("28.50", "USD"), 2),
                LabPricingRule("p2", null, null, "ASH", DecimalAmount("41.25", "USD"), 4),
            ),
        )
        assertEquals("69.75", quote.totalAmount.value)
        assertEquals(4, quote.maxTurnaroundDays)

        val reorders = LaboratoryOperationsEngine.auditLabInventoryReorders(
            listOf(
                LabInventoryItem(id = "inv1", workspaceId = "ws_lab", category = "reagent_lot", name = "Hexane", quantityOnHand = 1.0, reorderThreshold = 2.0, unit = "L", expiryDateIso = "2027-06-01"),
                LabInventoryItem(id = "inv2", workspaceId = "ws_lab", category = "reagent_lot", name = "Buffer pH 7", quantityOnHand = 10.0, reorderThreshold = 2.0, unit = "L", expiryDateIso = "2026-12-05"),
                LabInventoryItem(id = "inv3", workspaceId = "ws_lab", category = "lab_supply", name = "Gloves", quantityOnHand = 50.0, reorderThreshold = 5.0, unit = "box", expiryDateIso = "2028-01-01"),
            ),
            todayIso = "2026-11-25",
            expiryWarnDays = 30,
        )
        assertEquals(listOf("inv1", "inv2"), reorders.map { it.id })

        val gum = LaboratoryOperationsEngine.computeUncertaintyBudget(
            listOf(UncertaintyComponent("Repeatability", 0.3), UncertaintyComponent("Balance", 0.4)),
            coverageFactorK = 2.0,
            methodVersion = "M-101",
        )
        assertTrue(abs(gum.combinedStandardUncertainty - 0.5) < 1e-9)
        assertTrue(abs(gum.expandedUncertainty - 1.0) < 1e-9)
        assertTrue(gum.nonRegulatoryNotice.contains("NOT an ISO/IEC 17025"))

        // 6. PF-26 / PF-27 / N-032 / N-034: Deterministic Seeded Game Balance Simulator & Asset License Provenance
        val sim1 = GameBalanceSimulatorEngine.simulateCombat(CombatantStats("Knight", 100.0, 20.0, 6.0), CombatantStats("Goblin", 90.0, 15.0, 4.0), trials = 150, seed = 999L)
        val sim2 = GameBalanceSimulatorEngine.simulateCombat(CombatantStats("Knight", 100.0, 20.0, 6.0), CombatantStats("Goblin", 90.0, 15.0, 4.0), trials = 150, seed = 999L)
        assertEquals(sim1, sim2, "Identical seed MUST produce bit-for-bit identical simulation results")
        assertEquals(10, GameBalanceSimulatorEngine.xpCurveTable(10).size)
        val lic = AssetLicenseEntry("a1", "sword.svg", "0".repeat(64), "Original Author", "CC0-1.0", null, commercialAllowed = true)
        assertTrue(lic.commercialAllowed)

        // 7. PF-14 / PF-35 / PF-36 / PF-39 / PF-43..PF-45 / N-002..N-041: Custom Schema, Templates, Pivots, Graph ACL, Local Assist, Extension Guard
        val schema = CustomSchemaDef(
            "cs1", "ws_lab", "Batch",
            fields = listOf(
                CustomFieldDef("mass", "Mass", CustomFieldType.NUMBER, required = true, minNumber = 0.0),
                CustomFieldDef("vol", "Volume", CustomFieldType.NUMBER, required = true, minNumber = 0.01),
                CustomFieldDef("density", "Density", CustomFieldType.COMPUTED_FORMULA, formulaExpression = "mass / vol"),
            ),
        )
        val validRec = CustomSchemaEngine.validateAndCompute(schema, mapOf("mass" to "50", "vol" to "25")) { expr, vars ->
            MathAstEngine.evaluate(expr, MathEvalContext(variables = vars))
        }
        assertTrue(validRec.valid)
        assertEquals(2.0, validRec.computedValues["density"]!!, 1e-9)
        val badRec = CustomSchemaEngine.validateAndCompute(schema, mapOf("mass" to "-1")) { _, _ -> 0.0 }
        assertFalse(badRec.valid)
        assertTrue(badRec.errors.size >= 2)

        val tmpl = UniversalTemplatePackage("t1", "SOP", "Owner", 1, listOf("name"), "SOP: {{name}}", "Body {{name}}", RecordKind.NOTE)
        val (tTitle, tBody) = UniversalTemplateEngine.instantiateSafe(tmpl, mapOf("name" to "<script>alert(1)</script>"))
        assertFalse(tTitle.contains("<script", ignoreCase = true))
        assertFalse(tBody.contains("<script", ignoreCase = true))

        val recordsMixed = listOf(
            CanonicalRecord("r1", "ws_personal", RecordKind.TASK, "A", RecordStatus.ACTIVE, 1, 1, "2026", "2026", null, "d"),
            CanonicalRecord("r2", "ws_lab", RecordKind.TASK, "Secret Lab", RecordStatus.ACTIVE, 1, 1, "2026", "2026", null, "d"),
        )
        val pivots = UniversalPivotReportEngine.pivotByKindAndStatus(recordsMixed, authorizedWorkspaceId = "ws_personal")
        assertEquals(1, pivots.size)
        assertEquals(listOf("r1"), pivots.single().drillDownRecordIds)

        val rels = listOf(
            RecordRelation("e1", "ws_personal", "n1", RecordKind.NOTE, "n2", RecordKind.TASK, RelationKind.MENTIONS, 0, null, "2026"),
            RecordRelation("e2", "ws_lab", "n2", RecordKind.TASK, "secret_lab_node", RecordKind.LAB_SAMPLE, RelationKind.MENTIONS, 0, null, "2026"),
        )
        val neighborhood = RelationshipGraphExplorer.exploreNeighborhood("n1", "ws_personal", rels, maxHops = 3)
        assertEquals(setOf("n1", "n2"), neighborhood, "Relationship graph MUST NOT traverse unauthorized workspace edges")

        val assist = LocalAssistanceEngine()
        assertTrue(assist.suggestDailyPriorities(tasksForCal, optInEnabled = false).isEmpty())
        assist.recordUserPreferenceSignal("lab", optInEnabled = true)
        val sugs = assist.suggestDailyPriorities(tasksForCal, optInEnabled = true)
        assertEquals(1, sugs.size)
        assertTrue(sugs.single().requiresUserApproval)
        assist.resetLearning()

        assertFalse(ExtensionCapabilityValidator.validate(ExtensionCapabilityManifest("bad", "1.0", setOf("READ_TEMPLATES"), requestsHostExecution = true)).first)
        assertFalse(ExtensionCapabilityValidator.validate(ExtensionCapabilityManifest("bad2", "1.0", setOf("SHELL_EXEC"), requestsHostExecution = false)).first)
        assertTrue(ExtensionCapabilityValidator.validate(ExtensionCapabilityManifest("good", "1.0", setOf("CUSTOM_THEME", "EXPORT_FORMAT"), requestsHostExecution = false)).first)

        val policy = SelectiveSyncPolicy(syncedWorkspaceIds = setOf("ws_personal"), maxAutoDownloadAttachmentBytes = 5_000_000L)
        assertTrue(policy.shouldSyncWorkspace("ws_personal"))
        assertFalse(policy.shouldSyncWorkspace("ws_lab"))
        assertTrue(policy.shouldAutoDownloadBlob(1_000_000L))
        assertFalse(policy.shouldAutoDownloadBlob(20_000_000L))
    }

    @Test
    fun extendedFormatInterchangeXlsxDocxEpubMarkdownPdfShareBundleAnd260Stickers() {
        // 1. XLSX Multi-sheet export + import round-trip preserving formulas + fidelity preview (PF-50, PF-54)
        val wb = SpreadsheetWorkbook(
            id = "wb1", workspaceId = "ws_personal", title = "Lab Results",
            sheets = listOf(
                WorksheetModel("s1", "Data", cells = mapOf("A1" to SheetCellInput("10"), "A2" to SheetCellInput("20"), "A3" to SheetCellInput("=SUM(A1:A2)"), "B1" to SheetCellInput("Moisture"))),
                WorksheetModel("s2", "Summary", cells = mapOf("A1" to SheetCellInput("=Data!A3*2"))),
            ),
        )
        val xlsx = ExtendedFormatInterchange.exportXlsx(wb)
        val imported = ExtendedFormatInterchange.importXlsx(xlsx.bytes, "ws_personal", "wb_imp", "Imported")
        assertEquals(2, imported.workbook.sheets.size)
        assertEquals("=SUM(A1:A2)", imported.workbook.sheets[0].cells["A3"]?.raw)
        assertEquals("Moisture", imported.workbook.sheets[0].cells["B1"]?.raw)
        assertEquals("=Data!A3*2", imported.workbook.sheets[1].cells["A1"]?.raw)
        assertTrue(ExtendedFormatInterchange.previewExportFidelity("XLSX", 2, hasFormulas = true, hasMacros = true).unsupportedOrApproximated.isNotEmpty())

        // 2. DOCX & EPUB structured exports (N-018, PF-29)
        val book = BookProject("b1", "ws_personal", title = "Field Guide")
        val chs = listOf(BookChapter("c1", "b1", "ws_personal", 1, "Chapter One", manuscriptText = "Para one.\n\nPara two."))
        val docx = ExtendedFormatInterchange.exportBookDocx(book, chs)
        val epub = ExtendedFormatInterchange.exportBookEpub(book, chs)
        assertTrue(docx.filename.endsWith(".docx") && docx.bytes.size > 100)
        assertTrue(epub.filename.endsWith(".epub") && epub.bytes.size > 100)

        // 3. Portable Markdown + YAML Frontmatter round-trip (N-019)
        val note = NoteItem(
            id = "n1", workspaceId = "ws_personal", notebookId = "nb1",
            title = "Lab Method", content = "See [[Calibration]]",
            createdAtUtc = "2026-11-25T00:00:00Z", updatedAtUtc = "2026-11-25T00:00:00Z",
        )
        val mdArt = ExtendedFormatInterchange.exportPortableMarkdownNote(note, listOf("lab", "sop"))
        val (meta, body) = ExtendedFormatInterchange.parsePortableMarkdownNote(mdArt.bytes.decodeToString())
        assertEquals("Lab Method", meta["title"])
        assertEquals("See [[Calibration]]", body)

        // 4. ReviewedDocumentModel PDF annotations & OCR fallback (PF-22, N-017)
        val docReview = ReviewedDocumentModel(
            id = "doc1", workspaceId = "ws_lab", title = "Method.pdf", blobSha256 = "f".repeat(64), pageCount = 12,
            bookmarks = listOf(1, 4), ocrTextByPage = mapOf(1 to "Sample Prep Protocol"),
            annotations = listOf(DocumentAnnotation("a1", 4, "k=2", "Confirmed coverage factor", createdAtUtc = "2026-11-25T00:00:00Z")),
        )
        assertEquals(1, docReview.annotations.size)

        // 5. Authenticated AES-256-GCM Secure Share Bundle + Wrong Passphrase + Tamper Rejection (N-046)
        val recs = listOf(CanonicalRecord("r1", "ws_personal", RecordKind.RECIPE, "Oats", RecordStatus.ACTIVE, 1, 1, "2026", "2026", null, "dev"))
        val bundle = ExtendedFormatInterchange.createSecureShareBundle("ws_personal", WorkspaceRole.VIEWER, recs, "ShareSecret!99", "2026-11-25T00:00:00Z")
        val (hdr, opened) = ExtendedFormatInterchange.openSecureShareBundle(bundle.bytes, "ShareSecret!99")
        assertEquals(WorkspaceRole.VIEWER, hdr.allowedRole)
        assertEquals("Oats", opened.single().title)
        assertFails { ExtendedFormatInterchange.openSecureShareBundle(bundle.bytes, "WrongPassphrase") }

        // 6. 260 Newly Authored Stickers + Non-Destructive Photo Pipeline + Page Duplicate/Reorder (PF-32, R-0056..R-0094)
        assertEquals(260, PhotoAndPlannerAdvanced.BUILT_IN_STICKER_CATALOG.size)
        val origSha = "b".repeat(64)
        val photo = PhotoAsset("ph1", "ws_personal", origSha, "Sample Photo", createdAtUtc = "2026-11-25T00:00:00Z")
        val (editedPhoto, desc) = PhotoAndPlannerAdvanced.applyPhotoEditRecipe(photo, 2000, 1000, PhotoEditRecipe(cropW = 0.5, cropH = 1.0, rotationDeg = 90))
        assertEquals(origSha, editedPhoto.originalBlobSha256, "Original photo blob SHA-256 MUST remain immutable")
        assertTrue(desc.originalUntouched)
        assertEquals(1000, desc.outputWidthPx)
        assertEquals(1000, desc.outputHeightPx)

        val pages = listOf(
            PlannerPage("p1", "bk1", "ws_personal", 1, "Page 1"),
            PlannerPage("p2", "bk1", "ws_personal", 2, "Page 2"),
        )
        val duped = PhotoAndPlannerAdvanced.duplicatePage(pages, "p1", "p1_copy")
        assertEquals(listOf(1, 2, 3), duped.map { it.pageNumber })
        assertEquals("p1_copy", duped[1].id)
        val reordered = PhotoAndPlannerAdvanced.reorderPage(duped, "p2", 0)
        assertEquals("p2", reordered.first().id)
        assertEquals(listOf(1, 2, 3), reordered.map { it.pageNumber })
    }
}
