package com.raunak.daytimeline.alarm

import kotlin.math.abs

object AlarmMissionVerifiers {
    fun verifyMath(answer: String, expected: Int): Boolean = answer.trim().toIntOrNull() == expected
    fun verifyTyping(input: String, expected: String): Boolean = input.trim().equals(expected.trim(), ignoreCase = true)
    fun verifyMemory(input: List<Int>, expected: List<Int>): Boolean = input == expected
    fun verifySquats(count: Int, target: Int): Boolean = count >= target
    fun verifySteps(count: Int, target: Int): Boolean = count >= target
    fun verifyBarcode(value: String, expected: String): Boolean = value.trim() == expected.trim()
    fun verifyQr(value: String, expected: String): Boolean = value.trim() == expected.trim()
    fun photoSimilarity(score: Float, threshold: Float = .85f): Boolean = score >= threshold.coerceIn(.5f, .99f)
    fun movementDelta(previous: FloatArray, current: FloatArray): Float = if (previous.size >= 3 && current.size >= 3) {
        abs(current[0]-previous[0]) + abs(current[1]-previous[1]) + abs(current[2]-previous[2])
    } else 0f
}
