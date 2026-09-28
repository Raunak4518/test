package com.raunak.daytimeline

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.features.OfflineProductivityStore
import com.raunak.daytimeline.features.OfflineRoutine
import com.raunak.daytimeline.features.OfflineRoutineStep
import com.raunak.daytimeline.features.streak
import java.time.LocalDate

private val SuiteBg = Color(0xFFF5F1E9)
private val Ink = Color(0xFF24302B)
private val Sage = Color(0xFF55786A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSuite(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { OfflineProductivityStore(context.applicationContext) }
    val habits by store.habits.collectAsState()
    val goals by store.goals.collectAsState()
    val routines by store.routines.collectAsState()
    val projects by store.projects.collectAsState()
    val entries by store.timeEntries.collectAsState()
    val achievements by store.achievements.collectAsState()
    val journal by store.journal.collectAsState()
    val challenges by store.challenges.collectAsState()
    var tab by remember { mutableStateOf("Overview") }
    var dialog by remember { mutableStateOf<String?>(null) }
    var exportText by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.exportJson()) }
            Toast.makeText(context, "Backup exported", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Could not read file")
            store.importJson(json).getOrThrow()
            Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Import rejected: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        containerColor = SuiteBg,
        topBar = { SmallTopAppBar(
            title = { Column { Text("Offline Productivity", fontWeight = FontWeight.Bold); Text("Private · local · no account", style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = {
                IconButton({ exportLauncher.launch("daytimeline-backup.json") }) { Icon(Icons.Default.Download, "Export backup") }
                IconButton({ importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Icon(Icons.Default.Upload, "Import backup") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SuiteBg)
        ) },
        floatingActionButton = {
            FloatingActionButton({ dialog = tab }, containerColor = Sage, contentColor = Color.White) { Icon(Icons.Default.Add, "Add") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Overview", "Habits", "Goals", "Routines", "Time", "Journal").forEach { label ->
                    FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) })
                }
            }
            when (tab) {
                "Overview" -> OverviewTab(habits, goals, routines, projects, entries, achievements, challenges)
                "Habits" -> HabitsTab(habits, store)
                "Goals" -> GoalsTab(goals, store)
                "Routines" -> RoutinesTab(routines, store)
                "Time" -> TimeTab(entries, projects, store)
                else -> JournalTab(journal, store)
            }
        }
    }

    if (dialog != null) {
        when (dialog) {
            "Habits" -> HabitDialog({ name, target -> store.addHabit(name, target); dialog = null }, { dialog = null })
            "Goals" -> GoalDialog({ title, target -> store.addGoal(title, target); dialog = null }, { dialog = null })
            "Routines" -> RoutineDialog({ name, steps -> store.addRoutine(name, steps); dialog = null }, { dialog = null })
            "Time" -> TimeDialog({ label -> store.startTimeEntry(label); dialog = null }, { dialog = null })
            "Journal" -> JournalDialog({ mood, energy, wins, blockers, gratitude, note -> store.addJournal(LocalDate.now(), mood, energy, wins, blockers, gratitude, note); dialog = null }, { dialog = null })
            else -> dialog = null
        }
    }
}

@Composable
private fun OverviewTab(
    habits: List<com.raunak.daytimeline.features.OfflineHabit>,
    goals: List<com.raunak.daytimeline.features.OfflineGoal>,
    routines: List<OfflineRoutine>,
    projects: List<com.raunak.daytimeline.features.OfflineProject>,
    entries: List<com.raunak.daytimeline.features.OfflineTimeEntry>,
    achievements: List<com.raunak.daytimeline.features.OfflineAchievement>,
    challenges: List<com.raunak.daytimeline.features.OfflineChallenge>
) {
    val today = LocalDate.now()
    val minutes = entries.sumOf { e ->
        val end = e.endEpochMillis ?: System.currentTimeMillis()
        ((end - e.startEpochMillis).coerceAtLeast(0) / 60_000)
    }
    val completedHabits = habits.count { today.toString() in it.completedDates }
    val goalDone = goals.count { it.completed }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Ink)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Your private productivity cockpit", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp)); Text("Nothing here needs a server. Your data stays on this device.", color = Color(0xFFD9E3DE))
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("Habits", "$completedHabits/${habits.size}")
                        Metric("Goals", "$goalDone/${goals.size}")
                        Metric("Tracked", "${minutes / 60}h ${minutes % 60}m")
                    }
                }
            }
        }
        item { SectionCard("Routines", "${routines.size} reusable routines with real step durations") }
        item { SectionCard("Projects", "${projects.size} local projects for grouping work") }
        item { SectionCard("Challenges", "${challenges.count { it.progress >= it.target }} / ${challenges.size} completed") }
        item { SectionCard("Achievements", "${achievements.size} unlocked locally") }
    }
}

