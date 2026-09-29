package com.raunak.daytimeline.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "day_timeline_reminders"
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val title = intent.getStringExtra("title") ?: "Upcoming task"
        val id = intent.getLongExtra("taskId", System.currentTimeMillis()).toInt()
        val taskId = intent.getLongExtra("taskId", -1L)
        val complete = action(context, NotificationActionReceiver.ACTION_COMPLETE, taskId, id + 1)
        val snooze = action(context, NotificationActionReceiver.ACTION_SNOOZE, taskId, id + 2)
        manager.notify(
            id,
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText("Reminder from Chronora")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(android.R.drawable.checkbox_on_background, "Complete", complete)
                .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze 10m", snooze)
                .build()
        )
    }

    private fun action(context: Context, action: String, taskId: Long, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(action)
                .putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
