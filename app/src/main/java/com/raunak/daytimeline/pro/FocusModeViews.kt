package com.raunak.daytimeline.pro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Live Focus mode status, re-read when settings change and every 15 s (breaks and timers end on their own). */
@Composable
private fun rememberFocusMode(store: FocusGuardStore): Pair<FocusGuardConfig, FocusModeStatus> {
    val config by store.configFlow.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); tick++ } }
    val status = remember(config, tick) { FocusMode.status(config) }
    return config to status
}

private fun durationLabel(m: Int) = if (m % 60 == 0) "${m / 60} h" else if (m > 60) "${m / 60} h ${m % 60} m" else "$m min"

/** Compact switch for Home and the Focus tab; tap the card for the full page. */
@Composable
fun FocusModeCard(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    val store = remember { FocusGuardStore(context) }
    val (config, s) = rememberFocusMode(store)
    val active = s.on && !s.onBreak
    val bg by animateColorAsState(if (active) Chronora.colors.hero else MaterialTheme.colorScheme.surface, tween(400), label = "fm")
    val fg = if (active) Chronora.colors.onHero else MaterialTheme.colorScheme.onSurface
    Card(modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = bg)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (active) Color.White.copy(alpha = .15f) else MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SelfImprovement, null, tint = if (active) fg else MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Focus mode", fontWeight = FontWeight.Bold, color = fg)
                Text(if (config.focusModeApps.isEmpty()) "Choose distracting apps" else s.label + if (s.on) " · ${s.apps} paused" else "",
                    style = MaterialTheme.typography.bodySmall, color = if (active) Chronora.colors.heroMuted else Chronora.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(s.on, { on ->
                if (config.focusModeApps.isEmpty()) onOpen()
                else if (on) FocusMode.turnOn(context) else FocusMode.turnOff(context)
            }, enabled = !s.on || FocusMode.canTurnOff(config))
        }
    }
}

