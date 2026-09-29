package com.raunak.daytimeline.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (id < 0) return

        val kind = runCatching {
            AlarmKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: AlarmKind.PRIMARY.name)
        }.getOrDefault(AlarmKind.PRIMARY)

        val config = AlarmPersistentStore(context).find(id) ?: return
        if (!config.enabled) return

        when (kind) {
            AlarmKind.BEDTIME -> AlarmNotificationHelper.showBedtime(context, config)
            AlarmKind.WAKE_CHECK -> {
                val state = AlarmRuntimeStore(context)
                val dismissed = state.dismissedAt(id)
                val confirmed = state.wakeCheckConfirmedAt(id)
                if (dismissed > 0L && confirmed < dismissed) {
                    AlarmNotificationHelper.showWakeCheck(
                        context,
                        config,
                        intent.getIntExtra(EXTRA_ATTEMPT, 0)
                    )
                }
            }
            AlarmKind.PRIMARY, AlarmKind.BACKUP, AlarmKind.SNOOZE -> {
                AlarmNotificationHelper.showAlarm(context, config)
            }
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_KIND = "alarm_kind"
        const val EXTRA_ATTEMPT = "alarm_attempt"
    }
}
