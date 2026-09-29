package com.raunak.daytimeline.campus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun AttendanceTab() {
    val context = LocalContext.current
    val store = remember { CampusStore.get(context) }
    val data by store.data.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val minute = nowMinute()
    val stats = data.subjects.map { AttendanceEngine.subjectStats(data, it, today, minute) }
    var editing by remember { mutableStateOf<Subject?>(null) }
    var history by remember { mutableStateOf<Subject?>(null) }
    var showAllPending by remember { mutableStateOf(false) }
    val pending = AttendanceEngine.unmarked(data, today, minute)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val overall = AttendanceEngine.overall(stats)
            SectionCard("Overall ${"%.1f".format(overall)}%", "${stats.sumOf { it.attended }}/${stats.sumOf { it.conducted }} classes · ${data.semester.name} ends ${data.semester.end}") {
                val danger = stats.filter { it.mustAttend > 0 }
                if (danger.isNotEmpty()) Text("Below requirement: " + danger.joinToString { "${it.subject.name} (attend next ${it.mustAttend})" }, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
                else if (stats.isNotEmpty()) Text("Every subject is at or above its requirement.", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall)
                if (stats.isNotEmpty()) TextButton(onClick = { shareAttendance(context, data, stats) }) { Text("Export / share (CSV)") }
            }
        }
        if (pending.isNotEmpty()) item {
            SectionCard("Unmarked · ${pending.size}", action = {
                TextButton(onClick = { pending.forEach { store.mark(it.key, Mark.PRESENT) } }) { Text("All present") }
            }) {
                (if (showAllPending) pending else pending.take(6)).forEach { o ->
                    data.subjects.firstOrNull { it.id == o.subjectId }?.let { s -> ClassRow(o, s, null, past = true, showDate = true) { m -> store.mark(o.key, m) } }
                }
                if (pending.size > 6) TextButton(onClick = { showAllPending = !showAllPending }) { Text(if (showAllPending) "Show less" else "Show all") }
            }
        }
        items(stats, key = { it.subject.id }) { st ->
            val s = st.subject
            Card(Modifier.fillMaxWidth().clickable { history = s }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color(s.colorHex), shape = RoundedCornerShape(4.dp), modifier = Modifier.size(12.dp)) {}
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.Bold)
                            if (s.code.isNotBlank() || s.faculty.isNotBlank()) Text(listOf(s.code, s.faculty).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${"%.1f".format(st.percent)}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = percentColor(st.percent, s.requiredPercent, data.settings.attendanceMargin))
                        IconButton(onClick = { editing = s }) { Icon(Icons.Default.Edit, "Edit subject") }
                    }
                    LinearProgressIndicator(progress = { (st.percent / 100).toFloat() }, modifier = Modifier.fillMaxWidth(), color = percentColor(st.percent, s.requiredPercent, data.settings.attendanceMargin))
                    Text("${st.attended}/${st.conducted} attended · need ${s.requiredPercent}% · ${st.status}", style = MaterialTheme.typography.bodySmall)
                    Text("${st.remaining} classes left · can miss ${st.skippableOfRemaining} of them · ${"%.1f".format(st.projectedIfAllAttended)}% if you attend all" + if (st.unmarked > 0) " · ${st.unmarked} unmarked" else "", style = MaterialTheme.typography.bodySmall)
                    if (st.conducted > 0) Text("Skipping the next class → ${"%.1f".format(AttendanceEngine.afterSkipping(st))}%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (data.subjects.isEmpty()) item { Text("Add subjects and your weekly timetable in the Timetable tab.") }
    }
    editing?.let { s -> SubjectDialog(s, onSave = { u -> store.update { d -> d.copy(subjects = d.subjects.map { if (it.id == u.id) u else it }) }; editing = null }, onDelete = {
        store.update { d -> d.copy(subjects = d.subjects.filterNot { it.id == s.id }, slots = d.slots.filterNot { it.subjectId == s.id }) }; editing = null
    }) { editing = null } }
    history?.let { s -> HistoryDialog(s, data, store) { history = null } }
}

@Composable
private fun HistoryDialog(s: Subject, data: CampusData, store: CampusStore, close: () -> Unit) {
    val start = runCatching { LocalDate.parse(data.semester.start) }.getOrDefault(LocalDate.now().minusMonths(1))
    val past = AttendanceEngine.occurrencesBetween(data, start, LocalDate.now()).filter { it.subjectId == s.id && (it.date.isBefore(LocalDate.now()) || it.end <= nowMinute()) }.reversed()
    AlertDialog(onDismissRequest = close, title = { Text(s.name) }, text = {
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            if (past.isEmpty()) item { Text("No classes held yet this semester.") }
            items(past, key = { it.key }) { o -> ClassRow(o, s, data.marks[o.key], past = true, showDate = true) { m -> store.mark(o.key, m) } }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("Done") } })
}

