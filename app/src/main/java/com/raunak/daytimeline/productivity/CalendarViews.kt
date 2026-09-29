package com.raunak.daytimeline.productivity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.PlannerViewModel
import com.raunak.daytimeline.campus.AttendanceEngine
import com.raunak.daytimeline.campus.CampusStore
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.ui.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Month calendar: each day shows task dots (coloured by priority), a class marker and a deadline
 * marker; the chosen day's tasks and classes are listed underneath.
 */
@Composable
fun CalendarPage(vm: PlannerViewModel, initial: LocalDate, onEdit: (TaskModel) -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    val agenda by vm.agenda.collectAsStateWithLifecycle()
    val campus by remember { CampusStore.get(context.applicationContext).data }.collectAsStateWithLifecycle()
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var selected by remember { mutableStateOf(initial) }
    val today = LocalDate.now()
    val byDay = remember(agenda) { agenda.groupBy { it.date } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                ChronoraTopBar("Calendar", close, subtitle = month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "Previous month") }
                    IconButton(onClick = { month = YearMonth.from(today); selected = today }) { Icon(Icons.Default.Today, "Today") }
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "Next month") }
                }
                ScreenList {
                    item {
                        Card {
                            Column(Modifier.padding(10.dp)) {
                                Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = Chronora.muted) } }
                                val first = month.atDay(1)
                                val lead = first.dayOfWeek.value - 1
                                val cells = ((lead + month.lengthOfMonth() + 6) / 7) * 7
                                (0 until cells / 7).forEach { row ->
                                    Row(Modifier.fillMaxWidth()) {
                                        (0 until 7).forEach { col ->
                                            val i = row * 7 + col - lead
                                            if (i < 0 || i >= month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(54.dp))
                                            else {
                                                val d = first.plusDays(i.toLong())
                                                DayCell(d, d == selected, d == today, byDay[d].orEmpty(),
                                                    classes = AttendanceEngine.occurrences(campus, d).size,
                                                    deadlines = campus.deadlines.count { !it.done && it.date == d.toString() },
                                                    Modifier.weight(1f)) { selected = d }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        SectionHeader(relativeDay(selected, today) + " · " + selected.format(DateTimeFormatter.ofPattern("d MMM"))) {
                            TextButton(onClick = { vm.selectDate(selected); close() }) { Text("Open day") }
                        }
                    }
                    val classes = AttendanceEngine.occurrences(campus, selected).sortedBy { it.start }
                    items(classes, key = { "c" + it.key }) { o ->
                        val s = campus.subjects.firstOrNull { it.id == o.subjectId }
                        Card { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(s?.colorHex ?: 0xFF55786A)))
                            Text("  ${hm(o.start)}–${hm(o.end)}  ${s?.name ?: "Class"}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(o.type.label + (o.room.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                        } }
                    }
                    items(campus.deadlines.filter { !it.done && it.date == selected.toString() }, key = { "d${it.id}" }) { dl ->
                        Card { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, null, tint = Chronora.colors.warn, modifier = Modifier.size(18.dp))
                            Text("  ${dl.label}: ${dl.title}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("by ${hm(dl.minute)}", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                        } }
                    }
                    val tasks = byDay[selected].orEmpty()
                    items(tasks, key = { "t${it.id}" }) { t -> TaskCard(t, vm, { onEdit(t) }, today = today) }
                    if (tasks.isEmpty() && classes.isEmpty()) item { EmptyState("Nothing planned", "Open the day to add tasks, or use quick add.") }
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, selected: Boolean, today: Boolean, tasks: List<TaskModel>, classes: Int, deadlines: Int, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier.height(54.dp).padding(2.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) primary.copy(alpha = .14f) else Color.Transparent)
            .then(if (today) Modifier.border(1.5.dp, primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text("${d.dayOfMonth}", fontWeight = if (today || selected) FontWeight.Bold else null, color = if (today) primary else MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(8.dp), verticalAlignment = Alignment.CenterVertically) {
            tasks.sortedByDescending { it.priority }.take(3).forEach { t -> Box(Modifier.size(5.dp).clip(CircleShape).background(if (t.completed) Chronora.muted.copy(alpha = .4f) else TaskPriority.color(t.priority))) }
            if (classes > 0) Box(Modifier.size(width = 7.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(Chronora.colors.good))
            if (deadlines > 0) Text("!", fontSize = 9.sp, color = Chronora.colors.warn, fontWeight = FontWeight.Bold)
        }
    }
}
