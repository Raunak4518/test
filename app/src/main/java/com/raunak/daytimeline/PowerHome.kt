package com.raunak.daytimeline

import com.raunak.daytimeline.ui.*

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.features.OfflineProductivityStore
import com.raunak.daytimeline.features.streak
import com.raunak.daytimeline.productivity.LocalProductivityAnalytics
import com.raunak.daytimeline.productivity.SmartPlanningEngine
import com.raunak.daytimeline.productivity.ChronoraPowerCenter
import java.time.LocalDate
import java.time.LocalDateTime

private val HomeInk: Color @Composable get() = Chronora.colors.hero
private val HomeBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val HomeSage: Color @Composable get() = MaterialTheme.colorScheme.primary
private val HomeMuted: Color @Composable get() = Chronora.muted

private data class NavTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val Tabs = listOf(
    NavTab("Home", Icons.Default.ViewTimeline), NavTab("Plan", Icons.Default.CalendarViewDay),
    NavTab("Focus", Icons.Default.Timer), NavTab("Campus", Icons.Default.School), NavTab("More", Icons.Default.GridView)
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PowerHome(onOpenAlarms: () -> Unit = {}, onOpenCommandCenter: () -> Unit = {}, openQuickAdd: Boolean = false, onQuickAddHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val app = remember(context) { AppContainer(context.applicationContext) }
    val vm: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory(app))
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val allTasks by vm.allTasks.collectAsStateWithLifecycle()
    val date by vm.currentDate.collectAsStateWithLifecycle()
    val pomo by vm.pomodoro.collectAsStateWithLifecycle()
    val productivity = remember(context) { OfflineProductivityStore(context.applicationContext) }
    val habits by productivity.habits.collectAsStateWithLifecycle()
    val goals by productivity.goals.collectAsStateWithLifecycle()
    val routines by productivity.routines.collectAsStateWithLifecycle()
    val entries by productivity.timeEntries.collectAsStateWithLifecycle()
    val journal by productivity.journal.collectAsStateWithLifecycle()
    val notes by productivity.notes.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addAt by remember { mutableStateOf<Pair<LocalDate, Int?>?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<String?>(null) }
    var editTask by remember { mutableStateOf<TaskModel?>(null) }
    val updateRequested by com.raunak.daytimeline.update.UpdateNav.requested.collectAsStateWithLifecycle()
    LaunchedEffect(openQuickAdd) { if (openQuickAdd) { page = "tool:QUICK_ADD"; onQuickAddHandled() } }
    val open: (String) -> Unit = { route ->
        when (route) {
            "alarms" -> onOpenAlarms()
            "command" -> onOpenCommandCenter()
            "update" -> com.raunak.daytimeline.update.UpdateNav.requested.value = true
            "settings" -> dialog = "tools"
            else -> page = route
        }
    }

    Scaffold(
        containerColor = HomeBg,
        topBar = {
            ChronoraTopBar(if (tab == 0) "Chronora" else Tabs[tab].label, null) {
                IconButton(onClick = { page = "tool:SEARCH" }) { Icon(Icons.Default.Search, "Search") }
                IconButton(onClick = onOpenAlarms) { Icon(Icons.Default.Alarm, "Alarms") }
            }
        },
        bottomBar = {
            NavigationBar(Modifier.testTag("nav"), tonalElevation = 0.dp) {
                Tabs.forEachIndexed { i, t -> NavigationBarItem(tab == i, { tab = i }, icon = { Icon(t.icon, null) }, label = { Text(t.label) }) }
            }
        },
        floatingActionButton = {
            if (tab <= 1) FloatingActionButton(onClick = { addAt = date to null }, shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Add, "Add task") }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.animation.Crossfade(tab, label = "tab") { t ->
                when (t) {
                    0 -> com.raunak.daytimeline.home.HomeScreen(vm, { editTask = it }, { d, m -> addAt = d to m }) { tab = 2 }
                    1 -> com.raunak.daytimeline.home.PlanScreen(vm, productivity, { editTask = it }, { d, m -> addAt = d to m }) { page = "calendar" }
                    2 -> com.raunak.daytimeline.productivity.FocusPanel(pomo, tasks, allTasks, vm)
                    3 -> com.raunak.daytimeline.campus.CampusScreen(Modifier)
                    else -> com.raunak.daytimeline.home.MoreScreen(open)
                }
            }
        }
    }

    addAt?.let { (d, m) -> com.raunak.daytimeline.productivity.TaskEditor(vm, null, d, m) { addAt = null } }
    editTask?.let { t -> com.raunak.daytimeline.productivity.TaskEditor(vm, t, t.date) { editTask = null } }
    when (val p = page) {
        null -> Unit
        "calendar" -> com.raunak.daytimeline.productivity.CalendarPage(vm, date, { editTask = it }) { page = null }
        "power" -> ChronoraPowerCenter(allTasks, productivity, { page = null }) { vm.selectDate(it); page = null }
        else -> when {
            p.startsWith("wellbeing:") -> com.raunak.daytimeline.wellbeing.WellbeingHub(p.substringAfter(":").toIntOrNull() ?: 0) { page = null }
            p.startsWith("prod:") -> FullScreenPage(ProductivityLabels[p.substringAfter(":").toIntOrNull() ?: 0], { page = null }) {
                ProductivityScreen(habits, goals, routines, entries, journal, notes, productivity, p.substringAfter(":").toIntOrNull() ?: 0) { dialog = it }
            }
            p.startsWith("tool:") -> runCatching { com.raunak.daytimeline.pro.ProTool.valueOf(p.substringAfter(":")) }.getOrNull()
                ?.let { com.raunak.daytimeline.pro.ProToolPage(vm, it) { page = null } }
        }
    }
    if (updateRequested) com.raunak.daytimeline.update.UpdateDialog { com.raunak.daytimeline.update.UpdateNav.requested.value = false }
    if (dialog?.startsWith("editHabit:") == true) {
        val id = dialog!!.substringAfter(":").toLongOrNull()
        val habit = habits.firstOrNull { it.id == id }
        if (habit != null) com.raunak.daytimeline.productivity.HabitEditorDialog(habit, { productivity.saveHabit(it); dialog = null }) { dialog = null }
    }
    when (dialog) {
        "habit" -> com.raunak.daytimeline.productivity.HabitEditorDialog(null, { productivity.saveHabit(it); dialog = null }) { dialog = null }
        "goal" -> GoalDialog({ title, target, deadline -> productivity.addGoal(title, target, deadline); dialog = null }, { dialog = null })
        "journal" -> JournalDialog({ mood, energy, wins, blockers, gratitude, note -> productivity.addJournal(LocalDate.now(), mood, energy, wins, blockers, gratitude, note); dialog = null }, { dialog = null })
        "tools" -> OfflinePowerTools(productivity) { dialog = null }
        "note" -> NoteDialog({ title, body, tags, folder -> productivity.addNote(title, body, tags, folder); dialog = null }, { dialog = null })
        "routine" -> RoutineDialog({ name, steps -> productivity.addRoutine(name, steps); dialog = null }, { dialog = null })
        "analytics" -> AnalyticsDialog(tasks, habits, entries, productivity) { dialog = null }
        "smartplan" -> SmartPlanDialog(tasks, vm) { dialog = null }
    }
}