/** Full Focus mode page: the big switch, timers, breaks, distracting apps and schedules. */
@Composable
fun FocusModeScreen() {
    val context = LocalContext.current
    val store = remember { FocusGuardStore(context) }
    val (config, s) = rememberFocusMode(store)
    var serviceOn by remember { mutableStateOf(FocusGuardService.isEnabled(context)) }
    LaunchedEffect(Unit) { while (true) { serviceOn = FocusGuardService.isEnabled(context); delay(2000) } }
    var usage by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(Unit) { if (UsageAccess.granted(context)) usage = withContext(Dispatchers.IO) { UsageAccess.today(context) } }
    val apps = remember { launchableApps(context) }
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    // Most-used apps first, so the likely distractions are on top.
    val ranked = remember(apps, usage) { apps.sortedWith(compareByDescending<Pair<String, String>> { usage[it.first] ?: 0 }.thenBy { it.second.lowercase() }) }
    fun toggleApp(pkg: String, on: Boolean) = store.update { it.copy(focusModeApps = if (on) it.focusModeApps + pkg else it.focusModeApps - pkg) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!serviceOn) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Turn on Focus Guard so apps can be paused", Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Turn on") }
                }
            }
        }
        item { BigSwitch(config, s) }
        item { SectionHeader("Distracting apps · ${config.focusModeApps.size}") }
        val shown = ranked.filter { query.isBlank() || it.second.contains(query, true) }.let { l -> if (showAll || query.isNotBlank()) l else l.filter { it.first in config.focusModeApps || (usage[it.first] ?: 0) > 0 }.take(12) }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search apps") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
        }
        items(shown, key = { it.first }) { (pkg, label) ->
            val on = pkg in config.focusModeApps
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { toggleApp(pkg, !on) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                com.raunak.daytimeline.wellbeing.AppIcon(pkg, 36.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    usage[pkg]?.takeIf { it > 0 }?.let { Text("${if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m"} today", style = MaterialTheme.typography.labelSmall, color = Chronora.muted) }
                }
                Checkbox(on, { toggleApp(pkg, it) })
            }
        }
        if (query.isBlank()) item { TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show fewer" else "Show all apps") } }
        item { SchedulesCard(config, store) }
        item {
            SectionCard("Options") {
                SwitchRow("Strict: no breaks, no early off", config.focusModeStrict) { v -> store.update { it.copy(focusModeStrict = v) } }
                ListEditor("Timer lengths (minutes)", config.focusModeDurations.map { it.toString() }, numeric = true) { v -> store.update { it.copy(focusModeDurations = v.mapNotNull(String::toIntOrNull).filter { n -> n > 0 }) } }
                ListEditor("Break lengths (minutes)", config.focusModeBreaks.map { it.toString() }, numeric = true) { v -> store.update { it.copy(focusModeBreaks = v.mapNotNull(String::toIntOrNull).filter { n -> n > 0 }) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BigSwitch(config: FocusGuardConfig, s: FocusModeStatus) {
    val context = LocalContext.current
    val active = s.on && !s.onBreak
    val fill by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, tween(500), label = "fill")
    val pulse by rememberInfiniteTransition(label = "fm").animateFloat(1f, if (active) 1.12f else 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    var denied by remember { mutableStateOf(false) }
    val noApps = config.focusModeApps.isEmpty()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(170.dp).scale(pulse).clip(CircleShape).background(fill.copy(alpha = .18f)))
            Box(
                Modifier.size(150.dp).clip(CircleShape).background(fill)
                    .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0f else .5f), CircleShape)
                    .clickable(enabled = !noApps) {
                        if (s.on) { denied = !FocusMode.turnOff(context) } else FocusMode.turnOn(context)
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SelfImprovement, null, Modifier.size(44.dp), tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                    Text(if (s.on) "On" else "Off", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(when {
            noApps -> "Pick the apps to pause below"
            s.on -> s.label + " · ${s.apps} apps paused"
            else -> "Tap to pause ${s.apps} apps"
        }, textAlign = TextAlign.Center, color = Chronora.muted)
        if (denied) Text(if (s.scheduled) "Scheduled — take a break instead" else "Strict: wait for the timer", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            when {
                s.onBreak -> FilledTonalButton(onClick = { FocusMode.endBreak(context) }) { Text("End break") }
                s.on && !config.focusModeStrict -> config.focusModeBreaks.forEach { m ->
                    AssistChip(onClick = { FocusMode.takeBreak(context, m) }, label = { Text("Break ${durationLabel(m)}") }, leadingIcon = { Icon(Icons.Default.Coffee, null, Modifier.size(16.dp)) })
                }
                !s.on && !noApps -> config.focusModeDurations.forEach { m ->
                    AssistChip(onClick = { FocusMode.turnOn(context, m) }, label = { Text("For ${durationLabel(m)}") }, leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(16.dp)) })
                }
            }
        }
    }
}

@Composable
private fun SchedulesCard(config: FocusGuardConfig, store: FocusGuardStore) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Study") }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("13:00") }
    var days by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    SectionCard("Schedules", action = { IconButton(onClick = { adding = !adding }) { Icon(if (adding) Icons.Default.Close else Icons.Default.Add, "Add schedule") } }) {
        config.focusModeSchedules.forEach { sc ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sc.name, fontWeight = FontWeight.SemiBold)
                    Text("${"%02d:%02d".format(sc.startMinute / 60, sc.startMinute % 60)}–${"%02d:%02d".format(sc.endMinute / 60 % 24, sc.endMinute % 60)} · " +
                        sc.days.sorted().joinToString(" ") { java.time.DayOfWeek.of(it).name.take(2).lowercase().replaceFirstChar { c -> c.uppercase() } },
                        style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                }
                Switch(sc.enabled, { v -> store.update { c -> c.copy(focusModeSchedules = c.focusModeSchedules.map { if (it.id == sc.id) it.copy(enabled = v) else it }) } })
                IconButton(onClick = { store.update { c -> c.copy(focusModeSchedules = c.focusModeSchedules.filterNot { it.id == sc.id }) } }) { Icon(Icons.Default.Delete, "Delete schedule") }
            }
        }
        if (config.focusModeSchedules.isEmpty() && !adding) Text("None", color = Chronora.muted, style = MaterialTheme.typography.bodySmall)
        if (adding) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(end, { end = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (1..7).forEach { d -> FilterChip(d in days, { days = if (d in days) days - d else days + d }, label = { Text(java.time.DayOfWeek.of(d).name.take(1)) }) }
            }
            val s = com.raunak.daytimeline.campus.parseClock(start); val e = com.raunak.daytimeline.campus.parseClock(end)
            Button(enabled = s != null && e != null && s != e && days.isNotEmpty(), onClick = {
                store.update { it.copy(focusModeSchedules = it.focusModeSchedules + BlockSchedule(System.currentTimeMillis(), name.ifBlank { "Focus" }, days, s!!, e!!)) }
                adding = false
            }) { Text("Add") }
        }
    }
}

private fun launchableApps(context: android.content.Context): List<Pair<String, String>> {
    val pm = context.packageManager
    return pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
        .filter { it.packageName != context.packageName && it.packageName !in FocusGuardEngine.alwaysAllowed }
        .map { it.packageName to pm.getApplicationLabel(it).toString() }
}
