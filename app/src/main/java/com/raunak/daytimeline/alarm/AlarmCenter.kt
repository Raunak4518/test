package com.raunak.daytimeline.alarm

import com.raunak.daytimeline.ui.*

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Alarm repeat days use Calendar numbering (1 = Sunday … 7 = Saturday); shown Monday first. */
private val weekOrder = listOf(2 to "M", 3 to "T", 4 to "W", 5 to "T", 6 to "F", 7 to "S", 1 to "S")

private fun inText(ms: Long): String {
    val m = ((ms - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
    return when { m < 1 -> "less than a minute"; m < 60 -> "${m}m"; m < 1440 -> "${m / 60}h ${m % 60}m"; else -> "${m / 1440}d ${(m % 1440) / 60}h" }
}

private fun repeatText(a: AlarmPersistentConfig): String = when (a.advancedRepeat().mode) {
    AlarmScheduleMode.WEEKLY -> when (a.repeatDays) {
        emptySet<Int>() -> "Once"
        setOf(2, 3, 4, 5, 6) -> "Weekdays"
        setOf(1, 7) -> "Weekends"
        (1..7).toSet() -> "Every day"
        else -> weekOrder.filter { it.first in a.repeatDays }.joinToString(" ") { (d, _) -> listOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[d] }
    }
    else -> a.scheduleLabel()
}

@Composable
fun AlarmCenter(context: Context, onClose: () -> Unit) {
    val store = remember { AlarmPersistentStore(context) }
    val scheduler = remember { AlarmManagerBridge(context) }
    var alarms by remember { mutableStateOf(store.all()) }
    var editing by remember { mutableStateOf<AlarmEditorModel?>(null) }
    var settings by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    fun refresh() { alarms = store.all() }
    fun save(a: AlarmPersistentConfig) { store.save(a); if (a.enabled) scheduleWithExactAccess(context, scheduler, a) else scheduler.cancel(a.id); refresh() }

    editing?.let { model ->
        AlarmEditor(model, onCancel = { editing = null }, onDelete = if (alarms.any { it.id == model.id }) ({ scheduler.cancel(model.id); store.delete(model.id); AlarmReferenceStore(context).clear(model.id); refresh(); editing = null }) else null) { saved ->
            save(saved.toPersistent()); editing = null
        }
        return
    }
    if (settings) { AlarmSettingsPage(context) { settings = false }; return }

    val history = remember(tick, alarms) { AlarmHistoryStore(context).all() }
    val next = remember(alarms, tick) {
        alarms.filter { it.enabled }.map { it to AlarmSchedulePlanner.nextOccurrence(it, LocalDateTime.now()) }.filter { it.second != Long.MAX_VALUE }.minByOrNull { it.second }
    }
    Scaffold(
        topBar = { ChronoraTopBar("Alarms", onClose) { IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "Alarm settings") } } },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { editing = AlarmEditorModel(id = System.currentTimeMillis()) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Alarm") }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HeroCard {
                    if (next == null) {
                        Text("No alarms on", color = Chronora.colors.onHero, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    } else {
                        val at = Instant.ofEpochMilli(next.second).atZone(ZoneId.systemDefault()).toLocalDateTime()
                        Text("Rings in ${inText(next.second)}", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelLarge)
                        Text("%02d:%02d".format(at.hour, at.minute), color = Chronora.colors.onHero, fontSize = 48.sp, fontWeight = FontWeight.Light)
                        Text(next.first.label + " · " + com.raunak.daytimeline.productivity.relativeDay(at.toLocalDate()), color = Chronora.colors.heroMuted)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Nap", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelLarge)
                        listOf(10, 20, 30, 45, 60, 90).forEach { m ->
                            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .12f)).clickable { save(napAlarm(m)) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(if (m < 60) "${m}m" else "${m / 60}h${if (m % 60 > 0) " ${m % 60}m" else ""}", color = Chronora.colors.onHero)
                            }
                        }
                    }
                }
            }
            if (alarms.isEmpty()) item {
                EmptyState("No alarms yet", "Tap + Alarm, or start from a preset below.")
            }
            items(alarms.sortedWith(compareBy({ it.hour }, { it.minute })), key = { it.id }) { a ->
                AlarmCard(a, onToggle = { save(a.copy(enabled = it)) }, onEdit = { editing = AlarmEditorModel.fromPersistent(a) },
                    onPreview = { context.startActivity(Intent(context, AlarmRingingActivity::class.java).putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, a.id).putExtra(AlarmRingingActivity.EXTRA_TEST_MODE, true)) },
                    onSkip = { scheduler.skipNext(a); refresh() },
                    onDuplicate = { save(a.copy(id = System.currentTimeMillis(), label = a.label + " copy")) })
            }
            item {
                SectionHeader("Start from a preset")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("😴 Heavy sleeper" to AlarmPresets.heavySleeper(), "📝 Exam day" to AlarmPresets.examDay(), "🌅 Gentle" to AlarmPresets.gentle(), "💼 Weekdays" to AlarmPresets.workday()).forEach { (l, m) ->
                        AssistChip(onClick = { editing = m.copy(id = System.currentTimeMillis()) }, label = { Text(l) })
                    }
                }
            }
            if (history.isNotEmpty()) item { WakeRecordCard(history) }
        }
    }
}

