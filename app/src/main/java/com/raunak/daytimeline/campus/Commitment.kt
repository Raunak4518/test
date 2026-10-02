package com.raunak.daytimeline.campus

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A lock you set once and can't undo until its end date: protections can only be tightened or extended,
 * never loosened or shortened. Kept with the private Discipline data (never backed up).
 */
data class CommitLock(
    val until: Long = 0,
    val started: Long = 0,
    /** Words from you, shown every time something is blocked. */
    val letter: String = "",
    /** Cover the screen when explicit words show up in any app (two different words needed, to avoid false alarms). */
    val contentShield: Boolean = true,
    /** Close private / incognito tabs. */
    val noPrivateTabs: Boolean = true,
    /** Only one browser may open; others could skip the web filter. */
    val oneBrowser: Boolean = true,
    val browser: String = "com.android.chrome",
    /** Block every short-video feed (Reels, Shorts, Spotlight…). */
    val noShortVideos: Boolean = true,
    /** At night only the phone, clock and the apps below work. */
    val nightShield: Boolean = true,
    val nightStart: Int = 23 * 60,
    val nightEnd: Int = 6 * 60,
    /** Night starts sleep-goal before the next alarm instead of at a fixed time. */
    val nightFromAlarm: Boolean = true,
    val nightAllowed: Set<String> = emptySet(),
    /** Keep the web filter running: restart it if it stops. */
    val keepFilterOn: Boolean = true
) {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun normalized() = copy(letter = letter ?: "", browser = browser ?: "com.android.chrome", nightAllowed = nightAllowed ?: emptySet())
}

object Commitment {
    fun active(c: CommitLock, now: Long = System.currentTimeMillis()) = c.until > now

    fun daysLeft(c: CommitLock, now: Long = System.currentTimeMillis()) = if (!active(c, now)) 0 else ((c.until - now + 86_399_999L) / 86_400_000L).toInt()

    /** Starting or extending can only push the end later; switching a shield on is always allowed, off never. */
    fun tighten(old: CommitLock, next: CommitLock, now: Long = System.currentTimeMillis()): CommitLock {
        if (!active(old, now)) return next
        return next.copy(
            until = maxOf(old.until, next.until), started = old.started,
            contentShield = old.contentShield || next.contentShield, noPrivateTabs = old.noPrivateTabs || next.noPrivateTabs,
            oneBrowser = old.oneBrowser || next.oneBrowser, browser = if (old.oneBrowser) old.browser else next.browser,
            noShortVideos = old.noShortVideos || next.noShortVideos, nightShield = old.nightShield || next.nightShield,
            keepFilterOn = old.keepFilterOn || next.keepFilterOn,
            // Night hours may only grow and the allowed list only shrink.
            nightAllowed = if (old.nightShield) old.nightAllowed.intersect(next.nightAllowed) else next.nightAllowed,
            nightStart = old.nightStart, nightEnd = old.nightEnd, nightFromAlarm = old.nightFromAlarm
        )
    }

    /** Night window in minutes of the day; with [alarmMinute] it starts [sleepGoal] before the alarm. */
    fun nightWindow(c: CommitLock, alarmMinute: Int?, sleepGoal: Int): Pair<Int, Int> =
        if (c.nightFromAlarm && alarmMinute != null) Math.floorMod(alarmMinute - sleepGoal, 24 * 60) to alarmMinute else c.nightStart to c.nightEnd

    fun inWindow(window: Pair<Int, Int>, minute: Int): Boolean {
        val (s, e) = window
        return if (s <= e) minute in s until e else minute >= s || minute < e
    }

    private val wordCache = HashMap<String, Regex>()

    /** How many different listed words appear as whole words in the on-screen text. */
    fun explicitHits(texts: List<String>, words: List<String>): Int {
        if (texts.isEmpty()) return 0
        val blob = texts.joinToString(" ").lowercase()
        return words.count { w -> w.length >= 3 && wordCache.getOrPut(w) { Regex("(^|[^a-z0-9])" + Regex.escape(w) + "s?([^a-z0-9]|$)") }.containsMatchIn(blob) }
    }

    /** Words that mark a private/incognito browser tab. */
    val privateMarkers = listOf("Incognito", "InPrivate", "Private browsing", "Private tab", "New private tab", "You've gone incognito")

    fun dateText(ms: Long): String = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let { "%d %s".format(it.dayOfMonth, it.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }) }
}
