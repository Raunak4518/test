package com.raunak.daytimeline

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.domain.TimelineLayoutEngine
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun DayTimelineApp(appContext: Context) {
    val app = remember { AppContainer(appContext) }
    val vm: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory(app))
    MaterialTheme {
        PlannerRoot(vm)
    }
}

@Composable
private fun PlannerRoot(vm: PlannerViewModel) {
    var screen by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = screen == 0, onClick = { screen = 0 }, label = { Text("Planner") }, icon = { Icon(Icons.Default.Today, null) })
                NavigationBarItem(selected = screen == 1, onClick = { screen = 1 }, label = { Text("Stats") }, icon = { Icon(Icons.Default.PlayArrow, null) })
                NavigationBarItem(selected = screen == 2, onClick = { screen = 2 }, label = { Text("Settings") }, icon = { Icon(Icons.Default.Refresh, null) })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                0 -> PlannerScreen(vm)
                1 -> StatsScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerScreen(vm: PlannerViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val date by vm.currentDate.collectAsStateWithLifecycle()
    val now by vm.nowMillis.collectAsStateWithLifecycle()
    val pomodoro by vm.pomodoro.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    var showSheet by remember { mutableStateOf(false) }
    var quickAdd by remember { mutableStateOf("") }

    val dayRange = (settings.dayEndMinute - settings.dayStartMinute).coerceAtLeast(1)
    val nowMinute = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime().let { it.hour * 60 + it.minute }

    LaunchedEffect(date, settings.autoScrollNow) {
        if (settings.autoScrollNow && date == LocalDate.now()) {
            val px = ((nowMinute - settings.dayStartMinute).coerceAtLeast(0) * 2).coerceAtLeast(0)
            scroll.animateScrollTo(px)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopSummary(date, tasks, now)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = vm::onPrevDay) { Text("Yesterday") }
                if (date != LocalDate.now()) TextButton(onClick = vm::onToday) { Text("Today") }
                TextButton(onClick = vm::onNextDay) { Text("Tomorrow") }
            }

            OutlinedTextField(
                value = quickAdd,
                onValueChange = { quickAdd = it },
                placeholder = { Text("Quick add: DSA 7-9 pomodoro") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        vm.quickAdd(quickAdd)
                        quickAdd = ""
                    }) { Icon(Icons.Default.Add, null) }
                }
            )

            Box(Modifier.weight(1f).verticalScroll(scroll).padding(12.dp)) {
                val pxPerMinute = 2.dp
                val timelineHeight = pxPerMinute * dayRange
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(timelineHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(start = 56.dp)
                ) {
                    HourGrid(settings.dayStartMinute, settings.dayEndMinute, pxPerMinute)
                    val filtered = if (settings.showCompleted) tasks else tasks.filterNot { it.completed }
                    val placements = TimelineLayoutEngine.place(filtered)
                    placements.forEach { placement ->
                        val top = (placement.topMinute - settings.dayStartMinute).coerceAtLeast(0)
                        if (placement.task.endMinute >= settings.dayStartMinute && placement.task.startMinute <= settings.dayEndMinute) {
                            TaskCard(
                                task = placement.task,
                                modifier = Modifier
                                    .offset(y = pxPerMinute * top, x = (placement.column * 4).dp)
                                    .padding(start = 4.dp, end = 4.dp)
                                    .fillMaxWidth((1f / placement.columns) - 0.02f),
                                onToggle = { vm.toggleComplete(placement.task, !placement.task.completed) },
                                onDelete = { vm.deleteTask(placement.task) },
                                onDuplicate = { vm.duplicateTask(placement.task) }
                            )
                        }
                    }

                    if (date == LocalDate.now() && nowMinute in settings.dayStartMinute..settings.dayEndMinute) {
                        val top = nowMinute - settings.dayStartMinute
                        Row(
                            Modifier.offset(y = pxPerMinute * top).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp)))
                            Box(Modifier.height(2.dp).weight(1f).background(MaterialTheme.colorScheme.error))
                            Text("NOW", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Pomodoro: ${pomodoro.phase}")
                    Row {
                        IconButton(onClick = { if (pomodoro.running) vm.pausePomodoro() else vm.resumePomodoro() }) {
                            Icon(if (pomodoro.running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        }
                        IconButton(onClick = { vm.startPomodoro(tasks.firstOrNull { !it.completed }?.id) }) { Icon(Icons.Default.Today, null) }
                        IconButton(onClick = vm::resetPomodoro) { Icon(Icons.Default.Refresh, null) }
                    }
                }
                Text("${pomodoro.remainingSeconds}s remaining", modifier = Modifier.padding(start = 12.dp, bottom = 12.dp))
            }
        }

        FloatingActionButton(onClick = { showSheet = true }, modifier = Modifier.padding(16.dp).align(Alignment.BottomEnd)) {
            Icon(Icons.Default.Add, null)
        }
    }

    if (showSheet) {
        TaskSheet(
            onDismiss = { showSheet = false },
            onSave = { title, start, end, pomodoroEnabled, notes, priority, recurrence, reminderMode, reminderOffset ->
                vm.addOrUpdateTask(null, title, start, end, pomodoroEnabled, notes, priority, recurrence, reminderMode, reminderOffset)
                showSheet = false
            }
        )
    }
}

