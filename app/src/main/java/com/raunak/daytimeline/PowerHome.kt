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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PowerHome(onOpenAlarms: () -> Unit = {}, onOpenCommandCenter: () -> Unit = {}, openQuickAdd: Boolean = false, onQuickAddHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val app = remember(context) { AppContainer(context.applicationContext) }
    val vm: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory(app))
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val allTasks by vm.allTasks.collectAsStateWithLifecycle()
    val agenda by vm.agenda.collectAsStateWithLifecycle()
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
    var tab by remember { mutableIntStateOf(3) }
    var addTask by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var editTask by remember { mutableStateOf<TaskModel?>(null) }
    var calendarOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var powerCenterOpen by remember { mutableStateOf(false) }
    var proSuiteOpen by remember { mutableStateOf(false) }
    val updateRequested by com.raunak.daytimeline.update.UpdateNav.requested.collectAsStateWithLifecycle()
    LaunchedEffect(openQuickAdd) { if (openQuickAdd) { proSuiteOpen = true; onQuickAddHandled() } }

    run {
        Scaffold(
            containerColor = HomeBg,
            topBar = {
                var menu by remember { mutableStateOf(false) }
                ChronoraTopBar("Chronora", null, subtitle = (when (tab) { 0 -> "Today"; 1 -> "Focus"; 3 -> "Campus"; else -> "Productivity" }) + " · " + date) {
                    IconButton(onClick = { proSuiteOpen = true }) { Icon(Icons.Default.AutoAwesome, "Free Pro Suite") }
                    IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = onOpenAlarms) { Icon(Icons.Default.Alarm, "Alarms") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Calendar") }, leadingIcon = { Icon(Icons.Default.CalendarMonth, null) }, onClick = { menu = false; calendarOpen = true })
                            DropdownMenuItem(text = { Text("Power Center") }, leadingIcon = { Icon(Icons.Default.Dashboard, null) }, onClick = { menu = false; powerCenterOpen = true })
                            DropdownMenuItem(text = { Text("Command center") }, leadingIcon = { Icon(Icons.Default.Tune, null) }, onClick = { menu = false; onOpenCommandCenter() })
                            DropdownMenuItem(text = { Text("Update app") }, leadingIcon = { Icon(Icons.Default.SystemUpdate, null) }, onClick = { menu = false; com.raunak.daytimeline.update.UpdateNav.requested.value = true })
                            val prefs by productivity.settings.collectAsStateWithLifecycle()
                            DropdownMenuItem(text = { Text(if (prefs.pinnedQuickAdd) "Unpin quick-add notification" else "Pin quick-add notification") }, leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = {
                                menu = false
                                val on = !prefs.pinnedQuickAdd
                                productivity.updateSettings { it.copy(pinnedQuickAdd = on) }
                                if (on) com.raunak.daytimeline.productivity.QuickAddNotification.show(context.applicationContext) else com.raunak.daytimeline.productivity.QuickAddNotification.hide(context.applicationContext)
                            })
                        }
                    }
                }
            },
            bottomBar = {
                Column {
                    MadeByRaunak(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp))
                    NavigationBar {
                    NavigationBarItem(tab == 3, { tab = 3 }, icon = { Icon(Icons.Default.School, null) }, label = { Text("Campus") })
                    NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.CalendarToday, null) }, label = { Text("Today") })
                    NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.Timer, null) }, label = { Text("Focus") })
                    NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.Insights, null) }, label = { Text("Productivity") })
                    }
                }
            },
            floatingActionButton = {
                if (tab == 0) FloatingActionButton(onClick = { addTask = true }) { Icon(Icons.Default.Add, "Add task") }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> TodayScreen(tasks, agenda, date, vm, settings.showCompleted, productivity) { editTask = it }
                    1 -> com.raunak.daytimeline.productivity.FocusPanel(pomo, tasks, allTasks, vm)
                    2 -> ProductivityScreen(habits, goals, routines, entries, journal, notes, productivity) { dialog = it }
                    3 -> com.raunak.daytimeline.campus.CampusScreen(Modifier)
                }
            }
        }
    }

    if (addTask) com.raunak.daytimeline.productivity.TaskEditor(vm, null, date) { addTask = false }
    editTask?.let { t -> com.raunak.daytimeline.productivity.TaskEditor(vm, t, t.date) { editTask = null } }
    if (calendarOpen) com.raunak.daytimeline.productivity.CalendarPage(vm, date, { editTask = it }) { calendarOpen = false }
    if (searchOpen) TaskSearchDialog(allTasks, vm) { searchOpen = false }
    if (proSuiteOpen) com.raunak.daytimeline.pro.FreeProSuite(vm) { proSuiteOpen = false }
    if (updateRequested) com.raunak.daytimeline.update.UpdateDialog { com.raunak.daytimeline.update.UpdateNav.requested.value = false }
    if (powerCenterOpen) ChronoraPowerCenter(allTasks, productivity, { powerCenterOpen = false }) { vm.selectDate(it); powerCenterOpen = false }
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
        "notes" -> NotesManagerDialog(notes, productivity) { dialog = null }
        "goals" -> GoalManagerDialog(goals, productivity) { dialog = null }
        "journalHistory" -> JournalHistoryDialog(journal) { dialog = null }
    }
}

