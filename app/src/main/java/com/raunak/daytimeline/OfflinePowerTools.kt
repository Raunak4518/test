package com.raunak.daytimeline

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.features.Habit
import com.raunak.daytimeline.features.LocalFeatureStore
import com.raunak.daytimeline.features.QuickNote
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ToolBg = Color(0xFFF4F1E9)
private val ToolCard = Color(0xFFFFFCF7)
private val ToolInk = Color(0xFF18221F)
private val ToolSage = Color(0xFF55786A)
private val ToolMuted = Color(0xFF727B76)

/** Deep offline tools: scheduling audit, global local search and ICS export. */
@Composable
fun OfflinePowerTools(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = remember { AppContainer(context.applicationContext) }
    val vm: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory(app))
    val store = remember { LocalFeatureStore(context.applicationContext) }
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val habits by store.habits.collectAsStateWithLifecycle()
    val notes by store.notes.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var exported by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = ToolBg) {
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text("Productivity Lab", style = MaterialTheme.typography.headlineMedium)
                        Text("Local tools. No account. No server.", color = ToolMuted)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search tasks, notes and habits") },
                    shape = RoundedCornerShape(18.dp)
                )
            }
            if (query.isNotBlank()) {
                val q = query.trim()
                val taskHits = tasks.filter { it.title.contains(q, true) || it.notes.contains(q, true) || it.tags.contains(q, true) }
                val noteHits = notes.filter { it.title.contains(q, true) || it.body.contains(q, true) || it.tags.contains(q, true) }
                val habitHits = habits.filter { it.name.contains(q, true) }
                item { SearchSummary(taskHits, noteHits, habitHits) }
                items(taskHits, key = { it.id }) { SearchTask(it) }
                items(noteHits, key = { "n${it.id}" }) { SearchNote(it) }
                items(habitHits, key = { "h${it.id}" }) { SearchHabit(it) }
            }
            item { ScheduleAudit(tasks) }
            item { ExportCard(context, tasks, vm, exported) { exported = it } }
            item { OfflinePrinciples() }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@Composable private fun ScheduleAudit(tasks: List<TaskModel>) {
    val sorted = tasks.sortedBy { it.startMinute }
    val total = sorted.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    val conflicts = sorted.zipWithNext().count { it.first.endMinute > it.second.startMinute }
    val gaps = sorted.zipWithNext().sumOf { (it.second.startMinute - it.first.endMinute).coerceAtLeast(0) }
    val focus = sorted.filter { it.pomodoroEnabled }.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    val overload = total > 10 * 60
    val quality = when { conflicts > 0 -> "Resolve overlaps"; overload -> "Heavy day"; total == 0 -> "Open day"; else -> "Balanced plan" }
    Surface(RoundedCornerShape(24.dp), color = ToolCard, shadowElevation = 1.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column { Text("Schedule health", style = MaterialTheme.typography.titleLarge); Text(quality, color = ToolSage) }
                Icon(if (conflicts == 0) Icons.Default.Verified else Icons.Default.WarningAmber, null, tint = if (conflicts == 0) ToolSage else Color(0xFFB85F50), modifier = Modifier.size(28.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Planned", "${total / 60}h ${total % 60}m")
                Metric("Focus", "${focus / 60}h ${focus % 60}m")
                Metric("Open", "${gaps / 60}h ${gaps % 60}m")
            }
            if (conflicts > 0) Text("$conflicts overlapping block(s) detected. Move or resize them before starting.", color = Color(0xFF9D4E43))
            if (overload) Text("More than 10 hours are scheduled. Consider protecting breaks and moving low-priority work.", color = ToolMuted)
            if (conflicts == 0 && !overload && total > 0) Text("No overlaps detected. Your schedule leaves ${gaps / 60}h ${gaps % 60}m between blocks.", color = ToolMuted)
        }
    }
}

