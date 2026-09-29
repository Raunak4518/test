package com.raunak.daytimeline

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.features.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletionCenter(vm: PlannerViewModel, productivity: OfflineProductivityStore, close: () -> Unit) {
    val context = LocalContext.current
    val tasks by vm.allTasks.collectAsStateWithLifecycle()
    val habits by productivity.habits.collectAsStateWithLifecycle()
    val goals by productivity.goals.collectAsStateWithLifecycle()
    val entries by productivity.timeEntries.collectAsStateWithLifecycle()
    val store = remember(context) { CompletionStore(context.applicationContext) }
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Chronora · Command center") }, navigationIcon = { TextButton(onClick = close) { Text("Close") } }) },
        bottomBar = {
            NavigationBar {
                val labels = listOf("Calendar", "Search", "Study", "Insights", "Review", "Tools")
                val icons = listOf(Icons.Default.CalendarMonth, Icons.Default.Search, Icons.Default.School, Icons.Default.Insights, Icons.Default.RateReview, Icons.Default.Tune)
                labels.forEachIndexed { i, label ->
                    NavigationBarItem(tab == i, { tab = i }, icon = { Icon(icons[i], null) }, label = { Text(label) })
                }
            }
        }
    ) { p ->
        Box(Modifier.fillMaxSize().padding(p)) {
            when (tab) {
                0 -> CalendarWorkspace(vm, tasks, store)
                1 -> UniversalSearch(tasks, habits, goals, productivity, store)
                2 -> StudyWorkspace(store)
                3 -> InsightsWorkspace(tasks, habits, goals, entries, store)
                4 -> ReviewWorkspace(store)
                else -> ToolsWorkspace(context, tasks, store)
            }
        }
    }
}

@Composable
private fun CalendarWorkspace(vm: PlannerViewModel, tasks: List<TaskModel>, store: CompletionStore) {
    var mode by remember { mutableStateOf("DAY") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    val graph = remember(store.dependencies()) { DependencyGraph(store.dependencies()) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Interactive calendar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row {
                listOf("DAY", "WEEK", "MONTH").forEach { x -> FilterChip(mode == x, { mode = x }, label = { Text(x) }) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { if (mode == "MONTH") month = month.minusMonths(1) else if (mode == "WEEK") date = date.minusWeeks(1) else date = date.minusDays(1) }) { Text("Previous") }
            TextButton(onClick = { date = LocalDate.now(); month = YearMonth.now() }) { Text("Today") }
            TextButton(onClick = { if (mode == "MONTH") month = month.plusMonths(1) else if (mode == "WEEK") date = date.plusWeeks(1) else date = date.plusDays(1) }) { Text("Next") }
        }
        when (mode) {
            "DAY" -> DaySchedule(vm, tasks.filter { it.date == date }, tasks, graph, date)
            "WEEK" -> WeekSchedule(tasks, date)
            else -> MonthSchedule(tasks, month)
        }
    }
}

@Composable
private fun DaySchedule(vm: PlannerViewModel, tasks: List<TaskModel>, allTasks: List<TaskModel>, graph: DependencyGraph, date: LocalDate) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card { Column(Modifier.padding(12.dp)) {
                Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.titleMedium)
                Text("Drag a block vertically to move it. Duration controls resize it.")
            } }
        }
        items(tasks.sortedBy { it.startMinute }, key = { it.id }) { task ->
            var drag by remember(task.id) { mutableFloatStateOf(0f) }
            val blocked = graph.isBlocked(task, allTasks)
            Card(Modifier.fillMaxWidth().pointerInput(task.id) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> drag += amount },
                    onDragEnd = {
                        val delta = (drag / 20f).roundToInt() * 15
                        if (delta != 0) vm.moveTask(task, task.startMinute + delta, task.endMinute + delta)
                        drag = 0f
                    }
                )
            }) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        if (blocked) AssistChip(onClick = {}, label = { Text("Blocked") })
                    }
                    Text(clock(task.startMinute) + "–" + clock(task.endMinute) + " · " + (task.endMinute - task.startMinute) + "m")
                    if (blocked) Text("Waiting for: " + graph.blockers(task, allTasks).joinToString { it.title }, color = MaterialTheme.colorScheme.error)
                    Row {
                        TextButton(onClick = { vm.moveTask(task, task.startMinute - 15, task.endMinute - 15) }) { Text("−15m") }
                        TextButton(onClick = { vm.moveTask(task, task.startMinute + 15, task.endMinute + 15) }) { Text("+15m") }
                        TextButton(onClick = { vm.resizeTask(task, task.endMinute - 15) }) { Text("Shorten") }
                        TextButton(onClick = { vm.resizeTask(task, task.endMinute + 15) }) { Text("Extend") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekSchedule(tasks: List<TaskModel>, anchor: LocalDate) {
    val start = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items((0..6).toList()) { offset ->
            val day = start.plusDays(offset.toLong())
            val list = tasks.filter { it.date == day }.sortedBy { it.startMinute }
            Card { Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text(day.format(DateTimeFormatter.ofPattern("EEE d MMM")), fontWeight = FontWeight.Bold)
                if (list.isEmpty()) Text("Free")
                list.forEach { Text("• " + clock(it.startMinute) + " " + it.title + " (" + (it.endMinute - it.startMinute) + "m)") }
            } }
        }
    }
}

