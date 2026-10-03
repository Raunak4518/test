package com.raunak.daytimeline.wellbeing

import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.pro.FocusGuardStore
import com.raunak.daytimeline.pro.UsageAccess
import com.raunak.daytimeline.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Today's minutes per app, refreshed every minute. */
@Composable
private fun rememberTodayUsage(): Map<String, Int> {
    val context = LocalContext.current
    var usage by remember { mutableStateOf(emptyMap<String, Int>()) }
    LaunchedEffect(Unit) { while (true) { usage = withContext(Dispatchers.IO) { UsageAccess.today(context) }; delay(60_000) } }
    return usage
}

private val categoryGroups = listOf("Social" to ApplicationInfo.CATEGORY_SOCIAL, "Video" to ApplicationInfo.CATEGORY_VIDEO, "Games" to ApplicationInfo.CATEGORY_GAME, "News" to ApplicationInfo.CATEGORY_NEWS)

/** Every limit in one place: per-app timers, groups, the whole phone, goals and the daily report. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LimitsTab() {
    val context = LocalContext.current
    val guard = remember { FocusGuardStore(context) }
    val g by guard.configFlow.collectAsState()
    val store = remember { WellbeingStore(context) }
    var c by remember { mutableStateOf(store.config) }
    fun save(next: WellbeingConfig) { store.config = next; c = next; WellbeingAlarmReceiver.schedule(context) }
    val apps = rememberLauncherApps()
    val name = rememberAppNamer(apps)
    val usage = rememberTodayUsage()
    val confirm = rememberConfirm()
    var chooser by remember { mutableStateOf(false) }
    var newGroup by remember { mutableStateOf(false) }
    val total = usage.values.sum()
    val usageOn = remember(usage) { UsageAccess.granted(context) }

    ScreenList {
        item { SetupBanner(listOf(SetupStep("Usage access, so limits can count time", usageOn) { openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS) })) }
        item {
            val cap = if (c.totalDailyLimitMinutes > 0) c.totalDailyLimitMinutes else c.screenTimeGoalMinutes
            HeroCard {
                Text("Screen time today", style = MaterialTheme.typography.labelLarge, color = Chronora.colors.heroMuted)
                Text(hmText(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { if (cap > 0) (total.toFloat() / cap).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Color.White, trackColor = Color.White.copy(alpha = .25f), drawStopIndicator = {})
                Text(if (c.totalDailyLimitMinutes > 0) "Phone locks apps at ${hmText(c.totalDailyLimitMinutes)}" else "Goal ${hmText(c.screenTimeGoalMinutes)}", color = Chronora.colors.heroMuted)
            }
        }

        item {
            SectionCard("App timers", subtitle = "Blocked for the day once used up", icon = Icons.Default.Timer, action = { TextButton(onClick = { chooser = true }) { Text("Add") } }) {
                if (g.dailyLimits.isEmpty()) Text("Give an app a daily time budget", color = Chronora.muted)
                g.dailyLimits.entries.sortedByDescending { usage[it.key] ?: 0 }.forEach { (pkg, limit) ->
                    val used = usage[pkg] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, 36.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Stepper(name(pkg), hmText(limit),
                                { guard.update { it.copy(dailyLimits = it.dailyLimits + (pkg to (limit - 5).coerceAtLeast(5))) } },
                                { guard.update { it.copy(dailyLimits = it.dailyLimits + (pkg to (limit + 5).coerceAtMost(12 * 60))) } })
                            UsageBar(used, limit)
                            Text(if (used >= limit) "Used up for today" else "${hmText(limit - used)} left", style = MaterialTheme.typography.labelSmall, color = if (used >= limit) Chronora.colors.bad else Chronora.muted)
                        }
                        IconButton(onClick = { confirm.ask("the timer for ${name(pkg)}") { guard.update { it.copy(dailyLimits = it.dailyLimits - pkg) } } }) { Icon(Icons.Default.Close, "Remove timer for ${name(pkg)}") }
                    }
                }
            }
        }

        item {
            SectionCard("Group limits", subtitle = "One budget shared by several apps", icon = Icons.Default.Category) {
                c.groupLimits.forEach { grp ->
                    val used = grp.packages.sumOf { usage[it] ?: 0 }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                Stepper(grp.name, hmText(grp.minutes),
                                    { save(c.copy(groupLimits = c.groupLimits.map { if (it.id == grp.id) it.copy(minutes = (it.minutes - 5).coerceAtLeast(5)) else it })) },
                                    { save(c.copy(groupLimits = c.groupLimits.map { if (it.id == grp.id) it.copy(minutes = (it.minutes + 5).coerceAtMost(12 * 60)) else it })) })
                            }
                            IconButton(onClick = { confirm.ask("the group ${grp.name}") { save(c.copy(groupLimits = c.groupLimits.filterNot { it.id == grp.id })) } }) { Icon(Icons.Default.Close, "Remove ${grp.name}") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            grp.packages.take(8).forEach { AppIcon(it, 22.dp) }
                            if (grp.packages.size > 8) Text("+${grp.packages.size - 8}", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
                        }
                        UsageBar(used, grp.minutes)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categoryGroups.filter { (label, _) -> c.groupLimits.none { it.name == label } }.forEach { (label, cat) ->
                        AssistChip(onClick = {
                            val pkgs = apps.filter { it.category == cat }.map { it.pkg }.toSet()
                            if (pkgs.isEmpty()) Feedback.show("No $label apps on this phone")
                            else { save(c.copy(groupLimits = c.groupLimits + GroupLimit(System.currentTimeMillis(), label, pkgs, 60))); Feedback.show("$label: ${pkgs.size} apps, 1h a day") }
                        }, label = { Text(label) }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
                    }
                    AssistChip(onClick = { newGroup = true }, label = { Text("Custom") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
                }
            }
        }

        item {
            SectionCard("Whole phone", icon = Icons.Default.PhoneAndroid) {
                MinutesRow("Daily screen-time limit", c.totalDailyLimitMinutes, step = 15, zero = "Off") { save(c.copy(totalDailyLimitMinutes = it)) }
                MinutesRow("Warn before a limit", c.warnMinutesBefore, step = 1, max = 15, zero = "Never") { save(c.copy(warnMinutesBefore = it)) }
                Stepper("Unlock limit", if (c.unlockLimit == 0) "Off" else "${c.unlockLimit}",
                    { save(c.copy(unlockLimit = (c.unlockLimit - 10).coerceAtLeast(0))) },
                    { save(c.copy(unlockLimit = if (c.unlockLimit == 0) 50 else c.unlockLimit + 10)) })
            }
        }

        item {
            SectionCard("Goals & report", icon = Icons.Default.Flag) {
                MinutesRow("Screen-time goal", c.screenTimeGoalMinutes, step = 15, min = 15) { save(c.copy(screenTimeGoalMinutes = it)) }
                Stepper("Pickup goal", "${c.pickupGoal}", { save(c.copy(pickupGoal = (c.pickupGoal - 10).coerceAtLeast(10))) }, { save(c.copy(pickupGoal = c.pickupGoal + 10)) })
                SwitchRow("Daily report notification", c.dailyReport) { save(c.copy(dailyReport = it)) }
                if (c.dailyReport) ClockRow("Report at", c.reportMinute) { save(c.copy(reportMinute = it)) }
            }
        }
    }

    if (chooser) AppChooser("Add app timers", emptySet(), { chooser = false }) { picked ->
        val fresh = picked - g.dailyLimits.keys
        guard.update { it.copy(dailyLimits = it.dailyLimits + fresh.associateWith { 30 }) }
        if (fresh.isNotEmpty()) Feedback.show("${fresh.size} timers added at 30m · tap one to change")
        chooser = false
    }
    if (newGroup) {
        var groupName by remember { mutableStateOf("") }
        var picked by remember { mutableStateOf(emptySet<String>()) }
        var pick by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { newGroup = false }, title = { Text("New group") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(groupName, { groupName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                AppChips(picked, name, emptyText = "No apps yet", addLabel = "Choose apps", onRemove = { picked = picked - it }, onAdd = { pick = true })
            }
        }, confirmButton = {
            TextButton(enabled = groupName.isNotBlank() && picked.isNotEmpty(), onClick = {
                save(c.copy(groupLimits = c.groupLimits + GroupLimit(System.currentTimeMillis(), groupName.trim(), picked, 60))); newGroup = false
                Feedback.show("${groupName.trim()}: 1h a day · tap to change")
            }) { Text("Add") }
        }, dismissButton = { TextButton(onClick = { newGroup = false }) { Text("Cancel") } })
        if (pick) AppChooser("Apps in this group", picked, { pick = false }) { picked = it; pick = false }
    }
}

/** Bedtime and quiet notifications. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BedtimeTab() {
    val context = LocalContext.current
    val store = remember { WellbeingStore(context) }
    var c by remember { mutableStateOf(store.config) }
    fun save(next: WellbeingConfig) { store.config = next; c = next; WellbeingAlarmReceiver.schedule(context) }
    var perms by remember { mutableStateOf(Perms.read(context)) }
    LaunchedEffect(Unit) { while (true) { delay(3000); perms = Perms.read(context) } }
    val apps = rememberLauncherApps()
    val name = rememberAppNamer(apps)
    val confirm = rememberConfirm()
    var chooser by remember { mutableStateOf<String?>(null) }
    var addTime by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(store.held()) }
    val b = c.bedtime
    fun bed(next: BedtimeConfig) = save(c.copy(bedtime = next))

    ScreenList {
        item {
            SetupBanner(listOf(
                SetupStep("Accessibility, to block apps at bedtime", perms.accessibility || !b.blockApps) { openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                SetupStep("Do Not Disturb access", perms.dnd || !b.doNotDisturb) { openSettings(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
                SetupStep("Notification access, to hold notifications", perms.notifications || c.quietApps.isEmpty()) { openSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
            ))
        }
        item {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bedtime", style = MaterialTheme.typography.labelLarge, color = Chronora.colors.heroMuted)
                        Text("${clockText(b.startMinute)} → ${clockText(b.endMinute)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(if (b.enabled) dayText(b.days) else "Off", color = Chronora.colors.heroMuted)
                    }
                    Switch(b.enabled, { bed(b.copy(enabled = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = Color.White.copy(alpha = .35f), checkedThumbColor = Color.White))
                }
            }
        }
        item {
            SectionCard("When", icon = Icons.Default.Bedtime) {
                ClockRow("Starts", b.startMinute) { bed(b.copy(startMinute = it)) }
                ClockRow("Ends", b.endMinute) { bed(b.copy(endMinute = it)) }
                DayCircles(b.days) { bed(b.copy(days = it)) }
            }
        }
        item {
            SectionCard("During bedtime", icon = Icons.Default.NightsStay) {
                SwitchRow("Block apps", b.blockApps) { bed(b.copy(blockApps = it)) }
                if (b.blockApps) {
                    Text("Still allowed", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                    AppChips(b.allowedPackages, name, emptyText = "Phone, clock and messages stay open", onRemove = { bed(b.copy(allowedPackages = b.allowedPackages - it)) }, onAdd = { chooser = "allowed" })
                }
                SwitchRow("Do Not Disturb", b.doNotDisturb) { bed(b.copy(doNotDisturb = it)) }
                SwitchRow("Grayscale screen", b.grayscale) { bed(b.copy(grayscale = it)) }
                if (b.grayscale && !perms.grayscale) Text("Grayscale needs a one-time permission from a computer (adb).", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
            }
        }
        item {
            SectionCard("Quiet notifications", subtitle = "Held and delivered together", icon = Icons.Default.NotificationsPaused) {
                AppChips(c.quietApps, name, emptyText = "No apps held", onRemove = { save(c.copy(quietApps = c.quietApps - it)) }, onAdd = { chooser = "quiet" })
                SwitchRow("Only during focus and bedtime", c.quietOnlyDuringFocus) { save(c.copy(quietOnlyDuringFocus = it)) }
                Text("Delivered at", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    c.digestTimes.sorted().forEach { t ->
                        InputChip(selected = false, onClick = {}, label = { Text(clockText(t)) }, trailingIcon = {
                            IconButton(onClick = { save(c.copy(digestTimes = c.digestTimes - t)) }, Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Remove ${clockText(t)}", Modifier.size(16.dp)) }
                        })
                    }
                    AssistChip(onClick = { addTime = true }, label = { Text("Add time") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
                }
                if (held.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Held now · ${held.size}", style = MaterialTheme.typography.titleSmall)
                    NotificationDigest.summary(held).take(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Chronora.muted) }
                    Row {
                        TextButton(onClick = { WellbeingAlarmReceiver.deliverDigest(context); held = store.held(); Feedback.show("Delivered") }) { Text("Deliver now") }
                        TextButton(onClick = { confirm.ask("held notifications") { store.clearHeld(); held = emptyList() } }) { Text("Clear") }
                    }
                }
            }
        }
    }

    chooser?.let { mode ->
        val current = if (mode == "quiet") c.quietApps else b.allowedPackages
        AppChooser(if (mode == "quiet") "Hold notifications from" else "Allowed at bedtime", current, { chooser = null }) { picked ->
            if (mode == "quiet") save(c.copy(quietApps = picked)) else bed(b.copy(allowedPackages = picked))
            chooser = null
        }
    }
    if (addTime) ClockDialog(20 * 60, { addTime = false }) { t -> save(c.copy(digestTimes = (c.digestTimes + t).distinct().sorted())); addTime = false }
}

/** A minutes value shown as "1h 30m", or [zero] when 0. */
@Composable
private fun MinutesRow(label: String, value: Int, step: Int, min: Int = 0, max: Int = 16 * 60, zero: String = "0m", onChange: (Int) -> Unit) =
    Stepper(label, if (value == 0) zero else hmText(value), { onChange((value - step).coerceAtLeast(min)) }, { onChange((value + step).coerceAtMost(max)) })
