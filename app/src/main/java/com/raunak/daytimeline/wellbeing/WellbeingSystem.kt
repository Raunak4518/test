package com.raunak.daytimeline.wellbeing

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.pro.FocusGuardEngine
import com.raunak.daytimeline.pro.FocusGuardService
import com.raunak.daytimeline.pro.FocusGuardStore
import com.raunak.daytimeline.pro.PomodoroFocusFlag
import com.raunak.daytimeline.pro.UsageAccess
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Local storage for Digital Wellbeing settings and counters. */
class WellbeingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_wellbeing", Context.MODE_PRIVATE)
    private val gson = Gson()

    var config: WellbeingConfig
        get() = (read<WellbeingConfig>("config") ?: WellbeingConfig()).normalized()
        set(v) = write("config", v)

    fun update(transform: (WellbeingConfig) -> WellbeingConfig) { config = transform(config) }

    private fun today() = LocalDate.now().toString()

    /** App opens counted by the blocker, per package, for today. */
    fun opensToday(): Map<String, Int> = if (prefs.getString("opens_date", "") == today()) read("opens") ?: emptyMap() else emptyMap()

    fun recordOpen(pkg: String): Int {
        val next = opensToday().toMutableMap().apply { this[pkg] = (this[pkg] ?: 0) + 1 }
        prefs.edit().putString("opens_date", today()).apply()
        write("opens", next)
        return next.getValue(pkg)
    }

    /** Screen unlocks today, counted live by the blocker service. */
    fun unlocksToday(): Int = if (prefs.getString("unlocks_date", "") == today()) prefs.getInt("unlocks", 0) else 0

    fun recordUnlock(): Int {
        val n = unlocksToday() + 1
        prefs.edit().putString("unlocks_date", today()).putInt("unlocks", n).apply()
        return n
    }

    fun cooldowns(): Map<String, Long> = read("cooldowns") ?: emptyMap()
    fun setCooldown(pkg: String, until: Long) = write("cooldowns", cooldowns().filterValues { it > System.currentTimeMillis() } + (pkg to until))

    fun notificationCounts(date: LocalDate): Map<String, Int> = read("notif_${date}") ?: emptyMap()
    fun recordNotification(pkg: String) {
        val key = "notif_${today()}"
        write(key, (read<Map<String, Int>>(key) ?: emptyMap()).toMutableMap().apply { this[pkg] = (this[pkg] ?: 0) + 1 })
    }

    fun held(): List<HeldNotification> = read("held") ?: emptyList()
    fun hold(n: HeldNotification) = write("held", (held() + n).takeLast(500))
    fun clearHeld() = prefs.edit().remove("held").apply()

    fun dailyTotals(): Map<LocalDate, Int> = (read<Map<String, Int>>("daily_totals") ?: emptyMap()).mapKeys { LocalDate.parse(it.key) }
    fun saveDailyTotal(date: LocalDate, minutes: Int) {
        val all = (read<Map<String, Int>>("daily_totals") ?: emptyMap()) + (date.toString() to minutes)
        write("daily_totals", all.entries.sortedByDescending { it.key }.take(400).associate { it.key to it.value })
    }

    var dndSetByUs: Boolean
        get() = prefs.getBoolean("dnd_by_us", false)
        set(v) = prefs.edit().putBoolean("dnd_by_us", v).apply()
    var grayscaleSetByUs: Boolean
        get() = prefs.getBoolean("gray_by_us", false)
        set(v) = prefs.edit().putBoolean("gray_by_us", v).apply()

    /** Once-per-day warning keys so "5 minutes left" fires once. */
    fun warnOnce(key: String): Boolean {
        val k = "warn_${today()}_$key"
        if (prefs.getBoolean(k, false)) return false
        prefs.edit().putBoolean(k, true).apply()
        return true
    }

    private inline fun <reified T> read(key: String): T? = try {
        com.raunak.daytimeline.ui.ParsedCache.get("wb:$key", prefs.getString(key, null)) { gson.fromJson<T>(it, object : TypeToken<T>() {}.type) }
    } catch (_: Exception) { null }

    private fun write(key: String, value: Any) = prefs.edit().putString(key, gson.toJson(value)).apply()
}

/** Reads the system usage log into [UsageEventLite] and daily summaries. */
object UsageRepository {
    fun ignored(context: Context): Set<String> =
        FocusGuardService.homePackages(context) + setOf(context.packageName, "com.android.systemui", "android") + inputMethods(context)

