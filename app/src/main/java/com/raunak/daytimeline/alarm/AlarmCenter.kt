package com.raunak.daytimeline.alarm

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime

private fun AlarmMission.title() = when (type) {
    AlarmMissionType.MATH -> "Math"
    AlarmMissionType.TYPING -> "Typing"
    AlarmMissionType.MEMORY -> "Memory"
    AlarmMissionType.SHAKE -> "Shake"
    AlarmMissionType.SQUAT -> "Squats"
    AlarmMissionType.WALK -> "Walk"
    AlarmMissionType.PHOTO -> "Photo"
    AlarmMissionType.BARCODE -> "Barcode"
    AlarmMissionType.MULTI -> "Continue"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmCenter(context: Context, onClose: () -> Unit) {
    val store = remember { AlarmPersistentStore(context) }
    val scheduler = remember { AlarmManagerBridge(context) }
    var alarms by remember { mutableStateOf(store.all()) }
    var editing by remember { mutableStateOf<AlarmEditorModel?>(null) }
    if (editing != null) {
        AlarmEditor(editing!!, { editing = null }, { model ->
            val saved = model.toPersistent(); store.save(saved); scheduleWithExactAccess(context, scheduler, saved); alarms = store.all(); editing = null
        })
        return
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Alarm center") }, navigationIcon = { TextButton(onClick = onClose) { Text("Close") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { editing = AlarmEditorModel() }) { Icon(Icons.Default.Add, "New alarm") } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Offline alarms", style = MaterialTheme.typography.headlineMedium)
                Text("Exact scheduling, advanced repeats, mission verification, snooze policies and wake checks stay on this device.")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = AlarmPresets.heavySleeper() }) { Text("Heavy sleeper") }
                    OutlinedButton(onClick = { editing = AlarmPresets.examDay() }) { Text("Exam") }
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(String.format("%02d:%02d", alarm.hour, alarm.minute), style = MaterialTheme.typography.displaySmall)
                                Text(alarm.label)
                            }
                            Icon(Icons.Default.Alarm, null)
                        }
                        val next = AlarmSchedulePlanner.nextOccurrence(alarm, LocalDateTime.now())
                if (next != Long.MAX_VALUE) {
                    val dt = java.time.Instant.ofEpochMilli(next).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    Text("Next: " + dt.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM · HH:mm")))
                } else {
                    Text("No future occurrence")
                }
                Text(alarm.scheduleLabel())
                        Text("Missions: ${alarm.missionChain.joinToString(" → ") { it.title() }}")
                        Text("Snooze: ${alarm.snoozeMinutes}m × ${alarm.maxSnoozes}" + if (alarm.snoozeMaxTotalMinutes > 0) " · ${alarm.snoozeMaxTotalMinutes}m total" else "")
                        if (alarm.wakeCheckMinutes > 0) Text("Wake check: ${alarm.wakeCheckMinutes}m + ${alarm.wakeCheckRetries} retries")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { scheduleWithExactAccess(context, scheduler, alarm) }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Schedule") }
                            OutlinedButton(onClick = { context.startActivity(Intent(context, AlarmRingingActivity::class.java).apply { putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, alarm.id); putExtra(AlarmRingingActivity.EXTRA_TEST_MODE, true) }) }) { Icon(Icons.Default.BugReport, null); Spacer(Modifier.width(4.dp)); Text("Test") }
                            OutlinedButton(onClick = { scheduler.skipNext(alarm); alarms = store.all() }) { Text("Skip next") }
                            IconButton(onClick = { editing = AlarmEditorModel.fromPersistent(alarm) }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton(onClick = { scheduler.cancel(alarm.id); store.delete(alarm.id); AlarmReferenceStore(context).clear(alarm.id); alarms = store.all() }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
            item {
                val nextAlarm = alarms.filter { it.enabled }
                    .map { it to AlarmSchedulePlanner.nextOccurrence(it, LocalDateTime.now()) }
                    .filter { it.second != Long.MAX_VALUE }
                    .minByOrNull { it.second }
                Text(nextAlarm?.let { (_, at) ->
                    val dt = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    "Next alarm: " + String.format("%1\\$tb %1\\$td · %1\\$tH:%1\\$tM", dt)
                } ?: "Next alarm: None")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(model: AlarmEditorModel, onCancel: () -> Unit, onSave: (AlarmEditorModel) -> Unit) {
    val context = LocalContext.current
    val references = remember { AlarmReferenceStore(context) }
    var current by remember(model.id) { mutableStateOf(model) }
    var error by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf<Pair<Int, AlarmMissionType>?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val pending = registering
        registering = null
        if (bitmap == null || pending == null) return@rememberLauncherForActivityResult
        val (index, type) = pending
        if (type == AlarmMissionType.PHOTO) {
            references.savePhoto(current.id, bitmap)
            current = current.copy(missions = current.missions.toMutableList().also { it[index] = it[index].copy(payload = "registered_photo") })
        } else if (type == AlarmMissionType.BARCODE) {
            AlarmCameraVerifier.scan(bitmap) { value ->
                if (!value.isNullOrBlank()) {
                    references.saveBarcode(current.id, value)
                    current = current.copy(missions = current.missions.toMutableList().also { it[index] = it[index].copy(payload = value) })
                }
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Edit alarm") }, navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } }, actions = {
            TextButton(onClick = { val e = current.validate(); if (e.isEmpty()) onSave(current) else error = e.joinToString("\n") }) { Text("Save") }
        })
    }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> current = current.copy(hour = h, minute = m) }, current.hour, current.minute, true).show() }) { Text(String.format("Alarm time  %02d:%02d", current.hour, current.minute)) }
            OutlinedTextField(current.label, { current = current.copy(label = it) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(current.vibration, { current = current.copy(vibration = !current.vibration) }, { Text("Vibrate") })
                FilterChip(current.fullscreen, { current = current.copy(fullscreen = !current.fullscreen) }, { Text("Fullscreen") })
                FilterChip(current.deleteAfterRinging, { current = current.copy(deleteAfterRinging = !current.deleteAfterRinging) }, { Text("Delete after") })
            }
            Text("Repeat mode", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AlarmScheduleMode.entries.filter { it != AlarmScheduleMode.NAP && it != AlarmScheduleMode.POWER_NAP }.forEach { mode ->
                    FilterChip(current.scheduleMode == mode, { current = current.copy(scheduleMode = mode) }, { Text(mode.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            if (current.scheduleMode == AlarmScheduleMode.WEEKLY || current.scheduleMode == AlarmScheduleMode.ODD_WEEKS || current.scheduleMode == AlarmScheduleMode.EVEN_WEEKS) {
                Text("Days", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(2 to "M", 3 to "T", 4 to "W", 5 to "T", 6 to "F", 7 to "S", 1 to "S").forEach { (day, label) ->
                        FilterChip(
                            selected = day in current.repeatDays,
                            onClick = {
                                val next = current.repeatDays.toMutableSet().apply {
                                    if (!add(day)) remove(day)
                                }
                                current = current.copy(repeatDays = next)
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
            if (current.scheduleMode == AlarmScheduleMode.EVERY_N_DAYS) {
                OutlinedTextField(current.intervalDays.toString(), { current = current.copy(intervalDays = it.toIntOrNull() ?: current.intervalDays) }, label = { Text("Every N days") })
            }
            if (current.scheduleMode == AlarmScheduleMode.ONE_SHOT) {
                OutlinedTextField(
                    current.anchorDate.orEmpty(),
                    { current = current.copy(anchorDate = it.takeIf(String::isNotBlank)) },
                    label = { Text("Date (YYYY-MM-DD)") },
                    supportingText = { Text("One-shot alarms fire only on this date.") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(current.snoozeMinutes.toString(), { current = current.copy(snoozeMinutes = it.toIntOrNull() ?: current.snoozeMinutes) }, label = { Text("Snooze minutes") }, modifier = Modifier.weight(1f))
                OutlinedTextField(current.maxSnoozes.toString(), { current = current.copy(maxSnoozes = it.toIntOrNull() ?: current.maxSnoozes) }, label = { Text("Max snoozes") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(current.snoozeMaxTotalMinutes.toString(), { current = current.copy(snoozeMaxTotalMinutes = it.toIntOrNull() ?: current.snoozeMaxTotalMinutes) }, label = { Text("Max total snooze") }, modifier = Modifier.weight(1f))
                FilterChip(current.snoozeHalveEachTime, { current = current.copy(snoozeHalveEachTime = !current.snoozeHalveEachTime) }, { Text("Halve snooze") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(current.backupEnabled, { current = current.copy(backupEnabled = !current.backupEnabled) }, { Text("Backup") })
                FilterChip(current.wakeCheckMinutes > 0, { current = current.copy(wakeCheckMinutes = if (current.wakeCheckMinutes > 0) 0 else 10) }, { Text("Wake check") })
            }
            if (current.wakeCheckMinutes > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(current.wakeCheckMinutes.toString(), { current = current.copy(wakeCheckMinutes = it.toIntOrNull() ?: current.wakeCheckMinutes) }, label = { Text("First check min") }, modifier = Modifier.weight(1f))
                OutlinedTextField(current.wakeCheckRetries.toString(), { current = current.copy(wakeCheckRetries = it.toIntOrNull() ?: current.wakeCheckRetries) }, label = { Text("Retries") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(current.gentleVolumeSeconds.toString(), { current = current.copy(gentleVolumeSeconds = it.toIntOrNull() ?: current.gentleVolumeSeconds) }, label = { Text("Gentle volume ramp (sec)") })
            OutlinedTextField(current.timeoutMinutes.toString(), { current = current.copy(timeoutMinutes = it.toIntOrNull() ?: current.timeoutMinutes) }, label = { Text("Timeout (min)") })
            Text("Mission chain (${current.missions.size}/10)", style = MaterialTheme.typography.titleMedium)
            current.missions.forEachIndexed { index, mission ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { current = current.copy(missions = AlarmMissionBuilder.remove(current.missions, index)) }, label = { Text("${index + 1}. ${mission.title()} ×") })
                    if (mission.type == AlarmMissionType.PHOTO || mission.type == AlarmMissionType.BARCODE) Button(onClick = { registering = index to mission.type; camera.launch(null) }) { Text(if (mission.type == AlarmMissionType.PHOTO) "Register photo" else "Register code") }
                }
            }
            Button(onClick = { picker = true }, enabled = current.missions.size < 10) { Text("Add mission") }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
    if (picker) AlertDialog(onDismissRequest = { picker = false }, title = { Text("Choose mission") }, text = {
        Column {
            AlarmMissionType.entries.filter { it != AlarmMissionType.MULTI }.forEach { type ->
                TextButton(onClick = { current = current.copy(missions = AlarmMissionBuilder.add(current.missions, type)); picker = false }) { Text(type.name) }
            }
        }
    }, confirmButton = { TextButton(onClick = { picker = false }) { Text("Close") } })
}

private fun scheduleWithExactAccess(context: Context, scheduler: AlarmManagerBridge, alarm: AlarmPersistentConfig) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(android.app.AlarmManager::class.java)
        if (manager != null && !manager.canScheduleExactAlarms()) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:" + context.packageName)
                })
            }
        }
    }
    scheduler.schedule(alarm)
}
