package com.raunak.daytimeline.pro

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime

class FocusGuardEngineTest {
    // 2026-01-05 is a Monday
    private val mondayTen = LocalDateTime.of(2026, 1, 5, 10, 0)
    private val millis = 1_000_000L
    private val schedule = BlockSchedule(1, "Study", setOf(1, 2, 3, 4, 5), 9 * 60, 12 * 60)

    @Test
    fun blocks_listed_app_during_schedule_only() {
        val config = FocusGuardConfig(blockedPackages = setOf("com.insta"), schedules = listOf(schedule))
        val inside = FocusGuardEngine.decide(config, FocusGuardRuntime(), "com.insta", mondayTen, millis)
        assertThat(inside).isInstanceOf(GuardDecision.Block::class.java)
        val outside = FocusGuardEngine.decide(config, FocusGuardRuntime(), "com.insta", mondayTen.withHour(13), millis)
        assertThat(outside).isEqualTo(GuardDecision.Allow)
        val weekend = FocusGuardEngine.decide(config, FocusGuardRuntime(), "com.insta", mondayTen.minusDays(1), millis)
        assertThat(weekend).isEqualTo(GuardDecision.Allow)
    }

    @Test
    fun overnight_schedule_covers_early_morning() {
        val night = BlockSchedule(2, "Sleep", setOf(1), 22 * 60, 6 * 60)
        val config = FocusGuardConfig(schedules = listOf(night))
        assertThat(FocusGuardEngine.activeSchedule(config, mondayTen.withHour(23))).isNotNull()
        assertThat(FocusGuardEngine.activeSchedule(config, mondayTen.plusDays(1).withHour(3))).isNotNull()
        assertThat(FocusGuardEngine.activeSchedule(config, mondayTen.withHour(3))).isNull()
    }

    @Test
    fun allowlist_mode_blocks_everything_else_but_never_system_apps() {
        val config = FocusGuardConfig(allowlistMode = true, allowedPackages = setOf("com.notes"), sessionUntil = millis + 1)
        assertThat(FocusGuardEngine.decide(config, FocusGuardRuntime(), "com.game", mondayTen, millis)).isInstanceOf(GuardDecision.Block::class.java)
        assertThat(FocusGuardEngine.decide(config, FocusGuardRuntime(), "com.notes", mondayTen, millis)).isEqualTo(GuardDecision.Allow)
        assertThat(FocusGuardEngine.decide(config, FocusGuardRuntime(), "com.android.systemui", mondayTen, millis)).isEqualTo(GuardDecision.Allow)
    }

    @Test
    fun locked_mode_disables_emergency_unlock() {
        val config = FocusGuardConfig(blockedPackages = setOf("a"), sessionUntil = millis + 1, lockedMode = true)
        val decision = FocusGuardEngine.decide(config, FocusGuardRuntime(), "a", mondayTen, millis) as GuardDecision.Block
        assertThat(decision.canUnlock).isFalse()
    }

    @Test
    fun daily_limit_blocks_after_quota() {
        val config = FocusGuardConfig(dailyLimits = mapOf("yt" to 30))
        assertThat(FocusGuardEngine.decide(config, FocusGuardRuntime(), "yt", mondayTen, millis, usedMinutesToday = 29)).isEqualTo(GuardDecision.Allow)
        assertThat(FocusGuardEngine.decide(config, FocusGuardRuntime(), "yt", mondayTen, millis, usedMinutesToday = 30)).isInstanceOf(GuardDecision.Block::class.java)
    }

    @Test
    fun mindful_app_intervenes_until_granted() {
        val config = FocusGuardConfig(mindfulPackages = setOf("x"), interventionSeconds = 10)
        assertThat(FocusGuardEngine.decide(config, FocusGuardRuntime(), "x", mondayTen, millis)).isEqualTo(GuardDecision.Intervene(10))
        val granted = FocusGuardEngine.grant(config, FocusGuardRuntime(), "x", millis)
        assertThat(FocusGuardEngine.decide(config, granted, "x", mondayTen, millis + 1000)).isEqualTo(GuardDecision.Allow)
        assertThat(FocusGuardEngine.decide(config, granted, "x", mondayTen, millis + 6 * 60_000)).isEqualTo(GuardDecision.Intervene(10))
    }

    @Test
    fun emergency_unlocks_are_rationed_per_day() {
        val config = FocusGuardConfig(emergencyUnlocksPerDay = 1)
        val day = mondayTen.toLocalDate()
        val first = FocusGuardEngine.useEmergencyUnlock(config, FocusGuardRuntime(), day, millis)
        assertThat(first).isNotNull()
        assertThat(first!!.pausedUntil).isEqualTo(millis + 5 * 60_000)
        assertThat(FocusGuardEngine.useEmergencyUnlock(config, first, day, millis)).isNull()
        assertThat(FocusGuardEngine.useEmergencyUnlock(config, first, day.plusDays(1), millis)).isNotNull()
    }
}
