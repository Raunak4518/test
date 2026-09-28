package com.raunak.daytimeline.domain

object TimelineLayoutEngine {
    fun place(tasks: List<TaskModel>): List<TimelinePlacement> {
        if (tasks.isEmpty()) return emptyList()
        val sorted = tasks.sortedBy { it.startMinute }
        val active = mutableListOf<TimelinePlacement>()
        val out = mutableListOf<TimelinePlacement>()

        sorted.forEach { task ->
            active.removeAll { it.topMinute + it.durationMinutes <= task.startMinute }
            val used = active.map { it.column }.toSet()
            var col = 0
            while (col in used) col++
            val tentative = TimelinePlacement(task, task.startMinute, (task.endMinute - task.startMinute).coerceAtLeast(5), col, 1)
            active += tentative
            val groupCols = (active.maxOfOrNull { it.column } ?: 0) + 1
            for (i in active.indices) active[i] = active[i].copy(columns = groupCols)
            out.removeAll { prior -> active.any { it.task.id == prior.task.id } }
            out += active
        }
        return out.distinctBy { it.task.id }
    }
}
