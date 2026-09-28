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
    private fun manager(c: Context)=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun ensure(c: Context){manager(c).createNotificationChannel(NotificationChannel(CHANNEL,"Alarm reminders",NotificationManager.IMPORTANCE_HIGH))}
    fun showAlarm(c: Context,config: AlarmPersistentConfig){ensure(c);val intent=Intent(c,AlarmRingingActivity::class.java).apply{addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP);putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID,config.id)};val pi=PendingIntent.getActivity(c,(config.id xor(config.id ushr 32)).toInt(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val n=NotificationCompat.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(config.label).setContentText("Alarm — complete the wake-up mission").setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM).setOngoing(true).setFullScreenIntent(pi,config.fullscreen).build();manager(c).notify((config.id xor(config.id ushr 32)).toInt(),n)}
    fun showBedtime(c:Context,config:AlarmPersistentConfig)=show(c,config,"Bedtime reminder","Your alarm is in ${config.bedtimeReminderMinutes} minutes")
    fun showWakeCheck(c:Context,config:AlarmPersistentConfig)=show(c,config,"Wake-up check","Confirm that you are awake")
    private fun show(c:Context,config:AlarmPersistentConfig,title:String,text:String){ensure(c);val n=NotificationCompat.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build();manager(c).notify((config.id xor(config.id ushr 32)).toInt(),n)}
}
