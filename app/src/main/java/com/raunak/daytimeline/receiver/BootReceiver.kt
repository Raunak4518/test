package com.raunak.daytimeline.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raunak.daytimeline.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = AppContainer(context)
        CoroutineScope(Dispatchers.IO).launch {
            app.repository.rescheduleAllReminders()
        }
    }
}