@Composable
private fun TopSummary(date: LocalDate, tasks: List<TaskModel>, nowMillis: Long) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")
    val planned = tasks.sumOf { it.endMinute - it.startMinute }.coerceAtLeast(0)
    val done = tasks.filter { it.completed }.sumOf { it.endMinute - it.startMinute }
    val percent = if (planned == 0) 0 else (done * 100 / planned)
    val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(date.format(formatter), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(String.format("%dh %02dm planned", planned / 60, planned % 60), style = MaterialTheme.typography.bodyMedium)
            Text("$percent% of today's tasks completed", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Slider(value = (now.hour * 60f + now.minute) / (24f * 60f), onValueChange = {}, enabled = false)
            Text("Now ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun HourGrid(startMinute: Int, endMinute: Int, pxPerMinute: Dp) {
    val total = (endMinute - startMinute).coerceAtLeast(0)
    repeat(total / 30 + 1) { idx ->
        val minute = startMinute + idx * 30
        val hour = minute / 60
        val top = minute - startMinute
        Row(Modifier.offset(y = pxPerMinute * top).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (minute % 60 == 0) {
                Text(String.format("%02d:00", hour), style = MaterialTheme.typography.labelSmall, modifier = Modifier.offset(x = (-52).dp))
            }
            Box(
                Modifier
                    .height(if (minute % 60 == 0) 1.dp else 0.5.dp)
                    .fillMaxWidth()
                    .alpha(if (minute % 60 == 0) 0.35f else 0.18f)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

@Composable
private fun TaskCard(task: TaskModel, modifier: Modifier, onToggle: () -> Unit, onDelete: () -> Unit, onDuplicate: () -> Unit) {
    val minutes = task.endMinute - task.startMinute
    val cardColor = Color(task.colorHex).copy(alpha = if (task.completed) 0.45f else 0.9f)
    Card(
        modifier = modifier.height((minutes.coerceAtLeast(15) * 2).dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
            }
            Text(
                "%02d:%02d–%02d:%02d · %dh %02dm".format(
                    task.startMinute / 60,
                    task.startMinute % 60,
                    task.endMinute / 60,
                    task.endMinute % 60,
                    minutes / 60,
                    minutes % 60
                ),
                style = MaterialTheme.typography.labelMedium
            )
            if (task.pomodoroEnabled) Text("🍅 Pomodoro", style = MaterialTheme.typography.labelSmall)
            if (task.notes.isNotBlank()) Text(task.notes, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = onDuplicate) { Text("Duplicate") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskSheet(
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Boolean, String, Int, String, String, Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("10:00") }
    var notes by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(1) }
    var pomodoro by remember { mutableStateOf(false) }
    var recurrence by remember { mutableStateOf("NONE") }
    var reminderMode by remember { mutableStateOf("NONE") }
    var reminderOffset by remember { mutableIntStateOf(10) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add task", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task name") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start HH:mm") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End HH:mm") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15 to "15m", 30 to "30m", 45 to "45m", 60 to "1h", 90 to "1h30", 120 to "2h").forEach { (m, label) ->
                    FilledTonalButton(onClick = {
                        val s = parseMinute(start)
                        end = formatMinute(s + m)
                    }) { Text(label) }
                }
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Pomodoro")
                Switch(checked = pomodoro, onCheckedChange = { pomodoro = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Priority")
                Slider(value = priority.toFloat(), onValueChange = { priority = it.roundToInt() }, valueRange = 0f..3f, steps = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { recurrence = "NONE" }) { Text("No Repeat") }
                OutlinedButton(onClick = { recurrence = "DAILY" }) { Text("Daily") }
                OutlinedButton(onClick = { recurrence = "WEEKDAYS" }) { Text("Weekdays") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { reminderMode = "NONE" }) { Text("No Reminder") }
                OutlinedButton(onClick = { reminderMode = "AT_START" }) { Text("At Start") }
                OutlinedButton(onClick = { reminderMode = "BEFORE" }) { Text("${reminderOffset}m before") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    onSave(
                        title.ifBlank { "Untitled" },
                        parseMinute(start),
                        parseMinute(end).coerceAtLeast(parseMinute(start) + 5),
                        pomodoro,
                        notes,
                        priority,
                        recurrence,
                        reminderMode,
                        reminderOffset
                    )
                }, modifier = Modifier.weight(1f)) { Text("Save") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun StatsScreen(vm: PlannerViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val total = tasks.size
    val completed = tasks.count { it.completed }
    val planned = tasks.sumOf { it.endMinute - it.startMinute }
    val done = tasks.filter { it.completed }.sumOf { it.endMinute - it.startMinute }
    val free = (24 * 60 - planned).coerceAtLeast(0)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Statistics", style = MaterialTheme.typography.headlineSmall)
        StatRow("Tasks completed today", "$completed / $total")
        StatRow("Planned vs completed", "${planned / 60}h ${planned % 60}m vs ${done / 60}h ${done % 60}m")
        StatRow("Completion rate", if (total == 0) "0%" else "${completed * 100 / total}%")
        StatRow("Free time", "${free / 60}h ${free % 60}m")
        StatRow("Pomodoro-enabled", "${tasks.count { it.pomodoroEnabled }}")
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsScreen(vm: PlannerViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var exportText by remember { mutableStateOf<String?>(null) }
    var importText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Slider(value = settings.dayStartMinute.toFloat(), onValueChange = { vm.updateSettings { copy(dayStartMinute = it.roundToInt()) } }, valueRange = 0f..(20 * 60).toFloat())
        Text("Day start: ${formatMinute(settings.dayStartMinute)}")
        Slider(value = settings.dayEndMinute.toFloat(), onValueChange = { vm.updateSettings { copy(dayEndMinute = it.roundToInt().coerceAtLeast(dayStartMinute + 60)) } }, valueRange = (4 * 60).toFloat()..(24 * 60).toFloat())
        Text("Day end: ${formatMinute(settings.dayEndMinute)}")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Auto-scroll to now")
            Switch(checked = settings.autoScrollNow, onCheckedChange = { vm.updateSettings { copy(autoScrollNow = it) } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Show completed tasks")
            Switch(checked = settings.showCompleted, onCheckedChange = { vm.updateSettings { copy(showCompleted = it) } })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                vm.exportJson {
                    val out = File(context.filesDir, "daytimeline-export.json")
                    out.writeText(it)
                    exportText = out.absolutePath
                }
            }) { Text("Export JSON") }
            OutlinedButton(onClick = {
                val inFile = File(context.filesDir, "daytimeline-export.json")
                if (inFile.exists()) {
                    importText = inFile.readText()
                    vm.importJson(importText) { success ->
                        Toast.makeText(context, if (success) "Imported" else "Import failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text("Import JSON") }
        }
        exportText?.let { Text("Exported: $it", style = MaterialTheme.typography.labelSmall) }
    }
}

private fun parseMinute(text: String): Int {
    val parts = text.split(':')
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return (h.coerceIn(0, 23) * 60 + m.coerceIn(0, 59)).coerceIn(0, 24 * 60)
}

private fun formatMinute(minute: Int): String {
    val m = minute.coerceIn(0, 24 * 60)
    return "%02d:%02d".format(m / 60, m % 60)
}