@Composable private fun Metric(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Color.White, fontWeight = FontWeight.Bold); Text(label, color = Color(0xFFB9C8C1), style = MaterialTheme.typography.labelSmall) } }
@Composable private fun SectionCard(title: String, text: String) { Card { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(text, color = Color.Gray) } } }

@Composable
private fun HabitsTab(habits: List<com.raunak.daytimeline.features.OfflineHabit>, store: OfflineProductivityStore) {
    val today = LocalDate.now()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)) {
        items(habits, key = { it.id }) { h ->
            Card {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(h.name, fontWeight = FontWeight.SemiBold); Text("${h.weekCompletion(today)}/${h.targetPerWeek} this week · ${h.streak(today)} day streak", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                    IconButton({ store.toggleHabit(h.id, today) }) { Icon(Icons.Default.CheckCircle, "Toggle") }
                    IconButton({ store.deleteHabit(h.id) }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        }
        if (habits.isEmpty()) item { Empty("No habits yet", "Add habits with the + button. Completion and streaks persist locally.") }
    }
}

@Composable
private fun GoalsTab(goals: List<com.raunak.daytimeline.features.OfflineGoal>, store: OfflineProductivityStore) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)) {
        items(goals, key = { it.id }) { g ->
            Card { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Flag, null, tint = Sage); Spacer(Modifier.width(10.dp)); Text(g.title, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text("${g.progress}/${g.target}") }; Spacer(Modifier.height(10.dp)); LinearProgressIndicator({ g.progress.toFloat() / g.target.toFloat() }, Modifier.fillMaxWidth()); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton({ store.setGoalProgress(g.id, g.progress + 1) }) { Text("+1") }; TextButton({ store.setGoalProgress(g.id, g.progress - 1) }) { Text("-1") } } } }
        }
        if (goals.isEmpty()) item { Empty("No goals", "Create measurable goals and increment them as work gets done.") }
    }
}

@Composable
private fun RoutinesTab(routines: List<OfflineRoutine>, store: OfflineProductivityStore) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)) {
        items(routines, key = { it.id }) { r ->
            Card { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Refresh, null, tint = Sage); Spacer(Modifier.width(10.dp)); Text(r.name, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); TextButton({ store.setRoutineCompleted(r.id) }) { Text(if (r.lastCompletedDate == LocalDate.now().toString()) "Done" else "Complete") } }; Spacer(Modifier.height(8.dp)); r.steps.forEachIndexed { i, s -> Text("${i + 1}. ${s.title} · ${s.minutes}m", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; TextButton({ store.deleteRoutine(r.id) }) { Text("Delete") } } }
        }
    }
}

@Composable
private fun TimeTab(entries: List<com.raunak.daytimeline.features.OfflineTimeEntry>, projects: List<com.raunak.daytimeline.features.OfflineProject>, store: OfflineProductivityStore) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)) {
        items(entries.sortedByDescending { it.startEpochMillis }, key = { it.id }) { e ->
            val running = e.endEpochMillis == null
            Card { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Timer, null, tint = Sage); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(e.label, fontWeight = FontWeight.SemiBold); Text(if (running) "Running now" else duration(e.startEpochMillis, e.endEpochMillis!!), style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; if (running) TextButton({ store.stopTimeEntry(e.id) }) { Text("Stop") } } }
        }
        if (entries.isEmpty()) item { Empty("No tracked time", "Start a timer from the + button or add manual entries later.") }
    }
}

private fun duration(start: Long, end: Long): String { val m = ((end - start).coerceAtLeast(0) / 60_000); return "${m / 60}h ${m % 60}m" }

