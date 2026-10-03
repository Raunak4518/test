package com.raunak.daytimeline.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import com.raunak.daytimeline.receiver.ReminderReceiver
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(task: TaskEntity) {
        cancel(task.id)
        if (task.reminderMode == "NONE") return
        val reminderMillis = reminderTime(task) ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("taskId", task.id)
            .putExtra("title", task.title)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Exact alarms need permission only from Android 12; before that they're always allowed.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                reminderMillis,
                pendingIntent
            )
        } else {
            AlarmManagerCompat.setAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                reminderMillis,
                pendingIntent
            )
        }
    }

    fun cancel(taskId: Long) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun reminderTime(task: TaskEntity): Long? {
        val taskStart = LocalDate.ofEpochDay(task.dateEpochDay)
            .atTime(LocalTime.MIN.plusMinutes(task.startMinute.toLong()))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return when (task.reminderMode) {
            "AT_START" -> taskStart
            "BEFORE" -> taskStart - task.reminderOffsetMinutes * 60_000L
            "CUSTOM" -> taskStart - task.reminderOffsetMinutes * 60_000L
            else -> null
        }
    }
}
