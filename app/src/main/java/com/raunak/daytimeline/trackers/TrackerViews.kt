package com.raunak.daytimeline.trackers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Lets a notification open the Trackers page. */
object TrackerNav { val open = kotlinx.coroutines.flow.MutableStateFlow(false) }

/** Auto trackers' values for [date], refreshed every minute while visible. */
@Composable
private fun rememberAutoValues(trackers: List<Tracker>, date: LocalDate): Map<Long, Double> {
    val context = LocalContext.current
    var values by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    val auto = trackers.filter { it.auto && !it.archived }
    LaunchedEffect(auto.map { it.id }, date) {
        while (true) {
            values = withContext(Dispatchers.IO) { auto.associate { it.id to TrackerAuto.value(context, it, date) } }
            delay(60_000)
        }
    }
    return values
}

private fun valueFor(t: Tracker, entries: List<TrackerEntry>, auto: Map<Long, Double>, d: LocalDate) =
    if (t.auto) auto[t.id] ?: 0.0 else TrackerEngine.periodValue(t, entries, d)

/** Ring around an emoji: the tracker's progress at a glance. */
@Composable
fun TrackerRing(t: Tracker, progress: Float, met: Boolean, size: Dp = 52.dp, stroke: Dp = 5.dp) {
    val p by animateFloatAsState(progress, tween(700), label = "tracker")
    val c = Color(t.color)
    val over = t.goal == TrackerGoal.AT_MOST && !met
    val track = MaterialTheme.colorScheme.surfaceVariant
    val ring = if (over) Chronora.colors.bad else c
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val sz = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), sz, style = Stroke(w))
            drawArc(ring, -90f, 360f * p, false, Offset(w / 2, w / 2), sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Box(Modifier.size(size - stroke * 3).clip(CircleShape).background(if (met && t.goal != TrackerGoal.AT_MOST) c else c.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            if (met && t.goal == TrackerGoal.AT_LEAST && t.type == TrackerType.CHECK) Icon(Icons.Default.Check, null, tint = Color.White)
            else Text(t.emoji, fontSize = (size.value * .36f).sp)
        }
    }
}

