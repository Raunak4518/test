package com.raunak.daytimeline.domain

import com.raunak.daytimeline.data.TaskEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object RecurrenceEngine {
    fun occursOn(task: TaskEntity, date: LocalDate): Boolean {
        val base = LocalDate.ofEpochDay(task.dateEpochDay)
        if (date.isBefore(base)) return false
        if (date.toEpochDay() == task.dateEpochDay) return true
        return when (task.recurrenceType) {
            "DAILY" -> true
            "WEEKDAYS" -> date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            "WEEKENDS" -> date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            "WEEKLY" -> date.dayOfWeek == base.dayOfWeek
            "CUSTOM_DAYS" -> {
                val selected = task.recurrenceDays.split(',').mapNotNull { it.toIntOrNull() }.toSet()
                date.dayOfWeek.value in selected
            }
            else -> false
        }
    }
}
