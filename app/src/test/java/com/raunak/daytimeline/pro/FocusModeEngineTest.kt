package com.raunak.daytimeline.pro

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime

class FocusModeEngineTest {
    private val now = LocalDateTime.of(2026, 9, 30, 10, 0) // a Wednesday
    private val ms = 1_000_000_000L
    private val base = FocusGuardConfig(focusModeApps = setOf("com.insta"))

    private fun decide(c: FocusGuardConfig, pkg: String = "com.insta") = FocusGuardEngine.decide(c, FocusGuardRuntime(), pkg, now, ms)

    @Test
    fun pauses_only_chosen_apps_while_on() {
        assertThat(decide(base)).isEqualTo(GuardDecision.Allow)
        val on = base.copy(focusModeOn = true)
        assertThat(decide(on)).isInstanceOf(GuardDecision.Block::class.java)
        assertThat(decide(on, "com.notes")).isEqualTo(GuardDecision.Allow)
        // Focus mode does not pull in the separate blocker list.
        assertThat(decide(on.copy(blockedPackages = setOf("com.game")), "com.game")).isEqualTo(GuardDecision.Allow)
    }

    @Test
    fun timer_break_and_schedule() {
        assertThat(decide(base.copy(focusModeOn = true, focusModeUntil = ms - 1))).isEqualTo(GuardDecision.Allow)
        assertThat(decide(base.copy(focusModeOn = true, focusModeUntil = ms + 60_000))).isInstanceOf(GuardDecision.Block::class.java)
        assertThat(decide(base.copy(focusModeOn = true, focusModeBreakUntil = ms + 60_000))).isEqualTo(GuardDecision.Allow)
        val scheduled = base.copy(focusModeSchedules = listOf(BlockSchedule(1, "Study", setOf(3), 9 * 60, 12 * 60)))
        assertThat(decide(scheduled)).isInstanceOf(GuardDecision.Block::class.java)
        assertThat(FocusMode.status(scheduled, ms, now).scheduled).isTrue()
        assertThat(FocusMode.canTurnOff(scheduled, ms)).isFalse()
    }

    @Test
    fun strict_blocks_unlocks_and_early_off() {
        val strict = base.copy(focusModeOn = true, focusModeStrict = true, focusModeUntil = System.currentTimeMillis() + 3_600_000)
        val d = decide(strict) as GuardDecision.Block
        assertThat(d.canUnlock).isFalse()
        assertThat(FocusMode.canTurnOff(strict)).isFalse()
        // Strict without a timer can still be switched off, so it never locks you in.
        assertThat(FocusMode.canTurnOff(strict.copy(focusModeUntil = 0))).isTrue()
    }
}
