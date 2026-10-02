package com.raunak.daytimeline.wellbeing

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.raunak.daytimeline.pro.FocusGuardStore
import com.raunak.daytimeline.ui.Chronora
import com.raunak.daytimeline.ui.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun hm(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
private fun clockOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }

/** Colours for the top apps in the donut and bars. */
private val AppColors = listOf(0xFF55786A, 0xFFE07A5F, 0xFF3D85C6, 0xFFE0A33B, 0xFF8E6BBF, 0xFF4DB6AC).map { Color(it) }

/** App icons, loaded once per package. */
private object IconCache {
    private val cache = HashMap<String, ImageBitmap?>()
    fun get(context: android.content.Context, pkg: String): ImageBitmap? = cache.getOrPut(pkg) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
}

@Composable
internal fun AppIcon(pkg: String, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val context = LocalContext.current
    val icon = remember(pkg) { IconCache.get(context, pkg) }
    if (icon != null) Image(icon, null, Modifier.size(size).clip(RoundedCornerShape(10.dp)))
    else Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
}

/** Usage for the last 14 days, refreshed every minute while on screen. */
@Composable
private fun rememberWeekUsage(store: WellbeingStore, granted: Boolean): Map<LocalDate, DayUsage>? {
    val context = LocalContext.current
    var week by remember { mutableStateOf<Map<LocalDate, DayUsage>?>(null) }
    LaunchedEffect(granted) {
        if (!granted) { week = emptyMap(); return@LaunchedEffect }
        while (true) {
            week = withContext(Dispatchers.IO) {
                val today = LocalDate.now()
                (0L..13L).associate { today.minusDays(it) to UsageRepository.day(context, today.minusDays(it), store) }
                    .also { m -> m.forEach { (d, u) -> if (d != today && u.totalMinutes > 0) store.saveDailyTotal(d, u.totalMinutes) } }
            }
            delay(60_000)
        }
    }
    return week
}

/** YourHour-style overview: time ring, addiction level, pickups, hourly pattern, week trend, apps and a session timeline. */
@Composable
fun ScreenTimeDashboard(timelineMode: Boolean = false) {
    val context = LocalContext.current
    val store = remember { WellbeingStore(context) }
    val guard = remember { FocusGuardStore(context) }
    var config by remember { mutableStateOf(store.config) }
    var guardConfig by remember { mutableStateOf(guard.config) }
    var perms by remember { mutableStateOf(Perms.read(context)) }
    LaunchedEffect(Unit) { while (true) { delay(3000); perms = Perms.read(context) } }
    val week = rememberWeekUsage(store, perms.usage)
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var detail by remember { mutableStateOf<String?>(null) }
    val labels = remember { HashMap<String, String>() }
    fun name(pkg: String) = labels.getOrPut(pkg) { WellbeingAlarmReceiver.label(context, pkg) }
    fun save(next: WellbeingConfig) { store.config = next; config = next }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!perms.usage || !perms.accessibility) item { PermissionsCard(perms) }
        item { DayChips(selected) { selected = it } }
        val day = week?.get(selected)
        if (week == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (day != null) {
            if (timelineMode) {
                item { Text("${day.sessions.size} sessions · ${hm(day.totalMinutes)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                val sessions = day.sessions.filter { it.end - it.start >= 30_000L }.sortedByDescending { it.start }
                itemsIndexed(sessions, key = { _, s -> s.pkg + s.start }) { i, s -> SessionRow(s, name(s.pkg), first = i == 0, last = i == sessions.lastIndex) }
                if (sessions.isEmpty()) item { Text("No app use recorded", color = Chronora.muted) }
            } else {
                val yesterday = week[selected.minusDays(1)]
                item { HeroRing(day, config.screenTimeGoalMinutes, yesterday) }
                item { StatsRow(day, config) }
                item {
                    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Through the day", fontWeight = FontWeight.Bold)
                        BarChart(day.hourlyMinutes.toList(), labels = (0..23).map { if (it % 6 == 0) "%02d".format(it) else "" }, valueLabel = { i, v -> "%02d:00 · %s".format(i, hm(v)) }, height = 110.dp)
                    } }
                }
                item {
                    val today = LocalDate.now()
                    val last7 = (6L downTo 0L).map { today.minusDays(it) }
                    val totals = last7.map { week[it]?.totalMinutes ?: 0 }
                    val prev = (13L downTo 7L).sumOf { week[today.minusDays(it)]?.totalMinutes ?: 0 }
                    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("This week", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            val change = UsageStatsCalculator.percentChange(totals.sum(), prev)
                            Text("avg ${hm(totals.sum() / 7)}" + (change?.let { " · ${if (it > 0) "▲" else "▼"} ${kotlin.math.abs(it)}%" } ?: ""),
                                style = MaterialTheme.typography.labelMedium, color = if ((change ?: 0) > 0) Chronora.colors.bad else Chronora.colors.good)
                        }
                        BarChart(totals, labels = last7.map { it.dayOfWeek.name.take(1) }, valueLabel = { i, v -> "${last7[i]} · ${hm(v)}" }, height = 120.dp, goal = config.screenTimeGoalMinutes, highlight = last7.indexOf(selected))
                    } }
                }
                item { Text("Apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                val top = day.apps.filter { it.minutes > 0 }.take(40)
                val max = top.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                itemsIndexed(top, key = { _, a -> a.pkg }) { i, a ->
                    val limit = WellbeingEngine.dailyLimitFor(a.pkg, guardConfig.dailyLimits, config, selected)
                    AppRow(a, name(a.pkg), AppColors.getOrElse(i) { MaterialTheme.colorScheme.outline }, a.minutes.toFloat() / max, limit, i) { detail = a.pkg }
                }
                item {
                    Card { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SwitchRow("Usage bubble over apps", config.usageBubble) { save(config.copy(usageBubble = it)) }
                        com.raunak.daytimeline.ui.Stepper("Daily goal", hm(config.screenTimeGoalMinutes),
                            { save(config.copy(screenTimeGoalMinutes = (config.screenTimeGoalMinutes - 15).coerceAtLeast(15))) },
                            { save(config.copy(screenTimeGoalMinutes = config.screenTimeGoalMinutes + 15)) })
                    } }
                }
            }
        }
    }

    val locked = com.raunak.daytimeline.campus.rememberLockedUntil() != null
    if (!locked) detail?.let { pkg ->
        AppDetailDialog(pkg, name(pkg), week ?: emptyMap(), config, guardConfig,
            onSave = { store.config = it; config = it; WellbeingAlarmReceiver.schedule(context) },
            onSaveGuard = { guard.config = it; guardConfig = it }) { detail = null }
    }
}

