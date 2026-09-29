package com.raunak.daytimeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.features.OfflineProductivityStore
import com.raunak.daytimeline.features.streak
import com.raunak.daytimeline.productivity.LocalProductivityAnalytics
import com.raunak.daytimeline.productivity.SmartPlanningEngine
import java.time.LocalDate
import java.time.LocalDateTime

private val HomeInk = Color(0xFF17221E)
private val HomeBg = Color(0xFFF4F1E9)
private val HomeCard = Color(0xFFFFFDF8)
private val HomeSage = Color(0xFF55786A)
private val HomeMuted = Color(0xFF74807A)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PowerHome(onOpenAlarms: () -> Unit = {}) {
    val context = LocalContext.current
    val app = remember(context) { AppContainer(context.applicationContext) }
    val vm: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory(app))
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val allTasks by vm.allTasks.collectAsStateWithLifecycle()
    val date by vm.currentDate.collectAsStateWithLifecycle()
    val pomo by vm.pomodoro.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val productivity = remember(context) { OfflineProductivityStore(context.applicationContext) }
    val habits by productivity.habits.collectAsStateWithLifecycle()
    val goals by productivity.goals.collectAsStateWithLifecycle()
    val routines by productivity.routines.collectAsStateWithLifecycle()
    val entries by productivity.timeEntries.collectAsStateWithLifecycle()
    val journal by productivity.journal.collectAsStateWithLifecycle()
    val notes by productivity.notes.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var addTask by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var editTask by remember { mutableStateOf<TaskModel?>(null) }
    var calendarOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = lightColorScheme(background = HomeBg, surface = HomeCard, primary = HomeSage, onSurface = HomeInk)) {
        Scaffold(
            containerColor = HomeBg,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Chronora", fontWeight = FontWeight.Bold)
                            Text((if (tab == 0) "Today" else if (tab == 1) "Focus" else "Productivity") + " · " + date, style = MaterialTheme.typography.labelSmall, color = HomeMuted)
                        }
                    },
                    actions = { IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, "Search") }; IconButton(onClick = { calendarOpen = true }) { Icon(Icons.Default.CalendarMonth, "Calendar") }; IconButton(onClick = onOpenAlarms) { Icon(Icons.Default.Alarm, "Alarms") } }
                )
            },
            bottomBar = {
                Column {
                    MadeByRaunak(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp))
                    NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.CalendarToday, null) }, label = { Text("Today") })
                    NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.Timer, null) }, label = { Text("Focus") })
                    NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.Insights, null) }, label = { Text("Productivity") })
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { if (tab == 2) dialog = "habit" else addTask = true }) { Icon(Icons.Default.Add, "Add") }
            }
        ) { padding ->
            when (tab) {
                0 -> TodayScreen(tasks, date, vm, settings.showCompleted) { editTask = it }
                1 -> FocusScreen(pomo, tasks, vm)
                2 -> ProductivityScreen(habits, goals, routines, entries, journal, notes, productivity) { dialog = it }
            }
        }
    }

    if (addTask) TaskEditorDialog(vm, null) { addTask = false }
    if (editTask != null) TaskEditorDialog(vm, editTask) { editTask = null }
    if (calendarOpen) CalendarDialog(date, vm) { calendarOpen = false }
    if (searchOpen) TaskSearchDialog(allTasks, vm) { searchOpen = false }
    if (dialog?.startsWith("editHabit:") == true) {
        val id = dialog!!.substringAfter(":").toLongOrNull()
        val habit = habits.firstOrNull { it.id == id }
        if (habit != null) HabitDialog({ name, target, time -> productivity.updateHabit(habit.id, name, target, time); dialog = null }, { dialog = null }, habit.name, habit.targetPerWeek, habit.preferredTime)
    }
    when (dialog) {
        "habit" -> HabitDialog({ name, target, time -> productivity.addHabit(name, target, time); dialog = null }, { dialog = null })
        "goal" -> GoalDialog({ title, target, deadline -> productivity.addGoal(title, target, deadline); dialog = null }, { dialog = null })
        "journal" -> JournalDialog({ mood, energy, wins, blockers, gratitude, note -> productivity.addJournal(LocalDate.now(), mood, energy, wins, blockers, gratitude, note); dialog = null }, { dialog = null })
        "tools" -> OfflinePowerTools(productivity) { dialog = null }
        "note" -> NoteDialog({ title, body, tags -> productivity.addNote(title, body, tags); dialog = null }, { dialog = null })
        "routine" -> RoutineDialog({ name, steps -> productivity.addRoutine(name, steps); dialog = null }, { dialog = null })
        "analytics" -> AnalyticsDialog(tasks, habits, entries, productivity) { dialog = null }
        "smartplan" -> SmartPlanDialog(tasks, vm) { dialog = null }
        "notes" -> NotesManagerDialog(notes, productivity) { dialog = null }
        "goals" -> GoalManagerDialog(goals, productivity) { dialog = null }
        "journalHistory" -> JournalHistoryDialog(journal) { dialog = null }
    }
}

