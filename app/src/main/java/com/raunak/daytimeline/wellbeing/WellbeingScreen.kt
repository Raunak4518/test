package com.raunak.daytimeline.wellbeing

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.pro.FocusGuardService
import com.raunak.daytimeline.pro.FocusGuardStore
import com.raunak.daytimeline.pro.UsageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun hm(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
private fun clock(m: Int) = "%02d:%02d".format((m / 60) % 24, m % 60)

/**
 * Digital Wellbeing, StayFree, ActionDash, ScreenZen and Opal features in one place: usage dashboard,
 * app timers, group/total/open/session limits, bedtime, notification digest, Shorts/Reels blocking,
 * strict mode, goals and reports.
 */
@Composable
fun WellbeingScreen() {
    val context = LocalContext.current
    val store = remember { WellbeingStore(context) }
    val guard = remember { FocusGuardStore(context) }
    var config by remember { mutableStateOf(store.config) }
    var guardConfig by remember { mutableStateOf(guard.config) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var week by remember { mutableStateOf<Map<LocalDate, DayUsage>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var detailPkg by remember { mutableStateOf<String?>(null) }
    var perms by remember { mutableStateOf(Perms.read(context)) }

    fun save(next: WellbeingConfig) { store.config = next; config = next; WellbeingAlarmReceiver.schedule(context) }
    fun saveGuard(next: com.raunak.daytimeline.pro.FocusGuardConfig) { guard.config = next; guardConfig = next }

    LaunchedEffect(perms.usage) {
        loading = true
        week = withContext(Dispatchers.IO) {
            val today = LocalDate.now()
            (0L..13L).associate { today.minusDays(it) to UsageRepository.day(context, today.minusDays(it), store) }
                .also { m -> m.forEach { (d, u) -> if (d != today && u.totalMinutes > 0) store.saveDailyTotal(d, u.totalMinutes) } }
        }
        loading = false
    }
    LaunchedEffect(Unit) { while (true) { delay(3000); perms = Perms.read(context) } }

    val day = week[selectedDay]
    val labels = remember(week) { week.values.flatMap { it.apps }.map { it.pkg }.distinct().associateWith { WellbeingAlarmReceiver.label(context, it) } }
    fun name(pkg: String) = labels[pkg] ?: WellbeingAlarmReceiver.label(context, pkg)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!perms.all) item { PermissionsCard(perms) }
        item {
            val days = (6L downTo 0L).map { LocalDate.now().minusDays(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { d ->
                    FilterChip(d == selectedDay, { selectedDay = d }, label = { Text(if (d == LocalDate.now()) "Today" else d.dayOfWeek.name.take(2).lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        }
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (loading && day == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return@Column }
                val d = day ?: return@Column
                Text(hm(d.totalMinutes), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                val goal = config.screenTimeGoalMinutes
                Text(
                    (if (d.totalMinutes <= goal) "Under your ${hm(goal)} goal" else "${hm(d.totalMinutes - goal)} over your ${hm(goal)} goal") +
                        " · streak ${UsageStatsCalculator.goalStreak(store.dailyTotals() + (LocalDate.now() to (week[LocalDate.now()]?.totalMinutes ?: 0)), goal, LocalDate.now())} days",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Pickups", d.pickups.toString(), Modifier.weight(1f))
                    Stat("Opens", d.opens.toString(), Modifier.weight(1f))
                    Stat("Notifications", d.notifications.toString(), Modifier.weight(1f))
                }
                Text(
                    listOfNotNull(
                        d.firstPickup?.let { "First pickup " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) },
                        "longest session ${hm(d.longestSessionMinutes)}"
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall
                )
                Text("By hour", style = MaterialTheme.typography.labelMedium)
                BarChart(d.hourlyMinutes.toList(), labels = (0..23).map { if (it % 6 == 0) "%02d".format(it) else "" }, valueLabel = { i, v -> "%02d:00 · %s".format(i, hm(v)) }, height = 110.dp)
            } }
        }
        item {
            val today = LocalDate.now()
            val last7 = (6L downTo 0L).map { today.minusDays(it) }
            val totals = last7.map { week[it]?.totalMinutes ?: 0 }
            val prev = (13L downTo 7L).sumOf { week[today.minusDays(it)]?.totalMinutes ?: 0 }
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Last 7 days", fontWeight = FontWeight.Bold)
                val sum = totals.sum()
                Text("Daily average ${hm(sum / 7)}" + (UsageStatsCalculator.percentChange(sum, prev)?.let { " · ${if (it > 0) "+" else ""}$it% vs previous week" } ?: ""), style = MaterialTheme.typography.bodySmall)
                BarChart(totals, labels = last7.map { it.dayOfWeek.name.take(1) }, valueLabel = { i, v -> "${last7[i]} · ${hm(v)}" }, height = 120.dp, goal = config.screenTimeGoalMinutes, highlight = last7.indexOf(selectedDay))
            } }
        }
        item { Text("Apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(day?.apps?.take(30) ?: emptyList(), key = { it.pkg }) { a ->
            val limit = WellbeingEngine.dailyLimitFor(a.pkg, guardConfig.dailyLimits, config, selectedDay)
            ListItem(
                modifier = Modifier.clickable { detailPkg = a.pkg },
                headlineContent = { Text(name(a.pkg)) },
                supportingContent = { Text("${hm(a.minutes)} · ${a.opens} opens · ${a.notifications} notifications" + (limit?.let { " · timer ${hm(it)}" } ?: "")) },
                trailingContent = { if (limit != null) LinearProgressIndicator(progress = { (a.minutes.toFloat() / limit).coerceIn(0f, 1f) }, modifier = Modifier.width(56.dp)) }
            )
        }
        item { GoalsCard(config) { save(it) } }
        item { LimitsCard(config, context, ::name) { save(it) } }
        item { BedtimeCard(config, perms) { save(it) } }
        item { ShortFormCard(config) { save(it) } }
        item { NotificationsCard(config, store, ::name) { save(it) } }
        item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Protection", fontWeight = FontWeight.Bold)
                SwitchLine("Strict mode — during blocks, bedtime and cooldowns, Chronora's settings, uninstall and accessibility pages are covered", config.strictMode) { save(config.copy(strictMode = it)) }
                SwitchLine("Do Not Disturb during focus sessions", config.doNotDisturbDuringFocus) { save(config.copy(doNotDisturbDuringFocus = it)) }
                Text("Emergency unlocks and Locked mode are shared with Focus Guard.", style = MaterialTheme.typography.bodySmall)
            } }
        }
    }

    detailPkg?.let { pkg ->
        AppDetailDialog(pkg, name(pkg), week, config, guardConfig, onSave = ::save, onSaveGuard = ::saveGuard) { detailPkg = null }
    }
}

private data class Perms(val usage: Boolean, val accessibility: Boolean, val notifications: Boolean, val dnd: Boolean, val grayscale: Boolean) {
    val all get() = usage && accessibility && notifications && dnd
    companion object {
        fun read(c: android.content.Context) = Perms(UsageAccess.granted(c), FocusGuardService.isEnabled(c), WellbeingNotificationListener.enabled(c), WellbeingModes.dndAccess(c), WellbeingModes.grayscaleAccess(c))
    }
}

@Composable
private fun PermissionsCard(p: Perms) {
    val context = LocalContext.current
    fun open(action: String) = runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Finish setup", fontWeight = FontWeight.Bold)
        PermRow("Usage access — screen time, pickups, app timers", p.usage) { open(Settings.ACTION_USAGE_ACCESS_SETTINGS) }
        PermRow("Accessibility (Chronora Focus Guard) — enforce limits, block Shorts/Reels", p.accessibility) { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        PermRow("Notification access — counts and digest", p.notifications) { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
        PermRow("Do Not Disturb access — bedtime and focus", p.dnd) { open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
    } }
}

@Composable
private fun PermRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text((if (granted) "✓ " else "• ") + label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        if (!granted) TextButton(onClick = onGrant) { Text("Grant") }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Single-series bar chart: rounded data ends on the baseline, 2 dp gaps, tap a bar to read its value. */
@Composable
private fun BarChart(values: List<Int>, labels: List<String>, valueLabel: (Int, Int) -> String, height: androidx.compose.ui.unit.Dp, goal: Int? = null, highlight: Int = -1) {
    val bar = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    var selected by remember(values) { mutableIntStateOf(-1) }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(goal ?: 0).coerceAtLeast(1)
    Column {
        Text(if (selected in values.indices) valueLabel(selected, values[selected]) else "Tap a bar for details", style = MaterialTheme.typography.labelSmall)
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(values) {
                detectTapGestures { o -> selected = (o.x / (size.width / values.size.toFloat())).toInt().coerceIn(0, values.lastIndex) }
            }
        ) {
            val slot = size.width / values.size
            val gap = 2.dp.toPx()
            val w = (slot - gap).coerceAtLeast(1f)
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            goal?.let { g ->
                val y = size.height - size.height * g / max
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = (size.height * v / max).coerceAtLeast(2.dp.toPx())
                val color = if (highlight < 0 || i == highlight || i == selected) bar else muted
                roundedTopBar(color, Offset(i * slot + gap / 2, size.height - h), Size(w, h), 4.dp.toPx().coerceAtMost(w / 2))
            }
        }
        Row(Modifier.fillMaxWidth()) { labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) } }
    }
}

