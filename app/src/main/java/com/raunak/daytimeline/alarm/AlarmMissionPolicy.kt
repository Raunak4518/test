package com.raunak.daytimeline.alarm

import kotlin.math.max

/** Deterministic safety and anti-accidental-dismissal policy for local alarm missions. */
data class AlarmMissionPolicy(
    val maxSnoozes: Int = 3,
    val snoozeMinutes: Int = 10,
    val requireLongPressMs: Long = 1200,
    val timeoutMinutes: Int = 20,
    val backupDelayMinutes: Int = 5,
    val shakeTarget: Int = 30,
    val stepTarget: Int = 40,
) {
    fun validated(): AlarmMissionPolicy = copy(
        maxSnoozes = max(0, maxSnoozes.coerceAtMost(20)),
        snoozeMinutes = snoozeMinutes.coerceIn(1, 60),
        requireLongPressMs = requireLongPressMs.coerceIn(500, 5000),
        timeoutMinutes = timeoutMinutes.coerceIn(1, 120),
        backupDelayMinutes = backupDelayMinutes.coerceIn(1, 60),
        shakeTarget = shakeTarget.coerceIn(5, 500),
        stepTarget = stepTarget.coerceIn(1, 5000)
    )
}

enum class MissionType { NONE, MATH, TYPING, MEMORY, SHAKE, STEPS, PHOTO, BARCODE, QR, SQUATS }

data class AlarmMission(
    val type: MissionType,
    val label: String,
    val target: Int = 1,
    val payload: String = "",
    val order: Int = 0
)

data class AlarmRuntimeState(
    val alarmId: Long,
    val startedAt: Long,
    val snoozesUsed: Int = 0,
    val missionIndex: Int = 0,
    val missionProgress: Int = 0,
    val dismissed: Boolean = false,
    val backupScheduled: Boolean = false
)

object AlarmMissionCatalog {
    fun recommended(): List<AlarmMission> = listOf(
        AlarmMission(MissionType.MATH, "Solve a quick equation", target = 2, order = 0),
        AlarmMission(MissionType.TYPING, "Type the displayed phrase", payload = "WAKE UP", order = 1),
        AlarmMission(MissionType.SHAKE, "Shake the phone", target = 30, order = 2)
    )

    fun single(type: MissionType): AlarmMission = when (type) {
        MissionType.MATH -> AlarmMission(type, "Solve a math problem", 2)
        MissionType.TYPING -> AlarmMission(type, "Type the phrase", 1, "WAKE UP")
        MissionType.MEMORY -> AlarmMission(type, "Repeat the memory sequence", 4, "2,7,4,9")
        MissionType.SHAKE -> AlarmMission(type, "Shake", 30)
        MissionType.STEPS -> AlarmMission(type, "Walk", 40)
        MissionType.SQUATS -> AlarmMission(type, "Complete squats", 15)
        MissionType.PHOTO -> AlarmMission(type, "Take the required photo")
        MissionType.BARCODE -> AlarmMission(type, "Scan your saved barcode")
        MissionType.QR -> AlarmMission(type, "Scan your saved QR code")
        MissionType.NONE -> AlarmMission(type, "Long press to dismiss")
    }
}
