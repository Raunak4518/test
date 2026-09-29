package com.raunak.daytimeline.alarm

data class AlarmEditorModel(
    val id: Long = System.currentTimeMillis(),
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "Wake up",
    val enabled: Boolean = true,
    val repeatDays: Set<Int> = setOf(2, 3, 4, 5, 6),
    val vibration: Boolean = true,
    val fullscreen: Boolean = true,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    val backupEnabled: Boolean = false,
    val backupDelayMinutes: Int = 5,
    val wakeCheckMinutes: Int = 0,
    val bedtimeReminderMinutes: Int = 0,
    val timeoutMinutes: Int = 20,
    val gentleVolumeSeconds: Int = 0,
    val longPressMs: Long = 1200,
    val missions: List<AlarmMission> = listOf(AlarmMissionCatalog.default(AlarmMissionType.MATH)),
    val scheduleMode: AlarmScheduleMode = AlarmScheduleMode.WEEKLY,
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
    fun validate(): List<String> = buildList {
        if (hour !in 0..23) add("Hour must be 0–23")
        if (minute !in 0..59) add("Minute must be 0–59")
        if (label.isBlank()) add("Alarm name cannot be empty")
        if (snoozeMinutes !in 1..60) add("Snooze must be 1–60 minutes")
        if (maxSnoozes !in 0..20) add("Snooze limit must be 0–20")
        if (backupEnabled && backupDelayMinutes !in 1..60) add("Backup delay must be 1–60 minutes")
        if (wakeCheckMinutes !in 0..180) add("Wake check must be 0–180 minutes")
        if (bedtimeReminderMinutes !in 0..720) add("Bedtime reminder must be 0–720 minutes")
        if (timeoutMinutes !in 1..120) add("Alarm timeout must be 1–120 minutes")
        if (gentleVolumeSeconds !in 0..300) add("Gentle volume must be 0–300 seconds")
        if (longPressMs !in 500..5000) add("Long press must be 0.5–5 seconds")
        if (repeatDays.any { it !in 1..7 }) add("Invalid repeat day")
        if (missions.size > 10) add("A mission chain can contain at most 10 missions")
        if (intervalDays !in 1..16) add("Interval must be 1–16 days")
        if (snoozeMaxTotalMinutes !in 0..240) add("Maximum total snooze must be 0–240 minutes")
        if (snoozeOptionsMinutes.any { it !in 1..60 }) add("Snooze options must be 1–60 minutes")
        if (wakeCheckRetries !in 0..10) add("Wake-check retries must be 0–10")
        if (wakeCheckRetryDelayMinutes !in 1..60) add("Wake-check retry delay must be 1–60 minutes")
        if (wakeCheckConfirmationWindowMinutes !in 1..30) add("Wake-check window must be 1–30 minutes")
    }

    fun toPersistent(): AlarmPersistentConfig {
        val errors = validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return AlarmPersistentConfig(
            id = id,
            hour = hour,
            minute = minute,
            label = label.trim(),
            enabled = enabled,
            repeatDays = repeatDays,
            soundUri = null,
            vibration = vibration,
            fullscreen = fullscreen,
            snoozeMinutes = snoozeMinutes,
            maxSnoozes = maxSnoozes,
            missionChain = missions,
            backupAlarmEnabled = backupEnabled,
            backupDelayMinutes = backupDelayMinutes,
            wakeCheckMinutes = wakeCheckMinutes,
            bedtimeReminderMinutes = bedtimeReminderMinutes,
            timeoutMinutes = timeoutMinutes,
            gentleVolumeSeconds = gentleVolumeSeconds,
            longPressMs = longPressMs,
            scheduleMode = scheduleMode.name,
            intervalDays = intervalDays,
            anchorDate = anchorDate ?: if (scheduleMode == AlarmScheduleMode.EVERY_N_DAYS) java.time.LocalDate.now().toString() else null,
            snoozeMaxTotalMinutes = snoozeMaxTotalMinutes,
            snoozeHalveEachTime = snoozeHalveEachTime,
            snoozeAllowAfterScheduled = snoozeAllowAfterScheduled,
            snoozeOptionsMinutes = snoozeOptionsMinutes,
            wakeCheckRetries = wakeCheckRetries,
            wakeCheckRetryDelayMinutes = wakeCheckRetryDelayMinutes,
            wakeCheckConfirmationWindowMinutes = wakeCheckConfirmationWindowMinutes,
            deleteAfterRinging = deleteAfterRinging
        ).validated()
    }

    companion object {
        fun fromPersistent(a: AlarmPersistentConfig) = AlarmEditorModel(
            id = a.id,
            hour = a.hour,
            minute = a.minute,
            label = a.label,
            enabled = a.enabled,
            repeatDays = a.repeatDays,
            vibration = a.vibration,
            fullscreen = a.fullscreen,
            snoozeMinutes = a.snoozeMinutes,
            maxSnoozes = a.maxSnoozes,
            backupEnabled = a.backupAlarmEnabled,
            backupDelayMinutes = a.backupDelayMinutes,
            wakeCheckMinutes = a.wakeCheckMinutes,
            bedtimeReminderMinutes = a.bedtimeReminderMinutes,
            timeoutMinutes = a.timeoutMinutes,
            gentleVolumeSeconds = a.gentleVolumeSeconds,
            longPressMs = a.longPressMs,
            missions = a.missionChain,
            scheduleMode = a.advancedRepeat().mode,
            intervalDays = a.intervalDays,
            anchorDate = a.anchorDate,
            snoozeMaxTotalMinutes = a.snoozeMaxTotalMinutes,
            snoozeHalveEachTime = a.snoozeHalveEachTime,
            snoozeAllowAfterScheduled = a.snoozeAllowAfterScheduled,
            snoozeOptionsMinutes = a.snoozeOptionsMinutes,
            wakeCheckRetries = a.wakeCheckRetries,
            wakeCheckRetryDelayMinutes = a.wakeCheckRetryDelayMinutes,
            wakeCheckConfirmationWindowMinutes = a.wakeCheckConfirmationWindowMinutes,
            deleteAfterRinging = a.deleteAfterRinging
        )
    }
}

object AlarmMissionBuilder {
    fun add(existing: List<AlarmMission>, type: AlarmMissionType): List<AlarmMission> =
        (existing + AlarmMissionCatalog.default(type)).take(10)

    fun remove(existing: List<AlarmMission>, index: Int): List<AlarmMission> =
        existing.filterIndexed { i, _ -> i != index }

    fun move(existing: List<AlarmMission>, from: Int, to: Int): List<AlarmMission> {
        if (from !in existing.indices || to !in existing.indices) return existing
        val m = existing.toMutableList()
        val item = m.removeAt(from)
        m.add(to, item)
        return m
    }
}