/** Today's manual trackers as tappable rings, for the Today tab. Tap logs the main action, long lists scroll. */
@Composable
fun TrackerStrip(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TrackerStore.get(context) }
    val trackers by store.trackers.collectAsStateWithLifecycle()
    val entries by store.entries.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val auto = rememberAutoValues(trackers, today)
    val haptics = LocalHapticFeedback.current
    val list = trackers.filter { !it.archived && TrackerEngine.scheduled(it, today) }
    if (list.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trackers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${list.count { TrackerEngine.met(it, valueFor(it, entries, auto, today)) }}/${list.size}", style = MaterialTheme.typography.labelLarge, color = Chronora.muted, modifier = Modifier.clickable(onClick = onOpen))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { t ->
                val v = valueFor(t, entries, auto, today)
                Column(Modifier.width(68.dp).clip(MaterialTheme.shapes.small).clickable {
                    val a = TrackerReminders.actions(t).firstOrNull()
                    val before = TrackerEngine.streak(t, entries, today)
                    when {
                        t.auto || t.type == TrackerType.CHOICE || t.type == TrackerType.RATING || a == null -> onOpen()
                        t.type == TrackerType.CHECK -> { val done = store.toggle(t, today); if (done) afterLog(store, t, before, today, "${t.name} ✓") else Feedback.show("${t.name} unticked") }
                        else -> { val e = store.log(t, today, a.second); afterLog(store, t, before, today, "${t.name} ${a.first}") { store.remove(e.id) } }
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TrackerRing(t, TrackerEngine.progress(t, v), TrackerEngine.met(t, v))
                    Text(t.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    if (!t.auto) { val st = TrackerEngine.streak(t, entries, today); if (st > 1) Text("🔥 $st", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF7A1A), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** All trackers, grouped; pick a day, log with one tap, open one for its history. */
@Composable
fun TrackersScreen() {
    val context = LocalContext.current
    val store = remember { TrackerStore.get(context) }
    val trackers by store.trackers.collectAsStateWithLifecycle()
    val entries by store.entries.collectAsStateWithLifecycle()
    var date by remember { mutableStateOf(LocalDate.now()) }
    val auto = rememberAutoValues(trackers, date)
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tracker?>(null) }
    var detail by remember { mutableStateOf<Long?>(null) }
    var custom by remember { mutableStateOf(false) }
    var askReminders by remember { mutableStateOf<List<Tracker>?>(null) }
    var celebrations by remember { mutableStateOf(false) }
    val active = trackers.filter { !it.archived }
    val due = active.filter { TrackerEngine.scheduled(it, date) }
    val onTrack = due.count { TrackerEngine.met(it, valueFor(it, entries, auto, date)) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.Default.ChevronLeft, "Previous day") }
                    Text(com.raunak.daytimeline.productivity.relativeDay(date) + " · " + date.format(DateTimeFormatter.ofPattern("d MMM")), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { date = date.plusDays(1) }, enabled = date < LocalDate.now()) { Icon(Icons.Default.ChevronRight, "Next day") }
                    IconButton(onClick = { celebrations = true }) { Icon(Icons.Default.Celebration, "Celebrations") }
                }
            }
            if (due.isNotEmpty()) item {
                val today = LocalDate.now()
                val streaks = active.filter { !it.auto }.map { it to TrackerEngine.streak(it, entries, today) }
                val top = streaks.maxByOrNull { it.second }
                val left = due.size - onTrack
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(when { left == 0 -> "Perfect day! 🎉"; onTrack == 0 -> "Let's start"; else -> "$left to go" }, color = Chronora.colors.onHero, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(when { left == 0 -> "Every tracker done. Enjoy it."; onTrack == 0 -> "One small win starts the chain."; else -> "$onTrack of ${due.size} done · you've got this" }, color = Chronora.colors.heroMuted)
                        }
                        if (top != null && top.second > 0) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔥", fontSize = 34.sp)
                            Text("${top.second}", color = Chronora.colors.onHero, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(top.first.name, color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    LinearProgressIndicator(progress = { onTrack.toFloat() / due.size }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Chronora.colors.heroAccent, trackColor = Color.White.copy(alpha = .18f))
                }
            }
            if (active.isEmpty()) item { EmptyState("No trackers yet", "Add food, water, walks or your own.") }
            active.groupBy { it.group.ifBlank { "Other" } }.forEach { (group, list) ->
                item(key = "g$group") { Text(group, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
                items(list, key = { it.id }) { t -> TrackerCard(t, valueFor(t, entries, auto, date), TrackerEngine.scheduled(t, date), store, entries, date) { detail = t.id } }
            }
        }
        ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Tracker") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    }

    if (adding) TemplateSheet(onClose = { adding = false }, onCustom = { adding = false; custom = true }) { pack ->
        val meals = runCatching {
            val data = com.raunak.daytimeline.campus.CampusStore.get(context).data.value
            data.settings.meals.associate { it.name to (it.start to it.end) }
        }.getOrDefault(emptyMap())
        val made = pack.trackers(System.currentTimeMillis(), meals)
        store.saveAll(made); TrackerReminders.scheduleAll(context)
        adding = false
        askReminders = made.filter { !it.auto }.ifEmpty { null }
        if (askReminders == null) Feedback.show("Added ${made.size} tracker${if (made.size > 1) "s" else ""}")
    }
    if (custom) TrackerEditor(null, existingGroups = trackers.map { it.group }.filter { it.isNotBlank() }.distinct(), onClose = { custom = false }) { t -> store.save(t); TrackerReminders.scheduleAll(context); custom = false; if (!t.auto && t.reminders.isEmpty()) askReminders = listOf(t) else Feedback.show("${t.emoji} ${t.name} added. Day one starts now!") }
    editing?.let { e -> TrackerEditor(e, trackers.map { it.group }.filter { it.isNotBlank() }.distinct(), onClose = { editing = null }, onDelete = { TrackerReminders.cancelAll(context, e); store.delete(e.id); editing = null; detail = null }) { t ->
        TrackerReminders.cancelAll(context, e); store.save(t); TrackerReminders.scheduleAll(context); editing = null } }
    if (celebrations) CelebrationSettings { celebrations = false }
    askReminders?.let { list ->
        ReminderPrompt(list, close = { askReminders = null; Feedback.show("Added. Day one starts now! 💪") }) { updated ->
            list.forEach { TrackerReminders.cancelAll(context, it) }
            store.saveAll(updated); TrackerReminders.scheduleAll(context); askReminders = null
            Feedback.show("⏰ Reminders set. Day one starts now!")
        }
    }
    detail?.let { id -> trackers.firstOrNull { it.id == id }?.let { t -> TrackerDetail(t, entries, store, auto[t.id], date, onEdit = { editing = t }) { detail = null } } }
}

@Composable
private fun TrackerCard(t: Tracker, value: Double, due: Boolean, store: TrackerStore, entries: List<TrackerEntry>, date: LocalDate, onOpen: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val met = TrackerEngine.met(t, value)
    val today = LocalDate.now()
    val streak = if (t.auto) 0 else TrackerEngine.streak(t, entries, today)
    val atRisk = if (t.auto || date != today) 0 else TrackerEngine.atRisk(t, entries, today)
    val rescue = if (t.auto || date != today) 0 else TrackerEngine.rescuable(t, entries, today)
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackerRing(t, TrackerEngine.progress(t, value), met)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val target = TrackerEngine.targetText(t)
                    Text(TrackerEngine.format(t, value) + (if (target.isNotBlank()) " / $target" else "") + (if (!due) " · day off" else "") + (if (t.auto) " · auto" else ""),
                        style = MaterialTheme.typography.bodySmall, color = if (t.goal == TrackerGoal.AT_MOST && !met) Chronora.colors.bad else Chronora.muted)
                }
                if (!t.auto) StreakBadge(streak, atRisk > 0, Modifier.padding(end = 8.dp))
                if (t.type == TrackerType.CHECK && !t.auto) {
                    val c = Color(t.color)
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (value > 0) c else Color.Transparent).border(2.dp, c, CircleShape)
                        .clickable {
                            val before = TrackerEngine.streak(t, entries, today)
                            val done = store.toggle(t, date); haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (done) afterLog(store, t, before, date, "${t.name} ✓") else Feedback.show("${t.name} unticked")
                        }, contentAlignment = Alignment.Center) {
                        if (value > 0) Icon(Icons.Default.Check, "Done", tint = Color.White)
                    }
                }
            }
            if (!t.auto) StreakChain(t, TrackerEngine.chain(t, entries, today))
            StreakNote(t, streak, atRisk, rescue) {
                store.save(t.copy(frozen = t.frozen + today.minusDays(1).toString()))
                Feedback.show("❄️ Streak saved! ${TrackerEngine.freezesLeft(t, today) - 1} freezes left this month")
            }
            if (!t.auto && t.type != TrackerType.CHECK) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val logged = TrackerEngine.dayEntries(t, entries, date)
                TrackerReminders.actions(t).forEach { (label, v, choice) ->
                    val picked = t.type == TrackerType.CHOICE && logged.any { it.choice == choice }
                    val good = choice == null || t.goodChoices.isEmpty() || choice in t.goodChoices
                    FilterChip(picked, {
                        if (t.type == TrackerType.CHOICE && picked) logged.filter { it.choice == choice }.forEach { store.remove(it.id) }
                        else {
                            val before = TrackerEngine.streak(t, entries, today)
                            // A meal gets one answer: picking another replaces it.
                            if (t.type == TrackerType.CHOICE || t.type == TrackerType.RATING) logged.forEach { store.remove(it.id) }
                            val e = store.log(t, date, v, choice)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            afterLog(store, t, before, date, "${t.name} · $label") { store.remove(e.id) }
                        }
                    }, label = { Text(label) }, shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = (if (good) Color(t.color) else Chronora.colors.bad).copy(alpha = .2f)))
                }
            }
        }
    }
}

