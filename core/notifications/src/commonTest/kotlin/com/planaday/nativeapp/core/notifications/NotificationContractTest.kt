package com.planaday.nativeapp.core.notifications

import com.planaday.nativeapp.core.model.AlarmItem
import kotlin.test.*

class NotificationContractTest {
    @Test
    fun quietHoursDeferralSnoozeAndMonotonicTimer() {
        val alarm = AlarmItem("a1", "ws1", "Night Check", timeLocal = "23:30", timeZone = "UTC", repeatDays = emptyList())
        val quiet = QuietHoursPolicy(enabled = true, startLocal = "22:00", endLocal = "07:00")
        val occ = NotificationScheduleEngine.computeNextAlarmOccurrence(alarm, "2026-11-25", "20:00", quiet)
        assertNotNull(occ)
        assertTrue(occ.deferredByQuietHours)
        assertEquals("07:00", occ.fireTimeLocal)

        val snoozed = NotificationScheduleEngine.snoozeOccurrence(occ, 15)
        assertEquals("07:15", snoozed.fireTimeLocal)

        // Monotonic timer unaffected by wall clock changes
        val t0 = NotificationScheduleEngine.MonotonicTimerState("tm1", "Focus", totalDurationMs = 60_000L).start(100_000L)
        val paused = t0.pause(125_000L)
        assertEquals(35_000L, paused.remainingMs(999_999L))
        val resumed = paused.start(200_000L)
        assertTrue(resumed.isFinished(235_000L))
    }
}
