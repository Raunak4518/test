package com.raunak.daytimeline.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class AdvancedAlarmFeaturesTest {
    @Test fun halveSnoozeShrinksEachTime() {
        val p = SnoozePolicy(durationMinutes = 20, maxCount = 5, halveEachTime = true)
        val now = LocalDateTime.of(2026, 9, 29, 7, 0)
        assertEquals(20, p.nextDuration(0, 0, now, now.plusHours(1)))
        assertEquals(10, p.nextDuration(1, 20, now, now.plusHours(1)))
        assertEquals(5, p.nextDuration(2, 30, now, now.plusHours(1)))
    }

    @Test fun totalSnoozeLimitWins() {
        val p = SnoozePolicy(durationMinutes = 10, maxCount = 5, maxTotalMinutes = 25)
        val now = LocalDateTime.of(2026, 9, 29, 7, 0)
        assertEquals(5, p.nextDuration(2, 20, now, now.plusHours(1)))
        assertEquals(0, p.nextDuration(3, 25, now, now.plusHours(1)))
    }

    @Test fun weekdayRuleMatchesOnlyConfiguredDays() {
        val rule = AdvancedRepeatRule(
            mode = AlarmScheduleMode.WEEKLY,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        )
        assertTrue(AlarmRepeatPlanner.matches(LocalDateTime.of(2026, 9, 30, 7, 0), rule))
    }

    @Test fun napUsesCountdown() {
        val start = LocalDateTime.of(2026, 9, 29, 14, 0)
        val millis = start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val wake = NapPlanner.napAt(millis, NapSpec(20))
        assertEquals(20 * 60_000L, wake - millis)
    }

    @Test fun presetsHaveRealMissionChains() {
        assertTrue(AlarmPresets.examDay().missions.size >= 2)
        assertEquals("Heavy sleeper", AlarmPresets.heavySleeper().label)
    }
}
