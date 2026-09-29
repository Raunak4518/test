package com.raunak.daytimeline.productivity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.MadeByRaunak
import com.raunak.daytimeline.features.*
import java.time.LocalDate

@Composable
fun OfflineCompletenessCenter(store: OfflineProductivityStore) {
    var tab by remember { mutableIntStateOf(0) }
    val labels = listOf("Habits", "Goals", "Routines", "Notes", "Journal", "Challenges", "Time")
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab) {
            labels.forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) }) }
        }
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> OfflineHabitsPanel(store)
                1 -> OfflineGoalsPanel(store)
                2 -> OfflineRoutinesPanel(store)
                3 -> OfflineNotesPanel(store)
                4 -> OfflineJournalPanel(store)
                5 -> OfflineChallengesPanel(store)
                else -> OfflineTimePanel(store)
            }
        }
    }
}

@Composable
private fun OfflineHabitsPanel(store: OfflineProductivityStore) {
    val habits by store.habits.collectAsStateWithLifecycle()
    var add by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Habit system", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button({ add = true }) { Text("Add habit") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(habits, key = { it.id }) { habit ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(habit.name, fontWeight = FontWeight.Bold)
                                Text("Streak " + habit.streak() + " · " + habit.weekCompletion() + "/7 this week · target " + habit.targetPerWeek + "/week")
                            }
                            IconButton({ store.toggleHabit(habit.id) }) { Icon(Icons.Default.CheckCircle, "Toggle today") }
                            IconButton({ store.deleteHabit(habit.id) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                        Text("7-day history", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (6 downTo 0).forEach { offset ->
                                val date = LocalDate.now().minusDays(offset.toLong())
                                FilterChip(selected = habit.completedDates.contains(date.toString()), onClick = { store.toggleHabit(habit.id, date) }, label = { Text(date.dayOfWeek.name.take(1)) })
                            }
                        }
                    }
                }
            }
        }
    }
    if (add) {
        var name by remember { mutableStateOf("") }
        var time by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("New habit") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                OutlinedTextField(time, { time = it }, label = { Text("Reminder HH:mm (optional)") })
            }},
            confirmButton = { Button({ if (name.isNotBlank()) { store.addHabit(name, 7, time); add = false } }) { Text("Create") } },
            dismissButton = { TextButton({ add = false }) { Text("Cancel") } })
    }
}

@Composable
private fun OfflineGoalsPanel(store: OfflineProductivityStore) {
    val goals by store.goals.collectAsStateWithLifecycle()
    var add by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Goals & milestones", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button({ add = true }) { Text("Add goal") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(goals, key = { it.id }) { goal ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(goal.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(goal.progress.toString() + "/" + goal.target)
                            IconButton({ store.deleteGoal(goal.id) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                        LinearProgressIndicator({ if (goal.target == 0) 0f else goal.progress.toFloat() / goal.target }, Modifier.fillMaxWidth())
                        if (goal.milestones.isEmpty()) Text("No milestones")
                        goal.milestones.forEachIndexed { index, milestone ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(index in goal.milestoneDone, { store.toggleGoalMilestone(goal.id, index) })
                                Text(milestone)
                            }
                        }
                    }
                }
            }
        }
    }
    if (add) {
        var title by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("10") }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("New goal") },
            text = { Column { OutlinedTextField(title, { title = it }, label = { Text("Goal") }); OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Target") }) } },
            confirmButton = { Button({ if (title.isNotBlank()) { store.addGoal(title, target.toIntOrNull() ?: 10); add = false } }) { Text("Create") } },
            dismissButton = { TextButton({ add = false }) { Text("Cancel") } })
    }
}

@Composable
private fun OfflineRoutinesPanel(store: OfflineProductivityStore) {
    val routines by store.routines.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Routine execution", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(routines, key = { it.id }) { routine ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(routine.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Button({ store.setRoutineCompleted(routine.id) }) { Text("Complete") }
                        }
                        Text(routine.steps.sumOf { it.minutes }.toString() + " min · completed " + routine.completionDates.size + " times")
                        routine.steps.forEachIndexed { index, step ->
                            val key = LocalDate.now().toString() + ":" + index
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(key in routine.completedSteps, { store.toggleRoutineStep(routine.id, index) })
                                Text(step.title + " · " + step.minutes + " min")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MadeByRaunak()
    }
}

