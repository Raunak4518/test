package com.raunak.daytimeline.productivity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raunak.daytimeline.PlannerViewModel
import com.raunak.daytimeline.data.PomodoroStateEntity
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.pro.FocusGarden
import com.raunak.daytimeline.pro.GardenStore
import com.raunak.daytimeline.ui.*
import java.time.LocalDate

private fun phaseLabel(p: PomodoroStateEntity) = when (p.phase) {
    "FOCUS" -> "Focus · ${p.cycleIndex}/${p.cyclesPerRound}"
    "SHORT_BREAK" -> "Short break"
    "LONG_BREAK" -> "Long break"
    else -> "Ready"
}

private fun phaseSeconds(p: PomodoroStateEntity) = 60L * when (p.phase) {
    "SHORT_BREAK" -> p.shortBreakMinutes
    "LONG_BREAK" -> p.longBreakMinutes
    else -> p.focusMinutes
}

/**
 * Focus timer in the style of the best Pomodoro apps: progress ring, growing plant, round dots,
 * task link, one-tap presets, editable lengths, and today's / this week's focus stats.
 */
@Composable
fun FocusPanel(pomo: PomodoroStateEntity, tasks: List<TaskModel>, vm: PlannerViewModel) {
    val context = LocalContext.current
    val garden = remember(context) { GardenStore(context.applicationContext) }
    val today = LocalDate.now()
    val sessions = remember(pomo.phase, pomo.cycleIndex) { garden.sessions() }
    val summary = remember(sessions) { FocusGarden.summarize(sessions, today) }
    val idle = pomo.phase == "IDLE"
    val total = phaseSeconds(pomo).coerceAtLeast(1)
    val remaining = if (idle) total else pomo.remainingSeconds
    val progress = if (idle) 0f else (1f - remaining.toFloat() / total).coerceIn(0f, 1f)
    val isBreak = pomo.phase.endsWith("BREAK")
    val ringColor = if (isBreak) Chronora.colors.good else MaterialTheme.colorScheme.primary
    val linked = tasks.firstOrNull { it.id == pomo.taskId }

    ScreenList {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(phaseLabel(pomo), style = MaterialTheme.typography.labelLarge, color = ringColor)
                    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                        val track = MaterialTheme.colorScheme.surfaceVariant
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 16.dp.toPx()
                            val arc = Size(size.width - stroke, size.height - stroke)
                            val tl = Offset(stroke / 2, stroke / 2)
                            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(stroke))
                            drawArc(ringColor, -90f, 360f * progress, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val focusedMin = if (pomo.phase == "FOCUS") ((total - remaining) / 60).toInt() else 0
                            Text(if (isBreak) "☕" else FocusGarden.plantFor(if (idle) pomo.focusMinutes else focusedMin).emoji, fontSize = 34.sp)
                            Text("%02d:%02d".format(remaining / 60, remaining % 60), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                            Text(linked?.title ?: if (idle) "${pomo.focusMinutes} min focus" else "Free focus", style = MaterialTheme.typography.bodySmall, color = Chronora.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..pomo.cyclesPerRound.coerceIn(1, 12)).forEach { i ->
                            val filled = !idle && (i < pomo.cycleIndex || (i == pomo.cycleIndex && isBreak))
                            val current = !idle && i == pomo.cycleIndex && pomo.phase == "FOCUS"
                            Box(Modifier.size(if (current) 12.dp else 10.dp).clip(CircleShape).background(if (filled || current) ringColor else MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!idle) FilledTonalIconButton(onClick = vm::resetPomodoro) { Icon(Icons.Default.Stop, "Stop") }
                        Button(onClick = { when { idle -> vm.startPomodoro(null); pomo.running -> vm.pausePomodoro(); else -> vm.resumePomodoro() } }, modifier = Modifier.height(52.dp).widthIn(min = 140.dp)) {
                            Icon(if (pomo.running) Icons.Default.Pause else Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp))
                            Text(when { idle -> "Start focus"; pomo.running -> "Pause"; else -> "Resume" })
                        }
                        if (!idle) FilledTonalIconButton(onClick = vm::skipPomodoro) { Icon(Icons.Default.SkipNext, "Skip") }
                    }
                    if (!idle) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { vm.extendPomodoro(5) }, label = { Text("+5 min") })
                        if (pomo.phase == "FOCUS") Text("Stopping early withers this plant", style = MaterialTheme.typography.labelSmall, color = Chronora.muted, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                StatTile("Today", "${summary.todayMinutes}m", Modifier.weight(1f), "${sessions.count { it.completed && it.date == today.toString() }} sessions")
                StatTile("Streak", "${summary.focusDayStreak}d", Modifier.weight(1f), "best ${summary.bestStreak}d")
                StatTile("Level", "${summary.level}", Modifier.weight(1f), "${summary.plants.size} plants")
            }
        }
        item { WeekFocusChart(sessions.filter { it.completed }.groupBy { it.date }.mapValues { e -> e.value.sumOf { it.minutes } }, today) }
        item { TimerSettings(pomo, vm, idle) }
        val focusable = tasks.filter { !it.completed }.sortedBy { it.startMinute }
        if (focusable.isNotEmpty()) item {
            SectionCard("Focus on a task", "Sessions are credited to the task you pick") {
                focusable.take(8).forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontWeight = if (t.id == pomo.taskId) FontWeight.Bold else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("%02d:%02d · %dm".format(t.startMinute / 60, t.startMinute % 60, t.endMinute - t.startMinute), style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                        }
                        IconButton(onClick = { vm.startPomodoro(t.id) }) { Icon(Icons.Default.PlayArrow, "Focus on ${t.title}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekFocusChart(byDate: Map<String, Int>, today: LocalDate) {
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val values = days.map { byDate[it.toString()] ?: 0 }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(30)
    val bar = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.surfaceVariant
    SectionCard("This week", "${values.sum() / 60}h ${values.sum() % 60}m focused · daily average ${values.sum() / 7}m") {
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (values[i] > 0) Text("${values[i]}", fontSize = 10.sp, color = Chronora.muted)
                    Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().height((80f * values[i] / max).coerceAtLeast(4f).dp).clip(RoundedCornerShape(6.dp)).background(if (values[i] > 0) bar else dim))
                    Text(d.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = if (d == today) bar else Chronora.muted)
                }
            }
        }
    }
}

