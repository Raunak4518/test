package com.raunak.daytimeline.home

import com.raunak.daytimeline.ui.PillTabs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.PlannerViewModel
import com.raunak.daytimeline.campus.AttendanceEngine
import com.raunak.daytimeline.campus.CampusStore
import com.raunak.daytimeline.campus.Mess
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.features.OfflineProductivityStore
import com.raunak.daytimeline.productivity.EisenhowerMatrix
import com.raunak.daytimeline.productivity.OverdueCard
import com.raunak.daytimeline.productivity.QuickAddBar
import com.raunak.daytimeline.productivity.TaskViewsScope
import com.raunak.daytimeline.ui.Chronora
import com.raunak.daytimeline.ui.Stepper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val Views = listOf("Day", "Week", "Agenda", "Matrix")

/** Time-blocking planner: a day grid you can drag in, a week grid, the upcoming list and the priority matrix. */
@Composable
fun PlanScreen(vm: PlannerViewModel, store: OfflineProductivityStore, onEdit: (TaskModel) -> Unit, onAddAt: (LocalDate, Int) -> Unit, onOpenMonth: () -> Unit) {
    var view by rememberSaveable { mutableIntStateOf(0) }
    val date by vm.currentDate.collectAsStateWithLifecycle()
    val agenda by vm.agenda.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PillTabs(Views, view, Modifier.weight(1f)) { view = it }
            IconButton(onClick = onOpenMonth) { Icon(Icons.Default.CalendarMonth, "Month") }
        }
        AnimatedContent(view, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "plan") { v ->
            when (v) {
                0 -> Column {
                    WeekStrip(date, agenda, Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { vm.selectDate(it) }
                    DayGrid(vm, date, onEdit, onAddAt)
                }
                1 -> WeekGrid(vm, date, agenda) { vm.selectDate(it); view = 0 }
                2 -> UpcomingList(vm, store, agenda, onEdit)
                else -> MatrixView(vm, store, agenda, onEdit)
            }
        }
    }
}

@Composable
private fun UpcomingList(vm: PlannerViewModel, store: OfflineProductivityStore, agenda: List<TaskModel>, onEdit: (TaskModel) -> Unit) {
    val today = LocalDate.now()
    val prefs by store.settings.collectAsStateWithLifecycle()
    val overdue = agenda.filter { !it.completed && it.recurrenceType == "NONE" && it.date.isBefore(today) }
    val days = prefs.upcomingDays.takeIf { it > 0 } ?: 7
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { QuickAddBar(vm, today) }
        item { OverdueCard(overdue, vm, onEdit, today) }
        with(TaskViewsScope) { upcoming(agenda, days, vm, onEdit, {}, today) }
        item { Stepper("Days shown", "$days", { store.updateSettings { it.copy(upcomingDays = (days - 1).coerceAtLeast(1)) } }, { store.updateSettings { it.copy(upcomingDays = (days + 1).coerceAtMost(60)) } }) }
    }
}

@Composable
private fun MatrixView(vm: PlannerViewModel, store: OfflineProductivityStore, agenda: List<TaskModel>, onEdit: (TaskModel) -> Unit) {
    val prefs by store.settings.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(16.dp)) { item { EisenhowerMatrix(agenda, vm, prefs, store::updateSettings, onEdit) } }
}

private val HourHeight = 72.dp
private val LabelWidth = 52.dp

