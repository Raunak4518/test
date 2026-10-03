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
        runCatching { registerReceiver(unlockReceiver, android.content.IntentFilter(Intent.ACTION_USER_PRESENT)) }
    }

    /** Counts real unlocks (keyguard dismissed) for the daily unlock limit and pickup stats. */
    private val unlockReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) { if (::wellbeing.isInitialized) wellbeing.recordUnlock() }
    }

    override fun onDestroy() {
        hideBubble()
        usageExecutor.shutdownNow()
        runCatching { unregisterReceiver(unlockReceiver) }
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
    @Volatile private var totalsCache: Pair<Long, Pair<Map<String, Int>, Int>>? = null

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
        if (commitGuard(pkg)) return
        if (strictGuard(pkg)) return
        checkShortForm(pkg)
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        onForeground(pkg)
    }

    private fun isNeutral(pkg: String) = pkg == "com.android.systemui" || pkg in inputMethods()

    private fun onForeground(pkg: String) {
        if (isNeutral(pkg)) return
        val nowMillis = System.currentTimeMillis()
        runCatching { updateBubble(pkg) }
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
        val pomodoroRunning = PomodoroFocusFlag.isFocusRunning(this, nowMillis)
        val focusBlocking = FocusSoundPrefs(this).blockDuringFocus && pomodoroRunning
        val focusCfg = FocusPrefs(this).config
        // Deep focus: during a focus session only the allowlist (plus phone, launcher, Chronora) may open.
        val deepFocus = focusCfg.strict && pomodoroRunning
        // Daily app timers are handled below so weekend limits can differ from weekdays.
        val effective = config.copy(dailyLimits = emptyMap())
            .let { if ((focusBlocking || deepFocus) && !FocusGuardEngine.sessionActive(it, nowMillis)) it.copy(sessionUntil = nowMillis + 1) else it }
            .let { if (deepFocus) it.copy(allowlistMode = true, allowedPackages = it.allowedPackages + focusCfg.strictAllowed, lockedMode = true) else it }
        lockDecision(pkg, now)?.let { reason ->
            store.runtime = FocusGuardEngine.recordBlocked(store.runtime, pkg, LocalDate.now())
            logShield()
            show(pkg, BlockActivity.MODE_BLOCK, reason, false, 0, note = commit().letter)
            return
        }
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
            cooldownUntil = wellbeing.cooldowns()[pkg] ?: 0,
            unlocksToday = wellbeing.unlocksToday()
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

    // ------------------------------------------------------------------ commitment lock

    private val discipline by lazy { com.raunak.daytimeline.campus.DisciplineStore.get(this) }
    private fun commit() = discipline.state.value.commit
    private fun committed() = com.raunak.daytimeline.campus.Commitment.active(commit())
    private var lastPrivateCheck = 0L
    private var lastContentCheck = 0L
    private var wordsCache: Pair<Long, List<String>>? = null
    private var browsersCache: Set<String>? = null
    private var alarmCache: Pair<Long, Int?>? = null

    private fun words(): List<String> {
        val nowMillis = System.currentTimeMillis()
        wordsCache?.let { (at, w) -> if (nowMillis - at < 300_000) return w }
        val w = runCatching { com.raunak.daytimeline.filter.WebFilterStore(this).effectiveKeywords() }.getOrDefault(emptyList())
        wordsCache = nowMillis to w
        return w
    }

    private fun browsers(): Set<String> = browsersCache ?: runCatching {
        packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com")), android.content.pm.PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }.toSet() + browserUrlBars.keys
    }.getOrDefault(browserUrlBars.keys).also { browsersCache = it }

    /** Minute of the next enabled alarm (cached for 10 minutes), so the night ends when you wake. */
    private fun nextAlarmMinute(): Int? {
        val nowMillis = System.currentTimeMillis()
        alarmCache?.let { (at, m) -> if (nowMillis - at < 600_000) return m }
        val m = runCatching {
            com.raunak.daytimeline.alarm.AlarmPersistentStore(this).all().filter { it.enabled && !it.label.startsWith("Nap") }
                .map { it to com.raunak.daytimeline.alarm.AlarmSchedulePlanner.nextOccurrence(it, LocalDateTime.now()) }
                .filter { it.second != Long.MAX_VALUE && it.second - nowMillis < 24 * 3_600_000L }
                .minByOrNull { it.second }?.first?.let { it.hour * 60 + it.minute }
        }.getOrNull()
        alarmCache = nowMillis to m
        return m
    }

    private fun logShield() {
        val day = LocalDate.now().toString()
        discipline.update { it.copy(shieldLog = (it.shieldLog + (day to (it.shieldLog[day] ?: 0) + 1)).toList().takeLast(120).toMap()) }
    }

    /** Night shield and one-browser rule: returns why [pkg] is blocked, or null. */
    private fun lockDecision(pkg: String, now: LocalDateTime): String? {
        if (!committed()) return null
        val c = commit()
        if (pkg == packageName || pkg in launchers || pkg in FocusGuardEngine.alwaysAllowed || pkg in inputMethods() || pkg in clockApps) return null
        if (c.nightShield) {
            val sleepGoal = runCatching { com.raunak.daytimeline.campus.CampusStore.get(this).data.value.settings.sleepGoalMinutes }.getOrDefault(450)
            val window = com.raunak.daytimeline.campus.Commitment.nightWindow(c, nextAlarmMinute(), sleepGoal)
            if (com.raunak.daytimeline.campus.Commitment.inWindow(window, now.hour * 60 + now.minute) && pkg !in c.nightAllowed)
                return "Night shield · sleep until %02d:%02d".format(window.second / 60, window.second % 60)
        }
        if (pkg in c.blockedApps) return "Blocked until " + com.raunak.daytimeline.campus.Commitment.dateText(c.until)
        if (c.oneBrowser && pkg in browsers() && pkg != c.browser) return "Only one browser is allowed during your commitment"
        return null
    }

    /** Private tabs and on-screen explicit words while the lock is on. Returns true when it acted. */
    private fun commitGuard(pkg: String): Boolean {
        if (!committed() || pkg == packageName || pkg in launchers || isNeutral(pkg)) return false
        val c = commit()
        val nowMillis = System.currentTimeMillis()
        val root = rootInActiveWindow ?: return false
        // Settings pages that could undo the lock (DNS, clock, developer options, reset) close at once.
        if (pkg in strictPackages && nowMillis - lastPrivateCheck > 500) {
            lastPrivateCheck = nowMillis
            val hit = com.raunak.daytimeline.campus.Commitment.settingsMarkers.any { m -> runCatching { root.findAccessibilityNodeInfosByText(m) }.getOrNull()?.any { it.isVisibleToUser } == true }
            if (hit) { performGlobalAction(GLOBAL_ACTION_HOME); logShield(); toast("Locked until " + com.raunak.daytimeline.campus.Commitment.dateText(c.until)); return true }
        }
        if (pkg in FocusGuardEngine.alwaysAllowed) return false
        if (c.noPrivateTabs && pkg in browsers() && nowMillis - lastPrivateCheck > 800) {
            lastPrivateCheck = nowMillis
            val hit = com.raunak.daytimeline.campus.Commitment.privateMarkers.any { m -> runCatching { root.findAccessibilityNodeInfosByText(m) }.getOrNull()?.any { it.isVisibleToUser } == true }
            if (hit) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
                logShield()
                toast("Private tabs are off")
                return true
            }
        }
        if (c.contentShield && nowMillis - lastContentCheck > 1200) {
            lastContentCheck = nowMillis
            val texts = ArrayList<String>()
            val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            queue.add(root)
            var seen = 0
            while (queue.isNotEmpty() && seen < 500) {
                val n = queue.removeFirst(); seen++
                if (n.isVisibleToUser) { n.text?.let { texts += it.toString() }; n.contentDescription?.let { texts += it.toString() } }
                for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
            }
            if (com.raunak.daytimeline.campus.Commitment.explicitHits(texts, words()) >= 2) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                logShield()
                show(pkg, BlockActivity.MODE_BLOCK, "Content shield", false, 0, reopenAfterUnlock = false, note = c.letter)
                return true
            }
        }
        return false
    }

    private var bubble: android.widget.TextView? = null

    /** YourHour-style pill over the app in use: "Instagram · 42m today". Accessibility overlays need no extra permission. */
    private fun updateBubble(pkg: String?) {
        if (pkg == null || pkg == packageName || pkg in launchers || isNeutral(pkg) || !wellbeing.config.usageBubble) { hideBubble(); return }
        val minutes = todayTotals(System.currentTimeMillis()).first[pkg] ?: 0
        val text = WellbeingAlarmReceiver.label(this, pkg) + " · " + (if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m")
        val view = bubble ?: android.widget.TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            val pad = (resources.displayMetrics.density * 10).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = pad * 2f; setColor(0xCC17221E.toInt()) }
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT, android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.END; x = pad; y = (resources.displayMetrics.density * 36).toInt() }
            getSystemService(android.view.WindowManager::class.java).addView(this, params)
            bubble = this
        }
        view.text = text
    }

    private fun hideBubble() {
        bubble?.let { runCatching { getSystemService(android.view.WindowManager::class.java).removeView(it) } }
        bubble = null
    }

    private val usageExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var usageRefreshing = false

    /**
     * Today's usage per app. Scanning a day of usage events is slow, so it runs on a background thread;
     * callers get the last result (at most ~30 s old) and never block the UI thread.
     */
    private fun todayTotals(nowMillis: Long): Pair<Map<String, Int>, Int> {
        val cached = totalsCache
        if (cached != null && nowMillis - cached.first < 30_000) return cached.second
        if (!usageRefreshing) {
            usageRefreshing = true
            usageExecutor.execute {
                runCatching { totalsCache = System.currentTimeMillis() to UsageRepository.todayTotals(this) }
                usageRefreshing = false
            }
        }
        return cached?.second ?: (emptyMap<String, Int>() to 0)
    }

    private fun tick() {
        WellbeingModes.apply(this)
        val power = getSystemService(PowerManager::class.java)
        if (power?.isInteractive != true) {
            if (foreground != null) lastLeftAt = System.currentTimeMillis()
            foreground = null
            hideBubble()
            return
        }
        runCatching { updateBubble(foreground) }
        if (committed()) {
            val c = commit()
            val guarded = com.raunak.daytimeline.campus.Commitment.guardClock(c, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime())
            // Save only when the end date moved, after a reboot, or every 10 minutes, not on every tick.
            if (guarded.until != c.until || c.lastWall == 0L || guarded.lastElapsed < c.lastElapsed || guarded.lastWall - c.lastWall > 600_000L) discipline.update { it.copy(commit = guarded) }
        }
        if (committed() && commit().keepFilterOn && !com.raunak.daytimeline.filter.WebFilterVpnService.running && android.net.VpnService.prepare(this) == null)
            runCatching { com.raunak.daytimeline.filter.WebFilterVpnService.start(this) }
        if (foreground == null) rootInActiveWindow?.packageName?.toString()?.let { onForeground(it); return }
        val fg = foreground ?: return
        if (fg == packageName || fg in launchers || System.currentTimeMillis() - lastShownAt < 5_000) return
        evaluate(fg, fromOpen = false)
    }

    /** Backs out of blocked short-video screens (Shorts, Reels, Spotlight) while the rest of the app works. */
    private fun checkShortForm(pkg: String) {
        val forms = if (committed() && commit().noShortVideos) com.raunak.daytimeline.wellbeing.ShortForm.values().toSet() else wellbeing.config.blockedShortForm
        val blocked = forms.filter { it.packageName == pkg }
        if (blocked.isEmpty()) return
        val nowMillis = System.currentTimeMillis()
        if (nowMillis - lastShortCheck < 600) return
        lastShortCheck = nowMillis
        val root = rootInActiveWindow ?: return
        val hit = blocked.firstOrNull { sf -> wellbeing.config.idsFor(sf).any { id -> runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }.getOrNull()?.any { it.isVisibleToUser } == true } }
            ?: return
        performGlobalAction(GLOBAL_ACTION_BACK)
        toast("${hit.label} is blocked")
    }

    /** Strict mode: while blocks are active, cover pages that could uninstall or disable Chronora. */
    private fun strictGuard(pkg: String): Boolean {
        if (pkg !in strictPackages || !(wellbeing.config.strictMode || committed())) return false
        val nowMillis = System.currentTimeMillis()
        if (nowMillis - lastStrictCheck < 500) return false
        lastStrictCheck = nowMillis
        val now = LocalDateTime.now()
        val active = committed() || FocusGuardEngine.anyFocus(store.config, now, nowMillis) || PomodoroFocusFlag.isFocusRunning(this, nowMillis) ||
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

    private fun show(pkg: String, mode: String, reason: String, canUnlock: Boolean, seconds: Int, reopenAfterUnlock: Boolean = true, note: String = "") {
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
                .putExtra(BlockActivity.EXTRA_NOTE, note)
        )
    }

    private fun inputMethods(): Set<String> =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let { ComponentName.unflattenFromString(it)?.packageName }?.let { setOf(it) } ?: emptySet()

    override fun onInterrupt() = Unit

    companion object {
        private val clockApps = setOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage", "com.oneplus.deskclock", "com.miui.clock", "com.coloros.alarmclock")
        private val strictPackages = setOf(
            "com.android.settings", "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.samsung.android.lool", "com.miui.securitycenter",
            // The system "Disconnect VPN" dialog.
            "com.android.vpndialogs"
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
