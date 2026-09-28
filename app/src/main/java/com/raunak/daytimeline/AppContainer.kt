package com.raunak.daytimeline

import android.content.Context
import com.raunak.daytimeline.data.AppDatabase
import com.raunak.daytimeline.data.ReminderScheduler
import com.raunak.daytimeline.data.TaskRepository
import com.raunak.daytimeline.settings.SettingsStore

class AppContainer(context: Context) {
    private val db = AppDatabase.get(context)
    private val reminderScheduler = ReminderScheduler(context)
    val settingsStore = SettingsStore(context)
    val repository = TaskRepository(db.taskDao(), db.checklistDao(), db.pomodoroDao(), reminderScheduler)
}
