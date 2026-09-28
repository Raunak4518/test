package com.raunak.daytimeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raunak.daytimeline.data.ChecklistItemEntity
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.features.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PBg = Color(0xFFF5F2EA)
private val PCard = Color(0xFFFFFDF8)
private val PInk = Color(0xFF18221F)
private val PSage = Color(0xFF55786A)
private val PMuted = Color(0xFF77817C)
private val PLine = Color(0xFFE1DED4)

@Composable
fun PowerHome() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = remember { AppContainer(context.applicationContext) }
    val vm: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory(app))
    val store = remember { LocalFeatureStore(context.applicationContext) }
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val date by vm.currentDate.collectAsStateWithLifecycle()
    val habits by store.habits.collectAsStateWithLifecycle()
    val notes by store.notes.collectAsStateWithLifecycle()
    val goals by store.goals.collectAsStateWithLifecycle()
    val templates by store.templates.collectAsStateWithLifecycle()
    val focus by store.focusSessions.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TaskModel?>(null) }

    MaterialTheme(colorScheme = lightColorScheme(background = PBg, surface = PCard, primary = PSage, onSurface = PInk)) {
        Scaffold(containerColor = PBg, bottomBar = {
            NavigationBar(containerColor = PCard) {
                val nav = listOf(Icons.Default.CalendarMonth to "Day", Icons.Default.Timer to "Focus", Icons.Default.CheckCircle to "Habits", Icons.Default.AutoStories to "Notes", Icons.Default.Insights to "Review")
                nav.forEachIndexed { i, item -> NavigationBarItem(tab == i, { tab = i }, icon = { Icon(item.first, null) }, label = { Text(item.second) }) }
            }
        }, floatingActionButton = {
            FloatingActionButton({ showAdd = true }, containerColor = PInk, contentColor = Color.White) { Icon(Icons.Default.Add, "Add") }
        }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (tab) {
                    0 -> DayWorkspace(tasks, date, vm) { selected = it }
                    1 -> FocusWorkspace(tasks, vm, store, focus)
                    2 -> HabitWorkspace(habits, store)
                    3 -> NotesWorkspace(notes, store)
                    4 -> ReviewWorkspace(tasks, goals, templates, focus, store)
                }
            }
        }
    }
    if (showAdd) AddTaskSheet(vm) { showAdd = false }
    selected?.let { TaskSheet(it, app, vm) { selected = null } }
}

@Composable
private fun DayWorkspace(tasks: List<TaskModel>, date: LocalDate, vm: PlannerViewModel, open: (TaskModel) -> Unit) {
    val now = LocalTime.now().let { it.hour * 60 + it.minute }
    val visible = tasks.sortedBy { it.startMinute }
    val total = visible.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    val done = visible.filter { it.completed }.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    val progress = if (total == 0) 0f else done.toFloat() / total
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column { Text("${if (date == LocalDate.now()) "Today" else date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(date.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.getDefault())), color = PMuted) }
                Surface(CircleShape, color = PInk) { Text("${(progress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp)) }
            }
        }
        item { LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), PSage, PLine) }
        item { DayNavigator(date, vm) }
        item { SmartSummary(visible, now) }
        item { Text("Your day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { TimeGrid(visible, now, open) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable private fun DayNavigator(date: LocalDate, vm: PlannerViewModel) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        (-3..3).forEach { n -> val d = date.plusDays(n.toLong()); val selected = n == 0; Surface(onClick = { if (n < 0) vm.onPrevDay() else if (n > 0) vm.onNextDay() }, shape = RoundedCornerShape(16.dp), color = if (selected) PInk else PCard) { Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(d.dayOfWeek.name.take(3), color = if (selected) Color.White else PMuted, style = MaterialTheme.typography.labelSmall); Text("${d.dayOfMonth}", color = if (selected) Color.White else PInk, fontWeight = FontWeight.Bold) } } }
    }
}

@Composable private fun SmartSummary(tasks: List<TaskModel>, now: Int) {
    val current = tasks.firstOrNull { now in it.startMinute until it.endMinute }
    val next = tasks.firstOrNull { it.startMinute > now }
    Surface(RoundedCornerShape(24.dp), color = PInk, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (current != null) "NOW · ${current.title}" else "NEXT · ${next?.title ?: "Your day is open"}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(if (current != null) "${clock(current.startMinute)}–${clock(current.endMinute)} · in progress" else next?.let { "Starts ${clock(it.startMinute)} · ${it.endMinute - it.startMinute} min" } ?: "Add a block whenever you're ready", color = Color(0xFFC7D2CD))
            if (next != null && current != null) Text("Up next: ${next.title} at ${clock(next.startMinute)}", color = Color(0xFFAAC7B8), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun TimeGrid(tasks: List<TaskModel>, now: Int, open: (TaskModel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tasks.forEach { task ->
            val gap = tasks.zipWithNext().firstOrNull { it.first.id == task.id }?.let { (it.second.startMinute - task.endMinute).coerceAtLeast(0) } ?: 0
            if (now >= 0 && now in task.startMinute..task.endMinute) Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFB85F50))); HorizontalDivider(Modifier.weight(1f), color = Color(0xFFB85F50)); Text(" NOW", color = Color(0xFFB85F50), style = MaterialTheme.typography.labelSmall) }
            TaskBlock(task, open)
            if (gap >= 20) Surface(RoundedCornerShape(16.dp), color = Color(0xFFECE9E0), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Spa, null, tint = PSage, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("${gap / 60}h ${gap % 60}m open", color = PMuted); Spacer(Modifier.weight(1f)); Text("FREE TIME", color = PMuted, style = MaterialTheme.typography.labelSmall) } }
        }
        if (tasks.isEmpty()) Surface(RoundedCornerShape(24.dp), color = PCard) { Column(Modifier.fillMaxWidth().padding(35.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.WbSunny, null, tint = PSage, modifier = Modifier.size(42.dp)); Text("Blank canvas", fontWeight = FontWeight.SemiBold); Text("Add your first block and shape the day.", color = PMuted) } }
    }
}

