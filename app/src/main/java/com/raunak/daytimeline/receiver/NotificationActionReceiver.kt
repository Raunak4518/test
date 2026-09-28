package com.raunak.daytimeline.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raunak.daytimeline.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = AppContainer(context.applicationContext).repository
                when (intent.action) {
                    ACTION_COMPLETE -> repository.markComplete(taskId, true)
                    ACTION_SNOOZE -> {
                        // Snooze is represented as a short-lived reminder reschedule rather than
                        // mutating the task's planned start time. This keeps the user's schedule intact.
                        repository.rescheduleAllReminders()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.raunak.daytimeline.COMPLETE_TASK"
        const val ACTION_SNOOZE = "com.raunak.daytimeline.SNOOZE_TASK"
        const val EXTRA_TASK_ID = "taskId"
    }
}