private fun DrawScope.roundedTopBar(color: androidx.compose.ui.graphics.Color, topLeft: Offset, size: Size, r: Float) {
    val path = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r), bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero))
    }
    drawPath(path, color)
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
}

@Composable
private fun MinutesStepper(label: String, value: Int, step: Int = 15, min: Int = 0, max: Int = 24 * 60, zeroLabel: String = "Off", onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onChange((value - step).coerceAtLeast(min)) }) { Text("−") }
        Text(if (value == 0) zeroLabel else hm(value), Modifier.widthIn(min = 56.dp))
        TextButton(onClick = { onChange((value + step).coerceAtMost(max)) }) { Text("+") }
    }
}

@Composable
private fun GoalsCard(c: WellbeingConfig, save: (WellbeingConfig) -> Unit) {
    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Goals & reports", fontWeight = FontWeight.Bold)
        MinutesStepper("Daily screen-time goal", c.screenTimeGoalMinutes, min = 15) { save(c.copy(screenTimeGoalMinutes = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pickup goal", Modifier.weight(1f))
            TextButton(onClick = { save(c.copy(pickupGoal = (c.pickupGoal - 10).coerceAtLeast(10))) }) { Text("−") }
            Text("${c.pickupGoal}")
            TextButton(onClick = { save(c.copy(pickupGoal = c.pickupGoal + 10)) }) { Text("+") }
        }
        SwitchLine("Daily report at ${clock(c.reportMinute)} (weekly comparison on Sundays)", c.dailyReport) { save(c.copy(dailyReport = it)) }
        MinutesStepper("Report time", c.reportMinute, step = 15, min = 15, max = 24 * 60 - 15) { save(c.copy(reportMinute = it)) }
    } }
}

