package com.raunak.daytimeline.trackers

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Values read from the phone for automatic trackers. */
object TrackerAuto {
    fun value(context: Context, t: Tracker, date: LocalDate): Double = runCatching {
        when (t.source) {
            TrackerSource.SCREEN_TIME -> com.raunak.daytimeline.wellbeing.UsageRepository.day(context, date).totalMinutes.toDouble()
            TrackerSource.APP_USAGE -> com.raunak.daytimeline.wellbeing.UsageRepository.day(context, date).apps.filter { it.pkg in t.packages }.sumOf { it.minutes }.toDouble()
            TrackerSource.FOCUS -> com.raunak.daytimeline.pro.GardenStore(context).sessions().filter { it.date == date.toString() && it.completed }.sumOf { it.minutes }.toDouble()
            TrackerSource.MANUAL -> 0.0
        }
    }.getOrDefault(0.0)
}

/** Daily reminders per tracker, with one-tap logging right from the notification. */
object TrackerReminders {
    private const val CHANNEL = "trackers"
    const val ACTION_REMIND = "com.raunak.daytimeline.TRACKER_REMIND"
    const val ACTION_LOG = "com.raunak.daytimeline.TRACKER_LOG"
    const val ACTION_SAVER = "com.raunak.daytimeline.TRACKER_SAVER"
    /** Offset that keeps streak-saver alarms apart from normal reminders. */
    private const val SAVER = 3000

    private fun code(id: Long, minute: Int) = ((id xor (id ushr 32)).toInt() * 31 + minute) and 0x7FFFFFFF

    fun scheduleAll(context: Context) {
        val store = TrackerStore.get(context)
        store.trackers.value.forEach { t ->
            t.reminders.forEach { m -> if (t.archived || t.auto) cancel(context, t.id, m) else schedule(context, t, m) }
            if (t.saverMinute >= 0) { if (t.archived || t.auto) cancel(context, t.id, t.saverMinute, true) else schedule(context, t, t.saverMinute, true) }
        }
    }

    fun cancelAll(context: Context, t: Tracker) {
        t.reminders.forEach { cancel(context, t.id, it) }
        if (t.saverMinute >= 0) cancel(context, t.id, t.saverMinute, true)
    }

    private fun intent(context: Context, id: Long, minute: Int, saver: Boolean = false) = PendingIntent.getBroadcast(context, code(id, minute + if (saver) SAVER else 0),
        Intent(context, TrackerReceiver::class.java).setAction(if (saver) ACTION_SAVER else ACTION_REMIND).putExtra("id", id).putExtra("minute", minute),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun schedule(context: Context, t: Tracker, minute: Int, saver: Boolean = false) {
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(minute / 60, minute % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching { context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, intent(context, t.id, minute, saver)) }
    }

    private fun cancel(context: Context, id: Long, minute: Int, saver: Boolean = false) = runCatching { context.getSystemService(AlarmManager::class.java).cancel(intent(context, id, minute, saver)) }

    fun notify(context: Context, t: Tracker, saver: Boolean = false) {
        val store = TrackerStore.get(context)
        val today = LocalDate.now()
        val v = TrackerEngine.periodValue(t, store.entries.value, today)
        // Only nudge when it's due today and not already reached.
        if (!TrackerEngine.scheduled(t, today) || t.goal == TrackerGoal.AT_MOST || TrackerEngine.met(t, v)) return
        val streak = TrackerEngine.streak(t, store.entries.value, today)
        // The streak saver only speaks up when there's a streak to save.
        if (saver && streak < 1) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Trackers", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, code(t.id, 9999), Intent(context, MainActivity::class.java).putExtra("chronora.open", "trackers").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (saver) "🔥 Your $streak-day ${t.name} streak ends at midnight" else "${t.emoji} ${t.name}")
            .setContentText(if (saver) "Log it now to keep it going. You've come this far." else TrackerEngine.nudge(t, streak) +
                if (t.type != TrackerType.CHECK && t.type != TrackerType.CHOICE && t.goal != TrackerGoal.NONE) " · ${TrackerEngine.format(t, v)} of ${TrackerEngine.targetText(t)}" else "")
            .setContentIntent(open).setAutoCancel(true)
        actions(t).take(3).forEach { (label, value, choice) ->
            b.addAction(0, label, PendingIntent.getBroadcast(context, code(t.id, label.hashCode()), Intent(context, TrackerReceiver::class.java).setAction(ACTION_LOG)
                .putExtra("id", t.id).putExtra("value", value).putExtra("choice", choice), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        runCatching { NotificationManagerCompat.from(context).notify(code(t.id, 7777), b.build()) }
    }

    /** One-tap buttons for a tracker: label, value, choice. */
    fun actions(t: Tracker): List<Triple<String, Double, String?>> = when (t.type) {
        TrackerType.CHECK -> listOf(Triple("Done", 1.0, null))
        TrackerType.CHOICE -> t.choices.map { Triple(it, 1.0, it) }
        TrackerType.RATING -> (1..5).map { Triple("$it", it.toDouble(), null) }.takeLast(3)
        TrackerType.DURATION -> t.quickAmounts.ifEmpty { listOf(10.0) }.map { Triple("+${TrackerEngine.mins(it.toInt())}", it, null) }
        else -> t.quickAmounts.ifEmpty { listOf(1.0) }.map { Triple("+${TrackerEngine.num(it)}${if (t.unit.isNotBlank()) " ${t.unit}" else ""}", it, null) }
    }

    fun cancelNotification(context: Context, id: Long) = NotificationManagerCompat.from(context).cancel(code(id, 7777))
}

class TrackerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = TrackerStore.get(context)
        val t = store.trackers.value.firstOrNull { it.id == intent.getLongExtra("id", -1) } ?: return
        when (intent.action) {
            TrackerReminders.ACTION_REMIND -> {
                TrackerReminders.notify(context, t)
                TrackerReminders.schedule(context, t, intent.getIntExtra("minute", 9 * 60))
            }
            TrackerReminders.ACTION_SAVER -> {
                TrackerReminders.notify(context, t, saver = true)
                TrackerReminders.schedule(context, t, intent.getIntExtra("minute", 21 * 60), saver = true)
            }
            TrackerReminders.ACTION_LOG -> {
                store.log(t, LocalDate.now(), intent.getDoubleExtra("value", 1.0), intent.getStringExtra("choice"))
                TrackerReminders.cancelNotification(context, t.id)
            }
        }
    }
}
