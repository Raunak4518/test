package com.raunak.daytimeline.campus

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

/** Editable list of short values: remove with ×, add with the field below. */
@Composable
internal fun ListEditor(title: String, values: List<String>, numeric: Boolean = false, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        values.forEachIndexed { i, v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { onChange(values.toMutableList().also { it.removeAt(i) }) }) { Icon(Icons.Default.Close, "Remove $v") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, label = { Text("Add") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default
            )
            IconButton(onClick = {
                val v = input.trim()
                if (v.isNotEmpty() && (!numeric || v.toIntOrNull() != null)) { onChange(values + v); input = "" }
            }) { Icon(Icons.Default.Add, "Add") }
        }
    }
}

@Composable
private fun NumberStepper(label: String, value: Int, step: Int, min: Int, max: Int = 10_000, suffix: String = "m", onChange: (Int) -> Unit) =
    Stepper(label, "$value$suffix", { onChange((value - step).coerceAtLeast(min)) }, { onChange((value + step).coerceAtMost(max)) })

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
                NumberStepper("Shortest free slot worth using", st.minFreeWindowMinutes, 5, 10) { save(st.copy(minFreeWindowMinutes = it)) }
                NumberStepper("Library closing reminder", st.libraryCloseReminderMinutes, 5, 0) { save(st.copy(libraryCloseReminderMinutes = it)) }
                NumberStepper("A visit counts after", st.libraryVisitMinutes, 5, 5) { save(st.copy(libraryVisitMinutes = it)) }
                Text("Opening hours and the daily study goal are in the Library tab.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("Reminders") {
                ListEditor("Deadline reminders (hours before)", st.deadlineReminderHours.map { it.toString() }, numeric = true) { v -> save(st.copy(deadlineReminderHours = v.mapNotNull(String::toIntOrNull).filter { it > 0 }.distinct().sortedDescending())) }
                ListEditor("Interview/test reminders (hours before)", st.companyReminderHours.map { it.toString() }, numeric = true) { v -> save(st.copy(companyReminderHours = v.mapNotNull(String::toIntOrNull).filter { it > 0 }.distinct().sortedDescending())) }
                Text("Class, leave-now and wake-up timings are in the Wake-up tab.", style = MaterialTheme.typography.bodySmall)
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
                NumberStepper("Warn when within this many % of the requirement", st.attendanceMargin, 1, 0, 50, "%") { save(st.copy(attendanceMargin = it)) }
                NumberStepper("Pasted times before this hour are afternoon", st.afternoonBeforeHour, 1, 1, 12, ":00") { save(st.copy(afternoonBeforeHour = it)) }
                Text("Required % and credits are set per subject in the Attendance tab.", style = MaterialTheme.typography.bodySmall)
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