@Composable
private fun DayChips(selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (6L downTo 0L).map { today.minusDays(it) }.forEach { d ->
            val on = d == selected
            Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))
                .clickable { onPick(d) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(if (d == today) "Today" else d.dayOfWeek.name.take(2).lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else null,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    }
}

/** Donut split by the top apps, total in the middle, addiction level underneath. */
@Composable
private fun HeroRing(day: DayUsage, goal: Int, yesterday: DayUsage?) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
    val grow = anim.value
    val total = day.totalMinutes.coerceAtLeast(1)
    val top = day.apps.filter { it.minutes > 0 }.take(AppColors.size - 1)
    val otherMinutes = (day.totalMinutes - top.sumOf { it.minutes }).coerceAtLeast(0)
    val slices = top.mapIndexed { i, a -> a.minutes to AppColors[i] } + (otherMinutes to MaterialTheme.colorScheme.outline)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val level = AddictionLevel.of(day.totalMinutes, goal)
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 22.dp.toPx()
                    val sz = Size(size.width - w, size.height - w)
                    val o = Offset(w / 2, w / 2)
                    drawArc(track, 0f, 360f, false, o, sz, style = Stroke(w))
                    var angle = -90f
                    slices.forEach { (m, c) ->
                        val sweep = 360f * m / total * grow
                        if (sweep > 1.2f) drawArc(c, angle + .6f, sweep - 1.2f, false, o, sz, style = Stroke(w, cap = StrokeCap.Butt))
                        angle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(hm(day.totalMinutes), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("of ${hm(goal)} goal", color = Chronora.muted, style = MaterialTheme.typography.bodySmall)
                    yesterday?.let { y ->
                        val diff = day.totalMinutes - y.totalMinutes
                        if (y.totalMinutes > 0) Text((if (diff > 0) "▲ " else "▼ ") + hm(kotlin.math.abs(diff)) + " vs yesterday", style = MaterialTheme.typography.labelSmall, color = if (diff > 0) Chronora.colors.bad else Chronora.colors.good)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(CircleShape).background(level.color.copy(alpha = .15f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Icon(level.icon, null, Modifier.size(18.dp), tint = level.color)
                Text("  " + level.title, color = level.color, fontWeight = FontWeight.Bold)
            }
            FlowRowLegend(top.mapIndexed { i, a -> a.pkg to AppColors[i] })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowLegend(items: List<Pair<String, Color>>) {
    val context = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (pkg, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Text(" " + WellbeingAlarmReceiver.label(context, pkg), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatsRow(day: DayUsage, config: WellbeingConfig) = Column {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBubble(Icons.Default.LockOpen, "${day.pickups}", "Unlocks", Modifier.weight(1f), over = day.pickups > config.pickupGoal)
        StatBubble(Icons.Default.TouchApp, "${day.opens}", "Opens", Modifier.weight(1f))
        StatBubble(Icons.Default.Notifications, "${day.notifications}", "Alerts", Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBubble(Icons.Default.WbTwilight, day.firstPickup?.let(::clockOf) ?: "—", "First unlock", Modifier.weight(1f))
        StatBubble(Icons.Default.Timelapse, hm(day.longestSessionMinutes), "Longest", Modifier.weight(1f))
        StatBubble(Icons.Default.Bolt, if (day.pickups > 0) hm(day.totalMinutes / day.pickups) else "—", "Per unlock", Modifier.weight(1f))
    }
}

@Composable
private fun StatBubble(icon: ImageVector, value: String, label: String, modifier: Modifier, over: Boolean = false) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (over) Chronora.colors.bad else MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Chronora.muted, maxLines = 1)
        }
    }
}

@Composable
private fun AppRow(a: AppUsage, label: String, color: Color, fraction: Float, limit: Int?, index: Int, onClick: () -> Unit) {
    val p = remember(a.pkg) { Animatable(0f) }
    LaunchedEffect(a.pkg, fraction) { delay(index.coerceAtMost(10) * 40L); p.animateTo(fraction, tween(700, easing = FastOutSlowInEasing)) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(a.pkg, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hm(a.minutes), fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(p.value.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
            }
            Text("${a.opens} opens" + (limit?.let { " · limit ${hm(it)}" } ?: ""), style = MaterialTheme.typography.labelSmall,
                color = if (limit != null && a.minutes >= limit) Chronora.colors.bad else Chronora.muted)
        }
    }
}

@Composable
private fun SessionRow(s: AppSession, label: String, first: Boolean, last: Boolean) {
    val rail = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(clockOf(s.start), Modifier.width(48.dp).padding(top = 10.dp), style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
        Box(Modifier.width(20.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxHeight().padding(top = if (first) 16.dp else 0.dp, bottom = if (last) 0.dp else 0.dp).background(if (last) Color.Transparent else rail))
            Box(Modifier.padding(top = 12.dp).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
        Row(Modifier.weight(1f).padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(s.pkg, 32.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${clockOf(s.start)}–${clockOf(s.end)}", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
            }
            Text(if (s.minutes > 0) hm(s.minutes) else "<1m", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** How a day's screen time compares with your goal, in YourHour's six levels. */
enum class AddictionLevel(val title: String, val color: Color, val icon: ImageVector) {
    CHAMPION("Champion", Color(0xFF2E7D32), Icons.Default.EmojiEvents),
    ACHIEVER("Achiever", Color(0xFF43A047), Icons.Default.Star),
    FIT("Fit", Color(0xFF7CB342), Icons.Default.ThumbUp),
    HABITUAL("Habitual", Color(0xFFE0A33B), Icons.Default.Schedule),
    DEPENDENT("Dependent", Color(0xFFEF6C00), Icons.Default.Warning),
    ADDICTED("Addicted", Color(0xFFC62828), Icons.Default.Report);

    companion object {
        /** Bands are fractions of the goal, so changing the goal moves every level with it. */
        fun of(minutes: Int, goal: Int): AddictionLevel {
            val r = minutes.toFloat() / goal.coerceAtLeast(1)
            return when {
                r <= .33f -> CHAMPION
                r <= .66f -> ACHIEVER
                r <= 1f -> FIT
                r <= 1.5f -> HABITUAL
                r <= 2f -> DEPENDENT
                else -> ADDICTED
            }
        }
    }
}

/** Today's screen time in one compact card: total, unlocks and the top apps. */
@Composable
fun TodayUsageStrip(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    var day by remember { mutableStateOf<DayUsage?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            if (com.raunak.daytimeline.pro.UsageAccess.granted(context)) day = withContext(Dispatchers.IO) { UsageRepository.day(context, LocalDate.now()) }
            delay(60_000)
        }
    }
    val d = day ?: return
    val goal = remember { WellbeingStore(context).config.screenTimeGoalMinutes }
    Card(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                val track = MaterialTheme.colorScheme.surfaceVariant
                val level = AddictionLevel.of(d.totalMinutes, goal)
                Canvas(Modifier.fillMaxSize()) {
                    val w = 6.dp.toPx()
                    val sz = Size(size.width - w, size.height - w)
                    drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), sz, style = Stroke(w))
                    drawArc(level.color, -90f, 360f * (d.totalMinutes.toFloat() / goal.coerceAtLeast(1)).coerceAtMost(1f), false, Offset(w / 2, w / 2), sz, style = Stroke(w, cap = StrokeCap.Round))
                }
                Icon(Icons.Default.PhoneAndroid, null, Modifier.size(20.dp), tint = level.color)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(hm(d.totalMinutes) + " screen time", fontWeight = FontWeight.Bold)
                Text("${d.pickups} unlocks · ${AddictionLevel.of(d.totalMinutes, goal).title}", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
            }
            d.apps.filter { it.minutes > 0 }.take(3).forEach { a -> Box(Modifier.padding(start = 4.dp)) { AppIcon(a.pkg, 26.dp) } }
        }
    }
}
