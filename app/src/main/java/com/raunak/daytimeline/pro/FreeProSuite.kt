package com.raunak.daytimeline.pro

import com.raunak.daytimeline.ui.*

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.raunak.daytimeline.PlannerViewModel
import com.raunak.daytimeline.domain.QuickAddParser
import java.time.LocalDate

private data class InstalledApp(val packageName: String, val label: String)

/** The extra tools, each opened as its own page from More. */
enum class ProTool(val title: String) {
    QUICK_ADD("Quick add"), SEARCH("Search"), WEEK("Week review"), SOUNDS("Focus sounds"), GARDEN("Focus garden"),
    ENERGY("Energy planner"), PLACES("Place reminders"), JOURNAL("Private journal")
}

@Composable
fun ProToolPage(vm: PlannerViewModel, tool: ProTool, onClose: () -> Unit) {
    val openDate: (LocalDate) -> Unit = { vm.selectDate(it); onClose() }
    FullScreenPage(tool.title, onClose) {
        when (tool) {
            ProTool.QUICK_ADD -> QuickAddTab(vm)
            ProTool.SEARCH -> SearchTab(vm, openDate)
            ProTool.WEEK -> WeekTab(vm, openDate)
            ProTool.SOUNDS -> SoundsTab(vm)
            ProTool.GARDEN -> GardenTab()
            ProTool.ENERGY -> EnergyTab(vm)
            ProTool.PLACES -> PlacesTab()
            ProTool.JOURNAL -> PrivateJournalTab()
        }
    }
}

@Composable
private fun QuickAddTab(vm: PlannerViewModel) {
    var text by remember { mutableStateOf("") }
    var added by remember { mutableStateOf<String?>(null) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text = it }
    }
    val preview = remember(text) { QuickAddParser.parse(text, LocalDate.now()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                text, { text = it; added = null },
                label = { Text("Type or speak a task") },
                placeholder = { Text("Revise CN tomorrow at 7 for 90m #exam p1 remind 10m before") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        runCatching {
                            voice.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a task, time and date")
                            )
                        }.onFailure { added = "No speech recogniser installed on this device" }
                    }) { Icon(Icons.Default.Mic, "Voice input") }
                }
            )
        }
        if (preview != null && text.isNotBlank()) item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(preview.title, fontWeight = FontWeight.Bold)
                Text("${preview.date} · ${clock(preview.startMinute)}–${clock(preview.endMinute)} (${preview.endMinute - preview.startMinute}m)")
                val extras = buildList {
                    if (preview.recurrenceType != "NONE") add("Repeats: " + preview.recurrenceType.lowercase().replace('_', ' ') + if (preview.recurrenceDays.isNotBlank()) " (${preview.recurrenceDays})" else "")
                    if (preview.priority != 1) add("Priority ${preview.priority}")
                    if (preview.tags.isNotBlank()) add("#" + preview.tags.replace(",", " #"))
                    if (preview.reminderMode != "NONE") add(if (preview.reminderMode == "AT_START") "Remind at start" else "Remind ${preview.reminderOffsetMinutes}m before")
                    if (preview.pomodoro) add("Pomodoro")
                }
                if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { vm.quickAddExact(text); added = "Added “${preview.title}” on ${preview.date}"; text = "" }) { Text("Add to timeline") }
            } }
        }
        added?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.primary) } }
    }
}

