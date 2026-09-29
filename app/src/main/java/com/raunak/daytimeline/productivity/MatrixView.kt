package com.raunak.daytimeline.productivity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.PlannerViewModel
import com.raunak.daytimeline.domain.EisenhowerEngine
import com.raunak.daytimeline.domain.Quadrant
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.features.OfflineSettings
import com.raunak.daytimeline.ui.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
private fun quadrantColor(q: Quadrant): Color = when (q) {
    Quadrant.DO -> Chronora.colors.bad
    Quadrant.SCHEDULE -> MaterialTheme.colorScheme.primary
    Quadrant.DELEGATE -> Chronora.colors.warn
    Quadrant.DROP -> Chronora.muted
}

/**
 * Eisenhower matrix of open tasks. Rules (what counts as urgent / important, how far ahead to look)
 * are editable right here and saved to settings.
 */
@Composable
fun EisenhowerMatrix(tasks: List<TaskModel>, vm: PlannerViewModel, settings: OfflineSettings, saveSettings: ((OfflineSettings) -> OfflineSettings) -> Unit, onOpen: (TaskModel) -> Unit) {
    val today = LocalDate.now()
    val urgentDays = settings.matrixUrgentDays.coerceAtLeast(0)
    val important = settings.matrixImportantPriority.takeIf { it in 1..3 } ?: 2
    val horizon = settings.matrixHorizonDays.takeIf { it > 0 } ?: 14
    val groups = remember(tasks, urgentDays, important, horizon) { EisenhowerEngine.group(tasks, today, urgentDays, important, horizon) }
    var rules by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        SectionHeader("Priority matrix") { TextButton(onClick = { rules = !rules }) { Text(if (rules) "Done" else "Rules") } }
        if (rules) SectionCard("Matrix rules") {
            Stepper("Urgent if due within", if (urgentDays == 0) "today" else "$urgentDays d", { saveSettings { it.copy(matrixUrgentDays = (urgentDays - 1).coerceAtLeast(0)) } }, { saveSettings { it.copy(matrixUrgentDays = (urgentDays + 1).coerceAtMost(30)) } })
            Stepper("Important from priority", "$important", { saveSettings { it.copy(matrixImportantPriority = (important - 1).coerceAtLeast(1)) } }, { saveSettings { it.copy(matrixImportantPriority = (important + 1).coerceAtMost(3)) } })
            Stepper("Look ahead", "$horizon d", { saveSettings { it.copy(matrixHorizonDays = (horizon - 1).coerceAtLeast(1)) } }, { saveSettings { it.copy(matrixHorizonDays = (horizon + 1).coerceAtMost(90)) } })
        }
        listOf(Quadrant.DO to Quadrant.SCHEDULE, Quadrant.DELEGATE to Quadrant.DROP).forEach { (a, b) ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                QuadrantCard(a, groups[a].orEmpty(), today, important, vm, onOpen, Modifier.weight(1f).fillMaxHeight())
                QuadrantCard(b, groups[b].orEmpty(), today, important, vm, onOpen, Modifier.weight(1f).fillMaxHeight())
            }
        }
        Text("Tick to complete · tap a task to make it more or less important", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
    }
}

@Composable
private fun QuadrantCard(q: Quadrant, tasks: List<TaskModel>, today: LocalDate, important: Int, vm: PlannerViewModel, onOpen: (TaskModel) -> Unit, modifier: Modifier) {
    val color = quadrantColor(q)
    var showAll by remember { mutableStateOf(false) }
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(q.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${tasks.size}", style = MaterialTheme.typography.labelLarge, color = color)
            }
            Text(q.hint, style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
            if (tasks.isEmpty()) Text("Clear", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
            (if (showAll) tasks else tasks.take(5)).forEach { t -> MatrixTask(t, today, important, color, vm, onOpen) }
            if (tasks.size > 5) TextButton(onClick = { showAll = !showAll }, contentPadding = PaddingValues(0.dp)) { Text(if (showAll) "Less" else "+${tasks.size - 5} more") }
        }
    }
}

@Composable
private fun MatrixTask(t: TaskModel, today: LocalDate, important: Int, color: Color, vm: PlannerViewModel, onOpen: (TaskModel) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val days = ChronoUnit.DAYS.between(today, t.date)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = false, onCheckedChange = { vm.toggleComplete(t, true) }, modifier = Modifier.size(32.dp), colors = CheckboxDefaults.colors(uncheckedColor = color))
        Box(Modifier.weight(1f)) {
            Column(Modifier.clickable { menu = true }) {
                Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(when { days < 0 -> "${-days}d overdue"; days == 0L -> "Today"; days == 1L -> "Tomorrow"; else -> "In ${days}d" } + " · P${t.priority}",
                    style = MaterialTheme.typography.labelSmall, color = if (days < 0) Chronora.colors.bad else Chronora.muted)
            }
            DropdownMenu(menu, { menu = false }) {
                if (t.priority < important) DropdownMenuItem(text = { Text("Make important") }, onClick = { menu = false; vm.setPriority(t, important) })
                else DropdownMenuItem(text = { Text("Make less important") }, onClick = { menu = false; vm.setPriority(t, important - 1) })
                DropdownMenuItem(text = { Text("Open / edit") }, onClick = { menu = false; onOpen(t) })
                DropdownMenuItem(text = { Text("Go to its day") }, onClick = { menu = false; vm.selectDate(t.date) })
            }
        }
    }
}