@Composable
private fun LimitsCard(c: WellbeingConfig, context: android.content.Context, name: (String) -> String, save: (WellbeingConfig) -> Unit) {
    var groupName by remember { mutableStateOf("") }
    var groupMinutes by remember { mutableIntStateOf(60) }
    var picking by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val apps = remember { installedApps(context) }
    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Limits", fontWeight = FontWeight.Bold)
        Text("Per-app timers, open limits and session limits: tap an app above.", style = MaterialTheme.typography.bodySmall)
        MinutesStepper("Total daily screen time", c.totalDailyLimitMinutes) { save(c.copy(totalDailyLimitMinutes = it)) }
        MinutesStepper("Warn before a limit", c.warnMinutesBefore, step = 1, max = 15, zeroLabel = "Never") { save(c.copy(warnMinutesBefore = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mindful pause grows per open", Modifier.weight(1f))
            listOf(0, 2, 5).forEach { s -> FilterChip(c.progressiveDelayStep == s, { save(c.copy(progressiveDelayStep = s)) }, label = { Text(if (s == 0) "Fixed" else "+${s}s") }) }
        }
        HorizontalDivider()
        Text("Group limits", style = MaterialTheme.typography.titleSmall)
        c.groupLimits.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("${g.name} · ${hm(g.minutes)}/day"); Text(g.packages.joinToString { name(it) }, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                IconButton(onClick = { save(c.copy(groupLimits = c.groupLimits - g)) }) { Icon(Icons.Default.Close, "Remove group") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            categoryGroups.forEach { (label, cat) ->
                AssistChip({
                    val pkgs = apps.filter { it.third == cat }.map { it.first }.toSet()
                    if (pkgs.isNotEmpty()) save(c.copy(groupLimits = c.groupLimits + GroupLimit(System.currentTimeMillis(), label, pkgs, 60)))
                }, label = { Text("+ $label") })
            }
        }
        TextButton(onClick = { picking = !picking }) { Text(if (picking) "Cancel custom group" else "Custom group…") }
        if (picking) {
            OutlinedTextField(groupName, { groupName = it }, label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            MinutesStepper("Daily limit", groupMinutes, min = 5) { groupMinutes = it }
            Column(Modifier.heightIn(max = 260.dp)) {
                LazyColumn { items(apps, key = { it.first }) { (pkg, label, _) ->
                    Row(Modifier.fillMaxWidth().clickable { picked = if (pkg in picked) picked - pkg else picked + pkg }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pkg in picked, { picked = if (pkg in picked) picked - pkg else picked + pkg }); Text(label)
                    }
                } }
            }
            Button(enabled = picked.isNotEmpty() && groupName.isNotBlank(), onClick = {
                save(c.copy(groupLimits = c.groupLimits + GroupLimit(System.currentTimeMillis(), groupName.trim(), picked, groupMinutes)))
                picking = false; picked = emptySet(); groupName = ""
            }) { Text("Save group") }
        }
    } }
}

private val categoryGroups = listOf("Social" to ApplicationInfo.CATEGORY_SOCIAL, "Video" to ApplicationInfo.CATEGORY_VIDEO, "Games" to ApplicationInfo.CATEGORY_GAME, "News" to ApplicationInfo.CATEGORY_NEWS)

private fun installedApps(context: android.content.Context): List<Triple<String, String, Int>> {
    val pm = context.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map { Triple(it.packageName, pm.getApplicationLabel(it).toString(), it.category) }
        .sortedBy { it.second.lowercase() }
}

@Composable
private fun BedtimeCard(c: WellbeingConfig, perms: Perms, save: (WellbeingConfig) -> Unit) {
    val b = c.bedtime
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SwitchLine("Bedtime mode", b.enabled) { save(c.copy(bedtime = b.copy(enabled = it))) }
        MinutesStepper("Starts", b.startMinute, step = 15, max = 24 * 60 - 15, zeroLabel = "00:00") { save(c.copy(bedtime = b.copy(startMinute = it))) }
        MinutesStepper("Ends", b.endMinute, step = 15, max = 24 * 60 - 15, zeroLabel = "00:00") { save(c.copy(bedtime = b.copy(endMinute = it))) }
        Text("${clock(b.startMinute)} → ${clock(b.endMinute)}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..7).forEach { d -> FilterChip(d in b.days, { save(c.copy(bedtime = b.copy(days = if (d in b.days) b.days - d else b.days + d))) }, label = { Text(java.time.DayOfWeek.of(d).name.take(1)) }) }
        }
        SwitchLine("Block apps (except allowed)", b.blockApps) { save(c.copy(bedtime = b.copy(blockApps = it))) }
        SwitchLine("Do Not Disturb" + if (!perms.dnd) " (grant access above)" else "", b.doNotDisturb) { save(c.copy(bedtime = b.copy(doNotDisturb = it))) }
        SwitchLine("Grayscale screen", b.grayscale) { save(c.copy(bedtime = b.copy(grayscale = it))) }
        if (b.grayscale && !perms.grayscale) Text("Grayscale needs a one-time grant from a computer:\nadb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { picking = !picking }) { Text("Allowed at bedtime (${b.allowedPackages.size})") }
        if (picking) {
            val apps = remember { installedApps(context) }
            Column(Modifier.heightIn(max = 260.dp)) {
                LazyColumn { items(apps, key = { it.first }) { (pkg, label, _) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pkg in b.allowedPackages, { on -> save(c.copy(bedtime = b.copy(allowedPackages = if (on) b.allowedPackages + pkg else b.allowedPackages - pkg))) }); Text(label)
                    }
                } }
            }
        }
    } }
}