@Composable
internal fun FocusGuardTab() {
    val context = LocalContext.current
    val store = remember { FocusGuardStore(context) }
    val config by store.configFlow.collectAsState()
    var picker by remember { mutableStateOf<String?>(null) }
    var serviceOn by remember { mutableStateOf(FocusGuardService.isEnabled(context)) }
    var usageOn by remember { mutableStateOf(UsageAccess.granted(context)) }
    val now = System.currentTimeMillis()
    val apps = remember { installedApps(context) }
    val labels = remember(apps) { apps.associate { it.packageName to it.label } }
    fun name(pkg: String) = labels[pkg] ?: pkg

    LaunchedEffect(Unit) {
        while (true) { serviceOn = FocusGuardService.isEnabled(context); usageOn = UsageAccess.granted(context); kotlinx.coroutines.delay(2000) }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { com.raunak.daytimeline.wellbeing.TodayUsageStrip() }
        if (!serviceOn || !usageOn) item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Setup", fontWeight = FontWeight.Bold)
                Text(if (serviceOn) "✓ Blocking is on" else "Blocking is off")
                if (!serviceOn) Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Turn on") }
                Text(if (usageOn) "✓ Usage access" else "Usage access needed")
                if (!usageOn) OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Grant usage access") }
            } }
        }
        item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Focus session", fontWeight = FontWeight.Bold)
                if (config.sessionUntil > now) {
                    Text("Blocking until " + java.time.Instant.ofEpochMilli(config.sessionUntil).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0))
                    if (!config.lockedMode) OutlinedButton(onClick = { store.update { it.copy(sessionUntil = 0) } }) { Text("End session") }
                    else Text("Locked mode: the session cannot be ended early.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        config.sessionPresets.forEach { m ->
                            OutlinedButton(onClick = { store.update { it.copy(sessionUntil = System.currentTimeMillis() + m * 60_000L) } }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("${m}m") }
                        }
                    }
                }
                SwitchRow("Locked mode", config.lockedMode) { v -> store.update { it.copy(lockedMode = v) } }
                var phrase by remember(config.unlockPhrase) { mutableStateOf(config.unlockPhrase) }
                OutlinedTextField(phrase, { phrase = it }, label = { Text("Unlock phrase") }, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { if (phrase != config.unlockPhrase) TextButton(onClick = { store.update { it.copy(unlockPhrase = phrase.trim()) } }) { Text("Save") } })
                if (config.unlockPhrase.isBlank()) TextButton(onClick = { phrase = "I am choosing distraction over my goals right now" }) { Text("Use a suggested phrase") }
                SwitchRow("Allowlist mode", config.allowlistMode) { v -> store.update { it.copy(allowlistMode = v) } }
                Text("Emergency unlocks per day: ${config.emergencyUnlocksPerDay} · wait ${config.unlockDelaySeconds}s", style = MaterialTheme.typography.bodySmall)
                Slider(config.emergencyUnlocksPerDay.toFloat(), { v -> store.update { it.copy(emergencyUnlocksPerDay = v.toInt()) } }, valueRange = 0f..5f, steps = 4)
                Slider(config.unlockDelaySeconds.toFloat(), { v -> store.update { it.copy(unlockDelaySeconds = v.toInt()) } }, valueRange = 5f..120f)
                Stepper("Emergency unlock lasts", "${config.emergencyUnlockMinutes}m", { store.update { it.copy(emergencyUnlockMinutes = (it.emergencyUnlockMinutes - 1).coerceAtLeast(1)) } }, { store.update { it.copy(emergencyUnlockMinutes = it.emergencyUnlockMinutes + 1) } })
                Stepper("Mindful pause length", "${config.interventionSeconds}s", { store.update { it.copy(interventionSeconds = (it.interventionSeconds - 1).coerceAtLeast(3)) } }, { store.update { it.copy(interventionSeconds = (it.interventionSeconds + 1).coerceAtMost(60)) } })
                Stepper("App stays open after a pause", "${config.interventionGrantMinutes}m", { store.update { it.copy(interventionGrantMinutes = (it.interventionGrantMinutes - 1).coerceAtLeast(1)) } }, { store.update { it.copy(interventionGrantMinutes = it.interventionGrantMinutes + 1) } })
                ListEditor("Session buttons (minutes)", config.sessionPresets.map { it.toString() }, numeric = true) { v -> store.update { it.copy(sessionPresets = v.mapNotNull(String::toIntOrNull).filter { n -> n > 0 }) } }
            } }
        }
        item {
            PackageCard(
                title = if (config.allowlistMode) "Allowed apps" else "Blocked apps",
                subtitle = "",
                packages = if (config.allowlistMode) config.allowedPackages else config.blockedPackages,
                name = ::name,
                onAdd = { picker = if (config.allowlistMode) "allow" else "block" },
                onRemove = { pkg -> store.update { if (it.allowlistMode) it.copy(allowedPackages = it.allowedPackages - pkg) else it.copy(blockedPackages = it.blockedPackages - pkg) } }
            )
        }
        item {
            var listName by remember { mutableStateOf("") }
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Saved block lists", fontWeight = FontWeight.Bold)
                config.blockLists.forEach { (n, pkgs) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$n (${pkgs.size})", Modifier.weight(1f))
                        TextButton(onClick = { store.update { it.copy(blockedPackages = pkgs) } }) { Text("Use") }
                        IconButton(onClick = { store.update { it.copy(blockLists = it.blockLists - n) } }) { Icon(Icons.Default.Delete, "Delete list") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(listName, { listName = it }, label = { Text("Save current list as") }, modifier = Modifier.weight(1f), singleLine = true)
                    IconButton(onClick = { if (listName.isNotBlank()) { store.update { it.copy(blockLists = it.blockLists + (listName.trim() to it.blockedPackages)) }; listName = "" } }) { Icon(Icons.Default.Save, "Save list") }
                }
            } }
        }
        item {
            var site by remember { mutableStateOf("") }
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Blocked websites", fontWeight = FontWeight.Bold)
                SwitchRow("Block all day", config.sitesAlwaysBlocked) { v -> store.update { it.copy(sitesAlwaysBlocked = v) } }
                config.blockedSites.sorted().forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d, Modifier.weight(1f))
                        IconButton(onClick = { store.update { it.copy(blockedSites = it.blockedSites - d) } }) { Icon(Icons.Default.Close, "Remove site") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(site, { site = it }, label = { Text("Add website") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { val v = WebsiteRules.normalize(site).trimEnd('/'); if (v.contains('.')) { store.update { it.copy(blockedSites = it.blockedSites + v) }; site = "" } }) { Icon(Icons.Default.Add, "Add site") }
                }
            } }
        }
        item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recurring schedules", fontWeight = FontWeight.Bold)
                config.schedules.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name)
                            Text("${clock(s.startMinute)}–${clock(s.endMinute)} · " + s.days.sorted().joinToString(" ") { java.time.DayOfWeek.of(it).name.take(3).lowercase() }, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(s.enabled, { v -> store.update { c -> c.copy(schedules = c.schedules.map { if (it.id == s.id) it.copy(enabled = v) else it }) } })
                        IconButton(onClick = { store.update { c -> c.copy(schedules = c.schedules.filterNot { it.id == s.id }) } }) { Icon(Icons.Default.Delete, "Delete schedule") }
                    }
                }
                ScheduleEditor { schedule -> store.update { it.copy(schedules = it.schedules + schedule) } }
            } }
        }
        item {
            val usage by produceState(emptyMap<String, Int>(), usageOn) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { UsageAccess.today(context) } }
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Daily limits", fontWeight = FontWeight.Bold)
                config.dailyLimits.forEach { (pkg, limit) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.raunak.daytimeline.wellbeing.AppIcon(pkg, 30.dp)
                        Box(Modifier.weight(1f).padding(start = 10.dp)) {
                            Stepper("${name(pkg)} · ${usage[pkg] ?: 0}m used", "${limit}m",
                                { store.update { it.copy(dailyLimits = it.dailyLimits + (pkg to (limit - 5).coerceAtLeast(5))) } },
                                { store.update { it.copy(dailyLimits = it.dailyLimits + (pkg to limit + 5)) } })
                        }
                        IconButton(onClick = { store.update { it.copy(dailyLimits = it.dailyLimits - pkg) } }) { Icon(Icons.Default.Close, "Remove limit") }
                    }
                }
                OutlinedButton(onClick = { picker = "limit" }) { Text("Add app limit (30m)") }
            } }
        }
        item {
            PackageCard(
                title = "Mindful pause",
                subtitle = "${config.interventionSeconds}s pause before opening",
                packages = config.mindfulPackages,
                name = ::name,
                onAdd = { picker = "mindful" },
                onRemove = { pkg -> store.update { it.copy(mindfulPackages = it.mindfulPackages - pkg) } }
            )
        }
    }

    picker?.let { mode ->
        AppPicker(apps, onDismiss = { picker = null }) { pkg ->
            store.update {
                when (mode) {
                    "allow" -> it.copy(allowedPackages = it.allowedPackages + pkg)
                    "limit" -> it.copy(dailyLimits = it.dailyLimits + (pkg to 30))
                    "mindful" -> it.copy(mindfulPackages = it.mindfulPackages + pkg)
                    else -> it.copy(blockedPackages = it.blockedPackages + pkg)
                }
            }
            picker = null
        }
    }
}

