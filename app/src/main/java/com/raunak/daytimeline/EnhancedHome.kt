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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raunak.daytimeline.data.ChecklistItemEntity
import com.raunak.daytimeline.domain.TaskModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EInk = Color(0xFF17222A)
private val EPaper = Color(0xFFF6F4EF)
private val ECard = Color(0xFFFFFEFC)
private val ESage = Color(0xFF527565)
private val EMuted = Color(0xFF77817F)
private val ELine = Color(0xFFE4E2DB)

@Composable
fun EnhancedHome() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = remember { AppContainer(context.applicationContext) }
    val vm: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory(app))
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val date by vm.currentDate.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pomo by vm.pomodoro.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TaskModel?>(null) }

    MaterialTheme(colorScheme = lightColorScheme(background = EPaper, surface = ECard, primary = ESage, onSurface = EInk)) {
        Scaffold(
            containerColor = EPaper,
            bottomBar = {
                NavigationBar(containerColor = ECard) {
                    listOf(Icons.Default.CalendarMonth to "Plan", Icons.Default.Timer to "Focus", Icons.Default.Insights to "Review", Icons.Default.Settings to "Settings").forEachIndexed { i, item ->
                        NavigationBarItem(tab == i, { tab = i }, icon = { Icon(item.first, null) }, label = { Text(item.second) })
                    }
                }
            },
            floatingActionButton = {
                if (tab == 0) {
                    FloatingActionButton(onClick = { showAdd = true }, containerColor = EInk, contentColor = Color.White) { Icon(Icons.Default.Add, "Add") }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (tab) {
                    0 -> PlannerTimeline(tasks, date, settings.showCompleted, vm, { selected = it }, { showSearch = true })
                    1 -> FocusCenter(pomo, tasks, vm)
                    2 -> ReviewCenter(tasks)
                    3 -> SettingsCenter(settings, vm)
                }
            }
        }
    }
    if (showAdd) QuickAddSheet(vm) { showAdd = false }
    if (showSearch) SearchSheet(tasks, { selected = it }, { showSearch = false })
    selected?.let { TaskDetails(it, app, vm) { selected = null } }
}

@Composable
private fun PlannerTimeline(tasks: List<TaskModel>, date: LocalDate, showCompleted: Boolean, vm: PlannerViewModel, openTask: (TaskModel) -> Unit, search: () -> Unit) {
    val visible = tasks.filter { showCompleted || !it.completed }.sortedBy { it.startMinute }
    val nowMinute = if (date == LocalDate.now()) java.time.LocalTime.now().let { it.hour * 60 + it.minute } else -1
    val current = visible.firstOrNull { nowMinute in it.startMinute until it.endMinute }
    val remaining = visible.count { !it.completed }
    val planned = visible.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    val done = visible.filter { it.completed }.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    val pct = if (planned == 0) 0 else (done * 100 / planned).coerceIn(0, 100)

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column { Text(if (date == LocalDate.now()) "My day" else date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(date.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.getDefault())), color = EMuted) }
                IconButton(onClick = search) { Icon(Icons.Default.Search, "Search tasks") }
            }
        }
        item { DaySummary(remaining, planned, done, pct, current) }
        item { DayStrip(date, vm) }
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${visible.size} blocks", color = EMuted, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (visible.isEmpty()) item { EmptyDay() }
        else items(visible, key = { it.id }) { task ->
            if (nowMinute >= 0 && nowMinute == task.startMinute) NowDivider()
            EnhancedTaskCard(task, openTask, { vm.toggleComplete(task, !task.completed) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun DaySummary(remaining: Int, planned: Int, done: Int, pct: Int, current: TaskModel?) {
    Surface(RoundedCornerShape(28.dp), color = EInk, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Column { Text(if (current != null) "FOCUSING NOW" else "TODAY", color = Color(0xFFB7C8C0), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(current?.title ?: "$remaining things left", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
                Surface(CircleShape, color = Color.White.copy(alpha = .12f)) { Text("$pct%", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Bold) }
            }
            Text("${planned / 60}h ${planned % 60}m planned  ·  ${done / 60}h ${done % 60}m completed", color = Color(0xFFC8D2CF))
            LinearProgressIndicator({ if (planned == 0) 0f else done.toFloat() / planned }, Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), Color(0xFFAAC6B7), Color.White.copy(alpha = .12f))
        }
    }
}

@Composable
private fun DayStrip(date: LocalDate, vm: PlannerViewModel) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (-3..3).forEach { offset ->
            val d = date.plusDays(offset.toLong()); val selected = offset == 0
            Surface(onClick = { when { offset < 0 -> vm.onPrevDay(); offset > 0 -> vm.onNextDay() } }, shape = RoundedCornerShape(18.dp), color = if (selected) EInk else ECard) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.format(DateTimeFormatter.ofPattern("EEE")), color = if (selected) Color.White else EMuted, style = MaterialTheme.typography.labelMedium)
                    Text("${d.dayOfMonth}", color = if (selected) Color.White else EInk, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EnhancedTaskCard(t: TaskModel, open: (TaskModel) -> Unit, complete: () -> Unit) {
    Surface(onClick = { open(t) }, shape = RoundedCornerShape(22.dp), color = ECard, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(70.dp).clip(CircleShape).background(Color(t.colorHex)))
            Column(Modifier.weight(1f).padding(start = 13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.priority >= 3) Icon(Icons.Default.PriorityHigh, "High priority", tint = Color(0xFFB45B4C), modifier = Modifier.size(17.dp))
                }
                Text("${clock(t.startMinute)} — ${clock(t.endMinute)}  ·  ${(t.endMinute - t.startMinute).coerceAtLeast(0)} min", color = EMuted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (t.pomodoroEnabled) MiniChip("Focus", Icons.Default.Timer)
                    if (t.recurrenceType != "NONE") MiniChip("Repeats", Icons.Default.Repeat)
                    if (t.reminderMode != "NONE") MiniChip("Reminder", Icons.Default.Notifications)
                }
            }
            Checkbox(checked = t.completed, onCheckedChange = { complete() })
        }
    }
}

@Composable private fun MiniChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(RoundedCornerShape(50), color = Color(0xFFEAF0EC)) { Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = ESage, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)); Text(text, color = ESage, style = MaterialTheme.typography.labelSmall) } }
}

