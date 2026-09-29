package com.raunak.daytimeline.features

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raunak.daytimeline.domain.TaskModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Offline completion layer for the remaining productivity workflows.
 * Uses local persistence so the app remains useful without an account or network.
 */
class CompletionStore(context: Context) {
    private val prefs = context.getSharedPreferences("chronora_completion", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun dependencies(): List<TaskDependency> = read("dependencies", emptyList())
    fun setDependencies(list: List<TaskDependency>) =
        write("dependencies", list.distinctBy { "\${it.taskId}:\${it.dependsOnTaskId}" })

    fun addDependency(taskId: Long, dependsOnTaskId: Long): Boolean {
        if (taskId == dependsOnTaskId) return false
        val current = dependencies()
        if (current.any { it.taskId == taskId && it.dependsOnTaskId == dependsOnTaskId }) return true
        if (DependencyGraph(current).wouldCycle(taskId, dependsOnTaskId)) return false
        setDependencies(current + TaskDependency(taskId, dependsOnTaskId))
        return true
    }

    fun removeDependency(taskId: Long, dependsOnTaskId: Long) =
        setDependencies(dependencies().filterNot { it.taskId == taskId && it.dependsOnTaskId == dependsOnTaskId })

    fun templates(): List<AdvancedTemplate> = read("advanced_templates", defaultTemplates())
    fun saveTemplate(template: AdvancedTemplate) {
        saveTemplateInternal(template)
    }
    private fun saveTemplateInternal(template: AdvancedTemplate) {
        val next = templates().filterNot { it.id == template.id } + template
        write("advanced_templates", next)
    }
    fun deleteTemplate(id: Long) = write("advanced_templates", templates().filterNot { it.id == id })

    fun reviews(): List<ReviewRecord> = read("reviews", emptyList())
    fun saveReview(review: ReviewRecord) {
        write("reviews", (reviews().filterNot { it.date == review.date } + review).sortedByDescending { it.date }.take(366))
    }

    fun studyCards(): List<StudyCard> = read("study_cards", emptyList())
    fun saveCard(card: StudyCard) {
        write("study_cards", studyCards().filterNot { it.id == card.id } + card)
    }
    fun deleteCard(id: Long) = write("study_cards", studyCards().filterNot { it.id == id })

    fun exams(): List<ExamPlan> = read("exams", emptyList())
    fun saveExam(exam: ExamPlan) = write("exams", exams().filterNot { it.id == exam.id } + exam)
    fun deleteExam(id: Long) = write("exams", exams().filterNot { it.id == id })

    fun focusShield(): FocusShieldConfig = read("focus_shield", FocusShieldConfig())
    fun saveFocusShield(config: FocusShieldConfig) = write("focus_shield", config)

    fun appLockEnabled(): Boolean = prefs.getBoolean("app_lock", false)
    fun setAppLockEnabled(enabled: Boolean) = prefs.edit().putBoolean("app_lock", enabled).apply()

    fun blockedPackages(): Set<String> = prefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
    fun setBlockedPackages(packages: Set<String>) = prefs.edit().putStringSet("blocked_packages", packages).apply()

    fun onboardingComplete(): Boolean = prefs.getBoolean("onboarding", false)
    fun setOnboardingComplete() = prefs.edit().putBoolean("onboarding", true).apply()

    fun theme(): String = prefs.getString("theme", "SYSTEM") ?: "SYSTEM"
    fun setTheme(theme: String) = prefs.edit().putString("theme", theme).apply()

    private inline fun <reified T> read(key: String, fallback: T): T = try {
        prefs.getString(key, null)?.let { gson.fromJson<T>(it, object : TypeToken<T>() {}.type) } ?: fallback
    } catch (_: Exception) { fallback }

    private fun write(key: String, value: Any) {
        prefs.edit().putString(key, gson.toJson(value)).apply()
    }

    private fun defaultTemplates() = listOf(
        AdvancedTemplate(1, "Deep Work", listOf(TemplateBlock("Plan", 10), TemplateBlock("Focus", 50), TemplateBlock("Break", 10), TemplateBlock("Focus", 50), TemplateBlock("Review", 10))),
        AdvancedTemplate(2, "Exam Revision", listOf(TemplateBlock("Recall", 20), TemplateBlock("Practice", 50), TemplateBlock("Break", 10), TemplateBlock("Practice", 50), TemplateBlock("Error log", 20))),
        AdvancedTemplate(3, "Morning Reset", listOf(TemplateBlock("Hydrate", 5), TemplateBlock("Move", 20), TemplateBlock("Plan", 10))),
        AdvancedTemplate(4, "Shutdown", listOf(TemplateBlock("Clear inbox", 10), TemplateBlock("Review", 10), TemplateBlock("Plan tomorrow", 15), TemplateBlock("Journal", 10)))
    )
}

data class TaskDependency(val taskId: Long, val dependsOnTaskId: Long)

class DependencyGraph(private val edges: List<TaskDependency>) {
    fun dependenciesOf(taskId: Long): Set<Long> =
        edges.filter { it.taskId == taskId }.map { it.dependsOnTaskId }.toSet()

