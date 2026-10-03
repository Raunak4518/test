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

/** Picks the option closest to [value], for chip rows over a fixed set of values. */
internal fun nearest(options: List<Int>, value: Int) = options.indices.minByOrNull { kotlin.math.abs(options[it] - value) } ?: 0

/**
 * App blocker. Anything that loosens a rule goes through the turn-off protection, and during a locked
 * session nothing can be loosened at all.
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
    val offGuard = rememberOffGuard()
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
    var length by remember { mutableIntStateOf(config.sessionPresets.getOrElse(1) { config.sessionPresets.firstOrNull() ?: 25 }) }

    val sessionOn = config.sessionUntil > now
    val locked = sessionOn && config.lockedMode
    /** Loosening: refused in a locked session, otherwise behind the turn-off protection. */
    fun loosen(what: String, action: () -> Unit) { if (locked) Feedback.show("🔒 Locked until the session ends") else offGuard.ask(what, action) }
    val active = if (config.allowlistMode) config.allowedPackages else config.blockedPackages

    ScreenList {
        item {
            SetupBanner(listOf(
                SetupStep("Accessibility", serviceOn) { openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                SetupStep("Usage access", usageOn) { openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS) }
            ))
        }
        item {
            val left = ((config.sessionUntil - now) / 1000).coerceAtLeast(0)
            ModeHero(
                on = sessionOn, title = "Focus session", icon = if (locked) Icons.Default.Lock else Icons.Default.Block,
                onLabel = if (locked) "LOCKED" else "ON",
                status = if (sessionOn) (if (left >= 3600) "%d:%02d:%02d left".format(left / 3600, left / 60 % 60, left % 60) else "%02d:%02d left".format(left / 60, left % 60)) + " · ${active.size} apps"
                    else if (config.allowlistMode) "Only ${active.size} apps allowed" else "${active.size} apps · ${hmText(length)}",
                onToggle = {
                    when {
                        locked -> Feedback.show("🔒 Locked until the session ends")
                        sessionOn -> offGuard.ask("this session") { store.update { it.copy(sessionUntil = 0) } }
                        !config.allowlistMode && config.blockedPackages.isEmpty() -> { chooser = "block"; Feedback.show("Pick the apps to block first") }
                        else -> { store.update { it.copy(sessionUntil = System.currentTimeMillis() + length * 60_000L) }; Feedback.show("🛡️ Blocking for ${hmText(length)}. You've got this.") }
                    }
                }
            ) {
                if (!sessionOn) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.sessionPresets.forEach { m ->
                        FilterChip(length == m, { length = m }, label = { Text(hmText(m)) }, shape = MaterialTheme.shapes.extraLarge)
                    }
                }
            }
        }

        item {
            SectionCard("Apps", icon = Icons.Default.Apps) {
                PillTabs(listOf("Block these", "Allow only these"), if (config.allowlistMode) 1 else 0) { i ->
                    if (i == 0 && config.allowlistMode) loosen("allow-only mode") { store.update { it.copy(allowlistMode = false) } }
                    else if (i == 1 && !config.allowlistMode) { store.update { it.copy(allowlistMode = true) }; Feedback.show("Only the apps you allow will open") }
                }
                AppChips(active, name, emptyText = if (config.allowlistMode) "Every other app is blocked" else "No apps yet",
                    onRemove = { pkg -> loosen(name(pkg) + " blocking") { store.update { if (it.allowlistMode) it.copy(allowedPackages = it.allowedPackages - pkg) else it.copy(blockedPackages = it.blockedPackages - pkg) } } },
                    onAdd = { chooser = if (config.allowlistMode) "allow" else "block" })
                if (!config.allowlistMode && (config.blockLists.isNotEmpty() || config.blockedPackages.isNotEmpty())) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        config.blockLists.forEach { (n, pkgs) ->
                            FilterChip(pkgs == config.blockedPackages, {
                                if (pkgs.containsAll(config.blockedPackages)) { store.update { it.copy(blockedPackages = pkgs) }; Feedback.show("Using $n") }
                                else loosen("part of the current list") { store.update { it.copy(blockedPackages = pkgs) } }
                            }, label = { Text("$n · ${pkgs.size}") }, leadingIcon = { Icon(Icons.Default.Bookmark, null, Modifier.size(16.dp)) },
                                trailingIcon = { IconButton(onClick = { confirm.ask("the list $n") { store.update { it.copy(blockLists = it.blockLists - n) } } }, Modifier.size(22.dp)) { Icon(Icons.Default.Close, "Delete $n", Modifier.size(14.dp)) } })
                        }
                        if (config.blockedPackages.isNotEmpty()) AssistChip(onClick = { saveList = true }, label = { Text("Save list") }, leadingIcon = { Icon(Icons.Default.BookmarkAdd, null, Modifier.size(16.dp)) })
                    }
                }
            }
        }

        item {
            SectionCard("Schedules", icon = Icons.Default.Schedule, action = { TextButton(onClick = { newSchedule = true }) { Text("Add") } }) {
                if (config.schedules.isEmpty()) Text("None", color = Chronora.muted)
                config.schedules.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${clockText(s.startMinute)}–${clockText(s.endMinute)} · ${dayText(s.days)}", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                        }
                        Switch(s.enabled, { v ->
                            val apply = { store.update { c -> c.copy(schedules = c.schedules.map { if (it.id == s.id) it.copy(enabled = v) else it }) } }
                            if (v) apply() else loosen(s.name, apply)
                        })
                        IconButton(onClick = { loosen("and delete ${s.name}") { store.update { c -> c.copy(schedules = c.schedules.filterNot { it.id == s.id }) } } }) { Icon(Icons.Default.DeleteOutline, "Delete ${s.name}") }
                    }
                }
            }
        }

        item {
            SectionCard("Short videos", icon = Icons.Default.SmartDisplay) {
                ShortForm.values().forEach { sf ->
                    SwitchRow(sf.label, sf in w.blockedShortForm) { v -> if (v) { saveW(w.copy(blockedShortForm = w.blockedShortForm + sf)); Feedback.show("${sf.label} blocked") } else loosen(sf.label + " blocking") { saveW(w.copy(blockedShortForm = w.blockedShortForm - sf)) } }
                }
                Expandable("Fix detection") {
                    ShortForm.values().forEach { sf -> ListEditor(sf.label, w.idsFor(sf)) { ids -> saveW(w.copy(shortFormIds = w.shortFormIds + (sf.name to ids))) } }
                    TextButton(onClick = { saveW(w.copy(shortFormIds = emptyMap())) }) { Text("Restore built-in") }
                }
            }
        }

        item {
            var site by remember { mutableStateOf("") }
            SectionCard("Websites", icon = Icons.Default.Language) {
                SwitchRow("Block all day", config.sitesAlwaysBlocked) { v -> if (v) store.update { it.copy(sitesAlwaysBlocked = true) } else loosen("all-day site blocking") { store.update { it.copy(sitesAlwaysBlocked = false) } } }
                if (config.blockedSites.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.blockedSites.sorted().forEach { d -> TextChip(d) { loosen("$d blocking") { store.update { it.copy(blockedSites = it.blockedSites - d) } } } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(site, { site = it }, placeholder = { Text("site.com") }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small)
                    FilledTonalIconButton(onClick = {
                        val v = WebsiteRules.normalize(site).trimEnd('/')
                        if (v.contains('.')) { store.update { it.copy(blockedSites = it.blockedSites + v) }; site = ""; Feedback.show("$v blocked") } else Feedback.show("Type a site like reddit.com")
                    }) { Icon(Icons.Default.Add, "Add site") }
                }
            }
        }

        item {
            SectionCard("Mindful pause", icon = Icons.Default.SelfImprovement) {
                AppChips(config.mindfulPackages, name, emptyText = "No apps yet",
                    onRemove = { pkg -> loosen("the pause for ${name(pkg)}") { store.update { it.copy(mindfulPackages = it.mindfulPackages - pkg) } } },
                    onAdd = { chooser = "mindful" })
                if (config.mindfulPackages.isNotEmpty()) {
                    ChoiceRow("Pause", listOf(5, 8, 15, 30), config.interventionSeconds, { "${it}s" }) { v ->
                        if (v >= config.interventionSeconds) store.update { it.copy(interventionSeconds = v) } else loosen("a longer pause") { store.update { it.copy(interventionSeconds = v) } }
                    }
                    ChoiceRow("Then open for", listOf(2, 5, 10, 15), config.interventionGrantMinutes, { "${it}m" }) { v ->
                        if (v <= config.interventionGrantMinutes) store.update { it.copy(interventionGrantMinutes = v) } else loosen("a shorter open time") { store.update { it.copy(interventionGrantMinutes = v) } }
                    }
                    val steps = listOf(0, 2, 5)
                    ChoiceRow("Grows each open", steps, w.progressiveDelayStep, { if (it == 0) "No" else "+${it}s" }) { v -> saveW(w.copy(progressiveDelayStep = v)) }
                }
            }
        }

        item {
            SectionCard("Strictness", icon = Icons.Default.Lock) {
                if (locked) {
                    Text("🔒 Can't change during the session", color = Chronora.muted)
                } else {
                    SwitchRow("Locked sessions", config.lockedMode) { v -> if (v) { store.update { it.copy(lockedMode = true) }; Feedback.show("Sessions can't be ended early now") } else loosen("locked sessions") { store.update { it.copy(lockedMode = false) } } }
                    SwitchRow("Guard Settings & uninstall", w.strictMode) { v -> if (v) saveW(w.copy(strictMode = true)) else loosen("the Settings guard") { saveW(w.copy(strictMode = false)) } }
                    SwitchRow("Do Not Disturb in sessions", w.doNotDisturbDuringFocus) { v -> saveW(w.copy(doNotDisturbDuringFocus = v)) }
                    ChoiceRow("Emergency unlocks", listOf(0, 1, 2, 3), config.emergencyUnlocksPerDay, { if (it == 0) "None" else "$it" }) { v ->
                        if (v <= config.emergencyUnlocksPerDay) store.update { it.copy(emergencyUnlocksPerDay = v) } else loosen("more emergency unlocks") { store.update { it.copy(emergencyUnlocksPerDay = v) } }
                    }
                    Expandable("Turn-off protection") { OffGuardSettings(::loosen) }
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
            val apply: (Set<String>) -> Unit = { next -> store.update { when (mode) { "allow" -> it.copy(allowedPackages = next); "mindful" -> it.copy(mindfulPackages = next); else -> it.copy(blockedPackages = next) } } }
            // Adding is always fine (for allow-only, adding loosens, so it's protected).
            val loosening = if (mode == "allow") (picked - current).isNotEmpty() else removed.isNotEmpty()
            if (loosening) loosen("part of this list") { apply(picked) } else { apply(picked); if ((picked - current).isNotEmpty()) Feedback.show("${(picked - current).size} added") }
            chooser = null
        }
    }
    if (newSchedule) ScheduleDialog({ newSchedule = false }) { s -> store.update { it.copy(schedules = it.schedules + s) }; newSchedule = false; Feedback.show("${s.name} added") }
    if (saveList) {
        var listName by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { saveList = false }, title = { Text("Save ${config.blockedPackages.size} apps as") },
            text = { OutlinedTextField(listName, { listName = it }, placeholder = { Text("Study") }, singleLine = true) },
            confirmButton = { TextButton(enabled = listName.isNotBlank(), onClick = { store.update { it.copy(blockLists = it.blockLists + (listName.trim() to it.blockedPackages)) }; saveList = false; Feedback.show("Saved") }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { saveList = false }) { Text("Cancel") } })
    }
}

