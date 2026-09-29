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

    /** Free library windows of at least [minMinutes], leaving [buffer] minutes around classes for walking and time for meals. */
    fun freeWindows(data: CampusData, date: LocalDate, fromMinute: Int = 0, buffer: Int = data.settings.walkBufferMinutes, minMinutes: Int = data.settings.librarySlotMinutes): List<Window> {
        val holiday = data.exceptions.any { it.kind == ExceptionKind.HOLIDAY && it.date == date.toString() }
        val open = openWindow(data.library, date, holiday) ?: return emptyList()
        val classes = AttendanceEngine.occurrences(data, date).map { Window(it.start - buffer, it.end + buffer) }
        val meals = if (data.settings.mealsIgnoredInPlanning) emptyList() else Mess.reserved(data, date, classes).map { it.second }
        val busy = (classes + meals).sortedBy { it.start }
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

    /** Percentage from CGPA using the editable formula (CGPA − offset) × multiplier, clamped to 0–100. */
    fun percent(cgpa: Double, s: CampusSettings) = ((cgpa - s.percentOffset) * s.percentMultiplier).coerceIn(0.0, 100.0)

    /** Lowest and highest final CGPA still possible with [remainingCredits] left (lowest passing grade to top grade). */
    fun bounds(semesters: List<SemesterResult>, remainingCredits: Int, scale: List<GradePoint> = defaultScale): Pair<Double, Double>? {
        val done = earnedCredits(semesters, scale)
        val cur = cgpa(semesters, scale) ?: return null
        if (remainingCredits <= 0) return cur to cur
        val lowPass = scale.map { it.points }.filter { it > 0 }.minOrNull() ?: 0.0
        fun at(p: Double) = (cur * done + p * remainingCredits) / (done + remainingCredits)
        return at(lowPass) to at(max(scale))
    }

    /** "Label=7.5" lines → (label, cutoff). */
    fun cutoffs(lines: List<String>): List<Pair<String, Double>> = lines.mapNotNull { l ->
        val label = l.substringBefore('=').trim(); val v = l.substringAfter('=', "").trim().toDoubleOrNull()
        if (label.isBlank() || v == null) null else label to v
    }

    /** SGPA needed over [remainingCredits] to finish at [target]; null if above the scale's top grade. */
    fun requiredAverage(semesters: List<SemesterResult>, target: Double, remainingCredits: Int, scale: List<GradePoint> = defaultScale): Double? {
        if (remainingCredits <= 0) return null
        val done = earnedCredits(semesters, scale)
        val current = cgpa(semesters, scale) ?: 0.0
        val need = (target * (done + remainingCredits) - current * done) / remainingCredits
        return if (need > max(scale) + 1e-9) null else need.coerceAtLeast(0.0)
    }
}

// ---------------------------------------------------------------- hostel mess

object Mess {
    fun meals(data: CampusData, date: LocalDate) = data.settings.meals.filter { date.dayOfWeek.value in it.days && it.end > it.start }.sortedBy { it.start }

    /**
     * Where to eat inside each meal window so study time stays in the longest possible blocks:
     * tries the window edges and the moments right after/before classes, skipping anything that
     * overlaps a class. Meals with no room at all are left out (see [clashes]).
     */
    fun reserved(data: CampusData, date: LocalDate, classes: List<Window>): List<Pair<MealWindow, Window>> {
        val len = data.settings.mealMinutes
        return meals(data, date).mapNotNull { m ->
            val inside = classes.filter { it.end > m.start && it.start < m.end }
            val candidates = (listOf(m.start, m.end - len) + inside.map { it.end } + inside.map { it.start - len })
                .filter { it >= m.start && it + len <= m.end }.distinct()
            val ok = candidates.filter { s -> classes.none { it.start < s + len && s < it.end } }
            ok.maxByOrNull { s -> gapScore(classes + Window(s, s + len)) }?.let { m to Window(it, it + len) }
        }
    }

    /** Sum of squared free gaps: higher when free time is in fewer, longer blocks. */
    private fun gapScore(busy: List<Window>): Long {
        var cursor = 0; var score = 0L
        for (w in busy.sortedBy { it.start }) { val g = (w.start - cursor).coerceAtLeast(0).toLong(); score += g * g; cursor = maxOf(cursor, w.end) }
        val tail = (24 * 60 - cursor).coerceAtLeast(0).toLong()
        return score + tail * tail
    }

    /**
     * The latest wake-up (minute of day) that still leaves [WakeConfig.readyMinutes] to get ready and
     * time to eat the first meal of the day before [mustLeaveAt] (first class minus the walk), or
     * null when there's no such meal or it can't fit.
     */
    fun breakfastWake(data: CampusData, date: LocalDate, mustLeaveAt: Int?): Pair<MealWindow, Int>? {
        val w = data.wake
        if (w.ignoreBreakfast) return null
        val ready = if (w.readyMinutes > 0) w.readyMinutes else 25
        val len = data.settings.mealMinutes
        val meal = meals(data, date).firstOrNull { it.start < 12 * 60 && (mustLeaveAt == null || it.start + len <= mustLeaveAt) } ?: return null
        val eatBy = minOf(meal.end, mustLeaveAt ?: meal.end)
        return meal to (eatBy - len - ready)
    }