@Composable
private fun JournalTab(journal: List<com.raunak.daytimeline.features.OfflineJournalEntry>, store: OfflineProductivityStore) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)) {
        items(journal, key = { it.date }) { j -> Card { Column(Modifier.padding(16.dp)) { Text(j.date, fontWeight = FontWeight.Bold); Text("Mood ${j.mood}/5 · Energy ${j.energy}/5", color = Sage); if (j.wins.isNotBlank()) Text("Wins: ${j.wins}"); if (j.blockers.isNotBlank()) Text("Blockers: ${j.blockers}"); if (j.gratitude.isNotBlank()) Text("Gratitude: ${j.gratitude}"); if (j.note.isNotBlank()) Text(j.note, color = Color.Gray) } } }
        if (journal.isEmpty()) item { Empty("No journal entries", "Use the + button for a structured daily reflection.") }
    }
}

@Composable private fun Empty(title: String, text: String) { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(text, color = Color.Gray) } } }

@Composable private fun HabitDialog(onSave: (String, Int) -> Unit, onCancel: () -> Unit) { var name by remember { mutableStateOf("") }; var target by remember { mutableStateOf("7") }; SimpleDialog("New habit", onCancel) { OutlinedTextField(name, { name = it }, label = { Text("Habit") }); OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Days per week") }); Button({ onSave(name, target.toIntOrNull() ?: 7) }, enabled = name.isNotBlank()) { Text("Create") } } }
@Composable private fun GoalDialog(onSave: (String, Int) -> Unit, onCancel: () -> Unit) { var title by remember { mutableStateOf("") }; var target by remember { mutableStateOf("100") }; SimpleDialog("New goal", onCancel) { OutlinedTextField(title, { title = it }, label = { Text("Goal") }); OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Target") }); Button({ onSave(title, target.toIntOrNull() ?: 100) }, enabled = title.isNotBlank()) { Text("Create") } } }
@Composable private fun RoutineDialog(onSave: (String, List<OfflineRoutineStep>) -> Unit, onCancel: () -> Unit) { var name by remember { mutableStateOf("") }; var steps by remember { mutableStateOf(listOf(OfflineRoutineStep("Step 1", 10))) }; SimpleDialog("New routine", onCancel) { OutlinedTextField(name, { name = it }, label = { Text("Routine name") }); steps.forEachIndexed { i, s -> OutlinedTextField(s.title, { v -> steps = steps.toMutableList().also { it[i] = s.copy(title = v) } }, label = { Text("Step ${i + 1}") }) }; Row { TextButton({ steps = steps + OfflineRoutineStep("New step", 10) }) { Text("Add step") }; Spacer(Modifier.weight(1f)); Button({ onSave(name, steps) }, enabled = name.isNotBlank()) { Text("Create") } } } }
@Composable private fun TimeDialog(onSave: (String) -> Unit, onCancel: () -> Unit) { var label by remember { mutableStateOf("") }; SimpleDialog("Start timer", onCancel) { OutlinedTextField(label, { label = it }, label = { Text("What are you doing?") }); Button({ onSave(label) }, enabled = label.isNotBlank()) { Text("Start") } } }
@Composable private fun JournalDialog(onSave: (Int, Int, String, String, String, String) -> Unit, onCancel: () -> Unit) { var mood by remember { mutableStateOf(3) }; var energy by remember { mutableStateOf(3) }; var wins by remember { mutableStateOf("") }; var blockers by remember { mutableStateOf("") }; var gratitude by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; SimpleDialog("Daily review", onCancel) { Text("Mood: $mood / 5"); Row { (1..5).forEach { TextButton({ mood = it }) { Text(it.toString()) } } }; Text("Energy: $energy / 5"); Row { (1..5).forEach { TextButton({ energy = it }) { Text(it.toString()) } } }; OutlinedTextField(wins, { wins = it }, label = { Text("Wins") }); OutlinedTextField(blockers, { blockers = it }, label = { Text("Blockers") }); OutlinedTextField(gratitude, { gratitude = it }, label = { Text("Gratitude") }); OutlinedTextField(note, { note = it }, label = { Text("Notes") }); Button({ onSave(mood, energy, wins, blockers, gratitude, note) }) { Text("Save review") } } }

@Composable private fun SimpleDialog(title: String, onCancel: () -> Unit, content: @Composable () -> Unit) { AlertDialog(onDismissRequest = onCancel, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }, confirmButton = {}, dismissButton = { TextButton(onCancel) { Text("Cancel") } }) }
