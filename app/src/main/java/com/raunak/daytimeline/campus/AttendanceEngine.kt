package com.raunak.daytimeline.campus

import com.raunak.daytimeline.ui.*

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

data class SubjectAttendance(
    val subject: Subject,
    val attended: Int,
    val conducted: Int,
    val percent: Double,
    /** Classes you can still miss right now and stay at or above the requirement. */
    val safeToSkip: Int,
    /** Consecutive classes you must attend to get back to the requirement (0 when already safe). */
    val mustAttend: Int,
    /** Scheduled classes left until the semester ends. */
    val remaining: Int,
    /** Of the remaining classes, how many can be missed and still finish at the requirement. */
    val skippableOfRemaining: Int,
    val projectedIfAllAttended: Double,
    val unmarked: Int
) {
    val status: String get() = when {
        conducted == 0 -> "No classes yet"
        mustAttend > 0 -> "Attend next $mustAttend"
        safeToSkip == 0 -> "On the edge — don't skip"
        else -> "Can skip $safeToSkip"
    }
}

object AttendanceEngine {
    fun key(date: LocalDate, slotId: Long) = "$date|s$slotId"
    fun extraKey(exceptionId: Long, date: LocalDate) = "$date|x$exceptionId"

    /** All classes that actually happen on [date], after holidays, cancellations, reschedules and extras. */
    fun occurrences(data: CampusData, date: LocalDate): List<ClassOccurrence> {
        val semStart = runCatching { LocalDate.parse(data.semester.start) }.getOrNull()
        val semEnd = runCatching { LocalDate.parse(data.semester.end) }.getOrNull()
        val ds = date.toString()
        val exceptions = data.exceptions
        val holiday = exceptions.any { it.kind == ExceptionKind.HOLIDAY && it.date == ds }
        val inSemester = (semStart == null || !date.isBefore(semStart)) && (semEnd == null || !date.isAfter(semEnd))
        val out = mutableListOf<ClassOccurrence>()
        if (inSemester && !holiday) {
            val removed = exceptions.filter { (it.kind == ExceptionKind.CANCEL || it.kind == ExceptionKind.RESCHEDULE) && it.date == ds }.mapNotNull { it.slotId }.toSet()
            val week = rotationWeek(data, date)
            data.slots.filter { it.day == date.dayOfWeek.value && it.activeOn(date) && (week == 0 || it.rotationWeek == 0 || it.rotationWeek == week) && it.id !in removed && data.subjects.any { s -> s.id == it.subjectId } }.forEach {
                out += ClassOccurrence(key(date, it.id), date, it.subjectId, it.start, it.end, it.room, it.type, it.id, "Regular")
            }
        }
        // Rescheduled and extra classes happen even on otherwise free days.
        exceptions.filter { it.kind == ExceptionKind.RESCHEDULE && (it.newDate ?: it.date) == ds }.forEach { ex ->
            val slot = data.slots.firstOrNull { it.id == ex.slotId } ?: return@forEach
            out += ClassOccurrence(extraKey(ex.id, date), date, slot.subjectId, ex.start, ex.end, ex.room.ifBlank { slot.room }, slot.type, slot.id, "Rescheduled", ex.note.ifBlank { "Moved from ${ex.date}" })
        }
        exceptions.filter { it.kind == ExceptionKind.EXTRA && it.date == ds && it.subjectId != null }.forEach { ex ->
            out += ClassOccurrence(extraKey(ex.id, date), date, ex.subjectId!!, ex.start, ex.end, ex.room, ex.type, null, "Extra", ex.note)
        }
        return out.filter { data.subjects.any { s -> s.id == it.subjectId } }.sortedBy { it.start }
    }

    fun occurrencesBetween(data: CampusData, from: LocalDate, to: LocalDate): List<ClassOccurrence> {
        if (to.isBefore(from)) return emptyList()
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.flatMap { occurrences(data, it).asSequence() }.toList()
    }

