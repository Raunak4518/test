package com.raunak.daytimeline.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.raunak.daytimeline.R

object AlarmNotificationHelper {
    private const val CHANNEL = "alarm_reminders"
    private fun manager(context: Context): NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun ensure(context: Context) { manager(context).createNotificationChannel(NotificationChannel(CHANNEL, "Alarm reminders", NotificationManager.IMPORTANCE_HIGH)) }
    fun showBedtime(context: Context, config: AlarmPersistentConfig) { show(context, config, "Bedtime reminder", "Your alarm is in ${config.bedtimeReminderMinutes} minutes") }
    fun showWakeCheck(context: Context, config: AlarmPersistentConfig) { show(context, config, "Wake-up check", "Confirm that you are awake") }
    private fun show(context: Context, config: AlarmPersistentConfig, title: String, text: String) {
        ensure(context)
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        manager(context).notify((config.id xor (config.id ushr 32)).toInt(), n)
    }
}
