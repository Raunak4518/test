package com.raunak.daytimeline.campus

import com.raunak.daytimeline.ui.*

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class MarksSummary(val subjectId: Long, val weightDone: Double, val earnedPercent: Double, val projectedPercent: Double?, val predictedGrade: String?, val neededForNext: Pair<String, Double>?)

/** Study time, internal marks, exam revision plans, sleep and weekly reviews — all pure and testable. */
object StudyEngines {
    // ---------------- study time
    fun minutes(s: StudySession, now: Long) = (((s.end ?: now) - s.start) / 60_000L).toInt().coerceAtLeast(0)

    fun minutesOn(sessions: List<StudySession>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): Int {
        val a = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val b = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return sessions.sumOf { maxOf(0L, minOf(it.end ?: now, b) - maxOf(it.start, a)) }.let { (it / 60_000L).toInt() }
    }

    /** Minutes per subject (or topic when no subject) between two dates inclusive. */
    fun bySubject(sessions: List<StudySession>, subjects: List<Subject>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): List<Pair<String, Int>> {
        val a = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val b = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val names = subjects.associate { it.id to it.name }
        return sessions.groupBy { s -> s.subjectId?.let { names[it] } ?: s.topic.ifBlank { "Other" } }
            .mapValues { (_, l) -> l.sumOf { maxOf(0L, minOf(it.end ?: now, b) - maxOf(it.start, a)) } / 60_000L }
            .map { it.key to it.value.toInt() }.filter { it.second > 0 }.sortedByDescending { it.second }
    }

    // ---------------- internal marks
    fun marks(subjectId: Long, assessments: List<Assessment>, cutoffs: List<GradeCutoff>): MarksSummary? {
        val list = assessments.filter { it.subjectId == subjectId && it.maxMarks > 0 }
        if (list.isEmpty()) return null
        val graded = list.filter { it.obtained != null }
        val weightDone = graded.sumOf { it.weightPercent }
        val earned = graded.sumOf { it.weightPercent * (it.obtained!! / it.maxMarks) }
        val projected = if (weightDone > 0) earned / weightDone * 100 else null
        val sorted = cutoffs.sortedByDescending { it.minPercent }
        val predicted = projected?.let { p -> sorted.firstOrNull { p >= it.minPercent }?.grade }
        // What you still need, on average, in the remaining weight to reach the next grade up.
        val remainingWeight = (list.sumOf { it.weightPercent }.coerceAtLeast(100.0) - weightDone).coerceAtLeast(0.0)
        val next = projected?.let { p -> sorted.lastOrNull { it.minPercent > p } }
        val needed = if (next != null && remainingWeight > 0) {
            val req = (next.minPercent - earned) / remainingWeight * 100
            if (req <= 100) next.grade to req.coerceAtLeast(0.0) else null
        } else null
        return MarksSummary(subjectId, weightDone, earned, projected, predicted, needed)
    }

