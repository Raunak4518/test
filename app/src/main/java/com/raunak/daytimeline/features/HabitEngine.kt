package com.raunak.daytimeline.features

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.sqrt

/** How often a habit is meant to happen. */
enum class HabitFrequency(val label: String) {
    /** On chosen weekdays (every day when all seven are picked). */
    DAYS("Specific days"),
    /** Any days, a number of times per week. */
    WEEKLY("Times per week"),
    /** Once every N days. */
    INTERVAL("Every N days")
}

enum class HabitCell { DONE, SKIPPED, MISSED, PENDING, OFF, FUTURE }

data class HabitStreak(val current: Int, val best: Int, val unit: String)

/**
 * Pure habit maths, the same ideas top habit apps use:
 * - strength recovers gradually after a miss instead of dropping to zero (Loop's exponential score),
 * - skipped days (sick, travelling) never break a streak,
 * - weekly and every-N-days habits are judged per period, not per day.
 */
object HabitEngine {
    private const val HISTORY_DAYS = 400L

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun frequency(h: OfflineHabit): HabitFrequency = runCatching { HabitFrequency.valueOf(h.frequency ?: "DAYS") }.getOrDefault(HabitFrequency.DAYS)

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun days(h: OfflineHabit): Set<Int> = (h.activeDays ?: emptySet()).ifEmpty { (1..7).toSet() }

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun done(h: OfflineHabit): Set<String> = h.completedDates ?: emptySet()

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun skipped(h: OfflineHabit): Set<String> = h.skippedDates ?: emptySet()

    fun interval(h: OfflineHabit) = h.intervalDays.coerceIn(2, 60)
    fun perWeek(h: OfflineHabit) = h.targetPerWeek.coerceIn(1, 7)

    fun describe(h: OfflineHabit): String = when (frequency(h)) {
        HabitFrequency.DAYS -> days(h).let { d -> if (d.size == 7) "Every day" else if (d == setOf(1, 2, 3, 4, 5)) "Weekdays" else if (d == setOf(6, 7)) "Weekends" else d.sorted().joinToString(" ") { DayOfWeek.of(it).name.take(3).lowercase().replaceFirstChar(Char::uppercase) } }
        HabitFrequency.WEEKLY -> "${perWeek(h)}× a week"
        HabitFrequency.INTERVAL -> "Every ${interval(h)} days"
    }

    private fun start(h: OfflineHabit, today: LocalDate): LocalDate {
        val first = done(h).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.minOrNull()
        val created = h.createdDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return listOfNotNull(first, created, today).min().coerceAtLeast(today.minusDays(HISTORY_DAYS))
    }

    private fun LocalDate.coerceAtLeast(o: LocalDate) = if (isBefore(o)) o else this

    /** Whether [date] is one of the habit's planned days (weekly and interval habits can be done any day). */
    fun scheduled(h: OfflineHabit, date: LocalDate) = frequency(h) != HabitFrequency.DAYS || date.dayOfWeek.value in days(h)

    fun isDone(h: OfflineHabit, date: LocalDate) = date.toString() in done(h)
    fun isSkipped(h: OfflineHabit, date: LocalDate) = date.toString() in skipped(h)

    /** Completions in the Monday-based week containing [date]. */
    fun weekCount(h: OfflineHabit, date: LocalDate): Int {
        val monday = date.with(DayOfWeek.MONDAY)
        return (0L..6L).count { isDone(h, monday.plusDays(it)) }
    }

    /** Whether the habit still needs doing today. */
    fun dueToday(h: OfflineHabit, today: LocalDate): Boolean {
        if (h.archived || isDone(h, today) || isSkipped(h, today)) return false
        return when (frequency(h)) {
            HabitFrequency.DAYS -> scheduled(h, today)
            HabitFrequency.WEEKLY -> weekCount(h, today) < perWeek(h)
            HabitFrequency.INTERVAL -> {
                val last = done(h).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.filter { !it.isAfter(today) }.maxOrNull()
                last == null || ChronoUnit.DAYS.between(last, today) >= interval(h)
            }
        }
    }

    /** Relevant to today at all (shown on the Today list), even if already done. */
    fun forToday(h: OfflineHabit, today: LocalDate) = !h.archived && (isDone(h, today) || isSkipped(h, today) || dueToday(h, today))

    /**
     * Habit strength 0–100. Each day moves the score toward 1 (done) or 0 (missed) by a factor that
     * depends on how often the habit is meant to happen, so one miss after a long run barely dents it.
     */
    fun strength(h: OfflineHabit, today: LocalDate): Int {
        val freq = when (frequency(h)) {
            HabitFrequency.DAYS -> 1.0
            HabitFrequency.WEEKLY -> perWeek(h) / 7.0
            HabitFrequency.INTERVAL -> 1.0 / interval(h)
        }
        val m = 0.5.pow(sqrt(freq) / 13.0)
        var score = 0.0
        var d = start(h, today)
        while (!d.isAfter(today)) {
            val value: Double? = when (frequency(h)) {
                HabitFrequency.DAYS -> when {
                    !scheduled(h, d) || isSkipped(h, d) -> null
                    isDone(h, d) -> 1.0
                    d == today -> null
                    else -> 0.0
                }
                HabitFrequency.WEEKLY -> if (isSkipped(h, d)) null else {
                    val window = (0L..6L).count { isDone(h, d.minusDays(it)) }
                    if (d == today && !isDone(h, d)) null else (window.toDouble() / perWeek(h)).coerceAtMost(1.0)
                }
                HabitFrequency.INTERVAL -> if (isSkipped(h, d)) null else {
                    val hit = (0 until interval(h)).any { isDone(h, d.minusDays(it.toLong())) }
                    if (d == today && !hit) null else if (hit) 1.0 else 0.0
                }
            }
            if (value != null) score = score * m + value * (1 - m)
            d = d.plusDays(1)
        }
        return (score * 100).toInt().coerceIn(0, 100)
    }

