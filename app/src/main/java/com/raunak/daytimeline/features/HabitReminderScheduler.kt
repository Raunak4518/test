package com.raunak.daytimeline.features

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import com.raunak.daytimeline.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class HabitReminderScheduler(private val context: Context) {
    private val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    fun schedule(habit: OfflineHabit) {
        cancel(habit.id)
        val time = runCatching { LocalTime.parse(habit.preferredTime) }.getOrNull() ?: return
        val now = java.time.ZonedDateTime.now()
        var next = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val intent = Intent(context, HabitReminderReceiver::class.java).putExtra("habitId", habit.id).putExtra("title", habit.name)
        val pi = PendingIntent.getBroadcast(context, habit.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pi)
    }
    fun cancel(id: Long) {
        val pi = PendingIntent.getBroadcast(context, id.hashCode(), Intent(context, HabitReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.cancel(pi)
    }
}

class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel("habit_reminders", "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
        val title = intent.getStringExtra("title") ?: "Habit"
        manager.notify((intent.getLongExtra("habitId", 0L) and 0x7fffffff).toInt(), NotificationCompat.Builder(context, "habit_reminders").setSmallIcon(R.drawable.ic_notification).setContentTitle("Habit reminder").setContentText("Time for $title").setAutoCancel(true).build())
    }
}
