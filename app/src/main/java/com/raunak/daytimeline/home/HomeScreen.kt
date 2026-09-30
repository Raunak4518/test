package com.raunak.daytimeline.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.PlannerViewModel
import com.raunak.daytimeline.alarm.AlarmPersistentStore
import com.raunak.daytimeline.alarm.AlarmSchedulePlanner
import com.raunak.daytimeline.campus.CampusStore
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.ui.Chronora
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal fun hhmm(m: Int) = "%02d:%02d".format((m / 60).coerceIn(0, 23), (m % 60).coerceIn(0, 59))
internal fun span(m: Int) = when { m <= 0 -> ""; m < 60 -> "${m}m"; m % 60 == 0 -> "${m / 60}h"; else -> "${m / 60}h ${m % 60}m" }

internal fun kindIcon(k: EventKind): ImageVector = when (k) {
    EventKind.WAKE -> Icons.Default.WbSunny
    EventKind.CLASS -> Icons.Default.School
    EventKind.MEAL -> Icons.Default.Restaurant
    EventKind.TASK -> Icons.Default.TaskAlt
    EventKind.DEADLINE -> Icons.Default.Flag
    EventKind.FREE -> Icons.Default.Add
    EventKind.SLEEP -> Icons.Default.Bedtime
}

/** Minute of the day, refreshed every 20 s so the now-line moves on its own. */
@Composable
internal fun rememberNowMinute(): Int {
    var minute by remember { mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); minute = LocalTime.now().let { it.hour * 60 + it.minute } } }
    return minute
}

/** Earliest enabled alarm that rings on [date], as minute of day. */
internal fun wakeMinute(context: android.content.Context, date: LocalDate): Int? = runCatching {
    AlarmPersistentStore(context).all().filter { it.enabled }
        .filter { AlarmSchedulePlanner.matches(it, LocalDateTime.of(date, LocalTime.of(it.hour, it.minute))) }
        .minOfOrNull { it.hour * 60 + it.minute }
}.getOrNull()

/** Fades and lifts a row into place, one after another. */
internal fun Modifier.enterAnimated(index: Int, key: Any): Modifier = composedEnter(index, key)

@Composable
private fun enterProgress(index: Int, key: Any): Float {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { delay((index.coerceAtMost(12) * 45).toLong()); p.animateTo(1f, spring(dampingRatio = .8f, stiffness = Spring.StiffnessLow)) }
    return p.value
}

private fun Modifier.composedEnter(index: Int, key: Any): Modifier = composed {
    val p = enterProgress(index, key)
    val lift = with(LocalDensity.current) { 28.dp.toPx() }
    graphicsLayer { alpha = p; translationY = (1f - p) * lift }
}

/** The front page: a greeting, the week, what's on now, and the whole day as an animated timeline. */
@Composable
fun HomeScreen(vm: PlannerViewModel, onEdit: (TaskModel) -> Unit, onAddAt: (LocalDate, Int) -> Unit, onOpenFocusMode: () -> Unit = {}, onOpenFocus: () -> Unit) {
    val context = LocalContext.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val agenda by vm.agenda.collectAsStateWithLifecycle()
    val date by vm.currentDate.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val campus by remember { CampusStore.get(context.applicationContext).data }.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val nowMinute = rememberNowMinute()
    val wake = remember(date) { wakeMinute(context, date) }
    val events = remember(tasks, campus, date, wake, settings) {
        TimelineBuilder.build(date, tasks, campus, wake, minOf(settings.dayStartMinute, wake ?: settings.dayStartMinute), settings.dayEndMinute, showCompleted = settings.showCompleted)
    }
    val isToday = date == today
    val nowIndex = if (isToday) TimelineBuilder.nowIndex(events, nowMinute) else -1
    val list = rememberLazyListState()
    val header = 3 // greeting, week strip, now card
    LaunchedEffect(date, events.size) {
        if (isToday && nowIndex > 1) { delay(350); list.animateScrollToItem((header + nowIndex - 1).coerceAtLeast(0)) } else list.scrollToItem(0)
    }

    LazyColumn(state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)) {
        item(key = "greet") { Greeting(date, tasks, isToday, nowMinute) }
        item(key = "week") { WeekStrip(date, agenda, Modifier.padding(vertical = 14.dp)) { vm.selectDate(it) } }
        item(key = "now") {
            Column {
                if (isToday) NowCard(events, nowMinute, onOpenFocus, Modifier.padding(bottom = 12.dp))
                com.raunak.daytimeline.pro.FocusModeCard(Modifier.padding(bottom = 18.dp), onOpenFocusMode)
            }
        }
        itemsIndexed(events, key = { _, e -> e.key }) { i, e ->
            Column(Modifier.enterAnimated(i, date.toString() + e.key)) {
                if (i == nowIndex) NowLine(nowMinute)
                TimelineRow(e, isToday, nowMinute, first = i == 0, last = i == events.lastIndex,
                    onTask = onEdit, onToggle = { t -> vm.toggleComplete(t, !t.completed) }, onAdd = { onAddAt(date, it) })
            }
        }
        if (isToday && nowIndex == events.size) item(key = "nowEnd") { NowLine(nowMinute) }
    }
}

