package com.raunak.daytimeline.alarm

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.WeekFields

object AlarmSchedulePlanner {
    fun isRepeating(config: AlarmPersistentConfig): Boolean {
        val mode = config.advancedRepeat().mode
        return when (mode) {
            AlarmScheduleMode.WEEKLY -> config.repeatDays.isNotEmpty()
            AlarmScheduleMode.ODD_WEEKS, AlarmScheduleMode.EVEN_WEEKS, AlarmScheduleMode.EVERY_N_DAYS -> true
            AlarmScheduleMode.ONE_SHOT, AlarmScheduleMode.NAP, AlarmScheduleMode.POWER_NAP -> false
        }
    }

    fun nextOccurrence(config: AlarmPersistentConfig, now: LocalDateTime = LocalDateTime.now()): Long {
        val requested = LocalTime.of(config.hour, config.minute)
        val rule = config.advancedRepeat()
        for (offset in 0..366) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val candidate = LocalDateTime.of(date, requested)
            if (!candidate.isAfter(now.plusSeconds(1))) continue
            if (matches(config, candidate, rule)) {
                return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return LocalDateTime.of(now.toLocalDate().plusDays(1), requested)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun matches(config: AlarmPersistentConfig, dateTime: LocalDateTime, rule: AdvancedRepeatRule = config.advancedRepeat()): Boolean {
        if (rule.mode == AlarmScheduleMode.WEEKLY && config.repeatDays.isEmpty()) {
            return true
        }
        return AlarmRepeatPlanner.matches(dateTime, rule, dateTime)
    }

    fun backupAt(primaryMillis: Long, config: AlarmPersistentConfig): Long? =
        if (config.backupAlarmEnabled) primaryMillis + config.backupDelayMinutes * 60_000L else null

    fun wakeCheckAt(primaryMillis: Long, config: AlarmPersistentConfig): Long? =
        if (config.wakeCheckMinutes > 0) primaryMillis + config.wakeCheckMinutes * 60_000L else null

    fun bedtimeAt(primaryMillis: Long, config: AlarmPersistentConfig): Long? =
        if (config.bedtimeReminderMinutes > 0) primaryMillis - config.bedtimeReminderMinutes * 60_000L else null

    fun wakeChecksAfterDismissal(dismissedAt: Long, config: AlarmPersistentConfig): List<Long> =
        WakeCheckPlanner.checkTimes(dismissedAt, config.wakeCheckPolicy())

    fun dayNumber(date: LocalDate): Long = date.toEpochDay()

    private fun javaCalendarDay(day: DayOfWeek): Int = when (day) {
        DayOfWeek.SUNDAY -> 1; DayOfWeek.MONDAY -> 2; DayOfWeek.TUESDAY -> 3; DayOfWeek.WEDNESDAY -> 4
        DayOfWeek.THURSDAY -> 5; DayOfWeek.FRIDAY -> 6; DayOfWeek.SATURDAY -> 7
    }
}
