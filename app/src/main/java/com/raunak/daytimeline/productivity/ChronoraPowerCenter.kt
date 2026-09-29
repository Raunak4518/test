package com.raunak.daytimeline.productivity

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.CalendarContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.ChronoraBrandLine
import com.raunak.daytimeline.MadeByRaunak
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.features.OfflineProductivityStore
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class ChronoraDependencyStore(context: Context) {
    private val prefs = context.getSharedPreferences("chronora_dependencies", Context.MODE_PRIVATE)
    fun dependencies(taskId: Long): Set<Long> = prefs.getStringSet("d:" + taskId, emptySet<String>())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    fun set(taskId: Long, ids: Set<Long>) = prefs.edit().putStringSet("d:" + taskId, ids.map { it.toString() }.toSet()).apply()
    fun blocked(taskId: Long, completed: Set<Long>): Boolean = dependencies(taskId).any { it !in completed }
}

data class StudyCard(val id: Long, val front: String, val back: String, val due: Long, val interval: Int, val ease: Double, val reviews: Int)

class ChronoraStudyStore(context: Context) {
    private val prefs = context.getSharedPreferences("chronora_study", Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()
    private val type = object : com.google.gson.reflect.TypeToken<List<StudyCard>>() {}.type
    fun cards(): List<StudyCard> = runCatching { gson.fromJson<List<StudyCard>>(prefs.getString("cards", "[]"), type) ?: emptyList() }.getOrDefault(emptyList())
    private fun save(value: List<StudyCard>) = prefs.edit().putString("cards", gson.toJson(value)).apply()
    fun add(front: String, back: String) { save(cards() + StudyCard(System.currentTimeMillis(), front, back, LocalDate.now().toEpochDay(), 1, 2.5, 0)) }
    fun grade(id: Long, quality: Int) {
        val today = LocalDate.now().toEpochDay()
        save(cards().map { card ->
            if (card.id != id) card else {
                val q = quality.coerceIn(0, 5)
                val ease = (card.ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))).coerceAtLeast(1.3)
                val interval = when {
                    q < 3 -> 1
                    card.reviews == 0 -> 1
                    card.reviews == 1 -> 6
                    else -> (card.interval * ease).roundToInt().coerceAtMost(365)
                }
                card.copy(due = today + interval, interval = interval, ease = ease, reviews = card.reviews + 1)
            }
        })
    }
}

object ChronoraExport {
    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private fun esc(value: String) = value.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")
    fun ics(tasks: List<TaskModel>): String = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Chronora//Planner//EN\r\n")
        tasks.forEach { task ->
            val start = task.date.atTime(task.startMinute / 60, task.startMinute % 60).format(fmt)
            val end = task.date.atTime(task.endMinute / 60, task.endMinute % 60).format(fmt)
            append("BEGIN:VEVENT\r\nUID:").append(task.id).append("@chronora\r\n")
            append("DTSTART:").append(start).append("\r\nDTEND:").append(end).append("\r\n")
            append("SUMMARY:").append(esc(task.title)).append("\r\nDESCRIPTION:").append(esc(task.notes)).append("\r\nEND:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }
    fun csv(tasks: List<TaskModel>): String = buildString {
        append("id,date,title,start,end,priority,completed,tags,notes\n")
        tasks.forEach { task ->
            val values = listOf(task.id, task.date, task.title, task.startMinute, task.endMinute, task.priority, task.completed, task.tags, task.notes)
            append(values.joinToString(",") { "\"" + it.toString().replace("\"", "\"\"") + "\"" }).append("\n")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronoraPowerCenter(tasks: List<TaskModel>, store: OfflineProductivityStore, onClose: () -> Unit, onSelectDate: (LocalDate) -> Unit = {}) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Chronora Power Center") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back") } }) },
        bottomBar = {
            NavigationBar {
                val labels = listOf("Calendar", "Plan", "Insights", "Study", "Focus", "Review")
                val icons = listOf(Icons.Default.CalendarViewWeek, Icons.Default.AutoAwesome, Icons.Default.Insights, Icons.Default.School, Icons.Default.DoNotDisturb, Icons.Default.RateReview)
                labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], null) }, label = { Text(label) }) }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> CalendarMatrix(tasks, onSelectDate)
                1 -> PlanningCenter(tasks)
                2 -> InsightsCenter(tasks, store)
                3 -> StudyCenter()
                4 -> FocusShield()
                else -> ReviewCenter(tasks, store)
            }
        }
    }
}

