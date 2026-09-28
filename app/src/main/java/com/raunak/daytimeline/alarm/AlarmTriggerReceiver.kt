package com.raunak.daytimeline.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (id < 0) return
        val kind = runCatching { AlarmKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: AlarmKind.PRIMARY.name) }.getOrDefault(AlarmKind.PRIMARY)
        val config = AlarmPersistentStore(context).find(id) ?: return
        if (!config.enabled) return
        when (kind) {
            AlarmKind.BEDTIME -> AlarmNotificationHelper.showBedtime(context, config)
            AlarmKind.WAKE_CHECK -> AlarmNotificationHelper.showWakeCheck(context, config)
            AlarmKind.PRIMARY, AlarmKind.BACKUP, AlarmKind.SNOOZE -> {
                val activity = Intent(context, AlarmRingingActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_ALARM_ID, id)
                    putExtra(EXTRA_KIND, kind.name)
                }
                context.startActivity(activity)
            }
        }
    }
    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_KIND = "alarm_kind"
    }
}
