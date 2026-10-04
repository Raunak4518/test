package com.raunak.daytimeline.trackers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.ui.Feedback
import com.raunak.daytimeline.ui.ListEditor
import com.raunak.daytimeline.ui.PillTabs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Gold = Color(0xFFFFC83D)
private val FlameColor = Color(0xFFFF7A1A)

/** Everything the full-screen celebration needs to know about one win. */
data class Celebration(
    val tracker: Tracker,
    val streak: Int,
    val before: Int,
    val best: Int,
    val newBest: Boolean,
    val milestone: Boolean,
    val perfectDay: Boolean,
    val comeback: Boolean,
    val doneToday: Int,
    val dueToday: Int,
    val chain: List<Pair<LocalDate, TrackerEngine.Dot>>,
    val headline: String,
    val line: String
)

/** The win waiting to be shown; [CelebrationHost] shows it wherever the user is. */
object TrackerCelebration { val event = MutableStateFlow<Celebration?>(null) }

/** Headlines and lines for each kind of win. The user's own lines are mixed in. */
object CelebrationCopy {
    fun headline(streak: Int, milestone: Boolean, perfect: Boolean, comeback: Boolean, newBest: Boolean): String = when {
        perfect -> listOf("PERFECT DAY!", "ALL DONE!", "FLAWLESS!").random()
        milestone -> "$streak-DAY MILESTONE!"
        comeback -> listOf("WELCOME BACK!", "THE COMEBACK!", "BACK ON IT!").random()
        newBest && streak > 2 -> listOf("NEW RECORD!", "PERSONAL BEST!").random()
        streak >= 30 -> listOf("LEGENDARY!", "BEAST MODE!", "UNREAL!").random()
        streak >= 7 -> listOf("ON FIRE!", "UNSTOPPABLE!", "CRUSHING IT!").random()
        streak >= 2 -> listOf("AMAZING!", "KEEP IT UP!", "NICE WORK!", "YOU DID IT!").random()
        else -> listOf("DAY ONE!", "LET'S GO!", "GREAT START!").random()
    }

    fun line(t: Tracker, streak: Int, perfect: Boolean, comeback: Boolean, newBest: Boolean, mine: List<String>): String {
        val unit = if (t.period == TrackerPeriod.DAY) "day" else t.period.name.lowercase()
        val built = when {
            perfect -> listOf("Every tracker done today. Take a bow.", "A full day of wins. This is how habits are built.")
            comeback -> listOf("Day 1 of your comeback. The best streaks start again.", "You didn't quit. That's what matters.")
            newBest && streak > 2 -> listOf("$streak ${unit}s, your longest ever. Keep raising the bar.", "You just beat your own record.")
            streak >= 2 -> listOf("$streak ${unit}s in a row. Don't stop now.", "That's $streak ${unit}s straight. Future you says thanks.",
                "${t.name} done. The chain grows to $streak.", "$streak ${unit}s of showing up. That's discipline.")
            else -> listOf("Every streak starts with one. Come back tomorrow for $unit 2.", "${t.name} done. Small steps, big change.")
        }
        return (built + mine.filter { it.isNotBlank() }).random()
    }
}

/**
 * Call after a log. When it completed today's goal, cheers with a snackbar, and opens the full-screen
 * celebration (every win, or milestones only, as set). Otherwise just confirms with [label].
 */
