package com.raunak.daytimeline.productivity

import java.time.DayOfWeek
import java.time.LocalDate

object LocalProductivityAnalytics {
    data class DayScore(val date: LocalDate, val completionRate: Double, val focusMinutes: Long, val streak: Int)
    fun score(date: LocalDate, planned: Int, completed: Int, focusMinutes: Long, streak: Int): DayScore = DayScore(date, if (planned == 0) 0.0 else (completed.toDouble() / planned).coerceIn(0.0, 1.0), focusMinutes, streak)
    fun weeklyFocus(days: Map<LocalDate, Long>): Map<DayOfWeek, Long> = days.entries.groupBy({ it.key.dayOfWeek }, { it.value }).mapValues { it.value.sum() }
    fun currentStreak(completedDates: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int { var cursor = today; var count = 0; while (cursor in completedDates) { count++; cursor = cursor.minusDays(1) }; return count }
}
