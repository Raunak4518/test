package com.raunak.daytimeline.pro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.PlannerViewModel
import com.raunak.daytimeline.data.TaskEntity
import com.raunak.daytimeline.domain.RecurrenceEngine
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.features.OfflineProductivityStore
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private fun TaskModel.occursOn(date: LocalDate) = RecurrenceEngine.occursOn(
    TaskEntity(title = title, dateEpochDay = this.date.toEpochDay(), startMinute = startMinute, endMinute = endMinute, recurrenceType = recurrenceType, recurrenceDays = recurrenceDays),
    date
)

private fun hhmm(minute: Int) = "%02d:%02d".format((minute / 60).coerceIn(0, 24), minute % 60)

@Composable
internal fun SearchTab(vm: PlannerViewModel, onOpenDate: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val store = remember { OfflineProductivityStore(context.applicationContext) }
    val tasks by vm.allTasks.collectAsStateWithLifecycle()
    val notes by store.notes.collectAsStateWithLifecycle()
    val journal by store.journal.collectAsStateWithLifecycle()
    val habits by store.habits.collectAsStateWithLifecycle()
    val goals by store.goals.collectAsStateWithLifecycle()
    val routines by store.routines.collectAsStateWithLifecycle()
    val campus = remember { com.raunak.daytimeline.campus.CampusStore.get(context) }
    val campusData by campus.data.collectAsStateWithLifecycle()
    val sheets by campus.sheets.collectAsStateWithLifecycle()
    val companies by campus.companies.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val index = remember(tasks, notes, journal, habits, goals, routines, campusData, sheets, companies) {
        buildList {
            sheets.forEach { s -> s.items.forEach { i ->
                add(SearchItem(SearchKind.QUESTION, "q${s.id}-${i.id}", i.title, "${s.name} · ${i.section} ${i.notes}", i.doneDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, setOfNotNull(i.difficulty?.label?.lowercase(), i.section.lowercase()), i.status.done))
            } }
            campusData.deadlines.forEach { d ->
                add(SearchItem(SearchKind.DEADLINE, "d${d.id}", d.title, d.label + " " + (campusData.subjects.firstOrNull { it.id == d.subjectId }?.name ?: "") + " " + d.notes, runCatching { LocalDate.parse(d.date) }.getOrNull(), setOf(d.label.lowercase()), d.done))
            }
            campusData.subjects.forEach { s -> add(SearchItem(SearchKind.SUBJECT, "s${s.id}", s.name, "${s.code} ${s.faculty}")) }
            companies.forEach { c -> add(SearchItem(SearchKind.COMPANY, "c${c.id}", c.name, "${c.role} ${c.stageName} ${c.notes}", c.nextDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() })) }
            tasks.forEach { add(SearchItem(SearchKind.TASK, "t${it.id}", it.title, it.notes, it.date, it.tags.split(',').map(String::trim).filter(String::isNotBlank).toSet(), it.completed)) }
            notes.forEach { add(SearchItem(SearchKind.NOTE, "n${it.id}", it.title, it.body + " " + it.folder, java.time.Instant.ofEpochMilli(it.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate(), it.tags)) }
            journal.forEach { j -> add(SearchItem(SearchKind.JOURNAL, "j${j.date}", "Journal ${j.date}", listOf(j.wins, j.blockers, j.gratitude, j.note).joinToString(" "), runCatching { LocalDate.parse(j.date) }.getOrNull())) }
            habits.forEach { add(SearchItem(SearchKind.HABIT, "h${it.id}", it.name, it.preferredTime)) }
            goals.forEach { add(SearchItem(SearchKind.GOAL, "g${it.id}", it.title, it.milestones.joinToString(" "), it.deadline?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() }, done = it.completed)) }
            routines.forEach { r -> add(SearchItem(SearchKind.ROUTINE, "r${r.id}", r.name, r.steps.joinToString(" ") { it.title })) }
        }
    }
    val results = remember(query, index) { UniversalSearch.search(index, query, LocalDate.now()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(query, { query = it }, label = { Text("Search everything") }, placeholder = { Text("unfinished dsa tasks · notes #exam · everything yesterday") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Filters: task/note/journal/habit/goal/routine/question/deadline/subject/company, done/unfinished, #tag, today, yesterday, this/last/next week, this/last month", style = MaterialTheme.typography.bodySmall)
        }
        if (query.isNotBlank()) item { Text("${results.size} results", style = MaterialTheme.typography.labelMedium) }
        items(results, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth().clickable(enabled = r.kind == SearchKind.TASK && r.date != null) { r.date?.let(onOpenDate) }) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip({}, label = { Text(r.kind.name.lowercase()) })
                        Spacer(Modifier.width(8.dp))
                        Text(r.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (r.done == true) Text("✓")
                    }
                    val meta = listOfNotNull(r.date?.toString(), r.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall)
                    if (r.body.isNotBlank()) Text(r.body.trim(), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun WeekTab(vm: PlannerViewModel, onOpenDate: (LocalDate) -> Unit) {
    val tasks by vm.allTasks.collectAsStateWithLifecycle()
    var agenda by remember { mutableStateOf(false) }
    var weekStart by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
    val days = if (agenda) (0L until 21L).map { LocalDate.now().plusDays(it) } else (0L until 7L).map { weekStart.plusDays(it) }
    val byDay = remember(tasks, days) { days.associateWith { d -> tasks.filter { it.occursOn(d) }.sortedBy { it.startMinute } } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(!agenda, { agenda = false }, label = { Text("Week") })
                FilterChip(agenda, { agenda = true }, label = { Text("Agenda (3 weeks)") })
                if (!agenda) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { weekStart = weekStart.minusWeeks(1) }) { Text("‹") }
                    TextButton(onClick = { weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }) { Text("This week") }
                    TextButton(onClick = { weekStart = weekStart.plusWeeks(1) }) { Text("›") }
                }
            }
        }
        if (!agenda) item {
            val planned = byDay.values.flatten().sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
            val done = byDay.entries.sumOf { (d, list) -> list.filter { it.completed && it.date == d }.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) } }
            val heaviest = byDay.maxByOrNull { e -> e.value.sumOf { it.endMinute - it.startMinute } }
            Text("Planned ${planned / 60}h ${planned % 60}m · completed ${done / 60}h ${done % 60}m" + (heaviest?.takeIf { it.value.isNotEmpty() }?.let { " · busiest ${it.key.dayOfWeek.name.take(3).lowercase()}" } ?: ""), style = MaterialTheme.typography.bodySmall)
        }
        items(days.filter { !agenda || byDay[it].orEmpty().isNotEmpty() }, key = { it.toString() }) { d ->
            val list = byDay[d].orEmpty()
            val load = list.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
            Card(Modifier.fillMaxWidth().clickable { onOpenDate(d) }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row {
                        Text(d.format(DateTimeFormatter.ofPattern("EEE d MMM")) + if (d == LocalDate.now()) " · today" else "", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${load / 60}h ${load % 60}m", style = MaterialTheme.typography.labelMedium, color = if (load > 10 * 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                    LinearProgressIndicator(progress = { (load / (16f * 60)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    if (list.isEmpty()) Text("Free day", style = MaterialTheme.typography.bodySmall)
                    list.forEach { t ->
                        Text("${hhmm(t.startMinute)}–${hhmm(t.endMinute)}  ${t.title}" + if (t.recurrenceType != "NONE") " ↻" else "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (agenda && byDay.values.all { it.isEmpty() }) item { Text("Nothing scheduled in the next three weeks.") }
    }
}

@Composable
internal fun GardenTab() {
    val context = LocalContext.current
    val summary = remember { FocusGarden.summarize(GardenStore(context).sessions(), LocalDate.now()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Level ${summary.level}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { summary.xpIntoLevel.toFloat() / summary.xpForNextLevel.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                Text("${summary.xpIntoLevel}/${summary.xpForNextLevel} XP to level ${summary.level + 1} · ${summary.xp} XP total")
                Text("🔥 ${summary.focusDayStreak}-day focus streak · best ${summary.bestStreak} · ${summary.todayMinutes}m today")
            } }
        }
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your garden (${summary.plants.size} plants, ${summary.withered} withered)", fontWeight = FontWeight.Bold)
                if (summary.plants.isEmpty() && summary.withered == 0) Text("Finish a Pomodoro focus session to plant your first sprout. Longer sessions grow bigger plants; abandoning a session withers one.")
                val cells = summary.plants.map { it.emoji } + List(summary.withered) { "🥀" }
                cells.chunked(8).forEach { row -> Text(row.joinToString(" "), fontSize = 26.sp) }
                Text(Plant.values().joinToString("  ") { "${it.emoji} ${it.minMinutes}m+" }, style = MaterialTheme.typography.bodySmall)
            } }
        }
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Badges", fontWeight = FontWeight.Bold)
                if (summary.badges.isEmpty()) Text("None yet", style = MaterialTheme.typography.bodySmall)
                summary.badges.forEach { Text("🏅 $it") }
            } }
        }
    }
}