@Composable private fun DayButton(text: String, modifier: Modifier, onClick: () -> Unit) { OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) } }

@Composable private fun ChecklistDialog(task: TaskModel, vm: PlannerViewModel, close: () -> Unit) {
    val items by vm.checklist(task.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Checklist · ${task.title}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(item.checked, { vm.toggleChecklistItem(item) }); Text(item.text, modifier = Modifier.weight(1f)); IconButton(onClick = { vm.deleteChecklistItem(item.id) }) { Icon(Icons.Default.Delete, "Delete") } } }
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(text, { text = it }, label = { Text("Add item") }, modifier = Modifier.weight(1f)); IconButton(onClick = { if (text.isNotBlank()) { vm.addChecklistItem(task.id, text.trim()); text = "" } }) { Icon(Icons.Default.Add, "Add") } }
    } }, confirmButton = { TextButton(onClick = close) { Text("Done") } })
}

private val ProductivityLabels = listOf("Habits", "Goals", "Routines", "Notes", "Journal", "Time log")

@Composable
private fun ProductivityScreen(habits: List<com.raunak.daytimeline.features.OfflineHabit>, goals: List<com.raunak.daytimeline.features.OfflineGoal>, routines: List<com.raunak.daytimeline.features.OfflineRoutine>, entries: List<com.raunak.daytimeline.features.OfflineTimeEntry>, journal: List<com.raunak.daytimeline.features.OfflineJournalEntry>, notes: List<com.raunak.daytimeline.features.OfflineNote>, store: OfflineProductivityStore, initialSection: Int = 0, openDialog: (String) -> Unit) {
    val today = LocalDate.now()
    val prefs by store.settings.collectAsStateWithLifecycle()
    val projects by store.projects.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableIntStateOf(initialSection) }
    val labels = ProductivityLabels
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = section, edgePadding = 12.dp, containerColor = Color.Transparent) {
            labels.forEachIndexed { i, l -> Tab(section == i, { section = i }, text = { Text(l) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (section) {
                0 -> {
                    item { com.raunak.daytimeline.productivity.HabitTodayHero(habits, today) }
                    item { SectionHeader("Habits") { Row { TextButton(onClick = { openDialog("habit") }) { Text("Add") }; TextButton(onClick = { openDialog("tools") }) { Text("Power tools") } } } }
                    item { com.raunak.daytimeline.productivity.HabitList(habits, store, { openDialog("editHabit:${it.id}") }, today) }
                }
                1 -> item { com.raunak.daytimeline.productivity.GoalsSection(goals, store) }
                2 -> item { com.raunak.daytimeline.productivity.RoutinesSection(routines, store) }
                3 -> item { com.raunak.daytimeline.productivity.NotesSection(notes, store) }
                4 -> item { com.raunak.daytimeline.productivity.JournalSection(journal, prefs, store) }
                else -> item { com.raunak.daytimeline.productivity.TimeSection(entries, projects, store) }
            }
        }
    }
}

@Composable private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) = StatTile(label, value, modifier)
@Composable private fun SectionTitle(text: String) = SectionHeader(text)
@Composable private fun EmptyCard(title: String, body: String) = EmptyState(title, body)

