package com.raunak.daytimeline.alarm

import kotlin.random.Random

data class MathChallenge(val expression: String, val answer: Int)

object AlarmChallengeMath {
    fun generate(difficulty: Int = 1): MathChallenge {
        val d = difficulty.coerceIn(1, 3)
        val a = Random.nextInt(2, 10 + d * 5)
        val b = Random.nextInt(2, 10 + d * 5)
        return when (d) {
            1 -> MathChallenge("$a + $b", a + b)
            2 -> if (a >= b) MathChallenge("$a − $b", a - b) else MathChallenge("$a + $b", a + b)
            else -> MathChallenge("$a × $b", a * b)
        }
    }
}
