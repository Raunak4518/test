package com.raunak.daytimeline.wellbeing

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Combined limit for a group of apps, e.g. "Social" = Instagram + Snapchat + X at 60 min/day. */
data class GroupLimit(val id: Long, val name: String, val packages: Set<String>, val minutes: Int)

/** ScreenZen / Lock Me Out style: at most [maxMinutes] in one go, then a [cooldownMinutes] break. */
data class SessionLimit(val maxMinutes: Int, val cooldownMinutes: Int)

/** Short-video surfaces inside apps that can be blocked while the rest of the app keeps working. */
enum class ShortForm(val label: String, val packageName: String, val viewIds: List<String>) {
    YOUTUBE_SHORTS("YouTube Shorts", "com.google.android.youtube", listOf("reel_recycler", "reel_player_page_container", "reel_watch_player", "shorts_container")),
    INSTAGRAM_REELS("Instagram Reels", "com.instagram.android", listOf("clips_viewer_view_pager", "clips_viewer_container", "clips_video_container", "root_clips_layout")),
    FACEBOOK_REELS("Facebook Reels", "com.facebook.katana", listOf("reels_viewer_container", "reel_viewer_root")),
    SNAPCHAT_SPOTLIGHT("Snapchat Spotlight", "com.snapchat.android", listOf("spotlight_container", "spotlight_view_pager")),
    TIKTOK("TikTok feed", "com.zhiliaoapp.musically", listOf("viewpager", "feed_container"))
}

data class BedtimeConfig(
    val enabled: Boolean = false,
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 6 * 60 + 30,
    val days: Set<Int> = (1..7).toSet(),
    /** Apps still usable at bedtime (alarm, phone, messages…). Launcher and system are always allowed. */
    val allowedPackages: Set<String> = emptySet(),
    val blockApps: Boolean = true,
    val doNotDisturb: Boolean = true,
    val grayscale: Boolean = true
)

data class WellbeingConfig(
    /** Weekend daily limits (Sat/Sun) per package; weekdays use Focus Guard's daily limits. */
    val weekendLimits: Map<String, Int> = emptyMap(),
    val totalDailyLimitMinutes: Int = 0,
    val groupLimits: List<GroupLimit> = emptyList(),
    val openLimits: Map<String, Int> = emptyMap(),
    val sessionLimits: Map<String, SessionLimit> = emptyMap(),
    val warnMinutesBefore: Int = 5,
    /** Mindful pause grows by this many seconds per open today (ScreenZen). 0 = fixed pause. */
    val progressiveDelayStep: Int = 3,
    val blockedShortForm: Set<ShortForm> = emptySet(),
    val bedtime: BedtimeConfig = BedtimeConfig(),
    val doNotDisturbDuringFocus: Boolean = false,
    /** Strict mode: while blocking is active, Chronora's settings, uninstall and accessibility pages are covered. */
    val strictMode: Boolean = false,
    val screenTimeGoalMinutes: Int = 180,
    val pickupGoal: Int = 60,
    /** Notifications from these apps are silenced and collected into a digest. */
    val quietApps: Set<String> = emptySet(),
    val quietOnlyDuringFocus: Boolean = false,
    /** Minutes of the day when the digest is delivered. */
    val digestTimes: List<Int> = listOf(12 * 60 + 30, 18 * 60, 21 * 60),
    val dailyReport: Boolean = true,
    val reportMinute: Int = 21 * 60 + 30
)

/** What the blocker knows about current usage when it decides. */
data class UsageSnapshot(
    val todayMinutes: Map<String, Int> = emptyMap(),
    val opensToday: Map<String, Int> = emptyMap(),
    val totalTodayMinutes: Int = 0,
    val currentSessionMinutes: Int = 0,
    val cooldownUntil: Long = 0
)

sealed class WellbeingDecision {
    object Allow : WellbeingDecision()
    data class Block(val reason: String, val sessionCooldown: Boolean = false) : WellbeingDecision()
    data class Warn(val minutesLeft: Int, val what: String) : WellbeingDecision()
}

object WellbeingEngine {
    fun isWeekend(day: LocalDate) = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY

    fun dailyLimitFor(pkg: String, weekdayLimits: Map<String, Int>, config: WellbeingConfig, day: LocalDate): Int? =
        if (isWeekend(day)) config.weekendLimits[pkg] ?: weekdayLimits[pkg] else weekdayLimits[pkg]

    fun inWindow(minute: Int, day: Int, start: Int, end: Int, days: Set<Int>): Boolean {
        if (start == end) return false
        return if (start < end) day in days && minute in start until end
        else (day in days && minute >= start) || ((if (day == 1) 7 else day - 1) in days && minute < end)
    }

