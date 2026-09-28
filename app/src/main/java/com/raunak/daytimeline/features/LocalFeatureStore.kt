package com.raunak.daytimeline.features

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Small, offline-first store for secondary productivity features. No server or account required. */
class LocalFeatureStore(context: Context) {
    private val prefs = context.getSharedPreferences("productivity_features", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _habits = MutableStateFlow(read("habits", emptyList<Habit>()))
    val habits: StateFlow<List<Habit>> = _habits.asStateFlow()
    private val _notes = MutableStateFlow(read("notes", emptyList<QuickNote>()))
    val notes: StateFlow<List<QuickNote>> = _notes.asStateFlow()
    private val _goals = MutableStateFlow(read("goals", emptyList<Goal>()))
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()
    private val _templates = MutableStateFlow(read("templates", defaultTemplates()))
    val templates: StateFlow<List<PlanTemplate>> = _templates.asStateFlow()
    private val _reviews = MutableStateFlow(read("reviews", emptyList<DailyReview>()))
    val reviews: StateFlow<List<DailyReview>> = _reviews.asStateFlow()
    private val _focusSessions = MutableStateFlow(read("focus", emptyList<FocusLog>()))
    val focusSessions: StateFlow<List<FocusLog>> = _focusSessions.asStateFlow()

    fun addHabit(name: String, target: Int = 1) = update(_habits, "habits") { it + Habit(nextId(it), name.trim(), target.coerceAtLeast(1), emptyList()) }
    fun deleteHabit(id: Long) = update(_habits, "habits") { it.filterNot { h -> h.id == id } }
    fun toggleHabit(id: Long, date: LocalDate) = update(_habits, "habits") { list -> list.map { h -> if (h.id != id) h else { val key = date.toString(); val days = h.completedDates.toMutableList(); if (key in days) days.remove(key) else days.add(key); h.copy(completedDates = days.takeLast(366)) } } }

    fun addNote(title: String, body: String, tags: String = "") = update(_notes, "notes") { it + QuickNote(nextId(it), title.trim().ifBlank { "Untitled" }, body, tags, System.currentTimeMillis()) }
    fun updateNote(note: QuickNote) = update(_notes, "notes") { list -> list.map { if (it.id == note.id) note else it } }
    fun deleteNote(id: Long) = update(_notes, "notes") { it.filterNot { n -> n.id == id } }

    fun addGoal(title: String, target: Int = 100) = update(_goals, "goals") { it + Goal(nextId(it), title.trim(), 0, target.coerceAtLeast(1), System.currentTimeMillis()) }
    fun incrementGoal(id: Long, amount: Int = 1) = update(_goals, "goals") { list -> list.map { if (it.id == id) it.copy(progress = (it.progress + amount).coerceAtMost(it.target)) else it } }
    fun deleteGoal(id: Long) = update(_goals, "goals") { it.filterNot { g -> g.id == id } }

    fun addTemplate(name: String, tasks: List<TemplateTask>) = update(_templates, "templates") { it + PlanTemplate(nextId(it), name.trim(), tasks) }
    fun deleteTemplate(id: Long) = update(_templates, "templates") { it.filterNot { t -> t.id == id } }

    fun saveReview(review: DailyReview) = update(_reviews, "reviews") { list -> (list.filterNot { it.date == review.date } + review).sortedByDescending { it.date }.take(90) }
    fun logFocus(taskId: Long?, minutes: Int) = update(_focusSessions, "focus") { it + FocusLog(nextId(it), taskId, minutes.coerceAtLeast(1), System.currentTimeMillis()) }

    private fun <T> update(flow: MutableStateFlow<List<T>>, key: String, transform: (List<T>) -> List<T>) {
        val next = transform(flow.value)
        flow.value = next
        prefs.edit().putString(key, gson.toJson(next)).apply()
    }
    private inline fun <reified T> read(key: String, fallback: T): T = try {
        prefs.getString(key, null)?.let { gson.fromJson<T>(it, object : TypeToken<T>() {}.type) } ?: fallback
    } catch (_: Exception) { fallback }
    private fun <T> nextId(list: List<T>): Long = System.currentTimeMillis() + list.size
    private fun defaultTemplates() = listOf(
        PlanTemplate(1, "Deep Work", listOf(TemplateTask("Plan", 10), TemplateTask("Focus", 50), TemplateTask("Break", 10), TemplateTask("Focus", 50), TemplateTask("Review", 10))),
        PlanTemplate(2, "Study Session", listOf(TemplateTask("Warm up", 10), TemplateTask("Focus", 45), TemplateTask("Break", 10), TemplateTask("Focus", 45), TemplateTask("Recall", 15))),
        PlanTemplate(3, "Morning Routine", listOf(TemplateTask("Hydrate", 5), TemplateTask("Exercise", 30), TemplateTask("Shower", 15), TemplateTask("Plan day", 10)))
    )
}

data class Habit(val id: Long, val name: String, val targetPerDay: Int, val completedDates: List<String>)
data class QuickNote(val id: Long, val title: String, val body: String, val tags: String, val updatedAt: Long)
data class Goal(val id: Long, val title: String, val progress: Int, val target: Int, val createdAt: Long)
data class TemplateTask(val title: String, val minutes: Int)
data class PlanTemplate(val id: Long, val name: String, val tasks: List<TemplateTask>)
data class DailyReview(val date: String, val wins: String, val blockers: String, val gratitude: String, val score: Int)
data class FocusLog(val id: Long, val taskId: Long?, val minutes: Int, val timestamp: Long)
