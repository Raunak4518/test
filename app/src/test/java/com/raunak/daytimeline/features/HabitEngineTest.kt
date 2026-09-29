package com.raunak.daytimeline.features

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class HabitEngineTest {
    // 2026-01-05 is a Monday
    private val mon = LocalDate.of(2026, 1, 5)
    private fun habit(vararg doneOffsets: Long, freq: HabitFrequency = HabitFrequency.DAYS, days: Set<Int> = (1..7).toSet(), perWeek: Int = 3, interval: Int = 2, skipped: Set<Long> = emptySet()) =
        OfflineHabit(1, "Read", perWeek, "07:30", days, doneOffsets.map { mon.plusDays(it).toString() }.toSet(),
            frequency = freq.name, intervalDays = interval, skippedDates = skipped.map { mon.plusDays(it).toString() }.toSet(), createdDate = mon.toString())

    @Test
    fun daily_streak_ignores_unfinished_today_and_skips() {
        val h = habit(0, 1, 2, 4, skipped = setOf(3))
        val s = HabitEngine.streak(h, mon.plusDays(5))
        assertThat(s.current).isEqualTo(4) // skip on day 3 bridges; today (day 5) not over yet
        assertThat(s.best).isEqualTo(4)
        assertThat(HabitEngine.streak(habit(0, 1, 3), mon.plusDays(3)).current).isEqualTo(1)
    }

    @Test
    fun specific_days_only_count_planned_days() {
        val mwf = habit(0, 2, 4, 7, days = setOf(1, 3, 5))
        assertThat(HabitEngine.streak(mwf, mon.plusDays(8)).current).isEqualTo(4)
        assertThat(HabitEngine.scheduled(mwf, mon.plusDays(1))).isFalse()
        assertThat(HabitEngine.cell(mwf, mon.plusDays(1), mon.plusDays(8))).isEqualTo(HabitCell.OFF)
        assertThat(HabitEngine.completionRate(mwf, mon.plusDays(8))).isEqualTo(100)
    }

    @Test
    fun weekly_target_is_judged_per_week() {
        val h = habit(0, 2, 3, 7, 9, 11, 14, freq = HabitFrequency.WEEKLY, perWeek = 3)
        assertThat(HabitEngine.weekCount(h, mon)).isEqualTo(3)
        assertThat(HabitEngine.streak(h, mon.plusDays(15)).current).isEqualTo(2) // week 3 still in progress
        assertThat(HabitEngine.dueToday(h, mon.plusDays(15))).isTrue()
        assertThat(HabitEngine.dueToday(h, mon.plusDays(5))).isFalse() // target already met
    }

    @Test
    fun interval_habit_due_every_n_days() {
        val h = habit(0, 3, 6, freq = HabitFrequency.INTERVAL, interval = 3)
        assertThat(HabitEngine.dueToday(h, mon.plusDays(7))).isFalse()
        assertThat(HabitEngine.dueToday(h, mon.plusDays(9))).isTrue()
        assertThat(HabitEngine.streak(h, mon.plusDays(8)).current).isEqualTo(3)
        assertThat(HabitEngine.streak(h, mon.plusDays(12)).current).isEqualTo(0)
    }

    @Test
    fun strength_grows_and_recovers_gradually() {
        val month = habit(*(0L..29L).toList().toLongArray())
        val strong = HabitEngine.strength(month, mon.plusDays(29))
        assertThat(strong).isGreaterThan(70)
        val oneMiss = habit(*((0L..29L).toList() - 20L).toLongArray())
        val afterMiss = HabitEngine.strength(oneMiss, mon.plusDays(29))
        assertThat(afterMiss).isLessThan(strong)
        assertThat(afterMiss).isGreaterThan(strong - 10) // one miss is a dent, not a reset
        assertThat(HabitEngine.strength(habit(), mon.plusDays(10))).isEqualTo(0)
    }

    @Test
    fun older_saved_habits_get_defaults() {
        val old = com.google.gson.Gson().fromJson("{\"id\":1,\"name\":\"Run\",\"targetPerWeek\":7,\"preferredTime\":\"\",\"activeDays\":[1,2,3,4,5,6,7],\"completedDates\":[\"2026-01-05\"]}", OfflineHabit::class.java)
        assertThat(HabitEngine.frequency(old)).isEqualTo(HabitFrequency.DAYS)
        assertThat(HabitEngine.skipped(old)).isEmpty()
        assertThat(HabitEngine.streak(old, mon).current).isEqualTo(1)
        assertThat(HabitEngine.describe(old)).isEqualTo("Every day")
        assertThat(HabitEngine.heatmap(old, mon, 4)).hasSize(4)
    }
}