    fun firstClass(data: CampusData, date: LocalDate): ClassOccurrence? = occurrences(data, date).firstOrNull()

    /** Weekly slots in force on [date] (the "current timetable" for that week). */
    /** Which week of the rotation [date] falls in (1 = A, 2 = B …), or 0 when the timetable doesn't rotate. */
    fun rotationWeek(data: CampusData, date: LocalDate): Int {
        val n = data.settings.rotationWeeks
        if (n <= 1) return 0
        val anchor = (data.settings.rotationStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: runCatching { LocalDate.parse(data.semester.start) }.getOrNull() ?: date).with(java.time.DayOfWeek.MONDAY)
        val weeks = java.time.temporal.ChronoUnit.WEEKS.between(anchor, date.with(java.time.DayOfWeek.MONDAY))
        return (((weeks % n) + n) % n).toInt() + 1
    }

    fun weekLetter(w: Int) = if (w <= 0) "" else ('A' + (w - 1)).toString()

    fun currentSlots(data: CampusData, date: LocalDate) = data.slots.filter { it.activeOn(date) }

    /**
     * Replaces a subject's weekly schedule with [drafts]. With [from] set, slots that change stop the
     * day before and new ones start on [from], so attendance already marked keeps its classes;
     * unchanged slots are kept as they are. With [from] null the schedule is replaced for the whole
     * semester (unchanged slots still keep their ids and marks).
     */
    fun applyWeekSchedule(slots: List<TimetableSlot>, subjectId: Long, drafts: List<SlotDraft>, from: LocalDate?, nextId: () -> Long): List<TimetableSlot> {
        val fromS = from?.toString()
        val pool = drafts.toMutableList()
        val out = mutableListOf<TimetableSlot>()
        for (slot in slots) {
            val stillRunning = fromS == null || slot.validUntil == null || slot.validUntil >= fromS
            if (slot.subjectId != subjectId || !stillRunning) { out += slot; continue }
            val match = pool.firstOrNull { it.day == slot.day && it.start == slot.start && it.end == slot.end && it.room == slot.room && it.type == slot.type && it.rotationWeek == slot.rotationWeek }
            when {
                match != null -> { pool.remove(match); out += slot }
                fromS == null -> Unit // dropped for the whole semester
                slot.validFrom != null && slot.validFrom >= fromS -> Unit // never took effect
                else -> out += slot.copy(validUntil = from.minusDays(1).toString())
            }
        }
        pool.forEach { d -> out += TimetableSlot(nextId(), subjectId, d.day, d.start, d.end, d.room, d.type, validFrom = fromS, rotationWeek = d.rotationWeek) }
        return out
    }

    /** Overlapping slots on the same day, as pairs. */
    fun conflicts(slots: List<TimetableSlot>): List<Pair<TimetableSlot, TimetableSlot>> {
        val out = mutableListOf<Pair<TimetableSlot, TimetableSlot>>()
        slots.groupBy { it.day }.values.forEach { day ->
            val sorted = day.sortedBy { it.start }
            for (i in sorted.indices) for (j in i + 1 until sorted.size) {
                val sameWeek = sorted[i].rotationWeek == 0 || sorted[j].rotationWeek == 0 || sorted[i].rotationWeek == sorted[j].rotationWeek
                if (sameWeek && sorted[j].start < sorted[i].end) out += sorted[i] to sorted[j]
            }
        }
        return out
    }

    /** Overlapping classes on a single date (after all changes), as pairs. */
    fun dayConflicts(occ: List<ClassOccurrence>): List<Pair<ClassOccurrence, ClassOccurrence>> {
        val s = occ.sortedBy { it.start }
        return s.indices.flatMap { i -> (i + 1 until s.size).filter { s[it].start < s[i].end }.map { s[i] to s[it] } }
    }

    private fun weight(subject: Subject, o: ClassOccurrence) = if (subject.countByHours) o.hours else 1

