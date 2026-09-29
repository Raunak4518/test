package com.raunak.daytimeline

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.features.OfflineProductivityStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinePowerTools(store: OfflineProductivityStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val projects by store.projects.collectAsStateWithLifecycle()
    val entries by store.timeEntries.collectAsStateWithLifecycle()
    val challenges by store.challenges.collectAsStateWithLifecycle()
    val achievements by store.achievements.collectAsStateWithLifecycle()
    val settings by store.settings.collectAsStateWithLifecycle()
    var newProject by remember { mutableStateOf("") }
    var timerLabel by remember { mutableStateOf("") }
    var selectedProject by remember { mutableLongStateOf(-1L) }
    var status by remember { mutableStateOf("") }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Unable to read backup")
        }.onSuccess { json -> store.importJson(json).onSuccess { status = "Backup imported successfully" }.onFailure { status = "Import failed" } }
            .onFailure { status = "Import failed: cannot read file" }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Power tools") }, navigationIcon = { TextButton(onClick = onClose) { Text("Close") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Offline command center", style = MaterialTheme.typography.headlineSmall); Text("Projects, time tracking, challenges, achievements and portable backups. Everything remains local.") }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Time tracker", style = MaterialTheme.typography.titleMedium)
                val active = entries.firstOrNull { it.endEpochMillis == null }
                if (active != null) { Text(active.label, style = MaterialTheme.typography.titleLarge); Text("Started " + formatEpoch(active.startEpochMillis)); Button(onClick = { store.stopTimeEntry(active.id); status = "Timer stopped" }) { Text("Stop") } }
                else { OutlinedTextField(timerLabel, { timerLabel = it }, label = { Text("What are you working on?") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { store.startTimeEntry(timerLabel, selectedProject.takeIf { it > 0L }); timerLabel = ""; status = "Timer started" }, enabled = timerLabel.isNotBlank()) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Start timer") } }
                Text("Today: " + store.todayTrackedMinutes() + " minutes tracked", style = MaterialTheme.typography.labelLarge)
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Projects", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(newProject, { newProject = it }, label = { Text("Project name") }, modifier = Modifier.weight(1f)); Button(onClick = { store.addProject(newProject); newProject = "" }, enabled = newProject.isNotBlank()) { Text("Add") } }
                projects.forEach { project -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { FilterChip(selectedProject == project.id, { selectedProject = if (selectedProject == project.id) -1L else project.id }, label = { Text(project.name) }); IconButton(onClick = { store.deleteProject(project.id) }) { Icon(Icons.Default.Delete, "Delete project") } } }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Challenges", style = MaterialTheme.typography.titleMedium)
                challenges.forEach { challenge -> val ratio = if (challenge.target == 0) 0f else challenge.progress.toFloat() / challenge.target; Text(challenge.title); Text(challenge.description, style = MaterialTheme.typography.bodySmall); LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()); Text(challenge.progress.toString() + " / " + challenge.target); if (challenge.progress < challenge.target) TextButton(onClick = { store.completeChallenge(challenge.id) }) { Text("Record progress") } }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Achievements", style = MaterialTheme.typography.titleMedium)
                if (achievements.isEmpty()) Text("No achievements unlocked yet.") else achievements.sortedByDescending { it.unlockedAt }.forEach { ListItem(headlineContent = { Text(it.title) }, supportingContent = { Text(it.description) }, leadingContent = { Icon(Icons.Default.EmojiEvents, null) }) }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup & restore", style = MaterialTheme.typography.titleMedium); Text("Export the complete secondary productivity store as versioned JSON, or restore a validated backup.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { val send = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, store.exportJson()) }; context.startActivity(Intent.createChooser(send, "Export productivity backup")) }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Export") }; OutlinedButton(onClick = { importer.launch("application/json") }) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Import") } }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Local behavior", style = MaterialTheme.typography.titleMedium); SettingSwitch("Haptics", settings.haptics) { store.updateSettings { copy(haptics = it) } }; SettingSwitch("Sounds", settings.sounds) { store.updateSettings { copy(sounds = it) } }; SettingSwitch("Auto-scroll to now", settings.autoScrollNow) { store.updateSettings { copy(autoScrollNow = it) } }; SettingSwitch("Show completed", settings.showCompleted) { store.updateSettings { copy(showCompleted = it) } }; Text("Default task: " + settings.defaultTaskMinutes + "m · Focus: " + settings.defaultFocusMinutes + "m", style = MaterialTheme.typography.bodySmall)
            } } }
            if (status.isNotBlank()) item { Text(status, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked = checked, onCheckedChange = onChange) } }
private fun formatEpoch(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM · HH:mm"))