private fun napAlarm(minutes: Int): AlarmPersistentConfig {
    val at = LocalDateTime.now().plusMinutes(minutes.toLong())
    return AlarmPersistentConfig(System.currentTimeMillis(), at.hour, at.minute, label = "Nap · ${minutes}m", repeatDays = emptySet(), scheduleMode = AlarmScheduleMode.ONE_SHOT.name,
        anchorDate = at.toLocalDate().toString(), missionChain = emptyList(), maxSnoozes = 1, deleteAfterRinging = true, briefing = false, gentleVolumeSeconds = 30).validated()
}

@Composable
private fun AlarmCard(a: AlarmPersistentConfig, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onPreview: () -> Unit, onSkip: () -> Unit, onDuplicate: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val next = AlarmSchedulePlanner.nextOccurrence(a, LocalDateTime.now())
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(18.dp).alpha(if (a.enabled) 1f else .5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("%02d:%02d".format(a.hour, a.minute), fontSize = 44.sp, fontWeight = FontWeight.Light)
                    Text(a.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(a.enabled, onToggle)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Preview") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) }, onClick = { menu = false; onPreview() })
                        if (a.isRepeating() && a.enabled) DropdownMenuItem(text = { Text("Skip next") }, leadingIcon = { Icon(Icons.Default.SkipNext, null) }, onClick = { menu = false; onSkip() })
                        DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (a.advancedRepeat().mode == AlarmScheduleMode.WEEKLY && a.repeatDays.isNotEmpty()) weekOrder.forEach { (d, l) ->
                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (d in a.repeatDays) MaterialTheme.colorScheme.primary else Color.Transparent), contentAlignment = Alignment.Center) {
                        Text(l, fontSize = 11.sp, color = if (d in a.repeatDays) MaterialTheme.colorScheme.onPrimary else Chronora.muted, fontWeight = FontWeight.SemiBold)
                    }
                } else Text(repeatText(a), style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                Spacer(Modifier.weight(1f))
                Text(a.missionChain.joinToString(" ") { it.type.icon }.ifBlank { "No mission" }, style = MaterialTheme.typography.bodyMedium, color = Chronora.muted)
            }
            if (a.enabled && next != Long.MAX_VALUE) Text("in ${inText(next)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WakeRecordCard(history: List<WakeRecord>) {
    val last = history.takeLast(14)
    val s = AlarmHistoryStore.stats(last)
    SectionCard("Wake-up record", "Last ${last.size} mornings") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("On time", "${s.onTimeRate}%", Modifier.weight(1f), color = if (s.onTimeRate >= 70) Chronora.colors.good else Chronora.colors.warn)
            Stat("Streak", "${s.onTimeStreak}", Modifier.weight(1f))
            Stat("Avg late", "${s.averageLate}m", Modifier.weight(1f))
            Stat("Snoozes", "%.1f".format(s.averageSnoozes), Modifier.weight(1f))
        }
        val maxLate = last.maxOf { it.lateMinutes }.coerceAtLeast(10)
        Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            last.forEach { r ->
                Box(Modifier.weight(1f).height((60f * r.lateMinutes / maxLate).coerceAtLeast(4f).dp).clip(RoundedCornerShape(4.dp))
                    .background(if (r.lateMinutes <= 10) Chronora.colors.good else if (r.lateMinutes <= 30) Chronora.colors.warn else Chronora.colors.bad))
            }
        }
        Text("Bar = minutes from alarm to fully up", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(model: AlarmEditorModel, onCancel: () -> Unit, onDelete: (() -> Unit)?, onSave: (AlarmEditorModel) -> Unit) {
    val context = LocalContext.current
    val references = remember { AlarmReferenceStore(context) }
    var cur by remember(model.id) { mutableStateOf(model) }
    var missionEdit by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf<Pair<Int, AlarmMissionType>?>(null) }
    val time = rememberTimePickerState(cur.hour, cur.minute, is24Hour = true)
    LaunchedEffect(time.hour, time.minute) { cur = cur.copy(hour = time.hour, minute = time.minute) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val pending = registering; registering = null
        if (bitmap == null || pending == null) return@rememberLauncherForActivityResult
        val (index, type) = pending
        if (type == AlarmMissionType.PHOTO) {
            references.savePhoto(cur.id, bitmap)
            cur = cur.copy(missions = cur.missions.toMutableList().also { it[index] = it[index].copy(payload = "registered_photo") })
        } else AlarmCameraVerifier.scan(bitmap) { v -> if (!v.isNullOrBlank()) { references.saveBarcode(cur.id, v); cur = cur.copy(missions = cur.missions.toMutableList().also { it[index] = it[index].copy(payload = v) }) } }
    }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) else @Suppress("DEPRECATION") r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        cur = cur.copy(soundUri = uri?.toString(), soundName = uri?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() })
    }
    val nextAt = remember(cur) { runCatching { AlarmSchedulePlanner.nextOccurrence(cur.copy(label = cur.label.ifBlank { "Alarm" }).toPersistent(), LocalDateTime.now()) }.getOrDefault(Long.MAX_VALUE) }
    fun ok() = cur.copy(label = cur.label.ifBlank { "Alarm" }, snoozeMinutes = cur.snoozeMinutes.coerceIn(1, 60)).let { if (it.validate().isEmpty()) it else null }

    Scaffold(topBar = {
        ChronoraTopBar(if (onDelete == null) "New alarm" else "Edit alarm", onCancel, subtitle = if (nextAt != Long.MAX_VALUE) "Rings in ${inText(nextAt)}" else null) {
            TextButton(onClick = { ok()?.let(onSave) }, enabled = ok() != null) { Text("Save", fontWeight = FontWeight.Bold) }
        }
    }) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimeInput(time) }
            OutlinedTextField(cur.label, { cur = cur.copy(label = it) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Label") }, leadingIcon = { Icon(Icons.Default.Label, null) })

            SectionCard("Repeat") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    weekOrder.forEach { (d, l) ->
                        val on = d in cur.repeatDays && cur.scheduleMode != AlarmScheduleMode.ONE_SHOT && cur.scheduleMode != AlarmScheduleMode.EVERY_N_DAYS
                        Box(Modifier.size(40.dp).clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable {
                            cur = cur.copy(scheduleMode = if (cur.scheduleMode in setOf(AlarmScheduleMode.ONE_SHOT, AlarmScheduleMode.EVERY_N_DAYS)) AlarmScheduleMode.WEEKLY else cur.scheduleMode,
                                repeatDays = if (d in cur.repeatDays) cur.repeatDays - d else cur.repeatDays + d)
                        }, contentAlignment = Alignment.Center) { Text(l, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Once" to emptySet(), "Weekdays" to setOf(2, 3, 4, 5, 6), "Weekends" to setOf(1, 7), "Every day" to (1..7).toSet()).forEach { (l, set) ->
                        FilterChip(cur.scheduleMode == AlarmScheduleMode.WEEKLY && cur.repeatDays == set, { cur = cur.copy(scheduleMode = AlarmScheduleMode.WEEKLY, repeatDays = set) }, label = { Text(l) })
                    }
                    FilterChip(cur.scheduleMode == AlarmScheduleMode.ODD_WEEKS, { cur = cur.copy(scheduleMode = AlarmScheduleMode.ODD_WEEKS) }, label = { Text("Odd weeks") })
                    FilterChip(cur.scheduleMode == AlarmScheduleMode.EVEN_WEEKS, { cur = cur.copy(scheduleMode = AlarmScheduleMode.EVEN_WEEKS) }, label = { Text("Even weeks") })
                    FilterChip(cur.scheduleMode == AlarmScheduleMode.EVERY_N_DAYS, { cur = cur.copy(scheduleMode = AlarmScheduleMode.EVERY_N_DAYS) }, label = { Text("Every N days") })
                }
                if (cur.scheduleMode == AlarmScheduleMode.EVERY_N_DAYS) Stepper("Every", "${cur.intervalDays} days", { cur = cur.copy(intervalDays = (cur.intervalDays - 1).coerceAtLeast(2)) }, { cur = cur.copy(intervalDays = (cur.intervalDays + 1).coerceAtMost(16)) })
            }

            SectionCard("Missions", if (cur.missions.isEmpty()) "None — the alarm stops with one tap" else "Finish these to turn the alarm off") {
                cur.missions.forEachIndexed { i, m ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)).clickable { missionEdit = i }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.type.icon, fontSize = 24.sp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(m.type.title, fontWeight = FontWeight.SemiBold)
                            Text(AlarmChallenges.summary(m), style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                            if (m.type == AlarmMissionType.PHOTO && !references.hasPhoto(cur.id)) Text("Tap to register your photo", style = MaterialTheme.typography.labelSmall, color = Chronora.colors.warn)
                            if (m.type == AlarmMissionType.BARCODE && references.barcode(cur.id) == null) Text("Tap to register your code", style = MaterialTheme.typography.labelSmall, color = Chronora.colors.warn)
                        }
                        IconButton(onClick = { cur = cur.copy(missions = AlarmMissionBuilder.remove(cur.missions, i)) }) { Icon(Icons.Default.Close, "Remove mission") }
                    }
                }
                if (cur.missions.size < 5) OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add mission") }
            }

            SectionCard("Sound") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                    ringtonePicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, cur.soundUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)))
                }.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.MusicNote, null)
                    Text(cur.soundName ?: "Default alarm", Modifier.weight(1f).padding(start = 12.dp))
                    Icon(Icons.Default.ChevronRight, null)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, null)
                    Slider(cur.volume.toFloat(), { cur = cur.copy(volume = it.toInt()) }, valueRange = 10f..100f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text("${cur.volume}%", style = MaterialTheme.typography.labelLarge)
                }
                Text("Gradually louder", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Off", 30 to "30s", 60 to "1 min", 120 to "2 min", 300 to "5 min").forEach { (s, l) -> FilterChip(cur.gentleVolumeSeconds == s, { cur = cur.copy(gentleVolumeSeconds = s) }, label = { Text(l) }) }
                }
                Text("Vibration", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("OFF" to "Off", "PULSE" to "Pulse", "HEARTBEAT" to "Heartbeat", "STRONG" to "Strong").forEach { (k, l) ->
                        FilterChip((if (!cur.vibration) "OFF" else cur.vibrationPattern) == k, { cur = cur.copy(vibration = k != "OFF", vibrationPattern = k) }, label = { Text(l) })
                    }
                }
            }

            SectionCard("Snooze") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(cur.maxSnoozes == 0, { cur = cur.copy(maxSnoozes = 0) }, label = { Text("Off") })
                    listOf(1, 3, 5, 10, 15).forEach { m -> FilterChip(cur.maxSnoozes > 0 && cur.snoozeMinutes == m, { cur = cur.copy(snoozeMinutes = m, maxSnoozes = cur.maxSnoozes.coerceAtLeast(1)) }, label = { Text("$m min") }) }
                }
                if (cur.maxSnoozes > 0) Stepper("Times allowed", "${cur.maxSnoozes}", { cur = cur.copy(maxSnoozes = (cur.maxSnoozes - 1).coerceAtLeast(1)) }, { cur = cur.copy(maxSnoozes = (cur.maxSnoozes + 1).coerceAtMost(10)) })
            }

            SectionCard("Make sure I'm up") {
                Text("Wake-up check", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Off", 3 to "3 min", 5 to "5 min", 10 to "10 min", 15 to "15 min").forEach { (m, l) -> FilterChip(cur.wakeCheckMinutes == m, { cur = cur.copy(wakeCheckMinutes = m) }, label = { Text(l) }) }
                }
                if (cur.wakeCheckMinutes > 0) Text("You'll be asked to confirm you're awake; no answer rings again.", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                SwitchRow("Backup alarm if the first is ignored", cur.backupEnabled) { cur = cur.copy(backupEnabled = it) }
                SwitchRow("Good-morning briefing after", cur.briefing) { cur = cur.copy(briefing = it) }
            }

            TextButton(onClick = { more = !more }) { Text(if (more) "Fewer options" else "More options") }
            if (more) SectionCard("More") {
                Stepper("Bedtime reminder", if (cur.bedtimeReminderMinutes == 0) "Off" else "${cur.bedtimeReminderMinutes / 60}h ${cur.bedtimeReminderMinutes % 60}m before",
                    { cur = cur.copy(bedtimeReminderMinutes = (cur.bedtimeReminderMinutes - 30).coerceAtLeast(0)) }, { cur = cur.copy(bedtimeReminderMinutes = (cur.bedtimeReminderMinutes + 30).coerceAtMost(720)) })
                if (cur.backupEnabled) Stepper("Backup after", "${cur.backupDelayMinutes} min", { cur = cur.copy(backupDelayMinutes = (cur.backupDelayMinutes - 1).coerceAtLeast(1)) }, { cur = cur.copy(backupDelayMinutes = (cur.backupDelayMinutes + 1).coerceAtMost(60)) })
                if (cur.maxSnoozes > 0) SwitchRow("Each snooze is half as long", cur.snoozeHalveEachTime) { cur = cur.copy(snoozeHalveEachTime = it) }
                if (cur.repeatDays.isEmpty() || cur.scheduleMode == AlarmScheduleMode.ONE_SHOT) SwitchRow("Delete after it rings", cur.deleteAfterRinging) { cur = cur.copy(deleteAfterRinging = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    ok()?.let { m -> AlarmPersistentStore(context).save(m.toPersistent()); context.startActivity(Intent(context, AlarmRingingActivity::class.java).putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, m.id).putExtra(AlarmRingingActivity.EXTRA_TEST_MODE, true)) }
                }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Preview") }
                if (onDelete != null) OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Delete, null, tint = Chronora.colors.bad); Spacer(Modifier.width(4.dp)); Text("Delete", color = Chronora.colors.bad) }
            }
        }
    }

    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("Add a mission") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AlarmMissionType.entries.filter { it != AlarmMissionType.MULTI }.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { t ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                            cur = cur.copy(missions = AlarmMissionBuilder.add(cur.missions, t)); adding = false; missionEdit = cur.missions.lastIndex
                        }.padding(12.dp)) {
                            Text(t.icon, fontSize = 26.sp)
                            Text(t.title, fontWeight = FontWeight.SemiBold)
                            Text(t.blurb, style = MaterialTheme.typography.labelSmall, color = Chronora.muted, maxLines = 2)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { adding = false }) { Text("Close") } })

    missionEdit?.let { i -> cur.missions.getOrNull(i)?.let { m ->
        MissionEditor(m, onChange = { nm -> cur = cur.copy(missions = cur.missions.toMutableList().also { it[i] = nm }) },
            onRegister = { registering = i to m.type; camera.launch(null) }, registered = if (m.type == AlarmMissionType.PHOTO) references.hasPhoto(cur.id) else references.barcode(cur.id) != null) { missionEdit = null }
    } }
}

