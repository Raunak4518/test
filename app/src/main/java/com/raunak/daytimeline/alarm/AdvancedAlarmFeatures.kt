package com.raunak.daytimeline.alarm

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Offline alarm features inspired by advanced commercial alarm systems. */
enum class AlarmScheduleMode { WEEKLY, ODD_WEEKS, EVEN_WEEKS, EVERY_N_DAYS, ONE_SHOT, NAP, POWER_NAP }

data class AdvancedRepeatRule(
    val mode: AlarmScheduleMode = AlarmScheduleMode.WEEKLY,
    val weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
    val intervalDays: Int = 1,
    val anchorDate: java.time.LocalDate? = null
) {
    fun normalized() = copy(intervalDays = intervalDays.coerceIn(1, 16))
}

data class SnoozePolicy(
    val durationMinutes: Int = 5,
    val maxCount: Int = 3,
    val maxTotalMinutes: Int = 0,
    val halveEachTime: Boolean = false,
    val allowAfterScheduledTime: Boolean = true,
    val optionsMinutes: List<Int> = listOf(5, 10, 15)
) {
    fun normalized() = copy(
        durationMinutes = durationMinutes.coerceIn(1, 60),
        maxCount = maxCount.coerceIn(0, 20),
        maxTotalMinutes = maxTotalMinutes.coerceIn(0, 240),
        optionsMinutes = optionsMinutes.filter { it in 1..60 }.distinct().sorted().take(8)
    )

    fun nextDuration(snoozesUsed: Int, totalUsedMinutes: Int, now: LocalDateTime, scheduled: LocalDateTime): Int {
        if (snoozesUsed >= maxCount) return 0
        if (!allowAfterScheduledTime && now >= scheduled) return 0
        var minutes = if (halveEachTime) max(1, ceil(durationMinutes / (1 shl min(snoozesUsed, 6)).toDouble()).toInt()) else durationMinutes
        if (maxTotalMinutes > 0) minutes = min(minutes, maxTotalMinutes - totalUsedMinutes)
        return max(0, minutes)
    }
}

data class WakeCheckPolicy(
    val enabled: Boolean = false,
    val checkAfterMinutes: Int = 10,
    val confirmationWindowMinutes: Int = 5,
    val maxRetries: Int = 2,
    val retryDelayMinutes: Int = 5
) {
    fun normalized() = copy(
        checkAfterMinutes = checkAfterMinutes.coerceIn(1, 180),
        confirmationWindowMinutes = confirmationWindowMinutes.coerceIn(1, 30),
        maxRetries = maxRetries.coerceIn(0, 10),
        retryDelayMinutes = retryDelayMinutes.coerceIn(1, 60)
    )
}

object WakeCheckPlanner {
    fun checkTimes(dismissedAt: Long, policy: WakeCheckPolicy): List<Long> {
        if (!policy.enabled) return emptyList()
        val p = policy.normalized()
        return (0..p.maxRetries).map { retry ->
            dismissedAt + (p.checkAfterMinutes + retry * p.retryDelayMinutes) * 60_000L
        }
    }
}

data class NapSpec(
    val durationMinutes: Int,
    val deleteAfterRinging: Boolean = true,
    val missionChain: List<AlarmMission> = emptyList()
) {
    init { require(durationMinutes in 1..600) }
}

data class PowerNapSpec(
    val minimumSleepMinutes: Int = 20,
    val smartWindowMinutes: Int = 40,
    val deleteAfterRinging: Boolean = true
) {
    fun normalized() = copy(
        minimumSleepMinutes = minimumSleepMinutes.coerceIn(1, 600),
        smartWindowMinutes = smartWindowMinutes.coerceIn(0, 240)
    )
}

object NapPlanner {
    fun napAt(startedAt: Long, spec: NapSpec): Long = startedAt + spec.durationMinutes * 60_000L

    fun powerNapWindow(startedAt: Long, spec: PowerNapSpec): LongRange {
        val p = spec.normalized()
        val start = startedAt + p.minimumSleepMinutes * 60_000L
        val end = start + p.smartWindowMinutes * 60_000L
        return start..end
    }

    fun fixedPowerNapFallback(startedAt: Long, spec: PowerNapSpec): Long =
        startedAt + (spec.normalized().minimumSleepMinutes + spec.normalized().smartWindowMinutes) * 60_000L
}

data class AlarmSkipState(val alarmId: Long, val skipNext: Boolean = false)

object AlarmRepeatPlanner {
    fun matches(dateTime: LocalDateTime, rule: AdvancedRepeatRule, now: LocalDateTime = dateTime): Boolean {
        val r = rule.normalized()
        return when (r.mode) {
            AlarmScheduleMode.WEEKLY -> r.weekdays.contains(dateTime.dayOfWeek)
            AlarmScheduleMode.ODD_WEEKS -> r.weekdays.contains(dateTime.dayOfWeek) && dateTime.toLocalDate().get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()) % 2 == 1
            AlarmScheduleMode.EVEN_WEEKS -> r.weekdays.contains(dateTime.dayOfWeek) && dateTime.toLocalDate().get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()) % 2 == 0
            AlarmScheduleMode.EVERY_N_DAYS -> r.anchorDate != null && ChronoUnit.DAYS.between(r.anchorDate, dateTime.toLocalDate()) >= 0 && ChronoUnit.DAYS.between(r.anchorDate, dateTime.toLocalDate()) % r.intervalDays.toLong() == 0L
            AlarmScheduleMode.ONE_SHOT -> r.anchorDate == null || dateTime.toLocalDate() == r.anchorDate
            AlarmScheduleMode.NAP, AlarmScheduleMode.POWER_NAP -> false
        }
    }
}

object AlarmPresets {
    fun heavySleeper(hour: Int = 7, minute: Int = 0) = AlarmEditorModel(
        hour = hour, minute = minute, label = "Heavy sleeper",
        maxSnoozes = 0, backupEnabled = true, wakeCheckMinutes = 10,
        missions = listOf(
            AlarmMissionCatalog.default(AlarmMissionType.MATH, 3),
            AlarmMissionCatalog.default(AlarmMissionType.SHAKE, 3),
            AlarmMissionCatalog.default(AlarmMissionType.WALK, 2)
        )
    )

    fun gentle(hour: Int = 7, minute: Int = 0) = AlarmEditorModel(
        hour = hour, minute = minute, label = "Gentle wake",
        snoozeMinutes = 10, maxSnoozes = 2, gentleVolumeSeconds = 120
    )

    fun workday(hour: Int = 7, minute: Int = 0) = AlarmEditorModel(
        hour = hour, minute = minute, label = "Workday",
        repeatDays = setOf(2,3,4,5,6), maxSnoozes = 1, backupEnabled = true,
        wakeCheckMinutes = 10, missions = listOf(AlarmMissionCatalog.default(AlarmMissionType.TYPING, 2))
    )

    fun examDay(hour: Int = 6, minute: Int = 0) = AlarmEditorModel(
        hour = hour, minute = minute, label = "Exam day",
        maxSnoozes = 0, backupEnabled = true, backupDelayMinutes = 3,
        wakeCheckMinutes = 5, missions = listOf(
            AlarmMissionCatalog.default(AlarmMissionType.MATH, 4),
            AlarmMissionCatalog.default(AlarmMissionType.MEMORY, 3),
            AlarmMissionCatalog.default(AlarmMissionType.WALK, 3)
        )
    )
}
