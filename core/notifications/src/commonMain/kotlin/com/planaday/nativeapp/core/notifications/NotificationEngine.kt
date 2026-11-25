package com.planaday.nativeapp.core.notifications

import com.planaday.nativeapp.core.domain.CalendarDateMath
import com.planaday.nativeapp.core.model.AlarmItem
import kotlinx.serialization.Serializable

@Serializable
data class QuietHoursPolicy(
    val enabled: Boolean = false,
    val startLocal: String = "22:00",
    val endLocal: String = "07:00",
)

@Serializable
data class ScheduledNotificationItem(
    val occurrenceId: String,
    val sourceRecordId: String,
    val workspaceId: String,
    val title: String,
    val body: String,
    val fireDateIso: String,
    val fireTimeLocal: String,
    val timeZone: String,
    val deferredByQuietHours: Boolean = false,
    val missedReason: String? = null,
)

@Serializable
data class NotificationDiagnostics(
    val platform: String,
    val osNotificationPermissionGranted: Boolean,
    val exactAlarmPermissionGranted: Boolean,
    val scheduledCount: Int,
    val missedCount: Int,
    val disclosureNote: String,
)

/**
 * Wall-time alarm & monotonic timer scheduling engine with quiet hours, snooze,
 * and reboot/permission-denial diagnostics (R-0095..R-0108, PF-46, PF-53).
 */
object NotificationScheduleEngine {
    fun isWithinQuietHours(timeLocalHHMM: String, policy: QuietHoursPolicy): Boolean {
        if (!policy.enabled) return false
        val t = CalendarDateMath.timeToMinutes(timeLocalHHMM)
        val start = CalendarDateMath.timeToMinutes(policy.startLocal)
        val end = CalendarDateMath.timeToMinutes(policy.endLocal)
        return if (start <= end) t in start until end else (t >= start || t < end)
    }

    fun computeNextAlarmOccurrence(
        alarm: AlarmItem,
        todayIso: String,
        nowTimeLocalHHMM: String,
        quietHours: QuietHoursPolicy = QuietHoursPolicy(),
    ): ScheduledNotificationItem? {
        if (!alarm.enabled) return null
        val nowMin = CalendarDateMath.timeToMinutes(nowTimeLocalHHMM)
        val alarmMin = CalendarDateMath.timeToMinutes(alarm.timeLocal)

        var targetDate = todayIso
        if (alarm.repeatDays.isEmpty()) {
            if (alarmMin <= nowMin) targetDate = CalendarDateMath.addDays(todayIso, 1)
        } else {
            var found = false
            for (offset in 0..7) {
                val cand = CalendarDateMath.addDays(todayIso, offset)
                val dow = CalendarDateMath.weekdaySun0(cand)
                if (dow in alarm.repeatDays && (offset > 0 || alarmMin > nowMin)) {
                    targetDate = cand
                    found = true
                    break
                }
            }
            if (!found) return null
        }

        val inQuiet = isWithinQuietHours(alarm.timeLocal, quietHours)
        val effectiveTime = if (inQuiet) quietHours.endLocal else alarm.timeLocal
        return ScheduledNotificationItem(
            occurrenceId = "alarm:${alarm.id}:$targetDate:$effectiveTime",
            sourceRecordId = alarm.linkedRecordId ?: alarm.id,
            workspaceId = alarm.workspaceId,
            title = alarm.label.ifBlank { "Planaday Alarm" },
            body = if (inQuiet) "Deferred until end of quiet hours (${quietHours.endLocal})" else "Scheduled alarm",
            fireDateIso = targetDate,
            fireTimeLocal = effectiveTime,
            timeZone = alarm.timeZone,
            deferredByQuietHours = inQuiet,
        )
    }

    fun snoozeOccurrence(item: ScheduledNotificationItem, snoozeMinutes: Int): ScheduledNotificationItem {
        val curMin = CalendarDateMath.timeToMinutes(item.fireTimeLocal)
        val nextTotal = curMin + snoozeMinutes.coerceIn(1, 180)
        val dayRoll = nextTotal / 1440
        val newTime = CalendarDateMath.minutesToTime(nextTotal % 1440)
        val newDate = if (dayRoll > 0) CalendarDateMath.addDays(item.fireDateIso, dayRoll) else item.fireDateIso
        return item.copy(
            occurrenceId = "${item.occurrenceId}:snooze+$snoozeMinutes",
            fireDateIso = newDate,
            fireTimeLocal = newTime,
        )
    }

    /** Monotonic elapsed timer state avoiding wall-clock drift across timezone/DST/NTP adjustments. */
    data class MonotonicTimerState(
        val id: String,
        val label: String,
        val totalDurationMs: Long,
        val accumulatedMs: Long = 0L,
        val runningSinceMonotonicMs: Long? = null,
    ) {
        fun elapsedMs(nowMonotonicMs: Long): Long =
            accumulatedMs + (runningSinceMonotonicMs?.let { (nowMonotonicMs - it).coerceAtLeast(0L) } ?: 0L)

        fun remainingMs(nowMonotonicMs: Long): Long = (totalDurationMs - elapsedMs(nowMonotonicMs)).coerceAtLeast(0L)
        fun isFinished(nowMonotonicMs: Long): Boolean = remainingMs(nowMonotonicMs) == 0L
        fun start(nowMonotonicMs: Long): MonotonicTimerState =
            if (runningSinceMonotonicMs != null) this else copy(runningSinceMonotonicMs = nowMonotonicMs)
        fun pause(nowMonotonicMs: Long): MonotonicTimerState =
            if (runningSinceMonotonicMs == null) this else copy(accumulatedMs = elapsedMs(nowMonotonicMs), runningSinceMonotonicMs = null)
    }
}