    private fun inputMethods(context: Context): Set<String> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let { ComponentName.unflattenFromString(it)?.packageName }?.let { setOf(it) } ?: emptySet()

    fun events(context: Context, from: Long, to: Long): List<UsageEventLite> {
        if (!UsageAccess.granted(context)) return emptyList()
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val out = ArrayList<UsageEventLite>()
        val events = runCatching { manager.queryEvents(from, to) }.getOrNull() ?: return emptyList()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageEventType.APP_FOREGROUND
                UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageEventType.APP_BACKGROUND
                UsageEvents.Event.SCREEN_INTERACTIVE -> if (Build.VERSION.SDK_INT < 28) UsageEventType.UNLOCK else UsageEventType.SCREEN_ON
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventType.SCREEN_OFF
                18 /* KEYGUARD_HIDDEN, API 28 */ -> UsageEventType.UNLOCK
                else -> null
            } ?: continue
            out += UsageEventLite(e.timeStamp, type, e.packageName ?: "")
        }
        return out
    }

    fun day(context: Context, date: LocalDate, store: WellbeingStore = WellbeingStore(context)): DayUsage {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        // Start a little early so an app already open at midnight is counted from midnight.
        val notifications = store.notificationCounts(date).flatMap { (pkg, n) -> List(n) { UsageEventLite(start + 1, UsageEventType.NOTIFICATION, pkg) } }
        return UsageStatsCalculator.summarize(events(context, start - 2 * 3_600_000L, end) + notifications, start, maxOf(end, start + 1), ignored(context))
    }

    fun todayTotals(context: Context): Pair<Map<String, Int>, Int> {
        val d = day(context, LocalDate.now())
        return d.apps.associate { it.pkg to it.minutes } to d.totalMinutes
    }
}

/** Do Not Disturb and grayscale switching for bedtime and focus. */
object WellbeingModes {
    fun dndAccess(context: Context) = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun grayscaleAccess(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun apply(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val store = WellbeingStore(context)
        val c = store.config
        val bedtime = WellbeingEngine.bedtimeActive(c.bedtime, now)
        val nowMillis = System.currentTimeMillis()
        val focus = FocusGuardEngine.anyFocus(FocusGuardStore(context).config, now, nowMillis) || PomodoroFocusFlag.isFocusRunning(context, nowMillis)
        val wantDnd = (bedtime && c.bedtime.doNotDisturb) || (focus && c.doNotDisturbDuringFocus)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (dndAccess(context)) {
            if (wantDnd && nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY); store.dndSetByUs = true
            } else if (!wantDnd && store.dndSetByUs) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL); store.dndSetByUs = false
            }
        }
        if (grayscaleAccess(context)) {
            val wantGray = bedtime && c.bedtime.grayscale
            val cr = context.contentResolver
            val isGray = Settings.Secure.getInt(cr, "accessibility_display_daltonizer_enabled", 0) == 1 &&
                Settings.Secure.getInt(cr, "accessibility_display_daltonizer", -1) == 0
            runCatching {
                if (wantGray && !isGray) {
                    Settings.Secure.putInt(cr, "accessibility_display_daltonizer_enabled", 1)
                    Settings.Secure.putInt(cr, "accessibility_display_daltonizer", 0)
                    store.grayscaleSetByUs = true
                } else if (!wantGray && store.grayscaleSetByUs) {
                    Settings.Secure.putInt(cr, "accessibility_display_daltonizer_enabled", 0)
                    store.grayscaleSetByUs = false
                }
            }
        }
    }

    fun focusOrBedtime(context: Context): Boolean {
        val now = LocalDateTime.now(); val ms = System.currentTimeMillis()
        return WellbeingEngine.bedtimeActive(WellbeingStore(context).config.bedtime, now) ||
            FocusGuardEngine.anyFocus(FocusGuardStore(context).config, now, ms) || PomodoroFocusFlag.isFocusRunning(context, ms)
    }
}

/** Counts notifications per app and holds back "quiet" apps for the digest. */
class WellbeingNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName || sbn.isOngoing || sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return
        runCatching { com.raunak.daytimeline.classroom.ClassroomCapture.onPosted(this, sbn.packageName, sbn.notification.extras, sbn.postTime) }
        val store = WellbeingStore(this)
        store.recordNotification(sbn.packageName)
        if (NotificationDigest.shouldHold(store.config, sbn.packageName, WellbeingModes.focusOrBedtime(this))) {
            val extras = sbn.notification.extras
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
            store.hold(HeldNotification(sbn.postTime, sbn.packageName, label, extras.getCharSequence("android.title")?.toString().orEmpty(), extras.getCharSequence("android.text")?.toString().orEmpty()))
            cancelNotification(sbn.key)
        }
    }

    companion object {
        fun enabled(context: Context) = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}

