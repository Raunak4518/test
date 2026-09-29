package com.raunak.daytimeline.alarm

import android.content.Context

/** Persists one-shot skip-next state locally so it survives process death. */
class AlarmSkipStore(context: Context) {
    private val prefs = context.getSharedPreferences("alarm_skip_next", Context.MODE_PRIVATE)

    fun setSkipNext(alarmId: Long, skip: Boolean = true) {
        prefs.edit().putBoolean(alarmId.toString(), skip).apply()
    }

    fun consumeIfSkipped(alarmId: Long): Boolean {
        val skipped = prefs.getBoolean(alarmId.toString(), false)
        if (skipped) prefs.edit().remove(alarmId.toString()).apply()
        return skipped
    }

    fun clear(alarmId: Long) { prefs.edit().remove(alarmId.toString()).apply() }
}
