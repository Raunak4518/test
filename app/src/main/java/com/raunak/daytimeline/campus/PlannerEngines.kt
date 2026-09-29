package com.raunak.daytimeline.campus

import com.raunak.daytimeline.ui.*

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class Window(val start: Int, val end: Int) { val minutes get() = end - start }

/** When the library is open and which of those hours are free of classes. */
object LibraryPlanner {
    fun openWindow(h: LibraryHours, date: LocalDate, holiday: Boolean = false): Window? {
        if (holiday && h.closedOnHolidays) return null
        return if (AttendanceEngine.isWeekend(date, h)) Window(h.weekendOpen, h.weekendClose) else Window(h.weekdayOpen, h.weekdayClose)
    }

    /** Free library windows of at least [minMinutes], leaving [buffer] minutes around classes for walking. */
    fun freeWindows(data: CampusData, date: LocalDate, fromMinute: Int = 0, buffer: Int = data.settings.walkBufferMinutes, minMinutes: Int = data.settings.librarySlotMinutes): List<Window> {
        val holiday = data.exceptions.any { it.kind == ExceptionKind.HOLIDAY && it.date == date.toString() }
        val open = openWindow(data.library, date, holiday) ?: return emptyList()
        val busy = AttendanceEngine.occurrences(data, date).map { Window(it.start - buffer, it.end + buffer) }.sortedBy { it.start }
        val out = mutableListOf<Window>()
        var cursor = maxOf(open.start, fromMinute)
        for (b in busy) {
            if (b.start > cursor) out += Window(cursor, minOf(b.start, open.end))
            cursor = maxOf(cursor, b.end)
        }
        if (open.end > cursor) out += Window(cursor, open.end)
        return out.filter { it.minutes >= minMinutes && it.end <= open.end }
    }

    fun status(h: LibraryHours, now: LocalDateTime): String {
        val w = openWindow(h, now.toLocalDate()) ?: return "Closed today"
        val m = now.hour * 60 + now.minute
        return when {
            m < w.start -> "Opens at ${clock(w.start)}"
            m < w.end -> "Open · closes at ${clock(w.end)} (${hm(w.end - m)} left)"
            else -> "Closed · opens ${clock(openWindow(h, now.toLocalDate().plusDays(1))?.start ?: w.start)} tomorrow"
        }
    }

    data class Block(val start: Int, val end: Int, val title: String)

    /**
     * Fills free library windows with study blocks: 50-minute focus blocks with 10-minute breaks,
     * rotating through [topics] (e.g. "DSA", the subject with the nearest exam, pending assignments).
     */
    fun planBlocks(windows: List<Window>, topics: List<String>, focus: Int = 50, rest: Int = 10, split: Boolean = true): List<Block> {
        if (topics.isEmpty()) return emptyList()
        // One long library session per free slot; the Pomodoro inside it handles breaks.
        if (!split) return windows.mapIndexed { i, w -> Block(w.start, w.end, topics[i % topics.size]) }
        val out = mutableListOf<Block>()
        var i = 0
        for (w in windows) {
            var t = w.start
            while (t + 30 <= w.end) {
                val len = minOf(focus, w.end - t)
                out += Block(t, t + len, topics[i % topics.size])
                i++
                t += len + rest
            }
        }
        return out
    }

    fun minutesIn(sessions: List<LibrarySession>, date: LocalDate, nowMillis: Long = System.currentTimeMillis()): Int {
        val zone = java.time.ZoneId.systemDefault()
        val s = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val e = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return sessions.sumOf { maxOf(0L, minOf(it.end ?: nowMillis, e) - maxOf(it.start, s)) }.let { (it / 60_000L).toInt() }
    }
}

/** A single number that says how seriously the day went, from the parts the app can measure. */
data class ScorePart(val label: String, val earned: Double, val max: Int, val detail: String)

object DailyScore {
    fun compute(
        settings: CampusSettings,
        wakeOnTime: Boolean?,
        classesAttended: Int,
        classesTotal: Int,
        studyMinutes: Int,
        studyGoal: Int,
        problemsSolved: Int,
        problemTarget: Int,
        screenMinutes: Int?,
        screenGoal: Int,
        disciplineKept: Boolean?
    ): List<ScorePart> = buildList {
        val s = settings
        if (wakeOnTime != null) add(ScorePart("Woke on time", if (wakeOnTime) s.scoreWake.toDouble() else 0.0, s.scoreWake, if (wakeOnTime) "✓" else "late"))
        if (classesTotal > 0) add(ScorePart("Classes", s.scoreClasses.toDouble() * classesAttended / classesTotal, s.scoreClasses, "$classesAttended/$classesTotal"))
        add(ScorePart("Deep study", s.scoreStudy * (studyMinutes.toDouble() / studyGoal.coerceAtLeast(1)).coerceAtMost(1.0), s.scoreStudy, "${hm(studyMinutes)}/${hm(studyGoal)}"))
        if (problemTarget > 0) add(ScorePart("Problems", s.scoreProblems * (problemsSolved.toDouble() / problemTarget).coerceAtMost(1.0), s.scoreProblems, "$problemsSolved/$problemTarget"))
        if (screenMinutes != null) {
            val ratio = if (screenMinutes <= screenGoal) 1.0 else (1.0 - (screenMinutes - screenGoal).toDouble() / screenGoal.coerceAtLeast(1)).coerceAtLeast(0.0)
            add(ScorePart("Screen time", s.scoreScreen * ratio, s.scoreScreen, hm(screenMinutes)))
        }
        if (disciplineKept != null) add(ScorePart("Discipline", if (disciplineKept) s.scoreDiscipline.toDouble() else 0.0, s.scoreDiscipline, if (disciplineKept) "✓" else "reset"))
    }.filter { it.max > 0 }

