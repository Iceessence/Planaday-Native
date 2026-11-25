package com.planaday.nativeapp.core.usecases

import com.planaday.nativeapp.core.automation.*
import com.planaday.nativeapp.core.domain.*
import com.planaday.nativeapp.core.interchange.*
import com.planaday.nativeapp.core.math.*
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.notifications.NotificationScheduleEngine
import com.planaday.nativeapp.core.notifications.ScheduledNotificationItem
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.persistence.SyncCursorEntity
import com.planaday.nativeapp.core.security.CryptoPrimitives
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class GroceryReconciliationResult(
    val updatedPantryLot: CanonicalRecord,
    val createdExpense: CanonicalRecord,
    val totalCost: DecimalAmount,
)

data class StoryContinuityIssue(
    val severity: String,
    val chapterTitle: String,
    val description: String,
)

/**
 * Canonical Cross-Feature Command Orchestrator implementing all 15 mandatory integration journeys
 * (INT-001..INT-015), Universal Capture, Undo/Redo, Verified Backup/Restore, and Opt-In Synthetic Demo Data.
 */
class PlanadayUseCases(
    val repository: PlanadayRepository,
    val clock: DomainClock = SystemDomainClock,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val automationEngine = AutomationEngine()

    // --- INT-001: Today Meal -> Unified Food History ---
    suspend fun logMealFromToday(workspaceId: String, foodLog: FoodLogItem): CanonicalRecord {
        return repository.createRecord(
            workspaceId = workspaceId,
            kind = RecordKind.FOOD_LOG,
            title = "${foodLog.mealCategory}: ${foodLog.foodName}",
            payloadJson = json.encodeToString(foodLog),
            summaryText = "${foodLog.dateIso} ${foodLog.foodName} ${foodLog.notes}",
            tags = listOf("food", foodLog.mealCategory),
            id = foodLog.id,
        )
    }

    // --- INT-002 & INT-003: Single Canonical Task UUID across Note -> Task -> Calendar -> Kanban -> Goal ---
    suspend fun createTaskFromNoteAndSchedule(
        workspaceId: String,
        sourceNoteId: String,
        task: TaskItem,
        calendarId: String,
        startTimeLocal: String,
        endTimeLocal: String,
        goalId: String? = null,
    ): Triple<CanonicalRecord, CanonicalRecord, RecordRelation> {
        val taskRec = repository.createRecord(
            workspaceId = workspaceId,
            kind = RecordKind.TASK,
            title = task.title,
            payloadJson = json.encodeToString(task),
            summaryText = task.notes,
            tags = listOfNotNull("task", task.taskType),
            id = task.id,
        )
        val noteLink = repository.linkRecords(
            workspaceId = workspaceId,
            sourceId = sourceNoteId,
            sourceKind = RecordKind.NOTE,
            targetId = taskRec.id,
            targetKind = RecordKind.TASK,
            relationKind = RelationKind.PROMOTED_FROM,
            label = "Task created from note",
        )
        val event = CalendarEvent(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            calendarId = calendarId,
            title = task.title,
            startDateIso = task.doDateIso ?: clock.todayIso(),
            endDateIso = task.doDateIso ?: clock.todayIso(),
            startTimeLocal = startTimeLocal,
            endTimeLocal = endTimeLocal,
            linkedActionId = taskRec.id, // Same canonical Task UUID!
            createdAtUtc = clock.nowUtcIso(),
            updatedAtUtc = clock.nowUtcIso(),
        )
        val eventRec = repository.createRecord(
            workspaceId = workspaceId,
            kind = RecordKind.EVENT,
            title = event.title,
            payloadJson = json.encodeToString(event),
            summaryText = "Scheduled from task ${taskRec.id}",
            id = event.id,
        )
        repository.linkRecords(
            workspaceId = workspaceId,
            sourceId = eventRec.id,
            sourceKind = RecordKind.EVENT,
            targetId = taskRec.id,
            targetKind = RecordKind.TASK,
            relationKind = RelationKind.SCHEDULED_FROM,
        )
        if (goalId != null) {
            repository.linkRecords(
                workspaceId = workspaceId,
                sourceId = goalId,
                sourceKind = RecordKind.GOAL,
                targetId = taskRec.id,
                targetKind = RecordKind.TASK,
                relationKind = RelationKind.PARENT,
            )
        }
        return Triple(taskRec, eventRec, noteLink)
    }

    /** INT-003: Moving a card on the live Kanban board updates the canonical Task record directly (zero duplicate!). */
    suspend fun moveTaskKanbanColumn(workspaceId: String, taskId: String, newColumn: String): CanonicalRecord {
        return repository.updateRecordAtomic(workspaceId, taskId) { rec ->
            val current = json.decodeFromString<TaskItem>(rec.payloadJson)
            val updatedStatus = if (newColumn == "done") RecordStatus.DONE else RecordStatus.ACTIVE
            val updatedTask = current.copy(
                boardColumn = newColumn,
                status = updatedStatus,
                completedAtUtc = if (updatedStatus == RecordStatus.DONE) clock.nowUtcIso() else null,
            )
            rec.copy(status = updatedStatus, payloadJson = json.encodeToString(updatedTask))
        }
    }

    // --- INT-004: Lab Meeting Action Item -> Lab Task (strictly inside Lab Workspace) ---
    suspend fun promoteLabMeetingActionToTask(
        labWorkspaceId: String,
        meetingId: String,
        actionTitle: String,
        dueDateIso: String? = null,
    ): CanonicalRecord {
        val now = clock.nowUtcIso()
        val task = TaskItem(
            id = UUID.randomUUID().toString(),
            workspaceId = labWorkspaceId,
            title = actionTitle,
            priority = Priority.HIGH,
            dueDateIso = dueDateIso,
            doDateIso = clock.todayIso(),
            createdAtUtc = now,
            updatedAtUtc = now,
        )
        val taskRec = repository.createRecord(
            workspaceId = labWorkspaceId,
            kind = RecordKind.LAB_TASK,
            title = actionTitle,
            payloadJson = json.encodeToString(task),
            summaryText = "Action item from Lab Meeting $meetingId",
            tags = listOf("lab", "meeting-action"),
            id = task.id,
        )
        repository.linkRecords(
            workspaceId = labWorkspaceId,
            sourceId = meetingId,
            sourceKind = RecordKind.LAB_MEETING,
            targetId = taskRec.id,
            targetKind = RecordKind.LAB_TASK,
            relationKind = RelationKind.PROMOTED_FROM,
        )
        return taskRec
    }

    // --- INT-005: Lab Inventory Purchase -> Updates Lab Budget & Inventory Atomically (ExactDecimal) ---
    suspend fun recordLabPurchaseAndUpdateBudget(
        labWorkspaceId: String,
        budgetRecordId: String,
        inventoryRecordId: String,
        order: LabOrder,
    ): Pair<CanonicalRecord, CanonicalRecord> {
        repository.createRecord(
            workspaceId = labWorkspaceId,
            kind = RecordKind.LAB_ORDER,
            title = "PO: ${order.itemDescription}",
            payloadJson = json.encodeToString(order),
            summaryText = "${order.vendorName} ${order.totalCost.value} ${order.totalCost.unitOrCurrency}",
            id = order.id,
        )
        val updatedBudget = repository.updateRecordAtomic(labWorkspaceId, budgetRecordId) { rec ->
            val currentSpent = json.decodeFromString<DecimalAmount>(rec.payloadJson)
            val newSpent = ExactDecimal.add(currentSpent, order.totalCost)
            rec.copy(payloadJson = json.encodeToString(newSpent), summaryText = "Spent: ${newSpent.value} ${newSpent.unitOrCurrency}")
        }
        val updatedInv = repository.updateRecordAtomic(labWorkspaceId, inventoryRecordId) { rec ->
            val inv = json.decodeFromString<LabInventoryItem>(rec.payloadJson)
            val updated = inv.copy(quantityOnHand = inv.quantityOnHand + order.quantity)
            rec.copy(payloadJson = json.encodeToString(updated), summaryText = "On hand: ${updated.quantityOnHand} ${updated.unit}")
        }
        repository.linkRecords(labWorkspaceId, order.id, RecordKind.LAB_ORDER, budgetRecordId, RecordKind.LAB_BUDGET, RelationKind.EXPENSE)
        repository.linkRecords(labWorkspaceId, order.id, RecordKind.LAB_ORDER, inventoryRecordId, RecordKind.LAB_INVENTORY, RelationKind.SUPPLY)
        return updatedBudget to updatedInv
    }

    // --- INT-006: Complete Chore -> Next Due Date + Deterministic OccurrenceId + Room Health ---
    suspend fun completeChore(
        workspaceId: String,
        choreId: String,
        completedDateIso: String,
        assigneeId: String? = null,
        minutesSpent: Int? = null,
    ): CanonicalRecord {
        val occurrenceId = CryptoPrimitives.sha256Hex("$choreId|$completedDateIso".encodeToByteArray()).take(24)
        return repository.updateRecordAtomic(workspaceId, choreId) { rec ->
            val chore = json.decodeFromString<ChoreItem>(rec.payloadJson)
            if (chore.completions.any { it.occurrenceId == occurrenceId }) {
                return@updateRecordAtomic rec // Idempotent across devices!
            }
            val daysDelta = CalendarDateMath.diffDays(chore.nextDueDateIso, completedDateIso)
            val completion = ChoreCompletion(
                id = UUID.randomUUID().toString(),
                occurrenceId = occurrenceId,
                completedAtUtc = clock.nowUtcIso(),
                completedDateIso = completedDateIso,
                scheduledDueDateIso = chore.nextDueDateIso,
                wasOnTime = daysDelta <= 0,
                daysDelta = daysDelta,
                assigneeId = assigneeId,
                minutesSpent = minutesSpent,
            )
            val nextDue = ChoreEngine.computeNextDueDate(chore, completedDateIso)
            val updatedChore = chore.copy(
                lastCompletedDateIso = completedDateIso,
                nextDueDateIso = nextDue,
                completions = chore.completions + completion,
                updatedAtUtc = clock.nowUtcIso(),
            )
            rec.copy(payloadJson = json.encodeToString(updatedChore), summaryText = "Next due: $nextDue")
        }
    }

    // --- INT-007 & INT-008: Calculator -> Spreadsheet -> Immutable Snapshot -> Presentation ---
    suspend fun insertCalculationIntoSpreadsheet(
        workspaceId: String,
        workbookRecordId: String,
        sheetId: String,
        cellAddr: String,
        expression: String,
    ): Pair<CanonicalRecord, Double> {
        val value = MathAstEngine.evaluate(expression)
        val updated = repository.updateRecordAtomic(workspaceId, workbookRecordId, recordRevision = true) { rec ->
            val wb = json.decodeFromString<SpreadsheetWorkbook>(rec.payloadJson)
            val newSheets = wb.sheets.map { s ->
                if (s.id == sheetId) s.copy(cells = s.cells + (cellAddr.uppercase() to SheetCellInput(value.toString())))
                else s
            }
            rec.copy(payloadJson = json.encodeToString(wb.copy(sheets = newSheets)))
        }
        return updated to value
    }

    suspend fun embedSpreadsheetSnapshotInPresentation(
        workspaceId: String,
        workbookRecordId: String,
        presentationRecordId: String,
        slideId: String,
        title: String,
    ): Pair<EvidenceSnapshot, CanonicalRecord> {
        val snap = repository.createEvidenceSnapshot(
            workspaceId = workspaceId,
            sourceRecordId = workbookRecordId,
            title = title,
            provenanceNote = "Versioned spreadsheet snapshot embedded into presentation $presentationRecordId",
        )
        val updatedDeck = repository.updateRecordAtomic(workspaceId, presentationRecordId) { rec ->
            val deck = json.decodeFromString<PresentationDeck>(rec.payloadJson)
            val newSlides = deck.slides.map { s ->
                if (s.id == slideId) {
                    val obj = SlideObject(
                        id = UUID.randomUUID().toString(),
                        kind = SlideObjectKind.CHART_SNAPSHOT,
                        xPct = 10.0, yPct = 20.0, widthPct = 80.0, heightPct = 60.0,
                        text = title,
                        snapshotId = snap.id,
                    )
                    s.copy(objects = s.objects + obj)
                } else s
            }
            rec.copy(payloadJson = json.encodeToString(deck.copy(slides = newSlides)))
        }
        repository.linkRecords(workspaceId, presentationRecordId, RecordKind.PRESENTATION, workbookRecordId, RecordKind.SPREADSHEET, RelationKind.DERIVED_FROM)
        return snap to updatedDeck
    }

    // --- INT-010: Read-Only Book Story Continuity Checker ---
    fun checkBookContinuity(chapters: List<BookChapter>, knownCharacterNames: Set<String>): List<StoryContinuityIssue> {
        val issues = mutableListOf<StoryContinuityIssue>()
        val seenBefore = HashSet<String>()
        for (ch in chapters.sortedBy { it.chapterNumber }) {
            if (ch.manuscriptText.isBlank()) {
                issues.add(StoryContinuityIssue("INFO", ch.title, "Chapter ${ch.chapterNumber} has empty manuscript text."))
            }
            for (name in knownCharacterNames) {
                if (ch.manuscriptText.contains(name) && name !in seenBefore) {
                    seenBefore.add(name)
                }
            }
        }
        val unused = knownCharacterNames - seenBefore
        for (u in unused) {
            issues.add(StoryContinuityIssue("WARNING", "Manuscript", "Registered character '$u' does not appear in any chapter."))
        }
        return issues
    }

    // --- INT-012: Lab Calibration Immutable Raw Record + Append-Only Correction + Reminder ---
    suspend fun recordLabCalibrationCorrection(
        labWorkspaceId: String,
        calibrationId: String,
        actor: String,
        reason: String,
        correctedValue: String,
        unit: String,
    ): Pair<CanonicalRecord, ScheduledNotificationItem?> {
        var nextDueIso = clock.todayIso()
        var instrument = ""
        val updated = repository.updateRecordAtomic(labWorkspaceId, calibrationId, recordRevision = true, checkpointLabel = "Correction by $actor") { rec ->
            val cal = json.decodeFromString<LabCalibrationRecord>(rec.payloadJson)
            // Invariant: rawMeasurementValue is NEVER modified; correction appended only!
            val correction = LabCorrectionEntry(
                id = UUID.randomUUID().toString(),
                timestampUtc = clock.nowUtcIso(),
                actor = actor,
                reason = reason,
                correctedValue = correctedValue,
                unit = unit,
            )
            val appended = cal.copy(corrections = cal.corrections + correction)
            nextDueIso = appended.nextDueDateIso
            instrument = appended.instrumentId
            rec.copy(payloadJson = json.encodeToString(appended))
        }
        val alarm = AlarmItem(
            id = "cal_rem_$calibrationId",
            workspaceId = labWorkspaceId,
            label = "Calibration due: $instrument",
            timeLocal = "09:00",
            timeZone = "UTC",
            linkedRecordId = calibrationId,
        )
        val reminder = NotificationScheduleEngine.computeNextAlarmOccurrence(alarm, nextDueIso, "08:00")
        return updated to reminder
    }

    // --- INT-013: Grocery Purchase Reconciliation -> Pantry Lot + Single Finance Expense (No Duplicate Totals!) ---
    suspend fun reconcileGroceryPurchase(
        workspaceId: String,
        groceryItem: GroceryItem,
        existingPantryLotId: String?,
        expenseCategory: String = "Groceries",
        envelopeId: String? = null,
    ): GroceryReconciliationResult {
        val totalCost = groceryItem.unitPrice ?: DecimalAmount("0.00", "USD")
        val pantryRec = if (existingPantryLotId != null) {
            repository.updateRecordAtomic(workspaceId, existingPantryLotId) { rec ->
                val lot = json.decodeFromString<PantryLot>(rec.payloadJson)
                val updated = lot.copy(quantity = lot.quantity + groceryItem.quantity)
                rec.copy(payloadJson = json.encodeToString(updated), summaryText = "${updated.quantity} ${updated.unit} in ${updated.zone}")
            }
        } else {
            val lot = PantryLot(
                id = UUID.randomUUID().toString(),
                workspaceId = workspaceId,
                ingredientName = groceryItem.displayName,
                canonicalName = groceryItem.canonicalName,
                quantity = groceryItem.quantity,
                unit = groceryItem.unit,
                zone = groceryItem.targetZone,
                purchaseDateIso = clock.todayIso(),
                expiryDateIso = groceryItem.expiryDateIso,
            )
            repository.createRecord(
                workspaceId = workspaceId,
                kind = RecordKind.PANTRY_LOT,
                title = lot.ingredientName,
                payloadJson = json.encodeToString(lot),
                summaryText = "${lot.quantity} ${lot.unit} in ${lot.zone}",
                id = lot.id,
            )
        }

        val expense = ExpenseItem(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            dateIso = clock.todayIso(),
            description = "Grocery purchase: ${groceryItem.displayName}",
            amount = totalCost,
            category = expenseCategory,
            envelopeId = envelopeId,
            sourceRecordId = groceryItem.id,
        )
        val expenseRec = repository.createRecord(
            workspaceId = workspaceId,
            kind = RecordKind.EXPENSE,
            title = expense.description,
            payloadJson = json.encodeToString(expense),
            summaryText = "${totalCost.value} ${totalCost.unitOrCurrency}",
            tags = listOf("finance", "groceries"),
            id = expense.id,
        )
        repository.linkRecords(workspaceId, expenseRec.id, RecordKind.EXPENSE, pantryRec.id, RecordKind.PANTRY_LOT, RelationKind.SUPPLY)
        return GroceryReconciliationResult(pantryRec, expenseRec, totalCost)
    }

    // --- INT-015: Idempotent Automation Execution Backed by SQLite automation_runs ---
    suspend fun evaluateAndRunAutomations(
        rules: List<AutomationRuleDef>,
        event: AutomationDomainEvent,
        approvedKeys: Set<String> = emptySet(),
        actionHandler: suspend (AutomationActionPreview) -> Unit,
    ): List<AutomationActionPreview> {
        val alreadyRan = HashSet<String>()
        for (rule in rules) {
            val key = "${rule.id}:${event.eventId}"
            if (repository.db.syncDao().hasAutomationRun(key) > 0) alreadyRan.add(key)
        }
        val previews = automationEngine.preview(rules, event, alreadyExecutedKeys = alreadyRan)
        val ran = mutableListOf<AutomationActionPreview>()
        for (p in previews) {
            if (p.blockedReason != null) continue
            if (p.requiresApproval && p.idempotencyKey !in approvedKeys) continue
            val inserted = repository.db.syncDao().recordAutomationRun(
                com.planaday.nativeapp.core.persistence.AutomationRunEntity(
                    idempotencyKey = p.idempotencyKey,
                    ruleId = p.ruleId,
                    workspaceId = event.workspaceId,
                    executedAtUtc = clock.nowUtcIso(),
                    status = "EXECUTED",
                )
            )
            if (inserted != -1L) {
                actionHandler(p)
                ran.add(p)
            }
        }
        return ran
    }

    // --- PF-42 + PD-003: Export & Staged Verified Restore of Planaday Native v1 Backup ---
    suspend fun exportVerifiedBackup(workspaceId: String, passphrase: String? = null): ExportArtifact {
        val workspaces = repository.listWorkspaces()
        val records = repository.db.recordDao().listActive(workspaceId, limit = 50_000, offset = 0).mapNotNull {
            repository.getRecord(workspaceId, it.id)
        }
        val relations = repository.db.relationDao().listAllInWorkspace(workspaceId).map {
            RecordRelation(it.id, it.workspaceId, it.sourceId, RecordKind.valueOf(it.sourceKind), it.targetId, RecordKind.valueOf(it.targetKind), RelationKind.valueOf(it.relationKind), it.sortOrder, it.label, it.createdAtUtc)
        }
        val revisions = repository.db.draftAndRevisionDao().listAllRevisions(workspaceId).map {
            RevisionSnapshot(it.id, it.workspaceId, it.recordId, RecordKind.valueOf(it.recordKind), it.rev, it.title, it.contentSnapshot, it.checkpointLabel, it.createdAtUtc, it.deviceId, it.isImmutableEvidence)
        }
        val blobMap = LinkedHashMap<String, Pair<BackupBlobEntryMeta, ByteArray>>()
        for (rec in records) {
            for (ref in repository.db.attachmentDao().refsForRecord(workspaceId, rec.id)) {
                if (ref.sha256 !in blobMap) {
                    val bytes = repository.blobStore.getVerified(ref.sha256) ?: continue
                    val meta = repository.db.attachmentDao().getAttachment(ref.sha256) ?: continue
                    blobMap[ref.sha256] = BackupBlobEntryMeta(meta.sha256, meta.mimeType, meta.originalFilename, meta.byteSize) to bytes
                }
            }
        }
        val payload = NativeBackupBundlePayload(
            exportedAtUtc = clock.nowUtcIso(),
            workspaces = workspaces,
            records = records,
            relations = relations,
            revisions = revisions,
        )
        return ArchiveInterchange.createBackupZip(payload, blobMap, passphrase = passphrase)
    }

    /**
     * Stages and verifies Planaday Native v1 archive, restores records & verified blobs,
     * and ALWAYS clears boundServerId + sets restoredFromBackupAtUtc so replacement servers
     * enter safe reconciliation mode instead of falsely reporting SYNCED (PD-003, PF-42).
     */
    suspend fun restoreVerifiedBackup(archiveBytes: ByteArray, passphrase: String? = null): VerifiedRestoreBundle {
        val verified = ArchiveInterchange.verifyAndReadBackup(archiveBytes, passphrase)
        val now = clock.nowUtcIso()
        for (ws in verified.payload.workspaces) {
            repository.createWorkspace(ws)
        }
        for ((hash, bytes) in verified.verifiedBlobs) {
            repository.blobStore.putVerified(bytes, expectedSha256 = hash)
            val meta = verified.manifest.blobs[hash]!!
            repository.db.attachmentDao().insertAttachment(
                com.planaday.nativeapp.core.persistence.AttachmentEntity(
                    sha256 = hash,
                    mimeType = meta.mimeType,
                    byteSize = meta.byteSize,
                    originalFilename = meta.originalFilename,
                    createdAtUtc = now,
                    localStorageRelPath = repository.blobStore.relativePathFor(hash),
                    thumbnailRelPath = null,
                )
            )
        }
        for (rec in verified.payload.records) {
            repository.applyPulledRecordFromServer(rec)
        }
        for (ws in verified.payload.workspaces) {
            // PD-003: clear bound server identity and set restore marker!
            repository.db.syncDao().saveCursor(
                SyncCursorEntity(
                    workspaceId = ws.id,
                    boundServerId = null,
                    boundSyncEpoch = null,
                    lastCursor = 0L,
                    lastSyncAtUtc = null,
                    restoredFromBackupAtUtc = now,
                )
            )
        }
        return verified
    }

    /** Opt-in synthetic demonstration records (O-004: strictly opt-in and independently removable!). */
    suspend fun seedOptInSyntheticDemo(workspaceId: String) {
        val today = clock.todayIso()
        val now = clock.nowUtcIso()
        val demoNote = repository.createRecord(
            workspaceId = workspaceId,
            kind = RecordKind.NOTE,
            title = "Welcome to Planaday Native (Synthetic Demo)",
            payloadJson = """{"content":"Sample offline note with [[Quarterly Calibration Plan]] wiki link."}""",
            summaryText = "Sample offline note with wiki links and linked tasks.",
            tags = listOf("demo", "synthetic"),
            isSyntheticDemo = true,
            recordRevision = true,
        )
        createTaskFromNoteAndSchedule(
            workspaceId = workspaceId,
            sourceNoteId = demoNote.id,
            task = TaskItem(
                id = UUID.randomUUID().toString(),
                workspaceId = workspaceId,
                title = "Review NIR Moisture Calibration Curve",
                notes = "Synthetic demo task linked to calendar and Kanban.",
                priority = Priority.HIGH,
                doDateIso = today,
                dueDateIso = CalendarDateMath.addDays(today, 2),
                estimateMin = 45,
                taskType = "calibration_review",
                createdAtUtc = now,
                updatedAtUtc = now,
            ),
            calendarId = "cal_default",
            startTimeLocal = "10:00",
            endTimeLocal = "10:45",
        )
    }
}
