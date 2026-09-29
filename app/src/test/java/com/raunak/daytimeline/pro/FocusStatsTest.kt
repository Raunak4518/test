package com.raunak.daytimeline.pro

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.data.PomodoroStateEntity
import com.raunak.daytimeline.domain.PomodoroEngine
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusStatsTest {
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 3, 10)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun report_by_tag_task_hour_and_completion() {
        val s = listOf(
            GardenSession(day.toString(), 50, true, 1, at(9, 30), "DSA", interruptions = 2, rating = 4),
            GardenSession(day.toString(), 25, true, 1, at(14), "DSA", rating = 2),
            GardenSession(day.toString(), 25, true, null, at(21), "Reading"),
            GardenSession(day.toString(), 10, false, null, at(22), "Reading", interruptions = 3),
            GardenSession(day.minusDays(1).toString(), 60, true, 2, at(8))
        )
        val r = FocusStats.report(s, day, day, zone)
        assertThat(r.minutes).isEqualTo(100)
        assertThat(r.sessions).isEqualTo(3)
        assertThat(r.completionRate).isEqualTo(75)
        assertThat(r.byTag.first()).isEqualTo("DSA" to 75)
        assertThat(r.byTask.single()).isEqualTo(1L to 75)
        assertThat(r.byHour[9]).isEqualTo(30)
        assertThat(r.byHour[10]).isEqualTo(20)
        assertThat(r.bestHour).isEqualTo(9)
        assertThat(r.averageRating).isEqualTo(3.0)
        assertThat(r.interruptions).isEqualTo(5)
        assertThat(r.longest).isEqualTo(50)
        assertThat(FocusStats.timeline(s, day, zone).first().first).isEqualTo(9 * 60 + 30)
        assertThat(FocusStats.heatmap(s, day, 2).flatten().first { it.first == day }.second).isEqualTo(100)
        assertThat(FocusStats.pomodorosByTask(s)).containsExactly(1L, 2, 2L, 1)
    }

    @Test
    fun sessions_carry_tag_intention_and_distractions() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = FocusPrefs(ctx)
        prefs.tag = "DSA"; prefs.intention = "Graphs"
        val garden = GardenStore(ctx)
        val focus = PomodoroEngine.start(3, PomodoroStateEntity(), 1_000_000L)
        prefs.addInterruption(1_000_500L); prefs.addInterruption(1_001_000L)
        garden.onTransition(focus, PomodoroEngine.tick(focus, focus.targetEpochMillis))
        val saved = garden.sessions().single()
        assertThat(saved.tag).isEqualTo("DSA")
        assertThat(saved.intention).isEqualTo("Graphs")
        assertThat(saved.interruptions).isEqualTo(2)
        assertThat(saved.startedAt).isEqualTo(1_000_000L)
        assertThat(prefs.interruptions()).isEmpty()
        garden.update(saved.copy(rating = 5))
        assertThat(garden.sessions().single().rating).isEqualTo(5)
        // A 5-minute flow is below the 10-minute minimum → withered
        garden.onFlowStopped(PomodoroEngine.startFlow(null, PomodoroStateEntity(), 2_000_000L), 5, 10)
        assertThat(garden.sessions().last().completed).isFalse()
        assertThat(garden.sessions().last().flow).isTrue()
    }

    @Test
    fun old_config_and_sessions_get_defaults() {
        val c = com.google.gson.Gson().fromJson("{\"mode\":\"FLOW\"}", FocusConfig::class.java).normalized()
        assertThat(c.flow).isTrue()
        assertThat(c.tags).isNotEmpty()
        assertThat(c.dailyGoalMinutes).isEqualTo(240)
        val old = com.google.gson.Gson().fromJson("{\"date\":\"2026-03-10\",\"minutes\":25,\"completed\":true}", GardenSession::class.java)
        assertThat(FocusStats.report(listOf(old), day, day).minutes).isEqualTo(25)
    }
}