@Composable
private fun TodayScreen(tasks: List<TaskModel>, date: LocalDate, vm: PlannerViewModel, showCompleted: Boolean, onEdit: (TaskModel) -> Unit) {
    val visible = if (showCompleted) tasks else tasks.filterNot { it.completed }
    val total = tasks.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    val done = tasks.filter { it.completed }.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayButton("‹", Modifier.weight(1f)) { vm.onPrevDay() }
                DayButton("Today", Modifier.weight(2f)) { vm.onToday() }
                DayButton("›", Modifier.weight(1f)) { vm.onNextDay() }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = HomeInk), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("${tasks.count { !it.completed }} remaining", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${done / 60}h ${done % 60}m done · ${total / 60}h ${total % 60}m planned", color = Color(0xFFD5E0DA))
                    LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth(), color = Color(0xFFA8C7B7), trackColor = Color.White.copy(alpha = .15f))
                }
            }
        }
        item { Text(date.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(visible.sortedBy { it.startMinute }, key = { it.id }) { task ->
            TaskRow(task, vm, { vm.toggleComplete(task, !task.completed) }, { vm.startPomodoro(task.id) }, { onEdit(task) })
        }
        if (visible.isEmpty()) item { EmptyCard("Nothing scheduled", "Use + to add a block. Your day stays local and offline.") }
    }
}

@Composable private fun DayButton(text: String, modifier: Modifier, onClick: () -> Unit) { OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) } }

@Composable
private fun TaskRow(task: TaskModel, vm: PlannerViewModel, onComplete: () -> Unit, onFocus: () -> Unit, onEdit: () -> Unit) {
    var showChecklist by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.completed, onCheckedChange = { onComplete() })
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(task.title, fontWeight = FontWeight.SemiBold)
                Text("${clock(task.startMinute)}–${clock(task.endMinute)} · ${task.endMinute - task.startMinute}m", color = HomeMuted, style = MaterialTheme.typography.bodySmall)
                if (task.notes.isNotBlank()) Text(task.notes, color = HomeMuted, maxLines = 2)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
            IconButton(onClick = { vm.deleteTask(task) }) { Icon(Icons.Default.Delete, "Delete") }
            IconButton(onClick = { showChecklist = true }) { Icon(Icons.Default.Checklist, "Checklist") }
            if (task.pomodoroEnabled) IconButton(onClick = onFocus) { Icon(Icons.Default.PlayArrow, "Focus") }
        }
    }
    if (showChecklist) ChecklistDialog(task, vm) { showChecklist = false }
}

@Composable private fun ChecklistDialog(task: TaskModel, vm: PlannerViewModel, close: () -> Unit) {
    val items by vm.checklist(task.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Checklist · ${task.title}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(item.checked, { vm.toggleChecklistItem(item) }); Text(item.text, modifier = Modifier.weight(1f)); IconButton(onClick = { vm.deleteChecklistItem(item.id) }) { Icon(Icons.Default.Delete, "Delete") } } }
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(text, { text = it }, label = { Text("Add item") }, modifier = Modifier.weight(1f)); IconButton(onClick = { if (text.isNotBlank()) { vm.addChecklistItem(task.id, text.trim()); text = "" } }) { Icon(Icons.Default.Add, "Add") } }
    } }, confirmButton = { TextButton(onClick = close) { Text("Done") } })
}

@Composable private fun FocusScreen(pomo: com.raunak.daytimeline.data.PomodoroStateEntity, tasks: List<TaskModel>, vm: PlannerViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(10.dp))
        Surface(shape = CircleShape, color = HomeInk, modifier = Modifier.size(250.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%02d:%02d".format(pomo.remainingSeconds / 60, pomo.remainingSeconds % 60), color = Color.White, style = MaterialTheme.typography.displayMedium)
                    Text(pomo.phase, color = Color(0xFFB9CCC2))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (pomo.running) vm.pausePomodoro() else vm.resumePomodoro() }) { Text(if (pomo.running) "Pause" else "Start") }
            OutlinedButton(onClick = vm::resetPomodoro) { Text("Reset") }
        }
        tasks.filter { it.pomodoroEnabled }.take(5).forEach { task ->
            ListItem(headlineContent = { Text(task.title) }, supportingContent = { Text("${clock(task.startMinute)} · ${task.endMinute - task.startMinute}m", color = HomeMuted) }, trailingContent = { IconButton(onClick = { vm.startPomodoro(task.id) }) { Icon(Icons.Default.PlayArrow, null) } })
        }
    }
}