    fun subjectStats(data: CampusData, subject: Subject, today: LocalDate, nowMinute: Int = 24 * 60): SubjectAttendance {
        val semStart = runCatching { LocalDate.parse(data.semester.start) }.getOrDefault(today)
        val semEnd = runCatching { LocalDate.parse(data.semester.end) }.getOrDefault(today)
        val past = occurrencesBetween(data, semStart, today).filter { it.subjectId == subject.id && (it.date.isBefore(today) || it.end <= nowMinute) }
        var attended = subject.priorAttended
        var conducted = subject.priorConducted
        var unmarked = 0
        for (o in past) {
            val m = data.marks[o.key] ?: if (data.settings.assumePresent) Mark.PRESENT else null
            if (m == null) { unmarked++; continue }
            if (m.counts) {
                conducted += weight(subject, o)
                if (m.attended) attended += weight(subject, o)
            }
        }
        val future = occurrencesBetween(data, today, semEnd).filter { it.subjectId == subject.id && !(it.date == today && it.end <= nowMinute) && data.marks[it.key] == null }
        val remaining = future.sumOf { weight(subject, it) }
        val r = subject.requiredPercent / 100.0
        val safe = if (conducted == 0) 0 else floor(attended / r - conducted + 1e-9).toInt().coerceAtLeast(0)
        val must = if (conducted == 0 || attended >= r * conducted - 1e-9) 0 else ceil((r * conducted - attended) / (1 - r) - 1e-9).toInt()
        val skippable = floor(attended + remaining - r * (conducted + remaining) + 1e-9).toInt().coerceIn(0, remaining)
        val projected = if (conducted + remaining == 0) 100.0 else 100.0 * (attended + remaining) / (conducted + remaining)
        return SubjectAttendance(subject, attended, conducted, if (conducted == 0) 100.0 else 100.0 * attended / conducted, safe, must, remaining, skippable, projected, unmarked)
    }

    fun overall(stats: List<SubjectAttendance>): Double {
        val c = stats.sumOf { it.conducted }
        return if (c == 0) 100.0 else 100.0 * stats.sumOf { it.attended } / c
    }

    /** Past classes still waiting for a Present/Absent mark, newest first. */
    fun unmarked(data: CampusData, today: LocalDate, nowMinute: Int): List<ClassOccurrence> {
        if (data.settings.assumePresent) return emptyList()
        val semStart = runCatching { LocalDate.parse(data.semester.start) }.getOrDefault(today)
        return occurrencesBetween(data, maxOf(semStart, today.minusDays(30)), today)
            .filter { (it.date.isBefore(today) || it.end <= nowMinute) && data.marks[it.key] == null }
            .sortedWith(compareByDescending<ClassOccurrence> { it.date }.thenByDescending { it.start })
    }

    /** Percentage after attending [attend] and skipping [skip] more classes (one class each). */
    fun whatIf(stat: SubjectAttendance, attend: Int, skip: Int): Double {
        val held = stat.conducted + attend + skip
        return if (held <= 0) 100.0 else 100.0 * (stat.attended + attend) / held
    }

    /** Classes in a row needed afterwards to get back to the requirement. */
    fun toRecover(stat: SubjectAttendance, attend: Int, skip: Int): Int {
        val a = stat.attended + attend
        val c = stat.conducted + attend + skip
        val r = stat.subject.requiredPercent / 100.0
        return if (r >= 1.0 || a >= r * c - 1e-9) 0 else ceil((r * c - a) / (1 - r) - 1e-9).toInt()
    }

    /** What happens if you skip one specific class. */
    fun afterSkipping(stat: SubjectAttendance): Double =
        100.0 * stat.attended / (stat.conducted + 1).coerceAtLeast(1)

    private val dayNames = mapOf(
        "mon" to 1, "monday" to 1, "tue" to 2, "tues" to 2, "tuesday" to 2, "wed" to 3, "wednesday" to 3,
        "thu" to 4, "thur" to 4, "thurs" to 4, "thursday" to 4, "fri" to 5, "friday" to 5, "sat" to 6, "saturday" to 6, "sun" to 7, "sunday" to 7
    )

