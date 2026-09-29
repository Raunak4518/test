package com.raunak.daytimeline.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raunak.daytimeline.domain.ConflictDetector
import com.raunak.daytimeline.domain.QuickAddParser
import com.raunak.daytimeline.domain.RecurrenceEngine
import com.raunak.daytimeline.domain.TaskModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class TaskRepository(
    private val taskDao: TaskDao,
    private val checklistDao: ChecklistDao,
    private val pomodoroDao: PomodoroDao,
    private val reminderScheduler: ReminderScheduler
) {
    private val gson = Gson()

    fun observeAllTasks(): Flow<List<TaskModel>> = taskDao.observeAll().map { entities -> entities.map { it.toModel(LocalDate.ofEpochDay(it.dateEpochDay)) } }

    fun observeTasks(date: LocalDate): Flow<List<TaskModel>> {
        val dateEpoch = date.toEpochDay()
        return taskDao.observeForDate(dateEpoch).map { entities ->
            entities.filter { RecurrenceEngine.occursOn(it, date) }.map { it.toModel(date) }
        }
    }

    suspend fun addTask(task: TaskEntity): Long {
        val id = taskDao.insert(task)
        val inserted = task.copy(id = id)
        reminderScheduler.schedule(inserted)
        return id
    }

    suspend fun byId(id: Long): TaskEntity? = taskDao.byId(id)

    suspend fun updateTask(task: TaskEntity) {
        // Callers build a fresh entity; keep per-occurrence completions and creation time.
        val existing = taskDao.byId(task.id)
        val merged = if (existing == null) task else task.copy(completedDates = existing.completedDates, createdAt = existing.createdAt)
        taskDao.update(merged)
        reminderScheduler.schedule(task)
    }

    suspend fun deleteTask(taskId: Long) {
        reminderScheduler.cancel(taskId)
        taskDao.delete(taskId)
    }

    suspend fun duplicateTask(taskId: Long): Long {
        val existing = taskDao.byId(taskId) ?: return 0
        return addTask(existing.copy(id = 0, title = "${existing.title} (copy)", completed = false))
    }

    /** Repeating tasks are completed per occurrence ([date]); one-off tasks as a whole. */
    suspend fun markComplete(taskId: Long, completed: Boolean, date: LocalDate? = null) {
        val task = taskDao.byId(taskId) ?: return
        if (task.recurrenceType != "NONE" && date != null) {
            val days = task.doneDays().toMutableSet()
            if (completed) days += date.toEpochDay() else days -= date.toEpochDay()
            taskDao.update(task.copy(completedDates = days.sorted().takeLast(800).joinToString(",")))
        } else taskDao.update(task.copy(completed = completed))
    }

    /** Moves a one-off task to [date], keeping its time. */
    suspend fun reschedule(taskId: Long, date: LocalDate) {
        val task = taskDao.byId(taskId) ?: return
        if (task.recurrenceType != "NONE") return
        val moved = task.copy(dateEpochDay = date.toEpochDay())
        taskDao.update(moved)
        reminderScheduler.schedule(moved)
    }

    /**
     * Every task occurrence between [from] and [to]: one-off tasks on their date, repeating tasks on
     * each day they occur from today on (past repeats are not listed as overdue).
     */
    fun observeAgenda(from: LocalDate, to: LocalDate, today: LocalDate = LocalDate.now()): Flow<List<TaskModel>> = taskDao.observeAll().map { all ->
        all.flatMap { e ->
            if (e.recurrenceType == "NONE") {
                val d = LocalDate.ofEpochDay(e.dateEpochDay)
                if (d.isBefore(from) || d.isAfter(to)) emptyList() else listOf(e.toModel(d))
            } else {
                generateSequence(maxOf(from, today)) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
                    .filter { RecurrenceEngine.occursOn(e, it) }.map { e.toModel(it) }.toList()
            }
        }.sortedWith(compareBy<TaskModel> { it.date }.thenBy { it.startMinute })
    }

    fun checklist(taskId: Long): Flow<List<ChecklistItemEntity>> = checklistDao.observeForTask(taskId)

    suspend fun upsertChecklistItem(item: ChecklistItemEntity) { checklistDao.insert(item) }

    suspend fun toggleChecklistItem(item: ChecklistItemEntity) {
        checklistDao.update(item.copy(checked = !item.checked))
    }

    suspend fun deleteChecklistItem(itemId: Long) { checklistDao.delete(itemId) }

    fun observePomodoro() = pomodoroDao.observe().map { it ?: PomodoroStateEntity() }

    suspend fun savePomodoro(state: PomodoroStateEntity) = pomodoroDao.upsert(state)

    suspend fun quickAdd(input: String, date: LocalDate): Long? {
        val parsed = QuickAddParser.parse(input, date) ?: return null
        val task = TaskEntity(
            title = parsed.title,
            dateEpochDay = parsed.date.toEpochDay(),
            startMinute = parsed.startMinute,
            endMinute = parsed.endMinute,
            pomodoroEnabled = parsed.pomodoro,
            priority = parsed.priority,
            tags = parsed.tags,
            recurrenceType = parsed.recurrenceType,
            recurrenceDays = parsed.recurrenceDays,
            reminderMode = parsed.reminderMode,
            reminderOffsetMinutes = parsed.reminderOffsetMinutes
        )
        return addTask(task)
    }

    suspend fun detectConflicts(candidate: TaskEntity): Int {
        val sameDay = taskDao.forExactDate(candidate.dateEpochDay)
        return ConflictDetector.maxOverlapMinutes(candidate, sameDay.filter { it.id != candidate.id })
    }

    suspend fun exportJson(): String {
        val tasks = taskDao.all()
        val checklist = checklistDao.forTasks(tasks.map { it.id })
        return gson.toJson(mapOf("tasks" to tasks, "checklist" to checklist))
    }

    suspend fun importJson(json: String): Result<Unit> = runCatching {
        val mapType = object : TypeToken<Map<String, List<Map<String, Any>>>>() {}.type
        val parsed: Map<String, List<Map<String, Any>>> = gson.fromJson(json, mapType)
        val tasksRaw = parsed["tasks"].orEmpty()
        val checklistRaw = parsed["checklist"].orEmpty()
        tasksRaw.forEach { raw ->
            val id = addTask(
                TaskEntity(
                    title = raw["title"] as? String ?: "Task",
                    dateEpochDay = (raw["dateEpochDay"] as? Number)?.toLong() ?: LocalDate.now().toEpochDay(),
                    startMinute = (raw["startMinute"] as? Number)?.toInt() ?: 540,
                    endMinute = (raw["endMinute"] as? Number)?.toInt() ?: 600,
                    category = raw["category"] as? String ?: "Other",
                    priority = (raw["priority"] as? Number)?.toInt() ?: 1,
                    notes = raw["notes"] as? String ?: "",
                    pomodoroEnabled = raw["pomodoroEnabled"] as? Boolean ?: false,
                    tags = raw["tags"] as? String ?: "",
                    reminderMode = raw["reminderMode"] as? String ?: "NONE",
                    reminderOffsetMinutes = (raw["reminderOffsetMinutes"] as? Number)?.toInt() ?: 0,
                    completed = raw["completed"] as? Boolean ?: false,
                    recurrenceType = raw["recurrenceType"] as? String ?: "NONE",
                    recurrenceDays = raw["recurrenceDays"] as? String ?: "",
                    completedDates = raw["completedDates"] as? String ?: ""
                )
            )
            checklistRaw
                .filter { (it["taskId"] as? Number)?.toLong() == (raw["id"] as? Number)?.toLong() }
                .forEachIndexed { index, cl ->
                    checklistDao.insert(
                        ChecklistItemEntity(
                            taskId = id,
                            text = cl["text"] as? String ?: "",
                            checked = cl["checked"] as? Boolean ?: false,
                            position = index
                        )
                    )
                }
        }
    }

    suspend fun autoSchedule(date: LocalDate, dayStartMinute: Int, dayEndMinute: Int): Int {
        val existing = taskDao.forExactDate(date.toEpochDay()).filterNot { it.completed }
            .sortedWith(compareByDescending<TaskEntity> { it.priority }.thenBy { it.startMinute })
        var cursor = dayStartMinute.coerceIn(0, 1439)
        val boundary = dayEndMinute.coerceIn(cursor + 5, 1440)
        var changed = 0
        existing.forEach { task ->
            val duration = (task.endMinute - task.startMinute).coerceIn(5, 240)
            val start = cursor.coerceAtMost((boundary - 5).coerceAtLeast(cursor))
            val end = (start + duration).coerceAtMost(boundary)
            if (end > start && (task.startMinute != start || task.endMinute != end)) {
                taskDao.update(task.copy(startMinute = start, endMinute = end))
                reminderScheduler.schedule(task.copy(startMinute = start, endMinute = end))
                changed++
            }
            cursor = (end + 15).coerceAtMost(boundary)
            if (cursor >= boundary) return@forEach
        }
        return changed
    }

    suspend fun rescheduleAllReminders() {
        taskDao.all().forEach { task ->
            if (task.reminderMode != "NONE") reminderScheduler.schedule(task)
        }
    }
}

private fun TaskEntity.toModel(date: LocalDate): TaskModel = TaskModel(
    id = id,
    title = title,
    date = date,
    startMinute = startMinute,
    endMinute = endMinute,
    category = category,
    colorHex = colorHex,
    priority = priority,
    notes = notes,
    pomodoroEnabled = pomodoroEnabled,
    tags = tags,
    reminderMode = reminderMode,
    reminderOffsetMinutes = reminderOffsetMinutes,
    completed = if (recurrenceType != "NONE") date.toEpochDay() in doneDays() else completed,
    recurrenceType = recurrenceType,
    recurrenceDays = recurrenceDays
)

@Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
private fun TaskEntity.doneDays(): Set<Long> = (completedDates ?: "").split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
