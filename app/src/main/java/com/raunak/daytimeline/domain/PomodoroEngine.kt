package com.raunak.daytimeline.domain

import com.raunak.daytimeline.data.PomodoroStateEntity

object PomodoroEngine {
    fun start(taskId: Long?, current: PomodoroStateEntity): PomodoroStateEntity {
        val seconds = current.focusMinutes * 60L
        return current.copy(
            taskId = taskId,
            phase = "FOCUS",
            remainingSeconds = seconds,
            targetEpochMillis = System.currentTimeMillis() + seconds * 1000,
            running = true,
            cycleIndex = 1
        )
    }

    fun pause(current: PomodoroStateEntity): PomodoroStateEntity {
        val rem = ((current.targetEpochMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
        return current.copy(running = false, remainingSeconds = rem)
    }

    fun resume(current: PomodoroStateEntity): PomodoroStateEntity {
        val rem = current.remainingSeconds.coerceAtLeast(0)
        return current.copy(running = true, targetEpochMillis = System.currentTimeMillis() + rem * 1000)
    }

    fun reset(current: PomodoroStateEntity): PomodoroStateEntity = current.copy(
        phase = "IDLE",
        targetEpochMillis = 0,
        remainingSeconds = 0,
        running = false,
        cycleIndex = 0,
        taskId = null
    )

    fun tick(current: PomodoroStateEntity, now: Long = System.currentTimeMillis()): PomodoroStateEntity {
        if (!current.running) return current
        val rem = ((current.targetEpochMillis - now) / 1000L)
        if (rem > 0) return current.copy(remainingSeconds = rem)
        return when (current.phase) {
            "FOCUS" -> {
                val longBreak = current.cycleIndex >= current.cyclesPerRound
                val nextMin = if (longBreak) current.longBreakMinutes else current.shortBreakMinutes
                current.copy(
                    phase = if (longBreak) "LONG_BREAK" else "SHORT_BREAK",
                    remainingSeconds = nextMin * 60L,
                    targetEpochMillis = now + nextMin * 60_000L,
                    running = true
                )
            }
            "SHORT_BREAK", "LONG_BREAK" -> {
                val nextCycle = if (current.phase == "LONG_BREAK") 1 else current.cycleIndex + 1
                val mins = current.focusMinutes
                current.copy(
                    phase = "FOCUS",
                    cycleIndex = nextCycle,
                    remainingSeconds = mins * 60L,
                    targetEpochMillis = now + mins * 60_000L,
                    running = true
                )
            }
            else -> current
        }
    }
}