    fun streak(h: OfflineHabit, today: LocalDate): HabitStreak = when (frequency(h)) {
        HabitFrequency.DAYS -> dayStreak(h, today)
        HabitFrequency.WEEKLY -> weekStreak(h, today)
        HabitFrequency.INTERVAL -> intervalStreak(h, today)
    }

    private fun dayStreak(h: OfflineHabit, today: LocalDate): HabitStreak {
        var best = 0; var run = 0
        var d = start(h, today)
        while (!d.isAfter(today)) {
            if (scheduled(h, d) && !isSkipped(h, d)) {
                if (isDone(h, d)) { run++; best = maxOf(best, run) } else if (d != today) run = 0
            }
            d = d.plusDays(1)
        }
        return HabitStreak(run, best, "days")
    }

    private fun weekStreak(h: OfflineHabit, today: LocalDate): HabitStreak {
        var best = 0; var run = 0
        var w = start(h, today).with(DayOfWeek.MONDAY)
        val thisWeek = today.with(DayOfWeek.MONDAY)
        while (!w.isAfter(thisWeek)) {
            val ok = weekCount(h, w) >= perWeek(h) || (0L..6L).any { isSkipped(h, w.plusDays(it)) } && weekCount(h, w) > 0
            if (ok) { run++; best = maxOf(best, run) } else if (w != thisWeek) run = 0
            w = w.plusWeeks(1)
        }
        return HabitStreak(run, best, "weeks")
    }

    private fun intervalStreak(h: OfflineHabit, today: LocalDate): HabitStreak {
        val dates = done(h).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.filter { !it.isAfter(today) }.sorted()
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (d in dates) {
            val p = prev
            val gap = p?.let { ChronoUnit.DAYS.between(it, d) } ?: 0
            val skippedBetween = p != null && (1 until gap).any { isSkipped(h, p.plusDays(it)) }
            run = if (p == null || gap <= interval(h) || skippedBetween) run + 1 else 1
            best = maxOf(best, run); prev = d
        }
        val last = prev
        val broken = last == null || ChronoUnit.DAYS.between(last, today) > interval(h)
        return HabitStreak(if (broken) 0 else run, best, "times")
    }

    /** Share of planned occurrences done over the last [days] days (0–100). */
    fun completionRate(h: OfflineHabit, today: LocalDate, days: Int = 30): Int {
        val from = maxOf(today.minusDays(days - 1L), start(h, today))
        val span = ChronoUnit.DAYS.between(from, today) + 1
        val doneCount = (0 until span).count { isDone(h, from.plusDays(it)) }
        val planned = when (frequency(h)) {
            HabitFrequency.DAYS -> (0 until span).count { val d = from.plusDays(it); scheduled(h, d) && !isSkipped(h, d) && (d != today || isDone(h, d)) }
            HabitFrequency.WEEKLY -> Math.ceil(span * perWeek(h) / 7.0).toInt()
            HabitFrequency.INTERVAL -> Math.ceil(span.toDouble() / interval(h)).toInt()
        }
        return if (planned <= 0) 0 else (100 * doneCount / planned).coerceIn(0, 100)
    }

    fun cell(h: OfflineHabit, date: LocalDate, today: LocalDate): HabitCell = when {
        date.isAfter(today) -> HabitCell.FUTURE
        isDone(h, date) -> HabitCell.DONE
        isSkipped(h, date) -> HabitCell.SKIPPED
        date == today -> if (dueToday(h, today)) HabitCell.PENDING else HabitCell.OFF
        frequency(h) == HabitFrequency.DAYS && scheduled(h, date) && !date.isBefore(start(h, today)) -> HabitCell.MISSED
        else -> HabitCell.OFF
    }

    /** Weeks (Monday first) ending with the current one, for a heatmap. */
    fun heatmap(h: OfflineHabit, today: LocalDate, weeks: Int = 16): List<List<Pair<LocalDate, HabitCell>>> {
        val first = today.with(DayOfWeek.MONDAY).minusWeeks(weeks - 1L)
        return (0 until weeks).map { w -> (0L..6L).map { d -> first.plusWeeks(w.toLong()).plusDays(d).let { it to cell(h, it, today) } } }
    }

    /** Sort key: "HH:mm" reminder time, then name; habits without a time go last. */
    fun order(h: OfflineHabit) = (h.preferredTime.takeIf { it.matches(Regex("\\d{1,2}:\\d{2}")) }?.padStart(5, '0') ?: "99:99") + h.name.lowercase()

    /** Morning / Afternoon / Evening / Anytime, from the reminder time. */
    fun partOfDay(h: OfflineHabit): String {
        val hour = h.preferredTime.substringBefore(":").toIntOrNull() ?: return "Anytime"
        return when (hour) { in 4..11 -> "Morning"; in 12..16 -> "Afternoon"; in 17..23 -> "Evening"; else -> "Anytime" }
    }
}
