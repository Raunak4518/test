package com.raunak.daytimeline.productivity

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Deterministic offline planning utilities: no AI/server required. */
object SmartPlanningEngine {
    data class Block(val start: LocalDateTime, val end: LocalDateTime, val title: String)
    data class Gap(val start: LocalDateTime, val end: LocalDateTime, val minutes: Long)
    data class Health(val plannedMinutes: Long, val focusMinutes: Long, val freeMinutes: Long, val overlapMinutes: Long, val overloaded: Boolean)

    fun freeGaps(blocks: List<Block>, day: LocalDate, from: LocalTime = LocalTime.MIN, to: LocalTime = LocalTime.MAX): List<Gap> {
        val sorted = blocks.filter { it.end.isAfter(it.start) }.sortedBy { it.start }
        val result = mutableListOf<Gap>()
        var cursor = LocalDateTime.of(day, from)
        val boundary = LocalDateTime.of(day, to)
        for (b in sorted) {
            val start = maxOf(b.start, LocalDateTime.of(day, from)); val end = minOf(b.end, boundary)
            if (start.isAfter(cursor)) result += Gap(cursor, start, Duration.between(cursor, start).toMinutes())
            if (end.isAfter(cursor)) cursor = end
        }
        if (boundary.isAfter(cursor)) result += Gap(cursor, boundary, Duration.between(cursor, boundary).toMinutes())
        return result.filter { it.minutes >= 5 }
    }

    fun health(blocks: List<Block>, focusTitles: Set<String> = emptySet(), dayMinutes: Long = 16 * 60L): Health {
        val valid = blocks.filter { it.end.isAfter(it.start) }
        val planned = valid.sumOf { Duration.between(it.start, it.end).toMinutes() }
        val focus = valid.filter { it.title in focusTitles }.sumOf { Duration.between(it.start, it.end).toMinutes() }
        val sorted = valid.sortedBy { it.start }; var overlap = 0L
        for (i in 1 until sorted.size) overlap += maxOf(0L, Duration.between(sorted[i].start, minOf(sorted[i].end, sorted[i-1].end)).toMinutes())
        return Health(planned, focus, (dayMinutes - planned).coerceAtLeast(0), overlap, planned > dayMinutes || overlap > 0)
    }

    fun suggestPlacement(durationMinutes: Long, gaps: List<Gap>, preferredHour: Int = 9): Gap? = gaps.filter { it.minutes >= durationMinutes }.minByOrNull { kotlin.math.abs(it.start.hour - preferredHour) }
}
