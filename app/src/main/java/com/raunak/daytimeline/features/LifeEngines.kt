package com.raunak.daytimeline.features

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class GoalKind(val label: String) { TARGET("Reach a number"), PROJECT("Finish milestones") }

enum class Pace(val label: String) { DONE("Done"), AHEAD("Ahead"), ON_TRACK("On track"), BEHIND("Behind"), NO_DEADLINE("No deadline"), OVERDUE("Past deadline") }

data class GoalStatus(
    val percent: Int,
    val pace: Pace,
    /** Where progress should be today if moving evenly from start to deadline. */
    val expected: Int?,
    /** Per day from now to hit the target on time. */
    val neededPerDay: Double?,
    val daysLeft: Long?,
    /** Average per day over the last 7 days. */
    val recentPerDay: Double,
    /** Estimated finish date at the recent rate. */
    val projectedFinish: LocalDate?
)

/** Strides-style goal maths: pace line, required rate and projected finish. */
object GoalEngine {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun log(g: OfflineGoal): List<GoalLog> = g.log ?: emptyList()

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun kind(g: OfflineGoal): GoalKind = runCatching { GoalKind.valueOf(g.kind ?: "TARGET") }.getOrDefault(GoalKind.TARGET)

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun milestones(g: OfflineGoal): List<String> = g.milestones ?: emptyList()

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun milestoneDone(g: OfflineGoal): Set<Int> = g.milestoneDone ?: emptySet()

    /** Progress and target in the goal's own terms (milestones for projects). */
    fun progress(g: OfflineGoal): Pair<Int, Int> = if (kind(g) == GoalKind.PROJECT) milestoneDone(g).count { it in milestones(g).indices } to milestones(g).size.coerceAtLeast(1) else g.progress to g.target.coerceAtLeast(1)

    fun status(g: OfflineGoal, today: LocalDate): GoalStatus {
        val (value, target) = progress(g)
        val percent = (100 * value / target).coerceIn(0, 100)
        val start = g.createdDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: log(g).mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.minOrNull() ?: today
        val deadline = g.deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val recent = log(g).filter { runCatching { !LocalDate.parse(it.date).isBefore(today.minusDays(6)) }.getOrDefault(false) }.sumOf { it.amount } / 7.0
        val remaining = (target - value).coerceAtLeast(0)
        val projected = if (remaining == 0) today else if (recent > 0) today.plusDays(Math.ceil(remaining / recent).toLong()) else null
        if (value >= target) return GoalStatus(100, Pace.DONE, null, null, deadline?.let { ChronoUnit.DAYS.between(today, it) }, recent, today)
        if (deadline == null) return GoalStatus(percent, Pace.NO_DEADLINE, null, null, null, recent, projected)
        val daysLeft = ChronoUnit.DAYS.between(today, deadline)
        if (daysLeft < 0) return GoalStatus(percent, Pace.OVERDUE, target, null, daysLeft, recent, projected)
        val total = ChronoUnit.DAYS.between(start, deadline).coerceAtLeast(1)
        val elapsed = ChronoUnit.DAYS.between(start, today).coerceIn(0, total)
        val expected = (target.toDouble() * elapsed / total).toInt()
        val needed = remaining.toDouble() / (daysLeft + 1)
        val slack = (target * 0.05).coerceAtLeast(1.0)
        val pace = when {
            value >= expected + slack -> Pace.AHEAD
            value + slack >= expected -> Pace.ON_TRACK
            else -> Pace.BEHIND
        }
        return GoalStatus(percent, pace, expected, needed, daysLeft, recent, projected)
    }

    /** Cumulative progress for each of the last [days] days (for a sparkline). */
    fun history(g: OfflineGoal, today: LocalDate, days: Int = 30): List<Int> {
        val byDay = log(g).groupBy { it.date }.mapValues { e -> e.value.sumOf { it.amount } }
        val before = log(g).filter { it.date < today.minusDays(days - 1L).toString() }.sumOf { it.amount }
        var run = before
        return (days - 1 downTo 0).map { back -> run += byDay[today.minusDays(back.toLong()).toString()] ?: 0; run.coerceAtLeast(0) }
    }
}

