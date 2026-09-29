package com.raunak.daytimeline.domain

import com.raunak.daytimeline.data.PomodoroStateEntity

/**
 * Pomodoro and Flowtime state machine.
 *
 * Pomodoro phases count down: FOCUS → SHORT_BREAK/LONG_BREAK → FOCUS. When auto-start is off for the
 * next phase, the timer stops at the phase's full length and waits for a tap ("ready" state).
 *
 * FLOW counts up from [PomodoroStateEntity.targetEpochMillis] (the start time); stopping it starts a
 * break proportional to the time focused. While FLOW is paused, [PomodoroStateEntity.remainingSeconds]
 * holds the elapsed seconds.
 */
object PomodoroEngine {
    const val FLOW = "FLOW"

    fun start(taskId: Long?, current: PomodoroStateEntity, now: Long = System.currentTimeMillis()): PomodoroStateEntity {
        val seconds = current.focusMinutes * 60L
        return current.copy(
            taskId = taskId,
            phase = "FOCUS",
            remainingSeconds = seconds,
            targetEpochMillis = now + seconds * 1000,
            running = true,
            cycleIndex = 1
        )
    }

    /** Starts a count-up Flowtime session. */
    fun startFlow(taskId: Long?, current: PomodoroStateEntity, now: Long = System.currentTimeMillis()): PomodoroStateEntity =
        current.copy(taskId = taskId, phase = FLOW, targetEpochMillis = now, remainingSeconds = 0, running = true, cycleIndex = current.cycleIndex.coerceAtLeast(1))

    /** Seconds focused so far in a FLOW session. */
    fun flowElapsed(current: PomodoroStateEntity, now: Long = System.currentTimeMillis()): Long =
        if (current.phase != FLOW) 0 else if (current.running) ((now - current.targetEpochMillis) / 1000).coerceAtLeast(0) else current.remainingSeconds

    /** Break length (minutes) a FLOW session earns: focused time ÷ [divisor], at least one minute. */
    fun flowBreakMinutes(elapsedSeconds: Long, divisor: Int) = (elapsedSeconds / 60 / divisor.coerceAtLeast(1)).toInt().coerceAtLeast(1)

    /** Ends a FLOW session: returns the break state and the minutes focused. */
    fun stopFlow(current: PomodoroStateEntity, now: Long, divisor: Int, autoBreak: Boolean = true): Pair<PomodoroStateEntity, Int> {
        val elapsed = flowElapsed(current, now)
        val breakMin = flowBreakMinutes(elapsed, divisor)
        val next = current.copy(
            phase = "SHORT_BREAK",
            remainingSeconds = breakMin * 60L,
            targetEpochMillis = if (autoBreak) now + breakMin * 60_000L else 0,
            running = autoBreak
        )
        return next to (elapsed / 60).toInt()
    }

    /** True when a phase has ended and is waiting for a tap to start the next one. */
    fun waiting(current: PomodoroStateEntity) = current.phase != "IDLE" && current.phase != FLOW && !current.running && current.targetEpochMillis == 0L

    fun pause(current: PomodoroStateEntity, now: Long = System.currentTimeMillis()): PomodoroStateEntity {
        if (current.phase == FLOW) return current.copy(running = false, remainingSeconds = flowElapsed(current, now))
        val rem = ((current.targetEpochMillis - now) / 1000L).coerceAtLeast(0)
        return current.copy(running = false, remainingSeconds = rem)
    }

    fun resume(current: PomodoroStateEntity, now: Long = System.currentTimeMillis()): PomodoroStateEntity {
        if (current.phase == FLOW) return current.copy(running = true, targetEpochMillis = now - current.remainingSeconds * 1000)
        val rem = current.remainingSeconds.coerceAtLeast(0)
        return current.copy(running = true, targetEpochMillis = now + rem * 1000)
    }

    fun reset(current: PomodoroStateEntity): PomodoroStateEntity = current.copy(
        phase = "IDLE",
        targetEpochMillis = 0,
        remainingSeconds = 0,
        running = false,
        cycleIndex = 0,
        taskId = null
    )

    /** Ends the current phase immediately and moves to the next one (focus → break → focus). */
    fun skip(current: PomodoroStateEntity, now: Long = System.currentTimeMillis(), autoBreak: Boolean = true, autoFocus: Boolean = true): PomodoroStateEntity {
        if (current.phase == "IDLE" || current.phase == FLOW) return current
        val advanced = tick(current.copy(running = true, targetEpochMillis = now), now, autoBreak, autoFocus)
        return if (current.running || advanced.targetEpochMillis == 0L) advanced else pause(advanced, now)
    }

    /** Adds time to the current phase, whether running or paused. */
    fun extend(current: PomodoroStateEntity, minutes: Int, now: Long = System.currentTimeMillis()): PomodoroStateEntity {
        if (current.phase == "IDLE" || current.phase == FLOW) return current
        val extra = minutes * 60L
        return if (current.running) {
            val target = maxOf(current.targetEpochMillis, now) + extra * 1000
            current.copy(targetEpochMillis = target, remainingSeconds = (target - now) / 1000)
        } else current.copy(remainingSeconds = current.remainingSeconds + extra)
    }

    fun tick(current: PomodoroStateEntity, now: Long = System.currentTimeMillis(), autoBreak: Boolean = true, autoFocus: Boolean = true): PomodoroStateEntity {
        if (!current.running) return current
        if (current.phase == FLOW) return current.copy(remainingSeconds = flowElapsed(current, now))
        val rem = ((current.targetEpochMillis - now) / 1000L)
        if (rem > 0) return current.copy(remainingSeconds = rem)
        return when (current.phase) {
            "FOCUS" -> {
                val longBreak = current.cycleIndex >= current.cyclesPerRound
                val nextMin = if (longBreak) current.longBreakMinutes else current.shortBreakMinutes
                current.copy(
                    phase = if (longBreak) "LONG_BREAK" else "SHORT_BREAK",
                    remainingSeconds = nextMin * 60L,
                    targetEpochMillis = if (autoBreak) now + nextMin * 60_000L else 0,
                    running = autoBreak
                )
            }
            "SHORT_BREAK", "LONG_BREAK" -> {
                val nextCycle = if (current.phase == "LONG_BREAK") 1 else current.cycleIndex + 1
                val mins = current.focusMinutes
                current.copy(
                    phase = "FOCUS",
                    cycleIndex = nextCycle,
                    remainingSeconds = mins * 60L,
                    targetEpochMillis = if (autoFocus) now + mins * 60_000L else 0,
                    running = autoFocus
                )
            }
            else -> current
        }
    }
}