@Composable
private fun MonthSchedule(tasks: List<TaskModel>, month: YearMonth) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        item { Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items((1..month.lengthOfMonth()).toList()) { day ->
            val date = month.atDay(day)
            val list = tasks.filter { it.date == date }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("%02d".format(day), Modifier.width(36.dp))
                Text(list.size.toString() + " tasks", Modifier.weight(1f))
                Text(list.count { it.completed }.toString() + " done")
            }
        }
    }
}

@Composable
private fun UniversalSearch(tasks: List<TaskModel>, habits: List<OfflineHabit>, goals: List<OfflineGoal>, productivity: OfflineProductivityStore, store: CompletionStore) {
    var query by remember { mutableStateOf("") }
    val notes by productivity.notes.collectAsStateWithLifecycle()
    val journal by productivity.journal.collectAsStateWithLifecycle()
    val routines by productivity.routines.collectAsStateWithLifecycle()
    val q = query.trim().lowercase()
    val results = buildList<Pair<String, String>> {
        tasks.filter { q.isBlank() || it.title.lowercase().contains(q) || it.notes.lowercase().contains(q) || it.tags.lowercase().contains(q) }.forEach { add("Task · " + it.title to it.date.toString()) }
        notes.filter { q.isBlank() || it.title.lowercase().contains(q) || it.body.lowercase().contains(q) }.forEach { add("Note · " + it.title to it.body.take(120)) }
        goals.filter { q.isBlank() || it.title.lowercase().contains(q) }.forEach { add("Goal · " + it.title to it.progress.toString() + "/" + it.target) }
        habits.filter { q.isBlank() || it.name.lowercase().contains(q) }.forEach { add("Habit · " + it.name to it.streak().toString() + " day streak") }
        routines.filter { q.isBlank() || it.name.lowercase().contains(q) }.forEach { add("Routine · " + it.name to it.steps.size.toString() + " steps") }
        journal.filter { q.isBlank() || it.wins.lowercase().contains(q) || it.blockers.lowercase().contains(q) || it.note.lowercase().contains(q) }.forEach { add("Journal · " + it.date to it.note.take(120)) }
        store.studyCards().filter { q.isBlank() || it.front.lowercase().contains(q) || it.back.lowercase().contains(q) }.forEach { add("Flashcard · " + it.deck to it.front.take(120)) }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text("Search tasks, notes, goals, habits, journal, study") }, modifier = Modifier.fillMaxWidth())
        Text(results.size.toString() + " results", modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) { items(results.take(100)) { ListItem(headlineContent = { Text(it.first) }, supportingContent = { Text(it.second) }) } }
    }
}

