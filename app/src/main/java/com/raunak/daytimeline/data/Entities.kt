package com.raunak.daytimeline.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val category: String = "Other",
    val colorHex: Long = 0xFF5A6CF3,
    val priority: Int = 1,
    val notes: String = "",
    val pomodoroEnabled: Boolean = false,
    val tags: String = "",
    val reminderMode: String = "NONE",
    val reminderOffsetMinutes: Int = 0,
    val completed: Boolean = false,
    val recurrenceType: String = "NONE",
    val recurrenceDays: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val text: String,
    val checked: Boolean = false,
    val position: Int = 0
)

@Entity(tableName = "pomodoro_state")
data class PomodoroStateEntity(
    @PrimaryKey val id: Long = 1,
    val taskId: Long? = null,
    val phase: String = "IDLE",
    val targetEpochMillis: Long = 0,
    val remainingSeconds: Long = 0,
    val cycleIndex: Int = 0,
    val running: Boolean = false,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cyclesPerRound: Int = 4
)