data class ChecklistLine(val index: Int, val text: String, val checked: Boolean?)

/** Keep-style notes: lines starting with "[ ]" / "[x]" are checklist items. */
object NoteEngine {
    private val box = Regex("""^\s*\[( |x|X)]\s?(.*)$""")

    fun lines(body: String): List<ChecklistLine> = body.lines().mapIndexed { i, l ->
        box.find(l)?.let { m -> ChecklistLine(i, m.groupValues[2], m.groupValues[1].isNotBlank()) } ?: ChecklistLine(i, l, null)
    }

    fun isChecklist(body: String) = lines(body).any { it.checked != null }

    fun toggleLine(body: String, index: Int): String = body.lines().mapIndexed { i, l ->
        if (i != index) l else box.find(l)?.let { m -> "[" + (if (m.groupValues[1].isBlank()) "x" else " ") + "] " + m.groupValues[2] } ?: l
    }.joinToString("\n")

    /** Converts plain lines to checklist lines, or back. */
    fun toggleChecklist(body: String): String = if (isChecklist(body)) body.lines().joinToString("\n") { l -> box.find(l)?.groupValues?.get(2) ?: l }
    else body.lines().filter { it.isNotBlank() }.joinToString("\n") { "[ ] $it" }

    fun progress(body: String): Pair<Int, Int> = lines(body).filter { it.checked != null }.let { l -> l.count { it.checked == true } to l.size }

    /** Checked items move to the bottom, like Keep. */
    fun sorted(body: String): List<ChecklistLine> = lines(body).filter { it.text.isNotBlank() || it.checked != null }.sortedBy { if (it.checked == true) 1 else 0 }

    fun matches(n: OfflineNote, q: String): Boolean = q.isBlank() || n.title.contains(q, true) || n.body.contains(q, true) || tags(n).any { it.contains(q, true) } || n.folder.contains(q, true)

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun tags(n: OfflineNote): Set<String> = n.tags ?: emptySet()

    /** [[Title]] links in the body that point at other notes. */
    fun links(n: OfflineNote, all: List<OfflineNote>): List<OfflineNote> {
        val names = Regex("""\[\[([^\]]+)]]""").findAll(n.body).map { it.groupValues[1].trim().lowercase() }.toSet()
        return all.filter { it.id != n.id && it.title.trim().lowercase() in names }
    }
}

data class MoodStats(
    val entries: Int,
    val average: Double?,
    val streak: Int,
    val byWeekday: List<Double?>,
    /** Activity → (average mood with it, days logged). Sorted best first. */
    val activityMood: List<Triple<String, Double, Int>>,
    val moodCounts: List<Int>
)

/** Daylio-style statistics from journal entries. */
object JournalEngine {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun activities(e: OfflineJournalEntry): List<String> = e.activities ?: emptyList()

    fun stats(entries: List<OfflineJournalEntry>, today: LocalDate, days: Int = 30): MoodStats {
        val recent = entries.filter { runCatching { !LocalDate.parse(it.date).isBefore(today.minusDays(days - 1L)) && !LocalDate.parse(it.date).isAfter(today) }.getOrDefault(false) }
        val dates = entries.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet()
        var streak = 0
        var d = if (today in dates) today else today.minusDays(1)
        while (d in dates) { streak++; d = d.minusDays(1) }
        val byWeekday = (1..7).map { w -> recent.filter { LocalDate.parse(it.date).dayOfWeek.value == w }.map { it.mood }.takeIf { it.isNotEmpty() }?.average() }
        val acts = recent.flatMap { e -> activities(e).map { it to e.mood } }.groupBy({ it.first }, { it.second })
            .filter { it.value.size >= 2 }.map { Triple(it.key, it.value.average(), it.value.size) }.sortedByDescending { it.second }
        val counts = (1..5).map { m -> recent.count { it.mood == m } }
        return MoodStats(recent.size, recent.map { it.mood }.takeIf { it.isNotEmpty() }?.average(), streak, byWeekday, acts, counts)
    }

