package com.raunak.daytimeline.features

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Local-only productivity engine. No network, account, API key or backend is required.
 * This deliberately keeps the secondary productivity model independent from the Room task model
 * so it can be evolved without destructive database migrations.
 */
class OfflineProductivityStore(context: Context) {
    private val prefs = context.getSharedPreferences("offline_productivity_v2", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val habitScheduler = HabitReminderScheduler(context.applicationContext)

    private val _habits = MutableStateFlow(read("habits", emptyList<OfflineHabit>()))
    val habits: StateFlow<List<OfflineHabit>> = _habits.asStateFlow()
    private val _goals = MutableStateFlow(read("goals", emptyList<OfflineGoal>()))
    val goals: StateFlow<List<OfflineGoal>> = _goals.asStateFlow()
    private val _routines = MutableStateFlow(read("routines", defaultRoutines()))
    val routines: StateFlow<List<OfflineRoutine>> = _routines.asStateFlow()
    private val _projects = MutableStateFlow(read("projects", emptyList<OfflineProject>()))
    val projects: StateFlow<List<OfflineProject>> = _projects.asStateFlow()
    private val _timeEntries = MutableStateFlow(read("timeEntries", emptyList<OfflineTimeEntry>()))
    val timeEntries: StateFlow<List<OfflineTimeEntry>> = _timeEntries.asStateFlow()
    private val _achievements = MutableStateFlow(read("achievements", emptyList<OfflineAchievement>()))
    val achievements: StateFlow<List<OfflineAchievement>> = _achievements.asStateFlow()
    private val _journal = MutableStateFlow(read("journal", emptyList<OfflineJournalEntry>()))
    val journal: StateFlow<List<OfflineJournalEntry>> = _journal.asStateFlow()
    private val _challenges = MutableStateFlow(read("challenges", defaultChallenges()))
    val challenges: StateFlow<List<OfflineChallenge>> = _challenges.asStateFlow()
    private val _settings = MutableStateFlow(read("settings", OfflineSettings()))
    val settings: StateFlow<OfflineSettings> = _settings.asStateFlow()
    private val _notes = MutableStateFlow(read("notes", emptyList<OfflineNote>()))
    val notes: StateFlow<List<OfflineNote>> = _notes.asStateFlow()

    fun addHabit(name: String, targetPerWeek: Int = 7, preferredTime: String = "", activeDays: Set<Int> = (1..7).toSet()) {
        if (name.isBlank()) return
        update(_habits, "habits") { it + OfflineHabit(id(), name.trim(), targetPerWeek.coerceIn(1, 7), preferredTime, activeDays.ifEmpty { (1..7).toSet() }, emptySet(), createdDate = LocalDate.now().toString()) }.also { _habits.value.lastOrNull()?.let(habitScheduler::schedule) }
    }

    fun updateHabit(id: Long, name: String, targetPerWeek: Int, preferredTime: String, activeDays: Set<Int> = (1..7).toSet()) {
        habitScheduler.cancel(id)
        update(_habits, "habits") { list -> list.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }, targetPerWeek = targetPerWeek.coerceIn(1, 7), preferredTime = preferredTime, activeDays = activeDays.ifEmpty { (1..7).toSet() }) else it } }
        _habits.value.firstOrNull { it.id == id }?.let(habitScheduler::schedule)
    }

    /** Adds or replaces a habit with every field (frequency, colour, note…) and reschedules its reminder. */
    fun saveHabit(habit: OfflineHabit) {
        if (habit.name.isBlank()) return
        val clean = habit.copy(
            id = if (habit.id == 0L) id() else habit.id,
            name = habit.name.trim(),
            targetPerWeek = habit.targetPerWeek.coerceIn(1, 7),
            intervalDays = habit.intervalDays.coerceIn(2, 60),
            activeDays = habit.activeDays.ifEmpty { (1..7).toSet() },
            createdDate = habit.createdDate ?: LocalDate.now().toString()
        )
        habitScheduler.cancel(clean.id)
        update(_habits, "habits") { list -> if (list.any { it.id == clean.id }) list.map { if (it.id == clean.id) clean else it } else list + clean }
        if (!clean.archived) habitScheduler.schedule(clean)
    }

    /** Marks [date] as skipped (or clears the skip); a skip also clears a completion on that day. */
    fun skipHabit(id: Long, date: LocalDate = LocalDate.now()) = update(_habits, "habits") { list ->
        list.map { h ->
            if (h.id != id) h else {
                val key = date.toString()
                val skipped = HabitEngine.skipped(h).toMutableSet()
                if (!skipped.add(key)) skipped.remove(key)
                h.copy(skippedDates = skipped, completedDates = HabitEngine.done(h) - key)
            }
        }
    }

    fun archiveHabit(id: Long, archived: Boolean) {
        update(_habits, "habits") { list -> list.map { if (it.id == id) it.copy(archived = archived) else it } }
        _habits.value.firstOrNull { it.id == id }?.let { if (archived) habitScheduler.cancel(id) else habitScheduler.schedule(it) }
    }

    fun deleteHabit(id: Long) { habitScheduler.cancel(id); update(_habits, "habits") { it.filterNot { h -> h.id == id } } }

    fun toggleHabit(id: Long, date: LocalDate = LocalDate.now()) = update(_habits, "habits") { list ->
        list.map { h ->
            if (h.id != id) h else {
                val key = date.toString()
                val done = HabitEngine.done(h).toMutableSet()
                if (!done.add(key)) done.remove(key)
                h.copy(completedDates = done, skippedDates = HabitEngine.skipped(h) - key)
            }
        }
    }

    fun habitStreak(habit: OfflineHabit, today: LocalDate = LocalDate.now()): Int {
        var cursor = today
        var streak = 0
        while (habit.completedDates.contains(cursor.toString())) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun addNote(title: String, body: String, tags: Set<String> = emptySet(), folder: String = "General") {
        if (title.isBlank() && body.isBlank()) return
        update(_notes, "notes") { it + OfflineNote(id(), title.trim().ifBlank { "Untitled" }, body, tags, System.currentTimeMillis(), folder.trim().ifBlank { "General" }, false) }
    }

    fun updateNote(id: Long, title: String, body: String, tags: Set<String>, folder: String = "General", pinned: Boolean? = null) = update(_notes, "notes") { list ->
        list.map { if (it.id == id) it.copy(title = title.trim().ifBlank { "Untitled" }, body = body, tags = tags, updatedAt = System.currentTimeMillis(), folder = folder.trim().ifBlank { "General" }, pinned = pinned ?: it.pinned) else it }
    }

    fun toggleNotePinned(id: Long) = update(_notes, "notes") { list -> list.map { if (it.id == id) it.copy(pinned = !it.pinned) else it } }
    fun setNoteFolder(id: Long, folder: String) = update(_notes, "notes") { list -> list.map { if (it.id == id) it.copy(folder = folder.trim().ifBlank { "General" }) else it } }

    fun noteBacklinks(note: OfflineNote): List<OfflineNote> {
        val title = note.title.trim()
        if (title.isBlank()) return emptyList()
        return _notes.value.filter { it.id != note.id && it.body.contains(title, ignoreCase = true) }
    }

    fun deleteNote(id: Long) = update(_notes, "notes") { it.filterNot { n -> n.id == id } }

    fun addGoal(title: String, target: Int, deadline: LocalDate? = null) {
        if (title.isBlank()) return
        update(_goals, "goals") { it + OfflineGoal(id(), title.trim(), 0, target.coerceAtLeast(1), deadline?.toString(), emptyList(), false) }
    }

    fun updateGoal(id: Long, title: String, target: Int, deadline: LocalDate?, milestones: List<String>) = update(_goals, "goals") { list ->
        list.map { if (it.id == id) it.copy(title = title.trim().ifBlank { it.title }, target = target.coerceAtLeast(1), deadline = deadline?.toString(), milestones = milestones.filter { m -> m.isNotBlank() }, milestoneDone = it.milestoneDone.filter { index -> index < milestones.size }.toSet()) else it }
    }

    fun toggleGoalMilestone(id: Long, index: Int) = update(_goals, "goals") { list ->
        list.map { goal ->
            if (goal.id != id || index !in goal.milestones.indices) goal else {
                val done = goal.milestoneDone.toMutableSet()
                if (!done.add(index)) done.remove(index)
                goal.copy(milestoneDone = done)
            }
        }
    }

    fun addGoalMilestone(id: Long, milestone: String) = update(_goals, "goals") { list ->
        list.map { if (it.id == id && milestone.isNotBlank()) it.copy(milestones = it.milestones + milestone.trim()) else it }
    }

    fun removeGoalMilestone(id: Long, index: Int) = update(_goals, "goals") { list ->
        list.map { if (it.id == id && index in it.milestones.indices) it.copy(milestones = it.milestones.toMutableList().also { ms -> ms.removeAt(index) }) else it }
    }

    fun deleteGoal(id: Long) = update(_goals, "goals") { it.filterNot { g -> g.id == id } }

    fun setGoalProgress(id: Long, progress: Int) = update(_goals, "goals") { list ->
        list.map { if (it.id == id) it.copy(progress = progress.coerceIn(0, it.target), completed = progress >= it.target) else it }
    }

    fun addProject(name: String, color: Long = 0xFF55786A) {
        if (name.isBlank()) return
        update(_projects, "projects") { it + OfflineProject(id(), name.trim(), color, emptyList(), null) }
    }

    fun updateProject(id: Long, name: String, deadline: LocalDate?, color: Long) = update(_projects, "projects") { list ->
        list.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }, deadline = deadline?.toString(), color = color) else it }
    }

    fun archiveProject(id: Long) = update(_projects, "projects") { list ->
        list.filterNot { it.id == id }
    }

    fun deleteProject(id: Long) = update(_projects, "projects") { it.filterNot { p -> p.id == id } }

    fun addRoutine(name: String, steps: List<OfflineRoutineStep>) {
        if (name.isBlank() || steps.isEmpty()) return
        update(_routines, "routines") { it + OfflineRoutine(id(), name.trim(), steps, false) }
    }

    fun updateRoutine(id: Long, name: String, steps: List<OfflineRoutineStep>) = update(_routines, "routines") { list ->
        list.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }, steps = steps) else it }
    }

    fun deleteRoutine(id: Long) = update(_routines, "routines") { it.filterNot { r -> r.id == id } }

    fun setRoutineCompleted(id: Long, date: LocalDate = LocalDate.now()) = update(_routines, "routines") { list ->
        list.map { if (it.id == id) it.copy(lastCompletedDate = date.toString(), completionDates = it.completionDates + date.toString()) else it }
    }

    fun toggleRoutineStep(id: Long, stepIndex: Int, date: LocalDate = LocalDate.now()) = update(_routines, "routines") { list ->
        list.map { routine ->
            if (routine.id != id || stepIndex !in routine.steps.indices) routine else {
                val key = date.toString() + ":" + stepIndex
                val done = routine.completedSteps.toMutableSet()
                if (!done.add(key)) done.remove(key)
                routine.copy(completedSteps = done)
            }
        }
    }

    fun startTimeEntry(label: String, projectId: Long? = null): OfflineTimeEntry {
        val entry = OfflineTimeEntry(id(), label.trim().ifBlank { "Untitled" }, projectId, System.currentTimeMillis(), null, "", emptySet())
        update(_timeEntries, "timeEntries") { it + entry }
        return entry
    }

    fun stopTimeEntry(id: Long, note: String = "") = update(_timeEntries, "timeEntries") { list ->
        list.map { if (it.id == id && it.endEpochMillis == null) it.copy(endEpochMillis = System.currentTimeMillis(), note = note) else it }
    }

    fun addManualTime(label: String, start: LocalDateTime, end: LocalDateTime, projectId: Long? = null, tags: Set<String> = emptySet()) {
        if (end.isBefore(start) || label.isBlank()) return
        update(_timeEntries, "timeEntries") { it + OfflineTimeEntry(id(), label.trim(), projectId, start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), end.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), "", tags) }
    }

    fun todayTrackedMinutes(today: LocalDate = LocalDate.now()): Long {
        val start = today.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return _timeEntries.value.sumOf { e ->
            val s = maxOf(e.startEpochMillis, start)
            val finish = minOf(e.endEpochMillis ?: System.currentTimeMillis(), end)
            ((finish - s).coerceAtLeast(0L) / 60_000L)
        }
    }

    fun addJournal(date: LocalDate, mood: Int, energy: Int, wins: String, blockers: String, gratitude: String, note: String) {
        update(_journal, "journal") { list ->
            val entry = OfflineJournalEntry(date.toString(), mood.coerceIn(1, 5), energy.coerceIn(1, 5), wins, blockers, gratitude, note)
            (list.filterNot { it.date == entry.date } + entry).sortedByDescending { it.date }.take(365)
        }
    }

    fun addChallenge(title: String, description: String, target: Int) {
        if (title.isBlank() || target <= 0) return
        update(_challenges, "challenges") { it + OfflineChallenge(id(), title.trim(), description.trim(), target, 0) }
    }

    fun updateChallenge(id: Long, title: String, description: String, target: Int) = update(_challenges, "challenges") { list ->
        list.map { if (it.id == id) it.copy(title = title.trim().ifBlank { it.title }, description = description, target = target.coerceAtLeast(1), progress = it.progress.coerceAtMost(target)) else it }
    }

    fun deleteChallenge(id: Long) = update(_challenges, "challenges") { it.filterNot { c -> c.id == id } }

    fun completeChallenge(id: Long) = update(_challenges, "challenges") { list -> list.map { if (it.id == id) it.copy(progress = (it.progress + 1).coerceAtMost(it.target)) else it } }

    fun awardAchievement(key: String, title: String, description: String) = update(_achievements, "achievements") { list ->
        if (list.any { it.key == key }) list else list + OfflineAchievement(key, title, description, System.currentTimeMillis())
    }

    fun updateSettings(transform: (OfflineSettings) -> OfflineSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit().putString("settings", gson.toJson(next)).apply()
    }

    fun exportJson(): String = gson.toJson(OfflineBackup(
        schema = 2,
        exportedAt = System.currentTimeMillis(),
        habits = _habits.value,
        goals = _goals.value,
        routines = _routines.value,
        projects = _projects.value,
        timeEntries = _timeEntries.value,
        achievements = _achievements.value,
        journal = _journal.value,
        challenges = _challenges.value,
        settings = _settings.value,
        notes = _notes.value
    ))

    /** Replaces the secondary local store only after the complete JSON has parsed successfully. */
    fun importJson(json: String): Result<Unit> = runCatching {
        val backup = gson.fromJson(json, OfflineBackup::class.java) ?: error("Empty backup")
        require(backup.schema in 1..2) { "Unsupported backup schema ${backup.schema}" }
        val habits = backup.habits ?: emptyList()
        val goals = backup.goals ?: emptyList()
        val routines = backup.routines ?: defaultRoutines()
        val projects = backup.projects ?: emptyList()
        val entries = backup.timeEntries ?: emptyList()
        val achievements = backup.achievements ?: emptyList()
        val journal = backup.journal ?: emptyList()
        val challenges = backup.challenges ?: defaultChallenges()
        val settings = backup.settings ?: OfflineSettings()
        val notes = backup.notes ?: emptyList()
        prefs.edit()
            .putString("habits", gson.toJson(habits))
            .putString("goals", gson.toJson(goals))
            .putString("routines", gson.toJson(routines))
            .putString("projects", gson.toJson(projects))
            .putString("timeEntries", gson.toJson(entries))
            .putString("achievements", gson.toJson(achievements))
            .putString("journal", gson.toJson(journal))
            .putString("challenges", gson.toJson(challenges))
            .putString("settings", gson.toJson(settings))
            .putString("notes", gson.toJson(notes))
            .apply()
        _habits.value = habits
        _goals.value = goals
        _routines.value = routines
        _projects.value = projects
        _timeEntries.value = entries
        _achievements.value = achievements
        _journal.value = journal
        _challenges.value = challenges
        _settings.value = settings
        _notes.value = notes
    }

    fun rescheduleHabitReminders() { _habits.value.forEach(habitScheduler::schedule) }

    fun resetAll() {
        prefs.edit().clear().apply()
        _habits.value = emptyList()
        _goals.value = emptyList()
        _routines.value = defaultRoutines()
        _projects.value = emptyList()
        _timeEntries.value = emptyList()
        _achievements.value = emptyList()
        _journal.value = emptyList()
        _challenges.value = defaultChallenges()
        _settings.value = OfflineSettings()
        _notes.value = emptyList()
    }

    private fun id(): Long = System.currentTimeMillis() * 1000 + ((0..999).random())
    private fun <T> update(flow: MutableStateFlow<List<T>>, key: String, transform: (List<T>) -> List<T>) {
        val next = transform(flow.value)
        flow.value = next
        prefs.edit().putString(key, gson.toJson(next)).apply()
    }
    private inline fun <reified T> read(key: String, fallback: T): T = try {
        prefs.getString(key, null)?.let { gson.fromJson<T>(it, object : TypeToken<T>() {}.type) } ?: fallback
    } catch (_: Exception) { fallback }

    private fun defaultRoutines() = listOf(
        OfflineRoutine(1, "Morning Reset", listOf(OfflineRoutineStep("Hydrate", 5), OfflineRoutineStep("Move", 20), OfflineRoutineStep("Plan", 10)), false),
        OfflineRoutine(2, "Deep Work", listOf(OfflineRoutineStep("Prepare", 5), OfflineRoutineStep("Focus", 50), OfflineRoutineStep("Break", 10), OfflineRoutineStep("Focus", 50), OfflineRoutineStep("Review", 5)), false),
        OfflineRoutine(3, "Night Shutdown", listOf(OfflineRoutineStep("Clear tasks", 10), OfflineRoutineStep("Journal", 10), OfflineRoutineStep("Plan tomorrow", 10), OfflineRoutineStep("Wind down", 20)), false)
    )

    private fun defaultChallenges() = listOf(
        OfflineChallenge(1, "First Focus", "Complete one focus session", 1, 0),
        OfflineChallenge(2, "Three Wins", "Complete three planned tasks", 3, 0),
        OfflineChallenge(3, "Deep Day", "Track 120 minutes of focused work", 120, 0)
    )
}