@Composable
private fun CalendarMatrix(tasks: List<TaskModel>, onSelect: (LocalDate) -> Unit) {
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ anchor = if (month) anchor.minusMonths(1) else anchor.minusWeeks(1) }) { Icon(Icons.Default.ChevronLeft, "Previous") }
            Text(anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton({ anchor = if (month) anchor.plusMonths(1) else anchor.plusWeeks(1) }) { Icon(Icons.Default.ChevronRight, "Next") }
            FilterChip(!month, { month = false }, label = { Text("Week") })
            Spacer(Modifier.width(4.dp))
            FilterChip(month, { month = true }, label = { Text("Month") })
        }
        if (!month) {
            val monday = anchor.with(DayOfWeek.MONDAY)
            Row(Modifier.fillMaxWidth()) { repeat(7) { offset -> Text(monday.plusDays(offset.toLong()).format(DateTimeFormatter.ofPattern("EEE d")), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) } }
            Row(Modifier.fillMaxSize()) {
                (0..6).forEach { offset ->
                    val date = monday.plusDays(offset.toLong())
                    Card(onClick = { onSelect(date) }, modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
                        LazyColumn(Modifier.padding(4.dp)) {
                            items(tasks.filter { it.date == date }.take(25), key = { it.id }) { task -> Text(task.title, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 4.dp), maxLines = 2) }
                        }
                    }
                }
            }
        } else {
            val first = anchor.withDayOfMonth(1)
            val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
            LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxSize()) {
                items(42) { index ->
                    val date = start.plusDays(index.toLong())
                    Card(onClick = { onSelect(date) }, modifier = Modifier.padding(2.dp).height(82.dp)) {
                        Column(Modifier.padding(5.dp)) {
                            Text(date.dayOfMonth.toString(), fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                            tasks.filter { it.date == date }.take(3).forEach { Text(it.title, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanningCenter(tasks: List<TaskModel>) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var templateOpen by remember { mutableStateOf(false) }
    var dependencyTask by remember { mutableStateOf<TaskModel?>(null) }
    val planning = remember(context) { ChronoraPlanningStore(context) }
    val filtered = tasks.filter { task -> query.isBlank() || listOf(task.title, task.notes, task.tags, task.category).any { it.contains(query, true) } }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search tasks, notes, tags and categories") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button({ menu = true }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("Export") }
            OutlinedButton({ templateOpen = true }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Templates") }
            OutlinedButton({ context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Icon(Icons.Default.Block, null); Spacer(Modifier.width(4.dp)); Text("Focus access") }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("ICS calendar") }, onClick = { menu = false; shareText(context, "Chronora calendar.ics", ChronoraExport.ics(tasks), "text/calendar") })
            DropdownMenuItem(text = { Text("CSV tasks") }, onClick = { menu = false; shareText(context, "chronora-tasks.csv", ChronoraExport.csv(tasks), "text/csv") })
            DropdownMenuItem(text = { Text("JSON tasks") }, onClick = { menu = false; shareText(context, "chronora-tasks.json", com.google.gson.Gson().toJson(tasks), "application/json") })
        }
        Text(filtered.size.toString() + " matching tasks", style = MaterialTheme.typography.labelMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { task ->
                ListItem(
                    headlineContent = { Text(task.title) },
                    supportingContent = { Text(task.date.toString() + " · " + task.tags) },
                    trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { IconButton({ dependencyTask = task }) { Icon(Icons.Default.Link, "Dependencies") }; Icon(if (task.completed) Icons.Default.Check else Icons.Default.RadioButtonUnchecked, null) } }
                )
            }
        }
    }
}

@Composable
private fun InsightsCenter(tasks: List<TaskModel>, store: OfflineProductivityStore) {
    val today = LocalDate.now()
    val dates = (0..29).map { today.minusDays(it.toLong()) }.reversed()
    val planned = dates.map { date -> tasks.filter { it.date == date }.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) } }
    val completed = dates.map { date -> tasks.filter { it.date == date && it.completed }.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("30-day analytics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { MetricCard("Planned", planned.sum().toString() + " min") }
        item { MetricCard("Completed", completed.sum().toString() + " min") }
        item { MetricCard("Completion ratio", if (planned.sum() == 0) "0%" else (completed.sum() * 100 / planned.sum()).toString() + "%") }
        item { MetricCard("Tracked today", store.todayTrackedMinutes().toString() + " min") }
        item { Text("Daily trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(dates) { date ->
            val index = dates.indexOf(date)
            val p = planned[index]
            val c = completed[index]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(date.format(DateTimeFormatter.ofPattern("dd MMM")), Modifier.width(65.dp))
                LinearProgressIndicator(progress = { if (p == 0) 0f else c.toFloat() / p }, Modifier.weight(1f))
                Text(" " + c + "/" + p, Modifier.width(75.dp))
            }
        }
    }
}

@Composable private fun MetricCard(label: String, value: String) {
    Card { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.Bold) } }
}

