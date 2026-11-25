package com.planaday.nativeapp.core.domain

import com.planaday.nativeapp.core.model.*
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalTime::class)
interface DomainClock {
    fun nowInstant(): Instant
    fun nowUtcIso(): String = nowInstant().toString()
    fun todayIso(timeZone: TimeZone = TimeZone.UTC): String =
        nowInstant().toLocalDateTime(timeZone).date.toString()
}

@OptIn(ExperimentalTime::class)
object SystemDomainClock : DomainClock {
    override fun nowInstant(): Instant = Clock.System.now()
}

@OptIn(ExperimentalTime::class)
class FixedDomainClock(private var current: Instant) : DomainClock {
    override fun nowInstant(): Instant = current
    fun advanceSeconds(seconds: Long) {
        current = current + seconds.seconds
    }
}

/**
 * DST-aware and leap-year-safe date/time utilities (R-0019..R-0023).
 */
object CalendarDateMath {
    fun parseDate(iso: String): LocalDate = LocalDate.parse(iso)

    fun addDays(dateIso: String, days: Int): String =
        LocalDate.parse(dateIso).plus(days, DateTimeUnit.DAY).toString()

    fun diffDays(fromIso: String, toIso: String): Int =
        LocalDate.parse(fromIso).daysUntil(LocalDate.parse(toIso))

    /** 0 = Sunday .. 6 = Saturday */
    fun weekdaySun0(dateIso: String): Int {
        val isoDow = LocalDate.parse(dateIso).dayOfWeek.isoDayNumber // 1=Mon..7=Sun
        return if (isoDow == 7) 0 else isoDow
    }

    fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> throw IllegalArgumentException("Invalid month $month")
    }

    fun timeToMinutes(hhmm: String): Int {
        val parts = hhmm.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 24) ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return (h * 60 + m).coerceIn(0, 1440)
    }

    fun minutesToTime(totalMin: Int): String {
        val clamped = totalMin.coerceIn(0, 1440)
        val h = clamped / 60
        val m = clamped % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    /**
     * Resolves an annual date in [year], applying explicit Feb 29 policy for non-leap years (R-0023).
     */
    fun resolveAnnualDate(month: Int, day: Int, year: Int, feb29Policy: Feb29Policy): String? {
        if (month == 2 && day == 29 && !isLeapYear(year)) {
            return when (feb29Policy) {
                Feb29Policy.FEB28 -> LocalDate(year, 2, 28).toString()
                Feb29Policy.MAR1 -> LocalDate(year, 3, 1).toString()
                Feb29Policy.SKIP -> null
            }
        }
        val clampedDay = min(day, daysInMonth(year, month))
        return LocalDate(year, month, clampedDay).toString()
    }

    /**
     * Converts wall-clock date/time in an IANA timezone to UTC Instant, accurately handling DST spring-forward gaps
     * and fall-back folds (R-0019, R-0020).
     */
    @OptIn(ExperimentalTime::class)
    fun wallClockToUtcInstant(dateIso: String, timeHHMM: String, ianaZone: String): Instant {
        val date = LocalDate.parse(dateIso)
        val parts = timeHHMM.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        val ldt = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute)
        val tz = TimeZone.of(ianaZone)
        return ldt.toInstant(tz)
    }
}

/**
 * RFC 5545 recurrence expansion engine supporting daily, weekly, monthly (nth weekday / first business day),
 * yearly (with Feb 29 policies), intervals, until, count, skipWeekends, and EXDATE exceptions (R-0015, R-0035).
 */
