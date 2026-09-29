package com.raunak.daytimeline.campus

import java.time.LocalDate

/** What the alarm screen shows: a daily quote plus today's first class and deadlines. */
object WakeBriefing {
    /** Same quote all day, a different one each day, cycling through the list. */
    fun quote(settings: CampusSettings, date: LocalDate): String? =
        settings.wakeQuotes.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { it[(date.toEpochDay() % it.size).toInt()] }

    fun lines(data: CampusData, date: LocalDate): List<String> {
        if (data.settings.wakeBriefingOff) return emptyList()
        val classes = AttendanceEngine.occurrences(data, date).sortedBy { it.start }
        val first = classes.firstOrNull()?.let { o ->
            val name = data.subjects.firstOrNull { it.id == o.subjectId }?.name ?: "Class"
            "First class: $name at ${clock(o.start)}" + (o.room.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") + " · ${classes.size} today"
        } ?: "No classes today"
        val due = data.deadlines.filter { !it.done && it.date == date.toString() }.sortedBy { it.minute }
            .map { "Due today: ${it.title} (${it.label}) by ${clock(it.minute)}" }
        return listOf(first) + due.take(3)
    }
}