    /** Same prompt all day, a different one each day. */
    fun prompt(prompts: List<String>, date: LocalDate): String? = prompts.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { it[(date.toEpochDay() % it.size).toInt()] }
}

data class TimeSummary(val label: String, val minutes: Long, val color: Long?)

/** Toggl-style totals and reports. */
object TimeEngine {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun minutes(e: OfflineTimeEntry, now: Long = System.currentTimeMillis()) = (((e.endEpochMillis ?: now) - e.startEpochMillis).coerceAtLeast(0L)) / 60_000L

    fun dayOf(e: OfflineTimeEntry): LocalDate = Instant.ofEpochMilli(e.startEpochMillis).atZone(zone).toLocalDate()

    /** Minutes of each entry that fall inside [from, to] (inclusive days). */
    fun inRange(entries: List<OfflineTimeEntry>, from: LocalDate, to: LocalDate, now: Long = System.currentTimeMillis()): List<Pair<OfflineTimeEntry, Long>> {
        val a = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val b = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return entries.mapNotNull { e ->
            val s = maxOf(e.startEpochMillis, a); val f = minOf(e.endEpochMillis ?: now, b)
            if (f > s) e to (f - s) / 60_000L else null
        }
    }

    fun byProject(entries: List<OfflineTimeEntry>, projects: List<OfflineProject>, from: LocalDate, to: LocalDate, now: Long = System.currentTimeMillis()): List<TimeSummary> =
        inRange(entries, from, to, now).groupBy { it.first.projectId }.map { (pid, list) ->
            val p = projects.firstOrNull { it.id == pid }
            TimeSummary(p?.name ?: "No project", list.sumOf { it.second }, p?.color)
        }.sortedByDescending { it.minutes }

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun byTag(entries: List<OfflineTimeEntry>, from: LocalDate, to: LocalDate, now: Long = System.currentTimeMillis()): List<TimeSummary> =
        inRange(entries, from, to, now).flatMap { (e, m) -> (e.tags ?: emptySet()).ifEmpty { setOf("untagged") }.map { it to m } }
            .groupBy({ it.first }, { it.second }).map { TimeSummary("#" + it.key, it.value.sum(), null) }.sortedByDescending { it.minutes }

    fun perDay(entries: List<OfflineTimeEntry>, from: LocalDate, days: Int, now: Long = System.currentTimeMillis()): List<Long> =
        (0 until days).map { i -> val d = from.plusDays(i.toLong()); inRange(entries, d, d, now).sumOf { it.second } }

    /** Distinct recent (label, project, tags) combos to restart with one tap. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun recent(entries: List<OfflineTimeEntry>, limit: Int = 5): List<OfflineTimeEntry> =
        entries.filter { it.endEpochMillis != null }.sortedByDescending { it.startEpochMillis }.distinctBy { Triple(it.label.lowercase(), it.projectId, it.tags ?: emptySet()) }.take(limit)
}

/** Routinery-style step timing. */
object RoutineEngine {
    fun totalMinutes(r: OfflineRoutine) = r.steps.sumOf { it.minutes }

    /** Current step index and seconds left in it after [elapsedSeconds] of the routine. */
    fun position(r: OfflineRoutine, elapsedSeconds: Long): Pair<Int, Long>? {
        var left = elapsedSeconds
        r.steps.forEachIndexed { i, s ->
            val len = s.minutes * 60L
            if (left < len) return i to (len - left)
            left -= len
        }
        return null
    }

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun streak(r: OfflineRoutine, today: LocalDate): Int {
        val days = (r.completionDates ?: emptySet()).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    /** Clock time the routine ends if started at [startMinute]. */
    fun endsAt(r: OfflineRoutine, startMinute: Int) = (startMinute + totalMinutes(r)) % (24 * 60)
}
