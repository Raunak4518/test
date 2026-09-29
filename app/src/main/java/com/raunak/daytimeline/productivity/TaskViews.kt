package com.raunak.daytimeline.productivity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.raunak.daytimeline.PlannerViewModel
import com.raunak.daytimeline.domain.QuickAddParser
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.ui.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Priority is stored 0–3 (3 = highest), shown the way Todoist/TickTick do. */
object TaskPriority {
    val labels = listOf("None", "Low", "Medium", "High")
    fun label(p: Int) = labels[p.coerceIn(0, 3)]

    @Composable
    fun color(p: Int): Color = when (p.coerceIn(0, 3)) {
        3 -> Chronora.colors.bad
        2 -> Chronora.colors.warn
        1 -> Color(0xFF4E79A7)
        else -> Chronora.muted
    }
}

val TaskColors = listOf(0xFF5A6CF3, 0xFF55786A, 0xFFE15759, 0xFFF28E2B, 0xFF9C6ADE, 0xFF2BA3A3, 0xFFD4A017, 0xFF8C6D5A)

fun hm(m: Int) = "%02d:%02d".format((m / 60).coerceIn(0, 23), (m % 60).coerceIn(0, 59))
private fun duration(m: Int) = if (m >= 60) "${m / 60}h" + (if (m % 60 > 0) " ${m % 60}m" else "") else "${m}m"

fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (ChronoUnit.DAYS.between(today, date)) {
    0L -> "Today"
    1L -> "Tomorrow"
    -1L -> "Yesterday"
    in 2L..6L -> date.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase)
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
}

private fun recurrenceLabel(t: TaskModel) = when (t.recurrenceType) {
    "DAILY" -> "Daily"; "WEEKDAYS" -> "Weekdays"; "WEEKENDS" -> "Weekends"; "WEEKLY" -> "Weekly"
    "CUSTOM_DAYS" -> t.recurrenceDays.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.joinToString(" ") { DayOfWeek.of(it).name.take(2).lowercase().replaceFirstChar(Char::uppercase) }
    else -> null
}

/** One task: priority-coloured tick, title, time, repeat/reminder/focus markers, tags, and a menu. */
@Composable
fun TaskCard(task: TaskModel, vm: PlannerViewModel, onEdit: () -> Unit, onChecklist: () -> Unit = {}, showDate: Boolean = false, today: LocalDate = LocalDate.now()) {
    val pColor = TaskPriority.color(task.priority)
    var menu by remember { mutableStateOf(false) }
    val overdue = !task.completed && task.date.isBefore(today)
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(Color(task.colorHex).copy(alpha = if (task.completed) .3f else 1f)))
            Row(Modifier.weight(1f).padding(start = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(if (task.completed) pColor else Color.Transparent)
                        .border(2.dp, pColor, CircleShape).clickable { vm.toggleComplete(task, !task.completed) },
                    contentAlignment = Alignment.Center
                ) { if (task.completed) Icon(Icons.Default.Check, "Completed", tint = Color.White, modifier = Modifier.size(16.dp)) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(task.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                        color = if (task.completed) Chronora.muted else MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val when_ = (if (showDate || overdue) relativeDay(task.date, today) + " · " else "") + "${hm(task.startMinute)}–${hm(task.endMinute)} · ${duration(task.endMinute - task.startMinute)}"
                        Text(when_, style = MaterialTheme.typography.bodySmall, color = if (overdue) Chronora.colors.bad else Chronora.muted)
                        recurrenceLabel(task)?.let { Icon(Icons.Default.Repeat, it, Modifier.size(14.dp), tint = Chronora.muted) }
                        if (task.reminderMode != "NONE") Icon(Icons.Default.NotificationsActive, "Reminder", Modifier.size(14.dp), tint = Chronora.muted)
                        if (task.pomodoroEnabled) Icon(Icons.Default.Timer, "Focus", Modifier.size(14.dp), tint = Chronora.muted)
                    }
                    val tags = task.tags.split(',', ' ').map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }
                    if (tags.isNotEmpty() || task.priority >= 2) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (task.priority >= 2) Pill(TaskPriority.label(task.priority), pColor)
                        tags.take(3).forEach { Pill("#$it", MaterialTheme.colorScheme.primary) }
                    }
                    if (task.notes.isNotBlank()) Text(task.notes, style = MaterialTheme.typography.bodySmall, color = Chronora.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.align(Alignment.CenterVertically)) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Task options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Start focus") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) }, onClick = { menu = false; vm.startPomodoro(task.id) })
                    DropdownMenuItem(text = { Text("Checklist") }, leadingIcon = { Icon(Icons.Default.Checklist, null) }, onClick = { menu = false; onChecklist() })
                    if (task.recurrenceType == "NONE") {
                        if (task.date != today) DropdownMenuItem(text = { Text("Move to today") }, leadingIcon = { Icon(Icons.Default.Today, null) }, onClick = { menu = false; vm.reschedule(task, today) })
                        DropdownMenuItem(text = { Text("Move to tomorrow") }, leadingIcon = { Icon(Icons.Default.Schedule, null) }, onClick = { menu = false; vm.reschedule(task, maxOf(task.date, today).plusDays(1)) })
                    }
                    Text("Priority", style = MaterialTheme.typography.labelSmall, color = Chronora.muted, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        (3 downTo 0).forEach { p ->
                            IconButton(onClick = { menu = false; vm.setPriority(task, p) }) { Icon(if (p == task.priority) Icons.Default.Flag else Icons.Default.OutlinedFlag, TaskPriority.label(p), tint = TaskPriority.color(p)) }
                        }
                    }
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; vm.duplicateTask(task) })
                    DropdownMenuItem(text = { Text("Delete", color = Chronora.colors.bad) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Chronora.colors.bad) }, onClick = { menu = false; vm.deleteTask(task) })
                }
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = .12f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** Type a task in plain words; see exactly how it will be read before adding. */
@Composable
fun QuickAddBar(vm: PlannerViewModel, date: LocalDate) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text, date) { if (text.isBlank()) null else QuickAddParser.parse(text, date) }
    fun submit() { if (text.isNotBlank()) { if (date == LocalDate.now()) vm.quickAddExact(text) else vm.quickAdd(text); text = "" } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Add a task… \"DSA revision 7-9pm #study !2\"") },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                trailingIcon = { if (text.isNotBlank()) IconButton(onClick = ::submit) { Icon(Icons.Default.Send, "Add") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
            parsed?.let { p ->
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(p.title.ifBlank { "Untitled" }, MaterialTheme.colorScheme.onSurface)
                    Pill(relativeDay(p.date), MaterialTheme.colorScheme.primary)
                    Pill("${hm(p.startMinute)}–${hm(p.endMinute)}", MaterialTheme.colorScheme.primary)
                    if (p.priority != 1) Pill(TaskPriority.label(p.priority), TaskPriority.color(p.priority))
                    if (p.recurrenceType != "NONE") Pill(p.recurrenceType.lowercase().replace('_', ' '), Chronora.colors.good)
                    if (p.tags.isNotBlank()) Pill(p.tags.split(',').joinToString(" ") { "#" + it.trim() }, MaterialTheme.colorScheme.primary)
                    if (p.reminderMode != "NONE") Pill("reminder", Chronora.colors.warn)
                    if (p.pomodoro) Pill("focus", Chronora.colors.good)
                }
            }
        }
    }
}

