package com.raunak.daytimeline.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.raunak.daytimeline.R

/** AlarmManager entry point. Uses a full-screen alarm notification so Android can present the ringing UI reliably. */
class AlarmChallengeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val launch = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmRingingActivity.EXTRA_TITLE, intent.getStringExtra(AlarmRingingActivity.EXTRA_TITLE))
            putExtra(AlarmRingingActivity.EXTRA_MISSION, intent.getStringExtra(AlarmRingingActivity.EXTRA_MISSION) ?: MissionType.NONE.name)
        }
        val pending = PendingIntent.getActivity(context, 7101, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(true)
        })
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(intent.getStringExtra(AlarmRingingActivity.EXTRA_TITLE) ?: "Alarm")
                .setContentText("Wake up — complete your challenge")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(pending, true)
                .build()
        )
        if (android.os.Build.VERSION.SDK_INT < 29) context.startActivity(launch)
    }
    companion object { const val CHANNEL = "day_timeline_alarms"; const val NOTIFICATION_ID = 7200 }
}
