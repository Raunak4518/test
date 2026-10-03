package com.raunak.daytimeline.trackers

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.ui.Chronora
import com.raunak.daytimeline.ui.Feedback
import com.raunak.daytimeline.ui.TextChip
import com.raunak.daytimeline.wellbeing.ClockDialog
import com.raunak.daytimeline.wellbeing.ClockRow
import com.raunak.daytimeline.wellbeing.clockText
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

private val Flame = Color(0xFFFF7A1A)
private val Ice = Color(0xFF4FB3FF)

/** A milestone to celebrate, shown by [CelebrationHost] wherever it happened. */
object TrackerCelebration { val event = MutableStateFlow<Pair<Tracker, Int>?>(null) }

/**
 * Call after a log: cheers with the new streak when the log completed today's goal, and opens the big
 * celebration on milestones. Otherwise just confirms with [label].
 */
fun afterLog(store: TrackerStore, t: Tracker, before: Int, date: LocalDate, label: String, undo: (() -> Unit)? = null) {
    val today = LocalDate.now()
    if (date != today || t.auto) { Feedback.show(label, undo); return }
    val entries = store.entries.value
    val met = TrackerEngine.met(t, TrackerEngine.periodValue(t, entries, today))
    val now = TrackerEngine.streak(t, entries, today)
    if (met && now > before && t.goal != TrackerGoal.AT_MOST) {
        if (t.celebrate && now in TrackerEngine.milestones) TrackerCelebration.event.value = t to now
        Feedback.show(TrackerEngine.cheer(now, t.name), undo)
    } else Feedback.show(label, undo)
}

/** The flame and count; grey at zero, flickering when today still needs doing. */
@Composable
fun StreakBadge(streak: Int, atRisk: Boolean, modifier: Modifier = Modifier) {
    val flicker by rememberInfiniteTransition(label = "flame").animateFloat(1f, if (atRisk) .45f else 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "flicker")
    val on = streak > 0
    Row(modifier.clip(RoundedCornerShape(50)).background(if (on) Flame.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("🔥", fontSize = 15.sp, modifier = Modifier.alpha(if (on) flicker else .35f))
        Text(" $streak", fontWeight = FontWeight.Bold, color = if (on) Flame else Chronora.muted, style = MaterialTheme.typography.labelLarge)
    }
}

/** Seven dots for the last seven days: filled = done, ice = frozen, faint = day off, ring = today still open. */
@Composable
fun StreakChain(t: Tracker, chain: List<Pair<LocalDate, TrackerEngine.Dot>>) {
    val c = Color(t.color)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        chain.forEachIndexed { i, (d, dot) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val m = Modifier.size(24.dp).clip(CircleShape)
                when (dot) {
                    TrackerEngine.Dot.DONE -> Box(m.background(c), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    TrackerEngine.Dot.FROZEN -> Box(m.background(Ice.copy(alpha = .2f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.AcUnit, "Frozen", Modifier.size(14.dp), tint = Ice) }
                    TrackerEngine.Dot.TODAY_OPEN -> Box(m.border(2.dp, c, CircleShape))
                    TrackerEngine.Dot.MISSED -> Box(m.background(MaterialTheme.colorScheme.surfaceVariant))
                    TrackerEngine.Dot.OFF, TrackerEngine.Dot.BEFORE -> Box(m.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)))
                }
                Text(if (i == chain.lastIndex) "Today" else d.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
            }
        }
    }
}