@Composable
private fun ShortFormCard(c: WellbeingConfig, save: (WellbeingConfig) -> Unit) {
    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Block short videos", fontWeight = FontWeight.Bold)
        Text("Backs out of the short-video feed while messages, search and the rest of the app keep working. Apps change their screens often, so this is best-effort.", style = MaterialTheme.typography.bodySmall)
        var advanced by remember { mutableStateOf(false) }
        ShortForm.values().forEach { sf ->
            SwitchLine(sf.label, sf in c.blockedShortForm) { on -> save(c.copy(blockedShortForm = if (on) c.blockedShortForm + sf else c.blockedShortForm - sf)) }
            if (advanced) com.raunak.daytimeline.campus.ListEditor("Screen ids for ${sf.label} (${sf.packageName})", c.idsFor(sf)) { ids -> save(c.copy(shortFormIds = c.shortFormIds + (sf.name to ids))) }
        }
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide screen ids" else "Edit screen ids (if an app update breaks detection)") }
        if (advanced) TextButton(onClick = { save(c.copy(shortFormIds = emptyMap())) }) { Text("Restore built-in ids") }
    } }
}

@Composable
private fun NotificationsCard(c: WellbeingConfig, store: WellbeingStore, name: (String) -> String, save: (WellbeingConfig) -> Unit) {
    val context = LocalContext.current
    var held by remember { mutableStateOf(store.held()) }
    var picking by remember { mutableStateOf(false) }
    val counts = remember { store.notificationCounts(LocalDate.now()) }
    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Notifications", fontWeight = FontWeight.Bold)
        if (counts.isNotEmpty()) Text("Today: " + counts.entries.sortedByDescending { it.value }.take(5).joinToString { "${name(it.key)} ${it.value}" }, style = MaterialTheme.typography.bodySmall)
        Text("Quiet apps are silenced and delivered together as a digest.", style = MaterialTheme.typography.bodySmall)
        c.quietApps.forEach { pkg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name(pkg), Modifier.weight(1f))
                IconButton(onClick = { save(c.copy(quietApps = c.quietApps - pkg)) }) { Icon(Icons.Default.Close, "Remove") }
            }
        }
        TextButton(onClick = { picking = !picking }) { Text(if (picking) "Done" else "Choose quiet apps") }
        if (picking) {
            val apps = remember { installedApps(context) }
            Column(Modifier.heightIn(max = 260.dp)) {
                LazyColumn { items(apps, key = { it.first }) { (pkg, label, _) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pkg in c.quietApps, { on -> save(c.copy(quietApps = if (on) c.quietApps + pkg else c.quietApps - pkg)) }); Text(label)
                    }
                } }
            }
        }
        SwitchLine("Only during focus and bedtime", c.quietOnlyDuringFocus) { save(c.copy(quietOnlyDuringFocus = it)) }
        com.raunak.daytimeline.campus.ListEditor("Digest times (HH:MM)", c.digestTimes.sorted().map { clock(it) }) { v ->
            save(c.copy(digestTimes = v.mapNotNull { com.raunak.daytimeline.campus.parseClock(it) }.distinct().sorted()))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(listOf(12 * 60 + 30, 18 * 60, 21 * 60), listOf(9 * 60, 13 * 60, 17 * 60, 21 * 60), listOf(20 * 60)).forEach { times ->
                FilterChip(c.digestTimes.sorted() == times, { save(c.copy(digestTimes = times)) }, label = { Text("${times.size}×/day") })
            }
        }
        if (held.isNotEmpty()) {
            Text("Held now (${held.size})", style = MaterialTheme.typography.titleSmall)
            NotificationDigest.summary(held).take(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row {
                TextButton(onClick = { WellbeingAlarmReceiver.deliverDigest(context); held = store.held() }) { Text("Deliver now") }
                TextButton(onClick = { store.clearHeld(); held = emptyList() }) { Text("Clear") }
            }
        }
    } }
}

