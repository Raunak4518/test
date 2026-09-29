package com.raunak.daytimeline.pro

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.raunak.daytimeline.wellbeing.UsageRepository
import com.raunak.daytimeline.wellbeing.UsageSnapshot
import com.raunak.daytimeline.wellbeing.WellbeingAlarmReceiver
import com.raunak.daytimeline.wellbeing.WellbeingDecision
import com.raunak.daytimeline.wellbeing.WellbeingEngine
import com.raunak.daytimeline.wellbeing.WellbeingModes
import com.raunak.daytimeline.wellbeing.WellbeingStore
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
    private var lastSiteCheck = 0L
    private var lastBlockedUrl = ""

    override fun onServiceConnected() {
        store = FocusGuardStore(this)
        wellbeing = WellbeingStore(this)
        launchers = homePackages(this)
        handler.post(ticker)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wellbeing: WellbeingStore
    private var foreground: String? = null
    private var sessionPkg: String? = null
    private var sessionStart = 0L
    private var lastLeftAt = 0L
    private var lastShortCheck = 0L
    private var lastStrictCheck = 0L
    private var lastToastAt = 0L
    private var totalsCache: Pair<Long, Pair<Map<String, Int>, Int>>? = null

    /** Re-checks limits every 20 s so an app is stopped when its time runs out, not only when reopened. */
    private val ticker = object : Runnable {
        override fun run() {
            runCatching { tick() }
            handler.postDelayed(this, 20_000)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in browserUrlBars) checkBrowser(pkg)
        if (strictGuard(pkg)) return
        checkShortForm(pkg)
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        onForeground(pkg)
    }

    private fun isNeutral(pkg: String) = pkg == "com.android.systemui" || pkg in inputMethods()

    private fun onForeground(pkg: String) {
        if (isNeutral(pkg)) return
        val nowMillis = System.currentTimeMillis()
        if (pkg == packageName || pkg in launchers) {
            if (foreground != null && foreground != pkg) lastLeftAt = nowMillis
            foreground = pkg
            return
        }
        val isNewForeground = pkg != foreground
        if (isNewForeground) {
            if (pkg != sessionPkg || nowMillis - lastLeftAt > 60_000) { sessionPkg = pkg; sessionStart = nowMillis }
            if (pkg !in FocusGuardEngine.alwaysAllowed) wellbeing.recordOpen(pkg)
            foreground = pkg
        }
        if (pkg == lastPackage && nowMillis - lastShownAt < 1500) return
        evaluate(pkg, fromOpen = isNewForeground)
    }

    private fun evaluate(pkg: String, fromOpen: Boolean) {
        val nowMillis = System.currentTimeMillis()
        val now = LocalDateTime.now()
        val config = store.config
        val focusBlocking = FocusSoundPrefs(this).blockDuringFocus && PomodoroFocusFlag.isFocusRunning(this, nowMillis)
        // Daily app timers are handled below so weekend limits can differ from weekdays.
        val effective = config.copy(dailyLimits = emptyMap()).let { if (focusBlocking && !FocusGuardEngine.sessionActive(it, nowMillis)) it.copy(sessionUntil = nowMillis + 1) else it }
        val decision = FocusGuardEngine.decide(effective, store.runtime, pkg, now, nowMillis, extraAllowed = launchers + inputMethods())
        when (decision) {
            is GuardDecision.Block -> {
                store.runtime = FocusGuardEngine.recordBlocked(store.runtime, pkg, LocalDate.now())
                show(pkg, BlockActivity.MODE_BLOCK, decision.reason, decision.canUnlock, decision.unlockDelaySeconds)
                return
            }
            is GuardDecision.Intervene -> if (fromOpen) {
                val opens = wellbeing.opensToday()[pkg] ?: 1
                show(pkg, BlockActivity.MODE_INTERVENE, "Take a breath first · opened ${opens}× today", true, WellbeingEngine.pauseSeconds(decision.seconds, wellbeing.config.progressiveDelayStep, opens))
                return
            }
            GuardDecision.Allow -> Unit
        }
        if (pkg in FocusGuardEngine.alwaysAllowed || pkg in launchers) return

        val wb = wellbeing.config
        val needsUsage = wb.totalDailyLimitMinutes > 0 || wb.groupLimits.isNotEmpty() || pkg in config.dailyLimits || pkg in wb.weekendLimits
        val (perApp, total) = if (needsUsage) todayTotals(nowMillis) else emptyMap<String, Int>() to 0
        val snapshot = UsageSnapshot(
            todayMinutes = perApp,
            opensToday = wellbeing.opensToday(),
            totalTodayMinutes = total,
            currentSessionMinutes = if (sessionPkg == pkg) ((nowMillis - sessionStart) / 60_000L).toInt() else 0,
            cooldownUntil = wellbeing.cooldowns()[pkg] ?: 0
        )
        when (val w = WellbeingEngine.decide(wb, config.dailyLimits, pkg, now, nowMillis, snapshot)) {
            is WellbeingDecision.Block -> {
                if (w.sessionCooldown && snapshot.cooldownUntil <= nowMillis) {
                    wb.sessionLimits[pkg]?.let { wellbeing.setCooldown(pkg, nowMillis + it.cooldownMinutes * 60_000L) }
                    sessionPkg = null
                }
                store.runtime = FocusGuardEngine.recordBlocked(store.runtime, pkg, LocalDate.now())
                val canUnlock = !config.lockedMode && FocusGuardEngine.emergencyRemaining(config, store.runtime, LocalDate.now()) > 0
                show(pkg, BlockActivity.MODE_BLOCK, w.reason, canUnlock, config.unlockDelaySeconds)
            }
            is WellbeingDecision.Warn -> if (wellbeing.warnOnce("$pkg|${w.what}|${if (w.minutesLeft <= 1) 1 else 5}")) {
                WellbeingAlarmReceiver.warn(this, "${w.minutesLeft} min left", "${WellbeingAlarmReceiver.label(this, pkg)} · ${w.what}")
            }
            WellbeingDecision.Allow -> Unit
        }
    }

    private fun todayTotals(nowMillis: Long): Pair<Map<String, Int>, Int> {
        totalsCache?.let { (at, v) -> if (nowMillis - at < 30_000) return v }
        val v = UsageRepository.todayTotals(this)
        totalsCache = nowMillis to v
        return v
    }

    private fun tick() {
        WellbeingModes.apply(this)
        val power = getSystemService(PowerManager::class.java)
        if (power?.isInteractive != true) {
            if (foreground != null) lastLeftAt = System.currentTimeMillis()
            foreground = null
            return
        }
        if (foreground == null) rootInActiveWindow?.packageName?.toString()?.let { onForeground(it); return }
        val fg = foreground ?: return
        if (fg == packageName || fg in launchers || System.currentTimeMillis() - lastShownAt < 5_000) return
        evaluate(fg, fromOpen = false)
    }

    /** Backs out of blocked short-video screens (Shorts, Reels, Spotlight) while the rest of the app works. */
    private fun checkShortForm(pkg: String) {
        val blocked = wellbeing.config.blockedShortForm.filter { it.packageName == pkg }
        if (blocked.isEmpty()) return
        val nowMillis = System.currentTimeMillis()
        if (nowMillis - lastShortCheck < 600) return
        lastShortCheck = nowMillis
        val root = rootInActiveWindow ?: return
        val hit = blocked.firstOrNull { sf -> sf.viewIds.any { id -> runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }.getOrNull()?.any { it.isVisibleToUser } == true } }
            ?: return
        performGlobalAction(GLOBAL_ACTION_BACK)
        toast("${hit.label} is blocked")
    }

    /** Strict mode: while blocks are active, cover pages that could uninstall or disable Chronora. */
    private fun strictGuard(pkg: String): Boolean {
        if (pkg !in strictPackages || !wellbeing.config.strictMode) return false
        val nowMillis = System.currentTimeMillis()
        if (nowMillis - lastStrictCheck < 500) return false
        lastStrictCheck = nowMillis
        val now = LocalDateTime.now()
        val active = FocusGuardEngine.enforcing(store.config, now, nowMillis) || PomodoroFocusFlag.isFocusRunning(this, nowMillis) ||
            WellbeingEngine.bedtimeActive(wellbeing.config.bedtime, now) || wellbeing.cooldowns().values.any { it > nowMillis }
        if (!active) return false
        val root = rootInActiveWindow ?: return false
        val mentionsUs = runCatching { root.findAccessibilityNodeInfosByText(getString(com.raunak.daytimeline.R.string.app_name)) }.getOrNull()?.isNotEmpty() == true
        if (!mentionsUs) return false
        performGlobalAction(GLOBAL_ACTION_HOME)
        toast("Strict mode: Chronora can't be changed during a block")
        return true
    }

    private fun toast(text: String) {
        val nowMillis = System.currentTimeMillis()
        if (nowMillis - lastToastAt < 4000) return
        lastToastAt = nowMillis
        handler.post { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
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

    private fun inputMethods(): Set<String> =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let { ComponentName.unflattenFromString(it)?.packageName }?.let { setOf(it) } ?: emptySet()

    override fun onInterrupt() = Unit

    companion object {
        private val strictPackages = setOf(
            "com.android.settings", "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.samsung.android.lool", "com.miui.securitycenter"
        )

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
