package com.raunak.daytimeline.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Quadrant(val title: String, val hint: String, val urgent: Boolean, val important: Boolean) {
    DO("Do first", "Urgent and important", true, true),
    SCHEDULE("Schedule", "Important, not urgent", false, true),
    DELEGATE("Delegate / batch", "Urgent, less important", true, false),
    DROP("Later / drop", "Neither", false, false)
}

/** Sorts open tasks into the four Eisenhower quadrants using editable thresholds. */
object EisenhowerEngine {
    fun quadrant(task: TaskModel, today: LocalDate, urgentDays: Int, importantPriority: Int): Quadrant {
        val urgent = ChronoUnit.DAYS.between(today, task.date) <= urgentDays
        val important = task.priority >= importantPriority
        return Quadrant.values().first { it.urgent == urgent && it.important == important }
    }

    /** Open tasks from [horizonDays] overdue up to [horizonDays] ahead, grouped by quadrant, soonest first. */
    fun group(tasks: List<TaskModel>, today: LocalDate, urgentDays: Int, importantPriority: Int, horizonDays: Int): Map<Quadrant, List<TaskModel>> {
        val open = tasks.filter { !it.completed && !it.date.isAfter(today.plusDays(horizonDays.toLong())) && !it.date.isBefore(today.minusDays(horizonDays.toLong())) }
        val grouped = open.groupBy { quadrant(it, today, urgentDays, importantPriority) }
        return Quadrant.values().associateWith { q -> grouped[q].orEmpty().sortedWith(compareBy<TaskModel> { it.date }.thenBy { it.startMinute }) }
    }
}
