package com.raunak.daytimeline.campus

import com.raunak.daytimeline.ui.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
private fun NumberStepper(label: String, value: Int, step: Int, min: Int, max: Int = 10_000, suffix: String = "m", onChange: (Int) -> Unit) =
    Stepper(label, "$value$suffix", { onChange((value - step).coerceAtLeast(min)) }, { onChange((value + step).coerceAtMost(max)) })

/** Save everything to one file and restore it on a new phone. */
@Composable
private fun BackupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var includePrivate by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var restored by remember { mutableStateOf(false) }
    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                val json = FullBackup.export(context, includePrivate)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) } }
                "Backup saved"
            }.getOrElse { "Backup failed: ${it.message}" }
        }
    }
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() }
            if (json == null) { status = "Couldn't read that file"; return@launch }
            FullBackup.import(context, json).onSuccess { status = "$it. Restart Chronora to finish."; restored = true }.onFailure { status = "Restore failed: ${it.message}" }
        }
    }
    SectionCard("Backup & restore", "Everything in one file: timeline, Campus, habits, notes, alarms, blocking, wellbeing and web-filter settings") {
        SwitchRow("Include private Discipline data", includePrivate) { includePrivate = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { exporter.launch("chronora-backup-${java.time.LocalDate.now()}.json") }) { Text("Back up") }
            if (rememberLockedUntil() == null) OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "*/*")) }) { Text("Restore") }
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary)
        if (restored) Button(onClick = {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }) { Text("Restart now") }
    }
}