@Composable
private fun SubjectDialog(s: Subject, onSave: (Subject) -> Unit, onDelete: () -> Unit, close: () -> Unit) {
    var name by remember { mutableStateOf(s.name) }
    var code by remember { mutableStateOf(s.code) }
    var faculty by remember { mutableStateOf(s.faculty) }
    var credits by remember { mutableIntStateOf(s.credits) }
    var required by remember { mutableIntStateOf(s.requiredPercent) }
    var byHours by remember { mutableStateOf(s.countByHours) }
    var priorA by remember { mutableStateOf(s.priorAttended.toString()) }
    var priorC by remember { mutableStateOf(s.priorConducted.toString()) }
    var color by remember { mutableLongStateOf(s.colorHex) }
    AlertDialog(onDismissRequest = close, title = { Text("Subject") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(code, { code = it }, label = { Text("Code") }, singleLine = true, modifier = Modifier.weight(1f)); OutlinedTextField(faculty, { faculty = it }, label = { Text("Faculty") }, singleLine = true, modifier = Modifier.weight(1f)) } }
            item { Stepper("Credits", "$credits", { credits = (credits - 1).coerceAtLeast(0) }, { credits++ }) }
            item { Stepper("Required attendance", "$required%", { required = (required - 5).coerceAtLeast(50) }, { required = (required + 5).coerceAtMost(100) }) }
            item { SwitchRow("Count by hours (2-hour lab = 2)", byHours) { byHours = it } }
            item {
                Text("Before tracking started", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(priorA, { priorA = it.filter(Char::isDigit) }, label = { Text("Attended") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(priorC, { priorC = it.filter(Char::isDigit) }, label = { Text("Held") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AttendanceEngine.palette.take(8).forEach { c ->
                        Surface(color = Color(c), shape = RoundedCornerShape(50), modifier = Modifier.size(if (c == color) 30.dp else 24.dp).clickable { color = c }) {}
                    }
                }
            }
            item { TextButton(onClick = onDelete) { Text("Delete subject and its classes", color = MaterialTheme.colorScheme.error) } }
        }
    }, confirmButton = {
        Button(onClick = { onSave(s.copy(name = name.trim().ifBlank { s.name }, code = code.trim(), faculty = faculty.trim(), credits = credits, requiredPercent = required, countByHours = byHours, priorAttended = priorA.toIntOrNull() ?: 0, priorConducted = priorC.toIntOrNull() ?: 0, colorHex = color)) }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
internal fun PasteTimetableDialog(store: CampusStore, close: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val data = store.data.value
    val preview = remember(text) { AttendanceEngine.parseTimetable(text, data.subjects, 1, data.settings.afternoonBeforeHour) }
    AlertDialog(onDismissRequest = close, title = { Text("Paste weekly timetable") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("One class per line: day, time, subject, type (L/P/T or Lab), room.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(text, { text = it }, placeholder = { Text("Mon 9-10 DSA L 203\nMon 10-11 ML L 204\nTue 2-4pm ML Lab LAB-2\nWed 11:10-12:05 CN T") }, minLines = 6, modifier = Modifier.fillMaxWidth())
            Text("${preview.second.size} classes · ${preview.first.size} new subjects", style = MaterialTheme.typography.labelMedium)
        }
    }, confirmButton = {
        Button(enabled = preview.second.isNotEmpty(), onClick = {
            val (subjects, slots) = AttendanceEngine.parseTimetable(text, store.data.value.subjects, store.nextId(), store.data.value.settings.afternoonBeforeHour)
            store.update { d -> d.copy(subjects = d.subjects + subjects, slots = d.slots + slots) }
            close()
        }) { Text("Add") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
internal fun HolidayDialog(store: CampusStore, close: () -> Unit) {
    var from by remember { mutableStateOf(LocalDate.now()) }
    var to by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Holiday / no classes") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DateButton("From", from) { from = it; if (to.isBefore(it)) to = it }
            DateButton("To", to) { to = it }
            OutlinedTextField(note, { note = it }, label = { Text("Reason (Diwali, mid-sem break…)") }, singleLine = true)
        }
    }, confirmButton = {
        Button(onClick = {
            val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
            var id = store.nextId()
            store.update { d -> d.copy(exceptions = d.exceptions + days.map { ScheduleException(id++, ExceptionKind.HOLIDAY, it.toString(), note = note.trim()) }) }
            close()
        }) { Text("Add ${ChronoUnitDays(from, to)} day(s)") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

private fun ChronoUnitDays(a: LocalDate, b: LocalDate) = java.time.temporal.ChronoUnit.DAYS.between(a, b) + 1

