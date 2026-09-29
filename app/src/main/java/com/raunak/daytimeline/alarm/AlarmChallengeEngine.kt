package com.raunak.daytimeline.alarm

import kotlin.math.abs
import kotlin.random.Random

enum class AlarmMissionType(val title: String, val icon: String, val blurb: String) {
    MATH("Math", "➗", "Solve problems"),
    TYPING("Typing", "⌨️", "Type a phrase exactly"),
    MEMORY("Memory", "🧩", "Repeat a tile pattern"),
    SHAKE("Shake", "📳", "Shake the phone"),
    SQUAT("Squats", "🏋️", "Do squats holding the phone"),
    WALK("Steps", "🚶", "Walk around"),
    TAP("Tap", "🎯", "Tap the moving target"),
    PHOTO("Photo", "📷", "Photograph a place you registered"),
    BARCODE("QR / Barcode", "🔳", "Scan a code you registered"),
    MULTI("Continue", "▶️", "Just continue")
}

data class AlarmMission(
    val type: AlarmMissionType,
    val difficulty: Int = 1,
    val target: Int = 0,
    val payload: String = ""
)

data class AlarmProfile(
    val label: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val repeatDays: Set<Int> = setOf(1,2,3,4,5),
    val soundUri: String? = null,
    val volumeRampSeconds: Int = 0,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 0,
    val timeoutMinutes: Int = 10,
    val wakeCheckMinutes: Int = 0,
    val wakeCheckTimeoutMinutes: Int = 5,
    val backupDelayMinutes: Int = 0,
    val fullscreen: Boolean = true,
    val vibration: Boolean = true,
    val requireLongPressMs: Long = 0,
    val mission: AlarmMission = AlarmMission(AlarmMissionType.MATH),
    val multiMissions: List<AlarmMission> = emptyList()
)

object AlarmChallengeEngine {
    fun math(difficulty: Int): Pair<String, Int> {
        val d = difficulty.coerceIn(1, 5)
        val a = Random.nextInt(5 * d, 25 * d + 1)
        val b = Random.nextInt(2 * d, 15 * d + 1)
        return if (Random.nextBoolean()) "$a + $b" to (a + b) else "$a × $b" to (a * b)
    }

    fun typing(difficulty: Int): String {
        val words = listOf("WAKE UP NOW", "START THE DAY", "FOCUS FIRST", "GET OUT OF BED", "MAKE TODAY COUNT")
        return words[(difficulty - 1).coerceIn(0, words.lastIndex)]
    }

    fun memorySequence(difficulty: Int): List<Int> {
        val n = (3 + difficulty.coerceIn(1, 5)).coerceAtMost(8)
        return List(n) { Random.nextInt(0, 9) }
    }

    fun shakeTarget(difficulty: Int) = 20 + difficulty.coerceIn(1, 5) * 10
    fun walkTarget(difficulty: Int) = 50 + difficulty.coerceIn(1, 5) * 50
    fun squatTarget(difficulty: Int) = 5 + difficulty.coerceIn(1, 5) * 5

    fun validateMath(answer: Int, expected: Int) = answer == expected
    fun validateTyping(input: String, expected: String) = input.trim().equals(expected.trim(), ignoreCase = true)
    fun progress(current: Int, target: Int) = if (target <= 0) 1f else (current.toFloat() / target).coerceIn(0f, 1f)
    fun accelerometerMagnitude(x: Float, y: Float, z: Float) = abs(x) + abs(y) + abs(z)
}