    fun total(parts: List<ScorePart>): Int {
        val max = parts.sumOf { it.max }
        return if (max == 0) 0 else (100.0 * parts.sumOf { it.earned } / max).toInt()
    }

    fun grade(score: Int, settings: CampusSettings = CampusSettings()) = settings.grade(score)
}

// ---------------------------------------------------------------- CGPA

data class Course(val name: String, val credits: Int, val grade: String?)
data class SemesterResult(val number: Int, val courses: List<Course>)

object Cgpa {
    /** Default NIT-style 10-point scale; the one in use is editable in CGPA → Grade scale. */
    val defaultScale = CampusSettings().gradeScale

    fun max(scale: List<GradePoint>) = scale.maxOfOrNull { it.points } ?: 10.0

    /** Letter from the scale, or a plain number like "8.5" within the scale's range. */
    fun pointsFor(grade: String?, scale: List<GradePoint> = defaultScale): Double? {
        val g = grade?.trim() ?: return null
        if (g.isEmpty()) return null
        return scale.firstOrNull { it.letter.equals(g, true) }?.points ?: g.toDoubleOrNull()?.takeIf { it in 0.0..max(scale) }
    }

    fun sgpa(courses: List<Course>, scale: List<GradePoint> = defaultScale): Double? {
        val graded = courses.mapNotNull { c -> pointsFor(c.grade, scale)?.let { c.credits to it } }
        val credits = graded.sumOf { it.first }
        return if (credits == 0) null else graded.sumOf { it.first * it.second } / credits
    }

    fun cgpa(semesters: List<SemesterResult>, scale: List<GradePoint> = defaultScale): Double? = sgpa(semesters.flatMap { it.courses }, scale)

    fun earnedCredits(semesters: List<SemesterResult>, scale: List<GradePoint> = defaultScale) = semesters.flatMap { it.courses }.filter { pointsFor(it.grade, scale) != null }.sumOf { it.credits }

    /** SGPA needed over [remainingCredits] to finish at [target]; null if above the scale's top grade. */
    fun requiredAverage(semesters: List<SemesterResult>, target: Double, remainingCredits: Int, scale: List<GradePoint> = defaultScale): Double? {
        if (remainingCredits <= 0) return null
        val done = earnedCredits(semesters, scale)
        val current = cgpa(semesters, scale) ?: 0.0
        val need = (target * (done + remainingCredits) - current * done) / remainingCredits
        return if (need > max(scale) + 1e-9) null else need.coerceAtLeast(0.0)
    }
}

// ---------------------------------------------------------------- placements

enum class PlacementStage(val label: String) { WISHLIST("Wishlist"), APPLIED("Applied"), OA("Online test"), INTERVIEW("Interview"), HR("HR"), OFFER("Offer"), REJECTED("Rejected") }

data class Company(
    val id: Long,
    val name: String,
    val role: String = "",
    val ctc: String = "",
    val stage: PlacementStage = PlacementStage.WISHLIST,
    val nextDate: String? = null,
    val nextMinute: Int = 10 * 60,
    val nextEvent: String = "",
    val link: String = "",
    val notes: String = "",
    /** Editable stage name (Settings → placement stages); older entries fall back to [stage]. */
    val stageLabel: String? = null
) {
    val stageName: String get() = stageLabel ?: stage.label
}

object PlacementStats {
    fun funnel(companies: List<Company>, stages: List<String>) = (stages + companies.map { it.stageName }).distinct().associateWith { st -> companies.count { it.stageName.equals(st, true) } }
    fun upcoming(companies: List<Company>, today: LocalDate) = companies.filter { c ->
        c.nextDate?.let { runCatching { !LocalDate.parse(it).isBefore(today) }.getOrDefault(false) } == true
    }.sortedBy { it.nextDate }
}

fun daysUntil(date: String, today: LocalDate = LocalDate.now()): Long? = runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(date)) }.getOrNull()

fun clock(m: Int) = "%02d:%02d".format((m / 60) % 24, m % 60)
fun hm(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
