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
import androidx.compose.foundation.verticalScroll
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
import com.raunak.daytimeline.pro.FocusModeScreen
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

/** The Focus tab: the timer, Focus mode and stats, each in one place. */
@Composable
fun FocusTab(pomo: PomodoroStateEntity, tasks: List<TaskModel>, allTasks: List<TaskModel>, vm: PlannerViewModel) {
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    val idle = pomo.phase == "IDLE"
    Column(Modifier.fillMaxSize()) {
        // While a session runs, the session owns the screen.
        if (idle) PillTabs(listOf("Timer", "Focus mode", "Stats"), section, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { section = it }
        when (if (idle) section else 0) {
            0 -> FocusPanel(pomo, tasks, allTasks, vm)
            1 -> FocusModeScreen()
            else -> FocusStatsPage(allTasks)
        }
    }
}

/** Pomodoro / Flow timer: a setup screen when idle, a locked session screen while running. */
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
    val todayReport = remember(sessions) { FocusStats.report(sessions, today, today) }
    var settings by remember { mutableStateOf(false) }
    val idle = pomo.phase == "IDLE"

    val view = LocalView.current
    DisposableEffect(cfg.keepScreenOn, pomo.running) {
        view.keepScreenOn = cfg.keepScreenOn && pomo.running
        onDispose { view.keepScreenOn = false }
    }
    val pending = remember(sessions, cfg.reflect) {
        if (!cfg.reflect) null else sessions.lastOrNull { it.completed && it.startedAt > prefs.reflectedUpTo && it.rating == 0 && System.currentTimeMillis() - (it.startedAt + it.minutes * 60_000L) < 2 * 3_600_000L }
    }

    if (idle) FocusSetup(pomo, tasks, vm, cfg, prefs, todayReport.minutes, summary.focusDayStreak, ::save) { settings = true }
    else FocusRunning(pomo, allTasks, vm, cfg, prefs)

    pending?.let { s -> ReflectDialog(s, onSave = { rating, note -> garden.update(s.copy(rating = rating, note = note.ifBlank { null })); prefs.reflectedUpTo = s.startedAt; refresh++ }) { prefs.reflectedUpTo = s.startedAt; refresh++ } }
    if (settings) FullScreenPage("Timer settings", { settings = false }) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) { item { TimerSettings(pomo, vm, cfg) { save(it) } } }
    }
}