@Composable
private fun MissionEditor(m: AlarmMission, onChange: (AlarmMission) -> Unit, onRegister: () -> Unit, registered: Boolean, close: () -> Unit) {
    val hasDifficulty = m.type in setOf(AlarmMissionType.MATH, AlarmMissionType.MEMORY, AlarmMissionType.TYPING, AlarmMissionType.TAP)
    val counts: List<Int>? = when (m.type) {
        AlarmMissionType.MATH -> listOf(1, 2, 3, 5, 8)
        AlarmMissionType.TYPING -> listOf(1, 2, 3, 5)
        AlarmMissionType.MEMORY -> listOf(1, 2, 3, 4)
        AlarmMissionType.SHAKE -> listOf(20, 40, 60, 100)
        AlarmMissionType.SQUAT -> listOf(5, 10, 15, 25)
        AlarmMissionType.WALK -> listOf(20, 50, 100, 200)
        AlarmMissionType.TAP -> listOf(20, 40, 60, 100)
        else -> null
    }
    AlertDialog(onDismissRequest = close, title = { Text("${m.type.icon}  ${m.type.title}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasDifficulty) {
                Text(AlarmChallenges.difficultyName(m.difficulty), fontWeight = FontWeight.SemiBold)
                Slider(m.difficulty.toFloat(), { onChange(m.copy(difficulty = it.toInt().coerceIn(1, 5))) }, valueRange = 1f..5f, steps = 3)
                if (m.type == AlarmMissionType.MATH) Text("e.g. " + AlarmChallenges.math(m.difficulty, kotlin.random.Random(m.difficulty)).text, color = Chronora.muted)
                if (m.type == AlarmMissionType.MEMORY) AlarmChallenges.memorySize(m.difficulty).let { (s, n) -> Text("$n tiles on a $s×$s grid", color = Chronora.muted) }
            }
            counts?.let { list ->
                Text(when (m.type) { AlarmMissionType.MATH -> "Problems"; AlarmMissionType.TYPING -> "Phrases"; AlarmMissionType.MEMORY -> "Rounds"; else -> "How many" }, style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { n -> FilterChip(m.target == n, { onChange(m.copy(target = n)) }, label = { Text("$n") }) }
                }
            }
            if (m.type == AlarmMissionType.PHOTO || m.type == AlarmMissionType.BARCODE) {
                Text(if (m.type == AlarmMissionType.PHOTO) "Take a photo of a spot away from your bed (bathroom sink, kitchen). In the morning you'll need to photograph the same spot." else "Scan a barcode or QR code away from your bed (toothpaste, a book). In the morning you'll need to scan it again.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text(if (registered) "Register again" else if (m.type == AlarmMissionType.PHOTO) "Take the photo" else "Scan the code") }
                if (registered) Text("✓ Registered", color = Chronora.colors.good)
            }
            if (m.type == AlarmMissionType.WALK) Text("Needs the physical-activity permission.", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
        }
    }, confirmButton = { Button(onClick = close) { Text("Done") } })
}

@Composable
private fun AlarmSettingsPage(context: Context, close: () -> Unit) {
    val prefs = remember { AlarmPrefs(context) }
    var phrases by remember { mutableStateOf(prefs.phrases) }
    var idle by remember { mutableIntStateOf(prefs.idleSeconds) }
    var keepOnTop by remember { mutableStateOf(prefs.keepOnTop) }
    Scaffold(topBar = { ChronoraTopBar("Alarm settings", close) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard("During missions") {
                    SwitchRow("Bring the alarm back if I leave it", keepOnTop) { keepOnTop = it; prefs.keepOnTop = it }
                    Stepper("Full volume again after idle", "${idle}s", { idle = (idle - 5).coerceAtLeast(5); prefs.idleSeconds = idle }, { idle = (idle + 5).coerceAtMost(120); prefs.idleSeconds = idle })
                }
            }
            item {
                SectionCard("Typing phrases") { ListEditor("Phrases", phrases) { v -> phrases = v; prefs.phrases = v } }
            }
            item {
                SectionCard("Reliability") {
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 31) runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Allow exact alarms") }
                    OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, modifier = Modifier.fillMaxWidth()) { Text("Battery optimisation") }
                    if (Build.VERSION.SDK_INT >= 34) OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, modifier = Modifier.fillMaxWidth()) { Text("Allow full-screen alarms") }
                }
            }
        }
    }
}

private fun scheduleWithExactAccess(context: Context, scheduler: AlarmManagerBridge, alarm: AlarmPersistentConfig) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(android.app.AlarmManager::class.java)
        if (manager != null && !manager.canScheduleExactAlarms()) {
            runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = Uri.parse("package:" + context.packageName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
        }
    }
    scheduler.schedule(alarm)
}
