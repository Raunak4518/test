package com.raunak.daytimeline.alarm

import kotlin.math.max

/** Pure ringing-alarm state machine. Android UI/sensors drive it; state remains deterministic and testable. */
class AlarmAlarmFlow(
    private val missions: List<AlarmMission>,
    private val policy: AlarmMissionPolicy = AlarmMissionPolicy().validated()
) {
    private var index = 0
    private var progress = 0
    private var snoozes = 0
    private var dismissed = false

    fun currentMission(): AlarmMission? = missions.getOrNull(index)
    fun missionIndex(): Int = index
    fun progress(): Int = progress
    fun snoozesUsed(): Int = snoozes
    fun canSnooze(): Boolean = snoozes < policy.maxSnoozes && !dismissed
    fun snooze(): Boolean { if (!canSnooze()) return false; snoozes++; return true }
    fun recordProgress(value: Int = 1): Boolean {
        if (dismissed) return false
        progress = max(progress, value)
        val mission = currentMission() ?: return dismiss()
        if (mission.type == MissionType.NONE || progress >= mission.target) {
            index++
            progress = 0
            if (index >= missions.size) return dismiss()
        }
        return false
    }
    fun verifyText(input: String): Boolean {
        val mission = currentMission() ?: return false
        val expected = mission.payload.trim().lowercase()
        if (mission.type != MissionType.TYPING || input.trim().lowercase() != expected) return false
        return recordProgress(1)
    }
    fun advanceAfterCorrectMath(): Boolean = if (currentMission()?.type == MissionType.MATH) recordProgress(1) else false
    fun dismiss(): Boolean { dismissed = true; return true }
    fun isDismissed(): Boolean = dismissed
}
