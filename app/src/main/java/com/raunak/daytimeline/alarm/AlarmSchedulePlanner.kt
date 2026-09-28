package com.raunak.daytimeline.alarm

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AlarmSchedulePlanner {
    fun nextOccurrence(config: AlarmPersistentConfig, now: LocalDateTime = LocalDateTime.now()): Long {
        val requested = LocalTime.of(config.hour, config.minute)
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val dow = javaCalendarDay(date.dayOfWeek)
            if (config.repeatDays.isNotEmpty() && dow !in config.repeatDays) continue
            val candidate = LocalDateTime.of(date, requested)
            if (candidate.isAfter(now.plusSeconds(1))) return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val tomorrow = now.toLocalDate().plusDays(1)
        return LocalDateTime.of(tomorrow, requested).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun backupAt(primaryMillis: Long, config: AlarmPersistentConfig): Long? = if (config.backupAlarmEnabled) primaryMillis + config.backupDelayMinutes * 60_000L else null
    fun wakeCheckAt(primaryMillis: Long, config: AlarmPersistentConfig): Long? = if (config.wakeCheckMinutes > 0) primaryMillis + config.wakeCheckMinutes * 60_000L else null
    fun bedtimeAt(primaryMillis: Long, config: AlarmPersistentConfig): Long? = if (config.bedtimeReminderMinutes > 0) primaryMillis - config.bedtimeReminderMinutes * 60_000L else null

    private fun javaCalendarDay(day: DayOfWeek): Int = when (day) {
        DayOfWeek.SUNDAY -> 1; DayOfWeek.MONDAY -> 2; DayOfWeek.TUESDAY -> 3; DayOfWeek.WEDNESDAY -> 4
        DayOfWeek.THURSDAY -> 5; DayOfWeek.FRIDAY -> 6; DayOfWeek.SATURDAY -> 7
    }
}
