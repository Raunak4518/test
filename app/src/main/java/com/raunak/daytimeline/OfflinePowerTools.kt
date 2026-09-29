package com.raunak.daytimeline

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import com.raunak.daytimeline.protection.DayTimelineDeviceAdminReceiver
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
    val devicePolicyManager = remember(context) { context.getSystemService(DevicePolicyManager::class.java) }
    val adminComponent = remember(context) { ComponentName(context, DayTimelineDeviceAdminReceiver::class.java) }
    val protectionEnabled = devicePolicyManager?.isAdminActive(adminComponent) == true
    var newProject by remember { mutableStateOf("") }
    var timerLabel by remember { mutableStateOf("") }
    var selectedProject by remember { mutableLongStateOf(-1L) }
    var status by remember { mutableStateOf("") }
    var challengeEditor by remember { mutableStateOf<com.raunak.daytimeline.features.OfflineChallenge?>(null) }
    var newChallenge by remember { mutableStateOf(false) }
    var projectEditor by remember { mutableStateOf<com.raunak.daytimeline.features.OfflineProject?>(null) }
    var newProjectDialog by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.exportJson()) } ?: error("Unable to write backup") }.onSuccess { status = "Backup saved" }.onFailure { status = "Export failed" } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Unable to read backup")
        }.onSuccess { json -> store.importJson(json).onSuccess { status = "Backup imported successfully" }.onFailure { status = "Import failed" } }
            .onFailure { status = "Import failed: cannot read file" }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Chronora · Power tools") }, navigationIcon = { TextButton(onClick = onClose) { Text("Close") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Offline command center", style = MaterialTheme.typography.headlineSmall); Text("Projects, time tracking, challenges, achievements and portable backups. Everything remains local.") }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tamper protection", style = MaterialTheme.typography.titleMedium)
                Text(if (protectionEnabled) "Android device-admin protection is active. It must be explicitly disabled before normal uninstall can proceed." else "Optional Android-managed protection. This does not bypass Android security or make the app permanently undeletable.")
                Button(onClick = {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable explicit Chronora protection. Android may require this administrator to be disabled before the app can be uninstalled.")
                    }
                    context.startActivity(intent)
                }, enabled = !protectionEnabled) { Text(if (protectionEnabled) "Protection active" else "Enable protection") }
                if (protectionEnabled) {
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) { Text("Manage protection in Android settings") }
                }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Time tracker", style = MaterialTheme.typography.titleMedium)
                val active = entries.firstOrNull { it.endEpochMillis == null }
                if (active != null) { Text(active.label, style = MaterialTheme.typography.titleLarge); Text("Started " + formatEpoch(active.startEpochMillis)); Button(onClick = { store.stopTimeEntry(active.id); status = "Timer stopped" }) { Text("Stop") } }
                else { OutlinedTextField(timerLabel, { timerLabel = it }, label = { Text("What are you working on?") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { store.startTimeEntry(timerLabel, selectedProject.takeIf { it > 0L }); timerLabel = ""; status = "Timer started" }, enabled = timerLabel.isNotBlank()) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Start timer") } }
                Text("Today: " + store.todayTrackedMinutes() + " minutes tracked", style = MaterialTheme.typography.labelLarge)
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Projects", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(newProject, { newProject = it }, label = { Text("Project name") }, modifier = Modifier.weight(1f)); Button(onClick = { store.addProject(newProject); newProject = "" }, enabled = newProject.isNotBlank()) { Text("Add") }; OutlinedButton(onClick = { newProjectDialog = true }) { Text("Advanced") } }
                projects.forEach { project -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { FilterChip(selectedProject == project.id, { selectedProject = if (selectedProject == project.id) -1L else project.id }, label = { Text(project.name) }); Row { IconButton(onClick = { projectEditor = project }) { Icon(Icons.Default.Edit, "Edit project") }; IconButton(onClick = { store.deleteProject(project.id) }) { Icon(Icons.Default.Delete, "Delete project") } } } }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Challenges", style = MaterialTheme.typography.titleMedium); TextButton(onClick = { newChallenge = true }) { Text("Create") } }
                challenges.forEach { challenge -> val ratio = if (challenge.target == 0) 0f else challenge.progress.toFloat() / challenge.target; Text(challenge.title); Text(challenge.description, style = MaterialTheme.typography.bodySmall); LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()); Text(challenge.progress.toString() + " / " + challenge.target); Row { if (challenge.progress < challenge.target) TextButton(onClick = { store.completeChallenge(challenge.id) }) { Text("Record") }; TextButton(onClick = { challengeEditor = challenge }) { Text("Edit") }; TextButton(onClick = { store.deleteChallenge(challenge.id) }) { Text("Delete") } } }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Achievements", style = MaterialTheme.typography.titleMedium)
                if (achievements.isEmpty()) Text("No achievements unlocked yet.") else achievements.sortedByDescending { it.unlockedAt }.forEach { ListItem(headlineContent = { Text(it.title) }, supportingContent = { Text(it.description) }, leadingContent = { Icon(Icons.Default.EmojiEvents, null) }) }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup & restore", style = MaterialTheme.typography.titleMedium); Text("Export the complete secondary productivity store as versioned JSON, or restore a validated backup.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { exporter.launch("chronora-backup.json") }) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Save JSON") }
                OutlinedButton(onClick = { val send = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, store.exportJson()) }; context.startActivity(Intent.createChooser(send, "Share productivity backup")) }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Share") }; OutlinedButton(onClick = { importer.launch("application/json") }) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Import") } }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data safety", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { store.resetAll(); status = "Secondary productivity data reset" }) { Text("Reset secondary data") }
            } } }
            item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Local behavior", style = MaterialTheme.typography.titleMedium); SettingSwitch("Haptics", settings.haptics) { store.updateSettings { current -> current.copy(haptics = it) } }; SettingSwitch("Sounds", settings.sounds) { store.updateSettings { current -> current.copy(sounds = it) } }; SettingSwitch("Auto-scroll to now", settings.autoScrollNow) { store.updateSettings { current -> current.copy(autoScrollNow = it) } }; SettingSwitch("Show completed", settings.showCompleted) { store.updateSettings { current -> current.copy(showCompleted = it) } }; Text("Default task: " + settings.defaultTaskMinutes + "m · Focus: " + settings.defaultFocusMinutes + "m", style = MaterialTheme.typography.bodySmall)
            } } }
            item {
                MadeByRaunak(Modifier.fillMaxWidth().padding(top = 8.dp))
                ChronoraBrandLine(Modifier.fillMaxWidth())
            }
            if (status.isNotBlank()) item { Text(status, color = MaterialTheme.colorScheme.primary) }
        }
    }
    if (newChallenge || challengeEditor != null) ChallengeEditDialog(challengeEditor, store) { newChallenge = false; challengeEditor = null }
    if (newProjectDialog || projectEditor != null) ProjectEditDialog(projectEditor, store) { newProjectDialog = false; projectEditor = null }
}