    /**
     * Quick timetable entry, one class per line:
     * "Mon 09:00-10:00 DSA L 203", "tue 2-4pm ML Lab LAB-2", "Wed 11:10-12:05 CN T".
     * Unknown subjects are created. Returns the new subjects and slots.
     */
    fun parseTimetable(text: String, existing: List<Subject>, idStart: Long, afternoonBeforeHour: Int = 8): Pair<List<Subject>, List<TimetableSlot>> {
        val subjects = existing.toMutableList()
        val slots = mutableListOf<TimetableSlot>()
        var id = idStart
        val time = Regex("""(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?\s*[-–to]+\s*(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val first = line.split(Regex("\\s+")).first().lowercase().trimEnd(',', ':')
            val day = dayNames[first] ?: continue
            val m = time.find(line) ?: continue
            val g = m.groupValues
            var start = toMinute(g[1], g[2], g[3].ifBlank { g[6] })
            var end = toMinute(g[4], g[5], g[6])
            if (g[3].isBlank() && g[6].isBlank()) {
                // College hours: times before the configured hour without am/pm mean afternoon.
                val cut = afternoonBeforeHour.coerceIn(1, 12) * 60
                if (start < cut) start += 12 * 60
                if (end < cut || end <= start) end += 12 * 60
            }
            if (end <= start) end += 12 * 60
            val rest = line.substring(m.range.last + 1).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (rest.isEmpty()) continue
            val typeToken = rest.drop(1).firstOrNull { it.lowercase() in setOf("l", "lec", "lecture", "p", "lab", "practical", "t", "tut", "tutorial") }
            val type = when (typeToken?.lowercase()) {
                "p", "lab", "practical" -> ClassType.LAB
                "t", "tut", "tutorial" -> ClassType.TUTORIAL
                else -> if (rest.any { it.equals("lab", true) }) ClassType.LAB else ClassType.LECTURE
            }
            // Subject name: a known subject if the line starts with one, else words up to the type or the first room-like token.
            val known = subjects.map { it.name }.sortedByDescending { it.length }.firstOrNull { n ->
                val words = n.split(Regex("\\s+"))
                rest.size >= words.size && rest.take(words.size).joinToString(" ").equals(n, true)
            }
            val nameTokens = known?.let { rest.take(it.split(Regex("\\s+")).size) }
                ?: rest.takeWhile { it != typeToken && (it === rest.first() || it.none(Char::isDigit)) }.ifEmpty { rest.take(1) }
            val name = nameTokens.joinToString(" ")
            val room = rest.drop(nameTokens.size).filterNot { it == typeToken }.joinToString(" ")
            val subject = subjects.firstOrNull { it.name.equals(name, true) || it.code.equals(name, true) }
                ?: Subject(id++, name, colorHex = palette[subjects.size % palette.size]).also { subjects += it }
            slots += TimetableSlot(id++, subject.id, day, start, end, room, type)
        }
        return subjects.drop(existing.size) to slots
    }

    val palette = listOf(0xFF3F6FD8, 0xFF55786A, 0xFFB5651D, 0xFF8E44AD, 0xFFC0392B, 0xFF16A085, 0xFFD4AC0D, 0xFF2C3E50, 0xFFE67E22, 0xFF7F8C8D)

    private fun toMinute(h: String, m: String, ampm: String): Int {
        var hour = h.toInt() % 24
        val minute = m.toIntOrNull() ?: 0
        if (ampm.equals("pm", true) && hour in 1..11) hour += 12
        if (ampm.equals("am", true) && hour == 12) hour = 0
        return hour * 60 + minute
    }

    fun isWeekend(date: LocalDate, hours: LibraryHours) =
        date.dayOfWeek == DayOfWeek.SUNDAY || (hours.saturdayIsWeekend && date.dayOfWeek == DayOfWeek.SATURDAY)
}