/** Delivers the notification digest and the daily screen-time report at their set times. */
class WellbeingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DIGEST -> deliverDigest(context)
            ACTION_REPORT -> dailyReport(context)
        }
        schedule(context)
    }

    companion object {
        const val ACTION_DIGEST = "chronora.wellbeing.DIGEST"
        const val ACTION_REPORT = "chronora.wellbeing.REPORT"
        private const val CHANNEL = "chronora_wellbeing"

        fun schedule(context: Context) {
            val c = WellbeingStore(context).config
            val am = context.getSystemService(AlarmManager::class.java)
            val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
            fun at(minute: Int, tomorrow: Boolean) = LocalDate.now().plusDays(if (tomorrow) 1 else 0)
                .atStartOfDay(ZoneId.systemDefault()).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
            val digest = pending(context, ACTION_DIGEST)
            am.cancel(digest)
            if (c.quietApps.isNotEmpty()) NotificationDigest.nextDigest(c.digestTimes, nowMinute)?.let { (m, tomorrow) -> am.set(AlarmManager.RTC_WAKEUP, at(m, tomorrow), digest) }
            val report = pending(context, ACTION_REPORT)
            am.cancel(report)
            if (c.dailyReport) am.set(AlarmManager.RTC_WAKEUP, at(c.reportMinute, c.reportMinute <= nowMinute), report)
        }

        private fun pending(context: Context, action: String) = PendingIntent.getBroadcast(
            context, action.hashCode(), Intent(context, WellbeingAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun channel(context: Context) = context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Digital wellbeing", NotificationManager.IMPORTANCE_DEFAULT))

        private fun open(context: Context) = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        fun deliverDigest(context: Context) {
            val store = WellbeingStore(context)
            val held = store.held()
            if (held.isEmpty()) return
            channel(context)
            val style = NotificationCompat.InboxStyle()
            NotificationDigest.summary(held).take(7).forEach { style.addLine(it) }
            context.getSystemService(NotificationManager::class.java).notify(
                7501,
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("Notification digest · ${held.size} held")
                    .setContentText(NotificationDigest.summary(held).firstOrNull())
                    .setStyle(style)
                    .setContentIntent(open(context))
                    .setAutoCancel(true)
                    .build()
            )
            store.clearHeld()
        }

        fun dailyReport(context: Context) {
            if (!UsageAccess.granted(context)) return
            val store = WellbeingStore(context)
            val today = LocalDate.now()
            val day = UsageRepository.day(context, today, store)
            store.saveDailyTotal(today, day.totalMinutes)
            val c = store.config
            val streak = UsageStatsCalculator.goalStreak(store.dailyTotals(), c.screenTimeGoalMinutes, today)
            val top = day.apps.firstOrNull()?.let { " · top ${label(context, it.pkg)} ${hm(it.minutes)}" } ?: ""
            val goal = if (day.totalMinutes <= c.screenTimeGoalMinutes) "✓ under your ${hm(c.screenTimeGoalMinutes)} goal · $streak-day streak" else "Over your ${hm(c.screenTimeGoalMinutes)} goal by ${hm(day.totalMinutes - c.screenTimeGoalMinutes)}"
            var text = "${hm(day.totalMinutes)} screen time · ${day.pickups} pickups · ${day.notifications} notifications$top"
            if (today.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                val totals = store.dailyTotals()
                val week = (0L..6L).sumOf { totals[today.minusDays(it)] ?: 0 }
                val prev = (7L..13L).sumOf { totals[today.minusDays(it)] ?: 0 }
                UsageStatsCalculator.percentChange(week, prev)?.let { text += "\nThis week ${hm(week)} (${if (it <= 0) "" else "+"}$it% vs last week)" }
            }
            channel(context)
            context.getSystemService(NotificationManager::class.java).notify(
                7502,
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                    .setContentTitle(goal)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(open(context))
                    .setAutoCancel(true)
                    .build()
            )
        }

        fun warn(context: Context, title: String, text: String) {
            channel(context)
            context.getSystemService(NotificationManager::class.java).notify(
                7503,
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setTimeoutAfter(60_000)
                    .setAutoCancel(true)
                    .build()
            )
        }

        fun label(context: Context, pkg: String) = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg.substringAfterLast('.'))
        fun hm(minutes: Int) = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }
}