@Composable
private fun ScheduleEditor(onAdd: (BlockSchedule) -> Unit) {
    var name by remember { mutableStateOf("Study hours") }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("13:00") }
    var days by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(start, { start = it }, label = { Text("Start HH:MM") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(end, { end = it }, label = { Text("End HH:MM") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..7).forEach { d ->
            FilterChip(d in days, { days = if (d in days) days - d else days + d }, label = { Text(java.time.DayOfWeek.of(d).name.take(1)) })
        }
    }
    Button(onClick = {
        val s = parseClock(start); val e = parseClock(end)
        if (s != null && e != null && days.isNotEmpty()) onAdd(BlockSchedule(System.currentTimeMillis(), name.ifBlank { "Focus hours" }, days, s, e))
    }) { Text("Add schedule") }
}

@Composable
private fun PackageCard(title: String, subtitle: String, packages: Set<String>, name: (String) -> String, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Chronora.muted) }
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add app") }
        }
        if (packages.isEmpty()) Text("None yet", style = MaterialTheme.typography.bodySmall)
        packages.sortedBy(name).forEach { pkg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name(pkg), Modifier.weight(1f))
                IconButton(onClick = { onRemove(pkg) }) { Icon(Icons.Default.Close, "Remove") }
            }
        }
    } }
}

