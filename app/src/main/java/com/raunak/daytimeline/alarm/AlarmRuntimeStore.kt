package com.raunak.daytimeline.alarm

import android.content.Context

/** Small crash/process-death-safe state store for alarm lifecycle events. */
class AlarmRuntimeStore(context: Context) {
    private val prefs = context.getSharedPreferences("alarm_runtime", Context.MODE_PRIVATE)

    fun markDismissed(alarmId: Long, atMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("dismissed_$alarmId", atMillis).apply()
    }

    fun dismissedAt(alarmId: Long): Long = prefs.getLong("dismissed_$alarmId", 0L)

    fun clear(alarmId: Long) {
        prefs.edit().remove("dismissed_$alarmId").apply()
    }

    fun markWakeCheckConfirmed(alarmId: Long) {
        prefs.edit().putLong("confirmed_$alarmId", System.currentTimeMillis()).apply()
    }

    fun wakeCheckConfirmedAt(alarmId: Long): Long = prefs.getLong("confirmed_$alarmId", 0L)
}
