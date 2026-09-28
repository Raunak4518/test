package com.raunak.daytimeline.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.raunak.daytimeline.R

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "day_timeline_reminders"
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val title = intent.getStringExtra("title") ?: "Upcoming task"
        val id = intent.getLongExtra("taskId", System.currentTimeMillis()).toInt()
        manager.notify(
            id,
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText("Reminder from Day Timeline")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }
}
