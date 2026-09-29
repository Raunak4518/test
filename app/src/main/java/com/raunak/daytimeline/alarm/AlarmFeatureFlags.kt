package com.raunak.daytimeline.alarm

/** Feature capability matrix for the local alarm editor. */
data class AlarmFeatureFlags(
    val smartSnooze: Boolean = true,
    val maximumTotalSnooze: Boolean = true,
    val skipNext: Boolean = true,
    val wakeCheck: Boolean = true,
    val backupAlarm: Boolean = true,
    val naps: Boolean = true,
    val powerNap: Boolean = true,
    val oddEvenWeeks: Boolean = true,
    val intervalDays: Boolean = true,
    val missionChains: Boolean = true,
    val gentleVolume: Boolean = true,
    val vibrationDelay: Boolean = true,
    val perAlarmDifficulty: Boolean = true,
    val deleteAfterRinging: Boolean = true
)
