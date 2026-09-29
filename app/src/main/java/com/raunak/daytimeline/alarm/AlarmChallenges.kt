package com.raunak.daytimeline.alarm

import android.content.Context
import kotlin.random.Random

/** Alarmy-style challenge generation, tuned by difficulty 1 (very easy) … 5 (very hard). */
object AlarmChallenges {
    val difficultyNames = listOf("Very easy", "Easy", "Normal", "Hard", "Very hard")
    fun difficultyName(d: Int) = difficultyNames[(d - 1).coerceIn(0, 4)]

    data class MathProblem(val text: String, val answer: Int)

    /**
     * 1: 3 + 8 · 2: 27 + 46 · 3: 43 + 78 + 16 · 4: 14 × 7 + 38 · 5: 23 × 17 + 245
     * (answers are always positive whole numbers).
     */
    fun math(difficulty: Int, random: Random = Random): MathProblem {
        fun r(a: Int, b: Int) = random.nextInt(a, b + 1)
        return when (difficulty.coerceIn(1, 5)) {
            1 -> { val a = r(2, 9); val b = r(2, 9); MathProblem("$a + $b", a + b) }
            2 -> { val a = r(11, 49); val b = r(11, 49); MathProblem("$a + $b", a + b) }
            3 -> { val a = r(21, 89); val b = r(21, 89); val c = r(11, 49); MathProblem("$a + $b + $c", a + b + c) }
            4 -> { val a = r(11, 19); val b = r(3, 9); val c = r(11, 99); MathProblem("$a × $b + $c", a * b + c) }
            else -> { val a = r(13, 39); val b = r(11, 29); val c = r(101, 499); MathProblem("$a × $b + $c", a * b + c) }
        }
    }

    /** Grid side and number of lit tiles for the memory mission. */
    fun memorySize(difficulty: Int): Pair<Int, Int> = when (difficulty.coerceIn(1, 5)) {
        1 -> 3 to 3; 2 -> 3 to 4; 3 -> 4 to 5; 4 -> 4 to 7; else -> 5 to 9
    }

    fun memoryPattern(difficulty: Int, random: Random = Random): Set<Int> {
        val (side, lit) = memorySize(difficulty)
        return (0 until side * side).shuffled(random).take(lit).toSet()
    }

    val defaultPhrases = listOf(
        "I am awake and ready to win today",
        "Discipline beats motivation every single morning",
        "Small steps every day build big results",
        "I get up because my goals are waiting",
        "Today I choose focus over comfort",
        "The best version of me starts right now",
        "Wake up, show up, never give up"
    )

    /** Phrases get longer as difficulty rises. */
    fun phrase(difficulty: Int, phrases: List<String>, random: Random = Random): String {
        val pool = phrases.filter { it.isNotBlank() }.ifEmpty { defaultPhrases }.sortedBy { it.length }
        val third = (pool.size / 3).coerceAtLeast(1)
        val slice = when (difficulty.coerceIn(1, 5)) { 1, 2 -> pool.take(third); 3 -> pool.drop(third).take(third).ifEmpty { pool }; else -> pool.takeLast(third) }
        return slice.random(random)
    }

    /** Typing match allowing case and extra spaces. */
    fun typedOk(input: String, target: String) = input.trim().replace(Regex("\\s+"), " ").equals(target.trim().replace(Regex("\\s+"), " "), ignoreCase = true)

    /** How much of [input] matches [target] from the start (for live colouring). */
    fun typedPrefix(input: String, target: String): Int {
        var i = 0
        while (i < input.length && i < target.length && input[i].equals(target[i], ignoreCase = true)) i++
        return i
    }

    fun defaultTarget(type: AlarmMissionType, difficulty: Int) = AlarmMissionCatalog.default(type, difficulty).target

    /** One-line summary for a mission card, e.g. "Normal · 3 problems". */
    fun summary(m: AlarmMission): String {
        val n = m.target.coerceAtLeast(1)
        val count = when (m.type) {
            AlarmMissionType.MATH -> "$n problem${if (n > 1) "s" else ""}"
            AlarmMissionType.TYPING -> "$n phrase${if (n > 1) "s" else ""}"
            AlarmMissionType.MEMORY -> "$n round${if (n > 1) "s" else ""}"
            AlarmMissionType.SHAKE -> "$n shakes"
            AlarmMissionType.SQUAT -> "$n squats"
            AlarmMissionType.WALK -> "$n steps"
            AlarmMissionType.TAP -> "$n taps"
            AlarmMissionType.PHOTO -> "match your photo"
            AlarmMissionType.BARCODE -> "scan your code"
            AlarmMissionType.MULTI -> ""
        }
        return if (m.type in setOf(AlarmMissionType.MATH, AlarmMissionType.MEMORY, AlarmMissionType.TYPING)) "${difficultyName(m.difficulty)} · $count" else count
    }
}

/** App-wide alarm preferences (not per alarm). */
class AlarmPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_alarm_prefs", Context.MODE_PRIVATE)
    var phrases: List<String>
        get() = prefs.getString("phrases", null)?.split('\n')?.filter { it.isNotBlank() } ?: AlarmChallenges.defaultPhrases
        set(v) = prefs.edit().putString("phrases", v.joinToString("\n")).apply()
    /** While doing a mission the alarm goes quiet; this many idle seconds brings it back to full volume. */
    var idleSeconds: Int
        get() = prefs.getInt("idle", 20)
        set(v) = prefs.edit().putInt("idle", v.coerceIn(5, 120)).apply()
    /** Bring the alarm back if you leave it mid-mission. */
    var keepOnTop: Boolean
        get() = prefs.getBoolean("keepOnTop", true)
        set(v) = prefs.edit().putBoolean("keepOnTop", v).apply()
}