/** One day as a scrollable hour grid. Tap empty space to add, long-press a task to drag it, pull its bottom edge to resize. */
@Composable
private fun DayGrid(vm: PlannerViewModel, date: LocalDate, onEdit: (TaskModel) -> Unit, onAddAt: (LocalDate, Int) -> Unit) {
    val context = LocalContext.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val campus by remember { CampusStore.get(context.applicationContext).data }.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val now = rememberNowMinute()
    val today = LocalDate.now()
    val wake = remember(date) { wakeMinute(context, date) }
    val firstHour = (minOf(settings.dayStartMinute, wake ?: 24 * 60) / 60).coerceIn(0, 23)
    val startMin = firstHour * 60
    val pxPerMin = with(density) { HourHeight.toPx() } / 60f
    val events = remember(tasks, campus, date) {
        TimelineBuilder.build(date, tasks, campus, null, startMin, 24 * 60, minFreeGap = Int.MAX_VALUE)
            .filter { it.kind == EventKind.CLASS || it.kind == EventKind.MEAL || it.kind == EventKind.TASK }
    }
    val deadlines = campus.deadlines.filter { !it.done && it.date == date.toString() }
    val scroll = rememberScrollState()
    val busyMinutes = events.sumOf { it.minutes }
    val free = TimelineBuilder.freeMinutes(TimelineBuilder.build(date, tasks, campus, wake, maxOf(startMin, settings.dayStartMinute), settings.dayEndMinute))
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(date) {
        val target = if (date == today) now - 90 else (events.minOfOrNull { it.start } ?: (9 * 60)) - 30
        scroll.animateScrollTo(((target - startMin).coerceAtLeast(0) * pxPerMin).roundToInt())
    }

    Column {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("${span(free)} free", fontWeight = FontWeight.Bold)
                Text("${span(busyMinutes)} planned", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = {
                val busy = events.filter { it.kind != EventKind.TASK }.map { it.start to it.end }
                val from = if (date == today) now else 0
                val plan = AutoPlanner.plan(tasks.filter { it.date == date }, busy, settings.dayStartMinute, settings.dayEndMinute, from)
                plan.forEach { (id, se) -> tasks.firstOrNull { it.id == id }?.let { t -> if (t.startMinute != se.first) vm.moveTask(t, se.first, se.second) } }
                status = if (plan.isEmpty()) "Nothing to arrange" else "Arranged ${plan.size} around classes and meals"
            }) { Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Auto-plan") }
            status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
        BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = 16.dp, bottom = 96.dp)) {
            val hours = 24 - firstHour
            val gridHeight = HourHeight * hours
            val colWidth = maxWidth - LabelWidth - 12.dp
            val line = MaterialTheme.colorScheme.outlineVariant
            // Hour lines and labels; taps on empty space create a task at that time.
            Box(Modifier.fillMaxWidth().height(gridHeight).pointerInput(date, startMin) {
                detectTapGestures { o ->
                    val m = (startMin + o.y / pxPerMin).roundToInt() / 15 * 15
                    onAddAt(date, m.coerceIn(0, 24 * 60 - 15))
                }
            }) {
                (0 until hours).forEach { h ->
                    Row(Modifier.offset(y = HourHeight * h).fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(hhmm((firstHour + h) * 60), Modifier.width(LabelWidth).padding(start = 12.dp).offset(y = (-7).dp), style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
                        Box(Modifier.weight(1f).padding(end = 12.dp).height(1.dp).background(line))
                    }
                }
            }
            GridLayout.lanes(events).forEach { p ->
                val e = p.event
                val w = colWidth / p.lanes
                val top = HourHeight * ((e.start - startMin) / 60f)
                val h = (HourHeight * (e.minutes.coerceAtLeast(20) / 60f))
                Block(p, Modifier.offset(x = LabelWidth + w * p.lane, y = top).width(w).height(h).padding(1.5.dp), pxPerMin,
                    onEdit = onEdit,
                    onToggle = { t -> vm.toggleComplete(t, !t.completed) },
                    onMove = { t, delta -> haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.moveTask(t, (t.startMinute + delta).coerceAtLeast(0), (t.endMinute + delta).coerceAtMost(24 * 60 - 1)) },
                    onResize = { t, delta -> vm.resizeTask(t, (t.endMinute + delta).coerceIn(t.startMinute + 15, 24 * 60 - 1)) })
            }
            deadlines.forEach { d ->
                Row(Modifier.offset(x = LabelWidth, y = HourHeight * ((d.minute - startMin) / 60f) - 9.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Flag, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Text(" ${d.label}: ${d.title}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, maxLines = 1)
                }
            }
            if (date == today && now >= startMin) {
                val red = MaterialTheme.colorScheme.error
                Row(Modifier.offset(y = HourHeight * ((now - startMin) / 60f) - 4.dp).fillMaxWidth().padding(start = LabelWidth - 5.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(red))
                    Box(Modifier.weight(1f).height(2.dp).background(red))
                }
            }
        }
    }
}

@Composable
private fun Block(p: Placed, modifier: Modifier, pxPerMin: Float, onEdit: (TaskModel) -> Unit, onToggle: (TaskModel) -> Unit, onMove: (TaskModel, Int) -> Unit, onResize: (TaskModel, Int) -> Unit) {
    val e = p.event
    val t = e.task
    val tint = Color(e.color)
    var drag by remember(e.key, e.start) { mutableFloatStateOf(0f) }
    var stretch by remember(e.key, e.end) { mutableFloatStateOf(0f) }
    var lifted by remember { mutableStateOf(false) }
    val snap = { px: Float -> (px / pxPerMin / 15f).roundToInt() * 15 }
    Box(modifier.offset { IntOffset(0, drag.roundToInt()) }.zIndex(if (lifted) 1f else 0f)) {
        Column(
            Modifier.matchParentSize()
                .graphicsLayer { scaleX = if (lifted) 1.03f else 1f; scaleY = if (lifted) 1.03f else 1f }
                .shadow(if (lifted) 10.dp else 0.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(if (e.kind == EventKind.TASK) tint.copy(alpha = if (e.done) .18f else .30f) else tint.copy(alpha = .14f))
                .then(if (e.kind == EventKind.MEAL) Modifier.border(1.dp, tint.copy(alpha = .4f), RoundedCornerShape(12.dp)) else Modifier)
                .then(if (t != null) Modifier.clickable { onEdit(t) }.pointerInput(t.id, t.startMinute) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { lifted = true },
                        onDrag = { c, amount -> c.consume(); drag += amount.y },
                        onDragEnd = { lifted = false; val d = snap(drag); if (d != 0) onMove(t, d) else drag = 0f },
                        onDragCancel = { lifted = false; drag = 0f }
                    )
                } else Modifier)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(14.dp).clip(CircleShape).background(tint))
                Text(" " + e.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (e.done && t != null) TextDecoration.LineThrough else null)
                if (t != null && p.lanes <= 2) Icon(if (t.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, "Done", Modifier.size(18.dp).clickable { onToggle(t) }, tint = tint)
            }
            val shiftedStart = e.start + snap(drag)
            val shiftedEnd = e.end + snap(drag) + snap(stretch)
            if (e.minutes >= 40 || drag != 0f) Text("${hhmm(shiftedStart)}–${hhmm(shiftedEnd)}" + if (e.detail.isNotBlank() && p.lanes == 1) " · ${e.detail}" else "",
                style = MaterialTheme.typography.labelSmall, color = Chronora.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (t != null) Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(14.dp).pointerInput(t.id, t.endMinute) {
                detectVerticalDragGestures(
                    onVerticalDrag = { c, a -> c.consume(); stretch += a },
                    onDragEnd = { val d = snap(stretch); if (d != 0) onResize(t, d) else stretch = 0f }
                )
            }, contentAlignment = Alignment.Center
        ) { Box(Modifier.width(26.dp).height(3.dp).clip(CircleShape).background(tint.copy(alpha = .6f))) }
    }
}

/** Seven narrow columns of the same grid; tap a day to open it. */
@Composable
private fun WeekGrid(vm: PlannerViewModel, anchor: LocalDate, agenda: List<TaskModel>, onOpen: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val campus by remember { CampusStore.get(context.applicationContext).data }.collectAsStateWithLifecycle()
    val start = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
    val days = (0L..6L).map { start.plusDays(it) }
    val firstHour = (settings.dayStartMinute / 60).coerceIn(0, 22)
    val hourH = 40.dp
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.selectDate(anchor.minusWeeks(1)) }) { Icon(Icons.Default.ChevronLeft, "Previous week") }
            Text(start.format(DateTimeFormatter.ofPattern("d MMM")) + " – " + start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM")), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { vm.selectDate(anchor.plusWeeks(1)) }) { Icon(Icons.Default.ChevronRight, "Next week") }
        }
        Row(Modifier.fillMaxWidth().padding(start = 36.dp, end = 8.dp)) {
            days.forEach { d ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onOpen(d) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.name.take(2).lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
                    Text("${d.dayOfMonth}", fontWeight = FontWeight.Bold, color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
            val line = MaterialTheme.colorScheme.outlineVariant
            Column {
                (firstHour until 24).forEach { h ->
                    Row(Modifier.height(hourH)) {
                        Text("%02d".format(h), Modifier.width(36.dp).padding(start = 10.dp), style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
                        Box(Modifier.weight(1f).padding(end = 8.dp).height(1.dp).background(line))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(hourH * (24 - firstHour)).padding(start = 36.dp, end = 8.dp)) {
                days.forEach { d ->
                    val items = agenda.filter { it.date == d }.map { TimelineEvent("t${it.id}", EventKind.TASK, it.title, "", it.startMinute, it.endMinute, it.colorHex, it, it.completed) } +
                        AttendanceEngine.occurrences(campus, d).map { o -> TimelineEvent("c" + o.key, EventKind.CLASS, campus.subjects.firstOrNull { it.id == o.subjectId }?.let { it.code.ifBlank { it.name } } ?: "Class", "", o.start, o.end, campus.subjects.firstOrNull { it.id == o.subjectId }?.colorHex ?: 0xFF55786AL) }
                    val meals = Mess.meals(campus, d)
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(if (d == today) MaterialTheme.colorScheme.primary.copy(alpha = .05f) else Color.Transparent).clickable { onOpen(d) }) {
                        val colW = maxWidth
                        // Meals as quiet bands, so the week isn't seven copies of "Lunch".
                        meals.filter { it.end > firstHour * 60 }.forEach { m ->
                            Box(Modifier.offset(y = hourH * ((m.start - firstHour * 60).coerceAtLeast(0) / 60f)).fillMaxWidth().height(hourH * ((m.end - maxOf(m.start, firstHour * 60)) / 60f))
                                .background(Color(0xFFC98A4B).copy(alpha = .10f)))
                        }
                        GridLayout.lanes(items.filter { it.end > firstHour * 60 }).forEach { p ->
                            val e = p.event
                            val w = colW / p.lanes
                            val top = hourH * ((e.start - firstHour * 60).coerceAtLeast(0) / 60f)
                            val h = hourH * ((e.end - maxOf(e.start, firstHour * 60)).coerceAtLeast(20) / 60f)
                            Box(Modifier.offset(x = w * p.lane, y = top).width(w).height(h).padding(1.dp).clip(RoundedCornerShape(6.dp))
                                .background(Color(e.color).copy(alpha = if (e.kind == EventKind.TASK) (if (e.done) .3f else .6f) else .3f)).padding(horizontal = 3.dp, vertical = 2.dp)) {
                                Text(e.title, style = MaterialTheme.typography.labelSmall, maxLines = if (h >= 36.dp) 2 else 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