@Composable private fun TaskBlock(t: TaskModel, open: (TaskModel) -> Unit) {
    Surface(onClick = { open(t) }, shape = RoundedCornerShape(21.dp), color = PCard, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(68.dp).clip(CircleShape).background(Color(t.colorHex)))
            Column(Modifier.weight(1f).padding(start = 13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.title, fontWeight = FontWeight.SemiBold)
                Text("${clock(t.startMinute)} – ${clock(t.endMinute)}  ·  ${t.endMinute - t.startMinute}m", color = PMuted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { if (t.category.isNotBlank()) TinyTag(t.category); if (t.priority >= 3) TinyTag("Priority"); if (t.pomodoroEnabled) TinyTag("Pomodoro"); if (t.recurrenceType != "NONE") TinyTag("Repeating") }
            }
            Icon(if (t.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (t.completed) PSage else PLine)
        }
    }
}

@Composable private fun TinyTag(text: String) { Surface(RoundedCornerShape(30.dp), color = Color(0xFFEAF0EC)) { Text(text, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = PSage, style = MaterialTheme.typography.labelSmall) } }

@Composable private fun FocusWorkspace(tasks: List<TaskModel>, vm: PlannerViewModel, store: LocalFeatureStore, logs: List<FocusLog>) {
    val pomo by vm.pomodoro.collectAsStateWithLifecycle()
    val focusMinutes = logs.filter { java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }.sumOf { it.minutes }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Text("Focus", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Protect attention, then measure what happened.", color = PMuted) }
        item { Surface(RoundedCornerShape(30.dp), color = PInk, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(pomo.phase.replace('_', ' '), color = Color(0xFFB8C9C1), style = MaterialTheme.typography.labelMedium); Text(formatSeconds(pomo.remainingSeconds), color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (pomo.running) FilledTonalButton({ vm.pausePomodoro() }) { Icon(Icons.Default.Pause, null); Text("Pause") } else FilledTonalButton({ vm.resumePomodoro() }) { Icon(Icons.Default.PlayArrow, null); Text("Resume") }; OutlinedButton({ vm.resetPomodoro() }) { Text("Reset") } } } } }
        item { StatRow("Focused today", "${focusMinutes / 60}h ${focusMinutes % 60}m", Icons.Default.Bolt) }
        item { Text("Start a task", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(tasks.filter { it.pomodoroEnabled && !it.completed }.take(8), key = { it.id }) { t -> ListItem({ Text(t.title, fontWeight = FontWeight.SemiBold) }, { Text("${clock(t.startMinute)} · ${t.endMinute - t.startMinute}m", color = PMuted) }, { Icon(Icons.Default.Timer, null, tint = PSage) }, modifier = Modifier.clickable { vm.startPomodoro(t.id) }) }
    }
}

@Composable private fun HabitWorkspace(habits: List<Habit>, store: LocalFeatureStore) {
    var add by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; val today = LocalDate.now().toString()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Column { Text("Habits", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Small actions, visible streaks.", color = PMuted) }; IconButton({ add = true }) { Icon(Icons.Default.Add, "Add habit") } } }
        items(habits, key = { it.id }) { h -> val done = today in h.completedDates; val streak = streak(h.completedDates); Surface(RoundedCornerShape(20.dp), color = PCard) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(done, { store.toggleHabit(h.id, LocalDate.now()) }); Column(Modifier.weight(1f)) { Text(h.name, fontWeight = FontWeight.SemiBold); Text("${if (done) "Done today" else "Not done yet"} · ${streak}-day streak", color = PMuted, style = MaterialTheme.typography.bodySmall) }; IconButton({ store.deleteHabit(h.id) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = PMuted) } } } }
        if (habits.isEmpty()) item { EmptyCard("Build a habit", "Add routines you want to repeat daily.") }
    }
    if (add) AlertDialog(onDismissRequest = { add = false }, title = { Text("New habit") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Habit name") }) }, confirmButton = { TextButton({ if (name.isNotBlank()) { store.addHabit(name); name = ""; add = false } }) { Text("Create") } }, dismissButton = { TextButton({ add = false }) { Text("Cancel") } })
}

@Composable private fun NotesWorkspace(notes: List<QuickNote>, store: LocalFeatureStore) {
    var editor by remember { mutableStateOf(false) }; var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }; var query by remember { mutableStateOf("") }
    val filtered = notes.filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) || it.tags.contains(query, true) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Notes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); IconButton({ editor = true }) { Icon(Icons.Default.NoteAdd, "New note") } } }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search notes") }, leadingIcon = { Icon(Icons.Default.Search, null) }) }
        items(filtered, key = { it.id }) { n -> Surface(RoundedCornerShape(20.dp), color = PCard) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(n.title, fontWeight = FontWeight.SemiBold); IconButton({ store.deleteNote(n.id) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = PMuted) } }; Text(n.body.take(240), color = PMuted); if (n.tags.isNotBlank()) Text("#${n.tags}", color = PSage, style = MaterialTheme.typography.labelSmall) } } }
        if (filtered.isEmpty()) item { EmptyCard("Your local notebook", "Capture ideas, study notes and reflections. Everything stays on this phone.") }
    }
    if (editor) AlertDialog(onDismissRequest = { editor = false }, title = { Text("New note") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(title, { title = it }, label = { Text("Title") }); OutlinedTextField(body, { body = it }, label = { Text("Note") }, minLines = 5) } }, confirmButton = { TextButton({ if (body.isNotBlank() || title.isNotBlank()) { store.addNote(title, body); title = ""; body = ""; editor = false } }) { Text("Save") } }, dismissButton = { TextButton({ editor = false }) { Text("Cancel") } })
}

