package com.raunak.daytimeline.alarm

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    AlarmMissionType.MULTI -> "Mission chain"
}

@Composable
fun AlarmCenter(context: Context, onClose: () -> Unit) {
    val store = remember { AlarmPersistentStore(context) }
    val scheduler = remember { AlarmManagerBridge(context) }
    var alarms by remember { mutableStateOf(store.all()) }
    var editing by remember { mutableStateOf<AlarmEditorModel?>(null) }
    if (editing != null) { AlarmEditor(editing!!, { editing = null }, { model -> val saved = model.toPersistent(); store.save(saved); scheduler.schedule(saved); alarms = store.all(); editing = null }); return }
    Scaffold(topBar = { TopAppBar(title = { Text("Alarm center") }, navigationIcon = { TextButton(onClick = onClose) { Text("Close") } }) }, floatingActionButton = { FloatingActionButton(onClick = { editing = AlarmEditorModel() }) { Icon(Icons.Default.Add, "New alarm") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Offline alarms", style = MaterialTheme.typography.headlineMedium); Text("Missions, snooze limits and backup scheduling stay on this device.") }
            items(alarms, key = { it.id }) { alarm -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(String.format("%02d:%02d", alarm.hour, alarm.minute), style = MaterialTheme.typography.displaySmall); Text(alarm.label) }; Icon(Icons.Default.Alarm, null) }
                Text(if (alarm.repeatDays.isEmpty()) "One-time" else "Repeats ${alarm.repeatDays.sorted().joinToString(",")}")
                Text("Missions: ${alarm.missionChain.joinToString(" → ") { it.title() }}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { scheduler.schedule(alarm) }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Schedule") }; IconButton(onClick = { editing = alarm.toEditor() }) { Icon(Icons.Default.Edit, "Edit") }; IconButton(onClick = { scheduler.cancel(alarm.id); store.delete(alarm.id); alarms = store.all() }) { Icon(Icons.Default.Delete, "Delete") } }
            } } }
            item { Text("Next alarm: ${alarms.filter { it.enabled }.minByOrNull { AlarmSchedulePlanner.nextOccurrence(it, LocalDateTime.now()) }?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: "None"}") }
        }
    }
}

@Composable private fun AlarmEditor(model: AlarmEditorModel, onCancel: () -> Unit, onSave: (AlarmEditorModel) -> Unit) {
    val context = LocalContext.current; var current by remember(model.id) { mutableStateOf(model) }; var error by remember { mutableStateOf("") }; var picker by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Edit alarm") }, navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } }, actions = { TextButton(onClick = { val e=current.validate(); if(e.isEmpty()) onSave(current) else error=e.joinToString("\n") }) { Text("Save") } }) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { TimePickerDialog(context,{_,h,m->current=current.copy(hour=h,minute=m)},current.hour,current.minute,true).show() }) { Text(String.format("Alarm time  %02d:%02d",current.hour,current.minute)) }
            OutlinedTextField(current.label,{current=current.copy(label=it)},label={Text("Name")},modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ OutlinedButton(onClick={current=current.copy(repeatDays=if(current.repeatDays.isEmpty())setOf(2,3,4,5,6) else emptySet())}){Text(if(current.repeatDays.isEmpty())"One-time" else "Weekdays")}; FilterChip(current.vibration,{current=current.copy(vibration=!current.vibration)},{Text("Vibrate")}) }
            OutlinedTextField(current.snoozeMinutes.toString(),{current=current.copy(snoozeMinutes=it.toIntOrNull()?:current.snoozeMinutes)},label={Text("Snooze minutes")})
            OutlinedTextField(current.maxSnoozes.toString(),{current=current.copy(maxSnoozes=it.toIntOrNull()?:current.maxSnoozes)},label={Text("Maximum snoozes")})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(current.backupEnabled,{current=current.copy(backupEnabled=!current.backupEnabled)},{Text("Backup")});FilterChip(current.fullscreen,{current=current.copy(fullscreen=!current.fullscreen)},{Text("Fullscreen")})}
            OutlinedButton(onClick={picker=true}){Text("Add mission (${current.missions.size}/10)")}
            current.missions.forEachIndexed{index,m->AssistChip(onClick={current=current.copy(missions=AlarmMissionBuilder.remove(current.missions,index))},label={Text("${index+1}. ${m.title()} ×")})}
            if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
        }
    }
    if(picker)AlertDialog(onDismissRequest={picker=false},title={Text("Choose mission")},text={Column{listOf(AlarmMissionType.MATH,AlarmMissionType.TYPING,AlarmMissionType.MEMORY,AlarmMissionType.SHAKE,AlarmMissionType.WALK,AlarmMissionType.SQUAT,AlarmMissionType.PHOTO,AlarmMissionType.BARCODE).forEach{t->TextButton(onClick={current=current.copy(missions=AlarmMissionBuilder.add(current.missions,t));picker=false}){Text(t.name)}}}},confirmButton={TextButton(onClick={picker=false}){Text("Close")}})
}

private fun AlarmPersistentConfig.toEditor()=AlarmEditorModel(id,hour,minute,label,enabled,repeatDays,vibration,fullscreen,snoozeMinutes,maxSnoozes,backupAlarmEnabled,backupDelayMinutes,wakeCheckMinutes,bedtimeReminderMinutes,timeoutMinutes,gentleVolumeSeconds,longPressMs,missionChain)