    /** Meals you can't make on [date] because classes fill the whole window. */
    fun clashes(data: CampusData, date: LocalDate): List<MealWindow> {
        val classes = AttendanceEngine.occurrences(data, date).map { Window(it.start, it.end) }
        val fit = reserved(data, date, classes).map { it.first }
        return meals(data, date).filter { it !in fit }
    }

    /** "Lunch open · closes 14:00 (45m left)", "Dinner 19:30–21:00 · in 2h" or null when the mess is done for the day. */
    fun status(data: CampusData, now: LocalDateTime): String? {
        val minute = now.hour * 60 + now.minute
        val today = meals(data, now.toLocalDate())
        today.firstOrNull { minute in it.start until it.end }?.let { return "${it.name} open · closes ${clock(it.end)} (${hm(it.end - minute)} left)" }
        today.firstOrNull { it.start > minute }?.let { return "Next: ${it.name} ${clock(it.start)}–${clock(it.end)} · in ${hm(it.start - minute)}" }
        return meals(data, now.toLocalDate().plusDays(1)).firstOrNull()?.let { "Mess closed · ${it.name} tomorrow ${clock(it.start)}" }
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
    val stageLabel: String? = null,
    val contact: String = "",
    /** Last day to apply. */
    val applyBy: String? = null,
    /** Minimum CGPA the company asks for (0 = none). */
    val minCgpa: Double = 0.0,
    /** 0–5 stars: how much you want it. */
    val excitement: Int = 0,
    /** Prep checklist, one item per line, "[x]" when done. */
    val prep: String = "",
    /** Every stage change with its date. */
    val history: List<StageChange> = emptyList()
) {
    val stageName: String get() = stageLabel ?: stage.label
}

data class StageChange(val date: String, val stage: String)

data class PlacementSummary(val total: Int, val applied: Int, val responded: Int, val offers: Int, val rejected: Int) {
    val responseRate: Int get() = if (applied == 0) 0 else 100 * responded / applied
}

object PlacementStats {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun history(c: Company): List<StageChange> = c.history ?: emptyList()

    /** Moves a company to [stage], recording the change. */
    fun move(c: Company, stage: String, today: LocalDate): Company =
        if (c.stageName.equals(stage, true)) c else c.copy(stageLabel = stage, history = history(c) + StageChange(today.toString(), stage))

    /**
     * Response rate like Huntr: of the companies past the first ("wishlist") stage, how many got past
     * "applied" to a test, interview or result. Stage order comes from the editable stage list.
     */
    fun summary(companies: List<Company>, stages: List<String>): PlacementSummary {
        fun idx(c: Company) = stages.indexOfFirst { it.equals(c.stageName, true) }.let { if (it < 0) 0 else it }
        val offerIdx = stages.indexOfFirst { it.contains("offer", true) }
        val rejectIdx = stages.indexOfFirst { it.contains("reject", true) }
        val applied = companies.filter { idx(it) >= 1 }
        val responded = applied.filter { idx(it) >= 2 && idx(it) != rejectIdx || history(it).any { h -> stages.indexOfFirst { s -> s.equals(h.stage, true) } in 2 until (if (rejectIdx < 0) stages.size else rejectIdx) } }
        return PlacementSummary(companies.size, applied.size, responded.size, companies.count { idx(it) == offerIdx }, companies.count { idx(it) == rejectIdx })
    }

    fun eligible(c: Company, cgpa: Double?): Boolean? = if (c.minCgpa <= 0.0 || cgpa == null) null else cgpa + 1e-9 >= c.minCgpa

    /** Apply-by dates in the next [days] days for companies not yet applied to. */
    fun applyDeadlines(companies: List<Company>, stages: List<String>, today: LocalDate, days: Long = 14) = companies.filter { c ->
        val i = stages.indexOfFirst { it.equals(c.stageName, true) }
        i <= 0 && c.applyBy?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(days)) } == true
    }.sortedBy { it.applyBy }

    fun funnel(companies: List<Company>, stages: List<String>) = (stages + companies.map { it.stageName }).distinct().associateWith { st -> companies.count { it.stageName.equals(st, true) } }
    fun upcoming(companies: List<Company>, today: LocalDate) = companies.filter { c ->
        c.nextDate?.let { runCatching { !LocalDate.parse(it).isBefore(today) }.getOrDefault(false) } == true
    }.sortedBy { it.nextDate }
}

fun daysUntil(date: String, today: LocalDate = LocalDate.now()): Long? = runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(date)) }.getOrNull()

fun clock(m: Int) = "%02d:%02d".format((m / 60) % 24, m % 60)
fun hm(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
