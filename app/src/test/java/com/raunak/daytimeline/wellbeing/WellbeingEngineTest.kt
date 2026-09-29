package com.raunak.daytimeline.wellbeing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WellbeingEngineTest {
    private val monday = LocalDateTime.of(2026, 1, 5, 15, 0)
    private val saturday = LocalDateTime.of(2026, 1, 10, 15, 0)
    private val ms = 10_000_000L

    @Test
    fun weekday_and_weekend_app_timers() {
        val c = WellbeingConfig(weekendLimits = mapOf("ig" to 90), warnMinutesBefore = 0)
        val weekday = mapOf("ig" to 30)
        val used = UsageSnapshot(todayMinutes = mapOf("ig" to 45))
        assertThat(WellbeingEngine.decide(c, weekday, "ig", monday, ms, used)).isInstanceOf(WellbeingDecision.Block::class.java)
        assertThat(WellbeingEngine.decide(c, weekday, "ig", saturday, ms, used)).isEqualTo(WellbeingDecision.Allow)
    }

    @Test
    fun warns_before_limit() {
        val c = WellbeingConfig(warnMinutesBefore = 5)
        val d = WellbeingEngine.decide(c, mapOf("yt" to 60), "yt", monday, ms, UsageSnapshot(todayMinutes = mapOf("yt" to 57)))
        assertThat(d).isEqualTo(WellbeingDecision.Warn(3, "app timer"))
    }

    @Test
    fun group_total_open_and_session_limits() {
        val group = GroupLimit(1, "Social", setOf("ig", "sc"), 60)
        val c = WellbeingConfig(groupLimits = listOf(group), warnMinutesBefore = 0)
        val usage = UsageSnapshot(todayMinutes = mapOf("ig" to 40, "sc" to 25))
        assertThat((WellbeingEngine.decide(c, emptyMap(), "sc", monday, ms, usage) as WellbeingDecision.Block).reason).contains("Social")

        val total = WellbeingConfig(totalDailyLimitMinutes = 120, warnMinutesBefore = 0)
        assertThat(WellbeingEngine.decide(total, emptyMap(), "any", monday, ms, UsageSnapshot(totalTodayMinutes = 121))).isInstanceOf(WellbeingDecision.Block::class.java)

        val opens = WellbeingConfig(openLimits = mapOf("x" to 5))
        assertThat(WellbeingEngine.decide(opens, emptyMap(), "x", monday, ms, UsageSnapshot(opensToday = mapOf("x" to 5)))).isEqualTo(WellbeingDecision.Allow)
        assertThat(WellbeingEngine.decide(opens, emptyMap(), "x", monday, ms, UsageSnapshot(opensToday = mapOf("x" to 6)))).isInstanceOf(WellbeingDecision.Block::class.java)

        val session = WellbeingConfig(sessionLimits = mapOf("tt" to SessionLimit(15, 30)), warnMinutesBefore = 0)
        val over = WellbeingEngine.decide(session, emptyMap(), "tt", monday, ms, UsageSnapshot(currentSessionMinutes = 15)) as WellbeingDecision.Block
        assertThat(over.sessionCooldown).isTrue()
        val cooling = WellbeingEngine.decide(session, emptyMap(), "tt", monday, ms, UsageSnapshot(cooldownUntil = ms + 10 * 60_000)) as WellbeingDecision.Block
        assertThat(cooling.reason).contains("10 min")
    }

    @Test
    fun bedtime_blocks_except_allowed_and_spans_midnight() {
        val c = WellbeingConfig(bedtime = BedtimeConfig(enabled = true, startMinute = 23 * 60, endMinute = 6 * 60, allowedPackages = setOf("clock")))
        val late = monday.withHour(23).withMinute(30)
        val early = monday.plusDays(1).withHour(5)
        assertThat(WellbeingEngine.decide(c, emptyMap(), "ig", late, ms, UsageSnapshot())).isInstanceOf(WellbeingDecision.Block::class.java)
        assertThat(WellbeingEngine.decide(c, emptyMap(), "ig", early, ms, UsageSnapshot())).isInstanceOf(WellbeingDecision.Block::class.java)
        assertThat(WellbeingEngine.decide(c, emptyMap(), "clock", late, ms, UsageSnapshot())).isEqualTo(WellbeingDecision.Allow)
        assertThat(WellbeingEngine.decide(c, emptyMap(), "ig", monday, ms, UsageSnapshot())).isEqualTo(WellbeingDecision.Allow)
    }

    @Test
    fun progressive_pause() {
        assertThat(WellbeingEngine.pauseSeconds(8, 3, 1)).isEqualTo(8)
        assertThat(WellbeingEngine.pauseSeconds(8, 3, 5)).isEqualTo(20)
        assertThat(WellbeingEngine.pauseSeconds(8, 3, 100)).isEqualTo(60)
    }

    @Test
    fun usage_summary_counts_time_opens_pickups_and_hours() {
        val start = 0L
        val h = 3_600_000L
        val events = listOf(
            UsageEventLite(-10 * 60_000L, UsageEventType.APP_FOREGROUND, "ig"), // open before midnight
            UsageEventLite(10 * 60_000L, UsageEventType.APP_BACKGROUND, "ig"),
            UsageEventLite(h, UsageEventType.UNLOCK),
            UsageEventLite(h + 1000, UsageEventType.APP_FOREGROUND, "yt"),
            UsageEventLite(h + 30 * 60_000L, UsageEventType.APP_FOREGROUND, "ig"), // yt implicitly left
            UsageEventLite(h + 40 * 60_000L, UsageEventType.SCREEN_OFF),
            UsageEventLite(2 * h, UsageEventType.UNLOCK),
            UsageEventLite(2 * h + 1, UsageEventType.NOTIFICATION, "wa"),
            UsageEventLite(2 * h + 5, UsageEventType.APP_FOREGROUND, "launcher")
        )
        val d = UsageStatsCalculator.summarize(events, start, 3 * h, ignore = setOf("launcher"))
        assertThat(d.apps.first { it.pkg == "ig" }.minutes).isEqualTo(20)
        assertThat(d.apps.first { it.pkg == "yt" }.minutes).isEqualTo(29)
        assertThat(d.apps.first { it.pkg == "ig" }.opens).isEqualTo(1)
        assertThat(d.totalMinutes).isEqualTo(49)
        assertThat(d.pickups).isEqualTo(2)
        assertThat(d.firstPickup).isEqualTo(h)
        assertThat(d.notifications).isEqualTo(1)
        assertThat(d.hourlyMinutes[0]).isEqualTo(10)
        assertThat(d.hourlyMinutes[1]).isEqualTo(39)
        assertThat(d.longestSessionMinutes).isEqualTo(29)
    }

    @Test
    fun streaks_changes_and_digest() {
        val today = LocalDate.of(2026, 1, 10)
        val totals = mapOf(today to 100, today.minusDays(1) to 150, today.minusDays(2) to 200, today.minusDays(3) to 120)
        assertThat(UsageStatsCalculator.goalStreak(totals, 180, today)).isEqualTo(2)
        assertThat(UsageStatsCalculator.percentChange(90, 120)).isEqualTo(-25)
        assertThat(UsageStatsCalculator.percentChange(90, 0)).isNull()

        val c = WellbeingConfig(quietApps = setOf("wa"), quietOnlyDuringFocus = true)
        assertThat(NotificationDigest.shouldHold(c, "wa", focusOrBedtime = false)).isFalse()
        assertThat(NotificationDigest.shouldHold(c, "wa", focusOrBedtime = true)).isTrue()
        val held = listOf(HeldNotification(1, "wa", "WhatsApp", "Mom", ""), HeldNotification(2, "wa", "WhatsApp", "Team", ""), HeldNotification(3, "g", "Gmail", "Invoice", ""))
        assertThat(NotificationDigest.summary(held)).containsExactly("WhatsApp (2): Mom, Team", "Gmail (1): Invoice").inOrder()
        assertThat(NotificationDigest.nextDigest(listOf(720, 1080), 800)).isEqualTo(1080 to false)
        assertThat(NotificationDigest.nextDigest(listOf(720, 1080), 1200)).isEqualTo(720 to true)
    }
}
