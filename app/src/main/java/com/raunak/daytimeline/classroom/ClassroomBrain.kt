package com.raunak.daytimeline.classroom

import com.raunak.daytimeline.campus.Subject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Finds dates and times written the way teachers and Classroom write them. */
object DateFinder {
    private val months = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
    private val days = mapOf("mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY,
        "thurs" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY)
    private val monthAlt = "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"
    private val dayAlt = "mon(?:day)?|tue(?:s|sday)?|wed(?:nesday)?|thu(?:r|rs|rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?"
    private val opt = setOf(RegexOption.IGNORE_CASE)
    private val dMonth = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+($monthAlt)\.?,?(?:\s+(\d{4}))?""", opt)
    private val monthD = Regex("""\b($monthAlt)\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?(?:\s+(\d{4}))?""", opt)
    private val numeric = Regex("""\b(\d{1,2})[/.-](\d{1,2})(?:[/.-](\d{2,4}))?\b""")
    private val relative = Regex("""\b(today|tonight|tomorrow|tmrw|day after tomorrow)\b""", opt)
    private val weekday = Regex("""\b(?:(next|this|on|by)\s+)?($dayAlt)\b""", opt)
    private val time = Regex("""\b(\d{1,2})(?::|\.)(\d{2})\s*(am|pm)?\b|\b(\d{1,2})\s*(am|pm)\b""", opt)

    /** The first date mentioned in [text], relative to [today]. Past day/month dates roll to next year. */
    fun date(text: String, today: LocalDate): LocalDate? {
        relative.find(text)?.let { m ->
            return when (m.groupValues[1].lowercase()) { "today", "tonight" -> today; "day after tomorrow" -> today.plusDays(2); else -> today.plusDays(1) }
        }
        dMonth.find(text)?.let { m -> build(m.groupValues[1].toInt(), months[m.groupValues[2].lowercase().take(3)] ?: return@let null, m.groupValues[3], today)?.let { return it } }
        monthD.find(text)?.let { m -> build(m.groupValues[2].toInt(), months[m.groupValues[1].lowercase().take(3)] ?: return@let null, m.groupValues[3], today)?.let { return it } }
        numeric.find(text)?.let { m ->
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            if (b in 1..12 && a in 1..31) build(a, b, m.groupValues[3], today)?.let { return it } // Indian day/month order
        }
        weekday.find(text)?.let { m ->
            val dow = days[m.groupValues[2].lowercase().take(3)] ?: return@let
            // "next Monday" = the coming Monday (never today); a bare weekday may be today.
            val next = if (m.groupValues[1].equals("next", true)) today.with(TemporalAdjusters.next(dow)) else today.with(TemporalAdjusters.nextOrSame(dow))
            return next
        }
        return null
    }

    private fun build(day: Int, month: Int, year: String, today: LocalDate): LocalDate? {
        val y = year.toIntOrNull()?.let { if (it < 100) 2000 + it else it }
        val d = runCatching { LocalDate.of(y ?: today.year, month, day) }.getOrNull() ?: return null
        return if (y == null && d.isBefore(today.minusDays(30))) d.plusYears(1) else d
    }

    fun time(text: String): LocalTime? {
        val m = time.find(text) ?: return null
        val (h, min, ap) = if (m.groupValues[1].isNotEmpty()) Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3]) else Triple(m.groupValues[4].toInt(), 0, m.groupValues[5])
        var hour = h
        if (ap.equals("pm", true) && hour < 12) hour += 12
        if (ap.equals("am", true) && hour == 12) hour = 0
        return runCatching { LocalTime.of(hour, min) }.getOrNull()
    }

    /** "Due tomorrow, 11:59 PM", "due on 3 Oct", "Due Fri" → date-time (end of day when no time is given). */
    fun due(text: String, now: LocalDateTime): LocalDateTime? {
        val m = Regex("""\b(?:due|deadline|submit(?:\s+it)?\s+by|by)\b[:\s]*(.{0,60})""", opt).find(text) ?: return null
        val tail = m.groupValues[1]
        val d = date(tail, now.toLocalDate()) ?: return null
        return d.atTime(time(tail) ?: LocalTime.of(23, 59))
    }
}

/** A suggested change to Chronora found in an announcement. */
data class Suggestion(val id: String, val kind: Kind, val itemId: String, val course: String, val date: LocalDate, val text: String, val subjectId: Long?) {
    enum class Kind(val action: String) { CANCEL_CLASS("Mark the class cancelled"), EXAM("Add to Exams & tasks"), EXTRA_CLASS("Add the extra class"), EXTENDED("Move the deadline") }
}

data class StudyTarget(val item: ClassItem, val minutesToday: Int, val totalMinutes: Int, val daysLeft: Long)

data class Ranked(val item: ClassItem, val score: Double, val reason: String)

/** The "intelligent" part: parsing, matching, ranking, planning and spotting actions. */
object ClassroomBrain {
    const val CLASSROOM_PACKAGE = "com.google.android.apps.classroom"
    const val GMAIL_PACKAGE = "com.google.android.gm"

    private val opt = setOf(RegexOption.IGNORE_CASE)

    fun kindOf(text: String): ClassKind {
        val t = text.lowercase()
        return when {
            Regex("""\b(returned|graded|grade|marks? (?:for|on)|scored)\b""").containsMatchIn(t) -> ClassKind.GRADE
            Regex("""\bquiz\b""").containsMatchIn(t) -> ClassKind.QUIZ
            Regex("""\bnew question\b|\bquestion:""").containsMatchIn(t) -> ClassKind.QUESTION
            Regex("""\b(private comment|comment|replied|mentioned you)\b""").containsMatchIn(t) -> ClassKind.COMMENT
            Regex("""\b(assignment|due|submit|homework|lab report|tutorial sheet)\b""").containsMatchIn(t) -> ClassKind.ASSIGNMENT
            Regex("""\bmaterial\b|\buploaded\b|\bslides?\b|\bnotes\b""").containsMatchIn(t) -> ClassKind.MATERIAL
            Regex("""\b(announcement|posted|new post|shared)\b""").containsMatchIn(t) -> ClassKind.ANNOUNCEMENT
            else -> ClassKind.OTHER
        }
    }

    private val quoted = Regex("""[“"']([^”"']{3,120})[”"']""")
    private val afterColon = Regex("""(?:new\s+)?(?:assignment|quiz|question|material|announcement)s?\s*(?:posted)?\s*[:\-–]\s*(.+)""", opt)

    /**
     * Understands a notification from the Classroom app, or a Classroom e-mail shown by Gmail.
     * Returns null for anything that isn't Classroom.
     */
    fun fromNotification(pkg: String, title: String, text: String, bigText: String, subText: String, postedAt: Long, now: LocalDateTime): ClassItem? {
        val all = listOf(title, text, bigText, subText).filter { it.isNotBlank() }.joinToString("\n")
        val isClassroom = pkg == CLASSROOM_PACKAGE || (pkg == GMAIL_PACKAGE && Regex("""classroom""", opt).containsMatchIn("$title $text $subText"))
        if (!isClassroom || all.isBlank()) return null
        val kind = kindOf(all)
        val body = bigText.ifBlank { text }
        val itemTitle = quoted.find(all)?.groupValues?.get(1)?.trim()
            ?: afterColon.find(text)?.groupValues?.get(1)?.trim()
            ?: afterColon.find(title)?.groupValues?.get(1)?.trim()
            ?: text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(120) ?: title
        val course = when {
            subText.isNotBlank() && !subText.contains("@") -> subText.trim()
            pkg == GMAIL_PACKAGE -> Regex("""(?:in|for)\s+([A-Z][\w &\-.()]{2,60})""").find(bigText.ifBlank { text })?.groupValues?.get(1)?.trim() ?: "Classroom"
            kindOf(title) == ClassKind.OTHER && title.isNotBlank() -> title.trim()
            else -> Regex("""(?:in|for)\s+([A-Z][\w &\-.()]{2,60})""").find(all)?.groupValues?.get(1)?.trim() ?: "Classroom"
        }
        val due = DateFinder.due(all, now)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        val work = kind in setOf(ClassKind.ASSIGNMENT, ClassKind.QUIZ, ClassKind.QUESTION)
        // Repeated notifications about the same work collapse into one item; messages and grades don't.
        val key = if (work || kind == ClassKind.MATERIAL) "$kind|${course.lowercase()}|${itemTitle.lowercase()}" else "$kind|${course.lowercase()}|${itemTitle.lowercase()}|${postedAt / 60_000}"
        return ClassItem("n" + key.hashCode().toUInt().toString(16), "NOTIFICATION", kind, course, itemTitle, body, due, postedAt,
            state = if (work) WorkState.PENDING else WorkState.NONE)
    }

    /** Same coursework seen from two sources (e.g. a notification, then the sync). */
    fun sameThing(a: ClassItem, b: ClassItem): Boolean = a.kind == b.kind && a.kind != ClassKind.COMMENT && a.kind != ClassKind.GRADE &&
        norm(a.title) == norm(b.title) && (norm(a.course) == norm(b.course) || a.course == "Classroom" || b.course == "Classroom")

    private fun norm(s: String) = s.lowercase().replace(Regex("""[^a-z0-9]"""), "")

    private val stop = setOf("lab", "b", "tech", "btech", "sem", "semester", "the", "and", "of", "to", "in", "for", "section", "sec", "batch", "div", "class", "course", "i", "ii", "iii", "iv", "a", "b", "c", "d")
    private fun words(s: String) = s.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.length > 1 && it !in stop }.toSet()

    /** Best Campus subject for a Classroom course: your override, then subject code, then shared words / initials. */
    fun matchSubject(course: String, subjects: List<Subject>, overrides: Map<String, Long>): Subject? {
        overrides[course]?.let { id -> subjects.firstOrNull { it.id == id }?.let { return it } }
        val c = course.lowercase()
        subjects.firstOrNull { it.code.isNotBlank() && c.contains(it.code.lowercase().replace(" ", "")) || it.code.isNotBlank() && c.replace(" ", "").contains(it.code.lowercase().replace(" ", "")) }?.let { return it }
        val cw = words(course)
        val ordered = course.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.length > 1 && it !in stop }
        return subjects.map { s ->
            val sw = words(s.name)
            val overlap = if (sw.isEmpty()) 0.0 else sw.intersect(cw).size.toDouble() / sw.size
            val short = s.name.lowercase().replace(" ", "")
            val acronym = if (short.length in 2..6 && (cw.contains(short) || ordered.indices.any { acronymOf(short, ordered, 0, it) })) 1.0 else 0.0
            s to maxOf(overlap, acronym)
        }.filter { it.second >= 0.5 }.maxByOrNull { it.second }?.first
    }

    /**
     * "dbms" from "database management systems": consecutive words each give their first letter plus up
     * to two later letters in order (DataBase → "db"), together spelling the acronym.
     */
    private fun acronymOf(acr: String, words: List<String>, i: Int = 0, w: Int = 0): Boolean {
        if (i == acr.length) return true
        if (w >= words.size || words[w].first() != acr[i]) return false
        for (len in 1..minOf(3, acr.length - i)) {
            if (isSubsequence(acr.substring(i + 1, i + len), words[w].drop(1)) && acronymOf(acr, words, i + len, w + 1)) return true
        }
        return false
    }

    private fun isSubsequence(part: String, word: String): Boolean {
        var k = 0
        for (ch in word) if (k < part.length && ch == part[k]) k++
        return k == part.length
    }

    fun isOpenWork(i: ClassItem, now: Long) = !i.hidden && !i.done && i.kind in setOf(ClassKind.ASSIGNMENT, ClassKind.QUIZ, ClassKind.QUESTION) &&
        i.state != WorkState.SUBMITTED && i.state != WorkState.RETURNED && i.snoozedUntil <= now

    fun important(i: ClassItem, words: List<String>): Boolean {
        val t = (i.title + " " + i.body).lowercase()
        return words.any { it.isNotBlank() && t.contains(it.lowercase()) }
    }

    /** Ranks what needs attention: open work by urgency and weight, then unread messages, grades and key announcements. */
    fun rank(items: List<ClassItem>, now: LocalDateTime, s: ClassroomSettings): List<Ranked> {
        val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return items.filter { !it.hidden && it.snoozedUntil <= nowMs }.mapNotNull { i ->
            val weight = when (i.kind) { ClassKind.QUIZ -> 1.3; ClassKind.ASSIGNMENT -> 1.0; ClassKind.QUESTION -> .6; else -> .4 }
            if (isOpenWork(i, nowMs)) {
                val due = i.dueAt
                if (due == null) return@mapNotNull Ranked(i, 30 * weight, "No due date")
                val hours = (due - nowMs) / 3_600_000.0
                val (score, reason) = when {
                    hours < 0 -> (120 + minOf(-hours, 72.0) / 3) to "Overdue by ${human(-hours)}"
                    hours < 24 -> (100 - hours) to "Due in ${human(hours)}"
                    hours < s.urgentHours -> (80 - hours / 4) to "Due ${dayWord(due, now)}"
                    else -> (60 - minOf(hours / 24, 30.0)) to "Due ${dayWord(due, now)}"
                }
                Ranked(i, score * weight, reason)
            } else if (!i.read && !i.done && i.kind !in setOf(ClassKind.ASSIGNMENT, ClassKind.QUIZ, ClassKind.QUESTION)) {
                when {
                    i.kind == ClassKind.COMMENT -> Ranked(i, 55.0, "New message")
                    i.kind == ClassKind.GRADE -> Ranked(i, 45.0, "Graded")
                    important(i, s.importantWords) -> Ranked(i, 50.0, "Important")
                    i.kind == ClassKind.MATERIAL -> Ranked(i, 25.0, "New material")
                    else -> Ranked(i, 20.0, "New")
                }
            } else null
        }.sortedByDescending { it.score }
    }

    private fun human(hours: Double) = if (hours < 1) "${(hours * 60).toInt().coerceAtLeast(1)}m" else if (hours < 48) "${hours.toInt()}h" else "${(hours / 24).toInt()}d"

    fun dayWord(ms: Long, now: LocalDateTime): String {
        val t = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val d = ChronoUnit.DAYS.between(now.toLocalDate(), t.toLocalDate())
        val clock = "%02d:%02d".format(t.hour, t.minute)
        return when (d) { 0L -> "today $clock"; 1L -> "tomorrow $clock"; in 2..6 -> t.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase) + " $clock"; else -> "${t.dayOfMonth} ${t.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)}" }
    }

    /**
     * Study targets for today: each open piece of work gets its expected effort spread evenly over the
     * days left (finishing the day before it's due when possible), capped per day, soonest first.
     */
    fun studyTargets(items: List<ClassItem>, now: LocalDateTime, s: ClassroomSettings): List<StudyTarget> {
        val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var budget = s.dailyStudyCapMinutes
        return items.filter { isOpenWork(it, nowMs) && it.dueAt != null && it.dueAt > nowMs }.sortedBy { it.dueAt }.mapNotNull { i ->
            val total = s.effort(i.kind).takeIf { it > 0 } ?: return@mapNotNull null
            val dueDay = java.time.Instant.ofEpochMilli(i.dueAt!!).atZone(ZoneId.systemDefault()).toLocalDate()
            val daysLeft = ChronoUnit.DAYS.between(now.toLocalDate(), dueDay)
            // Finish the day before it's due: work due tomorrow is all for today.
            val spread = daysLeft.coerceAtLeast(1)
            val today = (Math.ceil(total.toDouble() / spread / 5) * 5).toInt().coerceAtMost(budget)
            if (today <= 0) return@mapNotNull null
            budget -= today
            StudyTarget(i, today, total, daysLeft)
        }
    }

    private val cancel = Regex("""\b(no (?:class|lecture|lab)|(?:class|lecture|lab)(?:es)?\s+(?:is\s+|are\s+|will be\s+|stands\s+)?(?:cancel+ed|suspended|off)|will not be (?:a |any )?(?:class|lecture)|not take (?:the )?(?:class|lecture))""", opt)
    private val exam = Regex("""\b(quiz|test|exam|viva|mid[- ]?sem|end[- ]?sem|minor|major|surprise test)\b""", opt)
    private val extra = Regex("""\b(extra|make[- ]?up|additional|compensatory|remedial)\s+(class|lecture|lab)\b""", opt)
    private val extended = Regex("""\b(extended|postponed|rescheduled|shifted)\b""", opt)

    /** Actions worth taking from announcements and messages (class cancelled, a test announced…). */
    fun suggestions(items: List<ClassItem>, subjects: List<Subject>, s: ClassroomSettings, today: LocalDate, handled: Set<String>): List<Suggestion> =
        items.filter { !it.hidden && it.kind in setOf(ClassKind.ANNOUNCEMENT, ClassKind.COMMENT, ClassKind.OTHER, ClassKind.MATERIAL) }.mapNotNull { i ->
            val text = i.title + "\n" + i.body
            val date = DateFinder.date(text, java.time.Instant.ofEpochMilli(i.postedAt).atZone(ZoneId.systemDefault()).toLocalDate()) ?: return@mapNotNull null
            if (date.isBefore(today)) return@mapNotNull null
            val kind = when {
                cancel.containsMatchIn(text) -> Suggestion.Kind.CANCEL_CLASS
                extra.containsMatchIn(text) -> Suggestion.Kind.EXTRA_CLASS
                extended.containsMatchIn(text) -> Suggestion.Kind.EXTENDED
                exam.containsMatchIn(text) -> Suggestion.Kind.EXAM
                else -> return@mapNotNull null
            }
            val id = "${i.id}|$kind|$date"
            if (id in handled) null else Suggestion(id, kind, i.id, i.course, date, i.title.ifBlank { i.body.take(80) }, matchSubject(i.course, subjects, s.courseMap)?.id)
        }.distinctBy { it.id }

    /** Should this new item interrupt now, or wait for the digest? */
    fun alertNow(i: ClassItem, now: LocalDateTime, s: ClassroomSettings): Boolean {
        if (!s.instantAlerts) return false
        val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return when {
            isOpenWork(i, nowMs) -> i.kind == ClassKind.QUIZ || (i.dueAt != null && i.dueAt - nowMs < s.urgentHours * 3_600_000L)
            i.kind == ClassKind.COMMENT || i.kind == ClassKind.GRADE -> true
            else -> important(i, s.importantWords)
        }
    }

    /** One-line smart summary of an item for notifications. */
    fun explain(i: ClassItem, now: LocalDateTime, s: ClassroomSettings): String {
        val due = i.dueAt?.let { " — due ${dayWord(it, now)}" } ?: ""
        val effort = s.effort(i.kind).takeIf { it > 0 && i.kind in setOf(ClassKind.ASSIGNMENT, ClassKind.QUIZ, ClassKind.QUESTION) }
        val plan = effort?.let { e -> studyTargets(listOf(i), now, s).firstOrNull()?.let { t -> " · ~${e / 60}h${if (e % 60 > 0) " ${e % 60}m" else ""} of work, ${t.minutesToday} min today keeps you on track" } } ?: ""
        return "${i.kind.icon} ${i.course}: ${i.title}$due$plan"
    }

    /** Morning digest lines. */
    fun digest(items: List<ClassItem>, now: LocalDateTime, s: ClassroomSettings, since: Long): List<String> {
        val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val open = items.filter { isOpenWork(it, nowMs) }
        val overdue = open.count { it.dueAt != null && it.dueAt < nowMs }
        val soon = open.filter { it.dueAt != null && it.dueAt >= nowMs && it.dueAt - nowMs < 48 * 3_600_000L }
        val fresh = items.filter { it.firstSeen > since && !it.hidden }
        val targets = studyTargets(items, now, s)
        return buildList {
            add("${open.size} pending" + (if (overdue > 0) " · $overdue overdue" else "") + (if (soon.isNotEmpty()) " · ${soon.size} due in 48h" else "") + (if (fresh.isNotEmpty()) " · ${fresh.size} new" else ""))
            soon.sortedBy { it.dueAt }.take(3).forEach { add("⏰ ${it.course}: ${it.title} — ${dayWord(it.dueAt!!, now)}") }
            if (targets.isNotEmpty()) add("🎯 Today: " + targets.joinToString(", ") { "${it.minutesToday}m ${it.item.title.take(24)}" })
            fresh.filter { it.kind == ClassKind.COMMENT || important(it, s.importantWords) }.take(2).forEach { add("${it.kind.icon} ${it.course}: ${it.title.take(60)}") }
        }
    }
}