fun afterLog(store: TrackerStore, t: Tracker, before: Int, date: LocalDate, label: String, undo: (() -> Unit)? = null) {
    val today = LocalDate.now()
    if (date != today || t.auto || t.goal == TrackerGoal.AT_MOST) { Feedback.show(label, undo); return }
    val entries = store.entries.value
    val met = TrackerEngine.met(t, TrackerEngine.periodValue(t, entries, today))
    val now = TrackerEngine.streak(t, entries, today)
    if (!met || now <= before) { Feedback.show(label, undo); return }
    val prefs = store.celebration.value
    val bestBefore = TrackerEngine.bestStreak(t, entries.filterNot { it.trackerId == t.id && it.date == today.toString() }, today)
    val due = store.trackers.value.filter { !it.archived && !it.auto && it.goal != TrackerGoal.AT_MOST && TrackerEngine.scheduled(it, today) }
    val done = due.count { TrackerEngine.met(it, TrackerEngine.periodValue(it, entries, today)) }
    val milestone = now in TrackerEngine.milestones
    val perfect = due.size >= 2 && done == due.size
    val comeback = now == 1 && bestBefore >= 3
    val newBest = now > bestBefore
    val show = t.celebrate && when (prefs.mode) { CelebrateMode.EVERY -> true; CelebrateMode.MILESTONES -> milestone || perfect; CelebrateMode.OFF -> false }
    if (show) TrackerCelebration.event.value = Celebration(
        t, now, before, maxOf(bestBefore, now), newBest, milestone, perfect, comeback, done, due.size,
        TrackerEngine.chain(t, entries, today),
        CelebrationCopy.headline(now, milestone, perfect, comeback, newBest),
        CelebrationCopy.line(t, now, perfect, comeback, newBest, prefs.cheers)
    )
    Feedback.show(TrackerEngine.cheer(now, t.name), undo)
}

private data class Particle(val angle: Float, val speed: Float, val size: Float, val color: Color, val spin: Float, val square: Boolean)

/** The full-screen win: light rays, a confetti burst, the ring filling, the streak counting up and the chain lighting. */
@Composable
fun CelebrationHost() {
    val context = LocalContext.current
    val c by TrackerCelebration.event.collectAsStateWithLifecycle()
    val win = c ?: return
    val prefs = remember { TrackerStore.get(context).celebration.value }
    val haptics = LocalHapticFeedback.current
    val close = { TrackerCelebration.event.value = null }
    LaunchedEffect(win) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(450); haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (prefs.autoCloseSeconds > 0) { delay(prefs.autoCloseSeconds * 1000L); close() }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { CelebrationScreen(win, close) }
}

