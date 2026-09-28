package com.raunak.daytimeline.alarm

import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime

/** Pure offline scheduling rules shared by alarm creation and ringing recovery. */
object AlarmSchedulePolicy {
    data class RingPlan(
        val primaryAt: ZonedDateTime,
        val backupAt: ZonedDateTime?,
        val wakeCheckAt: ZonedDateTime?,
        val bedtimeAt: ZonedDateTime?,
        val timeoutAt: ZonedDateTime
    )

    data class RepeatRule(
        val days: Set<DayOfWeek> = emptySet(),
        val enabled: Boolean = false
    ) {
        fun matches(day: DayOfWeek): Boolean = enabled && days.contains(day)
    }

    fun buildPlan(
        primaryAt: ZonedDateTime,
        timeoutMinutes: Int = 20,
        backupEnabled: Boolean = false,
        backupDelayMinutes: Int = 5,
        wakeCheckMinutes: Int = 0,
        bedtimeReminderMinutes: Int = 0
    ): RingPlan {
        val safeTimeout = timeoutMinutes.coerceIn(1, 120)
        val safeBackup = backupDelayMinutes.coerceIn(1, 60)
        val safeWake = wakeCheckMinutes.coerceIn(0, 240)
        val safeBed = bedtimeReminderMinutes.coerceIn(0, 24 * 60)
        return RingPlan(
            primaryAt = primaryAt,
            backupAt = if (backupEnabled) primaryAt.plusMinutes(safeBackup.toLong()) else null,
            wakeCheckAt = if (safeWake > 0) primaryAt.plusMinutes(safeWake.toLong()) else null,
            bedtimeAt = if (safeBed > 0) primaryAt.minusMinutes(safeBed.toLong()) else null,
            timeoutAt = primaryAt.plusMinutes(safeTimeout.toLong())
        )
    }

    fun nextOccurrence(now: ZonedDateTime, hour: Int, minute: Int, repeat: RepeatRule): ZonedDateTime? {
        val candidateToday = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!repeat.enabled) return if (candidateToday.isAfter(now)) candidateToday else candidateToday.plusDays(1)
        for (offset in 0..7) {
            val candidate = candidateToday.plusDays(offset.toLong())
            if (candidate.isAfter(now) && repeat.matches(candidate.dayOfWeek)) return candidate
        }
        return null
    }

    fun gentleVolumeSteps(start: Float, end: Float, seconds: Int, stepSeconds: Int = 5): List<Float> {
        if (seconds <= 0) return listOf(end.coerceIn(0f, 1f))
        val steps = (seconds / stepSeconds.coerceAtLeast(1)).coerceAtLeast(1)
        return (0..steps).map { i ->
            val t = i.toFloat() / steps
            (start + (end - start) * t).coerceIn(0f, 1f)
        }
    }

    fun snoozeAt(now: ZonedDateTime, minutes: Int): ZonedDateTime = now.plusMinutes(minutes.coerceIn(1, 60).toLong())
    fun durationBetween(start: ZonedDateTime, end: ZonedDateTime): Duration = Duration.between(start, end).coerceAtLeast(Duration.ZERO)
}
