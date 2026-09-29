package com.raunak.daytimeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.raunak.daytimeline.data.ChecklistItemEntity
import com.raunak.daytimeline.data.PomodoroStateEntity
import com.raunak.daytimeline.data.TaskEntity
import com.raunak.daytimeline.data.TaskRepository
import com.raunak.daytimeline.domain.PomodoroEngine
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.settings.PlannerSettings
import com.raunak.daytimeline.settings.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class PlannerViewModel(
    private val repository: TaskRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val now = MutableStateFlow(System.currentTimeMillis())

    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlannerSettings())

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
                if (ticked != pomodoro.value) repository.savePomodoro(ticked)
                delay(1000)
            }
        }
    }

    fun onPrevDay() { selectedDate.value = selectedDate.value.minusDays(1) }
    fun onNextDay() { selectedDate.value = selectedDate.value.plusDays(1) }
    fun onToday() { selectedDate.value = LocalDate.now() }

    fun addOrUpdateTask(id: Long?, title: String, start: Int, end: Int, pomodoroEnabled: Boolean, notes: String, priority: Int, recurrenceType: String, reminderMode: String, reminderOffsetMinutes: Int) {
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
                reminderMode = reminderMode,
                reminderOffsetMinutes = reminderOffsetMinutes
            )
            if (id == null) repository.addTask(candidate) else repository.updateTask(candidate)
        }
    }

    fun quickAdd(input: String) = viewModelScope.launch { repository.quickAdd(input, selectedDate.value) }
    fun toggleComplete(task: TaskModel, complete: Boolean) = viewModelScope.launch { repository.markComplete(task.id, complete) }
    fun deleteTask(task: TaskModel) = viewModelScope.launch { repository.deleteTask(task.id) }
    fun duplicateTask(task: TaskModel) = viewModelScope.launch { repository.duplicateTask(task.id) }

    fun checklist(taskId: Long) = repository.checklist(taskId)

    fun addChecklistItem(taskId: Long, text: String) = viewModelScope.launch {
        repository.upsertChecklistItem(ChecklistItemEntity(taskId = taskId, text = text, position = 0))
    }

    fun toggleChecklistItem(item: ChecklistItemEntity) = viewModelScope.launch { repository.toggleChecklistItem(item) }
    fun deleteChecklistItem(itemId: Long) = viewModelScope.launch { repository.deleteChecklistItem(itemId) }

    fun updateSettings(update: PlannerSettings.() -> PlannerSettings) = viewModelScope.launch {
        settingsStore.update(update)
    }

    fun startPomodoro(taskId: Long?) = viewModelScope.launch { repository.savePomodoro(PomodoroEngine.start(taskId, pomodoro.value)) }
    fun pausePomodoro() = viewModelScope.launch { repository.savePomodoro(PomodoroEngine.pause(pomodoro.value)) }
    fun resumePomodoro() = viewModelScope.launch { repository.savePomodoro(PomodoroEngine.resume(pomodoro.value)) }
    fun resetPomodoro() = viewModelScope.launch { repository.savePomodoro(PomodoroEngine.reset(pomodoro.value)) }

    fun exportJson(onComplete: (String) -> Unit) = viewModelScope.launch { onComplete(repository.exportJson()) }
    fun importJson(json: String, onComplete: (Boolean) -> Unit) = viewModelScope.launch { onComplete(repository.importJson(json).isSuccess) }

    class Factory(private val app: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PlannerViewModel(app.repository, app.settingsStore) as T
        }
    }
}
