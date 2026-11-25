package com.planaday.nativeapp.core.domain

import com.planaday.nativeapp.core.model.*
import kotlin.test.*

class CoreDomainContractTest {
    @OptIn(kotlin.time.ExperimentalTime::class)
    @Test
    fun recurrenceLeapYearDstAndCompletionDrivenChoreRecalculation() {
        // Leap year Feb 29 policy (R-0023)
        assertEquals("2025-02-28", CalendarDateMath.resolveAnnualDate(2, 29, 2025, Feb29Policy.FEB28))
        assertEquals("2025-03-01", CalendarDateMath.resolveAnnualDate(2, 29, 2025, Feb29Policy.MAR1))
        assertNull(CalendarDateMath.resolveAnnualDate(2, 29, 2025, Feb29Policy.SKIP))
        assertEquals("2028-02-29", CalendarDateMath.resolveAnnualDate(2, 29, 2028, Feb29Policy.SKIP))

        // DST transition check in America/Los_Angeles (R-0019, R-0020)
        val beforeDst = CalendarDateMath.wallClockToUtcInstant("2026-03-08", "01:30", "America/Los_Angeles")
        val afterDst = CalendarDateMath.wallClockToUtcInstant("2026-03-08", "03:30", "America/Los_Angeles")
        // Spring forward skips 02:00..02:59, so elapsed real time between 01:30 and 03:30 is 60 min (3600s), not 120 min!
        assertEquals(3600L, afterDst.epochSeconds - beforeDst.epochSeconds)

        // Recurrence with EXDATE exception (R-0015)
        val dates = RecurrenceEngine.expandDates(
            startDateIso = "2026-11-02",
            rule = RecurrenceRule(
                freq = RecurrenceFreq.DAILY,
                interval = 1,
                count = 4,
                exceptionsIso = listOf("2026-11-04"),
            ),
            rangeStartIso = "2026-11-01",
            rangeEndIso = "2026-11-10",
        )
        assertEquals(listOf("2026-11-02", "2026-11-03", "2026-11-05"), dates)

        // Completion-based chore recalculation: due Mon 2026-11-02, completed Wed 2026-11-04 on 2-day interval -> next due Fri 2026-11-06!
        val chore = ChoreItem(
            id = "ch1", workspaceId = "ws1", title = "Wipe counters", room = "Kitchen",
            mode = ChoreMode.AFTER_COMPLETION, intervalDays = 2, nextDueDateIso = "2026-11-02",
            createdAtUtc = "2026-11-01T00:00:00Z", updatedAtUtc = "2026-11-01T00:00:00Z",
        )
        assertEquals("2026-11-06", ChoreEngine.computeNextDueDate(chore, "2026-11-04"))
        val health = ChoreEngine.computeRoomHealth(listOf(chore), "2026-11-05")
        assertEquals(1, health.size)
        assertEquals(1, health[0].overdueCount)
    }

    @Test
    fun calendarOverlapPoliciesAndIntelligentTaskScheduler() {
        val ev1 = CalendarEvent(
            id = "e1", workspaceId = "ws1", calendarId = "cal1", title = "Lab Calibration",
            startDateIso = "2026-11-25", endDateIso = "2026-11-25", startTimeLocal = "10:00", endTimeLocal = "11:00",
            createdAtUtc = "2026-11-25T00:00:00Z", updatedAtUtc = "2026-11-25T00:00:00Z",
        )
        assertTrue(CalendarOverlapEngine.validateNewOrMovedEvent(630, 690, listOf(ev1), CalendarOverlapPolicy.PREVENT) is OverlapValidationResult.Prevented)
        assertTrue(CalendarOverlapEngine.validateNewOrMovedEvent(630, 690, listOf(ev1), CalendarOverlapPolicy.WARN) is OverlapValidationResult.Warning)
        assertTrue(CalendarOverlapEngine.validateNewOrMovedEvent(630, 690, listOf(ev1), CalendarOverlapPolicy.SIDE_BY_SIDE) is OverlapValidationResult.Allowed)

        // Carryover preserves original createdAt & increments reschedule count (R-0038, R-0039)
        val oldTask = TaskItem(
            id = "t1", workspaceId = "ws1", title = "Analyze Moisture Samples",
            priority = Priority.HIGH, doDateIso = "2026-11-22", dueDateIso = "2026-11-23",
            taskType = "nir_run", createdAtUtc = "2026-11-20T08:00:00Z", updatedAtUtc = "2026-11-22T08:00:00Z",
        )
        val carried = TaskSchedulerEngine.carryOverIncompleteTasks(listOf(oldTask), "2026-11-25", "2026-11-25T08:00:00Z").single()
        assertEquals("2026-11-25", carried.doDateIso)
        assertEquals("2026-11-20T08:00:00Z", carried.createdAtUtc)
        assertEquals(1, carried.rescheduleCount)
        val age = TaskSchedulerEngine.computeTaskAge(carried, "2026-11-25")
        assertEquals(5, age.ageDays)
        assertEquals(2, age.overdueDays)

        // Dynamic placement avoids calendar event [10:00..11:00] without deleting it
        val plan = TaskSchedulerEngine.buildDaySchedule(
            dateIso = "2026-11-25",
            nowMin = 9 * 60 + 45,
            preferredEndTimeLocal = "17:00",
            tasks = listOf(carried.copy(estimateMin = 30)),
            dayEvents = listOf(ev1),
        )
        assertEquals(1, plan.slots.size)
        assertEquals(11 * 60, plan.slots.first().startMin) // Placed right after 11:00 event!
    }

    @Test
    fun deepHierarchyCycleRejectionExactDecimalAndAclBoundaries() {
        // R-0233: 70-level deep hierarchy without stack overflow + cycle rejection
        val parents = LinkedHashMap<String, String?>()
        parents["n0"] = null
        for (i in 1..70) parents["n$i"] = "n${i - 1}"
        assertEquals(71, HierarchyGraphEngine.breadcrumbPath("n70", parents).size)
        assertTrue(HierarchyGraphEngine.wouldCreateCycle("n10", "n65", parents))
        assertFalse(HierarchyGraphEngine.wouldCreateCycle("n65", "n10", parents))

        // Exact decimal money math (zero binary float drift: 0.10 + 0.20 == 0.30 exactly!)
        val sum = ExactDecimal.add(DecimalAmount("0.10", "USD"), DecimalAmount("0.20", "USD"))
        assertEquals("0.30", sum.value)

        // Deny-by-default Workspace ACL
        val viewer = WorkspaceMembership("ws_lab", "u1", "Analyst", WorkspaceRole.VIEWER, "2026-11-25T00:00:00Z")
        assertTrue(WorkspaceAclEvaluator.canRead(viewer, "ws_lab"))
        assertFalse(WorkspaceAclEvaluator.canWrite(viewer, "ws_lab"))
        assertFalse(WorkspaceAclEvaluator.canRead(viewer, "ws_personal"))
    }
}