object RecurrenceEngine {
    fun expandDates(
        startDateIso: String,
        rule: RecurrenceRule?,
        rangeStartIso: String = startDateIso,
        rangeEndIso: String = CalendarDateMath.addDays(startDateIso, 366),
        maxCount: Int = 250,
    ): List<String> {
        if (rule == null) {
            return if (startDateIso in rangeStartIso..rangeEndIso) listOf(startDateIso) else emptyList()
        }
        val interval = max(1, rule.interval)
        val exceptions = rule.exceptionsIso.toSet()
        val out = mutableListOf<String>()
        var generated = 0

        fun accept(candidate: String): Boolean {
            if (candidate < startDateIso) return true
            val untilIso = rule.untilDateIso
            if (untilIso != null && candidate > untilIso) return false
            val maxOccurrences = rule.count
            if (maxOccurrences != null && generated >= maxOccurrences) return false
            if (rule.skipWeekends) {
                val dow = CalendarDateMath.weekdaySun0(candidate)
                if (dow == 0 || dow == 6) return true
            }
            generated++
            if (candidate !in exceptions && candidate in rangeStartIso..rangeEndIso) {
                out.add(candidate)
            }
            if (out.size >= maxCount) return false
            if (candidate > rangeEndIso && maxOccurrences == null) return false
            return true
        }

        when (rule.freq) {
            RecurrenceFreq.DAILY -> {
                var cur = startDateIso
                for (step in 0 until 3000) {
                    if (!accept(cur) || cur > rangeEndIso) break
                    cur = CalendarDateMath.addDays(cur, interval)
                }
            }
            RecurrenceFreq.WEEKLY -> {
                val targetDays = if (rule.byWeekday.isNotEmpty()) rule.byWeekday.sorted()
                else listOf(CalendarDateMath.weekdaySun0(startDateIso))
                var weekStart = CalendarDateMath.addDays(startDateIso, -CalendarDateMath.weekdaySun0(startDateIso))
                for (w in 0 until 600) {
                    var stop = false
                    for (d in targetDays) {
                        val cand = CalendarDateMath.addDays(weekStart, d)
                        if (cand < startDateIso) continue
                        if (!accept(cand)) {
                            stop = true
                            break
                        }
                    }
                    if (stop || weekStart > rangeEndIso) break
                    weekStart = CalendarDateMath.addDays(weekStart, 7 * interval)
                }
            }
            RecurrenceFreq.MONTHLY -> {
                val start = LocalDate.parse(startDateIso)
                var totalMonths = start.year * 12 + (start.monthNumber - 1)
                for (i in 0 until 360) {
                    val y = totalMonths / 12
                    val m = (totalMonths % 12) + 1
                    val setPos = rule.bySetPos
                    val cand = when {
                        rule.firstBusinessDay -> firstBusinessDayOfMonth(y, m)
                        setPos != null && rule.byWeekday.isNotEmpty() ->
                            nthWeekdayOfMonth(y, m, rule.byWeekday.first(), setPos)
                        else -> LocalDate(y, m, min(start.dayOfMonth, CalendarDateMath.daysInMonth(y, m))).toString()
                    }
                    if (cand != null) {
                        if (!accept(cand) || cand > rangeEndIso) break
                    }
                    totalMonths += interval
                }
            }
            RecurrenceFreq.YEARLY -> {
                val start = LocalDate.parse(startDateIso)
                var y = start.year
                repeat(80) {
                    val cand = CalendarDateMath.resolveAnnualDate(start.monthNumber, start.dayOfMonth, y, rule.feb29Policy)
                    if (cand != null) {
                        if (!accept(cand) || cand > rangeEndIso) return out
                    }
                    y += interval
                }
            }
        }
        return out
    }

    fun nthWeekdayOfMonth(year: Int, month: Int, weekdaySun0: Int, nth: Int): String? {
        val total = CalendarDateMath.daysInMonth(year, month)
        val matches = mutableListOf<Int>()
        for (d in 1..total) {
            val iso = LocalDate(year, month, d).toString()
            if (CalendarDateMath.weekdaySun0(iso) == weekdaySun0) matches.add(d)
        }
        if (matches.isEmpty()) return null
        val chosen = if (nth < 0) matches.getOrNull(matches.size + nth) else matches.getOrNull(nth - 1)
        return chosen?.let { LocalDate(year, month, it).toString() }
    }

    fun firstBusinessDayOfMonth(year: Int, month: Int): String {
        for (d in 1..7) {
            val iso = LocalDate(year, month, d).toString()
            val w = CalendarDateMath.weekdaySun0(iso)
            if (w != 0 && w != 6) return iso
        }
        return LocalDate(year, month, 1).toString()
    }
}

data class PositionedCalendarEvent(
    val event: CalendarEvent,
    val startMin: Int,
    val endMin: Int,
    val laneIndex: Int,
    val laneCount: Int,
    val hasOverlapWarning: Boolean,
)

sealed interface OverlapValidationResult {
    data object Allowed : OverlapValidationResult
    data class Warning(val conflictingIds: List<String>) : OverlapValidationResult
    data class Prevented(val conflictingIds: List<String>) : OverlapValidationResult
}

