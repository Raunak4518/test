package com.raunak.daytimeline

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.raunak.daytimeline.data.ChecklistItemEntity
import com.raunak.daytimeline.data.PomodoroStateEntity
import com.raunak.daytimeline.data.TaskEntity
import com.raunak.daytimeline.data.TaskRepository
import com.raunak.daytimeline.domain.PomodoroEngine
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.pro.FocusSessionService
import com.raunak.daytimeline.pro.GardenStore
import com.raunak.daytimeline.settings.PlannerSettings
import com.raunak.daytimeline.settings.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class PlannerViewModel(
    private val repository: TaskRepository,
    private val settingsStore: SettingsStore,
    private val appContext: Context? = null
) : ViewModel() {
    private val garden = appContext?.let { GardenStore(it) }
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val now = MutableStateFlow(System.currentTimeMillis())

    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlannerSettings())

    val allTasks: StateFlow<List<TaskModel>> = repository.observeAllTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskModel>> = selectedDate
        .flatMapLatest { repository.observeTasks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pomodoro = repository.observePomodoro().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PomodoroStateEntity())
    val currentDate = selectedDate.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())
    val nowMillis = now.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    init {
        viewModelScope.launch {
            while (true) {
                now.value = System.currentTimeMillis()
                val ticked = PomodoroEngine.tick(pomodoro.value, now.value)
                if (ticked != pomodoro.value) {
                    garden?.onTransition(pomodoro.value, ticked)
                    repository.savePomodoro(ticked)
                }
                delay(1000)
            }
        }
    }

    fun onPrevDay() { selectedDate.value = selectedDate.value.minusDays(1) }
    fun onNextDay() { selectedDate.value = selectedDate.value.plusDays(1) }
    fun onToday() { selectedDate.value = LocalDate.now() }
    fun selectDate(date: LocalDate) { selectedDate.value = date }

    fun addOrUpdateTask(id: Long?, title: String, start: Int, end: Int, pomodoroEnabled: Boolean, notes: String, priority: Int, recurrenceType: String, reminderMode: String, reminderOffsetMinutes: Int, recurrenceDays: String = "", tags: String = "") {
        viewModelScope.launch {
            val candidate = TaskEntity(
                id = id ?: 0,
                title = title,
                dateEpochDay = selectedDate.value.toEpochDay(),
                startMinute = start,
                endMinute = end,
                pomodoroEnabled = pomodoroEnabled,
                notes = notes,
                priority = priority,
                recurrenceType = recurrenceType,
                recurrenceDays = recurrenceDays,
                tags = tags,
                reminderMode = reminderMode,
                reminderOffsetMinutes = reminderOffsetMinutes
            )
            if (id == null) repository.addTask(candidate) else repository.updateTask(candidate)
        }
    }

    fun quickAdd(input: String) = viewModelScope.launch { repository.quickAdd(input, selectedDate.value) }
    /** Quick add where relative dates ("tomorrow", "next monday") are relative to the real today. */
    fun quickAddExact(input: String) = viewModelScope.launch { repository.quickAdd(input, LocalDate.now()) }
    fun toggleComplete(task: TaskModel, complete: Boolean) = viewModelScope.launch { repository.markComplete(task.id, complete) }
    fun deleteTask(task: TaskModel) = viewModelScope.launch { repository.deleteTask(task.id) }
    fun duplicateTask(task: TaskModel) = viewModelScope.launch { repository.duplicateTask(task.id) }

    fun moveTask(task: TaskModel, start: Int, end: Int) = viewModelScope.launch {
        val safeStart = start.coerceIn(0, 23 * 60 + 59)
        val duration = (end - task.startMinute).coerceAtLeast(5)
        repository.updateTask(TaskEntity(id = task.id, title = task.title, dateEpochDay = task.date.toEpochDay(), startMinute = safeStart, endMinute = (safeStart + duration).coerceAtMost(24 * 60), category = task.category, colorHex = task.colorHex, priority = task.priority, notes = task.notes, pomodoroEnabled = task.pomodoroEnabled, tags = task.tags, reminderMode = task.reminderMode, reminderOffsetMinutes = task.reminderOffsetMinutes, completed = task.completed, recurrenceType = task.recurrenceType, recurrenceDays = task.recurrenceDays))
    }

    fun autoSchedule(dayStartMinute: Int, dayEndMinute: Int) = viewModelScope.launch {
        repository.autoSchedule(selectedDate.value, dayStartMinute, dayEndMinute)
    }

    fun resizeTask(task: TaskModel, newEnd: Int) = viewModelScope.launch {
        val end = newEnd.coerceIn(task.startMinute + 5, 24 * 60)
        repository.updateTask(TaskEntity(id = task.id, title = task.title, dateEpochDay = task.date.toEpochDay(), startMinute = task.startMinute, endMinute = end, category = task.category, colorHex = task.colorHex, priority = task.priority, notes = task.notes, pomodoroEnabled = task.pomodoroEnabled, tags = task.tags, reminderMode = task.reminderMode, reminderOffsetMinutes = task.reminderOffsetMinutes, completed = task.completed, recurrenceType = task.recurrenceType, recurrenceDays = task.recurrenceDays))
    }

    fun checklist(taskId: Long) = repository.checklist(taskId)

    fun addChecklistItem(taskId: Long, text: String) = viewModelScope.launch {
        repository.upsertChecklistItem(ChecklistItemEntity(taskId = taskId, text = text, position = 0))
    }

    fun toggleChecklistItem(item: ChecklistItemEntity) = viewModelScope.launch { repository.toggleChecklistItem(item) }
    fun deleteChecklistItem(itemId: Long) = viewModelScope.launch { repository.deleteChecklistItem(itemId) }

    fun updateSettings(update: PlannerSettings.() -> PlannerSettings) = viewModelScope.launch {
        settingsStore.update(update)
    }

    fun startPomodoro(taskId: Long?) = viewModelScope.launch { repository.savePomodoro(PomodoroEngine.start(taskId, pomodoro.value)); focusService() }
    fun pausePomodoro() = viewModelScope.launch { repository.savePomodoro(PomodoroEngine.pause(pomodoro.value)); focusService() }
    fun resumePomodoro() = viewModelScope.launch { repository.savePomodoro(PomodoroEngine.resume(pomodoro.value)); focusService() }
    fun resetPomodoro() = viewModelScope.launch { garden?.onAbandon(pomodoro.value); repository.savePomodoro(PomodoroEngine.reset(pomodoro.value)); focusService(FocusSessionService.ACTION_STOP) }
    fun skipPomodoro() = viewModelScope.launch { repository.savePomodoro(PomodoroEngine.skip(pomodoro.value)); focusService() }
    fun extendPomodoro(minutes: Int = 5) = viewModelScope.launch { repository.savePomodoro(PomodoroEngine.extend(pomodoro.value, minutes)); focusService() }

    /** Keeps the timer, lock-screen countdown and focus sounds alive outside the app. */
    private fun focusService(action: String = FocusSessionService.ACTION_START) {
        val context = appContext ?: return
        runCatching { FocusSessionService.send(context, action) }
    }

    fun exportJson(onComplete: (String) -> Unit) = viewModelScope.launch { onComplete(repository.exportJson()) }
    fun importJson(json: String, onComplete: (Boolean) -> Unit) = viewModelScope.launch { onComplete(repository.importJson(json).isSuccess) }

    class Factory(private val app: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PlannerViewModel(app.repository, app.settingsStore, app.context) as T
        }
    }
}