@Composable
private fun AppPicker(apps: List<InstalledApp>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Search or type a package name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    val filtered = apps.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
                    items(filtered, key = { it.packageName }) { app ->
                        ListItem(headlineContent = { Text(app.label) }, supportingContent = { Text(app.packageName, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.clickable { onPick(app.packageName) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (query.contains('.')) onPick(query.trim()) else onDismiss() }) { Text(if (query.contains('.')) "Add package" else "Close") } }
    )
}

@Composable
private fun SoundsTab(vm: PlannerViewModel) {
    val context = LocalContext.current
    val prefs = remember { FocusSoundPrefs(context) }
    var sound by remember { mutableStateOf(prefs.sound) }
    var volume by remember { mutableFloatStateOf(prefs.volume) }
    var blockFocus by remember { mutableStateOf(prefs.blockDuringFocus) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
        }
        item {
            Card { Column(Modifier.padding(8.dp)) {
                (listOf<AmbientSound?>(null) + AmbientSound.values()).forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable { sound = s; prefs.sound = s; FocusSessionServiceSafe.notifySoundChanged(context) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(sound == s, { sound = s; prefs.sound = s; FocusSessionServiceSafe.notifySoundChanged(context) })
                        Text(s?.label ?: "Silence")
                    }
                }
            } }
        }
        item {
            Text("Volume ${(volume * 100).toInt()}%")
            Slider(volume, { volume = it; prefs.volume = it })
        }
        item { SwitchRow("Block apps during focus", blockFocus) { blockFocus = it; prefs.blockDuringFocus = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    vm.startPomodoro(null)
                }) { Text("Start Pomodoro") }
                OutlinedButton(onClick = { FocusSessionService.send(context, FocusSessionService.ACTION_SOUND_ONLY) }, enabled = sound != null) { Text("Play sound only") }
            }
        }
        item { TextButton(onClick = { FocusSessionService.send(context, FocusSessionService.ACTION_STOP) }) { Text("Stop session and sound") } }
    }
}

private object FocusSessionServiceSafe {
    fun notifySoundChanged(context: android.content.Context) {
        // Only nudge a running session; don't start a foreground service just for a preference change.
        val running = context.getSystemService(android.app.NotificationManager::class.java)?.activeNotifications?.any { it.id == 7301 } == true
        if (running) FocusSessionService.send(context, FocusSessionService.ACTION_SOUND_CHANGED)
    }
}

