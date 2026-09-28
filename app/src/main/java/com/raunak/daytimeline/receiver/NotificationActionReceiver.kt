package com.raunak.daytimeline.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import com.raunak.daytimeline.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) return
        when (intent.action) {
            ACTION_COMPLETE -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { AppContainer(context.applicationContext).repository.markComplete(taskId, true) }
                    finally { pending.finish() }
                }
            }
            ACTION_SNOOZE -> snooze(context, taskId)
        }
    }

    private fun snooze(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("taskId", taskId)
            .putExtra("title", "Snoozed task")
        val requestCode = (taskId xor 0x5A5A5A5AL).toInt()
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        AlarmManagerCompat.setAndAllowWhileIdle(
            alarmManager, AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 10 * 60_000L, pending
        )
    }

    companion object {
        const val ACTION_COMPLETE = "com.raunak.daytimeline.COMPLETE_TASK"
        const val ACTION_SNOOZE = "com.raunak.daytimeline.SNOOZE_TASK"
        const val EXTRA_TASK_ID = "taskId"
    }
}