@Composable
private fun StudyWorkspace(store: CompletionStore) {
    var cards by remember { mutableStateOf(store.studyCards()) }
    var showAdd by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var deck by remember { mutableStateOf("Default") }
    var examName by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf("") }
    val today = LocalDate.now().toEpochDay()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Study mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Flashcards use an offline SM-2-style scheduler. Exam plans stay on device.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAdd = !showAdd }) { Text("New card") }
            OutlinedButton(onClick = { cards = store.studyCards() }) { Text("Refresh") }
        }
        if (showAdd) Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedTextField(front, { front = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(back, { back = it }, label = { Text("Answer") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(deck, { deck = it }, label = { Text("Deck") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { if (front.isNotBlank() && back.isNotBlank()) { store.saveCard(StudyCard(System.currentTimeMillis(), front, back, deck)); cards = store.studyCards(); front = ""; back = ""; showAdd = false } }) { Text("Save") }
        } }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(cards.filter { it.dueEpochDay <= today }, key = { it.id }) { card ->
                var reveal by remember(card.id) { mutableStateOf(false) }
                Card { Column(Modifier.padding(10.dp)) {
                    Text(card.front, fontWeight = FontWeight.SemiBold)
                    if (reveal) Text(card.back, Modifier.padding(vertical = 6.dp))
                    TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide" else "Reveal") }
                    if (reveal) Row { (0..5).forEach { quality -> TextButton(onClick = { store.saveCard(SrsEngine.grade(card, quality)); cards = store.studyCards() }) { Text(quality.toString()) } } }
                } }
            }
        }
        Text("Exam planner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(examName, { examName = it }, label = { Text("Exam") }, Modifier.weight(1f))
            OutlinedTextField(examDate, { examDate = it }, label = { Text("YYYY-MM-DD") }, Modifier.weight(1f))
            Button(onClick = { if (examName.isNotBlank() && runCatching { LocalDate.parse(examDate) }.isSuccess) { store.saveExam(ExamPlan(System.currentTimeMillis(), examName, examDate, emptyList())); examName = ""; examDate = "" } }) { Text("Add") }
        }
        store.exams().sortedBy { it.date }.take(4).forEach { Text(it.name + " · " + it.date) }
    }
}

@Composable
private fun InsightsWorkspace(tasks: List<TaskModel>, habits: List<OfflineHabit>, goals: List<OfflineGoal>, entries: List<OfflineTimeEntry>, store: CompletionStore) {
    val snap = ProductivityAnalyticsEngine.snapshot(tasks, habits, goals, entries)
    val weekly = DailyReviewEngine.weeklySummary(store.reviews())
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Analytics & workload", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { MetricCard("Completion", snap.completionPercent.toString() + "%", snap.completedTasks.toString() + "/" + snap.totalTasks + " tasks") }
        item { MetricCard("Planned", snap.plannedMinutes.toString() + "m", snap.completedMinutes.toString() + "m completed") }
        item { MetricCard("Focus", snap.focusMinutes.toString() + "m", "tracked today") }
        item { MetricCard("Workload", snap.workloadScore.toString() + "/100", if (snap.workloadScore >= 75) "Heavy" else "Within capacity") }
        item { MetricCard("Habits", snap.habitCompletions.toString(), snap.streak.toString() + " day max streak") }
        item { MetricCard("Goals", snap.activeGoals.toString(), "active") }
        item { Text("Weekly review: " + weekly.days + " days · " + weekly.averageScore + "/5 average · " + weekly.wins + " win days") }
        item { Text("Workload balancing is computed from duration, priority and overdue work. Use the smart planner for an actionable block suggestion.") }
    }
}

@Composable private fun MetricCard(title: String, value: String, detail: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(title); Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(detail) } }
}

@Composable
private fun ReviewWorkspace(store: CompletionStore) {
    var wins by remember { mutableStateOf("") }
    var blockers by remember { mutableStateOf("") }
    var gratitude by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("") }
    var score by remember { mutableIntStateOf(3) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        val weekly = DailyReviewEngine.weeklySummary(store.reviews())
        Text("Daily and weekly review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("This week: " + weekly.days + " days logged · average " + weekly.averageScore + "/5")
        OutlinedTextField(wins, { wins = it }, label = { Text("Wins") }, Modifier.fillMaxWidth())
        OutlinedTextField(blockers, { blockers = it }, label = { Text("Blockers") }, Modifier.fillMaxWidth())
        OutlinedTextField(gratitude, { gratitude = it }, label = { Text("Gratitude") }, Modifier.fillMaxWidth())
        OutlinedTextField(priority, { priority = it }, label = { Text("Next priority") }, Modifier.fillMaxWidth())
        Row { Text("Score"); (1..5).forEach { TextButton(onClick = { score = it }) { Text(it.toString()) } } }
        Button(onClick = { store.saveReview(ReviewRecord(LocalDate.now().toString(), wins, blockers, gratitude, score, priority)) }) { Text("Save review") }
        Text("History", fontWeight = FontWeight.Bold)
        LazyColumn { items(store.reviews().take(14)) { Text(it.date + " · " + it.score + "/5 · " + it.nextPriority) } }
    }
}

