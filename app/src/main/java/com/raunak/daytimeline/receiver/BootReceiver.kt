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
            val store=AlarmPersistentStore(context); val scheduler=AlarmManagerBridge(context)
            store.all().filter { it.enabled }.forEach { scheduler.schedule(it) }
        }
    }
}
