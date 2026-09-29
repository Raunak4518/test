package com.raunak.daytimeline.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmWakeCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, -1L)
        if (id < 0) return
        when (intent.action) {
            ACTION_AWAKE -> {
                AlarmRuntimeStore(context).markWakeCheckConfirmed(id)
                AlarmManagerBridge(context).cancelWakeChecks(id)
                AlarmNotificationHelper.cancelWakeCheck(context, id)
            }
            ACTION_REOPEN -> {
                val config = AlarmPersistentStore(context).find(id) ?: return
                AlarmNotificationHelper.showAlarm(context, config)
            }
        }
    }

    companion object {
        const val ACTION_AWAKE = "com.raunak.daytimeline.ALARM_AWAKE"
        const val ACTION_REOPEN = "com.raunak.daytimeline.ALARM_REOPEN"
    }
}