    fun wouldCycle(taskId: Long, dependsOn: Long): Boolean {
        val seen = mutableSetOf<Long>()
        val queue = ArrayDeque<Long>()
        queue.add(dependsOn)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node == taskId) return true
            if (!seen.add(node)) continue
            edges.filter { it.taskId == node }.forEach { queue.add(it.dependsOnTaskId) }
        }
        return false
    }

    fun isBlocked(task: TaskModel, allTasks: List<TaskModel>): Boolean {
        val byId = allTasks.associateBy { it.id }
        return dependenciesOf(task.id).any { byId[it]?.completed != true }
    }

    fun blockers(task: TaskModel, allTasks: List<TaskModel>): List<TaskModel> =
        dependenciesOf(task.id).mapNotNull { id -> allTasks.firstOrNull { it.id == id && !it.completed } }
}

data class AdvancedTemplate(val id: Long, val name: String, val blocks: List<TemplateBlock>)
data class TemplateBlock(val title: String, val minutes: Int)

data class ReviewRecord(
    val date: String,
    val wins: String,
    val blockers: String,
    val gratitude: String,
    val score: Int,
    val nextPriority: String = ""
)

data class StudyCard(
    val id: Long,
    val front: String,
    val back: String,
    val deck: String = "Default",
    val dueEpochDay: Long = LocalDate.now().toEpochDay(),
    val intervalDays: Int = 1,
    val ease: Double = 2.5,
    val repetitions: Int = 0,
    val lapses: Int = 0
)

data class ExamPlan(
    val id: Long,
    val name: String,
    val date: String,
    val topics: List<String>,
    val targetHours: Int = 10
)

data class FocusShieldConfig(
    val enabled: Boolean = false,
    val endEpochMillis: Long = 0L,
    val strict: Boolean = false,
    val blockedPackages: Set<String> = emptySet()
)

data class AnalyticsSnapshot(
    val totalTasks: Int,
    val completedTasks: Int,
    val completionPercent: Int,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val focusMinutes: Int,
    val habitCompletions: Int,
    val activeGoals: Int,
    val streak: Int,
    val workloadScore: Int
)

object ProductivityAnalyticsEngine {
    fun snapshot(
        tasks: List<TaskModel>,
        habits: List<OfflineHabit>,
        goals: List<OfflineGoal>,
        timeEntries: List<OfflineTimeEntry>,
        today: LocalDate = LocalDate.now()
    ): AnalyticsSnapshot {
        val planned = tasks.sumOf { max(0, it.endMinute - it.startMinute) }
        val completed = tasks.filter { it.completed }.sumOf { max(0, it.endMinute - it.startMinute) }
        val focus = timeEntries.filter { entry ->
            java.time.Instant.ofEpochMilli(entry.startEpochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today
        }.sumOf { entry ->
            val end = entry.endEpochMillis ?: System.currentTimeMillis()
            max(0, ((end - entry.startEpochMillis) / 60_000L).toInt())
        }
        val habitCompletions = habits.count { today.toString() in it.completedDates }
        val streak = habits.maxOfOrNull { it.streak(today) } ?: 0
        val workload = WorkloadEngine.score(tasks, today)
        return AnalyticsSnapshot(
            tasks.size,
            tasks.count { it.completed },
            if (tasks.isEmpty()) 0 else tasks.count { it.completed } * 100 / tasks.size,
            planned,
            completed,
            focus,
            habitCompletions,
            goals.count { !it.completed },
            streak,
            workload
        )
    }
}

object WorkloadEngine {
    fun score(tasks: List<TaskModel>, date: LocalDate): Int {
        val minutes = tasks.sumOf { max(0, it.endMinute - it.startMinute) }
        val highPriority = tasks.count { it.priority >= 2 }
        val overdue = tasks.count { !it.completed && it.date.isBefore(date) }
        return (minutes / 6 + highPriority * 8 + overdue * 12).coerceIn(0, 100)
    }

    fun rebalance(tasks: List<TaskModel>, dayStart: Int, dayEnd: Int): List<TaskModel> {
        if (tasks.isEmpty()) return tasks
        val sorted = tasks.sortedWith(compareByDescending<TaskModel> { it.priority }.thenBy { it.startMinute })
        val capacity = (dayEnd - dayStart).coerceAtLeast(15)
        if (sorted.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(15) } <= capacity) return tasks
        var cursor = dayStart
        return sorted.map { task ->
            val duration = (task.endMinute - task.startMinute).coerceIn(15, 180)
            val start = cursor.coerceAtMost(dayEnd - 15)
            cursor = (start + duration + 15).coerceAtMost(dayEnd)
            task.copy(startMinute = start, endMinute = min(dayEnd, start + duration))
        }
    }
}

