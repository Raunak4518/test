package com.raunak.daytimeline.productivity

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.raunak.daytimeline.PlannerViewModel
import com.raunak.daytimeline.data.PomodoroStateEntity
import com.raunak.daytimeline.domain.PomodoroEngine
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.pro.*
import com.raunak.daytimeline.ui.*
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun phaseLabel(p: PomodoroStateEntity) = when (p.phase) {
    PomodoroEngine.FLOW -> "Flow focus"
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

private fun mmss(s: Long) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
private fun mins(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

/**
 * Focus timer built to match the best apps: Pomodoro or Flow (count-up) mode, tags and an intention,
 * distraction logging, auto-start choices, daily goal, estimated vs actual pomodoros per task,
 * deep-focus blocking, reflection after each session, zen mode, and full reports.
 */
@Composable
fun FocusPanel(pomo: PomodoroStateEntity, tasks: List<TaskModel>, allTasks: List<TaskModel>, vm: PlannerViewModel) {
    val context = LocalContext.current
    val garden = remember(context) { GardenStore(context.applicationContext) }
    val prefs = remember(context) { FocusPrefs(context.applicationContext) }
    var cfg by remember { mutableStateOf(prefs.config) }
    fun save(t: (FocusConfig) -> FocusConfig) { prefs.update(t); cfg = prefs.config }
    var refresh by remember { mutableIntStateOf(0) }
    val sessions = remember(pomo.phase, pomo.cycleIndex, pomo.running, refresh) { garden.sessions() }
    val today = LocalDate.now()
    val summary = remember(sessions) { FocusGarden.summarize(sessions, today) }
    var tag by remember { mutableStateOf(prefs.tag) }
    var intention by remember { mutableStateOf(prefs.intention) }
    var distractions by remember { mutableIntStateOf(prefs.interruptions().size) }
    var zen by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pomo.running, pomo.phase) { while (pomo.running) { now = System.currentTimeMillis(); delay(1000) } ; distractions = prefs.interruptions().size }

    val idle = pomo.phase == "IDLE"
    val flow = pomo.phase == PomodoroEngine.FLOW
    val waiting = PomodoroEngine.waiting(pomo)
    val focusing = pomo.phase == "FOCUS" || flow
    val isBreak = pomo.phase.endsWith("BREAK")
    val total = phaseSeconds(pomo).coerceAtLeast(1)
    val shown = when {
        idle -> if (cfg.flow) 0 else total
        flow -> PomodoroEngine.flowElapsed(pomo, now)
        else -> pomo.remainingSeconds
    }
    val progress = when {
        idle || waiting -> 0f
        flow -> ((shown % 3600) / 3600f)
        else -> (1f - shown.toFloat() / total).coerceIn(0f, 1f)
    }
    val ringColor = if (isBreak) Chronora.colors.good else MaterialTheme.colorScheme.primary
    val linked = allTasks.firstOrNull { it.id == pomo.taskId }

    val view = LocalView.current
    DisposableEffect(cfg.keepScreenOn, pomo.running, focusing) {
        view.keepScreenOn = cfg.keepScreenOn && pomo.running
        onDispose { view.keepScreenOn = false }
    }

    val todayReport = remember(sessions) { FocusStats.report(sessions, today, today) }
    val pending = remember(sessions, cfg.reflect) {
        if (!cfg.reflect) null else sessions.lastOrNull { it.completed && it.startedAt > prefs.reflectedUpTo && it.rating == 0 && System.currentTimeMillis() - (it.startedAt + it.minutes * 60_000L) < 2 * 3_600_000L }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                            listOf("POMODORO" to "Pomodoro", "FLOW" to "Flow").forEachIndexed { i, (k, l) ->
                                SegmentedButton(cfg.mode == k, { if (idle) save { it.copy(mode = k) } }, SegmentedButtonDefaults.itemShape(i, 2), enabled = idle || cfg.mode == k) { Text(l) }
                            }
                        }
                        IconButton(onClick = { zen = true }) { Icon(Icons.Default.Fullscreen, "Zen mode") }
                    }
                    Text(if (waiting) phaseLabel(pomo) + " · ready" else if (idle && cfg.flow) "Flow — count up, break when you're done" else phaseLabel(pomo), style = MaterialTheme.typography.labelLarge, color = ringColor)
                    Box(Modifier.size(236.dp), contentAlignment = Alignment.Center) {
                        val track = MaterialTheme.colorScheme.surfaceVariant
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 16.dp.toPx()
                            val arc = Size(size.width - stroke, size.height - stroke)
                            val tl = Offset(stroke / 2, stroke / 2)
                            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(stroke))
                            drawArc(ringColor, -90f, 360f * progress, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val focusedMin = if (flow) (shown / 60).toInt() else if (pomo.phase == "FOCUS") ((total - shown) / 60).toInt() else 0
                            Text(if (isBreak) "☕" else FocusGarden.plantFor(if (idle) (if (cfg.flow) cfg.flowMinMinutes else pomo.focusMinutes) else focusedMin).emoji, fontSize = 34.sp)
                            Text(mmss(shown), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                            Text(linked?.title ?: prefs.intention.takeIf { !idle && it.isNotBlank() } ?: if (idle) (if (cfg.flow) "open-ended focus" else "${pomo.focusMinutes} min focus") else tag.ifBlank { "Free focus" },
                                style = MaterialTheme.typography.bodySmall, color = Chronora.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                        }
                    }
                    if (!flow && !cfg.flow || pomo.phase in listOf("FOCUS", "SHORT_BREAK", "LONG_BREAK")) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..pomo.cyclesPerRound.coerceIn(1, 12)).forEach { i ->
                            val filled = !idle && (i < pomo.cycleIndex || (i == pomo.cycleIndex && isBreak))
                            val current = !idle && i == pomo.cycleIndex && pomo.phase == "FOCUS"
                            Box(Modifier.size(if (current) 12.dp else 10.dp).clip(CircleShape).background(if (filled || current) ringColor else MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                    if (idle) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            cfg.tags.forEach { t -> FilterChip(tag == t, { tag = if (tag == t) "" else t; prefs.tag = tag }, label = { Text(t) }) }
                        }
                        OutlinedTextField(intention, { intention = it; prefs.intention = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("What will you get done? (optional)") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!idle) FilledTonalIconButton(onClick = vm::resetPomodoro) { Icon(Icons.Default.Stop, if (focusing) "Give up" else "Stop") }
                        Button(onClick = {
                            when {
                                idle -> { prefs.tag = tag; prefs.intention = intention.trim(); vm.startPomodoro(null) }
                                pomo.running -> vm.pausePomodoro()
                                else -> vm.resumePomodoro()
                            }
                        }, modifier = Modifier.height(54.dp).widthIn(min = 150.dp)) {
                            Icon(if (pomo.running) Icons.Default.Pause else Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp))
                            Text(when {
                                idle -> if (cfg.flow) "Start flow" else "Start focus"
                                pomo.running -> "Pause"
                                waiting -> if (isBreak) "Start break" else "Start focus"
                                else -> "Resume"
                            })
                        }
                        if (!idle) FilledTonalIconButton(onClick = vm::skipPomodoro) { Icon(if (flow) Icons.Default.Coffee else Icons.Default.SkipNext, if (flow) "Take a break" else "Skip") }
                    }
                    if (flow) Text("Break earned so far: ${PomodoroEngine.flowBreakMinutes(shown, cfg.flowBreakDivisor)} min · tap ☕ when your focus fades", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                    if (!idle) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (focusing && pomo.running) AssistChip(onClick = { vm.logDistraction(); distractions = prefs.interruptions().size }, leadingIcon = { Icon(Icons.Default.NotificationsPaused, null, Modifier.size(18.dp)) }, label = { Text("Distracted" + if (distractions > 0) " · $distractions" else "") })
                        if (!flow) AssistChip(onClick = { vm.extendPomodoro(5) }, label = { Text("+5 min") })
                    }
                    if (focusing && !idle) Text(if (cfg.strict) "Deep focus on: other apps are blocked until this ends" else "Stopping early withers this plant", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
                }
            }
        }
        item {
            val goal = cfg.dailyGoalMinutes
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Today", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelLarge)
                        Text("${mins(todayReport.minutes)} of ${mins(goal)}", color = Chronora.colors.onHero, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("🔥 ${summary.focusDayStreak}d", color = Chronora.colors.onHero, fontWeight = FontWeight.Bold)
                        Text("Level ${summary.level}", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                LinearProgressIndicator(progress = { (todayReport.minutes.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Chronora.colors.heroAccent, trackColor = Color.White.copy(alpha = .15f))
                Text("${todayReport.sessions} sessions · ${todayReport.interruptions} distractions" + (todayReport.averageRating?.let { " · rated ${"%.1f".format(it)}/5" } ?: "") + if (todayReport.minutes >= goal) " · goal reached 🎉" else "",
                    color = Chronora.colors.heroMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        val open = tasks.filter { !it.completed }.sortedBy { it.startMinute }
        if (open.isNotEmpty()) item {
            val done = FocusStats.pomodorosByTask(sessions)
            SectionCard("Focus on a task", "🍅 done / estimated — set estimates with − +") {
                open.take(10).forEach { t ->
                    val est = cfg.estimate(t.id)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontWeight = if (t.id == pomo.taskId) FontWeight.Bold else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("🍅 ${done[t.id] ?: 0}" + (if (est > 0) " / $est" else "") + " · %02d:%02d".format(t.startMinute / 60, t.startMinute % 60), style = MaterialTheme.typography.bodySmall,
                                color = if (est > 0 && (done[t.id] ?: 0) > est) Chronora.colors.warn else Chronora.muted)
                        }
                        TextButton(onClick = { save { c -> c.copy(taskEstimates = c.taskEstimates + (t.id.toString() to (est - 1).coerceAtLeast(0))) } }, contentPadding = PaddingValues(0.dp)) { Text("−") }
                        TextButton(onClick = { save { c -> c.copy(taskEstimates = c.taskEstimates + (t.id.toString() to est + 1)) } }, contentPadding = PaddingValues(0.dp)) { Text("+") }
                        IconButton(onClick = { prefs.tag = tag; prefs.intention = t.title; vm.startPomodoro(t.id) }, enabled = idle) { Icon(Icons.Default.PlayArrow, "Focus on ${t.title}") }
                    }
                }
            }
        }
        item { FocusReports(sessions, allTasks, today) }
        item { FocusHistory(sessions, garden, cfg) { refresh++ } }
        item { TimerSettings(pomo, vm, cfg, idle) { save(it) } }
    }

    pending?.let { s -> ReflectDialog(s, onSave = { rating, note -> garden.update(s.copy(rating = rating, note = note.ifBlank { null })); prefs.reflectedUpTo = s.startedAt; refresh++ }) { prefs.reflectedUpTo = s.startedAt; refresh++ } }
    if (zen) ZenMode(pomo, shown, progress, ringColor, cfg.keepScreenOn) { zen = false }
}

/** Engross/Session-style check-in after a focus session. */
@Composable
private fun ReflectDialog(s: GardenSession, onSave: (Int, String) -> Unit, dismiss: () -> Unit) {
    var rating by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("${s.minutes} min done — how was it?") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOfNotNull(s.tag, s.intention).joinToString(" · ").takeIf { it.isNotBlank() }?.let { Text(it, color = Chronora.muted) }
            if (s.interruptions > 0) Text("${s.interruptions} distraction(s) logged", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (1..5).forEach { n -> Text(if (n <= rating) "★" else "☆", fontSize = 34.sp, color = Chronora.colors.warn, modifier = Modifier.clip(CircleShape).clickable { rating = n }.padding(4.dp)) }
            }
            Text(listOf("", "Scattered", "Meh", "Okay", "Focused", "Deep flow")[rating], Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(note, { note = it }, placeholder = { Text("What got done? What distracted you?") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { Button(enabled = rating > 0, onClick = { onSave(rating, note.trim()) }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Skip") } })
}

/** Distraction-free full screen: just the ring and the time. Tap to exit. */
@Composable
private fun ZenMode(pomo: PomodoroStateEntity, seconds: Long, progress: Float, color: Color, keepOn: Boolean, close: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = keepOn; onDispose { view.keepScreenOn = false } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B100E)).clickable(onClick = close), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val s = 6.dp.toPx(); val arc = Size(size.width - s, size.height - s); val tl = Offset(s / 2, s / 2)
                        drawArc(Color.White.copy(alpha = .08f), 0f, 360f, false, tl, arc, style = Stroke(s))
                        drawArc(color, -90f, 360f * progress, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
                    }
                    Text(mmss(seconds), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
                }
                Text(phaseLabel(pomo), color = Color.White.copy(alpha = .6f))
                Text("Tap anywhere to exit", color = Color.White.copy(alpha = .3f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FocusReports(sessions: List<GardenSession>, tasks: List<TaskModel>, today: LocalDate) {
    var range by remember { mutableIntStateOf(1) }
    val from = when (range) { 0 -> today; 1 -> today.minusDays(6); else -> today.minusDays(29) }
    val r = remember(sessions, range) { FocusStats.report(sessions, from, today) }
    SectionCard("Reports") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Today", "7 days", "30 days").forEachIndexed { i, l -> SegmentedButton(range == i, { range = i }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Focus", mins(r.minutes), Modifier.weight(1f))
            Stat("Sessions", "${r.sessions}", Modifier.weight(1f))
            Stat("Completed", "${r.completionRate}%", Modifier.weight(1f), color = if (r.completionRate >= 80) Chronora.colors.good else if (r.withered > 0) Chronora.colors.warn else MaterialTheme.colorScheme.onSurface)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Longest", mins(r.longest), Modifier.weight(1f))
            Stat("Distractions/h", "%.1f".format(r.interruptionsPerHour), Modifier.weight(1f))
            Stat("Rating", r.averageRating?.let { "%.1f★".format(it) } ?: "—", Modifier.weight(1f))
        }
        if (range == 0) {
            val line = FocusStats.timeline(sessions, today)
            if (line.isNotEmpty()) {
                Text("Timeline", style = MaterialTheme.typography.labelLarge)
                val start = (line.minOf { it.first } / 60) * 60
                val end = ((line.maxOf { it.second } + 59) / 60) * 60
                val span = (end - start).coerceAtLeast(60)
                val good = Chronora.colors.good; val bad = Chronora.colors.bad
                Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Canvas(Modifier.fillMaxSize()) {
                        line.forEach { (a, b, s) ->
                            drawRect(if (s.completed) good else bad, Offset(size.width * (a - start) / span, 0f), Size((size.width * (b - a) / span).coerceAtLeast(3f), size.height))
                        }
                    }
                }
                Row { Text("%02d:00".format(start / 60), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Chronora.muted); Text("%02d:00".format((end / 60) % 24), style = MaterialTheme.typography.labelSmall, color = Chronora.muted) }
            }
        } else {
            val max = (r.perDay.maxOfOrNull { it.second } ?: 0).coerceAtLeast(30)
            Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                r.perDay.forEach { (d, m) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height((70f * m / max).coerceAtLeast(3f).dp).clip(RoundedCornerShape(3.dp)).background(if (m > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                        if (range == 1) Text(d.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = if (d == today) MaterialTheme.colorScheme.primary else Chronora.muted)
                    }
                }
            }
        }
        if (r.byTag.isNotEmpty()) {
            Text("By tag", style = MaterialTheme.typography.labelLarge)
            r.byTag.take(6).forEach { (t, m) -> ReportBar(t, m, r.minutes, MaterialTheme.colorScheme.primary) }
        }
        if (r.byTask.isNotEmpty()) {
            Text("By task", style = MaterialTheme.typography.labelLarge)
            r.byTask.take(5).forEach { (id, m) -> ReportBar(tasks.firstOrNull { it.id == id }?.title ?: "Deleted task", m, r.minutes, Chronora.colors.good) }
        }
        r.bestHour?.let { best ->
            Text("Best focus hours — most around %02d:00".format(best), style = MaterialTheme.typography.labelLarge)
            val maxH = r.byHour.maxOrNull()?.coerceAtLeast(1) ?: 1
            Row(Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
                r.byHour.forEachIndexed { h, m -> Box(Modifier.weight(1f).height((44f * m / maxH).coerceAtLeast(2f).dp).background(if (h == best) Chronora.colors.warn else MaterialTheme.colorScheme.primary.copy(alpha = if (m > 0) .8f else .15f))) }
            }
            Row { listOf("00:00", "06:00", "12:00", "18:00").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Chronora.muted) } }
        }
        Text("Last 16 weeks", style = MaterialTheme.typography.labelLarge)
        val weeks = remember(sessions) { FocusStats.heatmap(sessions, today) }
        val peak = weeks.flatten().maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        val primary = MaterialTheme.colorScheme.primary
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            weeks.forEach { w ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    w.forEach { (d, m) -> Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(if (d.isAfter(today)) Color.Transparent else if (m == 0) MaterialTheme.colorScheme.surfaceVariant else primary.copy(alpha = (.25f + .75f * m / peak).coerceAtMost(1f)))) }
                }
            }
        }
    }
}

