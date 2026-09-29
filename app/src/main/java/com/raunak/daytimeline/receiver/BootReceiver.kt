package com.raunak.daytimeline.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raunak.daytimeline.AppContainer
import com.raunak.daytimeline.alarm.AlarmManagerBridge
import com.raunak.daytimeline.alarm.AlarmPersistentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app=AppContainer(context)
        CoroutineScope(Dispatchers.IO).launch {
            app.repository.rescheduleAllReminders()
            com.raunak.daytimeline.features.OfflineProductivityStore(context.applicationContext).rescheduleHabitReminders()
            val store=AlarmPersistentStore(context); val scheduler=AlarmManagerBridge(context)
            store.all().filter { it.enabled }.forEach { scheduler.schedule(it) }
            com.raunak.daytimeline.pro.LocationReminderManager(context.applicationContext).registerAll()
            com.raunak.daytimeline.wellbeing.WellbeingAlarmReceiver.schedule(context.applicationContext)
            val filter = com.raunak.daytimeline.filter.WebFilterStore(context).config
            if (filter.enabled && filter.startOnBoot) runCatching { com.raunak.daytimeline.filter.WebFilterVpnService.start(context.applicationContext) }
        }
    }
}