object RecurrencePlanner {
    fun occurrences(start: LocalDate, type: String, days: Set<Int> = emptySet(), count: Int = 30): List<LocalDate> {
        if (count <= 0) return emptyList()
        val result = mutableListOf<LocalDate>()
        var cursor = start
        while (result.size < count) {
            val day = cursor.dayOfWeek.value
            val include = when (type.uppercase()) {
                "DAILY" -> true
                "WEEKDAYS" -> day in 1..5
                "WEEKENDS" -> day >= 6
                "WEEKLY" -> days.isEmpty() || day in days
                "BIWEEKLY" -> days.isEmpty() || (day in days && ChronoUnit.WEEKS.between(start, cursor) % 2L == 0L)
                "MONTHLY" -> cursor.dayOfMonth == start.dayOfMonth
                else -> cursor == start
            }
            if (include) result += cursor
            cursor = cursor.plusDays(1)
            if (cursor.isAfter(start.plusYears(2))) break
        }
        return result
    }
}

object SrsEngine {
    fun grade(card: StudyCard, quality: Int, today: LocalDate = LocalDate.now()): StudyCard {
        val q = quality.coerceIn(0, 5)
        if (q < 3) {
            return card.copy(dueEpochDay = today.toEpochDay() + 1, intervalDays = 1, ease = max(1.3, card.ease - 0.2), repetitions = 0, lapses = card.lapses + 1)
        }
        val nextEase = max(1.3, card.ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))
        val nextInterval = when (card.repetitions) {
            0 -> 1
            1 -> 6
            else -> max(1, (card.intervalDays * nextEase).toInt())
        }
        return card.copy(dueEpochDay = today.toEpochDay() + nextInterval, intervalDays = nextInterval, ease = nextEase, repetitions = card.repetitions + 1)
    }
}

object IcsCodec {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun export(tasks: List<TaskModel>): String {
        val out = StringBuilder("BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nPRODID:-//Chronora//Offline Calendar//EN\\r\\n")
        tasks.forEach { task ->
            val start = LocalDateTime.of(task.date, java.time.LocalTime.of(task.startMinute / 60, task.startMinute % 60))
            val end = LocalDateTime.of(task.date, java.time.LocalTime.of(task.endMinute / 60, task.endMinute % 60))
            out.append("BEGIN:VEVENT\\r\\n")
            out.append("UID:chronora-").append(task.id).append("@local\\r\\n")
            out.append("DTSTAMP:").append(formatter.format(LocalDateTime.now())).append("\\r\\n")
            out.append("DTSTART:").append(formatter.format(start)).append("\\r\\n")
            out.append("DTEND:").append(formatter.format(end)).append("\\r\\n")
            out.append("SUMMARY:").append(escape(task.title)).append("\\r\\n")
            if (task.notes.isNotBlank()) out.append("DESCRIPTION:").append(escape(task.notes)).append("\\r\\n")
            out.append("END:VEVENT\\r\\n")
        }
        return out.append("END:VCALENDAR\\r\\n").toString()
    }

    fun importSummaries(ics: String): List<IcsEventSummary> {
        val lines = ics.replace("\\r", "").split("\\n")
        val result = mutableListOf<IcsEventSummary>()
        var summary: String? = null
        var start: String? = null
        var end: String? = null
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> { summary = null; start = null; end = null }
                line.startsWith("SUMMARY:") -> summary = unescape(line.substringAfter(':'))
                line.startsWith("DTSTART") -> start = line.substringAfter(':')
                line.startsWith("DTEND") -> end = line.substringAfter(':')
                line == "END:VEVENT" -> if (summary != null) result += IcsEventSummary(summary!!, start.orEmpty(), end.orEmpty())
            }
        }
        return result
    }

    private fun escape(value: String) = value.replace("\\\\", "\\\\\\\\").replace(";", "\\\\;").replace(",", "\\\\,").replace("\\n", "\\\\n")
    private fun unescape(value: String) = value.replace("\\\\n", "\\n").replace("\\\\,", ",").replace("\\\\;", ";").replace("\\\\\\\\", "\\\\")
}

data class IcsEventSummary(val title: String, val start: String, val end: String)

object DailyReviewEngine {
    fun weeklySummary(reviews: List<ReviewRecord>, today: LocalDate = LocalDate.now()): WeeklyReview {
        val start = today.with(DayOfWeek.MONDAY)
        val week = reviews.filter {
            runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> !d.isBefore(start) && !d.isAfter(today) } == true
        }
        return WeeklyReview(
            days = week.size,
            averageScore = if (week.isEmpty()) 0 else week.map { it.score }.average().toInt(),
            wins = week.count { it.wins.isNotBlank() },
            blockers = week.count { it.blockers.isNotBlank() },
            nextPriorities = week.map { it.nextPriority }.filter { it.isNotBlank() }.take(5)
        )
    }
}

data class WeeklyReview(
    val days: Int,
    val averageScore: Int,
    val wins: Int,
    val blockers: Int,
    val nextPriorities: List<String>
)