@Composable private fun ReviewWorkspace(tasks: List<TaskModel>, goals: List<Goal>, templates: List<PlanTemplate>, focus: List<FocusLog>, store: LocalFeatureStore) {
    var review by remember { mutableStateOf(false) }; var wins by remember { mutableStateOf("") }; var blockers by remember { mutableStateOf("") }; var gratitude by remember { mutableStateOf("") }
    val done = tasks.count { it.completed }; val planned = tasks.size; val mins = tasks.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }; val focusMins = focus.filter { java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }.sumOf { it.minutes }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Review", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Understand the day, don't just count it.", color = PMuted) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatBox("Done", "$done/$planned", Modifier.weight(1f)); StatBox("Planned", "${mins / 60}h ${mins % 60}m", Modifier.weight(1f)); StatBox("Focus", "${focusMins / 60}h ${focusMins % 60}m", Modifier.weight(1f)) } }
        item { Text("Goals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(goals, key = { it.id }) { g -> val p = g.progress.toFloat() / g.target; Surface(RoundedCornerShape(20.dp), color = PCard) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(g.title, fontWeight = FontWeight.SemiBold); Text("${g.progress}/${g.target}", color = PMuted) }; LinearProgressIndicator({ p }, Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), PSage, PLine); Row { TextButton({ store.incrementGoal(g.id) }) { Text("+1") }; Spacer(Modifier.weight(1f)); TextButton({ store.deleteGoal(g.id) }) { Text("Delete") } } } } }
        item { Button({ review = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.EditNote, null); Spacer(Modifier.width(7.dp)); Text("Write today's review") } }
        item { Text("Reusable plans", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(templates, key = { it.id }) { t -> Surface(RoundedCornerShape(20.dp), color = PCard) { Column(Modifier.padding(15.dp)) { Text(t.name, fontWeight = FontWeight.SemiBold); Text(t.tasks.joinToString("  ·  ") { "${it.title} ${it.minutes}m" }, color = PMuted, style = MaterialTheme.typography.bodySmall) } } }
    }
    if (review) AlertDialog(onDismissRequest = { review = false }, title = { Text("Daily review") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(wins, { wins = it }, label = { Text("What went well?") }); OutlinedTextField(blockers, { blockers = it }, label = { Text("What got in the way?") }); OutlinedTextField(gratitude, { gratitude = it }, label = { Text("One thing you're grateful for") }) } }, confirmButton = { TextButton({ store.saveReview(DailyReview(LocalDate.now().toString(), wins, blockers, gratitude, 0)); wins = ""; blockers = ""; gratitude = ""; review = false }) { Text("Save review") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AddTaskSheet(vm: PlannerViewModel, close: () -> Unit) {
    var title by remember { mutableStateOf("") }; var start by remember { mutableStateOf("09:00") }; var end by remember { mutableStateOf("10:00") }; var notes by remember { mutableStateOf("") }; var pomo by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = close) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text("Shape your time", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Task") }, leadingIcon = { Icon(Icons.Default.Edit, null) }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(start, { start = it }, Modifier.weight(1f), label = { Text("Start") }); OutlinedTextField(end, { end = it }, Modifier.weight(1f), label = { Text("End") }) }; OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Notes") }); Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Pomodoro focus"); Switch(pomo, { pomo = it }) }; Button({ parseTime(start)?.let { s -> parseTime(end)?.let { e -> if (title.isNotBlank() && e > s) vm.addOrUpdateTask(null, title, s, e, pomo, notes, 1, "NONE", "NONE", 0) } }; close() }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Add block") }; Spacer(Modifier.height(25.dp)) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TaskSheet(task: TaskModel, app: AppContainer, vm: PlannerViewModel, close: () -> Unit) {
    val scope = rememberCoroutineScope(); val items by app.repository.checklist(task.id).collectAsStateWithLifecycle(initialValue = emptyList()); var item by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = close) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(task.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${clock(task.startMinute)}–${clock(task.endMinute)} · ${task.category}", color = PMuted) }; IconButton({ vm.toggleComplete(task, !task.completed) }) { Icon(if (task.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = PSage) } }; if (task.notes.isNotBlank()) Text(task.notes, color = PMuted); Text("Checklist · ${items.count { it.checked }}/${items.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); items.sortedBy { it.position }.forEach { i -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(i.checked, { scope.launch { app.repository.toggleChecklistItem(i) } }); Text(i.text, Modifier.weight(1f), textDecoration = if (i.checked) TextDecoration.LineThrough else null) } }; Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(item, { item = it }, Modifier.weight(1f), singleLine = true, label = { Text("Add item") }); IconButton({ if (item.isNotBlank()) { scope.launch { app.repository.upsertChecklistItem(ChecklistItemEntity(taskId = task.id, text = item.trim(), position = items.size)) }; item = "" } }) { Icon(Icons.Default.AddCircle, "Add") } }; Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { if (task.pomodoroEnabled) FilledTonalButton({ vm.startPomodoro(task.id); close() }) { Icon(Icons.Default.Timer, null); Text("Focus") }; OutlinedButton({ vm.duplicateTask(task); close() }) { Text("Duplicate") }; OutlinedButton({ vm.deleteTask(task); close() }) { Text("Delete") } }; Spacer(Modifier.height(20.dp)) } }
}

