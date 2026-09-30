package com.raunak.daytimeline.home

import com.raunak.daytimeline.campus.AttendanceEngine
import com.raunak.daytimeline.campus.CampusData
import com.raunak.daytimeline.campus.Mess
import com.raunak.daytimeline.domain.TaskModel
import java.time.LocalDate

enum class EventKind { WAKE, CLASS, MEAL, TASK, DEADLINE, FREE, SLEEP }

/** One row of the day timeline. Minutes are minutes of the day; [end] == [start] for moments. */
data class TimelineEvent(
    val key: String,
    val kind: EventKind,
    val title: String,
    val detail: String,
    val start: Int,
    val end: Int,
    val color: Long,
    val task: TaskModel? = null,
    val done: Boolean = false
) {
    val minutes: Int get() = (end - start).coerceAtLeast(0)
}

/** Everything that happens on a day — alarm, classes, mess, tasks, deadlines — merged in time order with the gaps between. */
object TimelineBuilder {
    fun build(
        date: LocalDate,
        tasks: List<TaskModel>,
        campus: CampusData,
        wakeMinute: Int?,
        dayStart: Int,
        dayEnd: Int,
        minFreeGap: Int = 30,
        showCompleted: Boolean = true
    ): List<TimelineEvent> {
        val out = ArrayList<TimelineEvent>()
        wakeMinute?.let { out += TimelineEvent("wake", EventKind.WAKE, "Wake up", "Alarm", it, it, 0xFFE0A33BL) }
        val subjects = campus.subjects.associateBy { it.id }
        AttendanceEngine.occurrences(campus, date).forEach { o ->
            val s = subjects[o.subjectId]
            val detail = listOf(o.type.label, o.room).filter { it.isNotBlank() }.joinToString(" · ")
            out += TimelineEvent("c" + o.key, EventKind.CLASS, s?.name ?: "Class", detail, o.start, o.end, s?.colorHex ?: 0xFF55786AL, done = campus.marks[o.key] != null)
        }
        Mess.meals(campus, date).forEach { m ->
            out += TimelineEvent("m" + m.name + m.start, EventKind.MEAL, m.name, "Mess", m.start, m.end, 0xFFC98A4BL)
        }
        // [tasks] are already the day's tasks (recurring ones expanded by the repository).
        tasks.filter { showCompleted || !it.completed }
            .forEach { t ->
                out += TimelineEvent("t" + t.id, EventKind.TASK, t.title, t.tags.split(',', ' ').map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.joinToString(" ") { "#$it" }, t.startMinute, t.endMinute.coerceAtLeast(t.startMinute), t.colorHex, t, t.completed)
            }
        campus.deadlines.filter { !it.done && it.date == date.toString() }.forEach { d ->
            out += TimelineEvent("d" + d.id, EventKind.DEADLINE, d.title, d.label, d.minute, d.minute, 0xFFC62828L)
        }
        val sorted = out.sortedWith(compareBy<TimelineEvent> { it.start }.thenBy { it.kind.ordinal })
        return withGaps(sorted, dayStart, dayEnd, minFreeGap) + TimelineEvent("sleep", EventKind.SLEEP, "Wind down", "Day ends", dayEnd, dayEnd, 0xFF5B6BB0L)
    }

    /** Inserts FREE rows for every gap of at least [minGap] minutes between [dayStart] and [dayEnd]. */
    fun withGaps(events: List<TimelineEvent>, dayStart: Int, dayEnd: Int, minGap: Int): List<TimelineEvent> {
        val out = ArrayList<TimelineEvent>()
        var cursor = dayStart
        for (e in events) {
            if (e.kind != EventKind.WAKE && e.kind != EventKind.DEADLINE && e.start - cursor >= minGap && e.start <= dayEnd) out += free(cursor, e.start)
            out += e
            if (e.kind != EventKind.DEADLINE) cursor = maxOf(cursor, e.end)
        }
        if (dayEnd - cursor >= minGap) out += free(cursor, dayEnd)
        return out
    }

    private fun free(start: Int, end: Int) = TimelineEvent("f$start", EventKind.FREE, "Free", "", start, end, 0L)

    /** Index of the row the "now" marker sits before: the first row that hasn't started yet; events.size if none. */
    fun nowIndex(events: List<TimelineEvent>, minute: Int): Int {
        val i = events.indexOfFirst { it.start > minute }
        return if (i < 0) events.size else i
    }

    fun freeMinutes(events: List<TimelineEvent>) = events.filter { it.kind == EventKind.FREE }.sumOf { it.minutes }
}

/** An event placed in a side-by-side lane when it overlaps others. */
data class Placed(val event: TimelineEvent, val lane: Int, val lanes: Int)

object GridLayout {
    /** Assigns overlapping events to lanes; every event in an overlapping cluster shares the cluster's lane count. */
    fun lanes(events: List<TimelineEvent>): List<Placed> {
        val out = ArrayList<Placed>()
        val cluster = ArrayList<Placed>()
        val laneEnds = ArrayList<Int>()
        var clusterEnd = Int.MIN_VALUE
        fun flush() { cluster.forEach { out += it.copy(lanes = laneEnds.size) }; cluster.clear(); laneEnds.clear() }
        for (e in events.sortedWith(compareBy<TimelineEvent> { it.start }.thenByDescending { it.minutes })) {
            val end = maxOf(e.end, e.start + 15)
            if (cluster.isNotEmpty() && e.start >= clusterEnd) flush()
            var lane = laneEnds.indexOfFirst { it <= e.start }
            if (lane < 0) { laneEnds += end; lane = laneEnds.lastIndex } else laneEnds[lane] = end
            cluster += Placed(e, lane, 0)
            clusterEnd = if (cluster.size == 1) end else maxOf(clusterEnd, end)
        }
        flush()
        return out
    }
}

/**
 * Fits open tasks into the free time between classes and meals: highest priority first, keeping each
 * task's length, with a buffer between blocks. Tasks tagged #fixed, finished or repeating stay put.
 */
object AutoPlanner {
    fun movable(t: TaskModel) = !t.completed && t.recurrenceType == "NONE" && !t.tags.contains("fixed", ignoreCase = true)

    fun plan(tasks: List<TaskModel>, busy: List<Pair<Int, Int>>, dayStart: Int, dayEnd: Int, from: Int, buffer: Int = 10): Map<Long, Pair<Int, Int>> {
        val taken = (busy + tasks.filterNot(::movable).map { it.startMinute to it.endMinute }).sortedBy { it.first }.toMutableList()
        val out = LinkedHashMap<Long, Pair<Int, Int>>()
        val start = maxOf(dayStart, from)
        tasks.filter(::movable).sortedWith(compareByDescending<TaskModel> { it.priority }.thenBy { it.startMinute }).forEach { t ->
            val len = (t.endMinute - t.startMinute).coerceIn(5, 8 * 60)
            var cursor = roundUp(start, 5)
            for ((s, e) in taken.sortedBy { it.first }) {
                if (s - buffer >= cursor + len) break
                if (e + buffer > cursor) cursor = roundUp(e + buffer, 5)
            }
            if (cursor + len <= dayEnd) {
                out[t.id] = cursor to cursor + len
                taken += cursor to cursor + len
            }
        }
        return out
    }

    private fun roundUp(m: Int, step: Int) = (m + step - 1) / step * step
}
