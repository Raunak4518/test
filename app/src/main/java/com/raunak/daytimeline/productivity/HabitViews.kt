package com.raunak.daytimeline.productivity

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raunak.daytimeline.features.*
import com.raunak.daytimeline.ui.*
import java.time.DayOfWeek
import java.time.LocalDate

/** Colours offered for habits; any ARGB value saved on a habit is honoured. */
val HabitColors = listOf(0xFF55786A, 0xFF4E79A7, 0xFFE15759, 0xFFF28E2B, 0xFF9C6ADE, 0xFF2BA3A3, 0xFFD4A017, 0xFF8C6D5A)

/** Summary for the day: due, done, and a progress bar. */
@Composable
fun HabitTodayHero(habits: List<OfflineHabit>, today: LocalDate = LocalDate.now()) {
    val relevant = habits.filter { HabitEngine.forToday(it, today) }
    val done = relevant.count { HabitEngine.isDone(it, today) }
    val skipped = relevant.count { HabitEngine.isSkipped(it, today) }
    val target = (relevant.size - skipped).coerceAtLeast(0)
    val strength = habits.filterNot { it.archived }.map { HabitEngine.strength(it, today) }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    HeroCard {
        Text("Habits today", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelLarge)
        Text(if (target == 0) "Nothing due" else "$done of $target done", color = Chronora.colors.onHero, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(progress = { if (target == 0) 1f else done / target.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Chronora.colors.heroAccent, trackColor = Color.White.copy(alpha = .15f))
        Text(listOfNotNull(strength?.let { "Average strength $it%" }, if (skipped > 0) "$skipped skipped" else null, "Tap a day to tick · hold to skip").joinToString(" · "), color = Chronora.colors.heroMuted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Every active habit grouped by part of day, then archived ones on request. */
@Composable
fun HabitList(habits: List<OfflineHabit>, store: OfflineProductivityStore, onEdit: (OfflineHabit) -> Unit, today: LocalDate = LocalDate.now()) {
    var showArchived by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        val active = habits.filterNot { it.archived }.sortedBy(HabitEngine::order)
        if (active.isEmpty()) EmptyState("No habits yet", "Add one with +. Pick specific days, a number of times per week, or every few days.")
        active.groupBy(HabitEngine::partOfDay).toList().sortedBy { listOf("Morning", "Afternoon", "Evening", "Anytime").indexOf(it.first) }.forEach { (part, list) ->
            Text(part.uppercase(), style = MaterialTheme.typography.labelMedium, color = Chronora.muted, modifier = Modifier.padding(top = 4.dp))
            list.forEach { HabitCard(it, store, today) { onEdit(it) } }
        }
        val archived = habits.filter { it.archived }
        if (archived.isNotEmpty()) {
            TextButton(onClick = { showArchived = !showArchived }) { Text(if (showArchived) "Hide archived" else "Archived (${archived.size})") }
            if (showArchived) archived.forEach { HabitCard(it, store, today) { onEdit(it) } }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitCard(habit: OfflineHabit, store: OfflineProductivityStore, today: LocalDate = LocalDate.now(), onEdit: () -> Unit) {
    val confirm = com.raunak.daytimeline.ui.rememberConfirm()
    val color = Color(habit.color.takeIf { it != 0L } ?: HabitColors.first())
    var expanded by remember(habit.id) { mutableStateOf(false) }
    val strength = remember(habit, today) { HabitEngine.strength(habit, today) }
    val streak = remember(habit, today) { HabitEngine.streak(habit, today) }
    val rate = remember(habit, today) { HabitEngine.completionRate(habit, today) }
    val doneToday = HabitEngine.isDone(habit, today)
    Card(Modifier.fillMaxWidth().animateContentSize().testTag("habit-${habit.id}")) {
        Column(Modifier.padding(Spacing.inner), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StrengthRing(strength, color, 46.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp).clickable { expanded = !expanded }) {
                    Text(habit.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text(HabitEngine.describe(habit) + (habit.preferredTime.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                        (if (HabitEngine.frequency(habit) == HabitFrequency.WEEKLY) " · ${HabitEngine.weekCount(habit, today)}/${HabitEngine.perWeek(habit)} this week" else ""),
                        style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                    if (habit.note.isNotBlank()) Text(habit.note, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2)
                }
                CheckBubble(doneToday, HabitEngine.isSkipped(habit, today), color, 44.dp, Modifier.combinedClickable(onClick = { store.toggleHabit(habit.id, today) }, onLongClick = { store.skipHabit(habit.id, today) }))
            }
            WeekStrip(habit, store, today, color)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Streak", "${streak.current} ${streak.unit}", Modifier.weight(1f))
                Stat("Best", "${streak.best}", Modifier.weight(1f))
                Stat("30 days", "$rate%", Modifier.weight(1f))
                Stat("Total", "${HabitEngine.done(habit).size}", Modifier.weight(1f))
            }
            if (expanded) {
                Text("Last 16 weeks", style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
                Heatmap(habit, today, color)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit") }
                    TextButton(onClick = { store.archiveHabit(habit.id, !habit.archived) }) { Icon(Icons.Default.Archive, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (habit.archived) "Restore" else "Archive") }
                    TextButton(onClick = { confirm.ask("this habit and its history") { store.deleteHabit(habit.id) } }) { Icon(Icons.Default.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Delete") }
                }
            } else {
                Text("Tap the name for history and options", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
            }
        }
    }
}

@Composable
fun StrengthRing(percent: Int, color: Color, size: Dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.toPx() * 0.11f
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val tl = Offset(stroke / 2, stroke / 2)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * percent / 100f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$percent", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CheckBubble(done: Boolean, skipped: Boolean, color: Color, size: Dp, modifier: Modifier) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (done) color else Color.Transparent)
            .border(2.dp, if (skipped) Chronora.muted else color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when {
            done -> Icon(Icons.Default.Check, "Done", tint = Color.White)
            skipped -> Icon(Icons.Default.SkipNext, "Skipped", tint = Chronora.muted)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekStrip(habit: OfflineHabit, store: OfflineProductivityStore, today: LocalDate, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (6 downTo 0).forEach { back ->
            val date = today.minusDays(back.toLong())
            val cell = HabitEngine.cell(habit, date, today)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = if (date == today) color else Chronora.muted, fontWeight = if (date == today) FontWeight.Bold else null)
                Box(
                    Modifier.padding(top = 2.dp).size(32.dp).clip(CircleShape)
                        .background(cellColor(cell, color))
                        .combinedClickable(onClick = { store.toggleHabit(habit.id, date) }, onLongClick = { store.skipHabit(habit.id, date) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (cell == HabitCell.SKIPPED) "–" else date.dayOfMonth.toString(),
                        fontSize = 12.sp,
                        color = if (cell == HabitCell.DONE) Color.White else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun cellColor(cell: HabitCell, color: Color): Color = when (cell) {
    HabitCell.DONE -> color
    HabitCell.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
    HabitCell.MISSED -> Chronora.colors.bad.copy(alpha = .14f)
    HabitCell.PENDING -> color.copy(alpha = .18f)
    HabitCell.OFF, HabitCell.FUTURE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
}

@Composable
private fun Heatmap(habit: OfflineHabit, today: LocalDate, color: Color) {
    val weeks = remember(habit, today) { HabitEngine.heatmap(habit, today) }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            DayOfWeek.values().forEach { Box(Modifier.height(12.dp), contentAlignment = Alignment.Center) { Text(if (it.value % 2 == 1) it.name.take(1) else "", fontSize = 8.sp, color = Chronora.muted) } }
        }
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { (_, cell) -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(if (cell == HabitCell.FUTURE) Color.Transparent else cellColor(cell, color))) }
            }
        }
    }
}

/** Create or edit a habit: name, why, colour, frequency, days, reminder. */
@Composable
fun HabitEditorDialog(initial: OfflineHabit?, onSave: (OfflineHabit) -> Unit, close: () -> Unit) {
    val base = initial ?: OfflineHabit(0, "", 3, "", (1..7).toSet(), emptySet())
    var name by remember { mutableStateOf(base.name) }
    var note by remember { mutableStateOf(base.note) }
    var color by remember { mutableLongStateOf(base.color.takeIf { it != 0L } ?: HabitColors.first()) }
    var freq by remember { mutableStateOf(HabitEngine.frequency(base)) }
    var days by remember { mutableStateOf(HabitEngine.days(base)) }
    var perWeek by remember { mutableIntStateOf(HabitEngine.perWeek(base)) }
    var interval by remember { mutableIntStateOf(HabitEngine.interval(base)) }
    var time by remember { mutableStateOf(base.preferredTime) }
    val timeOk = time.isBlank() || Regex("([01]?\\d|2[0-3]):[0-5]\\d").matches(time.trim())
    AlertDialog(onDismissRequest = close, title = { Text(if (initial == null) "New habit" else "Edit habit") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Habit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Why it matters (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HabitColors.forEach { c ->
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Color(c)).border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape).clickable { color = c })
                }
            }
            Text("How often", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                HabitFrequency.values().forEachIndexed { i, f ->
                    SegmentedButton(selected = freq == f, onClick = { freq = f }, shape = SegmentedButtonDefaults.itemShape(i, HabitFrequency.values().size)) { Text(f.label, maxLines = 1, fontSize = 11.sp) }
                }
            }
            when (freq) {
                HabitFrequency.DAYS -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..7).forEach { d ->
                        val on = d in days
                        Box(Modifier.size(34.dp).clip(CircleShape).background(if (on) Color(color) else MaterialTheme.colorScheme.surfaceVariant).clickable { days = if (on) days - d else days + d }, contentAlignment = Alignment.Center) {
                            Text(DayOfWeek.of(d).name.take(1), color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                HabitFrequency.WEEKLY -> Stepper("Times per week", "$perWeek", { perWeek = (perWeek - 1).coerceAtLeast(1) }, { perWeek = (perWeek + 1).coerceAtMost(7) })
                HabitFrequency.INTERVAL -> Stepper("Every", "$interval days", { interval = (interval - 1).coerceAtLeast(2) }, { interval = (interval + 1).coerceAtMost(60) })
            }
            OutlinedTextField(time, { time = it }, label = { Text("Reminder HH:mm (optional)") }, singleLine = true, isError = !timeOk, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        Button(enabled = name.isNotBlank() && timeOk && (freq != HabitFrequency.DAYS || days.isNotEmpty()), onClick = {
            onSave(base.copy(
                name = name, note = note.trim(), color = color, frequency = freq.name,
                activeDays = if (freq == HabitFrequency.DAYS) days else (1..7).toSet(),
                targetPerWeek = if (freq == HabitFrequency.WEEKLY) perWeek else if (freq == HabitFrequency.DAYS) days.size else 7,
                intervalDays = interval, preferredTime = time.trim()
            ))
        }) { Text(if (initial == null) "Create" else "Save") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
