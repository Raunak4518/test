package com.raunak.daytimeline.domain

import java.time.LocalDate

object QuickAddParser {
    private val range = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*-\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
    private val duration = Regex("""(\d{1,2})h(?:\s*(\d{1,2})m)?""", RegexOption.IGNORE_CASE)

    fun parse(raw: String, nowDate: LocalDate): QuickTaskInput? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val pomodoro = text.contains("pomodoro", true)
        val date = if (text.contains("tomorrow", true)) nowDate.plusDays(1) else nowDate
        val rangeMatch = range.find(text)
        val durMatch = duration.find(text)

        val (start, end) = when {
            rangeMatch != null -> {
                val g = rangeMatch.groupValues
                val s = toMinute(g[1], g[2], g[3])
                val e = toMinute(g[4], g[5], g[6])
                s to if (e <= s) e + 12 * 60 else e
            }
            durMatch != null -> {
                val hours = durMatch.groupValues[1].toInt()
                val mins = durMatch.groupValues[2].takeIf { it.isNotBlank() }?.toInt() ?: 0
                val start = 9 * 60
                start to (start + hours * 60 + mins)
            }
            else -> 9 * 60 to 10 * 60
        }

        val title = text
            .replace(range, "")
            .replace(duration, "")
            .replace("pomodoro", "", true)
            .replace("tomorrow", "", true)
            .trim()
            .ifBlank { "Quick Task" }

        return QuickTaskInput(title, date, start.coerceAtLeast(0), end.coerceAtMost(24 * 60), pomodoro)
    }

    private fun toMinute(hourS: String, minuteS: String, ampm: String): Int {
        var hour = hourS.toInt() % 24
        val minute = minuteS.takeIf { it.isNotBlank() }?.toInt() ?: 0
        val marker = ampm.lowercase()
        if (marker == "pm" && hour in 1..11) hour += 12
        if (marker == "am" && hour == 12) hour = 0
        return hour * 60 + minute
    }
}