/** History of one tracker: ring, streak, last 30 days and the chosen day's entries. */
@Composable
private fun TrackerDetail(t: Tracker, entries: List<TrackerEntry>, store: TrackerStore, autoToday: Double?, date: LocalDate, onEdit: () -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()
    var history by remember(t.id) { mutableStateOf<List<Pair<LocalDate, Double>>>(emptyList()) }
    LaunchedEffect(t.id, entries) {
        history = withContext(Dispatchers.IO) {
            (29L downTo 0L).map { today.minusDays(it) }.map { d -> d to if (t.auto) TrackerAuto.value(context, t, d) else TrackerEngine.value(t, TrackerEngine.dayEntries(t, entries, d)) }
        }
    }
    val v = if (t.auto) autoToday ?: 0.0 else TrackerEngine.periodValue(t, entries, date)
    val streak = TrackerEngine.streak(t, entries, today) { d -> if (t.auto) history.firstOrNull { it.first == d }?.second ?: 0.0 else TrackerEngine.periodValue(t, entries, d) }
    FullScreenPage(t.name, close, accent = Color(t.color), actions = { IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") } }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrackerRing(t, TrackerEngine.progress(t, v), TrackerEngine.met(t, v), 140.dp, 12.dp)
                    Text(TrackerEngine.format(t, v), style = MaterialTheme.typography.headlineMedium)
                    val target = TrackerEngine.targetText(t)
                    if (target.isNotBlank()) Text("${t.goal.label.lowercase()} $target ${t.period.label.lowercase()}", color = Chronora.muted)
                    if (!t.auto) {
                        val best = TrackerEngine.bestStreak(t, entries, today)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("Streak", "🔥 $streak", Modifier.weight(1f))
                            StatTile("Best", "🏆 ${maxOf(best, streak)}", Modifier.weight(1f))
                            if (t.period == TrackerPeriod.DAY) StatTile("Freezes", "❄️ ${TrackerEngine.freezesLeft(t, today)}", Modifier.weight(1f))
                        }
                        if (t.why.isNotBlank()) Text("“${t.why}”", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
            }
            item {
                SectionCard("Last 30 days", icon = Icons.Default.BarChart) {
                    val max = (history.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(if (t.goal == TrackerGoal.NONE) 1.0 else t.target).coerceAtLeast(1.0)
                    val c = Color(t.color); val bad = Chronora.colors.bad; val off = MaterialTheme.colorScheme.surfaceVariant
                    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                        history.forEach { (d, x) ->
                            val ok = TrackerEngine.met(t, x)
                            Box(Modifier.weight(1f).fillMaxHeight((x / max).toFloat().coerceIn(.03f, 1f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(when { x <= 0 && t.goal != TrackerGoal.AT_MOST -> off; ok -> c; t.goal == TrackerGoal.AT_MOST -> bad; else -> c.copy(alpha = .45f) }))
                        }
                    }
                    val days = history.count { TrackerEngine.met(t, it.second) && TrackerEngine.scheduled(t, it.first) }
                    Text("$days of 30 days met", style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
                }
            }
            if (!t.auto) {
                val dayList = TrackerEngine.dayEntries(t, entries, date).sortedByDescending { it.minute }
                item { Text(com.raunak.daytimeline.productivity.relativeDay(date), style = MaterialTheme.typography.titleMedium) }
                if (dayList.isEmpty()) item { Text("Nothing logged", color = Chronora.muted) }
                items(dayList, key = { it.id }) { e ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("%02d:%02d".format(e.minute / 60, e.minute % 60), style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                        Text("   " + (e.choice ?: if (t.type == TrackerType.CHECK) "Done" else TrackerEngine.format(t, e.value)), Modifier.weight(1f))
                        val late = t.windowStart >= 0 && (e.minute < t.windowStart || e.minute > t.windowEnd)
                        if (late) Text("outside time", style = MaterialTheme.typography.labelSmall, color = Chronora.colors.warn)
                        IconButton(onClick = { store.remove(e.id); Feedback.show("Entry removed", undo = { store.restore(e) }) }) { Icon(Icons.Default.Close, "Remove") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateSheet(onClose: () -> Unit, onCustom: () -> Unit, onPick: (TrackerTemplates.Pack) -> Unit) {
    FullScreenPage("New tracker", onClose) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onCustom).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Text("  Build your own", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
            item { Text("Ready-made", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
            items(TrackerTemplates.packs) { p ->
                val sample = p.trackers(0, emptyMap())
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).clickable { onPick(p) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.emoji, fontSize = 28.sp)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(sample.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodySmall, color = Chronora.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.Add, "Add", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private val Emojis = listOf("✅", "💧", "🍎", "🍳", "🍛", "🍲", "🚶", "🏃", "💪", "🧘", "😴", "📖", "🎯", "📵", "📱", "💊", "🦷", "🧹", "💰", "🙂", "📝", "🎸", "🌙", "☀️")
private val Colors = listOf(0xFF4F5BD5, 0xFF7C5CE6, 0xFFEF6A45, 0xFF0E9F9A, 0xFFDB8F12, 0xFFE5486B, 0xFF2F8FE0, 0xFF22A06B, 0xFF5B6BB0)

/** Every part of a tracker is editable: what, how it's logged, the target, when, reminders and source. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackerEditor(initial: Tracker?, existingGroups: List<String>, onClose: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (Tracker) -> Unit) {
    var t by remember { mutableStateOf(initial ?: Tracker(System.currentTimeMillis(), "")) }
    var pickTime by remember { mutableStateOf<String?>(null) } // "rem" | "ws" | "we"
    var pickApps by remember { mutableStateOf(false) }
    FullScreenPage(if (initial == null) "New tracker" else "Edit tracker", onClose, accent = Color(t.color), actions = {
        TextButton(enabled = t.name.isNotBlank(), onClick = { onSave(t.copy(name = t.name.trim())) }) { Text("Save") }
    }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(60.dp).clip(CircleShape).background(Color(t.color).copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text(t.emoji, fontSize = 28.sp) }
                    OutlinedTextField(t.name, { t = t.copy(name = it) }, Modifier.weight(1f).padding(start = 12.dp), placeholder = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Emojis.forEach { e -> Box(Modifier.size(42.dp).clip(CircleShape).background(if (t.emoji == e) Color(t.color).copy(alpha = .25f) else Color.Transparent).clickable { t = t.copy(emoji = e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 22.sp) } }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Colors.forEach { c -> Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c)).border(3.dp, if (t.color == c) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape).clickable { t = t.copy(color = c) }) }
                }
            }
            item {
                SectionCard("Group", icon = Icons.Default.Folder) {
                    OutlinedTextField(t.group, { t = t.copy(group = it) }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. Food, Health") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    if (existingGroups.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { existingGroups.forEach { g -> FilterChip(t.group == g, { t = t.copy(group = g) }, label = { Text(g) }) } }
                }
            }
            item {
                SectionCard("Source", icon = Icons.Default.Sensors) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TrackerSource.values().forEach { s -> FilterChip(t.source == s, {
                            t = t.copy(source = s, type = if (s == TrackerSource.MANUAL) t.type else TrackerType.DURATION, unit = if (s == TrackerSource.MANUAL) t.unit else "min",
                                goal = if (s == TrackerSource.SCREEN_TIME || s == TrackerSource.APP_USAGE) TrackerGoal.AT_MOST else t.goal)
                        }, label = { Text(s.label) }) }
                    }
                    if (t.source == TrackerSource.APP_USAGE) OutlinedButton(onClick = { pickApps = true }, shape = RoundedCornerShape(14.dp)) { Text("Apps · ${t.packages.size}") }
                }
            }
            if (!t.auto) item {
                SectionCard("How you log it", icon = Icons.Default.TouchApp) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TrackerType.values().forEach { ty -> FilterChip(t.type == ty, {
                            t = t.copy(type = ty, target = when (ty) { TrackerType.CHECK, TrackerType.CHOICE -> 1.0; TrackerType.RATING -> 3.0; else -> t.target.coerceAtLeast(1.0) },
                                choices = if (ty == TrackerType.CHOICE && t.choices.isEmpty()) listOf("Good", "Okay", "Bad") else t.choices,
                                goodChoices = if (ty == TrackerType.CHOICE && t.goodChoices.isEmpty()) setOf("Good", "Okay") else t.goodChoices)
                        }, label = { Text(ty.label) }) }
                    }
                    if (t.type == TrackerType.AMOUNT || t.type == TrackerType.COUNT) OutlinedTextField(t.unit, { t = t.copy(unit = it.take(12)) }, Modifier.fillMaxWidth(), placeholder = { Text("Unit (ml, km, glasses…)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    if (t.type in setOf(TrackerType.AMOUNT, TrackerType.COUNT, TrackerType.DURATION)) ListEditor("One-tap amounts", t.quickAmounts.map { TrackerEngine.num(it) }, numeric = true) { v -> t = t.copy(quickAmounts = v.mapNotNull { it.toDoubleOrNull() }.filter { it > 0 }) }
                    if (t.type == TrackerType.CHOICE) {
                        ListEditor("Options", t.choices) { v -> t = t.copy(choices = v, goodChoices = t.goodChoices.intersect(v.toSet())) }
                        Text("Counts as a success", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { t.choices.forEach { c -> FilterChip(c in t.goodChoices, { t = t.copy(goodChoices = if (c in t.goodChoices) t.goodChoices - c else t.goodChoices + c) }, label = { Text(c) }) } }
                    }
                }
            }
            item {
                SectionCard("Target", icon = Icons.Default.Flag) {
                    PillTabs(TrackerGoal.values().map { it.label }, t.goal.ordinal) { t = t.copy(goal = TrackerGoal.values()[it]) }
                    PillTabs(TrackerPeriod.values().map { it.label }, t.period.ordinal) { t = t.copy(period = TrackerPeriod.values()[it]) }
                    if (t.goal != TrackerGoal.NONE && !(t.type == TrackerType.CHECK && t.period == TrackerPeriod.DAY)) {
                        val step = when { t.type == TrackerType.DURATION -> 5.0; t.unit == "ml" -> 250.0; t.type == TrackerType.RATING -> 0.5; t.target >= 100 -> 10.0; else -> 1.0 }
                        Stepper(if (t.type == TrackerType.CHECK) "Times" else "Target", if (t.type == TrackerType.CHECK) "${TrackerEngine.num(t.target)}×" else TrackerEngine.targetText(t).removePrefix("≤ "),
                            { t = t.copy(target = (t.target - step).coerceAtLeast(step)) }, { t = t.copy(target = t.target + step) })
                    }
                }
            }
            if (t.period == TrackerPeriod.DAY) item {
                SectionCard("Days", icon = Icons.Default.CalendarMonth) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { d ->
                            val on = d in t.days
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(if (on) Color(t.color) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { t = t.copy(days = if (on && t.days.size > 1) t.days - d else t.days + d) }, contentAlignment = Alignment.Center) {
                                Text(java.time.DayOfWeek.of(d).name.take(1), color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            if (!t.auto) item {
                SectionCard("Reminders & time", icon = Icons.Default.Notifications) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        t.reminders.sorted().forEach { m -> InputChip(false, { t = t.copy(reminders = t.reminders - m) }, label = { Text("%02d:%02d".format(m / 60, m % 60)) }, trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp)) }) }
                        AssistChip(onClick = { pickTime = "rem" }, label = { Text("Add") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Window", Modifier.weight(1f))
                        AssistChip(onClick = { pickTime = "ws" }, label = { Text(if (t.windowStart < 0) "Any time" else "%02d:%02d".format(t.windowStart / 60, t.windowStart % 60)) })
                        if (t.windowStart >= 0) {
                            Text("–")
                            AssistChip(onClick = { pickTime = "we" }, label = { Text("%02d:%02d".format(t.windowEnd / 60, t.windowEnd % 60)) })
                            IconButton(onClick = { t = t.copy(windowStart = -1, windowEnd = -1) }) { Icon(Icons.Default.Close, "Clear window") }
                        }
                    }
                }
            }
            if (!t.auto) item {
                SectionCard("Streak & motivation", icon = Icons.Default.LocalFireDepartment) {
                    OutlinedTextField(t.why, { t = t.copy(why = it.take(120)) }, Modifier.fillMaxWidth(), placeholder = { Text("Why this matters to you") }, shape = RoundedCornerShape(14.dp))
                    if (t.period == TrackerPeriod.DAY) {
                        Text("Streak freezes a month", style = MaterialTheme.typography.bodyLarge)
                        val opts = listOf(0, 1, 2, 3, 5)
                        PillTabs(opts.map { if (it == 0) "None" else "$it" }, opts.indexOf(t.freezesPerMonth).coerceAtLeast(0)) { t = t.copy(freezesPerMonth = opts[it]) }
                    }
                    SwitchRow("Streak saver reminder", t.saverMinute >= 0) { t = t.copy(saverMinute = if (it) 21 * 60 else -1) }
                    if (t.saverMinute >= 0) com.raunak.daytimeline.wellbeing.ClockRow("At", t.saverMinute) { m -> t = t.copy(saverMinute = m) }
                    SwitchRow("Celebrate milestones", t.celebrate) { t = t.copy(celebrate = it) }
                }
            }
            if (onDelete != null) item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onSave(t.copy(archived = !t.archived)) }, shape = RoundedCornerShape(14.dp)) { Text(if (t.archived) "Unarchive" else "Archive") }
                    var confirm by remember { mutableStateOf(false) }
                    TextButton(onClick = { confirm = true }) { Text("Delete", color = Chronora.colors.bad) }
                    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete ${t.name}?") }, text = { Text("Its history goes too.") },
                        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Delete", color = Chronora.colors.bad) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } })
                }
            }
        }
    }
    pickTime?.let { which ->
        val init = when (which) { "ws" -> t.windowStart.takeIf { it >= 0 } ?: 8 * 60; "we" -> t.windowEnd.takeIf { it >= 0 } ?: 9 * 60; else -> 9 * 60 }
        TimeDialog(init, { pickTime = null }) { m ->
            t = when (which) {
                "ws" -> t.copy(windowStart = m, windowEnd = if (t.windowEnd <= m) (m + 60).coerceAtMost(24 * 60 - 1) else t.windowEnd)
                "we" -> t.copy(windowEnd = m.coerceAtLeast(t.windowStart + 5))
                else -> t.copy(reminders = (t.reminders + m).distinct())
            }
            pickTime = null
        }
    }
    if (pickApps) AppsDialog(t.packages, { pickApps = false }) { t = t.copy(packages = it); pickApps = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(initial: Int, close: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initial / 60, initial % 60, true)
    AlertDialog(onDismissRequest = close, text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun AppsDialog(selected: Set<String>, close: () -> Unit, onSave: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }.filter { it.packageName != context.packageName }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }.sortedBy { it.second.lowercase() }
    }
    var chosen by remember { mutableStateOf(selected) }
    AlertDialog(onDismissRequest = close, title = { Text("Apps") }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(apps, key = { it.first }) { (pkg, label) ->
                Row(Modifier.fillMaxWidth().clickable { chosen = if (pkg in chosen) chosen - pkg else chosen + pkg }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.raunak.daytimeline.wellbeing.AppIcon(pkg, 30.dp)
                    Text(label, Modifier.weight(1f).padding(start = 10.dp))
                    Checkbox(pkg in chosen, { chosen = if (pkg in chosen) chosen - pkg else chosen + pkg })
                }
            }
        }
    }, confirmButton = { Button(onClick = { onSave(chosen) }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
