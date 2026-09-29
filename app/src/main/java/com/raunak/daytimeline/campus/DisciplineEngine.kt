package com.raunak.daytimeline.campus

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class UrgeLog(val time: Long, val intensity: Int, val trigger: String, val resisted: Boolean, val note: String = "")
data class ResetLog(val time: Long, val trigger: String, val lesson: String)

/** Private habit-breaking tracker. Kept separate from other data and never exported with backups. */
data class DisciplineState(
    val started: Long = 0,
    val streakStart: Long = 0,
    val resets: List<ResetLog> = emptyList(),
    val urges: List<UrgeLog> = emptyList(),
    val reasons: List<String> = emptyList(),
    val riskStart: Int = 23 * 60,
    val riskEnd: Int = 2 * 60,
    val riskGuard: Boolean = true,
    val checkIns: Map<String, Boolean> = emptyMap(),
    val dailyCheckIn: Boolean = true
)

data class DisciplineInsights(
    val currentDays: Int,
    val currentHours: Long,
    val bestDays: Int,
    val nextMilestone: Int,
    val urgesByHour: IntArray,
    val topTriggers: List<Pair<String, Int>>,
    val resistRate: Int?,
    val riskiestHours: List<Int>,
    val cleanDaysThisMonth: Int
)

object DisciplineEngine {
    val milestones = listOf(1, 3, 7, 14, 21, 30, 45, 60, 90, 120, 180, 270, 365)
    val triggers = listOf("Bored", "Alone late at night", "Stressed", "Lonely", "Tired", "Scrolling social media", "In bed with phone", "After a bad day", "Procrastinating")

    fun insights(s: DisciplineState, now: Long, zone: ZoneId = ZoneId.systemDefault()): DisciplineInsights {
        val start = if (s.streakStart > 0) s.streakStart else now
        val hours = ((now - start) / 3_600_000L).coerceAtLeast(0)
        val days = (hours / 24).toInt()
        // Best streak: gaps between consecutive resets, plus the current run.
        val points = (listOf(s.started.takeIf { it > 0 } ?: start) + s.resets.map { it.time }.sorted() + now)
        val best = points.zipWithNext { a, b -> ((b - a) / 86_400_000L).toInt() }.maxOrNull() ?: days
        val byHour = IntArray(24)
        s.urges.forEach { byHour[Instant.ofEpochMilli(it.time).atZone(zone).hour]++ }
        s.resets.forEach { byHour[Instant.ofEpochMilli(it.time).atZone(zone).hour] += 2 }
        val triggers = (s.urges.map { it.trigger } + s.resets.map { it.trigger }).filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
        val resisted = s.urges.count { it.resisted }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val monthStart = today.withDayOfMonth(1)
        val resetDays = s.resets.map { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }.toSet()
        val tracked = maxOf(monthStart, Instant.ofEpochMilli(s.started.takeIf { it > 0 } ?: now).atZone(zone).toLocalDate())
        val clean = generateSequence(tracked) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.count { it !in resetDays }
        return DisciplineInsights(
            currentDays = days,
            currentHours = hours,
            bestDays = maxOf(best, days),
            nextMilestone = milestones.firstOrNull { it > days } ?: (days + 30),
            urgesByHour = byHour,
            topTriggers = triggers.take(4),
            resistRate = if (s.urges.isEmpty()) null else 100 * resisted / s.urges.size,
            riskiestHours = byHour.withIndex().filter { it.value > 0 }.sortedByDescending { it.value }.take(3).map { it.index },
            cleanDaysThisMonth = clean
        )
    }

    fun reset(s: DisciplineState, now: Long, trigger: String, lesson: String) =
        s.copy(streakStart = now, resets = s.resets + ResetLog(now, trigger, lesson))

    fun inRiskWindow(s: DisciplineState, minute: Int): Boolean =
        if (s.riskStart <= s.riskEnd) minute in s.riskStart until s.riskEnd else minute >= s.riskStart || minute < s.riskEnd

    fun keptToday(s: DisciplineState, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean? {
        if (s.started == 0L) return null
        return s.resets.none { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() == today }
    }

    val copingActions = listOf(
        "Stand up and leave the room right now",
        "Splash cold water on your face",
        "20 push-ups or squats",
        "Go to the library or a common area — be around people",
        "Open your next DSA problem",
        "Text or call a friend",
        "Put the phone in another room for 15 minutes",
        "Walk outside for 10 minutes"
    )
}