@Composable
private fun ToolsWorkspace(context: Context, tasks: List<TaskModel>, store: CompletionStore) {
    var dependencies by remember { mutableStateOf(store.dependencies()) }
    var shield by remember { mutableStateOf(store.focusShield()) }
    var packageName by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf<List<IcsEventSummary>>(emptyList()) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(IcsCodec.export(tasks).toByteArray()) } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { imported = IcsCodec.importSummaries(it.readText()) } }
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Integrations, focus and data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Card { Column(Modifier.padding(12.dp)) {
            Text("Calendar interoperability", fontWeight = FontWeight.Bold)
            Row { Button(onClick = { exportLauncher.launch("chronora-" + LocalDate.now() + ".ics") }) { Text("Export ICS") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/calendar", "text/plain")) }) { Text("Import ICS") } }
            if (imported.isNotEmpty()) Text(imported.size.toString() + " calendar events parsed locally.")
            Row { OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:?subject=Chronora%20plan"))) } }) { Text("Email") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Chronora plan") }, "Share")) }) { Text("Share") } }
        } } }
        item { Card { Column(Modifier.padding(12.dp)) {
            Text("Dependencies", fontWeight = FontWeight.Bold)
            if (tasks.size >= 2) Button(onClick = { store.addDependency(tasks[1].id, tasks[0].id); dependencies = store.dependencies() }) { Text("Make task 2 depend on task 1") }
            dependencies.forEach { dep -> Text(dep.taskId.toString() + " → " + dep.dependsOnTaskId.toString()) }
        } } }
        item { Card { Column(Modifier.padding(12.dp)) {
            Text("Templates", fontWeight = FontWeight.Bold)
            store.templates().forEach { Text(it.name + " · " + it.blocks.sumOf { b -> b.minutes } + "m") }
            Text("Templates are persistent offline schedule blueprints.")
        } } }
        item { Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Focus shield / distraction awareness", fontWeight = FontWeight.Bold)
            Text("Usage Access is used for transparent usage reporting. Android does not permit an ordinary app to silently make itself an undeletable system blocker.")
            Row { Button(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }) { Text("Usage access") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { shield = FocusShieldConfig(true, System.currentTimeMillis() + 25 * 60_000L, false, store.blockedPackages()); store.saveFocusShield(shield) }) { Text("Start 25m") } }
            OutlinedTextField(packageName, { packageName = it }, label = { Text("Flag distraction package") }, Modifier.fillMaxWidth())
            Button(onClick = { if (packageName.isNotBlank()) { val next = store.blockedPackages() + packageName.trim(); store.setBlockedPackages(next); shield = shield.copy(blockedPackages = next); store.saveFocusShield(shield); packageName = "" } }) { Text("Add") }
            Text("Flagged: " + shield.blockedPackages.joinToString().ifBlank { "none" })
        } } }
        item { Card { Column(Modifier.padding(12.dp)) {
            Text("App lock", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Require biometric/device authentication on launch", Modifier.weight(1f)); Switch(store.appLockEnabled(), { store.setAppLockEnabled(it) }) }
        } } }
        item { Card { Column(Modifier.padding(12.dp)) {
            Text("Cloud / cross-device path", fontWeight = FontWeight.Bold)
            Text("Chronora remains offline-first. JSON backup plus ICS export provides portable sync artifacts; no account or server is silently introduced.")
        } } }
        item { Card { Column(Modifier.padding(12.dp)) { Text("Usage snapshot", fontWeight = FontWeight.Bold); Text(usageSummary(context)) } } }
    }
}

private fun usageSummary(context: Context): String {
    val ops = context.getSystemService(AppOpsManager::class.java)
    val mode = runCatching { ops?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) }.getOrNull()
    if (mode != AppOpsManager.MODE_ALLOWED) return "Grant Usage Access to inspect foreground usage."
    val manager = context.getSystemService(UsageStatsManager::class.java) ?: return "Usage stats unavailable."
    val end = System.currentTimeMillis()
    val list = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 86_400_000L, end).sortedByDescending { it.totalTimeInForeground }.take(5)
    return if (list.isEmpty()) "No usage data." else list.joinToString(" · ") { it.packageName.substringAfterLast('.') + " " + it.totalTimeInForeground / 60_000L + "m" }
}

private fun clock(minute: Int) = "%02d:%02d".format((minute / 60).coerceIn(0, 23), (minute % 60).coerceIn(0, 59))