@Composable
private fun Greeting(date: LocalDate, tasks: List<TaskModel>, isToday: Boolean, nowMinute: Int) {
    val hello = when (nowMinute / 60) { in 4..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night" }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (isToday) hello else com.raunak.daytimeline.productivity.relativeDay(date), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = Chronora.muted)
        }
        val done = tasks.count { it.completed }
        ProgressRing(if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size, "$done/${tasks.size}", Modifier.size(64.dp))
    }
}

/** Animated ring with a gradient sweep. */
@Composable
internal fun ProgressRing(fraction: Float, label: String, modifier: Modifier = Modifier, stroke: androidx.compose.ui.unit.Dp = 7.dp, sub: String? = null) {
    val sweep by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val a = MaterialTheme.colorScheme.primary
    val b = MaterialTheme.colorScheme.tertiary
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val sz = Size(size.width - w, size.height - w)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
            drawArc(Brush.sweepGradient(listOf(a, b, a)), -90f, 360f * sweep, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
        }
    }
}

/** Seven days around the selected one; the selected pill slides colour in. */
@Composable
internal fun WeekStrip(selected: LocalDate, agenda: List<TaskModel>, modifier: Modifier = Modifier, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val start = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    val busy = remember(agenda) { agenda.groupBy { it.date }.mapValues { it.value.size } }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPick(selected.minusWeeks(1)) }, Modifier.size(28.dp)) { Icon(Icons.Default.ChevronLeft, "Previous week", tint = Chronora.muted) }
        (0L..6L).map { start.plusDays(it) }.forEach { d ->
            val on = d == selected
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, tween(300), label = "day")
            val fg by animateColorAsState(if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, tween(300), label = "dayText")
            Column(
                Modifier.weight(1f).padding(horizontal = 2.dp).clip(RoundedCornerShape(16.dp)).background(bg)
                    .then(if (d == today && !on) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier)
                    .clickable { onPick(d) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(d.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = if (on) fg else Chronora.muted)
                Text("${d.dayOfMonth}", fontWeight = FontWeight.Bold, color = fg)
                Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if ((busy[d] ?: 0) > 0) (if (on) fg else MaterialTheme.colorScheme.primary) else Color.Transparent))
            }
        }
        IconButton(onClick = { onPick(selected.plusWeeks(1)) }, Modifier.size(28.dp)) { Icon(Icons.Default.ChevronRight, "Next week", tint = Chronora.muted) }
    }
}

