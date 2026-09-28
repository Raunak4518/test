package com.raunak.daytimeline.alarm

/** Local safety bounds for alarm configuration. Uses the repository's canonical AlarmProfile model. */
data class AlarmMissionPolicy(
    val maxSnoozes: Int = 3,
    val snoozeMinutes: Int = 10,
    val requireLongPressMs: Long = 1200,
    val timeoutMinutes: Int = 20,
    val backupDelayMinutes: Int = 5,
    val shakeTarget: Int = 30,
    val stepTarget: Int = 40
) {
    fun validated(): AlarmMissionPolicy = copy(
        maxSnoozes = maxSnoozes.coerceIn(0, 20),
        snoozeMinutes = snoozeMinutes.coerceIn(1, 60),
        requireLongPressMs = requireLongPressMs.coerceIn(500, 5000),
        timeoutMinutes = timeoutMinutes.coerceIn(1, 120),
        backupDelayMinutes = backupDelayMinutes.coerceIn(1, 60),
        shakeTarget = shakeTarget.coerceIn(5, 500),
        stepTarget = stepTarget.coerceIn(1, 5000)
    )
}

data class AlarmRuntimeState(
    val alarmId: Long,
    val startedAt: Long,
    val snoozesUsed: Int = 0,
    val missionIndex: Int = 0,
    val missionProgress: Int = 0,
    val dismissed: Boolean = false,
    val backupScheduled: Boolean = false
)
