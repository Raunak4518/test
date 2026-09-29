package com.raunak.daytimeline.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AlarmPersistentConfig(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String = "Alarm",
    val enabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(),
    val soundUri: String? = null,
    val vibration: Boolean = true,
    val fullscreen: Boolean = true,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    val missionChain: List<AlarmMission> = listOf(AlarmMissionCatalog.default(AlarmMissionType.MATH)),
    val backupAlarmEnabled: Boolean = false,
    val backupDelayMinutes: Int = 5,
    val wakeCheckMinutes: Int = 0,
    val bedtimeReminderMinutes: Int = 0,
    val timeoutMinutes: Int = 20,
    val gentleVolumeSeconds: Int = 0,
    val longPressMs: Long = 1200,
    val scheduleMode: String = AlarmScheduleMode.WEEKLY.name,
    val intervalDays: Int = 1,
    val anchorDate: String? = null,
    val snoozeMaxTotalMinutes: Int = 0,
    val snoozeHalveEachTime: Boolean = false,
    val snoozeAllowAfterScheduled: Boolean = true,
    val snoozeOptionsMinutes: List<Int> = listOf(5, 10, 15),
    val wakeCheckRetries: Int = 2,
    val wakeCheckRetryDelayMinutes: Int = 5,
    val wakeCheckConfirmationWindowMinutes: Int = 5,
    val deleteAfterRinging: Boolean = false
) {
    fun advancedRepeat(): AdvancedRepeatRule {
        val mode = runCatching { AlarmScheduleMode.valueOf(scheduleMode) }.getOrDefault(AlarmScheduleMode.WEEKLY)
        val weekdays = repeatDays.mapNotNull { day ->
            when (day) {
                1 -> java.time.DayOfWeek.SUNDAY
                2 -> java.time.DayOfWeek.MONDAY
                3 -> java.time.DayOfWeek.TUESDAY
                4 -> java.time.DayOfWeek.WEDNESDAY
                5 -> java.time.DayOfWeek.THURSDAY
                6 -> java.time.DayOfWeek.FRIDAY
                7 -> java.time.DayOfWeek.SATURDAY
                else -> null
            }
        }.toSet()
        return AdvancedRepeatRule(mode, weekdays.ifEmpty {
            setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY)
        }, intervalDays, anchorDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }).normalized()
    }

    fun snoozePolicy() = SnoozePolicy(
        durationMinutes = snoozeMinutes,
        maxCount = maxSnoozes,
        maxTotalMinutes = snoozeMaxTotalMinutes,
        halveEachTime = snoozeHalveEachTime,
        allowAfterScheduledTime = snoozeAllowAfterScheduled,
        optionsMinutes = snoozeOptionsMinutes
    ).normalized()

    fun wakeCheckPolicy() = WakeCheckPolicy(
        enabled = wakeCheckMinutes > 0,
        checkAfterMinutes = wakeCheckMinutes.coerceAtLeast(1),
        confirmationWindowMinutes = wakeCheckConfirmationWindowMinutes,
        maxRetries = wakeCheckRetries,
        retryDelayMinutes = wakeCheckRetryDelayMinutes
    ).normalized()

    fun isRepeating(): Boolean = AlarmSchedulePlanner.isRepeating(this)

    fun validated() = copy(
        hour = hour.coerceIn(0, 23),
        minute = minute.coerceIn(0, 59),
        snoozeMinutes = snoozeMinutes.coerceIn(1, 60),
        maxSnoozes = maxSnoozes.coerceIn(0, AlarmSafetyPolicy.MAX_SNOOZES),
        backupDelayMinutes = backupDelayMinutes.coerceIn(1, 60),
        wakeCheckMinutes = wakeCheckMinutes.coerceIn(0, 180),
        bedtimeReminderMinutes = bedtimeReminderMinutes.coerceIn(0, 720),
        timeoutMinutes = timeoutMinutes.coerceIn(1, 120),
        gentleVolumeSeconds = gentleVolumeSeconds.coerceIn(0, 300),
        longPressMs = longPressMs.coerceIn(500, 5000),
        repeatDays = repeatDays.filter { it in 1..7 }.toSet(),
        missionChain = missionChain.take(10).ifEmpty { listOf(AlarmMissionCatalog.default(AlarmMissionType.MATH)) },
        scheduleMode = runCatching { AlarmScheduleMode.valueOf(scheduleMode) }.getOrDefault(AlarmScheduleMode.WEEKLY).name,
        intervalDays = intervalDays.coerceIn(1, 16),
        snoozeMaxTotalMinutes = snoozeMaxTotalMinutes.coerceIn(0, 240),
        snoozeOptionsMinutes = snoozeOptionsMinutes.filter { it in 1..60 }.distinct().sorted().take(8).ifEmpty { listOf(snoozeMinutes.coerceIn(1, 60)) },
        wakeCheckRetries = wakeCheckRetries.coerceIn(0, AlarmSafetyPolicy.MAX_WAKE_CHECK_RETRIES),
        wakeCheckRetryDelayMinutes = wakeCheckRetryDelayMinutes.coerceIn(1, 60),
        wakeCheckConfirmationWindowMinutes = wakeCheckConfirmationWindowMinutes.coerceIn(1, 30)
    )

    fun scheduleLabel(): String = when (advancedRepeat().mode) {
        AlarmScheduleMode.WEEKLY -> if (repeatDays.isEmpty()) "One-time" else "Weekly"
        AlarmScheduleMode.ODD_WEEKS -> "Odd weeks"
        AlarmScheduleMode.EVEN_WEEKS -> "Even weeks"
        AlarmScheduleMode.EVERY_N_DAYS -> "Every $intervalDays day(s)"
        AlarmScheduleMode.ONE_SHOT -> "One-time"
        AlarmScheduleMode.NAP -> "Nap"
        AlarmScheduleMode.POWER_NAP -> "Power nap"
    }
}

