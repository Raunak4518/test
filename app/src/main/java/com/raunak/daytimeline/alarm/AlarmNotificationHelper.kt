package com.raunak.daytimeline.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.raunak.daytimeline.R

object AlarmNotificationHelper {
    private const val CHANNEL = "alarm_reminders"

    private fun manager(c: Context) = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensure(c: Context) {
        manager(c).createNotificationChannel(
            NotificationChannel(CHANNEL, "Alarm reminders", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun showAlarm(c: Context, config: AlarmPersistentConfig) {
        ensure(c)
        val intent = Intent(c, AlarmRingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, config.id)
        }
        val pi = PendingIntent.getActivity(
            c, (config.id xor (config.id ushr 32)).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(config.label)
            .setContentText("Alarm — complete the wake-up mission")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(pi, config.fullscreen)
            .build()
        manager(c).notify(notificationId(config.id), n)
    }

    fun showBedtime(c: Context, config: AlarmPersistentConfig) =
        show(c, config, "Bedtime reminder", "Your alarm is in ${config.bedtimeReminderMinutes} minutes")

    fun showWakeCheck(c: Context, config: AlarmPersistentConfig, attempt: Int = 0) {
        ensure(c)
        val awakeIntent = Intent(c, AlarmWakeCheckReceiver::class.java).apply {
            action = AlarmWakeCheckReceiver.ACTION_AWAKE
            putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, config.id)
        }
        val awake = PendingIntent.getBroadcast(
            c, notificationId(config.id) + 1000, awakeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val reopenIntent = Intent(c, AlarmWakeCheckReceiver::class.java).apply {
            action = AlarmWakeCheckReceiver.ACTION_REOPEN
            putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, config.id)
        }
        val reopen = PendingIntent.getBroadcast(
            c, notificationId(config.id) + 1001 + attempt, reopenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Wake-up check")
            .setContentText(if (attempt == 0) "Confirm that you are awake." else "Still awake? Confirm again.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .addAction(0, "I'm awake", awake)
            .addAction(0, "Open alarm", reopen)
            .build()
        manager(c).notify(notificationId(config.id) + 500 + attempt, n)
    }

    fun cancelWakeCheck(c: Context, id: Long) {
        val m = manager(c)
        (0..10).forEach { m.cancel(notificationId(id) + 500 + it) }
    }

    private fun show(c: Context, config: AlarmPersistentConfig, title: String, text: String) {
        ensure(c)
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager(c).notify(notificationId(config.id), n)
    }

    private fun notificationId(id: Long) = (id xor (id ushr 32)).toInt()
}