/** A labelled row of chips over fixed values, so small numbers are one tap instead of a dial. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoiceRow(label: String, options: List<Int>, value: Int, text: (Int) -> String, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        PillTabs(options.map(text), nearest(options, value)) { i -> if (options[i] != value) onPick(options[i]) }
    }
}

/** Wait length and the sentence for the turn-off protection. Making it easier is itself protected. */
@Composable
internal fun OffGuardSettings(loosen: (String, () -> Unit) -> Unit) {
    val context = LocalContext.current
    var wait by remember { mutableIntStateOf(OffGuardPrefs.waitSeconds(context)) }
    var phrase by remember { mutableStateOf(OffGuardPrefs.phrase(context)) }
    val waits = listOf(15, 30, 60, 120, 300)
    ChoiceRow("Wait", waits, wait, { if (it >= 60) "${it / 60}m" else "${it}s" }) { v ->
        val apply = { OffGuardPrefs.set(context, v, phrase); wait = v }
        if (v >= wait) apply() else loosen("a shorter wait", apply)
    }
    var draft by remember { mutableStateOf(phrase) }
    OutlinedTextField(draft, { draft = it }, label = { Text("Sentence to type") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        trailingIcon = { if (draft.trim() != phrase && draft.isNotBlank()) TextButton(onClick = {
            val apply = { OffGuardPrefs.set(context, wait, draft); phrase = OffGuardPrefs.phrase(context); Feedback.show("Saved") }
            if (draft.trim().length >= phrase.length) apply() else loosen("a shorter sentence", apply)
        }) { Text("Save") } })
}

@Composable
private fun ScheduleDialog(close: () -> Unit, onSave: (BlockSchedule) -> Unit) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableIntStateOf(9 * 60) }
    var end by remember { mutableIntStateOf(13 * 60) }
    var days by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    AlertDialog(onDismissRequest = close, title = { Text("New schedule") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, placeholder = { Text("Study hours") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ClockRow("Starts", start) { start = it }
            ClockRow("Ends", end) { end = it }
            DayCircles(days) { days = it }
        }
    }, confirmButton = {
        TextButton(enabled = days.isNotEmpty() && start != end, onClick = { onSave(BlockSchedule(System.currentTimeMillis(), name.trim().ifBlank { "Focus hours" }, days, start, end)) }) { Text("Add") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
