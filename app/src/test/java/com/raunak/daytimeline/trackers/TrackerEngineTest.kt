package com.raunak.daytimeline.trackers

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TrackerEngineTest {
    private val today = LocalDate.of(2026, 10, 2) // Friday
    private val createdLongAgo = today.minusDays(60).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun e(t: Tracker, d: LocalDate, v: Double, choice: String? = null) = TrackerEntry(System.nanoTime(), t.id, d.toString(), 600, v, choice)

    @Test
    fun amounts_choices_and_limits() {
        val water = Tracker(createdLongAgo, "Water", type = TrackerType.AMOUNT, unit = "ml", target = 3000.0)
        val list = listOf(e(water, today, 250.0), e(water, today, 500.0))
        assertThat(TrackerEngine.periodValue(water, list, today)).isEqualTo(750.0)
        assertThat(TrackerEngine.met(water, 750.0)).isFalse()
        assertThat(TrackerEngine.progress(water, 1500.0)).isEqualTo(.5f)

        val lunch = Tracker(createdLongAgo + 1, "Lunch", type = TrackerType.CHOICE, choices = listOf("Healthy", "Junk"), goodChoices = setOf("Healthy"))
        assertThat(TrackerEngine.met(lunch, TrackerEngine.value(lunch, listOf(e(lunch, today, 1.0, "Junk"))))).isFalse()
        assertThat(TrackerEngine.met(lunch, TrackerEngine.value(lunch, listOf(e(lunch, today, 1.0, "Healthy"))))).isTrue()

        val reels = Tracker(createdLongAgo + 2, "Short videos", type = TrackerType.DURATION, target = 30.0, goal = TrackerGoal.AT_MOST, source = TrackerSource.APP_USAGE)
        assertThat(TrackerEngine.met(reels, 25.0)).isTrue()
        assertThat(TrackerEngine.met(reels, 45.0)).isFalse()
    }

    @Test
    fun weekly_target_and_streaks() {
        val gym = Tracker(createdLongAgo, "Workout", type = TrackerType.CHECK, target = 3.0, period = TrackerPeriod.WEEK)
        val monday = today.minusDays(4)
        val list = listOf(e(gym, monday, 1.0), e(gym, monday.plusDays(2), 1.0), e(gym, today, 1.0))
        assertThat(TrackerEngine.periodValue(gym, list, today)).isEqualTo(3.0)
        assertThat(TrackerEngine.met(gym, 3.0)).isTrue()

        val walk = Tracker(createdLongAgo, "Walk", type = TrackerType.CHECK, days = (1..5).toSet())
        // Mon–Thu done, weekend off, today (Fri) not yet: the streak still counts the earlier days.
        val walks = (1L..4L).map { e(walk, today.minusDays(it), 1.0) } + e(walk, today.minusDays(7), 1.0)
        assertThat(TrackerEngine.streak(walk, walks, today)).isEqualTo(5)
        assertThat(TrackerEngine.streak(walk, walks + e(walk, today, 1.0), today)).isEqualTo(6)
    }

    @Test
    fun templates_follow_mess_times() {
        val food = TrackerTemplates.packs.first { it.name == "Food" }.trackers(1000, mapOf("Breakfast" to (7 * 60 + 30 to 8 * 60 + 30)))
        val breakfast = food.first { it.name == "Breakfast" }
        assertThat(breakfast.windowStart).isEqualTo(7 * 60 + 30)
        assertThat(breakfast.reminders).containsExactly(8 * 60 + 15)
        assertThat(food.map { it.name }).containsAtLeast("Water", "Fruits", "Lunch", "Dinner")
    }
}
