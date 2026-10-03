package com.raunak.daytimeline.wellbeing

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
import com.raunak.daytimeline.pro.BlockSchedule
import com.raunak.daytimeline.pro.FocusGuardService
import com.raunak.daytimeline.pro.FocusGuardStore
import com.raunak.daytimeline.pro.UsageAccess
import com.raunak.daytimeline.pro.WebsiteRules
import com.raunak.daytimeline.ui.*
import kotlinx.coroutines.delay

/**
 * App blocker: start a session, pick the apps, set schedules, short videos, websites, mindful pause and how strict
 * it is. While a session runs in locked mode nothing here can be loosened.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlockerTab() {
    val context = LocalContext.current
    val store = remember { FocusGuardStore(context) }
    val config by store.configFlow.collectAsState()
    val wStore = remember { WellbeingStore(context) }
    var w by remember { mutableStateOf(wStore.config) }
    fun saveW(next: WellbeingConfig) { wStore.config = next; w = next; WellbeingAlarmReceiver.schedule(context) }
    val apps = rememberLauncherApps()
    val name = rememberAppNamer(apps)
    val confirm = rememberConfirm()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var serviceOn by remember { mutableStateOf(FocusGuardService.isEnabled(context)) }
    var usageOn by remember { mutableStateOf(UsageAccess.granted(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            if (now / 1000 % 3 == 0L) { serviceOn = FocusGuardService.isEnabled(context); usageOn = UsageAccess.granted(context) }
            delay(1000)
        }
    }
    var chooser by remember { mutableStateOf<String?>(null) }
    var newSchedule by remember { mutableStateOf(false) }
    var saveList by remember { mutableStateOf(false) }

    val sessionOn = config.sessionUntil > now
    val locked = sessionOn && config.lockedMode
    /** Runs [action] unless a locked session forbids loosening the rules. */
    fun guard(action: () -> Unit) { if (locked) Feedback.show("Locked until the session ends") else action() }
    val active = if (config.allowlistMode) config.allowedPackages else config.blockedPackages

    ScreenList {
        item {
            SetupBanner(listOf(
                SetupStep("Accessibility, so apps can be blocked", serviceOn) { openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                SetupStep("Usage access, for limits and stats", usageOn) { openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS) }
            ))
        }
        item { SessionHero(config.sessionUntil, now, config.lockedMode, config.allowlistMode, active.size, config.sessionPresets,
            onStart = { m ->
                if (!config.allowlistMode && config.blockedPackages.isEmpty()) Feedback.show("Add apps to block first")
                else { store.update { it.copy(sessionUntil = System.currentTimeMillis() + m * 60_000L) }; Feedback.show("Blocking for ${hmText(m)}") }
            },
            onEnd = { store.update { it.copy(sessionUntil = 0) }; Feedback.show("Session ended") }) }

        item {
            SectionCard("Apps", icon = Icons.Default.Apps) {
                PillTabs(listOf("Block these", "Allow only these"), if (config.allowlistMode) 1 else 0) { i ->
                    // Switching to "allow only" is stricter, so it is fine even while locked.
                    if (i == 0 && config.allowlistMode) guard { store.update { it.copy(allowlistMode = false) } }
                    else if (i == 1) store.update { it.copy(allowlistMode = true) }
                }
                AppChips(active, name,
                    emptyText = if (config.allowlistMode) "Every other app is blocked during a session" else "No apps yet",
                    onRemove = { pkg -> guard {
                        store.update { if (it.allowlistMode) it.copy(allowedPackages = it.allowedPackages - pkg) else it.copy(blockedPackages = it.blockedPackages - pkg) }
                        Feedback.show("${name(pkg)} removed") { store.update { if (it.allowlistMode) it.copy(allowedPackages = it.allowedPackages + pkg) else it.copy(blockedPackages = it.blockedPackages + pkg) } }
                    } },
                    onAdd = { chooser = if (config.allowlistMode) "allow" else "block" })
                if (!config.allowlistMode) {
                    if (config.blockLists.isNotEmpty()) {
                        Text("Saved lists", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            config.blockLists.forEach { (n, pkgs) ->
                                InputChip(selected = pkgs == config.blockedPackages, onClick = {
                                    val before = config.blockedPackages
                                    if (locked && !pkgs.containsAll(before)) Feedback.show("Locked until the session ends")
                                    else { store.update { it.copy(blockedPackages = pkgs) }; Feedback.show("Using $n") { store.update { it.copy(blockedPackages = before) } } }
                                }, label = { Text("$n · ${pkgs.size}") },
                                    trailingIcon = { IconButton(onClick = { confirm.ask("the list $n") { store.update { it.copy(blockLists = it.blockLists - n) } } }, Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Delete $n", Modifier.size(16.dp)) } })
                            }
                        }
                    }
                    if (config.blockedPackages.isNotEmpty()) TextButton(onClick = { saveList = true }) { Icon(Icons.Default.BookmarkAdd, null, Modifier.size(18.dp)); Text("  Save as a list") }
                }
            }
        }

        item {
            SectionCard("Schedules", icon = Icons.Default.Schedule, action = { TextButton(onClick = { newSchedule = true }) { Text("Add") } }) {
                if (config.schedules.isEmpty()) Text("Block the apps automatically at set times", color = Chronora.muted)
                config.schedules.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${clockText(s.startMinute)}–${clockText(s.endMinute)} · ${dayText(s.days)}", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                        }
                        Switch(s.enabled, { v -> val apply = { store.update { c -> c.copy(schedules = c.schedules.map { if (it.id == s.id) it.copy(enabled = v) else it }) } }; if (v) apply() else guard(apply) })
                        IconButton(onClick = { guard { confirm.ask("the schedule ${s.name}") { store.update { c -> c.copy(schedules = c.schedules.filterNot { it.id == s.id }) } } } }) { Icon(Icons.Default.DeleteOutline, "Delete ${s.name}") }
                    }
                }
            }
        }

        item {
            SectionCard("Short videos", icon = Icons.Default.SmartDisplay) {
                ShortForm.values().forEach { sf ->
                    val on = sf in w.blockedShortForm
                    SwitchRow(sf.label, on) { v -> if (v) saveW(w.copy(blockedShortForm = w.blockedShortForm + sf)) else guard { saveW(w.copy(blockedShortForm = w.blockedShortForm - sf)) } }
                }
                Expandable("Screen ids (if an app update breaks blocking)") {
                    ShortForm.values().forEach { sf -> ListEditor(sf.label, w.idsFor(sf)) { ids -> saveW(w.copy(shortFormIds = w.shortFormIds + (sf.name to ids))) } }
                    TextButton(onClick = { saveW(w.copy(shortFormIds = emptyMap())) }) { Text("Restore built-in ids") }
                }
            }
        }

        item {
            var site by remember { mutableStateOf("") }
            SectionCard("Websites", subtitle = "Blocked in your browsers", icon = Icons.Default.Language) {
                SwitchRow("Block all day, not only in sessions", config.sitesAlwaysBlocked) { v -> if (v) store.update { it.copy(sitesAlwaysBlocked = true) } else guard { store.update { it.copy(sitesAlwaysBlocked = false) } } }
                if (config.blockedSites.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.blockedSites.sorted().forEach { d ->
                        InputChip(selected = false, onClick = {}, label = { Text(d) }, trailingIcon = {
                            IconButton(onClick = { guard { store.update { it.copy(blockedSites = it.blockedSites - d) }; Feedback.show("$d removed") { store.update { it.copy(blockedSites = it.blockedSites + d) } } } }, Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Remove $d", Modifier.size(16.dp)) }
                        })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(site, { site = it }, placeholder = { Text("site.com or site.com/page") }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small)
                    FilledTonalIconButton(onClick = {
                        val v = WebsiteRules.normalize(site).trimEnd('/')
                        if (v.contains('.')) { store.update { it.copy(blockedSites = it.blockedSites + v) }; site = "" } else Feedback.show("Type a site like reddit.com")
                    }) { Icon(Icons.Default.Add, "Add site") }
                }
            }
        }

        item {
            SectionCard("Mindful pause", subtitle = "A breathing pause before these open", icon = Icons.Default.SelfImprovement) {
                AppChips(config.mindfulPackages, name, emptyText = "No apps yet",
                    onRemove = { pkg -> guard { store.update { it.copy(mindfulPackages = it.mindfulPackages - pkg) } } },
                    onAdd = { chooser = "mindful" })
                Stepper("Pause length", "${config.interventionSeconds}s",
                    { guard { store.update { it.copy(interventionSeconds = (it.interventionSeconds - 1).coerceAtLeast(3)) } } },
                    { store.update { it.copy(interventionSeconds = (it.interventionSeconds + 1).coerceAtMost(60)) } })
                Stepper("Then stays open for", hmText(config.interventionGrantMinutes),
                    { store.update { it.copy(interventionGrantMinutes = (it.interventionGrantMinutes - 1).coerceAtLeast(1)) } },
                    { guard { store.update { it.copy(interventionGrantMinutes = (it.interventionGrantMinutes + 1).coerceAtMost(60)) } } })
                Text("Pause grows each time you open", style = MaterialTheme.typography.bodyLarge)
                val steps = listOf(0, 2, 5)
                PillTabs(listOf("Fixed", "+2s", "+5s"), steps.indexOf(w.progressiveDelayStep).coerceAtLeast(0)) { i -> saveW(w.copy(progressiveDelayStep = steps[i])) }
            }
        }

        item {
            SectionCard("Strictness", icon = Icons.Default.Lock) {
                if (locked) {
                    Text("Locked until ${clockText(java.time.Instant.ofEpochMilli(config.sessionUntil).atZone(java.time.ZoneId.systemDefault()).toLocalTime().let { it.hour * 60 + it.minute })}. These can't change during the session.", color = Chronora.muted)
                } else {
                    SwitchRow("Locked mode: no ending early", config.lockedMode) { v -> store.update { it.copy(lockedMode = v) } }
                    SwitchRow("Guard Settings and uninstall", w.strictMode) { v -> saveW(w.copy(strictMode = v)) }
                    SwitchRow("Do Not Disturb during focus", w.doNotDisturbDuringFocus) { v -> saveW(w.copy(doNotDisturbDuringFocus = v)) }
                    Stepper("Emergency unlocks a day", if (config.emergencyUnlocksPerDay == 0) "None" else "${config.emergencyUnlocksPerDay}",
                        { store.update { it.copy(emergencyUnlocksPerDay = (it.emergencyUnlocksPerDay - 1).coerceAtLeast(0)) } },
                        { store.update { it.copy(emergencyUnlocksPerDay = (it.emergencyUnlocksPerDay + 1).coerceAtMost(5)) } })
                    if (config.emergencyUnlocksPerDay > 0) {
                        Stepper("Wait before unlocking", "${config.unlockDelaySeconds}s",
                            { store.update { it.copy(unlockDelaySeconds = (it.unlockDelaySeconds - 5).coerceAtLeast(5)) } },
                            { store.update { it.copy(unlockDelaySeconds = (it.unlockDelaySeconds + 5).coerceAtMost(300)) } })
                        Stepper("Unlock lasts", hmText(config.emergencyUnlockMinutes),
                            { store.update { it.copy(emergencyUnlockMinutes = (it.emergencyUnlockMinutes - 1).coerceAtLeast(1)) } },
                            { store.update { it.copy(emergencyUnlockMinutes = (it.emergencyUnlockMinutes + 1).coerceAtMost(60)) } })
                        var phrase by remember(config.unlockPhrase) { mutableStateOf(config.unlockPhrase) }
                        OutlinedTextField(phrase, { phrase = it }, label = { Text("Sentence to type before unlocking") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
                            trailingIcon = { if (phrase.trim() != config.unlockPhrase) TextButton(onClick = { store.update { it.copy(unlockPhrase = phrase.trim()) }; Feedback.show("Saved") }) { Text("Save") } })
                    }
                    Expandable("Session lengths") {
                        ListEditor("Minutes", config.sessionPresets.map { it.toString() }, numeric = true) { v -> store.update { it.copy(sessionPresets = v.mapNotNull(String::toIntOrNull).filter { n -> n > 0 }.distinct().sorted().ifEmpty { listOf(25) }) } }
                    }
                }
            }
        }
    }

    chooser?.let { mode ->
        val current = when (mode) { "allow" -> config.allowedPackages; "mindful" -> config.mindfulPackages; else -> config.blockedPackages }
        AppChooser(when (mode) { "allow" -> "Apps you can still use"; "mindful" -> "Pause before opening"; else -> "Apps to block" }, current, { chooser = null }) { picked ->
            val removed = current - picked
            if (removed.isNotEmpty() && locked) Feedback.show("Locked: apps can only be added during the session")
            val next = if (locked) current + picked else picked
            store.update { when (mode) { "allow" -> it.copy(allowedPackages = next); "mindful" -> it.copy(mindfulPackages = next); else -> it.copy(blockedPackages = next) } }
            chooser = null
        }
    }
    if (newSchedule) ScheduleDialog({ newSchedule = false }) { s -> store.update { it.copy(schedules = it.schedules + s) }; newSchedule = false; Feedback.show("${s.name} added") }
    if (saveList) {
        var listName by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { saveList = false }, title = { Text("Save these ${config.blockedPackages.size} apps") },
            text = { OutlinedTextField(listName, { listName = it }, label = { Text("List name") }, singleLine = true) },
            confirmButton = { TextButton(enabled = listName.isNotBlank(), onClick = { store.update { it.copy(blockLists = it.blockLists + (listName.trim() to it.blockedPackages)) }; saveList = false; Feedback.show("Saved") }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { saveList = false }) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionHero(until: Long, now: Long, lockedMode: Boolean, allowlist: Boolean, count: Int, presets: List<Int>, onStart: (Int) -> Unit, onEnd: () -> Unit) {
    val onHero = Chronora.colors.onHero
    val heroButton = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .2f), contentColor = onHero)
    HeroCard {
        if (until > now) {
            val left = (until - now) / 1000
            Text(if (lockedMode) "Locked session" else "Blocking now", style = MaterialTheme.typography.labelLarge, color = Chronora.colors.heroMuted)
            Text(if (left >= 3600) "%d:%02d:%02d".format(left / 3600, left / 60 % 60, left % 60) else "%02d:%02d".format(left / 60, left % 60),
                style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text(if (allowlist) "Only $count allowed apps open" else "$count apps blocked", color = Chronora.colors.heroMuted)
            if (!lockedMode) Button(onClick = onEnd, colors = heroButton) { Text("End session") }
            else Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lock, null, Modifier.size(16.dp)); Text("  Can't be ended early", style = MaterialTheme.typography.labelLarge) }
        } else {
            Text("Focus session", style = MaterialTheme.typography.labelLarge, color = Chronora.colors.heroMuted)
            Text(if (allowlist) "Allow only $count apps" else if (count == 0) "Pick apps to block" else "Block $count apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { m -> Button(onClick = { onStart(m) }, colors = heroButton, contentPadding = PaddingValues(horizontal = 16.dp)) { Text(hmText(m)) } }
            }
            if (lockedMode) Text("Locked mode is on", style = MaterialTheme.typography.labelMedium, color = Chronora.colors.heroMuted)
        }
    }
}

@Composable
private fun ScheduleDialog(close: () -> Unit, onSave: (BlockSchedule) -> Unit) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableIntStateOf(9 * 60) }
    var end by remember { mutableIntStateOf(13 * 60) }
    var days by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    AlertDialog(onDismissRequest = close, title = { Text("New schedule") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, placeholder = { Text("Study hours") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ClockRow("Starts", start) { start = it }
            ClockRow("Ends", end) { end = it }
            DayCircles(days) { days = it }
            if (end <= start) Text("Ends the next day", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
        }
    }, confirmButton = {
        TextButton(enabled = days.isNotEmpty() && start != end, onClick = { onSave(BlockSchedule(System.currentTimeMillis(), name.trim().ifBlank { "Focus hours" }, days, start, end)) }) { Text("Add") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
