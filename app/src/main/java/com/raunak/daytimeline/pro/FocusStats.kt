package com.raunak.daytimeline.pro

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class FocusReport(
    val minutes: Int,
    val sessions: Int,
    val withered: Int,
    val completionRate: Int,
    val byTag: List<Pair<String, Int>>,
    val byTask: List<Pair<Long, Int>>,
    /** Focus minutes by hour of day (24 values). */
    val byHour: List<Int>,
    val perDay: List<Pair<LocalDate, Int>>,
    val averageRating: Double?,
    val interruptions: Int,
    val longest: Int
) {
    /** The hour with the most focus, or null with no timed sessions. */
    val bestHour: Int? get() = byHour.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index
    val interruptionsPerHour: Double get() = if (minutes == 0) 0.0 else interruptions * 60.0 / minutes
}

/** Focus To-Do / Forest-style reports from the recorded sessions. */
object FocusStats {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun GardenSession.day(): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()

    fun inRange(sessions: List<GardenSession>, from: LocalDate, to: LocalDate) = sessions.filter { s -> s.day()?.let { !it.isBefore(from) && !it.isAfter(to) } == true }

    fun report(sessions: List<GardenSession>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): FocusReport {
        val all = inRange(sessions, from, to)
        val done = all.filter { it.completed }
        val byHour = IntArray(24)
        done.filter { it.startedAt > 0 }.forEach { s ->
            // Spread the minutes over the hours the session covered.
            var t = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDateTime()
            var left = s.minutes
            while (left > 0) {
                val inHour = minOf(left, 60 - t.minute)
                byHour[t.hour] += inHour
                left -= inHour
                t = t.plusMinutes(inHour.toLong())
            }
        }
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        val perDay = days.map { d -> d to done.filter { it.day() == d }.sumOf { it.minutes } }
        val rated = done.filter { it.rating in 1..5 }
        return FocusReport(
            minutes = done.sumOf { it.minutes },
            sessions = done.size,
            withered = all.count { !it.completed },
            completionRate = if (all.isEmpty()) 0 else 100 * done.size / all.size,
            byTag = done.groupBy { it.tag?.takeIf { t -> t.isNotBlank() } ?: "Untagged" }.map { it.key to it.value.sumOf { s -> s.minutes } }.sortedByDescending { it.second },
            byTask = done.filter { it.taskId != null }.groupBy { it.taskId!! }.map { it.key to it.value.sumOf { s -> s.minutes } }.sortedByDescending { it.second },
            byHour = byHour.toList(),
            perDay = perDay,
            averageRating = rated.takeIf { it.isNotEmpty() }?.map { it.rating }?.average(),
            interruptions = all.sumOf { it.interruptions },
            longest = done.maxOfOrNull { it.minutes } ?: 0
        )
    }

    /** Minutes per day for the last [weeks] weeks, Monday first, for a heatmap. */
    fun heatmap(sessions: List<GardenSession>, today: LocalDate, weeks: Int = 16): List<List<Pair<LocalDate, Int>>> {
        val byDay = sessions.filter { it.completed }.groupBy { it.date }.mapValues { e -> e.value.sumOf { it.minutes } }
        val first = today.with(java.time.DayOfWeek.MONDAY).minusWeeks(weeks - 1L)
        return (0 until weeks).map { w -> (0L..6L).map { d -> first.plusWeeks(w.toLong()).plusDays(d).let { it to (byDay[it.toString()] ?: 0) } } }
    }

    /** Completed pomodoros per task (for "🍅 actual / estimate"). */
    fun pomodorosByTask(sessions: List<GardenSession>): Map<Long, Int> = sessions.filter { it.completed && !it.flow && it.taskId != null }.groupingBy { it.taskId!! }.eachCount()

    /** Today's sessions as (start minute, end minute, session) for a timeline; sessions without a start time are skipped. */
    fun timeline(sessions: List<GardenSession>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Triple<Int, Int, GardenSession>> =
        sessions.filter { it.date == day.toString() && it.startedAt > 0 }.map { s ->
            val t = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalTime()
            val start = t.hour * 60 + t.minute
            Triple(start, (start + s.minutes).coerceAtMost(24 * 60), s)
        }.sortedBy { it.first }
}