    // ---------------- exam revision plan
    /**
     * Spreads the unfinished items of a sheet over the days before the exam, keeping [bufferDays]
     * before it free for full revision. Returns date → items, starting today.
     */
    fun revisionPlan(items: List<SheetItem>, exam: LocalDate, today: LocalDate, bufferDays: Int): Map<LocalDate, List<SheetItem>> {
        val open = items.filter { !it.status.done && it.status != ItemStatus.SKIPPED }
        val lastStudyDay = exam.minusDays((bufferDays + 1).toLong())
        val days = generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(lastStudyDay) }.toList().ifEmpty { listOf(today) }
        val perDay = Math.ceil(open.size.toDouble() / days.size).toInt().coerceAtLeast(1)
        return open.chunked(perDay).mapIndexed { i, chunk -> days[minOf(i, days.lastIndex)] to chunk }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.flatten() }
    }

    // ---------------- sleep
    /**
     * Night of sleep before [wakeDay]: the last screen-off between 18:00 the evening before and the
     * first unlock after 03:00 that morning. [screenOffs] and [unlocks] are epoch millis.
     */
    fun sleepFor(wakeDay: LocalDate, screenOffs: List<Long>, unlocks: List<Long>, zone: ZoneId = ZoneId.systemDefault()): SleepEntry? {
        val eveningStart = wakeDay.minusDays(1).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val morningFrom = wakeDay.atTime(3, 0).atZone(zone).toInstant().toEpochMilli()
        val noon = wakeDay.atTime(13, 0).atZone(zone).toInstant().toEpochMilli()
        val woke = unlocks.filter { it in morningFrom until noon }.minOrNull() ?: return null
        // The longest screen-off gap that ends at or before waking counts as sleep.
        val offs = screenOffs.filter { it in eveningStart until woke }.sorted()
        val ons = (unlocks.filter { it in eveningStart..woke }).sorted()
        var best: Pair<Long, Long>? = null
        for (off in offs) {
            val nextOn = ons.firstOrNull { it > off } ?: woke
            if (best == null || nextOn - off > best.second - best.first) best = off to nextOn
        }
        val (slept, woken) = best ?: return null
        if (woken - slept < 2 * 3_600_000L) return null
        return SleepEntry(wakeDay.toString(), slept, woken)
    }

    fun clockOf(ms: Long, zone: ZoneId = ZoneId.systemDefault()) = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().let { clock(it.hour * 60 + it.minute) }

    // ---------------- weekly review
    data class Week(val avgScore: Int?, val bestDay: String?, val studyMinutes: Int, val problems: Int, val onTimeWakes: Int, val wakeDays: Int, val avgSleep: Int?)

    fun week(data: CampusData, sheets: List<StudySheet>, end: LocalDate, now: Long = System.currentTimeMillis()): Week {
        val days = (0L..6L).map { end.minusDays(it) }
        val scores = data.scoreHistory.filter { e -> days.any { it.toString() == e.date } }
        val wakes = data.wakeLogs.filter { w -> days.any { it.toString() == w.date } }
        val sleep = data.sleepLog.filter { s -> days.any { it.toString() == s.date } }
        return Week(
            avgScore = scores.map { it.score }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            bestDay = scores.maxByOrNull { it.score }?.date,
            studyMinutes = days.sumOf { minutesOn(data.studySessions, it, now = now) },
            problems = days.sumOf { SheetEngine.doneOn(sheets, it) },
            onTimeWakes = wakes.count { it.dismissedAt != null && it.dismissedAt <= it.target + data.settings.onTimeToleranceMinutes * 60_000L },
            wakeDays = wakes.size,
            avgSleep = sleep.map { it.minutes }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        )
    }

    // ---------------- quick add for deadlines
    /** "ML assignment due fri 11pm" → a deadline using the app's date parser, your types and subjects. */
    fun parseDeadline(text: String, today: LocalDate, kinds: List<String>, subjects: List<Subject>, id: Long): Deadline? {
        if (text.isBlank()) return null
        val cleaned = text.replace(Regex("""\b(due|by|on|submit|submission)\b""", RegexOption.IGNORE_CASE), " ")
        val parsed = com.raunak.daytimeline.domain.QuickAddParser.parse(cleaned, today) ?: return null
        val hasTime = Regex("""\d{1,2}(:\d{2})?\s*(am|pm)|\d{1,2}:\d{2}|\bat\s+\d""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val kind = kinds.firstOrNull { k -> Regex("""\b${Regex.escape(k)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) }
        val subject = subjects.sortedByDescending { it.name.length }.firstOrNull { s ->
            Regex("""\b${Regex.escape(s.name)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) || (s.code.isNotBlank() && text.contains(s.code, true))
        }
        return Deadline(id, parsed.title.ifBlank { text.trim() }, DeadlineKind.OTHER, parsed.date.toString(), if (hasTime) parsed.startMinute else 23 * 60 + 59, subject?.id, kindLabel = kind ?: kinds.firstOrNull())
    }
}
