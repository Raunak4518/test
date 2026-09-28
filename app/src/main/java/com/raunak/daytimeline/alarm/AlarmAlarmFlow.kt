package com.raunak.daytimeline.alarm

import kotlin.math.max

/** Pure state machine for a ringing alarm; UI and Android sensors can drive it. */
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
        val expected = currentMission()?.payload?.trim()?.lowercase() ?: return false
        return input.trim().lowercase() == expected && recordProgress(1)
    }
    fun verifyMath(input: Int): Boolean = currentMission()?.type == MissionType.MATH && input == currentMission()?.target && recordProgress(1)
    fun dismiss(): Boolean { dismissed = true; return true }
    fun isDismissed(): Boolean = dismissed
}
