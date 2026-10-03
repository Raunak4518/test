package com.raunak.daytimeline.productivity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raunak.daytimeline.features.*
import com.raunak.daytimeline.ui.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

val MoodFaces = listOf("😣", "🙁", "😐", "🙂", "😄")
val MoodColors = listOf(0xFFE15759, 0xFFF28E2B, 0xFFB0A58C, 0xFF59A14F, 0xFF2BA3A3)

private fun moodLabel(s: OfflineSettings, m: Int) = s.moodLabels.getOrNull(m - 1) ?: MoodFaces[m - 1]

/** Daylio-style journal: one-tap mood, activities, a daily prompt, mood calendar and what lifts your mood. */
@Composable
fun JournalSection(entries: List<OfflineJournalEntry>, settings: OfflineSettings, store: OfflineProductivityStore) {
    val today = LocalDate.now()
    var editing by remember { mutableStateOf<OfflineJournalEntry?>(null) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var customise by remember { mutableStateOf(false) }
    val todayEntry = entries.firstOrNull { it.date == today.toString() }
    val stats = remember(entries) { JournalEngine.stats(entries, today) }
    fun blank(date: LocalDate, mood: Int = 3) = OfflineJournalEntry(date.toString(), mood, 3, "", "", "", "", prompt = JournalEngine.prompt(settings.journalPrompts, date) ?: "")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        HeroCard {
            Text(if (todayEntry == null) "How was your day?" else "Today: ${moodLabel(settings, todayEntry.mood)} ${MoodFaces[todayEntry.mood - 1]}", color = Chronora.colors.onHero, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (1..5).forEach { m ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { editing = (todayEntry ?: blank(today)).copy(mood = m) }.padding(6.dp)) {
                        Text(MoodFaces[m - 1], fontSize = 30.sp, modifier = Modifier.then(if (todayEntry?.mood == m) Modifier.background(Color.White.copy(alpha = .18f), CircleShape).padding(4.dp) else Modifier.padding(4.dp)))
                        Text(moodLabel(settings, m), color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("${stats.streak}-day streak · ${stats.entries} entries in 30 days" + (stats.average?.let { " · average " + "%.1f".format(it) } ?: ""), color = Chronora.colors.heroMuted, style = MaterialTheme.typography.bodySmall)
        }
        JournalEngine.prompt(settings.journalPrompts, today)?.let { p ->
            SectionCard("Today's prompt", p) { TextButton(onClick = { editing = todayEntry ?: blank(today) }) { Text(if (todayEntry?.answer.isNullOrBlank()) "Answer" else "Edit answer") } }
        }
        SectionCard(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), "Mood calendar — tap a day to write or edit", action = {
            Row { IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "Previous") }; IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "Next") } }
        }) {
            val byDate = entries.associateBy { it.date }
            val lead = month.atDay(1).dayOfWeek.value - 1
            val cells = ((lead + month.lengthOfMonth() + 6) / 7) * 7
            (0 until cells / 7).forEach { r ->
                Row(Modifier.fillMaxWidth()) {
                    (0 until 7).forEach { c ->
                        val i = r * 7 + c - lead
                        if (i < 0 || i >= month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(38.dp)) else {
                            val d = month.atDay(i + 1)
                            val e = byDate[d.toString()]
                            Box(Modifier.weight(1f).height(38.dp).padding(2.dp).clip(CircleShape)
                                .background(e?.let { Color(MoodColors[it.mood - 1]) } ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (d.isAfter(today)) .25f else .6f))
                                .then(if (d == today) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                .clickable(enabled = !d.isAfter(today)) { editing = e ?: blank(d) }, contentAlignment = Alignment.Center) {
                                Text("${d.dayOfMonth}", fontSize = 12.sp, color = if (e != null) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
        if (stats.entries >= 3) SectionCard("Insights", "Last 30 days") {
            Text("Mood by weekday", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                stats.byWeekday.forEachIndexed { i, v ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        v?.let { Text("%.1f".format(it), fontSize = 10.sp, color = Chronora.muted) }
                        Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().height((((v ?: 0.0) / 5.0) * 60).coerceAtLeast(3.0).dp).clip(RoundedCornerShape(6.dp))
                            .background(v?.let { Color(MoodColors[(it.toInt() - 1).coerceIn(0, 4)]) } ?: MaterialTheme.colorScheme.surfaceVariant))
                        Text(java.time.DayOfWeek.of(i + 1).name.take(1), style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                stats.moodCounts.forEachIndexed { i, n -> Text("${MoodFaces[i]} $n", style = MaterialTheme.typography.bodySmall) }
            }
            if (stats.activityMood.isNotEmpty()) {
                Text("What goes with better days", style = MaterialTheme.typography.labelLarge)
                stats.activityMood.take(4).forEach { (a, avg, n) -> Text("${MoodFaces[(avg.toInt() - 1).coerceIn(0, 4)]}  $a · average ${"%.1f".format(avg)} ($n days)", style = MaterialTheme.typography.bodySmall) }
                stats.activityMood.takeLast(2).filter { it.second < (stats.average ?: 3.0) }.forEach { (a, avg, n) ->
                    Text("${MoodFaces[(avg.toInt() - 1).coerceIn(0, 4)]}  $a · average ${"%.1f".format(avg)} ($n days)", style = MaterialTheme.typography.bodySmall, color = Chronora.colors.bad)
                }
            }
        }
        SectionHeader("Entries") { TextButton(onClick = { customise = !customise }) { Text(if (customise) "Done" else "Customise") } }
        if (customise) SectionCard("Customise journal") {
            ListEditor("Activities", settings.journalActivities) { v -> store.updateSettings { it.copy(journalActivities = v) } }
            ListEditor("Daily prompts", settings.journalPrompts) { v -> store.updateSettings { it.copy(journalPrompts = v) } }
            ListEditor("Mood names (worst → best, 5)", settings.moodLabels) { v -> store.updateSettings { it.copy(moodLabels = (v + OfflineSettings().moodLabels).take(5)) } }
        }
        if (entries.isEmpty()) EmptyState("No entries yet", "Tap a face above — it takes five seconds.")
        entries.sortedByDescending { it.date }.take(30).forEach { e ->
            Card(Modifier.fillMaxWidth().clickable { editing = e }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Text(MoodFaces[e.mood - 1], fontSize = 28.sp)
                    Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(relativeDay(LocalDate.parse(e.date), today) + " · " + moodLabel(settings, e.mood), fontWeight = FontWeight.SemiBold)
                        if (JournalEngine.activities(e).isNotEmpty()) Text(JournalEngine.activities(e).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        listOf(e.answer, e.wins, e.note).firstOrNull { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
                    }
                }
            }
        }
    }
    editing?.let { e -> JournalEditor(e, settings, { store.saveJournal(it); editing = null }, onDelete = if (entries.any { it.date == e.date }) ({ store.deleteJournal(e.date); editing = null }) else null) { editing = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalEditor(initial: OfflineJournalEntry, settings: OfflineSettings, onSave: (OfflineJournalEntry) -> Unit, onDelete: (() -> Unit)?, close: () -> Unit) {
    val confirm = com.raunak.daytimeline.ui.rememberConfirm()
    var mood by remember { mutableIntStateOf(initial.mood.coerceIn(1, 5)) }
    var energy by remember { mutableIntStateOf(initial.energy.coerceIn(1, 5)) }
    var acts by remember { mutableStateOf(JournalEngine.activities(initial).toSet()) }
    var answer by remember { mutableStateOf(initial.answer) }
    var wins by remember { mutableStateOf(initial.wins) }
    var gratitude by remember { mutableStateOf(initial.gratitude) }
    var blockers by remember { mutableStateOf(initial.blockers) }
    var note by remember { mutableStateOf(initial.note) }
    AlertDialog(onDismissRequest = close, title = { Text(relativeDay(LocalDate.parse(initial.date))) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (1..5).forEach { m -> Text(MoodFaces[m - 1], fontSize = if (m == mood) 34.sp else 26.sp, modifier = Modifier.clip(CircleShape).background(if (m == mood) Color(MoodColors[m - 1]).copy(alpha = .3f) else Color.Transparent).clickable { mood = m }.padding(6.dp)) }
            }
            Text(moodLabel(settings, mood), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            Stepper("Energy", "$energy / 5", { energy = (energy - 1).coerceAtLeast(1) }, { energy = (energy + 1).coerceAtMost(5) })
            Text("What did you do?", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (settings.journalActivities + acts.filter { it !in settings.journalActivities }).forEach { a -> FilterChip(a in acts, { acts = if (a in acts) acts - a else acts + a }, label = { Text(a) }) }
            }
            if (initial.prompt.isNotBlank()) OutlinedTextField(answer, { answer = it }, label = { Text(initial.prompt) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(wins, { wins = it }, label = { Text("Wins") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(gratitude, { gratitude = it }, label = { Text("Grateful for") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(blockers, { blockers = it }, label = { Text("What got in the way") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Anything else") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            if (onDelete != null) TextButton(onClick = { confirm.ask("this entry", onDelete) }) { Text("Delete entry", color = Chronora.colors.bad) }
        }
    }, confirmButton = { Button(onClick = { onSave(initial.copy(mood = mood, energy = energy, activities = acts.toList(), answer = answer, wins = wins, gratitude = gratitude, blockers = blockers, note = note)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
