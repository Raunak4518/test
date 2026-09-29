package com.raunak.daytimeline.pro

import java.time.LocalDate
import java.time.LocalDateTime

/** A recurring window in which the block list is enforced, e.g. study hours Mon–Fri 09:00–13:00. */
data class BlockSchedule(
    val id: Long = System.currentTimeMillis(),
    val name: String = "Focus hours",
    /** ISO day numbers, 1 = Monday … 7 = Sunday. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
    val enabled: Boolean = true
)

/**
 * Everything Chronora needs to block distracting apps — the features Freedom, Opal, one sec,
 * AppBlock and Forest keep behind a subscription, all stored locally.
 */
data class FocusGuardConfig(
    /** Apps blocked during sessions and schedules. */
    val blockedPackages: Set<String> = emptySet(),
    /** Optional named block lists; the active one is merged into [blockedPackages] by the UI. */
    val blockLists: Map<String, Set<String>> = emptyMap(),
    /** Allowlist mode blocks every launchable app except these while a session/schedule is active. */
    val allowlistMode: Boolean = false,
    val allowedPackages: Set<String> = emptySet(),
    val schedules: List<BlockSchedule> = emptyList(),
    /** Daily limits in minutes per package; enforced all day, independent of sessions. */
    val dailyLimits: Map<String, Int> = emptyMap(),
    /** one sec style: apps that need a breathing pause before opening, at any time. */
    val mindfulPackages: Set<String> = emptySet(),
    val interventionSeconds: Int = 8,
    /** Minutes an app stays open after the user deliberately passes an intervention. */
    val interventionGrantMinutes: Int = 5,
    /** A manual focus session end time (epoch millis). */
    val sessionUntil: Long = 0,
    /** Locked mode: no emergency unlock while a session/schedule is active. */
    val lockedMode: Boolean = false,
    val unlockDelaySeconds: Int = 30,
    val emergencyUnlocksPerDay: Int = 2,
    val emergencyUnlockMinutes: Int = 5,
    /** Websites blocked in supported browsers, e.g. "reddit.com" or "youtube.com/shorts". */
    val blockedSites: Set<String> = emptySet(),
    /** Block the websites all day instead of only during sessions and schedules. */
    val sitesAlwaysBlocked: Boolean = false,
    /** Quick-start session lengths (minutes). */
    val sessionPresets: List<Int> = listOf(25, 50, 90, 180),
    /** If set, an emergency unlock needs this exact sentence typed first (friction against impulse). */
    val unlockPhrase: String = ""
) {
    /** Fills fields missing from settings saved by an older version. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun normalized(): FocusGuardConfig {
        val d = FocusGuardConfig()
        return copy(
            blockedPackages = blockedPackages ?: emptySet(), blockLists = blockLists ?: emptyMap(), allowedPackages = allowedPackages ?: emptySet(),
            schedules = schedules ?: emptyList(), dailyLimits = dailyLimits ?: emptyMap(), mindfulPackages = mindfulPackages ?: emptySet(),
            blockedSites = blockedSites ?: emptySet(), unlockPhrase = unlockPhrase ?: "", sessionPresets = (sessionPresets ?: d.sessionPresets).filter { it > 0 }.ifEmpty { d.sessionPresets },
            interventionSeconds = if (interventionSeconds > 0) interventionSeconds else d.interventionSeconds,
            interventionGrantMinutes = if (interventionGrantMinutes > 0) interventionGrantMinutes else d.interventionGrantMinutes,
            emergencyUnlockMinutes = if (emergencyUnlockMinutes > 0) emergencyUnlockMinutes else d.emergencyUnlockMinutes
        )
    }
}

/** Volatile state updated by the accessibility service and the block screen. */
data class FocusGuardRuntime(
    val pausedUntil: Long = 0,
    val grants: Map<String, Long> = emptyMap(),
    val emergencyDate: String = "",
    val emergencyCount: Int = 0,
    val blockedToday: Map<String, Int> = emptyMap(),
    val blockedDate: String = ""
)

sealed class GuardDecision {
    object Allow : GuardDecision()
    data class Block(val reason: String, val canUnlock: Boolean, val unlockDelaySeconds: Int) : GuardDecision()
    data class Intervene(val seconds: Int) : GuardDecision()
}

object FocusGuardEngine {
    /** Packages that must never be blocked or the phone becomes unusable. */
    val alwaysAllowed = setOf(
        "com.raunak.daytimeline",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.emergency",
        "com.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller"
    )

    fun activeSchedule(config: FocusGuardConfig, now: LocalDateTime): BlockSchedule? {
        val minute = now.hour * 60 + now.minute
        val day = now.dayOfWeek.value
        return config.schedules.firstOrNull { s ->
            if (!s.enabled) return@firstOrNull false
            if (s.startMinute <= s.endMinute) {
                day in s.days && minute >= s.startMinute && minute < s.endMinute
            } else {
                // Overnight window, e.g. 22:00–06:00: the early part belongs to the previous day's schedule.
                val yesterday = if (day == 1) 7 else day - 1
                (day in s.days && minute >= s.startMinute) || (yesterday in s.days && minute < s.endMinute)
            }
        }
    }

    fun sessionActive(config: FocusGuardConfig, nowMillis: Long) = config.sessionUntil > nowMillis

    fun enforcing(config: FocusGuardConfig, now: LocalDateTime, nowMillis: Long) =
        sessionActive(config, nowMillis) || activeSchedule(config, now) != null

    fun emergencyRemaining(config: FocusGuardConfig, runtime: FocusGuardRuntime, today: LocalDate): Int {
        val used = if (runtime.emergencyDate == today.toString()) runtime.emergencyCount else 0
        return (config.emergencyUnlocksPerDay - used).coerceAtLeast(0)
    }

    fun decide(
        config: FocusGuardConfig,
        runtime: FocusGuardRuntime,
        packageName: String,
        now: LocalDateTime,
        nowMillis: Long,
        usedMinutesToday: Int = 0,
        extraAllowed: Set<String> = emptySet()
    ): GuardDecision {
        if (packageName in alwaysAllowed || packageName in extraAllowed) return GuardDecision.Allow
        if (runtime.pausedUntil > nowMillis) return GuardDecision.Allow

        val enforcing = enforcing(config, now, nowMillis)
        val canUnlock = !(config.lockedMode && enforcing) &&
            emergencyRemaining(config, runtime, now.toLocalDate()) > 0

        if (enforcing) {
            val blocked = if (config.allowlistMode) packageName !in config.allowedPackages
            else packageName in config.blockedPackages
            if (blocked) {
                val reason = if (sessionActive(config, nowMillis)) "Focus session in progress"
                else "Scheduled block: " + (activeSchedule(config, now)?.name ?: "focus hours")
                return GuardDecision.Block(reason, canUnlock, config.unlockDelaySeconds)
            }
        }

        val limit = config.dailyLimits[packageName]
        if (limit != null && usedMinutesToday >= limit) {
            return GuardDecision.Block("Daily limit of ${limit}m reached", canUnlock, config.unlockDelaySeconds)
        }

        if (packageName in config.mindfulPackages && (runtime.grants[packageName] ?: 0L) <= nowMillis) {
            return GuardDecision.Intervene(config.interventionSeconds.coerceIn(3, 60))
        }
        return GuardDecision.Allow
    }

    fun decideSite(config: FocusGuardConfig, runtime: FocusGuardRuntime, url: String, now: LocalDateTime, nowMillis: Long): GuardDecision {
        if (runtime.pausedUntil > nowMillis) return GuardDecision.Allow
        if (!config.sitesAlwaysBlocked && !enforcing(config, now, nowMillis)) return GuardDecision.Allow
        val rule = WebsiteRules.matches(url, config.blockedSites) ?: return GuardDecision.Allow
        val canUnlock = !(config.lockedMode && enforcing(config, now, nowMillis)) &&
            emergencyRemaining(config, runtime, now.toLocalDate()) > 0
        return GuardDecision.Block("Website blocked: $rule", canUnlock, config.unlockDelaySeconds)
    }

    fun grant(config: FocusGuardConfig, runtime: FocusGuardRuntime, packageName: String, nowMillis: Long) =
        runtime.copy(grants = runtime.grants.filterValues { it > nowMillis } + (packageName to nowMillis + config.interventionGrantMinutes * 60_000L))

    fun useEmergencyUnlock(config: FocusGuardConfig, runtime: FocusGuardRuntime, today: LocalDate, nowMillis: Long): FocusGuardRuntime? {
        if (emergencyRemaining(config, runtime, today) <= 0) return null
        val count = if (runtime.emergencyDate == today.toString()) runtime.emergencyCount + 1 else 1
        return runtime.copy(pausedUntil = nowMillis + config.emergencyUnlockMinutes * 60_000L, emergencyDate = today.toString(), emergencyCount = count)
    }

    fun recordBlocked(runtime: FocusGuardRuntime, packageName: String, today: LocalDate): FocusGuardRuntime {
        val base = if (runtime.blockedDate == today.toString()) runtime.blockedToday else emptyMap()
        return runtime.copy(blockedDate = today.toString(), blockedToday = base + (packageName to (base[packageName] ?: 0) + 1))
    }
}