/** The celebration itself, full screen. */
@Composable
fun CelebrationScreen(win: Celebration, close: () -> Unit) {
    val t = win.tracker
    val base = if (win.milestone || win.perfectDay) Gold else Color(t.color)
    val palette = listOf(base, Gold, FlameColor, Color(0xFF22C55E), Color(0xFF4FB3FF), Color(0xFFE5486B), Color.White)
    run {
        // One clock drives the whole show: the burst in the first ~1.6 s, everything else staggered after.
        val clock = remember { Animatable(0f) }
        LaunchedEffect(win) { clock.animateTo(1f, tween(1600, easing = LinearOutSlowInEasing)) }
        var stage by remember { mutableIntStateOf(0) }
        LaunchedEffect(win) { for (i in 1..5) { delay(220); stage = i } }
        val spin by rememberInfiniteTransition(label = "rays").animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "spin")
        val glow by rememberInfiniteTransition(label = "glow").animateFloat(.85f, 1.08f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g")
        val fall by rememberInfiniteTransition(label = "fall").animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "f")
        val particles = remember(win) { List(90) { Particle(Random.nextFloat() * 6.283f, .35f + Random.nextFloat() * .75f, 4f + Random.nextFloat() * 7f, palette.random(), Random.nextFloat() * 720f, Random.nextBoolean()) } }
        val rain = remember(win) { List(40) { Triple(Random.nextFloat(), Random.nextFloat(), palette.random()) } }

        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerp(base, Color.Black, .55f), lerp(base, Color.Black, .25f), lerp(base, Color.Black, .6f))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)) {
            // Rotating light rays behind everything.
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height * .36f)
                rotate(spin, center) {
                    for (i in 0 until 14) {
                        val a = i * (6.283f / 14)
                        val r = size.maxDimension
                        val p = Path().apply {
                            moveTo(center.x, center.y)
                            lineTo(center.x + r * cos(a - .09f), center.y + r * sin(a - .09f))
                            lineTo(center.x + r * cos(a + .09f), center.y + r * sin(a + .09f))
                            close()
                        }
                        drawPath(p, Color.White.copy(alpha = .06f))
                    }
                }
                drawCircle(Brush.radialGradient(listOf(base.copy(alpha = .55f), Color.Transparent), center, size.minDimension * .55f), size.minDimension * .55f, center)
                // The burst: out from the ring, then pulled down.
                val k = clock.value
                particles.forEach { p ->
                    val dist = p.speed * size.minDimension * .7f * k
                    val x = center.x + cos(p.angle) * dist
                    val y = center.y + sin(p.angle) * dist + 900f * k * k * p.speed * .6f
                    val alpha = (1f - k).coerceIn(0f, 1f) * .95f + .05f
                    if (k < 1f) {
                        if (p.square) rotate(p.spin * k, Offset(x, y)) { drawRect(p.color.copy(alpha = alpha), Offset(x - p.size, y - p.size / 2), Size(p.size * 2, p.size)) }
                        else drawCircle(p.color.copy(alpha = alpha), p.size, Offset(x, y))
                    }
                }
                // Then a gentle confetti rain.
                if (k > .6f) rain.forEach { (x, off, col) ->
                    val y = ((fall + off) % 1f) * size.height
                    rotate(y, Offset(x * size.width, y)) { drawRect(col.copy(alpha = .8f), Offset(x * size.width, y), Size(14f, 7f)) }
                }
            }

            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(.55f))
                // The ring fills to complete with the emoji popping in.
                val ring by animateFloatAsState(if (stage >= 1) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "ring")
                val pop by animateFloatAsState(if (stage >= 1) 1f else .2f, spring(dampingRatio = .38f, stiffness = 320f), label = "pop")
                Box(Modifier.size(196.dp).scale(glow), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 14.dp.toPx(); val sz = Size(size.width - w, size.height - w)
                        drawArc(Color.White.copy(alpha = .18f), 0f, 360f, false, Offset(w / 2, w / 2), sz, style = Stroke(w))
                        drawArc(Brush.sweepGradient(listOf(Gold, FlameColor, base, Gold)), -90f, 360f * ring, false, Offset(w / 2, w / 2), sz, style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Box(Modifier.size(140.dp).scale(pop).clip(CircleShape).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                        Text(if (win.perfectDay) "🏆" else if (win.milestone) "🏅" else t.emoji, fontSize = 72.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                AnimatedVisibility(stage >= 2, enter = scaleIn(spring(dampingRatio = .45f, stiffness = 300f), initialScale = .3f) + fadeIn()) {
                    Text(win.headline, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 44.sp)
                }
                AnimatedVisibility(stage >= 2, enter = fadeIn(tween(500)) + slideInVertically { it / 2 }) {
                    Text(win.line, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(22.dp))
                AnimatedVisibility(stage >= 3, enter = fadeIn() + scaleIn(initialScale = .7f)) {
                    StreakCounter(win)
                }
                Spacer(Modifier.height(18.dp))
                AnimatedVisibility(stage >= 4, enter = fadeIn() + slideInVertically { it }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CelebrationChain(win, base)
                        val next = TrackerEngine.milestones.firstOrNull { it > win.streak }
                        if (next != null) {
                            val prev = TrackerEngine.milestones.lastOrNull { it <= win.streak } ?: 0
                            val p by animateFloatAsState((win.streak - prev).toFloat() / (next - prev), tween(900), label = "next")
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row { Text("Next: 🏅 $next days", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)); Text("${next - win.streak} to go", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelLarge) }
                                LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)), color = Gold, trackColor = Color.White.copy(alpha = .2f), drawStopIndicator = {})
                            }
                        }
                        if (win.dueToday > 1) Text("Today ${win.doneToday} of ${win.dueToday} done" + if (win.doneToday < win.dueToday) " · ${win.dueToday - win.doneToday} to go" else " 🎉",
                            color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(stage >= 5, enter = fadeIn()) {
                    Button(onClick = close, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = lerp(base, Color.Black, .35f))) {
                        Text("Keep going 🔥", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

/** The flame with the number rolling up from the old streak to the new one, plus best and NEW BEST. */
@Composable
private fun StreakCounter(win: Celebration) {
    var target by remember { mutableIntStateOf(win.before) }
    LaunchedEffect(win) { delay(150); target = win.streak }
    val shown by animateIntAsState(target, tween(700, easing = FastOutSlowInEasing), label = "count")
    val flame by rememberInfiniteTransition(label = "flame").animateFloat(.92f, 1.12f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "fl")
    val unit = if (win.tracker.period == TrackerPeriod.DAY) "day" else win.tracker.period.name.lowercase()
    Row(Modifier.clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = .14f)).padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🔥", fontSize = 44.sp, modifier = Modifier.scale(flame))
        Column(Modifier.padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$shown", color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Black)
                Text("  $unit streak", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (win.newBest && win.streak > 1) {
                val shimmer by rememberInfiniteTransition(label = "best").animateFloat(.6f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "b")
                Text("★ NEW BEST", color = Color(0xFF4A2B00), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Gold.copy(alpha = shimmer)).padding(horizontal = 8.dp, vertical = 2.dp))
            } else Text("Best ${win.best}", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The last seven days with today's dot popping in last. */
@Composable
private fun CelebrationChain(win: Celebration, base: Color) {
    var lit by remember { mutableStateOf(false) }
    LaunchedEffect(win) { delay(350); lit = true }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        win.chain.forEachIndexed { i, (d, dot) ->
            val today = i == win.chain.lastIndex
            val s by animateFloatAsState(if (!today || lit) 1f else .3f, spring(dampingRatio = .35f, stiffness = 380f), label = "dot")
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val done = dot == TrackerEngine.Dot.DONE || (today && lit)
                Box(Modifier.size(if (today) 38.dp else 30.dp).scale(s).clip(CircleShape)
                    .background(when { done -> if (today) Gold else base; dot == TrackerEngine.Dot.FROZEN -> Color(0xFF4FB3FF).copy(alpha = .5f); else -> Color.White.copy(alpha = .15f) })
                    .border(if (today) 2.dp else 0.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Text(when { done -> "✓"; dot == TrackerEngine.Dot.FROZEN -> "❄"; else -> "" }, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(if (today) "Today" else d.dayOfWeek.name.take(1), color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** How celebrations behave: when they show, how long they stay, and your own cheer lines. */
@Composable
fun CelebrationSettings(close: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TrackerStore.get(context) }
    val p by store.celebration.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = close, title = { Text("🎉 Celebrations") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Show the full-screen win", style = MaterialTheme.typography.bodyLarge)
            PillTabs(CelebrateMode.values().map { it.label }, p.mode.ordinal) { store.saveCelebration(p.copy(mode = CelebrateMode.values()[it])) }
            Text("Closes by itself after", style = MaterialTheme.typography.bodyLarge)
            val opts = listOf(0, 4, 6, 10)
            PillTabs(opts.map { if (it == 0) "Tap" else "${it}s" }, opts.indexOf(p.autoCloseSeconds).coerceAtLeast(0)) { store.saveCelebration(p.copy(autoCloseSeconds = opts[it])) }
            ListEditor("Your own cheer lines", p.cheers) { store.saveCelebration(p.copy(cheers = it)) }
            TextButton(onClick = {
                val t = store.trackers.value.firstOrNull { !it.archived } ?: Tracker(0, "Preview", "⭐")
                TrackerCelebration.event.value = Celebration(t, 7, 6, 7, true, true, false, false, 3, 4,
                    (6L downTo 0L).map { LocalDate.now().minusDays(it) to TrackerEngine.Dot.DONE },
                    CelebrationCopy.headline(7, true, false, false, true), CelebrationCopy.line(t, 7, false, false, true, p.cheers))
                close()
            }) { Text("Preview") }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("Done") } })
}