@Composable
private fun ReportBar(label: String, minutes: Int, total: Int, color: Color) {
    Column {
        Row { Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(mins(minutes), style = MaterialTheme.typography.bodySmall, color = Chronora.muted) }
        LinearProgressIndicator(progress = { if (total == 0) 0f else minutes.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun FocusHistory(sessions: List<GardenSession>, garden: GardenStore, cfg: FocusConfig, changed: () -> Unit) {
    var all by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val list = sessions.reversed().let { if (all) it.take(100) else it.take(8) }
    SectionCard("History", "Every session, including the ones that withered", action = { TextButton(onClick = { logging = true }) { Text("Log session") } }) {
        if (list.isEmpty()) Text("No sessions yet — start one above.", color = Chronora.muted)
        list.forEach { s ->
            var menu by remember(s) { mutableStateOf(false) }
            Box {
                Row(Modifier.fillMaxWidth().clickable { menu = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (!s.completed) "🥀" else FocusGarden.plantFor(s.minutes).emoji, fontSize = 20.sp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        val time = if (s.startedAt > 0) Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalTime().let { " %02d:%02d".format(it.hour, it.minute) } else ""
                        Text(relativeDay(LocalDate.parse(s.date)) + time + " · " + mins(s.minutes) + (if (s.flow) " flow" else ""), fontWeight = FontWeight.SemiBold)
                        val detail = listOfNotNull(s.tag, s.intention, if (s.interruptions > 0) "${s.interruptions} distraction(s)" else null, s.note).joinToString(" · ")
                        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Chronora.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (s.rating > 0) Text("★".repeat(s.rating), color = Chronora.colors.warn, fontSize = 12.sp)
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Delete session") }, onClick = { menu = false; garden.delete(s); changed() })
                }
            }
        }
        if (sessions.size > 8) TextButton(onClick = { all = !all }) { Text(if (all) "Show less" else "Show more") }
    }
    if (logging) {
        var minutes by remember { mutableIntStateOf(25) }
        var tag by remember { mutableStateOf(cfg.tags.firstOrNull() ?: "") }
        var daysAgo by remember { mutableIntStateOf(0) }
        AlertDialog(onDismissRequest = { logging = false }, title = { Text("Log a focus session") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Forgot to start the timer? Add it here.", style = MaterialTheme.typography.bodySmall)
                Stepper("Minutes", "$minutes", { minutes = (minutes - 5).coerceAtLeast(5) }, { minutes = (minutes + 5).coerceAtMost(600) })
                Stepper("When", if (daysAgo == 0) "Today" else if (daysAgo == 1) "Yesterday" else "$daysAgo days ago", { daysAgo = (daysAgo - 1).coerceAtLeast(0) }, { daysAgo = (daysAgo + 1).coerceAtMost(30) })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { cfg.tags.forEach { t -> FilterChip(tag == t, { tag = t }, label = { Text(t) }) } }
            }
        }, confirmButton = {
            Button(onClick = {
                val day = LocalDate.now().minusDays(daysAgo.toLong())
                garden.add(GardenSession(day.toString(), minutes, true, startedAt = System.currentTimeMillis() - daysAgo * 86_400_000L - minutes * 60_000L, tag = tag.ifBlank { null }))
                logging = false; changed()
            }) { Text("Add") }
        }, dismissButton = { TextButton(onClick = { logging = false }) { Text("Cancel") } })
    }
}

@Composable
private fun TimerSettings(pomo: PomodoroStateEntity, vm: PlannerViewModel, cfg: FocusConfig, idle: Boolean, save: ((FocusConfig) -> FocusConfig) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val soundPrefs = remember { FocusSoundPrefs(context) }
    var sound by remember { mutableStateOf(soundPrefs.sound) }
    var volume by remember { mutableFloatStateOf(soundPrefs.volume) }
    fun set(f: Int = pomo.focusMinutes, s: Int = pomo.shortBreakMinutes, l: Int = pomo.longBreakMinutes, c: Int = pomo.cyclesPerRound) = vm.configurePomodoro(f, s, l, c)
    fun soundChanged() {
        val running = context.getSystemService(android.app.NotificationManager::class.java)?.activeNotifications?.any { it.id == 7301 } == true
        if (running) FocusSessionService.send(context, FocusSessionService.ACTION_SOUND_CHANGED)
    }
    SectionCard("Timer settings", if (cfg.flow) "Flow · break = focus ÷ ${cfg.flowBreakDivisor}" else "${pomo.focusMinutes}/${pomo.shortBreakMinutes} · long ${pomo.longBreakMinutes} every ${pomo.cyclesPerRound}", action = { TextButton(onClick = { open = !open }) { Text(if (open) "Done" else "Customise") } }) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(25, 5, 15), Triple(50, 10, 20), Triple(90, 20, 30), Triple(15, 3, 10)).forEach { (f, s, l) ->
                FilterChip(pomo.focusMinutes == f && pomo.shortBreakMinutes == s, { set(f, s, l) }, label = { Text("$f / $s") })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(sound == null, { sound = null; soundPrefs.sound = null; soundChanged() }, label = { Text("Silence") })
            AmbientSound.values().forEach { a -> FilterChip(sound == a, { sound = a; soundPrefs.sound = a; soundChanged() }, label = { Text(a.label) }) }
        }
        if (sound != null) Slider(volume, { volume = it; soundPrefs.volume = it })
        if (open) {
            Text("Lengths", style = MaterialTheme.typography.labelLarge)
            Stepper("Focus", "${pomo.focusMinutes} min", { set(f = pomo.focusMinutes - 5) }, { set(f = pomo.focusMinutes + 5) })
            Stepper("Short break", "${pomo.shortBreakMinutes} min", { set(s = pomo.shortBreakMinutes - 1) }, { set(s = pomo.shortBreakMinutes + 1) })
            Stepper("Long break", "${pomo.longBreakMinutes} min", { set(l = pomo.longBreakMinutes - 5) }, { set(l = pomo.longBreakMinutes + 5) })
            Stepper("Sessions before a long break", "${pomo.cyclesPerRound}", { set(c = pomo.cyclesPerRound - 1) }, { set(c = pomo.cyclesPerRound + 1) })
            Stepper("Flow break = focus ÷", "${cfg.flowBreakDivisor}", { save { it.copy(flowBreakDivisor = (cfg.flowBreakDivisor - 1).coerceAtLeast(2)) } }, { save { it.copy(flowBreakDivisor = (cfg.flowBreakDivisor + 1).coerceAtMost(10)) } })
            Stepper("Shortest flow that counts", "${cfg.flowMinMinutes} min", { save { it.copy(flowMinMinutes = (cfg.flowMinMinutes - 5).coerceAtLeast(5)) } }, { save { it.copy(flowMinMinutes = cfg.flowMinMinutes + 5) } })
            Stepper("Daily focus goal", mins(cfg.dailyGoalMinutes), { save { it.copy(dailyGoalMinutes = (cfg.dailyGoalMinutes - 30).coerceAtLeast(30)) } }, { save { it.copy(dailyGoalMinutes = cfg.dailyGoalMinutes + 30) } })
            Text("Flow", style = MaterialTheme.typography.labelLarge)
            SwitchRow("Start breaks automatically", cfg.autoStartBreaks) { v -> save { it.copy(autoStartBreaks = v) } }
            SwitchRow("Start the next focus automatically", cfg.autoStartFocus) { v -> save { it.copy(autoStartFocus = v) } }
            SwitchRow("Ask how it went after each session", cfg.reflect) { v -> save { it.copy(reflect = v) } }
            Text("Sound & screen", style = MaterialTheme.typography.labelLarge)
            SwitchRow("Ticking sound while focusing", cfg.tickSound) { v -> save { it.copy(tickSound = v) } }
            SwitchRow("Vibrate when a phase ends", cfg.vibrate) { v -> save { it.copy(vibrate = v) } }
            SwitchRow("Keep screen on while the timer runs", cfg.keepScreenOn) { v -> save { it.copy(keepScreenOn = v) } }
            Text("Deep focus", style = MaterialTheme.typography.labelLarge)
            SwitchRow("Block every other app during focus", cfg.strict) { v -> save { it.copy(strict = v) } }
            if (cfg.strict) {
                Text("Allowed: " + (if (cfg.strictAllowed.isEmpty()) "only phone, launcher and Chronora" else cfg.strictAllowed.joinToString { appLabel(context, it) }), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { picking = true }) { Text("Choose allowed apps") }
                Text("Uses the App blocker (More → App blocker).", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
            }
            ListEditor("Tags", cfg.tags) { v -> save { it.copy(tags = v) } }
            if (!idle) Text("New lengths apply from the next phase.", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
        }
    }
    if (picking) AllowedAppsDialog(cfg.strictAllowed, { v -> save { it.copy(strictAllowed = v) } }) { picking = false }
}

private fun appLabel(context: android.content.Context, pkg: String) = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

@Composable
private fun AllowedAppsDialog(selected: Set<String>, onSave: (Set<String>) -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }.distinctBy { it.packageName }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }.sortedBy { it.second.lowercase() }
    }
    var chosen by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Allowed during focus") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search apps") }, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(apps.filter { query.isBlank() || it.second.contains(query, true) }, key = { it.first }) { (pkg, label) ->
                    Row(Modifier.fillMaxWidth().clickable { chosen = if (pkg in chosen) chosen - pkg else chosen + pkg }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pkg in chosen, { chosen = if (pkg in chosen) chosen - pkg else chosen + pkg }); Text(label)
                    }
                }
            }
        }
    }, confirmButton = { Button(onClick = { onSave(chosen); close() }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
