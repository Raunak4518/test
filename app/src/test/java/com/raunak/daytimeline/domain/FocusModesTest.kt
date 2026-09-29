package com.raunak.daytimeline.domain

import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.data.PomodoroStateEntity
import org.junit.Test

class FocusModesTest {
    private val t0 = 1_000_000_000L
    private val base = PomodoroStateEntity(focusMinutes = 25, shortBreakMinutes = 5, longBreakMinutes = 15, cyclesPerRound = 2)

    @Test
    fun auto_start_off_waits_for_a_tap() {
        val focus = PomodoroEngine.start(null, base, t0)
        val brk = PomodoroEngine.tick(focus, t0 + 25 * 60_000L, autoBreak = false, autoFocus = false)
        assertThat(brk.phase).isEqualTo("SHORT_BREAK")
        assertThat(brk.running).isFalse()
        assertThat(PomodoroEngine.waiting(brk)).isTrue()
        assertThat(brk.remainingSeconds).isEqualTo(5 * 60L)
        // Waiting doesn't tick down
        assertThat(PomodoroEngine.tick(brk, t0 + 40 * 60_000L)).isEqualTo(brk)
        val started = PomodoroEngine.resume(brk, t0 + 30 * 60_000L)
        assertThat(started.targetEpochMillis).isEqualTo(t0 + 35 * 60_000L)
        val next = PomodoroEngine.tick(started, t0 + 35 * 60_000L, autoBreak = true, autoFocus = false)
        assertThat(next.phase).isEqualTo("FOCUS")
        assertThat(next.cycleIndex).isEqualTo(2)
        assertThat(PomodoroEngine.waiting(next)).isTrue()
    }

    @Test
    fun long_break_after_the_round() {
        var s = PomodoroEngine.start(null, base, t0)
        s = PomodoroEngine.tick(s, t0 + 25 * 60_000L)
        s = PomodoroEngine.tick(s, s.targetEpochMillis)
        assertThat(s.cycleIndex).isEqualTo(2)
        s = PomodoroEngine.tick(s, s.targetEpochMillis)
        assertThat(s.phase).isEqualTo("LONG_BREAK")
    }

    @Test
    fun flow_counts_up_pauses_and_earns_a_break() {
        val f = PomodoroEngine.startFlow(7, base, t0)
        assertThat(PomodoroEngine.flowElapsed(f, t0 + 30 * 60_000L)).isEqualTo(1800L)
        val paused = PomodoroEngine.pause(f, t0 + 30 * 60_000L)
        assertThat(PomodoroEngine.flowElapsed(paused, t0 + 99 * 60_000L)).isEqualTo(1800L)
        val resumed = PomodoroEngine.resume(paused, t0 + 40 * 60_000L)
        assertThat(PomodoroEngine.flowElapsed(resumed, t0 + 60 * 60_000L)).isEqualTo(50 * 60L)
        val (brk, minutes) = PomodoroEngine.stopFlow(resumed, t0 + 60 * 60_000L, divisor = 5)
        assertThat(minutes).isEqualTo(50)
        assertThat(brk.phase).isEqualTo("SHORT_BREAK")
        assertThat(brk.remainingSeconds).isEqualTo(10 * 60L)
        assertThat(brk.running).isTrue()
        assertThat(PomodoroEngine.flowBreakMinutes(120, 5)).isEqualTo(1)
        // Skip and extend don't apply to a flow session
        assertThat(PomodoroEngine.skip(f, t0)).isEqualTo(f)
        assertThat(PomodoroEngine.extend(f, 5, t0)).isEqualTo(f)
    }
}