/**
 * Flow Calendar overlap layout & policy engine (R-0001..R-0010):
 * supports all 7 overlap policies and minute-level (1m..4h) geometry calculation.
 */
object CalendarOverlapEngine {
    val STANDARD_ZOOM_INTERVALS_MIN: List<Int> = listOf(1, 2, 5, 10, 15, 20, 30, 45, 60, 120, 180, 240)

    fun validateNewOrMovedEvent(
        candidateStartMin: Int,
        candidateEndMin: Int,
        existingEvents: List<CalendarEvent>,
        policy: CalendarOverlapPolicy,
        ignoreEventId: String? = null,
    ): OverlapValidationResult {
        val conflicts = existingEvents.filter { ev ->
            ev.id != ignoreEventId && !ev.allDay && ev.eventStatus != "canceled" && run {
                val s = CalendarDateMath.timeToMinutes(ev.startTimeLocal ?: "09:00")
                val e = max(s + 1, CalendarDateMath.timeToMinutes(ev.endTimeLocal ?: "10:00"))
                candidateStartMin < e && candidateEndMin > s
            }
        }.map { it.id }

        if (conflicts.isEmpty()) return OverlapValidationResult.Allowed
        return when (policy) {
            CalendarOverlapPolicy.PREVENT -> OverlapValidationResult.Prevented(conflicts)
            CalendarOverlapPolicy.WARN -> OverlapValidationResult.Warning(conflicts)
            else -> OverlapValidationResult.Allowed
        }
    }

    fun layoutDayEvents(
        events: List<CalendarEvent>,
        policy: CalendarOverlapPolicy,
    ): List<PositionedCalendarEvent> {
        val timed = events
            .filter { !it.allDay && it.eventStatus != "canceled" }
            .map { ev ->
                val s = CalendarDateMath.timeToMinutes(ev.startTimeLocal ?: "09:00")
                val e = max(s + 5, CalendarDateMath.timeToMinutes(ev.endTimeLocal ?: "10:00"))
                Triple(ev, s, e)
            }
            .sortedWith(compareBy<Triple<CalendarEvent, Int, Int>> { it.second }.thenByDescending { it.third - it.second })

        if (timed.isEmpty()) return emptyList()

        // Cluster overlapping events and assign lanes
        val clusters = mutableListOf<MutableList<Triple<CalendarEvent, Int, Int>>>()
        var currentCluster = mutableListOf<Triple<CalendarEvent, Int, Int>>()
        var clusterEnd = -1
        for (item in timed) {
            if (currentCluster.isEmpty() || item.second < clusterEnd) {
                currentCluster.add(item)
                clusterEnd = max(clusterEnd, item.third)
            } else {
                clusters.add(currentCluster)
                currentCluster = mutableListOf(item)
                clusterEnd = item.third
            }
        }
        if (currentCluster.isNotEmpty()) clusters.add(currentCluster)

        val result = mutableListOf<PositionedCalendarEvent>()
        for (cluster in clusters) {
            val laneEndTimes = mutableListOf<Int>()
            val assigned = mutableListOf<Pair<Triple<CalendarEvent, Int, Int>, Int>>()
            for (item in cluster) {
                var chosenLane = laneEndTimes.indexOfFirst { it <= item.second }
                if (chosenLane < 0) {
                    chosenLane = laneEndTimes.size
                    laneEndTimes.add(item.third)
                } else {
                    laneEndTimes[chosenLane] = item.third
                }
                assigned.add(item to chosenLane)
            }
            val totalLanes = max(1, laneEndTimes.size)
            val hasConflict = totalLanes > 1
            for ((triple, lane) in assigned) {
                val (effectiveLane, effectiveCount) = when (policy) {
                    CalendarOverlapPolicy.ALLOW, CalendarOverlapPolicy.STACK -> 0 to 1
                    else -> lane to totalLanes
                }
                result.add(
                    PositionedCalendarEvent(
                        event = triple.first,
                        startMin = triple.second,
                        endMin = triple.third,
                        laneIndex = effectiveLane,
                        laneCount = effectiveCount,
                        hasOverlapWarning = hasConflict && (policy == CalendarOverlapPolicy.WARN || policy == CalendarOverlapPolicy.PREVENT),
                    )
                )
            }
        }
        return result
    }
}