@Composable
private fun TimerSettings(pomo: PomodoroStateEntity, vm: PlannerViewModel, idle: Boolean) {
    var open by remember { mutableStateOf(false) }
    fun set(f: Int = pomo.focusMinutes, s: Int = pomo.shortBreakMinutes, l: Int = pomo.longBreakMinutes, c: Int = pomo.cyclesPerRound) = vm.configurePomodoro(f, s, l, c)
    SectionCard("Timer", "${pomo.focusMinutes} / ${pomo.shortBreakMinutes} min · long break ${pomo.longBreakMinutes} min every ${pomo.cyclesPerRound}", action = { TextButton(onClick = { open = !open }) { Text(if (open) "Done" else "Customise") } }) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(25, 5, 15), Triple(50, 10, 20), Triple(90, 20, 30), Triple(15, 3, 10)).forEach { (f, s, l) ->
                FilterChip(selected = pomo.focusMinutes == f && pomo.shortBreakMinutes == s, onClick = { set(f, s, l) }, label = { Text("$f / $s") })
            }
        }
        if (open) {
            Stepper("Focus", "${pomo.focusMinutes} min", { set(f = pomo.focusMinutes - 5) }, { set(f = pomo.focusMinutes + 5) })
            Stepper("Short break", "${pomo.shortBreakMinutes} min", { set(s = pomo.shortBreakMinutes - 1) }, { set(s = pomo.shortBreakMinutes + 1) })
            Stepper("Long break", "${pomo.longBreakMinutes} min", { set(l = pomo.longBreakMinutes - 5) }, { set(l = pomo.longBreakMinutes + 5) })
            Stepper("Focus sessions per round", "${pomo.cyclesPerRound}", { set(c = pomo.cyclesPerRound - 1) }, { set(c = pomo.cyclesPerRound + 1) })
            if (!idle) Text("New lengths apply from the next phase.", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
        }
    }
}