/** Classes from the Campus timetable for [date], so the whole day is in one place. */
@Composable
private fun DayClasses(date: LocalDate) {
    val context = LocalContext.current
    val store = remember { com.raunak.daytimeline.campus.CampusStore.get(context) }
    val data by store.data.collectAsStateWithLifecycle()
    val classes = com.raunak.daytimeline.campus.AttendanceEngine.occurrences(data, date)
    val meals = com.raunak.daytimeline.campus.Mess.meals(data, date)
    if (classes.isEmpty() && meals.isEmpty()) return
    val now = LocalDateTime.now()
    val minute = now.hour * 60 + now.minute
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (meals.isNotEmpty()) Text("Mess: " + meals.joinToString(" · ") { "${it.name} ${com.raunak.daytimeline.campus.clock(it.start)}–${com.raunak.daytimeline.campus.clock(it.end)}" },
                style = MaterialTheme.typography.bodySmall, color = HomeMuted)
            if (classes.isNotEmpty()) Text("Classes · ${classes.size}", fontWeight = FontWeight.Bold)
            classes.forEach { o ->
                val s = data.subjects.firstOrNull { it.id == o.subjectId } ?: return@forEach
                val past = date.isBefore(now.toLocalDate()) || (date == now.toLocalDate() && o.end <= minute)
                com.raunak.daytimeline.campus.ClassRow(o, s, data.marks[o.key], past) { m -> store.mark(o.key, m) }
            }
        }
    }
}

@Composable
private fun TodayScreen(tasks: List<TaskModel>, agenda: List<TaskModel>, date: LocalDate, vm: PlannerViewModel, showCompleted: Boolean, store: OfflineProductivityStore, onEdit: (TaskModel) -> Unit) {
    val today = LocalDate.now()
    val visible = if (showCompleted) tasks else tasks.filterNot { it.completed }
    val total = tasks.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    val done = tasks.filter { it.completed }.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    val prefs by store.settings.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableIntStateOf(0) }
    var checklistFor by remember { mutableStateOf<TaskModel?>(null) }
    val overdue = agenda.filter { !it.completed && it.recurrenceType == "NONE" && it.date.isBefore(today) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Day", "Upcoming", "Matrix").forEachIndexed { i, l ->
                    SegmentedButton(selected = view == i, onClick = { view = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                }
            }
        }
        item { com.raunak.daytimeline.productivity.QuickAddBar(vm, if (view == 0) date else today) }
        when (view) {
            2 -> item { com.raunak.daytimeline.productivity.EisenhowerMatrix(agenda, vm, prefs, store::updateSettings, onEdit) }
            1 -> {
                item { com.raunak.daytimeline.productivity.OverdueCard(overdue, vm, onEdit, today) }
                with(com.raunak.daytimeline.productivity.TaskViewsScope) { upcoming(agenda, prefs.upcomingDays.takeIf { it > 0 } ?: 7, vm, onEdit, { checklistFor = it }, today) }
                item {
                    val d = prefs.upcomingDays.takeIf { it > 0 } ?: 7
                    Stepper("Days shown", "$d", { store.updateSettings { it.copy(upcomingDays = (d - 1).coerceAtLeast(1)) } }, { store.updateSettings { it.copy(upcomingDays = (d + 1).coerceAtMost(60)) } })
                }
            }
            else -> {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayButton("‹", Modifier.weight(1f)) { vm.onPrevDay() }
                        DayButton(com.raunak.daytimeline.productivity.relativeDay(date, today), Modifier.weight(2f)) { vm.onToday() }
                        DayButton("›", Modifier.weight(1f)) { vm.onNextDay() }
                    }
                }
                item {
                    HeroCard {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("${tasks.count { !it.completed }} remaining", color = Chronora.colors.onHero, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("${done / 60}h ${done % 60}m done · ${total / 60}h ${total % 60}m planned", color = Chronora.colors.heroMuted)
                            LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth(), color = Chronora.colors.heroAccent, trackColor = Color.White.copy(alpha = .15f))
                        }
                    }
                }
                if (date == today) item { com.raunak.daytimeline.productivity.OverdueCard(overdue, vm, onEdit, today) }
                item { DayClasses(date) }
                items(visible.sortedWith(compareBy<TaskModel> { it.completed }.thenBy { it.startMinute }), key = { it.id }) { task ->
                    com.raunak.daytimeline.productivity.TaskCard(task, vm, { onEdit(task) }, { checklistFor = task }, today = today)
                }
                if (visible.isEmpty()) item { EmptyCard("Nothing planned", "Type above to add a task — dates, times, #tags, !priority and repeats are understood.") }
            }
        }
    }
    checklistFor?.let { ChecklistDialog(it, vm) { checklistFor = null } }
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

@Composable
private fun ProductivityScreen(habits: List<com.raunak.daytimeline.features.OfflineHabit>, goals: List<com.raunak.daytimeline.features.OfflineGoal>, routines: List<com.raunak.daytimeline.features.OfflineRoutine>, entries: List<com.raunak.daytimeline.features.OfflineTimeEntry>, journal: List<com.raunak.daytimeline.features.OfflineJournalEntry>, notes: List<com.raunak.daytimeline.features.OfflineNote>, store: OfflineProductivityStore, openDialog: (String) -> Unit) {
    val today = LocalDate.now()
    val prefs by store.settings.collectAsStateWithLifecycle()
    val projects by store.projects.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf("Habits", "Goals", "Routines", "Notes", "Journal", "Time")
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
