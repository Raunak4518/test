package com.raunak.daytimeline.pro

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Watches which app is in the foreground and shows [BlockActivity] when Focus Guard rules say so.
 * Nothing leaves the device; the service only reads package names of window changes.
 */
class FocusGuardService : AccessibilityService() {
    private lateinit var store: FocusGuardStore
    private var launchers: Set<String> = emptySet()
    private var lastPackage = ""
    private var lastShownAt = 0L
    private val usageCache = mutableMapOf<String, Pair<Long, Int>>()
    private var lastSiteCheck = 0L
    private var lastBlockedUrl = ""

    override fun onServiceConnected() {
        store = FocusGuardStore(this)
        launchers = homePackages(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in browserUrlBars) checkBrowser(pkg)
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (pkg == packageName) return
        val nowMillis = System.currentTimeMillis()
        if (pkg == lastPackage && nowMillis - lastShownAt < 1500) return

        val config = store.config
        val focusBlocking = FocusSoundPrefs(this).blockDuringFocus && PomodoroFocusFlag.isFocusRunning(this, nowMillis)
        val effective = if (focusBlocking && !FocusGuardEngine.sessionActive(config, nowMillis)) config.copy(sessionUntil = nowMillis + 1) else config
        val decision = FocusGuardEngine.decide(
            effective, store.runtime, pkg, LocalDateTime.now(), nowMillis,
            usedMinutesToday = if (pkg in config.dailyLimits) usedMinutes(pkg, nowMillis) else 0,
            extraAllowed = launchers + inputMethods()
        )
        when (decision) {
            GuardDecision.Allow -> Unit
            is GuardDecision.Block -> {
                store.runtime = FocusGuardEngine.recordBlocked(store.runtime, pkg, LocalDate.now())
                show(pkg, BlockActivity.MODE_BLOCK, decision.reason, decision.canUnlock, decision.unlockDelaySeconds)
            }
            is GuardDecision.Intervene -> show(pkg, BlockActivity.MODE_INTERVENE, "Take a breath first", true, decision.seconds)
        }
    }

    /** Reads the address bar of supported browsers and covers blocked websites. */
    private fun checkBrowser(pkg: String) {
        val config = store.config
        if (config.blockedSites.isEmpty()) return
        val nowMillis = System.currentTimeMillis()
        if (nowMillis - lastSiteCheck < 700) return
        lastSiteCheck = nowMillis
        val root = rootInActiveWindow ?: return
        val url = browserUrlBars.getValue(pkg).firstNotNullOfOrNull { id ->
            runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }.getOrNull()?.firstOrNull()?.text?.toString()
        } ?: return
        if (url == lastBlockedUrl && nowMillis - lastShownAt < 3000) return
        val decision = FocusGuardEngine.decideSite(config, store.runtime, url, LocalDateTime.now(), nowMillis)
        if (decision is GuardDecision.Block) {
            lastBlockedUrl = url
            store.runtime = FocusGuardEngine.recordBlocked(store.runtime, WebsiteRules.hostOf(url), LocalDate.now())
            show(pkg, BlockActivity.MODE_BLOCK, decision.reason, decision.canUnlock, decision.unlockDelaySeconds, reopenAfterUnlock = false)
        }
    }

    private fun show(pkg: String, mode: String, reason: String, canUnlock: Boolean, seconds: Int, reopenAfterUnlock: Boolean = true) {
        lastPackage = pkg
        lastShownAt = System.currentTimeMillis()
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                .putExtra(BlockActivity.EXTRA_MODE, mode)
                .putExtra(BlockActivity.EXTRA_REASON, reason)
                .putExtra(BlockActivity.EXTRA_CAN_UNLOCK, canUnlock)
                .putExtra(BlockActivity.EXTRA_SECONDS, seconds)
                .putExtra(BlockActivity.EXTRA_REOPEN, reopenAfterUnlock)
        )
    }

    private fun usedMinutes(pkg: String, nowMillis: Long): Int {
        usageCache[pkg]?.let { (at, minutes) -> if (nowMillis - at < 30_000) return minutes }
        val minutes = UsageAccess.todayMinutes(this, pkg)
        usageCache[pkg] = nowMillis to minutes
        return minutes
    }

    private fun inputMethods(): Set<String> =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let { ComponentName.unflattenFromString(it)?.packageName }?.let { setOf(it) } ?: emptySet()

    override fun onInterrupt() = Unit

    companion object {
        /** Address-bar view ids of popular browsers. */
        val browserUrlBars = mapOf(
            "com.android.chrome" to listOf("url_bar"),
            "com.chrome.beta" to listOf("url_bar"),
            "com.brave.browser" to listOf("url_bar"),
            "com.microsoft.emmx" to listOf("url_bar"),
            "com.vivaldi.browser" to listOf("url_bar"),
            "com.opera.browser" to listOf("url_field"),
            "com.opera.mini.native" to listOf("url_field"),
            "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            "org.mozilla.focus" to listOf("display_url", "mozac_browser_toolbar_url_view"),
            "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text", "custom_tab_toolbar_url_bar_text"),
            "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
            "com.kiwibrowser.browser" to listOf("url_bar")
        )

        fun homePackages(context: Context): Set<String> =
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .map { it.activityInfo.packageName }.toSet()

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, FocusGuardService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}

object UsageAccess {
    fun granted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        }.getOrNull()
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground minutes per package since local midnight. */
    fun today(context: Context): Map<String, Int> {
        if (!granted(context)) return emptyMap()
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return runCatching {
            manager.queryAndAggregateUsageStats(start, System.currentTimeMillis())
                .mapValues { (it.value.totalTimeInForeground / 60_000L).toInt() }
                .filterValues { it > 0 }
        }.getOrDefault(emptyMap())
    }

    fun todayMinutes(context: Context, pkg: String): Int = today(context)[pkg] ?: 0
}
