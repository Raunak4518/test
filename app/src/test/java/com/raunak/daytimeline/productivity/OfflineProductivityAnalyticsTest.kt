package com.raunak.daytimeline.productivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class OfflineProductivityAnalyticsTest {
    @Test fun completionScoreAndStreakAreDeterministic() {
        val d = LocalDate.of(2026, 9, 29)
        val score = LocalProductivityAnalytics.score(d, 4, 3, 90, 5)
        assertEquals(0.75, score.completionRate, 0.0001)
        assertEquals(90, score.focusMinutes)
        assertEquals(5, score.streak)
        assertEquals(2, LocalProductivityAnalytics.currentStreak(setOf(d, d.minusDays(1)), d))
    }

    @Test fun smartPlannerFindsGapAndDetectsOverlap() {
        val d = LocalDate.of(2026, 9, 29)
        val blocks = listOf(
            SmartPlanningEngine.Block(LocalDateTime.of(d, LocalTime.of(9, 0)), LocalDateTime.of(d, LocalTime.of(10, 0)), "Study"),
            SmartPlanningEngine.Block(LocalDateTime.of(d, LocalTime.of(10, 30)), LocalDateTime.of(d, LocalTime.of(11, 0)), "Break")
        )
        val gaps = SmartPlanningEngine.freeGaps(blocks, d, LocalTime.of(8, 0), LocalTime.of(12, 0))
        assertTrue(gaps.any { it.minutes >= 25 })
        val suggestion = SmartPlanningEngine.suggestPlacement(25, gaps)
        assertTrue(suggestion != null)
        val health = SmartPlanningEngine.health(blocks, setOf("Study"), 240)
        assertEquals(0, health.overlapMinutes)
        assertEquals(90, health.plannedMinutes)
    }
}
