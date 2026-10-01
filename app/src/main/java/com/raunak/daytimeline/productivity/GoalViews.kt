package com.raunak.daytimeline.productivity

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.features.*
import com.raunak.daytimeline.ui.*
import java.time.LocalDate

@Composable
private fun paceColor(p: Pace) = when (p) {
    Pace.DONE, Pace.AHEAD -> Chronora.colors.good
    Pace.ON_TRACK -> MaterialTheme.colorScheme.primary
    Pace.BEHIND, Pace.OVERDUE -> Chronora.colors.bad
    Pace.NO_DEADLINE -> Chronora.muted
}

private fun num(v: Double) = if (v >= 10) "%.0f".format(v) else "%.1f".format(v).removeSuffix(".0")

/** "3/day", or "1 every 4 days" when the rate is below one a day. */
private fun rate(v: Double) = if (v <= 0.0) "nothing more" else if (v >= 0.95) "${num(v)}/day" else "1 every ${Math.round(1 / v)} days"

/** Every goal as a card, plus an add button; used by the Productivity tab and the Power Center. */
@Composable
fun GoalsSection(goals: List<OfflineGoal>, store: OfflineProductivityStore) {
    var editing by remember { mutableStateOf<OfflineGoal?>(null) }
    var adding by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        val active = goals.filter { GoalEngine.status(it, today).pace != Pace.DONE }
        val done = goals - active.toSet()
        HeroCard {
            Text("Goals", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelLarge)
            Text("${active.size} in progress · ${done.size} done", color = Chronora.colors.onHero, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val behind = active.count { GoalEngine.status(it, today).pace == Pace.BEHIND }
            Text(if (behind == 0) "Everything with a deadline is on pace." else "$behind behind pace — see what's needed per day below.", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.bodySmall)
        }
        SectionHeader("Goals") { TextButton(onClick = { adding = true }) { Text("New goal") } }
        if (goals.isEmpty()) EmptyState("No goals yet", "Set a number to reach by a date (pages, problems, kg, ₹) or a project with milestones.")
        active.forEach { GoalCard(it, store, today) { editing = it } }
        if (done.isNotEmpty()) {
            Text("COMPLETED", style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
            done.forEach { GoalCard(it, store, today) { editing = it } }
        }
    }
    if (adding) GoalEditor(null, { store.saveGoal(it); adding = false }) { adding = false }
    editing?.let { g -> GoalEditor(g, { store.saveGoal(it); editing = null }, onDelete = { store.deleteGoal(g.id); editing = null }) { editing = null } }
}

@Composable
fun GoalCard(g: OfflineGoal, store: OfflineProductivityStore, today: LocalDate, onEdit: () -> Unit) {
    val st = remember(g, today) { GoalEngine.status(g, today) }
    val color = Color(g.color.takeIf { it != 0L } ?: 0xFF55786A)
    val (value, target) = GoalEngine.progress(g)
    var custom by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(Spacing.inner), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StrengthRing(st.percent, color, 52.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(g.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text("$value / $target" + (if (GoalEngine.kind(g) == GoalKind.PROJECT) " milestones" else g.unit.takeIf { it.isNotBlank() }?.let { " $it" } ?: "") +
                        (g.deadline?.let { " · by " + relativeDay(LocalDate.parse(it), today) } ?: ""), style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                    if (g.why.isNotBlank()) Text(g.why, style = MaterialTheme.typography.bodySmall, color = color)
                }
                Pill(st.pace.label, paceColor(st.pace))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit goal") }
            }
            val hint = when (st.pace) {
                Pace.BEHIND -> "Should be at ${st.expected} by now · need ${rate(st.neededPerDay ?: 0.0)} for ${st.daysLeft} more days"
                Pace.ON_TRACK, Pace.AHEAD -> "Keep ${rate(st.neededPerDay ?: 0.0)} to finish on time" + (st.projectedFinish?.let { " · at this rate done ${relativeDay(it, today)}" } ?: "")
                Pace.NO_DEADLINE -> if (st.recentPerDay > 0) "${rate(st.recentPerDay)} lately" + (st.projectedFinish?.let { " · done around ${relativeDay(it, today)}" } ?: "") else "Log progress to see your pace"
                Pace.OVERDUE -> "Deadline passed · ${target - value} left"
                Pace.DONE -> "Completed 🎉"
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = paceColor(st.pace))
            if (GoalEngine.kind(g) == GoalKind.TARGET) {
                Sparkline(GoalEngine.history(g, today), target, color)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { store.logGoal(g.id, -1) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("−1") }
                    Button(onClick = { store.logGoal(g.id, 1) }, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("+1") }
                    val step = (target / 20).coerceAtLeast(5)
                    OutlinedButton(onClick = { store.logGoal(g.id, step) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("+$step") }
                    TextButton(onClick = { custom = true }) { Text("Log…") }
                }
            } else {
                GoalEngine.milestones(g).forEachIndexed { i, m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(i in GoalEngine.milestoneDone(g), { store.toggleGoalMilestone(g.id, i) })
                        Text(m, Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (custom) {
        var text by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { custom = false }, title = { Text("Log progress") }, text = {
            OutlinedTextField(text, { text = it.filter { c -> c.isDigit() || c == '-' } }, label = { Text("Amount (negative to undo)") }, singleLine = true)
        }, confirmButton = { Button(enabled = text.toIntOrNull() != null, onClick = { store.logGoal(g.id, text.toInt()); custom = false }) { Text("Log") } },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel") } })
    }
}

@Composable
private fun Sparkline(values: List<Int>, target: Int, color: Color) {
    val grid = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        if (values.isEmpty()) return@Canvas
        val max = maxOf(target, values.max(), 1).toFloat()
        drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), 2f)
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v -> val x = i * step; val y = size.height - size.height * v / max; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        drawPath(path, color, style = Stroke(5f, cap = StrokeCap.Round))
    }
}

@Composable
fun GoalEditor(initial: OfflineGoal?, onSave: (OfflineGoal) -> Unit, onDelete: (() -> Unit)? = null, close: () -> Unit) {
    val base = initial ?: OfflineGoal(0, "", 0, 10, null, emptyList(), false)
    var title by remember { mutableStateOf(base.title) }
    var why by remember { mutableStateOf(base.why) }
    var kind by remember { mutableStateOf(GoalEngine.kind(base)) }
    var target by remember { mutableStateOf(base.target.toString()) }
    var unit by remember { mutableStateOf(base.unit) }
    var deadline by remember { mutableStateOf(base.deadline ?: "") }
    var milestones by remember { mutableStateOf(GoalEngine.milestones(base).joinToString("\n")) }
    var color by remember { mutableLongStateOf(base.color.takeIf { it != 0L } ?: HabitColors.first()) }
    val deadlineOk = deadline.isBlank() || runCatching { LocalDate.parse(deadline.trim()) }.isSuccess
    AlertDialog(onDismissRequest = close, title = { Text(if (initial == null) "New goal" else "Edit goal") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Goal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(why, { why = it }, label = { Text("Why (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PillTabs(GoalKind.values().map { it.label }, GoalKind.values().indexOf(kind)) { kind = GoalKind.values()[it] }
            if (kind == GoalKind.TARGET) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Target") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit (pages, km…)") }, singleLine = true, modifier = Modifier.weight(1f))
            } else OutlinedTextField(milestones, { milestones = it }, label = { Text("Milestones, one per line") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(deadline, { deadline = it }, label = { Text("Deadline YYYY-MM-DD (optional)") }, singleLine = true, isError = !deadlineOk, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7L, 30L, 90L, 180L).forEach { d -> AssistChip(onClick = { deadline = LocalDate.now().plusDays(d).toString() }, label = { Text(if (d < 30) "${d}d" else "${d / 30}mo") }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HabitColors.forEach { c -> Box(Modifier.size(24.dp).clip(CircleShape).background(Color(c)).border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape).clickable { color = c }) }
            }
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete goal", color = Chronora.colors.bad) }
        }
    }, confirmButton = {
        Button(enabled = title.isNotBlank() && deadlineOk && (kind == GoalKind.PROJECT || (target.toIntOrNull() ?: 0) > 0), onClick = {
            val ms = milestones.lines().map { it.trim() }.filter { it.isNotBlank() }
            onSave(base.copy(title = title, why = why.trim(), kind = kind.name, target = if (kind == GoalKind.PROJECT) ms.size.coerceAtLeast(1) else target.toInt(), unit = unit.trim(),
                deadline = deadline.trim().ifBlank { null }, milestones = if (kind == GoalKind.PROJECT) ms else GoalEngine.milestones(base),
                milestoneDone = GoalEngine.milestoneDone(base).filter { it < ms.size || kind == GoalKind.TARGET }.toSet(), color = color))
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