/** Shows the milestone celebration from anywhere in the app. */
@Composable
fun CelebrationHost() {
    val event by TrackerCelebration.event.collectAsStateWithLifecycle()
    val (t, n) = event ?: return
    Dialog(onDismissRequest = { TrackerCelebration.event.value = null }) {
        var start by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { start = true }
        val scale by animateFloatAsState(if (start) 1f else .4f, spring(dampingRatio = .45f, stiffness = 260f), label = "scale")
        val fall by rememberInfiniteTransition(label = "confetti").animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "fall")
        val pieces = remember { List(36) { Triple(Math.random().toFloat(), Math.random().toFloat(), listOf(Flame, Color(t.color), Color(0xFFFFD23F), Color(0xFF22C55E), Ice).random()) } }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface)) {
            Canvas(Modifier.matchParentSize()) {
                pieces.forEach { (x, offset, col) ->
                    val y = ((fall + offset) % 1f) * size.height
                    drawCircle(col, radius = 5.dp.toPx(), center = Offset(x * size.width, y))
                }
            }
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🔥", fontSize = 76.sp, modifier = Modifier.scale(scale))
                Text("$n-day streak!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Flame)
                Text("${t.emoji} ${t.name}", style = MaterialTheme.typography.titleMedium)
                Text(TrackerEngine.cheer(n, t.name).drop(2).trim(), textAlign = TextAlign.Center, color = Chronora.muted)
                if (t.why.isNotBlank()) Text("“${t.why}”", textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                val next = TrackerEngine.milestones.firstOrNull { it > n }
                if (next != null) Text("Next milestone: $next days", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                Button(onClick = { TrackerCelebration.event.value = null }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Flame)) { Text("Keep going") }
            }
        }
    }
}

/**
 * Asked right after trackers are created: when to be reminded, plus an evening streak saver.
 * Returns the trackers with their reminders set.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderPrompt(trackers: List<Tracker>, close: () -> Unit, onDone: (List<Tracker>) -> Unit) {
    var list by remember { mutableStateOf(trackers.map { t -> if (t.reminders.isEmpty()) t.copy(reminders = listOf(suggestedTime(t))) else t }) }
    var saver by remember { mutableStateOf(true) }
    var saverAt by remember { mutableIntStateOf(21 * 60) }
    var adding by remember { mutableStateOf<Long?>(null) }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("⏰ Want reminders?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("A nudge at the right time makes the streak much easier to keep.", color = Chronora.muted)
            LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { t ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${t.emoji}  ${t.name}", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            t.reminders.sorted().forEach { m -> TextChip(clockText(m)) { list = list.map { x -> if (x.id == t.id) x.copy(reminders = x.reminders - m) else x } } }
                            AssistChip(onClick = { adding = t.id }, label = { Text("Add time") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🔥 Streak saver", style = MaterialTheme.typography.titleMedium)
                    Text("One last nudge if a streak is about to break", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                }
                Switch(saver, { saver = it })
            }
            if (saver) ClockRow("Saver at", saverAt) { saverAt = it }
            Button(onClick = { onDone(list.map { it.copy(saverMinute = if (saver) saverAt else -1) }) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Set reminders") }
            TextButton(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
        }
    }
    adding?.let { id ->
        ClockDialog(9 * 60, { adding = null }) { m -> list = list.map { x -> if (x.id == id) x.copy(reminders = (x.reminders + m).distinct()) else x }; adding = null }
    }
}

/** A sensible first reminder: just before the tracker's window ends, else by what it is. */
private fun suggestedTime(t: Tracker): Int = when {
    t.windowEnd >= 0 -> (t.windowEnd - 15).coerceAtLeast(t.windowStart)
    t.type == TrackerType.RATING -> 21 * 60 + 30
    t.name.contains("walk", true) || t.name.contains("morning", true) -> 6 * 60 + 30
    else -> 19 * 60
}

/** The streak line under a card: rescue a missed day, warn when today is still open, or cheer. */
@Composable
fun StreakNote(t: Tracker, streak: Int, atRisk: Int, rescue: Int, onFreeze: () -> Unit) {
    when {
        rescue > 0 -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ice.copy(alpha = .12f)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("❄️ Missed yesterday. Save your $rescue-day streak?", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onFreeze) { Text("Use a freeze", color = Ice, fontWeight = FontWeight.Bold) }
        }
        atRisk > 0 -> Text("🔥 Do it today to keep your $atRisk-day streak", style = MaterialTheme.typography.bodyMedium, color = Flame, fontWeight = FontWeight.Medium)
    }
}