@Composable private fun ProjectEditDialog(project: com.raunak.daytimeline.features.OfflineProject?, store: OfflineProductivityStore, close: () -> Unit) {
    var name by remember { mutableStateOf(project?.name ?: "") }
    var deadline by remember { mutableStateOf(project?.deadline ?: "") }
    AlertDialog(onDismissRequest = close, title = { Text(if (project == null) "New project" else "Edit project") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Project") }); OutlinedTextField(deadline, { deadline = it }, label = { Text("Deadline YYYY-MM-DD") }) } }, confirmButton = { Button(onClick = { val d = runCatching { java.time.LocalDate.parse(deadline) }.getOrNull(); if (project == null) store.addProject(name) else store.updateProject(project.id, name, d, project.color); close() }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable private fun ChallengeEditDialog(challenge: com.raunak.daytimeline.features.OfflineChallenge?, store: OfflineProductivityStore, close: () -> Unit) {
    var title by remember { mutableStateOf(challenge?.title ?: "") }
    var description by remember { mutableStateOf(challenge?.description ?: "") }
    var target by remember { mutableStateOf((challenge?.target ?: 1).toString()) }
    AlertDialog(onDismissRequest = close, title = { Text(if (challenge == null) "New challenge" else "Edit challenge") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(title, { title = it }, label = { Text("Title") }); OutlinedTextField(description, { description = it }, label = { Text("Description") }); OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Target") }) } }, confirmButton = { Button(onClick = { if (challenge == null) store.addChallenge(title, description, target.toIntOrNull() ?: 1) else store.updateChallenge(challenge.id, title, description, target.toIntOrNull() ?: challenge.target); close() }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked = checked, onCheckedChange = onChange) } }
private fun formatEpoch(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM · HH:mm"))
