package com.raunak.daytimeline.alarm

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One wake-up: when it was meant to ring, when it first rang, when you finished, snoozes and mission time. */
data class WakeRecord(
    val alarmId: Long,
    val label: String,
    val scheduledAt: Long,
    val firstRangAt: Long,
    val dismissedAt: Long,
    val snoozes: Int,
    val missionSeconds: Int
) {
    /** Minutes from the planned time to actually being up. */
    val lateMinutes: Int get() = ((dismissedAt - scheduledAt) / 60_000L).toInt().coerceAtLeast(0)
}

data class WakeStats(val records: Int, val onTimeRate: Int, val averageLate: Int, val averageSnoozes: Double, val averageMissionSeconds: Int, val onTimeStreak: Int)

class AlarmHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_alarm_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun all(): List<WakeRecord> = runCatching { gson.fromJson<List<WakeRecord>>(prefs.getString("records", "[]"), object : TypeToken<List<WakeRecord>>() {}.type) }.getOrNull() ?: emptyList()

    /** First ring of a wake-up (snoozes keep the original start). */
    fun onRing(alarmId: Long, now: Long = System.currentTimeMillis()) { if (prefs.getLong("start_$alarmId", 0) == 0L) prefs.edit().putLong("start_$alarmId", now).apply() }

    fun onSnooze(alarmId: Long) = prefs.edit().putInt("snoozes_$alarmId", snoozes(alarmId) + 1).apply()

    fun snoozes(alarmId: Long) = prefs.getInt("snoozes_$alarmId", 0)

    fun onDismiss(config: AlarmPersistentConfig, missionSeconds: Int, now: Long = System.currentTimeMillis()) {
        val first = prefs.getLong("start_${config.id}", now)
        val zone = ZoneId.systemDefault()
        var scheduled = Instant.ofEpochMilli(first).atZone(zone).toLocalDate().atTime(config.hour, config.minute).atZone(zone).toInstant().toEpochMilli()
        if (scheduled > first + 60_000L) scheduled -= 86_400_000L
        val record = WakeRecord(config.id, config.label, scheduled, first, now, snoozes(config.id), missionSeconds)
        prefs.edit().putString("records", gson.toJson((all() + record).takeLast(365))).remove("start_${config.id}").remove("snoozes_${config.id}").apply()
    }

    companion object {
        /** On time = up within [graceMinutes] of the planned time. */
        fun stats(records: List<WakeRecord>, graceMinutes: Int = 10): WakeStats {
            if (records.isEmpty()) return WakeStats(0, 0, 0, 0.0, 0, 0)
            val onTime = records.map { it.lateMinutes <= graceMinutes }
            var streak = 0
            for (ok in onTime.reversed()) if (ok) streak++ else break
            return WakeStats(records.size, 100 * onTime.count { it } / records.size, records.map { it.lateMinutes }.average().toInt(),
                records.map { it.snoozes }.average(), records.map { it.missionSeconds }.average().toInt(), streak)
        }

        fun day(r: WakeRecord): LocalDate = Instant.ofEpochMilli(r.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