@Composable
private fun AddTaskDialog(vm: PlannerViewModel, close: () -> Unit) {
    var title by remember { mutableStateOf("") }; var start by remember { mutableStateOf("09:00") }; var end by remember { mutableStateOf("10:00") }; var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("New block") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(title, { title = it }, label = { Text("Task") }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(start, { start = it }, label = { Text("Start") }, modifier = Modifier.weight(1f)); OutlinedTextField(end, { end = it }, label = { Text("End") }, modifier = Modifier.weight(1f)) }; OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }) } }, confirmButton = { Button(onClick = { if (title.isNotBlank()) { val s = parseClock(start); vm.addOrUpdateTask(null, title, s, parseClock(end).coerceAtLeast(s + 5), true, notes, 1, "NONE", "NONE", 10); close() } }) { Text("Add") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable private fun GoalDialog(onSave: (String, Int, LocalDate?) -> Unit, close: () -> Unit) { var title by remember { mutableStateOf("") }; var target by remember { mutableStateOf("100") }; var deadline by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = close, title = { Text("New goal") }, text = { Column { OutlinedTextField(title, { title = it }, label = { Text("Goal") }); OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Target") }); OutlinedTextField(deadline, { deadline = it }, label = { Text("Deadline YYYY-MM-DD (optional)") }) } }, confirmButton = { Button(onClick = { onSave(title, target.toIntOrNull() ?: 100, runCatching { LocalDate.parse(deadline) }.getOrNull()) }) { Text("Create") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } }) }
@Composable private fun JournalDialog(onSave: (Int, Int, String, String, String, String) -> Unit, close: () -> Unit) { var mood by remember { mutableIntStateOf(3) }; var energy by remember { mutableIntStateOf(3) }; var wins by remember { mutableStateOf("") }; var blockers by remember { mutableStateOf("") }; var gratitude by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = close, title = { Text("Daily reflection") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Mood $mood/5"); Row { (1..5).forEach { TextButton(onClick = { mood = it }) { Text(it.toString()) } } }; Text("Energy $energy/5"); Row { (1..5).forEach { TextButton(onClick = { energy = it }) { Text(it.toString()) } } }; OutlinedTextField(wins, { wins = it }, label = { Text("Wins") }); OutlinedTextField(blockers, { blockers = it }, label = { Text("Blockers") }); OutlinedTextField(gratitude, { gratitude = it }, label = { Text("Gratitude") }); OutlinedTextField(note, { note = it }, label = { Text("Notes") }) } }, confirmButton = { Button(onClick = { onSave(mood, energy, wins, blockers, gratitude, note) }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } }) }

@Composable private fun NoteDialog(onSave: (String, String, Set<String>, String) -> Unit, close: () -> Unit) {
    var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }; var tags by remember { mutableStateOf("") }; var folder by remember { mutableStateOf("General") }
    AlertDialog(onDismissRequest = close, title = { Text("New note") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text("Title") })
        OutlinedTextField(body, { body = it }, label = { Text("Note") }, minLines = 4)
        OutlinedTextField(tags, { tags = it }, label = { Text("Tags, comma separated") })
        OutlinedTextField(folder, { folder = it }, label = { Text("Folder") })
    } }, confirmButton = { Button(onClick = { onSave(title, body, tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet(), folder) }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun NoteBacklinksDialog(note: com.raunak.daytimeline.features.OfflineNote, store: OfflineProductivityStore, close: () -> Unit) {
    val backlinks = store.noteBacklinks(note)
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Backlinks · " + note.title) },
        text = {
            if (backlinks.isEmpty()) Text("No notes currently reference this title.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(backlinks, key = { it.id }) {
                    Card { Column(Modifier.padding(10.dp)) {
                        Text(it.title, fontWeight = FontWeight.SemiBold)
                        Text(it.body.take(180), color = HomeMuted)
                        Text("Folder: " + it.folder, style = MaterialTheme.typography.labelSmall)
                    } }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } }
    )
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
