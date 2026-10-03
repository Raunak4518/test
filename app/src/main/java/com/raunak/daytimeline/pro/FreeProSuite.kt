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
    val confirm = com.raunak.daytimeline.ui.rememberConfirm()
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
                IconButton(onClick = { confirm.ask("this reminder") { manager.delete(r.id); reminders = store.all() } }) { Icon(Icons.Default.Delete, "Delete") }
            } }
        }
    }
}



private fun clock(minute: Int) = "%02d:%02d".format((minute / 60).coerceIn(0, 24), minute % 60)