@Composable
private fun AppDetailDialog(
    pkg: String, label: String, week: Map<LocalDate, DayUsage>,
    c: WellbeingConfig, g: com.raunak.daytimeline.pro.FocusGuardConfig,
    onSave: (WellbeingConfig) -> Unit, onSaveGuard: (com.raunak.daytimeline.pro.FocusGuardConfig) -> Unit, onClose: () -> Unit
) {
    val today = LocalDate.now()
    val days = (6L downTo 0L).map { today.minusDays(it) }
    val mins = days.map { d -> week[d]?.apps?.firstOrNull { it.pkg == pkg }?.minutes ?: 0 }
    val opens = days.map { d -> week[d]?.apps?.firstOrNull { it.pkg == pkg }?.opens ?: 0 }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(label) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Text("7-day total ${hm(mins.sum())} · avg ${hm(mins.sum() / 7)} · ${opens.sum()} opens", style = MaterialTheme.typography.bodySmall)
                    BarChart(mins, days.map { it.dayOfWeek.name.take(1) }, { i, v -> "${days[i]} · ${hm(v)} · ${opens[i]} opens" }, 90.dp)
                }
                item {
                    MinutesStepper("Weekday timer", g.dailyLimits[pkg] ?: 0) { v -> onSaveGuard(g.copy(dailyLimits = if (v == 0) g.dailyLimits - pkg else g.dailyLimits + (pkg to v))) }
                    MinutesStepper("Weekend timer", c.weekendLimits[pkg] ?: 0, zeroLabel = "Same") { v -> onSave(c.copy(weekendLimits = if (v == 0) c.weekendLimits - pkg else c.weekendLimits + (pkg to v))) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Max opens per day", Modifier.weight(1f))
                        val cur = c.openLimits[pkg] ?: 0
                        TextButton(onClick = { val v = (cur - 5).coerceAtLeast(0); onSave(c.copy(openLimits = if (v == 0) c.openLimits - pkg else c.openLimits + (pkg to v))) }) { Text("−") }
                        Text(if (cur == 0) "Off" else "$cur")
                        TextButton(onClick = { onSave(c.copy(openLimits = c.openLimits + (pkg to cur + 5))) }) { Text("+") }
                    }
                    val s = c.sessionLimits[pkg]
                    MinutesStepper("Session length", s?.maxMinutes ?: 0, step = 5) { v -> onSave(c.copy(sessionLimits = if (v == 0) c.sessionLimits - pkg else c.sessionLimits + (pkg to SessionLimit(v, s?.cooldownMinutes ?: 30)))) }
                    if (s != null) MinutesStepper("Break after a session", s.cooldownMinutes, step = 5, min = 5) { v -> onSave(c.copy(sessionLimits = c.sessionLimits + (pkg to s.copy(cooldownMinutes = v)))) }
                    SwitchLine("Mindful pause before opening", pkg in g.mindfulPackages) { on -> onSaveGuard(g.copy(mindfulPackages = if (on) g.mindfulPackages + pkg else g.mindfulPackages - pkg)) }
                    SwitchLine("Block during focus sessions and schedules", pkg in g.blockedPackages) { on -> onSaveGuard(g.copy(blockedPackages = if (on) g.blockedPackages + pkg else g.blockedPackages - pkg)) }
                    SwitchLine("Quiet notifications (digest)", pkg in c.quietApps) { on -> onSave(c.copy(quietApps = if (on) c.quietApps + pkg else c.quietApps - pkg)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } }
    )
}
