package com.raunak.daytimeline.alarm

data class AlarmEditorModel(
    val id: Long = System.currentTimeMillis(), val hour: Int = 7, val minute: Int = 0,
    val label: String = "Wake up", val enabled: Boolean = true, val repeatDays: Set<Int> = setOf(2,3,4,5,6),
    val vibration: Boolean = true, val fullscreen: Boolean = true, val snoozeMinutes: Int = 5, val maxSnoozes: Int = 3,
    val backupEnabled: Boolean = false, val backupDelayMinutes: Int = 5, val wakeCheckMinutes: Int = 0,
    val bedtimeReminderMinutes: Int = 0, val timeoutMinutes: Int = 20, val gentleVolumeSeconds: Int = 0,
    val longPressMs: Long = 1200, val missions: List<AlarmMission> = listOf(AlarmMissionCatalog.default(AlarmMissionType.MATH))
) {
    fun validate(): List<String> = buildList {
        if (hour !in 0..23) add("Hour must be 0–23"); if (minute !in 0..59) add("Minute must be 0–59"); if (label.isBlank()) add("Alarm name cannot be empty")
        if (snoozeMinutes !in 1..60) add("Snooze must be 1–60 minutes"); if (maxSnoozes !in 0..20) add("Snooze limit must be 0–20")
        if (backupEnabled && backupDelayMinutes !in 1..60) add("Backup delay must be 1–60 minutes"); if (wakeCheckMinutes !in 0..180) add("Wake check must be 0–180 minutes")
        if (bedtimeReminderMinutes !in 0..720) add("Bedtime reminder must be 0–720 minutes"); if (timeoutMinutes !in 1..120) add("Alarm timeout must be 1–120 minutes")
        if (gentleVolumeSeconds !in 0..300) add("Gentle volume must be 0–300 seconds"); if (longPressMs !in 500..5000) add("Long press must be 0.5–5 seconds")
        if (repeatDays.any { it !in 1..7 }) add("Invalid repeat day"); if (missions.size > 10) add("A mission chain can contain at most 10 missions")
    }
    fun toPersistent(): AlarmPersistentConfig { val e = validate(); require(e.isEmpty()) { e.joinToString("; ") }; return AlarmPersistentConfig(id,hour,minute,label.trim(),enabled,repeatDays,null,vibration,fullscreen,snoozeMinutes,maxSnoozes,missions,backupEnabled,backupDelayMinutes,wakeCheckMinutes,bedtimeReminderMinutes,timeoutMinutes,gentleVolumeSeconds,longPressMs).validated() }
}

object AlarmMissionBuilder {
    fun add(existing: List<AlarmMission>, type: AlarmMissionType): List<AlarmMission> = (existing + AlarmMissionCatalog.default(type)).take(10)
    fun remove(existing: List<AlarmMission>, index: Int): List<AlarmMission> = existing.filterIndexed { i, _ -> i != index }
    fun move(existing: List<AlarmMission>, from: Int, to: Int): List<AlarmMission> { if (from !in existing.indices || to !in existing.indices) return existing; val m = existing.toMutableList(); val item = m.removeAt(from); m.add(to,item); return m }
}