/** Overdue one-off tasks with a one-tap rescue. */
@Composable
fun OverdueCard(overdue: List<TaskModel>, vm: PlannerViewModel, onEdit: (TaskModel) -> Unit, today: LocalDate = LocalDate.now()) {
    if (overdue.isEmpty()) return
    var open by remember { mutableStateOf(overdue.size <= 3) }
    SectionCard("Overdue · ${overdue.size}", "Not done on their day", action = {
        TextButton(onClick = { overdue.forEach { vm.reschedule(it, today) } }) { Text("Move all to today") }
    }) {
        (if (open) overdue else overdue.take(2)).forEach { TaskCard(it, vm, { onEdit(it) }, showDate = true, today = today) }
        if (overdue.size > 2) TextButton(onClick = { open = !open }) { Text(if (open) "Show less" else "Show all ${overdue.size}") }
    }
}

/** Next [days] days grouped by day, like the Upcoming view of Todoist/TickTick. */
object TaskViewsScope {
    fun LazyListScope.upcoming(agenda: List<TaskModel>, days: Int, vm: PlannerViewModel, onEdit: (TaskModel) -> Unit, onChecklist: (TaskModel) -> Unit, today: LocalDate = LocalDate.now()) {
    val window = agenda.filter { !it.date.isBefore(today) && it.date.isBefore(today.plusDays(days.toLong())) }
    val byDay = window.groupBy { it.date }
    (0 until days).map { today.plusDays(it.toLong()) }.forEach { d ->
        val list = byDay[d].orEmpty()
        item(key = "h$d") {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(relativeDay(d, today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("  " + d.format(DateTimeFormatter.ofPattern("d MMM")), style = MaterialTheme.typography.bodySmall, color = Chronora.muted, modifier = Modifier.weight(1f))
                val open = list.count { !it.completed }
                Text(if (list.isEmpty()) "Free" else "$open open · ${duration(list.sumOf { it.endMinute - it.startMinute })}", style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
            }
        }
        items(list, key = { "u${it.id}-${it.date}" }) { t -> TaskCard(t, vm, { onEdit(t) }, { onChecklist(t) }, today = today) }
    }
}
}

/** Full task editor: date and time pickers, duration shortcuts, priority, colour, repeat, reminder, tags, notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditor(vm: PlannerViewModel, initial: TaskModel?, defaultDate: LocalDate, close: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: defaultDate) }
    var start by remember { mutableIntStateOf(initial?.startMinute ?: 9 * 60) }
    var end by remember { mutableIntStateOf(initial?.endMinute ?: 10 * 60) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var tags by remember { mutableStateOf(initial?.tags ?: "") }
    var priority by remember { mutableIntStateOf(initial?.priority ?: 1) }
    var color by remember { mutableLongStateOf(initial?.colorHex ?: TaskColors.first()) }
    var pomodoro by remember { mutableStateOf(initial?.pomodoroEnabled ?: false) }
    var recurrence by remember { mutableStateOf(initial?.recurrenceType ?: "NONE") }
    var days by remember { mutableStateOf(initial?.recurrenceDays?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()) }
    var reminder by remember { mutableStateOf(initial?.reminderMode ?: "NONE") }
    var offset by remember { mutableIntStateOf(initial?.reminderOffsetMinutes?.takeIf { it > 0 } ?: 10) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    val length = (end - start).coerceAtLeast(5)

    AlertDialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp),
        title = { Text(if (initial == null) "New task" else "Edit task") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("What") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Label("When")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val today = LocalDate.now()
                    FilterChip(date == today, { date = today }, label = { Text("Today") })
                    FilterChip(date == today.plusDays(1), { date = today.plusDays(1) }, label = { Text("Tomorrow") })
                    val weekend = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                    FilterChip(date == weekend, { date = weekend }, label = { Text("Weekend") })
                    AssistChip(onClick = { pickDate = true }, leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)) }, label = { Text(relativeDay(date)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickTime = true }, Modifier.weight(1f)) { Text(hm(start)) }
                    Text("→")
                    OutlinedButton(onClick = { pickTime = false }, Modifier.weight(1f)) { Text(hm(end)) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60, 90, 120).forEach { m -> FilterChip(length == m, { end = (start + m).coerceAtMost(24 * 60 - 1) }, label = { Text(duration(m)) }) }
                }
                Label("Priority")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    (3 downTo 0).forEachIndexed { i, p ->
                        SegmentedButton(priority == p, { priority = p }, SegmentedButtonDefaults.itemShape(i, 4), icon = {}) {
                            Text(TaskPriority.label(p), color = if (priority == p) TaskPriority.color(p) else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                        }
                    }
                }
                Label("Colour")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskColors.forEach { c -> Box(Modifier.size(26.dp).clip(CircleShape).background(Color(c)).border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape).clickable { color = c }) }
                }
                Label("Repeat")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NONE" to "Never", "DAILY" to "Daily", "WEEKDAYS" to "Weekdays", "WEEKENDS" to "Weekends", "WEEKLY" to "Weekly", "CUSTOM_DAYS" to "Pick days").forEach { (k, l) ->
                        FilterChip(recurrence == k, { recurrence = k }, label = { Text(l) })
                    }
                }
                if (recurrence == "CUSTOM_DAYS") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..7).forEach { d ->
                        val on = d in days
                        Box(Modifier.size(34.dp).clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable { days = if (on) days - d else days + d }, contentAlignment = Alignment.Center) {
                            Text(DayOfWeek.of(d).name.take(1), color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Label("Reminder")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(reminder == "NONE", { reminder = "NONE" }, label = { Text("None") })
                    FilterChip(reminder == "AT_START", { reminder = "AT_START" }, label = { Text("At start") })
                    listOf(5, 10, 30, 60, 1440).forEach { m -> FilterChip(reminder == "BEFORE" && offset == m, { reminder = "BEFORE"; offset = m }, label = { Text(if (m == 1440) "1 day before" else duration(m) + " before") }) }
                }
                SwitchRow("Focus session (Pomodoro) for this task", pomodoro) { pomodoro = it }
                OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank() && (recurrence != "CUSTOM_DAYS" || days.isNotEmpty()), onClick = {
                vm.addOrUpdateTask(initial?.id, title.trim(), start, end.coerceAtLeast(start + 5), pomodoro, notes, priority, recurrence, reminder, if (reminder == "BEFORE") offset else 0,
                    days.sorted().joinToString(","), tags, date, color)
                close()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86_400_000L)
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { date = LocalDate.ofEpochDay(it / 86_400_000L) }; pickDate = false }) { Text("OK") }
        }, dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
    pickTime?.let { isStart ->
        val cur = if (isStart) start else end
        val state = rememberTimePickerState(cur / 60, cur % 60, is24Hour = true)
        AlertDialog(onDismissRequest = { pickTime = null }, title = { Text(if (isStart) "Start" else "End") }, text = { TimePicker(state) }, confirmButton = {
            TextButton(onClick = {
                val m = state.hour * 60 + state.minute
                if (isStart) { end = (m + length).coerceAtMost(24 * 60 - 1); start = m } else end = m.coerceAtLeast(start + 5)
                pickTime = null
            }) { Text("OK") }
        }, dismissButton = { TextButton(onClick = { pickTime = null }) { Text("Cancel") } })
    }
}

@Composable private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
