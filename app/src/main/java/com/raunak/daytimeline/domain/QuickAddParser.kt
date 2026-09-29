package com.raunak.daytimeline.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Offline natural-language quick add.
 *
 * Understands, in any order:
 *  - ranges: "7-9", "7:30pm-9", "from 19:00 to 20:30"
 *  - start times: "at 7", "7pm", "19:30", "@7"
 *  - durations: "2h", "1h30m", "90m", "for 2 hours", "for 45 minutes"
 *  - dates: today, tomorrow, "day after tomorrow", weekday names, "next monday",
 *    "in 3 days", "next week", ISO "2026-10-02", "2/10" (day/month)
 *  - recurrence: "daily", "every day", "every weekday", "every weekend", "weekly",
 *    "every monday and thursday", "every mon, wed"
 *  - priority: "!1".."!4", "p1".."p4", "!!!"/"urgent"
 *  - tags: "#dsa #study"
 *  - reminders: "remind 10m before", "remind me 1h before", "remind at start"
 *  - "pomodoro" / "focus" enables a focus session
 */
object QuickAddParser {
    private const val DEFAULT_START = 9 * 60
    private const val DEFAULT_DURATION = 60

    private val weekdayNames = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY
    )
    private val dayAlt = weekdayNames.keys.sortedByDescending { it.length }.joinToString("|")

    private val opt = setOf(RegexOption.IGNORE_CASE)
    private val time = """(\d{1,2})(?::(\d{2}))?\s*(am|pm)?"""

    private val reminder = Regex("""\bremind(?:\s+me)?\s+(?:(\d+)\s*(m|min|mins|minutes|h|hr|hrs|hours?)\s+before|at\s+start)\b""", opt)
    private val every = Regex("""\bevery\s+((?:(?:$dayAlt)(?:\s*(?:,|and|&)\s*)?)+)\b""", opt)
    private val everyWeekday = Regex("""\bevery\s+(?:weekday|weekdays|work\s*day|workday)\b""", opt)
    private val everyWeekend = Regex("""\bevery\s+weekend\b""", opt)
    private val daily = Regex("""\b(?:daily|every\s*day|everyday)\b""", opt)
    private val weekly = Regex("""\b(?:weekly|every\s+week)\b""", opt)
    private val fromTo = Regex("""\bfrom\s+$time\s+(?:to|until|till)\s+$time\b""", opt)
    private val range = Regex("""(?<![\d/:-])$time\s*-\s*$time(?![\d/])""", opt)
    private val forDuration = Regex("""\bfor\s+(\d+(?:\.\d+)?)\s*(h|hr|hrs|hours?|m|min|mins|minutes?)\b""", opt)
    private val compactDuration = Regex("""\b(\d{1,2})h(?:\s*(\d{1,2})m)?\b|\b(\d{1,3})\s?m(?:in|ins|inutes?)?\b""", opt)
    private val atTime = Regex("""(?:\bat\s+|@)$time\b""", opt)
    private val bareTime = Regex("""\b(\d{1,2})(?::(\d{2}))\s*(am|pm)?\b|\b(\d{1,2})\s*(am|pm)\b""", opt)
    private val isoDate = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val dayMonth = Regex("""\b(\d{1,2})/(\d{1,2})\b""")
    private val inDays = Regex("""\bin\s+(\d+)\s+(days?|weeks?)\b""", opt)
    private val nextWeekday = Regex("""\b(?:next|this|on)?\s*($dayAlt)\b""", opt)
    private val priority = Regex("""(?:^|\s)(?:!([1-4])|p([1-4]))(?=\s|$)""", opt)
    private val urgent = Regex("""(?:^|\s)(?:!!!|urgent)(?=\s|$)""", opt)
    private val tag = Regex("""(?:^|\s)#([\p{L}\p{N}_-]+)""")
    private val focusWord = Regex("""\b(?:pomodoro|focus)\b""", opt)

    fun parse(raw: String, nowDate: LocalDate): QuickTaskInput? {
        var text = " " + raw.trim() + " "
        if (raw.isBlank()) return null

        fun take(regex: Regex): MatchResult? = regex.find(text)?.also { text = text.replaceRange(it.range, " ") }

        // Reminder
        var reminderMode = "NONE"
        var reminderOffset = 0
        take(reminder)?.let { m ->
            if (m.groupValues[1].isBlank()) {
                reminderMode = "AT_START"
            } else {
                val n = m.groupValues[1].toInt()
                reminderOffset = if (m.groupValues[2].startsWith("h", true)) n * 60 else n
                reminderMode = "BEFORE"
            }
        }

        // Recurrence
        var recurrenceType = "NONE"
        var recurrenceDays = ""
        take(everyWeekday)?.let { recurrenceType = "WEEKDAYS" }
        if (recurrenceType == "NONE") take(everyWeekend)?.let { recurrenceType = "WEEKENDS" }
        if (recurrenceType == "NONE") take(daily)?.let { recurrenceType = "DAILY" }
        if (recurrenceType == "NONE") take(every)?.let { m ->
            val days = Regex(dayAlt, opt).findAll(m.groupValues[1]).mapNotNull { weekdayNames[it.value.lowercase()]?.value }.distinct().sorted().toList()
            if (days.isNotEmpty()) { recurrenceType = "CUSTOM_DAYS"; recurrenceDays = days.joinToString(",") }
        }
        if (recurrenceType == "NONE") take(weekly)?.let { recurrenceType = "WEEKLY" }

        // Times and durations
        var start: Int? = null
        var end: Int? = null
        (take(fromTo) ?: take(range))?.let { m ->
            val g = m.groupValues
            val s = toMinute(g[1], g[2], g[3])
            var e = toMinute(g[4], g[5], g[6].ifBlank { g[3] })
            if (e <= s) e += 12 * 60
            start = s; end = e
        }
        var duration: Int? = null
        take(forDuration)?.let { m ->
            val n = m.groupValues[1].toDouble()
            duration = if (m.groupValues[2].startsWith("h", true)) (n * 60).toInt() else n.toInt()
        }
        if (duration == null && start == null) take(compactDuration)?.let { m ->
            duration = if (m.groupValues[3].isNotBlank()) m.groupValues[3].toInt()
            else m.groupValues[1].toInt() * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
        }
        if (start == null) (take(atTime))?.let { m -> start = toMinute(m.groupValues[1], m.groupValues[2], m.groupValues[3], guessPm = true) }
        if (start == null) take(bareTime)?.let { m ->
            start = if (m.groupValues[1].isNotBlank()) toMinute(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            else toMinute(m.groupValues[4], "", m.groupValues[5])
        }

        // Dates
        var date = nowDate
        when {
            Regex("""\bday after tomorrow\b""", opt).containsMatchIn(text) -> { take(Regex("""\bday after tomorrow\b""", opt)); date = nowDate.plusDays(2) }
            Regex("""\btomorrow\b|\btmrw\b|\btmr\b""", opt).containsMatchIn(text) -> { take(Regex("""\btomorrow\b|\btmrw\b|\btmr\b""", opt)); date = nowDate.plusDays(1) }
            Regex("""\btoday\b|\btonight\b""", opt).containsMatchIn(text) -> {
                val m = take(Regex("""\btoday\b|\btonight\b""", opt))
                if (m?.value.equals("tonight", true) && start == null) start = 20 * 60
            }
            Regex("""\bnext week\b""", opt).containsMatchIn(text) -> { take(Regex("""\bnext week\b""", opt)); date = nowDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) }
            else -> {
                val iso = take(isoDate)
                val dm = if (iso == null) take(dayMonth) else null
                val inN = if (iso == null && dm == null) take(inDays) else null
                when {
                    iso != null -> runCatching { LocalDate.of(iso.groupValues[1].toInt(), iso.groupValues[2].toInt(), iso.groupValues[3].toInt()) }.getOrNull()?.let { date = it }
                    dm != null -> runCatching {
                        var d = LocalDate.of(nowDate.year, dm.groupValues[2].toInt(), dm.groupValues[1].toInt())
                        if (d.isBefore(nowDate)) d = d.plusYears(1)
                        d
                    }.getOrNull()?.let { date = it }
                    inN != null -> {
                        val n = inN.groupValues[1].toLong()
                        date = if (inN.groupValues[2].startsWith("week", true)) nowDate.plusWeeks(n) else nowDate.plusDays(n)
                    }
                    recurrenceType == "NONE" -> take(nextWeekday)?.let { m ->
                        val dow = weekdayNames.getValue(m.groupValues[1].lowercase())
                        val isNext = m.value.trim().startsWith("next", true)
                        date = if (isNext) nowDate.with(TemporalAdjusters.next(dow)) else nowDate.with(TemporalAdjusters.nextOrSame(dow))
                    }
                }
            }
        }
        if (recurrenceType == "CUSTOM_DAYS") {
            val first = recurrenceDays.split(",").map { DayOfWeek.of(it.toInt()) }
                .map { nowDate.with(TemporalAdjusters.nextOrSame(it)) }.minOrNull()
            if (first != null && date == nowDate) date = first
        }

        // Priority and tags
        var prio = 1
        take(priority)?.let { m ->
            val level = (m.groupValues[1].ifBlank { m.groupValues[2] }).toInt()
            // p1 is the most urgent; the app stores higher numbers as more important (0..3)
            prio = (4 - level).coerceIn(0, 3)
        }
        take(urgent)?.let { prio = 3 }
        val tags = tag.findAll(text).map { it.groupValues[1] }.toList()
        text = text.replace(tag, " ")
        val pomodoro = focusWord.containsMatchIn(text)
        text = text.replace(focusWord, " ")

        val s = (start ?: DEFAULT_START).coerceIn(0, 24 * 60 - 5)
        val e = (end ?: (s + (duration ?: DEFAULT_DURATION))).coerceIn(s + 5, 24 * 60)

        val title = text
            .replace(Regex("""\b(?:at|on|from|for|by)\s*$""", opt), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim(',', '-', ':')
            .trim()
            .ifBlank { "Quick Task" }

        return QuickTaskInput(
            title = title,
            date = date,
            startMinute = s,
            endMinute = e,
            pomodoro = pomodoro,
            priority = prio,
            tags = tags.joinToString(","),
            recurrenceType = recurrenceType,
            recurrenceDays = recurrenceDays,
            reminderMode = reminderMode,
            reminderOffsetMinutes = reminderOffset
        )
    }

    private fun toMinute(hourS: String, minuteS: String, ampm: String, guessPm: Boolean = false): Int {
        var hour = hourS.toInt() % 24
        val minute = minuteS.takeIf { it.isNotBlank() }?.toInt()?.coerceIn(0, 59) ?: 0
        val marker = ampm.lowercase()
        if (marker == "pm" && hour in 1..11) hour += 12
        if (marker == "am" && hour == 12) hour = 0
        // "at 7" with no marker: assume the next sensible occurrence during waking hours
        if (marker.isBlank() && guessPm && hour in 1..6) hour += 12
        return hour * 60 + minute
    }
}