/** Every number and list Campus uses, in one place. */
@Composable
internal fun CampusSettingsTab() {
    val context = LocalContext.current
    val store = remember { CampusStore.get(context) }
    val data by store.data.collectAsStateWithLifecycle()
    val st = data.settings
    fun save(next: CampusSettings) = store.update { it.copy(settings = next) }
    var confirmReset by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("Study sheets") {
                ListEditor("Revision gaps (days) — first after solving, then after each revision", st.reviewGaps.map { it.toString() }, numeric = true) { v ->
                    save(st.copy(reviewGaps = v.mapNotNull(String::toIntOrNull).filter { it > 0 }.ifEmpty { CampusSettings().reviewGaps }))
                }
            }
        }
        item {
            SectionCard("Library & study blocks") {
                NumberStepper("Focus block", st.focusBlockMinutes, 5, 10) { save(st.copy(focusBlockMinutes = it)) }
                NumberStepper("Break between blocks", st.breakMinutes, 5, 0) { save(st.copy(breakMinutes = it)) }
                NumberStepper("Walking time around classes", st.walkBufferMinutes, 5, 0) { save(st.copy(walkBufferMinutes = it)) }
                NumberStepper("Library slot: shortest free gap", st.librarySlotMinutes, 15, 30) { save(st.copy(librarySlotMinutes = it)) }
                SwitchRow("Split into focus blocks", st.splitLibraryIntoBlocks) { save(st.copy(splitLibraryIntoBlocks = it)) }
                NumberStepper("Library closing reminder", st.libraryCloseReminderMinutes, 5, 0) { save(st.copy(libraryCloseReminderMinutes = it)) }
                NumberStepper("A visit counts after", st.libraryVisitMinutes, 5, 5) { save(st.copy(libraryVisitMinutes = it)) }
            }
        }
        item {
            SectionCard("Reminders") {
                ListEditor("Deadline reminders (hours before)", st.deadlineReminderHours.map { it.toString() }, numeric = true) { v -> save(st.copy(deadlineReminderHours = v.mapNotNull(String::toIntOrNull).filter { it > 0 }.distinct().sortedDescending())) }
                ListEditor("Interview/test reminders (hours before)", st.companyReminderHours.map { it.toString() }, numeric = true) { v -> save(st.copy(companyReminderHours = v.mapNotNull(String::toIntOrNull).filter { it > 0 }.distinct().sortedDescending())) }
            }
        }
        item {
            SectionCard("Types & stages") {
                ListEditor("Deadline & exam types", st.deadlineKinds) { v -> save(st.copy(deadlineKinds = v.ifEmpty { CampusSettings().deadlineKinds })) }
                Text("Counted as exams (countdown):", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(st.deadlineKinds.size) { i ->
                        val k = st.deadlineKinds[i]
                        FilterChip(st.isExam(k), { save(st.copy(examKinds = if (st.isExam(k)) st.examKinds.filterNot { it.equals(k, true) } else st.examKinds + k)) }, label = { Text(k) })
                    }
                }
                ListEditor("Placement stages", st.placementStages) { v -> save(st.copy(placementStages = v.ifEmpty { CampusSettings().placementStages })) }
            }
        }
        item {
            SectionCard("Daily score", "Points for each part; parts you set to 0 are left out") {
                NumberStepper("Woke on time", st.scoreWake, 5, 0, 100, "") { save(st.copy(scoreWake = it)) }
                NumberStepper("Classes attended", st.scoreClasses, 5, 0, 100, "") { save(st.copy(scoreClasses = it)) }
                NumberStepper("Deep study", st.scoreStudy, 5, 0, 100, "") { save(st.copy(scoreStudy = it)) }
                NumberStepper("Problems solved", st.scoreProblems, 5, 0, 100, "") { save(st.copy(scoreProblems = it)) }
                NumberStepper("Screen time", st.scoreScreen, 5, 0, 100, "") { save(st.copy(scoreScreen = it)) }
                NumberStepper("Discipline", st.scoreDiscipline, 5, 0, 100, "") { save(st.copy(scoreDiscipline = it)) }
                NumberStepper("On time means within", st.onTimeToleranceMinutes, 1, 0) { save(st.copy(onTimeToleranceMinutes = it)) }
                ListEditor("Grade letters (score=letter)", st.gradeBands.sortedByDescending { it.minScore }.map { "${it.minScore}=${it.letter}" }) { v ->
                    val bands = v.mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { (n, l) -> n.trim().toIntOrNull()?.let { GradeBand(it, l.trim()) } } }
                    save(st.copy(gradeBands = bands.ifEmpty { CampusSettings().gradeBands }))
                }
            }
        }
        item {
            SectionCard("Attendance & timetable") {
                NumberStepper("Warning margin", st.attendanceMargin, 1, 0, 50, "%") { save(st.copy(attendanceMargin = it)) }
                SwitchRow("Count unmarked as present", st.assumePresent) { save(st.copy(assumePresent = it)) }
                NumberStepper("Rotation weeks", st.rotationWeeks.coerceAtLeast(1), 1, 1, 4, if (st.rotationWeeks <= 1) " (off)" else " weeks") { save(st.copy(rotationWeeks = it)) }
                if (st.rotationWeeks > 1) {
                    val today = java.time.LocalDate.now()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("This week is Week ${AttendanceEngine.weekLetter(AttendanceEngine.rotationWeek(data, today))}", Modifier.weight(1f))
                        TextButton(onClick = { save(st.copy(rotationStart = today.with(java.time.DayOfWeek.MONDAY).toString())) }) { Text("Make this Week A") }
                    }
                }
                NumberStepper("PM before hour", st.afternoonBeforeHour, 1, 1, 12, ":00") { save(st.copy(afternoonBeforeHour = it)) }
            }
        }
        item {
            SectionCard("Exams & marks") {
                NumberStepper("Revision days before exam", st.examBufferDays, 1, 0, 14, "d") { save(st.copy(examBufferDays = it)) }
                ListEditor("Predicted grade from internal marks (percent=grade)", st.gradeCutoffs.sortedByDescending { it.minPercent }.map { "${it.minPercent}=${it.grade}" }) { v ->
                    val cuts = v.mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { (n, g) -> n.trim().toIntOrNull()?.let { GradeCutoff(it, g.trim()) } } }
                    save(st.copy(gradeCutoffs = cuts.ifEmpty { CampusSettings().gradeCutoffs }))
                }
                NumberStepper("Sleep goal", st.sleepGoalMinutes, 15, 240, 720) { save(st.copy(sleepGoalMinutes = it)) }
            }
        }
        item {
            SectionCard("Mess timings", "When the hostel mess serves; study plans keep time to eat") {
                st.meals.forEachIndexed { i, m ->
                    fun put(n: MealWindow) = save(st.copy(meals = st.meals.toMutableList().also { it[i] = n }))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        var name by remember(m.name) { mutableStateOf(m.name) }
                        OutlinedTextField(name, { name = it; if (it.isNotBlank()) put(m.copy(name = it.trim())) }, singleLine = true, modifier = Modifier.weight(1f))
                        TimeButton(m.start, { put(m.copy(start = it, end = maxOf(m.end, it + 15))) })
                        Text("–")
                        TimeButton(m.end, { put(m.copy(end = maxOf(it, m.start + 15))) })
                        IconButton(onClick = { save(st.copy(meals = st.meals.filterIndexed { j, _ -> j != i })) }) { Icon(Icons.Default.Close, "Remove meal") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        (1..7).forEach { d -> FilterChip(d in m.days, { put(m.copy(days = if (d in m.days) (m.days - d).ifEmpty { m.days } else m.days + d)) }, label = { Text(java.time.DayOfWeek.of(d).name.take(2).lowercase().replaceFirstChar(Char::uppercase)) }) }
                    }
                }
                TextButton(onClick = { save(st.copy(meals = st.meals + MealWindow("Snacks", 17 * 60, 18 * 60))) }) { Icon(Icons.Default.Add, null); Text("Add meal") }
                NumberStepper("Time a meal takes (with the walk)", st.mealMinutes, 5, 10, 120) { save(st.copy(mealMinutes = it)) }
                SwitchRow("Remind me before the mess closes", !st.mealReminderOff) { save(st.copy(mealReminderOff = !it)) }
                if (!st.mealReminderOff) NumberStepper("Minutes before closing", st.mealReminderMinutes, 5, 5, 90) { save(st.copy(mealReminderMinutes = it)) }
                SwitchRow("Keep meal times free", !st.mealsIgnoredInPlanning) { save(st.copy(mealsIgnoredInPlanning = !it)) }
                val today = java.time.LocalDate.now()
                val clashes = (0L..6L).map { today.plusDays(it) }.flatMap { d -> Mess.clashes(data, d).map { d to it } }
                if (clashes.isNotEmpty()) Text("No time to eat: " + clashes.joinToString { (d, m) -> "${d.dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase)} ${m.name.lowercase()}" }, color = Chronora.colors.warn, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { BackupCard() }
        item {
            SectionCard("Wake-up screen", "Shown while the alarm rings") {
                SwitchRow("Morning briefing", !st.wakeBriefingOff) { save(st.copy(wakeBriefingOff = !it)) }
                ListEditor("Wake-up quotes (one is picked each day)", st.wakeQuotes) { v -> save(st.copy(wakeQuotes = v)) }
            }
        }
        item {
            SectionCard("Lock-in buttons") {
                ListEditor("Durations on the Today tab (minutes)", st.lockInMinutes.map { it.toString() }, numeric = true) { v -> save(st.copy(lockInMinutes = v.mapNotNull(String::toIntOrNull).filter { it > 0 })) }
            }
        }
        item {
            OutlinedButton(onClick = { confirmReset = true }) { Text("Reset these settings to defaults") }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset settings?") },
        text = { Text("Only the numbers and lists on this page go back to their defaults. Your timetable, attendance, sheets and grades are kept.") },
        confirmButton = { Button(onClick = { save(CampusSettings(gradeScale = st.gradeScale)); confirmReset = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
    )
}