class AlarmPersistentStore(context: Context) {
    private val prefs = context.getSharedPreferences("offline_alarms", Context.MODE_PRIVATE)

    fun all(): List<AlarmPersistentConfig> =
        runCatching {
            val a = JSONArray(prefs.getString("alarms", "[]"))
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())

    fun save(config: AlarmPersistentConfig) {
        val items = all().filterNot { it.id == config.id } + config.validated()
        saveAll(items)
    }

    fun delete(id: Long) = saveAll(all().filterNot { it.id == id })
    fun find(id: Long): AlarmPersistentConfig? = all().firstOrNull { it.id == id }

    private fun saveAll(items: List<AlarmPersistentConfig>) {
        prefs.edit().putString("alarms", JSONArray().apply { items.forEach { put(toJson(it)) } }.toString()).apply()
    }

    private fun toJson(a: AlarmPersistentConfig) = JSONObject().apply {
        put("id", a.id); put("hour", a.hour); put("minute", a.minute); put("label", a.label); put("enabled", a.enabled)
        put("repeatDays", JSONArray(a.repeatDays.toList())); put("soundUri", a.soundUri); put("vibration", a.vibration); put("fullscreen", a.fullscreen)
        put("snoozeMinutes", a.snoozeMinutes); put("maxSnoozes", a.maxSnoozes); put("backup", a.backupAlarmEnabled); put("backupDelay", a.backupDelayMinutes)
        put("wakeCheck", a.wakeCheckMinutes); put("bedtime", a.bedtimeReminderMinutes); put("timeout", a.timeoutMinutes); put("gentle", a.gentleVolumeSeconds); put("longPress", a.longPressMs)
        put("scheduleMode", a.scheduleMode); put("intervalDays", a.intervalDays); put("anchorDate", a.anchorDate)
        put("snoozeMaxTotal", a.snoozeMaxTotalMinutes); put("snoozeHalve", a.snoozeHalveEachTime); put("snoozeAfterScheduled", a.snoozeAllowAfterScheduled)
        put("snoozeOptions", JSONArray(a.snoozeOptionsMinutes)); put("wakeRetries", a.wakeCheckRetries); put("wakeRetryDelay", a.wakeCheckRetryDelayMinutes)
        put("wakeWindow", a.wakeCheckConfirmationWindowMinutes); put("deleteAfterRinging", a.deleteAfterRinging)
        put("missions", JSONArray(a.missionChain.map { JSONObject().apply { put("type", it.type.name); put("difficulty", it.difficulty); put("target", it.target); put("payload", it.payload) } }))
    }

    private fun fromJson(o: JSONObject): AlarmPersistentConfig {
        val days = mutableSetOf<Int>()
        val d = o.optJSONArray("repeatDays") ?: JSONArray()
        for (i in 0 until d.length()) days += d.optInt(i)

        val options = mutableListOf<Int>()
        val so = o.optJSONArray("snoozeOptions") ?: JSONArray()
        for (i in 0 until so.length()) options += so.optInt(i)

        val missions = mutableListOf<AlarmMission>()
        val m = o.optJSONArray("missions") ?: JSONArray()
        for (i in 0 until m.length()) {
            val x = m.getJSONObject(i)
            val type = runCatching { AlarmMissionType.valueOf(x.optString("type")) }.getOrDefault(AlarmMissionType.MATH)
            missions += AlarmMission(type, x.optInt("difficulty", 2), x.optInt("target", 0), x.optString("payload"))
        }

        return AlarmPersistentConfig(
            id = o.optLong("id"),
            hour = o.optInt("hour"),
            minute = o.optInt("minute"),
            label = o.optString("label", "Alarm"),
            enabled = o.optBoolean("enabled", true),
            repeatDays = days,
            soundUri = o.optString("soundUri").takeIf { it.isNotEmpty() },
            vibration = o.optBoolean("vibration", true),
            fullscreen = o.optBoolean("fullscreen", true),
            snoozeMinutes = o.optInt("snoozeMinutes", 5),
            maxSnoozes = o.optInt("maxSnoozes", 3),
            missionChain = missions.ifEmpty { listOf(AlarmMissionCatalog.default(AlarmMissionType.MATH)) },
            backupAlarmEnabled = o.optBoolean("backup", false),
            backupDelayMinutes = o.optInt("backupDelay", 5),
            wakeCheckMinutes = o.optInt("wakeCheck", 0),
            bedtimeReminderMinutes = o.optInt("bedtime", 0),
            timeoutMinutes = o.optInt("timeout", 20),
            gentleVolumeSeconds = o.optInt("gentle", 0),
            longPressMs = o.optLong("longPress", 1200),
            scheduleMode = o.optString("scheduleMode", AlarmScheduleMode.WEEKLY.name),
            intervalDays = o.optInt("intervalDays", 1),
            anchorDate = o.optString("anchorDate").takeIf { it.isNotBlank() },
            snoozeMaxTotalMinutes = o.optInt("snoozeMaxTotal", 0),
            snoozeHalveEachTime = o.optBoolean("snoozeHalve", false),
            snoozeAllowAfterScheduled = o.optBoolean("snoozeAfterScheduled", true),
            snoozeOptionsMinutes = options.ifEmpty { listOf(5, 10, 15) },
            wakeCheckRetries = o.optInt("wakeRetries", 2),
            wakeCheckRetryDelayMinutes = o.optInt("wakeRetryDelay", 5),
            wakeCheckConfirmationWindowMinutes = o.optInt("wakeWindow", 5),
            deleteAfterRinging = o.optBoolean("deleteAfterRinging", false)
        ).validated()
    }
}