@Composable private fun Metric(label: String, value: String) {
    Surface(Modifier.weight(1f), RoundedCornerShape(15.dp), color = Color(0xFFEEF1EC)) { Column(Modifier.padding(11.dp)) { Text(label, color = ToolMuted, style = MaterialTheme.typography.labelSmall); Text(value, color = ToolInk, style = MaterialTheme.typography.titleMedium) } }
}

@Composable private fun ExportCard(context: Context, tasks: List<TaskModel>, vm: PlannerViewModel, exported: Boolean, setExported: (Boolean) -> Unit) {
    var pending by remember { mutableStateOf(false) }
    Surface(RoundedCornerShape(24.dp), color = ToolInk) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Calendar interoperability", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("Export today's timeline as a standard .ics calendar file. It stays completely on-device until you choose where to save it.", color = Color(0xFFC5D0CB))
            Button(onClick = {
                pending = true
                val text = buildIcs(tasks)
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "text/calendar"
                    putExtra(Intent.EXTRA_TITLE, "daytimeline-${LocalDate.now()}.ics")
                    putExtra("daytimeline.ics.content", text)
                }
                context.startActivity(intent)
                setExported(true)
                pending = false
            }, enabled = !pending) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(7.dp)); Text(if (exported) "Export again" else "Export .ics") }
        }
    }
}

private fun buildIcs(tasks: List<TaskModel>): String {
    val date = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    val stamp = java.time.ZonedDateTime.now(zone).format(fmt)
    return buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//DayTimeline//Offline Calendar//EN")
        tasks.forEach { t ->
            val start = date.atStartOfDay().plusMinutes(t.startMinute.toLong()).atZone(zone).format(fmt)
            val end = date.atStartOfDay().plusMinutes(t.endMinute.toLong()).atZone(zone).format(fmt)
            appendLine("BEGIN:VEVENT")
            appendLine("UID:daytimeline-${t.id}-$start@local")
            appendLine("DTSTAMP:$stamp")
            appendLine("DTSTART:$start")
            appendLine("DTEND:$end")
            appendLine("SUMMARY:${escapeIcs(t.title)}")
            if (t.notes.isNotBlank()) appendLine("DESCRIPTION:${escapeIcs(t.notes)}")
            appendLine("END:VEVENT")
        }
        appendLine("END:VCALENDAR")
    }
}

private fun escapeIcs(value: String) = value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

@Composable private fun SearchSummary(tasks: List<TaskModel>, notes: List<QuickNote>, habits: List<Habit>) {
    Text("${tasks.size} tasks · ${notes.size} notes · ${habits.size} habits", color = ToolMuted, style = MaterialTheme.typography.labelLarge)
}

@Composable private fun SearchTask(t: TaskModel) { ResultCard(Icons.Default.CheckCircle, t.title, "Task · ${clockText(t.startMinute)}–${clockText(t.endMinute)}${if (t.completed) " · completed" else ""}") }
@Composable private fun SearchNote(n: QuickNote) { ResultCard(Icons.Default.Description, n.title, "Note · ${n.tags.ifBlank { "No tags" }}") }
@Composable private fun SearchHabit(h: Habit) { ResultCard(Icons.Default.Loop, h.name, "Habit · ${h.completedDates.size} recorded days") }

@Composable private fun ResultCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Surface(RoundedCornerShape(18.dp), color = ToolCard) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = ToolSage); Spacer(Modifier.width(12.dp)); Column { Text(title); Text(subtitle, color = ToolMuted, style = MaterialTheme.typography.bodySmall) } } }
}

@Composable private fun OfflinePrinciples() {
    Surface(RoundedCornerShape(24.dp), color = Color(0xFFE9EEE9)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text("Private by design", style = MaterialTheme.typography.titleMedium); Text("Core planning data, habits, notes, focus history and reviews stay on this device. There is no required account, server, subscription or API key.", color = ToolMuted); Text("Export is explicit: nothing leaves the device unless you choose a destination.", color = ToolMuted) } }
}

private fun clockText(minute: Int): String = String.format("%02d:%02d", minute / 60, minute % 60)
