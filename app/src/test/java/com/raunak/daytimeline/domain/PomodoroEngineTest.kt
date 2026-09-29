package com.raunak.daytimeline.domain

import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.data.PomodoroStateEntity
import org.junit.Test

class PomodoroEngineTest {
    @Test
    fun transitions_from_focus_to_break() {
        val state = PomodoroStateEntity(phase = "FOCUS", targetEpochMillis = System.currentTimeMillis() - 1000, running = true, focusMinutes = 25)
        val next = PomodoroEngine.tick(state)
        assertThat(next.phase).contains("BREAK")
    }
}

class PomodoroSkipExtendTest {
    private val now = 1_000_000_000L

    @Test
    fun skip_moves_focus_to_break() {
        val state = PomodoroStateEntity(phase = "FOCUS", targetEpochMillis = now + 600_000, running = true, cycleIndex = 1)
        val next = PomodoroEngine.skip(state, now)
        assertThat(next.phase).isEqualTo("SHORT_BREAK")
        assertThat(next.remainingSeconds).isEqualTo(5 * 60L)
    }

    @Test
    fun skip_keeps_paused_state_paused() {
        val state = PomodoroStateEntity(phase = "SHORT_BREAK", remainingSeconds = 100, running = false, cycleIndex = 1)
        val next = PomodoroEngine.skip(state, now)
        assertThat(next.phase).isEqualTo("FOCUS")
        assertThat(next.running).isFalse()
        assertThat(next.remainingSeconds).isEqualTo(25 * 60L)
    }

    @Test
    fun extend_adds_minutes() {
        val running = PomodoroStateEntity(phase = "FOCUS", targetEpochMillis = now + 60_000, running = true)
        assertThat(PomodoroEngine.extend(running, 5, now).remainingSeconds).isEqualTo(360L)
        val paused = PomodoroStateEntity(phase = "FOCUS", remainingSeconds = 60, running = false)
        assertThat(PomodoroEngine.extend(paused, 5, now).remainingSeconds).isEqualTo(360L)
        assertThat(PomodoroEngine.extend(PomodoroStateEntity(), 5, now).phase).isEqualTo("IDLE")
    }
}
