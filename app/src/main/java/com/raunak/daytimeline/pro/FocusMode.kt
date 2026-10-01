package com.raunak.daytimeline.pro

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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** What Focus mode is doing right now, for the UI, the tile and the notification. */
data class FocusModeStatus(val on: Boolean, val onBreak: Boolean, val scheduled: Boolean, val until: Long, val breakUntil: Long, val apps: Int, val strict: Boolean) {
    val label: String get() = when {
        onBreak -> "On a break until ${clockOf(breakUntil)}"
        on && until > 0 -> "On until ${clockOf(until)}"
        on && scheduled -> "On (scheduled)"
        on -> "On"
        else -> "Off"
    }
    companion object {
        fun clockOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
    }
}

/** One-tap Focus mode: pauses the chosen distracting apps until it's turned off, times out, or a break is taken. */
object FocusMode {
    private const val CHANNEL = "focus_mode"
    private const val NOTIFICATION_ID = 7410
    const val ACTION = "com.raunak.daytimeline.FOCUS_MODE"
    const val EXTRA_OP = "op"
    const val EXTRA_MINUTES = "minutes"

    fun status(c: FocusGuardConfig, nowMillis: Long = System.currentTimeMillis(), now: LocalDateTime = LocalDateTime.now()): FocusModeStatus {
        val manual = c.focusModeOn && (c.focusModeUntil == 0L || c.focusModeUntil > nowMillis)
        val scheduled = FocusGuardEngine.activeIn(c.focusModeSchedules, now) != null
        val on = manual || scheduled
        return FocusModeStatus(on, on && c.focusModeBreakUntil > nowMillis, scheduled && !manual, if (manual) c.focusModeUntil else 0L, c.focusModeBreakUntil, c.focusModeApps.size, c.focusModeStrict)
    }

    /** Turns Focus mode on, for [minutes] or until turned off (null). */
    fun turnOn(context: Context, minutes: Int? = null) {
        val now = System.currentTimeMillis()
        FocusGuardStore(context).update { it.copy(focusModeOn = true, focusModeUntil = minutes?.let { m -> now + m * 60_000L } ?: 0L, focusModeBreakUntil = 0L, focusModeStartedAt = now) }
        refresh(context)
    }

    /** Can the user switch it off now? Not inside a schedule, and not before a strict timer ends. */
    fun canTurnOff(c: FocusGuardConfig, nowMillis: Long = System.currentTimeMillis(), now: LocalDateTime = LocalDateTime.now()): Boolean {
        val s = status(c, nowMillis, now)
        return !s.scheduled && !(c.focusModeStrict && s.until > nowMillis)
    }

    /** Returns false if refused (strict timer running, or a schedule is active). */
    fun turnOff(context: Context): Boolean {
        val store = FocusGuardStore(context)
        val c = store.config
        if (!canTurnOff(c)) return false
        store.update { it.copy(focusModeOn = false, focusModeUntil = 0L, focusModeBreakUntil = 0L) }
        refresh(context)
        return true
    }

    fun takeBreak(context: Context, minutes: Int): Boolean {
        val store = FocusGuardStore(context)
        if (store.config.focusModeStrict) return false
        val now = System.currentTimeMillis()
        store.update { it.copy(focusModeBreakUntil = now + minutes * 60_000L, focusModeBreakStartedAt = now) }
        refresh(context)
        return true
    }

    fun endBreak(context: Context) {
        FocusGuardStore(context).update { it.copy(focusModeBreakUntil = 0L) }
        refresh(context)
    }

    /** Updates the ongoing notification and schedules the next refresh (break over, timer ended). */
    fun refresh(context: Context) {
        val c = FocusGuardStore(context).config
        val nowMillis = System.currentTimeMillis()
        val s = status(c, nowMillis)
        val nm = NotificationManagerCompat.from(context)
        if (!s.on) {
            nm.cancel(NOTIFICATION_ID)
            if (c.focusModeOn) FocusGuardStore(context).update { it.copy(focusModeOn = false, focusModeUntil = 0L) }
            return
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Focus mode", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 7411, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Focus mode " + if (s.onBreak) "· break" else "is on")
            .setContentText(s.label + " · ${s.apps} apps paused")
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
        if (s.onBreak) b.addAction(0, "End break", op(context, "end_break", 0))
        else if (!s.strict) c.focusModeBreaks.take(2).forEach { m -> b.addAction(0, "Break ${m}m", op(context, "break", m)) }
        if (canTurnOff(c, nowMillis)) b.addAction(0, "Turn off", op(context, "off", 0))
        runCatching { nm.notify(NOTIFICATION_ID, b.build()) }
        val next = listOf(c.focusModeBreakUntil, c.focusModeUntil).filter { it > nowMillis }.minOrNull()
        if (next != null) runCatching {
            context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, next + 1000, op(context, "refresh", 0))
        }
    }

    private fun op(context: Context, op: String, minutes: Int): PendingIntent =
        PendingIntent.getBroadcast(context, (op.hashCode() + minutes) and 0xFFFF, Intent(context, FocusModeReceiver::class.java).setAction(ACTION).putExtra(EXTRA_OP, op).putExtra(EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

class FocusModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra(FocusMode.EXTRA_OP)) {
            "break" -> FocusMode.takeBreak(context, intent.getIntExtra(FocusMode.EXTRA_MINUTES, 5))
            "end_break" -> FocusMode.endBreak(context)
            "off" -> FocusMode.turnOff(context)
            else -> FocusMode.refresh(context)
        }
    }
}