@Composable
internal fun EnergyTab(vm: PlannerViewModel) {
    val tasks by vm.allTasks.collectAsStateWithLifecycle()
    var peakStart by remember { mutableFloatStateOf(9f) }
    var peakEnd by remember { mutableFloatStateOf(13f) }
    var dayEnd by remember { mutableFloatStateOf(22f) }
    var applied by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
    val todays = tasks.filter { it.occursOn(today) }
    val movable = todays.filter { !it.completed && it.recurrenceType == "NONE" && it.date == today && it.startMinute >= nowMinute && !it.tags.contains("fixed", true) }
    val fixed = todays - movable.toSet()
    val gaps = remember(todays, dayEnd) {
        val start = maxOf(nowMinute + 5, 6 * 60)
        val end = (dayEnd * 60).toInt()
        val busy = fixed.filter { !it.completed }.map { it.startMinute to it.endMinute }.sortedBy { it.first }
        val out = mutableListOf<PlanGap>()
        var cursor = start
        for ((s, e) in busy) { if (s > cursor) out += PlanGap(cursor, minOf(s, end)); cursor = maxOf(cursor, e) }
        if (end > cursor) out += PlanGap(cursor, end)
        out.filter { it.end - it.start >= 5 }
    }
    val plan = remember(movable, gaps, peakStart, peakEnd) {
        EnergyPlanner.plan(movable.map { PlanTask(it.id, (it.endMinute - it.startMinute).coerceAtLeast(5), EnergyPlanner.energyFromTags(it.tags), it.priority) }, gaps, (peakStart * 60).toInt(), (peakEnd * 60).toInt())
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Energy-aware day plan", fontWeight = FontWeight.Bold)
            Text("Tag tasks #deep/#hard/#high for demanding work and #easy/#admin/#low for light work. Hard work goes into your peak hours, light work goes outside them, higher priority first, with 5-minute buffers. Tag a task #fixed to keep it in place.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("Peak hours ${peakStart.toInt()}:00–${peakEnd.toInt()}:00")
            RangeSlider(peakStart..peakEnd, { r -> peakStart = r.start.toInt().toFloat(); peakEnd = maxOf(r.endInclusive.toInt(), peakStart.toInt() + 1).toFloat() }, valueRange = 5f..23f, steps = 17)
            Text("Plan until ${dayEnd.toInt()}:00")
            Slider(dayEnd, { dayEnd = it.toInt().toFloat() }, valueRange = 14f..24f, steps = 9)
        }
        item { Text("${movable.size} movable tasks · ${gaps.sumOf { it.end - it.start } / 60}h ${gaps.sumOf { it.end - it.start } % 60}m free", style = MaterialTheme.typography.labelMedium) }
        items(plan, key = { it.taskId }) { slot ->
            val t = movable.first { it.id == slot.taskId }
            ListItem(
                headlineContent = { Text(t.title) },
                supportingContent = { Text("${hhmm(t.startMinute)} → ${hhmm(slot.start)}–${hhmm(slot.end)} · ${EnergyPlanner.energyFromTags(t.tags).name.lowercase()} energy") }
            )
        }
        val unplaced = movable.filter { m -> plan.none { it.taskId == m.id } }
        if (unplaced.isNotEmpty()) item { Text("Doesn't fit today: " + unplaced.joinToString { it.title }, color = MaterialTheme.colorScheme.error) }
        item {
            Button(enabled = plan.isNotEmpty(), onClick = {
                plan.forEach { slot -> movable.firstOrNull { it.id == slot.taskId }?.let { vm.moveTask(it, slot.start, it.endMinute) } }
                applied = "Rescheduled ${plan.size} tasks"
            }) { Text("Apply plan") }
            if (applied.isNotBlank()) Text(applied, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun PrivateJournalTab() {
    val context = LocalContext.current
    val store = remember { PrivateJournalStore(context) }
    var entries by remember { mutableStateOf(runCatching { store.entries() }.getOrDefault(emptyList())) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null)
                Spacer(Modifier.width(8.dp))
                Text("Entries are encrypted with AES-256 using a key kept in the phone's secure hardware. Turn on App lock in settings to require your fingerprint or PIN.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            OutlinedTextField(text, { text = it }, label = { Text("Private entry") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Button(enabled = text.isNotBlank(), onClick = {
                runCatching { store.add(text.trim()) }.onSuccess { text = ""; error = ""; entries = store.entries() }.onFailure { error = "Encryption unavailable: ${it.message}" }
            }) { Text("Save encrypted") }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
        items(entries, key = { it.first }) { (id, date, body) ->
            Card { Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(date, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { store.delete(id); entries = store.entries() }) { Icon(Icons.Default.Delete, "Delete entry") }
                }
                Text(body)
            } }
        }
    }
}