@Composable
private fun PlacesTab() {
    val context = LocalContext.current
    val manager = remember { LocationReminderManager(context) }
    val store = remember { LocationReminderStore(context) }
    var reminders by remember { mutableStateOf(store.all()) }
    var places by remember { mutableStateOf(store.places()) }
    var hasFine by remember { mutableStateOf(manager.hasPermission()) }
    var hasBackground by remember { mutableStateOf(manager.hasBackgroundPermission()) }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasFine = manager.hasPermission(); if (hasFine) manager.registerAll() }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasBackground = manager.hasBackgroundPermission() }

    var title by remember { mutableStateOf("") }
    var placeName by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var radius by remember { mutableFloatStateOf(150f) }
    var trigger by remember { mutableStateOf(PlaceTrigger.ARRIVE) }
    var repeat by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Location reminders", fontWeight = FontWeight.Bold)
                if (!hasFine) Button(onClick = { fineLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Allow location") }
                else if (!hasBackground && Build.VERSION.SDK_INT >= 29) {
                    Text("Background location is off", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) { Text("Allow background location") }
                }
            } }
        }
        item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Remind me to…") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(placeName, { placeName = it }, label = { Text("Place name (Home, College, Gym)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (places.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    places.take(4).forEach { p -> AssistChip({ placeName = p.name; lat = p.latitude.toString(); lon = p.longitude.toString() }, label = { Text(p.name) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedButton(enabled = hasFine, onClick = {
                    status = "Getting a GPS fix…"
                    manager.currentLocation { loc ->
                        if (loc == null) status = "No fix yet — try outdoors or enable location"
                        else { lat = "%.6f".format(java.util.Locale.US, loc.latitude); lon = "%.6f".format(java.util.Locale.US, loc.longitude); status = "Using current location (±${loc.accuracy.toInt()} m)" }
                    }
                }) { Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(6.dp)); Text("Use where I am now") }
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
                Text("Radius ${radius.toInt()} m")
                Slider(radius, { radius = it }, valueRange = 50f..1000f)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaceTrigger.values().forEach { t -> FilterChip(trigger == t, { trigger = t }, label = { Text(t.label) }) }
                }
                SwitchRow("Every visit (off = once)", repeat) { repeat = it }
                Button(onClick = {
                    val la = lat.toDoubleOrNull(); val lo = lon.toDoubleOrNull()
                    if (title.isBlank() || la == null || lo == null) { status = "Add a title and coordinates"; return@Button }
                    val place = placeName.ifBlank { "Saved place" }
                    manager.save(LocationReminder(System.currentTimeMillis(), title.trim(), place, la, lo, radius, trigger, true, repeat))
                    store.savePlace(SavedPlace(place, la, lo))
                    reminders = store.all(); places = store.places()
                    title = ""; status = "Saved"
                }) { Text("Save place reminder") }
            } }
        }
        items(reminders, key = { it.id }) { r ->
            Card { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.title, fontWeight = FontWeight.SemiBold)
                    Text("${r.trigger.label} · ${r.placeName} · ${r.radiusMeters.toInt()} m" + if (!r.repeat) " · once" else "", style = MaterialTheme.typography.bodySmall)
                }
                Switch(r.enabled, { v -> manager.save(r.copy(enabled = v)); reminders = store.all() })
                IconButton(onClick = { manager.delete(r.id); reminders = store.all() }) { Icon(Icons.Default.Delete, "Delete") }
            } }
        }
    }
}


private fun installedApps(context: android.content.Context): List<InstalledApp> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.applicationInfo }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
        .sortedWith(compareBy<InstalledApp> { it.label.lowercase() })
}

private fun clock(minute: Int) = "%02d:%02d".format((minute / 60).coerceIn(0, 24), minute % 60)

private fun parseClock(text: String): Int? {
    val parts = text.trim().split(':')
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    if (h !in 0..24 || m !in 0..59) return null
    return (h * 60 + m).coerceAtMost(24 * 60)
}