@Composable
private fun ProductivityScreen(habits: List<com.raunak.daytimeline.features.OfflineHabit>, goals: List<com.raunak.daytimeline.features.OfflineGoal>, routines: List<com.raunak.daytimeline.features.OfflineRoutine>, entries: List<com.raunak.daytimeline.features.OfflineTimeEntry>, journal: List<com.raunak.daytimeline.features.OfflineJournalEntry>, notes: List<com.raunak.daytimeline.features.OfflineNote>, store: OfflineProductivityStore, openDialog: (String) -> Unit) {
    val today = LocalDate.now()
    val tracked = store.todayTrackedMinutes()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Habits", habits.count { it.completedDates.contains(today.toString()) }.toString(), Modifier.weight(1f)); StatCard("Tracked", "${tracked}m", Modifier.weight(1f)); StatCard("Journal", journal.size.toString(), Modifier.weight(1f)) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { SectionTitle("Habits"); TextButton(onClick = { openDialog("tools") }) { Text("Power tools") } } }
        items(habits, key = { it.id }) { h ->
            Card { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(h.name, fontWeight = FontWeight.SemiBold); Text("${h.streak()} day streak", color = HomeMuted) }; IconButton(onClick = { store.toggleHabit(h.id) }) { Icon(Icons.Default.CheckCircle, null) }; IconButton(onClick = { openDialog("editHabit:${h.id}") }) { Icon(Icons.Default.Edit, "Edit habit") }; IconButton(onClick = { store.deleteHabit(h.id) }) { Icon(Icons.Default.Delete, null) } } }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { SectionTitle("Goals"); Row { TextButton(onClick = { openDialog("goals") }) { Text("Manage") }; TextButton(onClick = { openDialog("goal") }) { Text("Add") } } } }
        items(goals, key = { it.id }) { g -> Card { Column(Modifier.padding(14.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(g.title, fontWeight = FontWeight.SemiBold); Text("${g.progress}/${g.target}") }; LinearProgressIndicator(progress = { g.progress.toFloat() / g.target }, modifier = Modifier.fillMaxWidth()); Row { TextButton(onClick = { store.setGoalProgress(g.id, g.progress + 1) }) { Text("+1") }; TextButton(onClick = { store.setGoalProgress(g.id, g.progress - 1) }) { Text("-1") } } } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { SectionTitle("Routines"); TextButton(onClick = { openDialog("routine") }) { Text("Create") } } }
        items(routines, key = { it.id }) { r -> Card { Column(Modifier.padding(14.dp)) { Text(r.name, fontWeight = FontWeight.SemiBold); Text("${r.steps.sumOf { it.minutes }} min · ${r.steps.size} steps", color = HomeMuted); Row { TextButton(onClick = { store.setRoutineCompleted(r.id) }) { Text(if (r.lastCompletedDate == today.toString()) "Completed today" else "Mark complete") }; TextButton(onClick = { store.deleteRoutine(r.id) }) { Text("Delete") } } } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { SectionTitle("Notes"); Row { TextButton(onClick = { openDialog("notes") }) { Text("Manage") }; TextButton(onClick = { openDialog("note") }) { Text("New") } } } }
        items(notes.sortedByDescending { it.updatedAt }.take(8), key = { it.id }) { n ->
            Card { Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(n.title, fontWeight = FontWeight.SemiBold); IconButton(onClick = { store.deleteNote(n.id) }) { Icon(Icons.Default.Delete, "Delete note") } }
                if (n.tags.isNotEmpty()) Text(n.tags.joinToString(" · "), color = HomeSage, style = MaterialTheme.typography.labelSmall)
                Text(n.body, maxLines = 5, color = HomeMuted)
            } }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { SectionTitle("Reflection"); Row { TextButton(onClick = { openDialog("journalHistory") }) { Text("History") }; TextButton(onClick = { openDialog("journal") }) { Text("Write") } } } }
        item { if (journal.isEmpty()) EmptyCard("No journal yet", "Capture mood, energy, wins, blockers and gratitude.") else Card { Column(Modifier.padding(14.dp)) { val j = journal.first(); Text(j.date, fontWeight = FontWeight.Bold); Text("Mood ${j.mood}/5 · Energy ${j.energy}/5", color = HomeSage); if (j.wins.isNotBlank()) Text("Wins: ${j.wins}"); if (j.blockers.isNotBlank()) Text("Blockers: ${j.blockers}") } } }
        item { SectionTitle("Tracked time"); Text("${entries.size} local time entries · ${tracked} minutes today", color = HomeMuted) }
    }
}