data class OfflineHabit(
    val id: Long,
    val name: String,
    val targetPerWeek: Int,
    val preferredTime: String,
    val activeDays: Set<Int> = (1..7).toSet(),
    val completedDates: Set<String> = emptySet(),
    /** A [HabitFrequency] name. */
    val frequency: String = "DAYS",
    val intervalDays: Int = 2,
    val color: Long = 0xFF55786A,
    /** Days deliberately skipped (ill, travelling); they never break a streak. */
    val skippedDates: Set<String> = emptySet(),
    val createdDate: String? = null,
    val archived: Boolean = false,
    /** Why this habit matters, shown under its name. */
    val note: String = ""
)
data class OfflineGoal(val id: Long, val title: String, val progress: Int, val target: Int, val deadline: String?, val milestones: List<String>, val completed: Boolean, val milestoneDone: Set<Int> = emptySet())
data class OfflineProject(val id: Long, val name: String, val color: Long, val taskIds: List<Long>, val deadline: String?)
data class OfflineRoutine(val id: Long, val name: String, val steps: List<OfflineRoutineStep>, val archived: Boolean, val lastCompletedDate: String? = null, val completionDates: Set<String> = emptySet(), val completedSteps: Set<String> = emptySet())
data class OfflineRoutineStep(val title: String, val minutes: Int)
data class OfflineTimeEntry(val id: Long, val label: String, val projectId: Long?, val startEpochMillis: Long, val endEpochMillis: Long?, val note: String, val tags: Set<String>)
data class OfflineAchievement(val key: String, val title: String, val description: String, val unlockedAt: Long)
data class OfflineJournalEntry(val date: String, val mood: Int, val energy: Int, val wins: String, val blockers: String, val gratitude: String, val note: String)
data class OfflineChallenge(val id: Long, val title: String, val description: String, val target: Int, val progress: Int)
data class OfflineSettings(
    val theme: String = "SYSTEM",
    val weekStartsMonday: Boolean = true,
    val dayStartMinute: Int = 360,
    val dayEndMinute: Int = 1440,
    val snapMinutes: Int = 15,
    val haptics: Boolean = true,
    val sounds: Boolean = true,
    val autoScrollNow: Boolean = true,
    val showCompleted: Boolean = true,
    val defaultTaskMinutes: Int = 30,
    val defaultFocusMinutes: Int = 25,
    /** Eisenhower matrix: tasks due within this many days (and overdue ones) are urgent. */
    val matrixUrgentDays: Int = 1,
    /** Eisenhower matrix: tasks at or above this priority (0–3) are important. */
    val matrixImportantPriority: Int = 2,
    /** Eisenhower matrix: how far ahead to look, in days. */
    val matrixHorizonDays: Int = 14,
    /** Keep a pinned "Add a task" notification with a text box. */
    val pinnedQuickAdd: Boolean = false,
    /** Days shown in the Upcoming view. */
    val upcomingDays: Int = 7
)
data class OfflineNote(val id: Long, val title: String, val body: String, val tags: Set<String>, val updatedAt: Long, val folder: String = "General", val pinned: Boolean = false)

data class OfflineBackup(
    val schema: Int,
    val exportedAt: Long,
    val habits: List<OfflineHabit>?,
    val goals: List<OfflineGoal>?,
    val routines: List<OfflineRoutine>?,
    val projects: List<OfflineProject>?,
    val timeEntries: List<OfflineTimeEntry>?,
    val achievements: List<OfflineAchievement>?,
    val journal: List<OfflineJournalEntry>?,
    val challenges: List<OfflineChallenge>?,
    val settings: OfflineSettings?,
    val notes: List<OfflineNote>? = null
)

fun OfflineHabit.streak(today: LocalDate = LocalDate.now()): Int {
    var cursor = today
    var count = 0
    while (completedDates.contains(cursor.toString())) {
        count++
        cursor = cursor.minusDays(1)
    }
    return count
}

fun OfflineHabit.weekCompletion(today: LocalDate = LocalDate.now()): Int {
    val monday = today.with(DayOfWeek.MONDAY)
    return (0L..6L).count { completedDates.contains(monday.plusDays(it).toString()) }
}