private val Presets = listOf(15, 25, 45, 50, 60, 90)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusSetup(pomo: PomodoroStateEntity, tasks: List<TaskModel>, vm: PlannerViewModel, cfg: FocusConfig, prefs: FocusPrefs, todayMinutes: Int, streak: Int, save: ((FocusConfig) -> FocusConfig) -> Unit, openSettings: () -> Unit) {
    val context = LocalContext.current
    var tag by remember { mutableStateOf(prefs.tag) }
    var intention by remember { mutableStateOf(prefs.intention) }
    var taskId by remember { mutableStateOf<Long?>(null) }
    val soundPrefs = remember { FocusSoundPrefs(context) }
    var sound by remember { mutableStateOf(soundPrefs.sound) }
    val open = tasks.filter { !it.completed }.sortedBy { it.startMinute }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillTabs(listOf("Pomodoro", "Flow"), if (cfg.flow) 1 else 0, Modifier.weight(1f)) { i -> save { it.copy(mode = if (i == 1) "FLOW" else "POMODORO") } }
                IconButton(onClick = openSettings) { Icon(Icons.Default.Tune, "Timer settings") }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TimerRing(0f, MaterialTheme.colorScheme.primary, 220.dp) {
                    Text(FocusGarden.plantFor(if (cfg.flow) cfg.flowMinMinutes else pomo.focusMinutes).emoji, fontSize = 30.sp)
                    Text(if (cfg.flow) "00:00" else "%02d:00".format(pomo.focusMinutes), style = MaterialTheme.typography.displayMedium)
                    Text(if (cfg.flow) "count up" else "${pomo.shortBreakMinutes} min break after", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                }
                if (!cfg.flow) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    Presets.forEach { m ->
                        val on = pomo.focusMinutes == m
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))
                            .clickable { vm.configurePomodoro(m, (m / 5).coerceIn(3, 20), (m / 2).coerceIn(10, 30), pomo.cyclesPerRound) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("$m", style = MaterialTheme.typography.titleMedium, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        if (open.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Working on", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    open.take(8).forEach { t ->
                        val on = taskId == t.id
                        Column(Modifier.width(150.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            .clickable { taskId = if (on) null else t.id }.padding(12.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(t.colorHex)))
                            Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            Text("%02d:%02d".format(t.startMinute / 60, t.startMinute % 60), style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cfg.tags.forEach { t -> FilterChip(tag == t, { tag = if (tag == t) "" else t }, label = { Text(t) }, shape = RoundedCornerShape(50)) }
                }
                if (taskId == null) OutlinedTextField(intention, { intention = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text("Intention (optional)") })
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sound", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (listOf<AmbientSound?>(null) + AmbientSound.values()).forEach { a ->
                        val on = sound == a
                        Column(Modifier.width(68.dp).clip(RoundedCornerShape(16.dp)).clickable { sound = a; soundPrefs.sound = a }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)), contentAlignment = Alignment.Center) {
                                Icon(soundIcon(a), null, tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                            Text(a?.label?.substringBefore(" ") ?: "Off", style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = {
                prefs.tag = tag
                prefs.intention = (open.firstOrNull { it.id == taskId }?.title ?: intention).trim()
                vm.startPomodoro(taskId)
            }, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (cfg.flow) "Start flow" else "Start focus", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Today", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelLarge)
                        Text("${mins(todayMinutes)} / ${mins(cfg.dailyGoalMinutes)}", color = Chronora.colors.onHero, style = MaterialTheme.typography.headlineSmall)
                    }
                    Text("🔥 ${streak}d", color = Chronora.colors.onHero, style = MaterialTheme.typography.titleLarge)
                }
                LinearProgressIndicator(progress = { (todayMinutes.toFloat() / cfg.dailyGoalMinutes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Chronora.colors.heroAccent, trackColor = Color.White.copy(alpha = .18f))
            }
        }
    }
}

private fun soundIcon(a: AmbientSound?) = when (a) {
    null -> Icons.Default.VolumeOff
    AmbientSound.RAIN -> Icons.Default.WaterDrop
    AmbientSound.OCEAN -> Icons.Default.Waves
    AmbientSound.FAN -> Icons.Default.Air
    AmbientSound.BINAURAL_FOCUS -> Icons.Default.GraphicEq
    else -> Icons.Default.Grain
}

@Composable
private fun TimerRing(progress: Float, color: Color, size: androidx.compose.ui.unit.Dp, content: @Composable ColumnScope.() -> Unit) {
    val p by androidx.compose.animation.core.animateFloatAsState(progress, androidx.compose.animation.core.tween(900), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val track = MaterialTheme.colorScheme.surfaceVariant
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val tl = Offset(stroke / 2, stroke / 2)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * p, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/** While a session runs only the session is on screen: nothing to edit, just the timer and its controls. */
@Composable
private fun FocusRunning(pomo: PomodoroStateEntity, allTasks: List<TaskModel>, vm: PlannerViewModel, cfg: FocusConfig, prefs: FocusPrefs) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pomo.running, pomo.phase) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    var distractions by remember { mutableIntStateOf(prefs.interruptions().size) }
    var zen by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    val flow = pomo.phase == PomodoroEngine.FLOW
    val waiting = PomodoroEngine.waiting(pomo)
    val focusing = pomo.phase == "FOCUS" || flow
    val isBreak = pomo.phase.endsWith("BREAK")
    val total = phaseSeconds(pomo).coerceAtLeast(1)
    val shown = if (flow) PomodoroEngine.flowElapsed(pomo, now) else pomo.remainingSeconds
    val progress = when { waiting -> 0f; flow -> (shown % 3600) / 3600f; else -> (1f - shown.toFloat() / total).coerceIn(0f, 1f) }
    val color = if (isBreak) Chronora.colors.good else MaterialTheme.colorScheme.primary
    val linked = allTasks.firstOrNull { it.id == pomo.taskId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .14f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text(if (waiting) phaseLabel(pomo) + " · ready" else phaseLabel(pomo), color = color, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { zen = true }) { Icon(Icons.Default.Fullscreen, "Full screen") }
        }
        TimerRing(progress, color, 280.dp) {
            Text(if (isBreak) "☕" else FocusGarden.plantFor(if (flow) (shown / 60).toInt() else ((total - shown) / 60).toInt()).emoji, fontSize = 36.sp)
            Text(mmss(shown), style = MaterialTheme.typography.displayLarge)
            Text(linked?.title ?: prefs.intention.ifBlank { prefs.tag.ifBlank { "Focus" } }, style = MaterialTheme.typography.bodyMedium, color = Chronora.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
        }
        if (!flow) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..pomo.cyclesPerRound.coerceIn(1, 12)).forEach { i ->
                val filled = i < pomo.cycleIndex || (i == pomo.cycleIndex && isBreak)
                val current = i == pomo.cycleIndex && pomo.phase == "FOCUS"
                Box(Modifier.size(if (current) 12.dp else 10.dp).clip(CircleShape).background(if (filled || current) color else MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundAction(Icons.Default.Stop, "Stop") { if (focusing) confirmStop = true else vm.resetPomodoro() }
            FilledIconButton(onClick = { if (pomo.running) vm.pausePomodoro() else vm.resumePomodoro() }, modifier = Modifier.size(84.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = color)) {
                Icon(if (pomo.running) Icons.Default.Pause else Icons.Default.PlayArrow, if (pomo.running) "Pause" else "Resume", Modifier.size(40.dp))
            }
            RoundAction(if (flow) Icons.Default.Coffee else Icons.Default.SkipNext, if (flow) "Take a break" else "Skip") { vm.skipPomodoro() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (focusing && pomo.running) AssistChip(onClick = { vm.logDistraction(); distractions = prefs.interruptions().size; Feedback.show("Distraction noted") },
                leadingIcon = { Icon(Icons.Default.NotificationsPaused, null, Modifier.size(18.dp)) }, label = { Text("Distracted" + if (distractions > 0) " · $distractions" else "") }, shape = RoundedCornerShape(50))
            if (!flow && !waiting) AssistChip(onClick = { vm.extendPomodoro(5); Feedback.show("Added 5 minutes") }, leadingIcon = { Icon(Icons.Default.MoreTime, null, Modifier.size(18.dp)) }, label = { Text("5 min") }, shape = RoundedCornerShape(50))
        }
        if (flow) Text("Break earned: ${PomodoroEngine.flowBreakMinutes(shown, cfg.flowBreakDivisor)} min", style = MaterialTheme.typography.bodyMedium, color = Chronora.muted)
        if (focusing && cfg.strict) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = Chronora.muted); Text("  Other apps are blocked", style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
        }
    }
    if (confirmStop) AlertDialog(onDismissRequest = { confirmStop = false }, icon = { Text("🥀", fontSize = 28.sp) }, title = { Text("Give up this session?") },
        confirmButton = { TextButton(onClick = { confirmStop = false; vm.resetPomodoro() }) { Text("Give up", color = Chronora.colors.bad) } },
        dismissButton = { Button(onClick = { confirmStop = false }) { Text("Keep going") } })
    if (zen) ZenMode(pomo, shown, progress, color, cfg.keepScreenOn) { zen = false }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(60.dp)) { Icon(icon, label, Modifier.size(28.dp)) }
}

/** Reports, history and the garden. */
@Composable
fun FocusStatsPage(allTasks: List<TaskModel>) {
    val context = LocalContext.current
    val garden = remember(context) { GardenStore(context.applicationContext) }
    val prefs = remember(context) { FocusPrefs(context.applicationContext) }
    var refresh by remember { mutableIntStateOf(0) }
    val sessions = remember(refresh) { garden.sessions() }
    val today = LocalDate.now()
    val summary = remember(sessions) { FocusGarden.summarize(sessions, today) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Level ${summary.level}", color = Chronora.colors.onHero, style = MaterialTheme.typography.headlineSmall)
                        Text("${summary.plants.size} plants · 🔥 ${summary.focusDayStreak} day streak", color = Chronora.colors.heroMuted)
                    }
                    Text(summary.plants.takeLast(3).joinToString("") { it.emoji }.ifBlank { "🌱" }, fontSize = 30.sp)
                }
                LinearProgressIndicator(progress = { summary.xpIntoLevel.toFloat() / summary.xpForNextLevel.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Chronora.colors.heroAccent, trackColor = Color.White.copy(alpha = .18f))
            }
        }
        item { FocusReports(sessions, allTasks, today) }
        item { FocusHistory(sessions, garden, prefs.config) { refresh++ } }
    }
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
    SectionCard("Reports", icon = Icons.Default.Insights) {
        PillTabs(listOf("Today", "7 days", "30 days"), range) { range = it }
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
    SectionCard("History", icon = Icons.Default.History, action = { TextButton(onClick = { logging = true }) { Text("Log session") } }) {
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
private fun TimerSettings(pomo: PomodoroStateEntity, vm: PlannerViewModel, cfg: FocusConfig, save: ((FocusConfig) -> FocusConfig) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val soundPrefs = remember { FocusSoundPrefs(context) }
    var volume by remember { mutableFloatStateOf(soundPrefs.volume) }
    fun set(f: Int = pomo.focusMinutes, s: Int = pomo.shortBreakMinutes, l: Int = pomo.longBreakMinutes, c: Int = pomo.cyclesPerRound) = vm.configurePomodoro(f, s, l, c)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard("Lengths", icon = Icons.Default.Timer) {
            Stepper("Focus", "${pomo.focusMinutes} min", { set(f = (pomo.focusMinutes - 5).coerceAtLeast(5)) }, { set(f = pomo.focusMinutes + 5) })
            Stepper("Short break", "${pomo.shortBreakMinutes} min", { set(s = (pomo.shortBreakMinutes - 1).coerceAtLeast(1)) }, { set(s = pomo.shortBreakMinutes + 1) })
            Stepper("Long break", "${pomo.longBreakMinutes} min", { set(l = (pomo.longBreakMinutes - 5).coerceAtLeast(5)) }, { set(l = pomo.longBreakMinutes + 5) })
            Stepper("Rounds before long break", "${pomo.cyclesPerRound}", { set(c = (pomo.cyclesPerRound - 1).coerceAtLeast(1)) }, { set(c = pomo.cyclesPerRound + 1) })
            Stepper("Daily goal", mins(cfg.dailyGoalMinutes), { save { it.copy(dailyGoalMinutes = (cfg.dailyGoalMinutes - 15).coerceAtLeast(15)) } }, { save { it.copy(dailyGoalMinutes = cfg.dailyGoalMinutes + 15) } })
        }
        SectionCard("Flow mode", icon = Icons.Default.AllInclusive) {
            Stepper("Break = focus ÷", "${cfg.flowBreakDivisor}", { save { it.copy(flowBreakDivisor = (cfg.flowBreakDivisor - 1).coerceAtLeast(2)) } }, { save { it.copy(flowBreakDivisor = (cfg.flowBreakDivisor + 1).coerceAtMost(10)) } })
            Stepper("Shortest that counts", "${cfg.flowMinMinutes} min", { save { it.copy(flowMinMinutes = (cfg.flowMinMinutes - 5).coerceAtLeast(5)) } }, { save { it.copy(flowMinMinutes = cfg.flowMinMinutes + 5) } })
        }
        SectionCard("Automation", icon = Icons.Default.AutoMode) {
            SwitchRow("Auto-start breaks", cfg.autoStartBreaks) { v -> save { it.copy(autoStartBreaks = v) } }
            SwitchRow("Auto-start next focus", cfg.autoStartFocus) { v -> save { it.copy(autoStartFocus = v) } }
            SwitchRow("Rate each session", cfg.reflect) { v -> save { it.copy(reflect = v) } }
        }
        SectionCard("Sound & screen", icon = Icons.Default.VolumeUp) {
            Text("Volume", style = MaterialTheme.typography.bodyLarge)
            Slider(volume, { volume = it; soundPrefs.volume = it })
            SwitchRow("Ticking", cfg.tickSound) { v -> save { it.copy(tickSound = v) } }
            SwitchRow("Vibrate at phase end", cfg.vibrate) { v -> save { it.copy(vibrate = v) } }
            SwitchRow("Keep screen on", cfg.keepScreenOn) { v -> save { it.copy(keepScreenOn = v) } }
        }
        SectionCard("Deep focus", icon = Icons.Default.Lock) {
            SwitchRow("Block all other apps", cfg.strict) { v -> save { it.copy(strict = v) } }
            if (cfg.strict) OutlinedButton(onClick = { picking = true }, shape = RoundedCornerShape(14.dp)) { Text("Allowed apps · ${cfg.strictAllowed.size}") }
        }
        SectionCard("Tags", icon = Icons.Default.Sell) { ListEditor("", cfg.tags) { v -> save { it.copy(tags = v) } } }
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
