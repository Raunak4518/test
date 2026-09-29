package com.raunak.daytimeline.domain

import java.time.LocalDate

data class TaskModel(
    val id: Long,
    val title: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val category: String,
    val colorHex: Long,
    val priority: Int,
    val notes: String,
    val pomodoroEnabled: Boolean,
    val tags: String,
    val reminderMode: String,
    val reminderOffsetMinutes: Int,
    val completed: Boolean,
    val recurrenceType: String,
    val recurrenceDays: String
)

data class QuickTaskInput(
    val title: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val pomodoro: Boolean,
    val priority: Int = 1,
    val tags: String = "",
    val recurrenceType: String = "NONE",
    val recurrenceDays: String = "",
    val reminderMode: String = "NONE",
    val reminderOffsetMinutes: Int = 0
)

data class TimelinePlacement(
    val task: TaskModel,
    val topMinute: Int,
    val durationMinutes: Int,
    val column: Int,
    val columns: Int
)