/** What's happening right now (or next), on a slowly shifting gradient. */
@Composable
private fun NowCard(events: List<TimelineEvent>, minute: Int, onOpenFocus: () -> Unit, modifier: Modifier = Modifier) {
    val real = events.filter { it.kind != EventKind.FREE && it.kind != EventKind.SLEEP }
    val current = real.firstOrNull { it.start <= minute && minute < it.end }
    val next = real.firstOrNull { it.start > minute }
    val shift by rememberInfiniteTransition(label = "hero").animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    val hero = Chronora.colors.hero
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .drawBehind {
                drawRect(Brush.linearGradient(listOf(hero, accent.copy(alpha = .85f), hero), start = Offset(size.width * shift, 0f), end = Offset(size.width * (1 - shift), size.height)))
            }
            .padding(20.dp)
    ) {
        val onHero = Chronora.colors.onHero
        val muted = Chronora.colors.heroMuted
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                current != null -> {
                    Text("NOW", color = muted, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
                    Text(current.title, color = onHero, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val left = current.end - minute
                    Text("${span(left)} left · until ${hhmm(current.end)}", color = muted)
                    val p by animateFloatAsState((minute - current.start).toFloat() / current.minutes.coerceAtLeast(1), tween(900), label = "now")
                    LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Chronora.colors.heroAccent, trackColor = Color.White.copy(alpha = .15f))
                }
                next != null -> {
                    Text("UP NEXT", color = muted, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
                    Text(next.title, color = onHero, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("in ${span(next.start - minute)} · ${hhmm(next.start)}", color = muted)
                }
                else -> {
                    Text("ALL CLEAR", color = muted, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
                    Text("Nothing left today", color = onHero, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            if (current?.kind != EventKind.CLASS && current?.kind != EventKind.MEAL) {
                FilledTonalButton(onClick = onOpenFocus, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = .16f), contentColor = onHero)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Focus")
                }
            }
        }
    }
}

/** Red line with a breathing dot marking the current time. */
@Composable
private fun NowLine(minute: Int) {
    val pulse by rememberInfiniteTransition(label = "now").animateFloat(.6f, 1.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val red = MaterialTheme.colorScheme.error
    Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(hhmm(minute), Modifier.width(48.dp), color = red, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(14.dp).scale(pulse).clip(CircleShape).background(red.copy(alpha = .25f)))
            Box(Modifier.size(8.dp).clip(CircleShape).background(red))
        }
        Box(Modifier.weight(1f).height(2.dp).background(Brush.horizontalGradient(listOf(red, red.copy(alpha = 0f)))))
    }
}

@Composable
private fun TimelineRow(e: TimelineEvent, isToday: Boolean, now: Int, first: Boolean, last: Boolean, onTask: (TaskModel) -> Unit, onToggle: (TaskModel) -> Unit, onAdd: (Int) -> Unit) {
    val past = isToday && e.end <= now && e.kind != EventKind.SLEEP
    val live = isToday && e.start <= now && now < e.end
    val tint = if (e.kind == EventKind.FREE) MaterialTheme.colorScheme.outline else Color(e.color)
    val done = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.outlineVariant
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(1f, 1.6f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "node")
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(48.dp).padding(top = 14.dp)) {
            Text(hhmm(e.start), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (past) Chronora.muted else MaterialTheme.colorScheme.onSurface)
            if (e.minutes > 0 && e.kind != EventKind.FREE) Text(span(e.minutes), style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
        }
        // Rail: coloured where the day has passed, muted ahead; the live node breathes.
        Box(Modifier.width(24.dp).fillMaxHeight().drawBehind {
            val x = size.width / 2
            val node = 22.dp.toPx()
            val w = 2.5.dp.toPx()
            if (!first) drawLine(if (past || live) done else rest, Offset(x, 0f), Offset(x, node), w)
            if (!last) {
                if (live) {
                    val frac = ((now - e.start).toFloat() / e.minutes.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val mid = node + (size.height - node) * frac
                    drawLine(done, Offset(x, node), Offset(x, mid), w)
                    drawLine(rest, Offset(x, mid), Offset(x, size.height), w)
                } else drawLine(if (past) done else rest, Offset(x, node), Offset(x, size.height), w,
                    pathEffect = if (e.kind == EventKind.FREE) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null)
            }
        }) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(16.dp), contentAlignment = Alignment.Center) {
                if (live) Box(Modifier.size(16.dp).scale(pulse).clip(CircleShape).background(tint.copy(alpha = .25f)))
                Box(Modifier.size(if (e.kind == EventKind.FREE) 8.dp else 12.dp).clip(CircleShape)
                    .background(if (e.kind == EventKind.FREE) MaterialTheme.colorScheme.background else if (past && !live) done else tint)
                    .border(2.dp, if (e.kind == EventKind.FREE) rest else Color.Transparent, CircleShape))
            }
        }
        Box(Modifier.weight(1f).padding(start = 6.dp, top = 4.dp, bottom = 10.dp)) {
            when (e.kind) {
                EventKind.FREE -> FreeSlot(e, onAdd)
                EventKind.WAKE, EventKind.SLEEP, EventKind.DEADLINE -> Moment(e, past)
                else -> EventCard(e, past, live, tint, onTask, onToggle)
            }
        }
    }
}

@Composable
private fun EventCard(e: TimelineEvent, past: Boolean, live: Boolean, tint: Color, onTask: (TaskModel) -> Unit, onToggle: (TaskModel) -> Unit) {
    val alpha = if (past && !live) .55f else 1f
    Row(
        Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }.clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = if (live) .26f else .16f), tint.copy(alpha = .05f))))
            .then(if (live) Modifier.border(1.5.dp, tint.copy(alpha = .7f), RoundedCornerShape(18.dp)) else Modifier)
            .clickable(enabled = e.task != null) { e.task?.let(onTask) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
            Icon(kindIcon(e.kind), null, Modifier.size(19.dp), tint = tint)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(e.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (e.kind == EventKind.TASK && e.done) TextDecoration.LineThrough else null)
            val detail = listOf("${hhmm(e.start)}–${hhmm(e.end)}", e.detail).filter { it.isNotBlank() }.joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Chronora.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        e.task?.let { t -> CheckBubble(t.completed, tint) { onToggle(t) } }
        if (e.kind == EventKind.CLASS && e.done) Icon(Icons.Default.CheckCircle, "Marked", Modifier.size(20.dp), tint = tint)
    }
}

/** Round check that pops when ticked. */
@Composable
private fun CheckBubble(checked: Boolean, tint: Color, onClick: () -> Unit) {
    val s by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = .45f, stiffness = Spring.StiffnessMedium), label = "check")
    Box(Modifier.size(30.dp).clip(CircleShape).border(2.dp, tint, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(30.dp).scale(s).clip(CircleShape).background(tint))
        if (checked) Icon(Icons.Default.Check, "Done", Modifier.size(18.dp).scale(s), tint = Color.White)
    }
}

@Composable
private fun Moment(e: TimelineEvent, past: Boolean) {
    val tint = Color(e.color)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).graphicsLayer { alpha = if (past) .55f else 1f }, verticalAlignment = Alignment.CenterVertically) {
        Icon(kindIcon(e.kind), null, Modifier.size(18.dp), tint = tint)
        Text("  " + e.title, fontWeight = FontWeight.SemiBold, color = tint)
        if (e.detail.isNotBlank()) Text(" · " + e.detail, style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
    }
}

@Composable
private fun FreeSlot(e: TimelineEvent, onAdd: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onAdd(e.start) }.padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${span(e.minutes)} free", color = Chronora.muted, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.Default.AddCircleOutline, "Plan something", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}