    fun bedtimeActive(b: BedtimeConfig, now: LocalDateTime) =
        b.enabled && inWindow(now.hour * 60 + now.minute, now.dayOfWeek.value, b.startMinute, b.endMinute, b.days)

    /**
     * Limits beyond Focus Guard's schedules: weekday/weekend app limits, group limits, a total
     * screen-time cap, open counts, session limits with cooldown and bedtime. Returns the first
     * limit hit, or a warning when one is close.
     */
    fun decide(
        config: WellbeingConfig,
        weekdayLimits: Map<String, Int>,
        pkg: String,
        now: LocalDateTime,
        nowMillis: Long,
        usage: UsageSnapshot
    ): WellbeingDecision {
        val b = config.bedtime
        if (bedtimeActive(b, now) && b.blockApps && pkg !in b.allowedPackages) {
            return WellbeingDecision.Block("Bedtime until %02d:%02d".format(b.endMinute / 60, b.endMinute % 60))
        }
        if (usage.cooldownUntil > nowMillis) {
            val mins = ((usage.cooldownUntil - nowMillis + 59_999) / 60_000).toInt()
            return WellbeingDecision.Block("Session break — back in $mins min", sessionCooldown = true)
        }
        val used = usage.todayMinutes[pkg] ?: 0
        val warnings = mutableListOf<WellbeingDecision.Warn>()

        dailyLimitFor(pkg, weekdayLimits, config, now.toLocalDate())?.let { limit ->
            if (used >= limit) return WellbeingDecision.Block("App timer: ${limit}m today used up")
            warnings += WellbeingDecision.Warn(limit - used, "app timer")
        }
        config.groupLimits.filter { pkg in it.packages }.forEach { g ->
            val groupUsed = g.packages.sumOf { usage.todayMinutes[it] ?: 0 }
            if (groupUsed >= g.minutes) return WellbeingDecision.Block("${g.name} limit: ${g.minutes}m today used up")
            warnings += WellbeingDecision.Warn(g.minutes - groupUsed, g.name)
        }
        if (config.totalDailyLimitMinutes > 0) {
            if (usage.totalTodayMinutes >= config.totalDailyLimitMinutes) return WellbeingDecision.Block("Daily screen time of ${config.totalDailyLimitMinutes / 60}h ${config.totalDailyLimitMinutes % 60}m reached")
            warnings += WellbeingDecision.Warn(config.totalDailyLimitMinutes - usage.totalTodayMinutes, "total screen time")
        }
        config.openLimits[pkg]?.let { max ->
            if ((usage.opensToday[pkg] ?: 0) > max) return WellbeingDecision.Block("Opened ${max}× today — that's the limit")
        }
        config.sessionLimits[pkg]?.let { s ->
            if (usage.currentSessionMinutes >= s.maxMinutes) return WellbeingDecision.Block("${s.maxMinutes}-minute session over — take a ${s.cooldownMinutes}-minute break", sessionCooldown = true)
            warnings += WellbeingDecision.Warn(s.maxMinutes - usage.currentSessionMinutes, "session")
        }
        val soonest = warnings.minByOrNull { it.minutesLeft }
        return if (soonest != null && soonest.minutesLeft <= config.warnMinutesBefore) soonest else WellbeingDecision.Allow
    }

    /** Mindful-pause length that grows with each open today. */
    fun pauseSeconds(base: Int, step: Int, opensToday: Int) = (base + step * (opensToday - 1).coerceAtLeast(0)).coerceIn(3, 60)
}

// ---------------------------------------------------------------- usage statistics

enum class UsageEventType { APP_FOREGROUND, APP_BACKGROUND, UNLOCK, SCREEN_ON, SCREEN_OFF, NOTIFICATION }

data class UsageEventLite(val time: Long, val type: UsageEventType, val pkg: String = "")

data class AppUsage(val pkg: String, val minutes: Int, val opens: Int, val notifications: Int)

data class DayUsage(
    val totalMinutes: Int,
    val pickups: Int,
    val notifications: Int,
    val opens: Int,
    val firstPickup: Long?,
    val hourlyMinutes: IntArray,
    val apps: List<AppUsage>,
    val longestSessionMinutes: Int
)

