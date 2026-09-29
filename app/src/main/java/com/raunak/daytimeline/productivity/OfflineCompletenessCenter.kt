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
    var editing by remember { mutableStateOf<OfflineHabit?>(null) }
    var adding by remember { mutableStateOf(false) }
    com.raunak.daytimeline.ui.ScreenList {
        item { HabitTodayHero(habits) }
        item { com.raunak.daytimeline.ui.SectionHeader("Habit system") { Button({ adding = true }) { Text("Add habit") } } }
        item { HabitList(habits, store, { editing = it }) }
    }
    if (adding) HabitEditorDialog(null, { store.saveHabit(it); adding = false }) { adding = false }
    editing?.let { h -> HabitEditorDialog(h, { store.saveHabit(it); editing = null }) { editing = null } }
}

@Composable
private fun OfflineGoalsPanel(store: OfflineProductivityStore) {
    val goals by store.goals.collectAsStateWithLifecycle()
    com.raunak.daytimeline.ui.ScreenList { item { GoalsSection(goals, store) } }
}

@Composable
private fun OfflineRoutinesPanel(store: OfflineProductivityStore) {
    val routines by store.routines.collectAsStateWithLifecycle()
    com.raunak.daytimeline.ui.ScreenList { item { RoutinesSection(routines, store) } }
}

@Composable
private fun OfflineNotesPanel(store: OfflineProductivityStore) {
    val notes by store.notes.collectAsStateWithLifecycle()
    com.raunak.daytimeline.ui.ScreenList { item { NotesSection(notes, store) } }
}

@Composable
private fun OfflineJournalPanel(store: OfflineProductivityStore) {
    val entries by store.journal.collectAsStateWithLifecycle()
    val settings by store.settings.collectAsStateWithLifecycle()
    com.raunak.daytimeline.ui.ScreenList { item { JournalSection(entries, settings, store) } }
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
    val projects by store.projects.collectAsStateWithLifecycle()
    com.raunak.daytimeline.ui.ScreenList { item { TimeSection(entries, projects, store) } }
}

