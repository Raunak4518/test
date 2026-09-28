package com.raunak.daytimeline.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Offline per-alarm configuration store. No account or server required. */
data class AlarmPersistentConfig(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String = "Alarm",
    val enabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(), // java Calendar: 1=Sun ... 7=Sat
    val soundUri: String? = null,
    val vibration: Boolean = true,
    val fullscreen: Boolean = true,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    val missionChain: List<AlarmMission> = AlarmMissionCatalog.recommended(),
    val backupAlarmEnabled: Boolean = false,
    val backupDelayMinutes: Int = 5,
    val wakeCheckMinutes: Int = 0,
    val bedtimeReminderMinutes: Int = 0,
    val timeoutMinutes: Int = 20,
    val gentleVolumeSeconds: Int = 0,
    val longPressMs: Long = 1200
) {
    fun validated(): AlarmPersistentConfig = copy(
        hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59),
        snoozeMinutes = snoozeMinutes.coerceIn(1, 60), maxSnoozes = maxSnoozes.coerceIn(0, 20),
        backupDelayMinutes = backupDelayMinutes.coerceIn(1, 60), wakeCheckMinutes = wakeCheckMinutes.coerceIn(0, 180),
        bedtimeReminderMinutes = bedtimeReminderMinutes.coerceIn(0, 720), timeoutMinutes = timeoutMinutes.coerceIn(1, 120),
        gentleVolumeSeconds = gentleVolumeSeconds.coerceIn(0, 300), longPressMs = longPressMs.coerceIn(500, 5000),
        repeatDays = repeatDays.filter { it in 1..7 }.toSet()
    )
}

class AlarmPersistentStore(context: Context) {
    private val prefs = context.getSharedPreferences("offline_alarms", Context.MODE_PRIVATE)

    fun all(): List<AlarmPersistentConfig> = runCatching {
        val array = JSONArray(prefs.getString("alarms", "[]"))
        (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun save(config: AlarmPersistentConfig) {
        val updated = all().filterNot { it.id == config.id } + config.validated()
        prefs.edit().putString("alarms", JSONArray().apply { updated.forEach { put(toJson(it)) } }.toString()).apply()
    }

    fun delete(id: Long) { saveAll(all().filterNot { it.id == id }) }
    fun find(id: Long): AlarmPersistentConfig? = all().firstOrNull { it.id == id }

    private fun saveAll(items: List<AlarmPersistentConfig>) {
        prefs.edit().putString("alarms", JSONArray().apply { items.forEach { put(toJson(it)) } }.toString()).apply()
    }

    private fun toJson(a: AlarmPersistentConfig) = JSONObject().apply {
        put("id", a.id); put("hour", a.hour); put("minute", a.minute); put("label", a.label); put("enabled", a.enabled)
        put("repeatDays", JSONArray(a.repeatDays.toList())); put("soundUri", a.soundUri); put("vibration", a.vibration); put("fullscreen", a.fullscreen)
        put("snoozeMinutes", a.snoozeMinutes); put("maxSnoozes", a.maxSnoozes); put("backup", a.backupAlarmEnabled); put("backupDelay", a.backupDelayMinutes)
        put("wakeCheck", a.wakeCheckMinutes); put("bedtime", a.bedtimeReminderMinutes); put("timeout", a.timeoutMinutes); put("gentle", a.gentleVolumeSeconds); put("longPress", a.longPressMs)
        put("missions", JSONArray(a.missionChain.map { JSONObject().apply { put("type", it.type.name); put("label", it.label); put("target", it.target); put("payload", it.payload); put("order", it.order) } }))
    }

    private fun fromJson(o: JSONObject): AlarmPersistentConfig {
        val days = mutableSetOf<Int>(); val d = o.optJSONArray("repeatDays") ?: JSONArray()
        for (i in 0 until d.length()) days += d.optInt(i)
        val missions = mutableListOf<AlarmMission>(); val m = o.optJSONArray("missions") ?: JSONArray()
        for (i in 0 until m.length()) { val x = m.getJSONObject(i); missions += AlarmMission(MissionType.valueOf(x.optString("type", "NONE")), x.optString("label"), x.optInt("target", 1), x.optString("payload"), x.optInt("order", i)) }
        return AlarmPersistentConfig(o.optLong("id"), o.optInt("hour"), o.optInt("minute"), o.optString("label", "Alarm"), o.optBoolean("enabled", true), days, o.optString("soundUri").takeIf { it.isNotEmpty() }, o.optBoolean("vibration", true), o.optBoolean("fullscreen", true), o.optInt("snoozeMinutes", 5), o.optInt("maxSnoozes", 3), missions.ifEmpty { AlarmMissionCatalog.recommended() }, o.optBoolean("backup", false), o.optInt("backupDelay", 5), o.optInt("wakeCheck", 0), o.optInt("bedtime", 0), o.optInt("timeout", 20), o.optInt("gentle", 0), o.optLong("longPress", 1200)).validated()
    }
}