@Composable private fun StatBox(label: String, value: String, modifier: Modifier) { Surface(RoundedCornerShape(18.dp), color = PCard, modifier = modifier) { Column(Modifier.padding(13.dp)) { Text(label, color = PMuted, style = MaterialTheme.typography.labelSmall); Text(value, fontWeight = FontWeight.Bold) } } }
@Composable private fun StatRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Surface(RoundedCornerShape(20.dp), color = PCard, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = PSage); Spacer(Modifier.width(10.dp)); Text(label, Modifier.weight(1f)); Text(value, fontWeight = FontWeight.Bold) } } }
@Composable private fun EmptyCard(title: String, body: String) { Surface(RoundedCornerShape(22.dp), color = PCard) { Column(Modifier.fillMaxWidth().padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Spa, null, tint = PSage, modifier = Modifier.size(38.dp)); Text(title, fontWeight = FontWeight.SemiBold); Text(body, color = PMuted) } } }

private fun parseTime(value: String): Int? = value.trim().split(":").takeIf { it.size == 2 }?.let { p -> p[0].toIntOrNull()?.let { h -> p[1].toIntOrNull()?.let { m -> if (h in 0..23 && m in 0..59) h * 60 + m else null } } }
private fun clock(minute: Int): String = "%02d:%02d".format(Locale.getDefault(), (minute / 60).coerceIn(0, 23), minute % 60)
private fun formatSeconds(seconds: Long): String = "%02d:%02d".format(Locale.getDefault(), (seconds / 60).coerceAtLeast(0), (seconds % 60).coerceAtLeast(0))
private fun streak(dates: List<String>): Int { var d = LocalDate.now(); var count = 0; val set = dates.toSet(); while (d.toString() in set) { count++; d = d.minusDays(1) }; return count }