@Composable
private fun OfflineNotesPanel(store: OfflineProductivityStore) {
    val notes by store.notes.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(false) }
    val filtered = notes.filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) || it.tags.any { t -> t.contains(query, true) } }
        .sortedWith(compareByDescending<OfflineNote> { it.pinned }.thenByDescending { it.updatedAt })
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notes & knowledge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button({ add = true }) { Text("New") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), label = { Text("Search notes") })
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { note ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(note.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton({ store.toggleNotePinned(note.id) }) { Icon(Icons.Default.PushPin, "Pin") }
                            IconButton({ store.deleteNote(note.id) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                        Text(note.body, maxLines = 4)
                        Text(note.folder + " · " + note.tags.joinToString(" "), style = MaterialTheme.typography.labelSmall)
                        val backlinks = store.noteBacklinks(note)
                        if (backlinks.isNotEmpty()) Text("Backlinks: " + backlinks.joinToString { it.title }, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    if (add) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var folder by remember { mutableStateOf("General") }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("New note") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") })
                OutlinedTextField(body, { body = it }, label = { Text("Body") })
                OutlinedTextField(folder, { folder = it }, label = { Text("Folder") })
            }},
            confirmButton = { Button({ store.addNote(title, body, emptySet(), folder); add = false }) { Text("Save") } },
            dismissButton = { TextButton({ add = false }) { Text("Cancel") } })
    }
}

@Composable
private fun OfflineJournalPanel(store: OfflineProductivityStore) {
    val entries by store.journal.collectAsStateWithLifecycle()
    var add by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Journal & reflection", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button({ add = true }) { Text("Today's entry") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.date }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(entry.date, fontWeight = FontWeight.Bold)
                        Text("Mood " + entry.mood + "/5 · Energy " + entry.energy + "/5")
                        if (entry.wins.isNotBlank()) Text("Wins: " + entry.wins)
                        if (entry.blockers.isNotBlank()) Text("Blockers: " + entry.blockers)
                        if (entry.gratitude.isNotBlank()) Text("Gratitude: " + entry.gratitude)
                        if (entry.note.isNotBlank()) Text(entry.note)
                    }
                }
            }
        }
    }
    if (add) {
        var note by remember { mutableStateOf("") }
        var mood by remember { mutableIntStateOf(3) }
        var energy by remember { mutableIntStateOf(3) }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("Daily reflection") },
            text = { Column { Text("Mood " + mood + "/5"); Slider({ mood = it.toInt().coerceIn(1,5) }, 1f, 5f, steps = 3); Text("Energy " + energy + "/5"); Slider({ energy = it.toInt().coerceIn(1,5) }, 1f, 5f, steps = 3); OutlinedTextField(note, { note = it }, label = { Text("Reflection") }) } },
            confirmButton = { Button({ store.addJournal(LocalDate.now(), mood, energy, note, "", "", note); add = false }) { Text("Save") } },
            dismissButton = { TextButton({ add = false }) { Text("Cancel") } })
    }
}

@Composable
private fun OfflineChallengesPanel(store: OfflineProductivityStore) {
    val challenges by store.challenges.collectAsStateWithLifecycle()
    val habits by store.habits.collectAsStateWithLifecycle()
    val routines by store.routines.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val habitWins = habits.count { it.completedDates.contains(today.toString()) }
    val routineWins = routines.count { it.lastCompletedDate == today.toString() }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Challenges & achievements", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Progress is derived locally from your activity.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(challenges, key = { it.id }) { c ->
                val auto = when {
                    c.title.contains("Focus", true) -> habitWins
                    c.title.contains("Three", true) -> routineWins
                    else -> c.progress
                }.coerceAtMost(c.target)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(c.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(auto.toString() + "/" + c.target) }
                        Text(c.description)
                        LinearProgressIndicator({ auto.toFloat() / c.target.coerceAtLeast(1) }, Modifier.fillMaxWidth())
                        if (auto < c.target) Button({ store.completeChallenge(c.id) }) { Text("Manual +1") } else Text("Completed", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineTimePanel(store: OfflineProductivityStore) {
    val entries by store.timeEntries.collectAsStateWithLifecycle()
    val running = entries.filter { it.endEpochMillis == null }
    val total = entries.sumOf { ((it.endEpochMillis ?: System.currentTimeMillis()) - it.startEpochMillis).coerceAtLeast(0L) } / 60000L
    var label by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Time tracking", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Tracked total: " + total + " min")
        OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), label = { Text("What are you working on?") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ if (label.isNotBlank()) { store.startTimeEntry(label); label = "" } }) { Text("Start") }
            running.firstOrNull()?.let { Button({ store.stopTimeEntry(it.id) }) { Text("Stop " + it.label) } }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
            items(entries.sortedByDescending { it.startEpochMillis }, key = { it.id }) { entry ->
                ListItem(headlineContent = { Text(entry.label) }, supportingContent = { Text((((entry.endEpochMillis ?: System.currentTimeMillis()) - entry.startEpochMillis) / 60000).toString() + " min") })
            }
        }
    }
}