@Composable private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) { Card(modifier) { Column(Modifier.padding(12.dp)) { Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); Text(label, color = HomeMuted, style = MaterialTheme.typography.labelSmall) } } }
@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
@Composable private fun EmptyCard(title: String, body: String) { Card { Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, fontWeight = FontWeight.SemiBold); Text(body, color = HomeMuted) } } }

@Composable
private fun AddTaskDialog(vm: PlannerViewModel, close: () -> Unit) {
    var title by remember { mutableStateOf("") }; var start by remember { mutableStateOf("09:00") }; var end by remember { mutableStateOf("10:00") }; var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("New block") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(title, { title = it }, label = { Text("Task") }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(start, { start = it }, label = { Text("Start") }, modifier = Modifier.weight(1f)); OutlinedTextField(end, { end = it }, label = { Text("End") }, modifier = Modifier.weight(1f)) }; OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }) } }, confirmButton = { Button(onClick = { if (title.isNotBlank()) { val s = parseClock(start); vm.addOrUpdateTask(null, title, s, parseClock(end).coerceAtLeast(s + 5), true, notes, 1, "NONE", "NONE", 10); close() } }) { Text("Add") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable private fun HabitDialog(onSave: (String, Int, String) -> Unit, close: () -> Unit, initialName: String = "", initialTarget: Int = 7, initialTime: String = "") {
    var name by remember { mutableStateOf(initialName) }
    var target by remember { mutableStateOf(initialTarget.toString()) }
    var time by remember { mutableStateOf(initialTime) }
    AlertDialog(onDismissRequest = close, title = { Text(if (initialName.isBlank()) "New habit" else "Edit habit") }, text = {
        Column { OutlinedTextField(name, { name = it }, label = { Text("Habit") }); OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Days/week") }); OutlinedTextField(time, { time = it }, label = { Text("Preferred time HH:mm (optional)") }) }
    }, confirmButton = { Button(onClick = { onSave(name, target.toIntOrNull() ?: 7, time) }) { Text(if (initialName.isBlank()) "Create" else "Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
@Composable private fun GoalDialog(onSave: (String, Int, LocalDate?) -> Unit, close: () -> Unit) { var title by remember { mutableStateOf("") }; var target by remember { mutableStateOf("100") }; var deadline by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = close, title = { Text("New goal") }, text = { Column { OutlinedTextField(title, { title = it }, label = { Text("Goal") }); OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Target") }); OutlinedTextField(deadline, { deadline = it }, label = { Text("Deadline YYYY-MM-DD (optional)") }) } }, confirmButton = { Button(onClick = { onSave(title, target.toIntOrNull() ?: 100, runCatching { LocalDate.parse(deadline) }.getOrNull()) }) { Text("Create") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } }) }
@Composable private fun JournalDialog(onSave: (Int, Int, String, String, String, String) -> Unit, close: () -> Unit) { var mood by remember { mutableIntStateOf(3) }; var energy by remember { mutableIntStateOf(3) }; var wins by remember { mutableStateOf("") }; var blockers by remember { mutableStateOf("") }; var gratitude by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = close, title = { Text("Daily reflection") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Mood $mood/5"); Row { (1..5).forEach { TextButton(onClick = { mood = it }) { Text(it.toString()) } } }; Text("Energy $energy/5"); Row { (1..5).forEach { TextButton(onClick = { energy = it }) { Text(it.toString()) } } }; OutlinedTextField(wins, { wins = it }, label = { Text("Wins") }); OutlinedTextField(blockers, { blockers = it }, label = { Text("Blockers") }); OutlinedTextField(gratitude, { gratitude = it }, label = { Text("Gratitude") }); OutlinedTextField(note, { note = it }, label = { Text("Notes") }) } }, confirmButton = { Button(onClick = { onSave(mood, energy, wins, blockers, gratitude, note) }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } }) }

@Composable private fun NoteDialog(onSave: (String, String, Set<String>) -> Unit, close: () -> Unit) {
    var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }; var tags by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("New note") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text("Title") })
        OutlinedTextField(body, { body = it }, label = { Text("Note") }, minLines = 4)
        OutlinedTextField(tags, { tags = it }, label = { Text("Tags, comma separated") })
    } }, confirmButton = { Button(onClick = { onSave(title, body, tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()) }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable private fun RoutineDialog(onSave: (String, List<com.raunak.daytimeline.features.OfflineRoutineStep>) -> Unit, close: () -> Unit) {
    var name by remember { mutableStateOf("") }; var raw by remember { mutableStateOf("Hydrate|5\nPlan|10\nFocus|25") }
    AlertDialog(onDismissRequest = close, title = { Text("New routine") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Routine name") })
        OutlinedTextField(raw, { raw = it }, label = { Text("Steps: title|minutes") }, minLines = 4)
    } }, confirmButton = { Button(onClick = {
        val steps = raw.lines().mapNotNull { row -> val p = row.split("|", limit = 2); val m = p.getOrNull(1)?.trim()?.toIntOrNull(); if (p.firstOrNull()?.isNotBlank() == true && m != null && m > 0) com.raunak.daytimeline.features.OfflineRoutineStep(p[0].trim(), m) else null }
        if (steps.isNotEmpty()) onSave(name, steps)
    }) { Text("Create") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun AnalyticsDialog(tasks: List<TaskModel>, habits: List<com.raunak.daytimeline.features.OfflineHabit>, entries: List<com.raunak.daytimeline.features.OfflineTimeEntry>, store: OfflineProductivityStore, close: () -> Unit) {
    val today = LocalDate.now()
    val planned = tasks.size
    val completed = tasks.count { it.completed }
    val focus = entries.filter { it.endEpochMillis != null }.sumOf { e -> ((e.endEpochMillis!! - e.startEpochMillis).coerceAtLeast(0L) / 60000L) }
    val habitDates = habits.flatMap { it.completedDates }.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
    val streak = LocalProductivityAnalytics.currentStreak(habitDates, today)
    val score = LocalProductivityAnalytics.score(today, planned, completed, store.todayTrackedMinutes(), streak)
    AlertDialog(onDismissRequest = close, title = { Text("Productivity insights") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Today score: ${(score.completionRate * 100).toInt()}%")
        Text("Tasks: $completed/$planned completed")
        Text("Tracked today: ${store.todayTrackedMinutes()} min")
        Text("All recorded focus: ${focus} min")
        Text("Habit streak: $streak days")
        LinearProgressIndicator(progress = { score.completionRate.toFloat() }, modifier = Modifier.fillMaxWidth())
    } }, confirmButton = { TextButton(onClick = close) { Text("Close") } })
}

@Composable
private fun SmartPlanDialog(tasks: List<TaskModel>, vm: PlannerViewModel, close: () -> Unit) {
    val day = LocalDate.now()
    val blocks = tasks.map { t -> SmartPlanningEngine.Block(LocalDateTime.of(day, java.time.LocalTime.of(t.startMinute / 60, t.startMinute % 60)), LocalDateTime.of(day, java.time.LocalTime.of(t.endMinute / 60, t.endMinute % 60)), t.title) }
    val gaps = SmartPlanningEngine.freeGaps(blocks, day, java.time.LocalTime.of(6, 0), java.time.LocalTime.of(23, 0))
    val suggestion = SmartPlanningEngine.suggestPlacement(25, gaps)
    val health = SmartPlanningEngine.health(blocks, tasks.filter { it.pomodoroEnabled }.map { it.title }.toSet())
    AlertDialog(onDismissRequest = close, title = { Text("Smart offline planner") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Planned: ${health.plannedMinutes} min · Free: ${health.freeMinutes} min")
        Text(if (health.overlapMinutes > 0) "Schedule conflict: ${health.overlapMinutes} min overlap" else "No schedule overlap detected")
        if (suggestion != null) Text("Suggested 25m focus block: ${suggestion.start.toLocalTime()}–${suggestion.end.toLocalTime()}") else Text("No 25m free slot found")
        Text("Available gaps: ${gaps.size}")
        if (suggestion != null) Button(onClick = { vm.addOrUpdateTask(null, "Smart focus block", suggestion.start.toLocalTime().hour * 60 + suggestion.start.toLocalTime().minute, suggestion.end.toLocalTime().hour * 60 + suggestion.end.toLocalTime().minute, true, "Created by Smart Planner", 2, "NONE", "NONE", 0); close() }) { Text("Add suggested block") }
    } }, confirmButton = { TextButton(onClick = close) { Text("Close") } })
}


private fun clock(minutes: Int) = "%02d:%02d".format((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))
private fun parseClock(value: String): Int { val p = value.trim().split(":"); return ((p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)).coerceIn(0, 1439) }