@Composable private fun NowDivider() { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFB45B4C))); Spacer(Modifier.width(7.dp)); HorizontalDivider(Modifier.weight(1f), color = Color(0xFFB45B4C)); Text(" NOW", color = Color(0xFFB45B4C), style = MaterialTheme.typography.labelSmall) } }
@Composable private fun EmptyDay() { Surface(RoundedCornerShape(24.dp), color = ECard) { Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Spa, null, tint = ESage, modifier = Modifier.size(42.dp)); Spacer(Modifier.height(8.dp)); Text("Nothing planned", fontWeight = FontWeight.SemiBold); Text("Use + to shape your day.", color = EMuted) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(vm: PlannerViewModel, close: () -> Unit) {
    var input by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Quick add", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Examples:  DSA 7-9 pomodoro  ·  Gym 18:00-19:00", color = EMuted)
            OutlinedTextField(input, { input = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Describe your block") }, leadingIcon = { Icon(Icons.Default.Bolt, null) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Deep work 60m", "Break 15m", "Study 2h").forEach { suggestion -> AssistChip({ input = suggestion }, { Text(suggestion) }) } }
            Button(onClick = { if (input.isNotBlank()) { vm.quickAdd(input); close() } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Add to today") }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(tasks: List<TaskModel>, open: (TaskModel) -> Unit, close: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = tasks.filter { query.isBlank() || it.title.contains(query, true) || it.notes.contains(query, true) || it.tags.contains(query, true) }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search today's plan") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            if (results.isEmpty()) Text("No matching blocks", color = EMuted, modifier = Modifier.padding(24.dp))
            else results.forEach { t -> ListItem({ Text(t.title, fontWeight = FontWeight.SemiBold) }, { Text("${clock(t.startMinute)} · ${t.category}", color = EMuted) }, { Icon(Icons.Default.Event, null, tint = ESage) }, modifier = Modifier.clickable { open(t); close() }) }
            Spacer(Modifier.height(25.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetails(task: TaskModel, app: AppContainer, vm: PlannerViewModel, close: () -> Unit) {
    val scope = rememberCoroutineScope(); val items by app.repository.checklist(task.id).collectAsStateWithLifecycle(initialValue = emptyList()); var newItem by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) { Column(Modifier.weight(1f)) { Text(task.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${clock(task.startMinute)} — ${clock(task.endMinute)}", color = EMuted) }; IconButton({ vm.toggleComplete(task, !task.completed) }) { Icon(if (task.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, "Complete", tint = ESage) } }
            if (task.notes.isNotBlank()) Surface(RoundedCornerShape(16.dp), color = Color(0xFFF0EEE8)) { Text(task.notes, Modifier.padding(14.dp), color = EMuted) }
            Text("Checklist  ${items.count { it.checked }}/${items.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            items.sortedBy { it.position }.forEach { item -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(item.checked, { scope.launch { app.repository.toggleChecklistItem(item) } }); Text(item.text, Modifier.weight(1f), textDecoration = if (item.checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null) } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(newItem, { newItem = it }, Modifier.weight(1f), singleLine = true, label = { Text("Add checklist item") }); IconButton({ if (newItem.isNotBlank()) { scope.launch { app.repository.upsertChecklistItem(ChecklistItemEntity(taskId = task.id, text = newItem.trim(), position = items.size)) }; newItem = "" } }) { Icon(Icons.Default.AddCircle, "Add") } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (task.pomodoroEnabled) FilledTonalButton({ vm.startPomodoro(task.id); close() }) { Icon(Icons.Default.Timer, null); Spacer(Modifier.width(5.dp)); Text("Focus") }; OutlinedButton({ vm.duplicateTask(task); close() }) { Text("Duplicate") }; OutlinedButton({ vm.deleteTask(task); close() }) { Text("Delete") } }
            Spacer(Modifier.height(15.dp))
        }
    }
}

@Composable
private fun FocusCenter(p: com.raunak.daytimeline.data.PomodoroStateEntity, tasks: List<TaskModel>, vm: PlannerViewModel) {
    val focusTasks = tasks.filter { it.pomodoroEnabled && !it.completed }
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("FOCUS", color = ESage, fontWeight = FontWeight.Bold); Spacer(Modifier.height(22.dp))
        Surface(CircleShape, color = EInk, modifier = Modifier.size(250.dp)) { Box(Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("%02d:%02d".format(p.remainingSeconds / 60, p.remainingSeconds % 60), color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Light); Text(p.phase, color = Color(0xFFB8C8C0)) } } }
        Spacer(Modifier.height(20.dp)); Text("One thing at a time.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); Text("Choose a planned block or start a free session.", color = EMuted)
        Spacer(Modifier.height(18.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button({ if (p.running) vm.pausePomodoro() else vm.resumePomodoro() }) { Icon(if (p.running) Icons.Default.Pause else Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text(if (p.running) "Pause" else "Start") }; OutlinedButton(vm::resetPomodoro) { Text("Reset") } }
        Spacer(Modifier.height(22.dp)); Text("Planned focus", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp))
        focusTasks.take(5).forEach { t -> ListItem({ Text(t.title) }, { Text("${clock(t.startMinute)} · ${t.endMinute - t.startMinute} min", color = EMuted) }, { Icon(Icons.Default.Circle, null, tint = Color(t.colorHex)) }, { IconButton({ vm.startPomodoro(t.id) }) { Icon(Icons.Default.PlayArrow, "Start") } }) }
    }
}

@Composable private fun ReviewCenter(tasks: List<TaskModel>) {
    val total = tasks.size; val done = tasks.count { it.completed }; val focus = tasks.count { it.pomodoroEnabled }; val minutes = tasks.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Review", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("A quiet look at how you planned.", color = EMuted) }
        item { ReviewStat("Completion", "$done / $total", if (total == 0) 0f else done.toFloat() / total, Icons.Default.CheckCircle) }
        item { ReviewStat("Focus blocks", "$focus", if (total == 0) 0f else focus.toFloat() / total, Icons.Default.Timer) }
        item { ReviewStat("Planned", "${minutes / 60}h ${minutes % 60}m", 1f, Icons.Default.Schedule) }
        item { Text("By category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        tasks.groupBy { it.category.ifBlank { "Other" } }.forEach { (category, group) -> ListItem({ Text(category) }, { Text("${group.size} blocks · ${group.sumOf { it.endMinute - it.startMinute }} min", color = EMuted) }, { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(group.first().colorHex))) }) }
        item { Text("Planning signal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Surface(RoundedCornerShape(20.dp), color = Color(0xFFEAF0EC)) { Text(if (done == total && total > 0) "Everything planned is complete. Keep the rest of the day open." else "Protect the next unfinished block before adding more work.", Modifier.padding(16.dp), color = Color(0xFF456355)) } }
    }
}

@Composable private fun ReviewStat(title: String, value: String, progress: Float, icon: androidx.compose.ui.graphics.vector.ImageVector) { Surface(RoundedCornerShape(23.dp), color = ECard) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(title, color = EMuted); Icon(icon, null, tint = ESage) }; Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); LinearProgressIndicator({ progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), ESage, ELine) } } }

@Composable private fun SettingsCenter(settings: com.raunak.daytimeline.settings.PlannerSettings, vm: PlannerViewModel) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Keep the system quiet and useful.", color = EMuted) }
        item { SettingToggle("Auto-scroll to now", "Open today's plan around the current time", settings.autoScrollNow) { vm.updateSettings { copy(autoScrollNow = it) } } }
        item { SettingToggle("Show completed", "Keep finished blocks in the timeline", settings.showCompleted) { vm.updateSettings { copy(showCompleted = it) } } }
        item { SettingRow("Day starts", clock(settings.dayStartMinute), Icons.Default.WbSunny) }
        item { SettingRow("Day ends", clock(settings.dayEndMinute), Icons.Default.NightsStay) }
        item { SettingRow("Data", "Stored on this phone", Icons.Default.Lock) }
        item { Surface(RoundedCornerShape(22.dp), color = Color(0xFFE9F0EA)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.WifiOff, null, tint = ESage); Spacer(Modifier.width(12.dp)); Column { Text("Offline first", fontWeight = FontWeight.SemiBold); Text("Your schedule does not need a server.", color = Color(0xFF5C6B63)) } } } }
    }
}

@Composable private fun SettingToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) { Surface(RoundedCornerShape(22.dp), color = ECard) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = EMuted, style = MaterialTheme.typography.bodySmall) }; Switch(checked, onChange) } } }
@Composable private fun SettingRow(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Surface(RoundedCornerShape(22.dp), color = ECard) { ListItem({ Text(title) }, { Text(value, color = EMuted) }, { Icon(icon, null, tint = ESage) }) } }
private fun clock(m: Int) = "%02d:%02d".format((m / 60).coerceIn(0, 23), m % 60)