@Composable private fun StudyCenter() {
    val context = LocalContext.current
    val study = remember(context) { ChronoraStudyStore(context) }
    var cards by remember { mutableStateOf(study.cards()) }
    var add by remember { mutableStateOf(false) }
    val due = cards.filter { it.due <= LocalDate.now().toEpochDay() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Study mode", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(due.size.toString() + " due · " + cards.size + " total") }
            Button({ add = true }) { Text("Add") }
        }
        LazyColumn {
            items(due, key = { it.id }) { card ->
                var reveal by remember(card.id) { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(card.front, fontWeight = FontWeight.Bold)
                        if (reveal) Text(card.back, Modifier.padding(top = 10.dp))
                        TextButton({ reveal = !reveal }) { Text(if (reveal) "Hide answer" else "Show answer") }
                        if (reveal) Row { listOf(0 to "Again", 3 to "Hard", 4 to "Good", 5 to "Easy").forEach { (q, label) -> TextButton({ study.grade(card.id, q); cards = study.cards() }) { Text(label) } } }
                    }
                }
            }
        }
    }
    if (add) {
        var front by remember { mutableStateOf("") }
        var back by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("New flashcard") }, text = { Column { OutlinedTextField(front, { front = it }, label = { Text("Front") }); OutlinedTextField(back, { back = it }, label = { Text("Back") }) } }, confirmButton = { Button({ if (front.isNotBlank() && back.isNotBlank()) { study.add(front, back); cards = study.cards(); add = false } }) { Text("Add") } }, dismissButton = { TextButton({ add = false }) { Text("Cancel") } })
    }
}

@Composable private fun FocusShield() {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Default.DoNotDisturbOn, null, Modifier.size(64.dp))
        Text("Focus shield", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Usage access lets Chronora measure app usage for focus analytics. Android does not permit an ordinary app to silently block or make other apps uninstallable.")
        Button({ context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Grant usage access") }
        OutlinedButton({ context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Notification access") }
        Text("For system-level app timers and blocking, use Android Digital Wellbeing.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun ReviewCenter(tasks: List<TaskModel>, store: OfflineProductivityStore) {
    val today = LocalDate.now()
    val done = tasks.count { it.date == today && it.completed }
    val total = tasks.count { it.date == today }
    var score by remember { mutableIntStateOf(3) }
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Daily & weekly review", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Today: " + done + "/" + total + " tasks completed")
        Text("Review score: " + score + "/5")
        Row { (1..5).forEach { value -> TextButton({ score = value }) { Text(value.toString()) } } }
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp), label = { Text("Wins, blockers, lessons and next actions") })
        Button({ store.addJournal(today, score, score, text, "", "", text) }, Modifier.padding(top = 10.dp)) { Text("Save review") }
        Spacer(Modifier.height(18.dp))
        Text("Weekly checklist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        listOf("Review planned vs completed time", "Find recurring unfinished work", "Review habits and streaks", "Choose three priorities", "Schedule buffer and recovery").forEach { Text("□ " + it, Modifier.padding(vertical = 5.dp)) }
        Spacer(Modifier.height(16.dp))
        MadeByRaunak()
        ChronoraBrandLine()
    }
}

private fun shareText(context: Context, name: String, value: String, mime: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_TEXT, value); putExtra(Intent.EXTRA_TITLE, name) }
    context.startActivity(Intent.createChooser(intent, "Share " + name))
}


private fun openCalendar(context: Context, task: TaskModel) {
    val start = task.date.atTime(task.startMinute / 60, task.startMinute % 60)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = task.date.atTime(task.endMinute / 60, task.endMinute % 60)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, task.title)
        putExtra(CalendarContract.Events.DESCRIPTION, task.notes)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
    }
    runCatching { context.startActivity(intent) }
}