object UsageStatsCalculator {
    /**
     * Turns raw usage events into a day's summary. Foreground time is clipped to [start, end) and an
     * app still open at the end of the range is counted up to [end].
     */
    fun summarize(events: List<UsageEventLite>, start: Long, end: Long, ignore: Set<String> = emptySet()): DayUsage {
        val sorted = events.sortedBy { it.time }
        val openSince = HashMap<String, Long>()
        val minutesMs = HashMap<String, Long>()
        val opens = HashMap<String, Int>()
        val notifs = HashMap<String, Int>()
        val hourly = LongArray(24)
        var pickups = 0
        var firstPickup: Long? = null
        var longest = 0L
        var lastForeground: String? = null

        fun close(pkg: String, at: Long) {
            val from = openSince.remove(pkg) ?: return
            val s = maxOf(from, start)
            val e = minOf(at, end)
            if (e <= s) return
            minutesMs[pkg] = (minutesMs[pkg] ?: 0) + (e - s)
            longest = maxOf(longest, e - s)
            // spread across hour buckets
            var cursor = s
            while (cursor < e) {
                val hourIndex = (((cursor - start) / 3_600_000L).toInt()).coerceIn(0, 23)
                val bucketEnd = minOf(e, start + (hourIndex + 1) * 3_600_000L)
                hourly[hourIndex] += bucketEnd - cursor
                cursor = if (bucketEnd > cursor) bucketEnd else e
            }
        }

        for (ev in sorted) {
            if (ev.pkg in ignore && ev.type != UsageEventType.UNLOCK) continue
            when (ev.type) {
                UsageEventType.APP_FOREGROUND -> {
                    // Another app coming forward implies the previous one left.
                    openSince.keys.filter { it != ev.pkg }.forEach { close(it, ev.time) }
                    if (ev.pkg !in openSince) openSince[ev.pkg] = ev.time
                    if (ev.time in start until end && ev.pkg != lastForeground) opens[ev.pkg] = (opens[ev.pkg] ?: 0) + 1
                    lastForeground = ev.pkg
                }
                UsageEventType.APP_BACKGROUND -> close(ev.pkg, ev.time)
                UsageEventType.SCREEN_OFF -> { openSince.keys.toList().forEach { close(it, ev.time) }; lastForeground = null }
                UsageEventType.UNLOCK -> if (ev.time in start until end) {
                    pickups++
                    if (firstPickup == null) firstPickup = ev.time
                }
                UsageEventType.NOTIFICATION -> if (ev.time in start until end) notifs[ev.pkg] = (notifs[ev.pkg] ?: 0) + 1
                UsageEventType.SCREEN_ON -> Unit
            }
        }
        openSince.keys.toList().forEach { close(it, end) }

        val pkgs = minutesMs.keys + opens.keys + notifs.keys
        val apps = pkgs.map { AppUsage(it, ((minutesMs[it] ?: 0) / 60_000L).toInt(), opens[it] ?: 0, notifs[it] ?: 0) }
            .filter { it.minutes > 0 || it.opens > 0 || it.notifications > 0 }
            .sortedWith(compareByDescending<AppUsage> { it.minutes }.thenByDescending { it.opens })
        return DayUsage(
            totalMinutes = (minutesMs.values.sum() / 60_000L).toInt(),
            pickups = pickups,
            notifications = notifs.values.sum(),
            opens = opens.values.sum(),
            firstPickup = firstPickup,
            hourlyMinutes = IntArray(24) { (hourly[it] / 60_000L).toInt() },
            apps = apps,
            longestSessionMinutes = (longest / 60_000L).toInt()
        )
    }

    /** Consecutive days (ending yesterday or today) at or under the goal. */
    fun goalStreak(dailyTotals: Map<LocalDate, Int>, goal: Int, today: LocalDate): Int {
        var streak = 0
        var d = if ((dailyTotals[today] ?: Int.MAX_VALUE) <= goal) today else today.minusDays(1)
        while (true) {
            val v = dailyTotals[d] ?: break
            if (v > goal) break
            streak++
            d = d.minusDays(1)
        }
        return streak
    }

    fun percentChange(current: Int, previous: Int): Int? = if (previous <= 0) null else ((current - previous) * 100.0 / previous).toInt()
}

// ---------------------------------------------------------------- notification digest

data class HeldNotification(val time: Long, val pkg: String, val appLabel: String, val title: String, val text: String)

object NotificationDigest {
    fun shouldHold(config: WellbeingConfig, pkg: String, focusOrBedtime: Boolean): Boolean =
        pkg in config.quietApps && (!config.quietOnlyDuringFocus || focusOrBedtime)

    /** One line per app, most active first: "WhatsApp (12): Mom, Team chat…". */
    fun summary(held: List<HeldNotification>): List<String> = held.groupBy { it.appLabel }
        .entries.sortedByDescending { it.value.size }
        .map { (app, items) -> "$app (${items.size}): " + items.map { it.title }.filter { it.isNotBlank() }.distinct().take(3).joinToString(", ") }

    /** Next delivery time (minute of day) after [minute], wrapping to tomorrow. */
    fun nextDigest(times: List<Int>, minute: Int): Pair<Int, Boolean>? {
        if (times.isEmpty()) return null
        val sorted = times.sorted()
        return sorted.firstOrNull { it > minute }?.let { it to false } ?: (sorted.first() to true)
    }
}
