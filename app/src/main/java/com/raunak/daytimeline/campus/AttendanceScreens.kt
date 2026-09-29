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

// ------------------------------------------------------------------ Timetable

@Composable
internal fun TimetableTab() {
    val context = LocalContext.current
    val store = remember { CampusStore.get(context) }
    val data by store.data.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }
    var editSlot by remember { mutableStateOf<TimetableSlot?>(null) }
    val subjectOf = data.subjects.associateBy { it.id }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("Semester", "${data.semester.name}: ${data.semester.start} → ${data.semester.end}") {
                var name by remember(data.semester.name) { mutableStateOf(data.semester.name) }
                OutlinedTextField(name, { name = it; store.update { d -> d.copy(semester = d.semester.copy(name = it)) } }, label = { Text("Name (e.g. Sem 5)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DateButton("Start", LocalDate.parse(data.semester.start)) { d -> store.update { it.copy(semester = it.semester.copy(start = d.toString())) } }
                    DateButton("End", LocalDate.parse(data.semester.end)) { d -> store.update { it.copy(semester = it.semester.copy(end = d.toString())) } }
                }
            }
        }
        item {
            SectionCard("Weekly timetable", "${data.slots.size} classes · ${data.subjects.size} subjects", action = {
                Row { TextButton(onClick = { dialog = "paste" }) { Text("Paste") }; TextButton(onClick = { dialog = "slot" }) { Text("Add") } }
            }) {
                (1..7).forEach { day ->
                    val slots = data.slots.filter { it.day == day }.sortedBy { it.start }
                    if (slots.isNotEmpty()) {
                        Text(DayOfWeek.of(day).name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        slots.forEach { sl ->
                            val s = subjectOf[sl.subjectId]
                            Row(Modifier.fillMaxWidth().clickable { editSlot = sl }, verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = Color(s?.colorHex ?: 0xFF888888), shape = RoundedCornerShape(3.dp), modifier = Modifier.width(4.dp).height(28.dp)) {}
                                Spacer(Modifier.width(8.dp))
                                Text("${clock(sl.start)}–${clock(sl.end)}  ${s?.name ?: "?"} ${sl.type.short}" + if (sl.room.isNotBlank()) " · ${sl.room}" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { store.update { d -> d.copy(slots = d.slots.filterNot { it.id == sl.id }) } }) { Icon(Icons.Default.Delete, "Delete") }
                            }
                        }
                    }
                }
                if (data.slots.isEmpty()) Text("Tap Paste to enter the whole week at once, e.g.\nMon 9-10 DSA L 203\nMon 2-4pm ML Lab LAB-2", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("Changes & holidays", "Cancelled, rescheduled and extra classes, holidays") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip({ dialog = "holiday" }, label = { Text("Holiday") })
                    AssistChip({ dialog = "cancel" }, label = { Text("Cancel") })
                    AssistChip({ dialog = "move" }, label = { Text("Reschedule") })
                    AssistChip({ dialog = "extra" }, label = { Text("Extra") })
                }
                data.exceptions.filter { (it.newDate ?: it.date) >= LocalDate.now().minusDays(7).toString() }.sortedBy { it.date }.forEach { ex ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(describe(ex, data), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { store.update { d -> d.copy(exceptions = d.exceptions.filterNot { it.id == ex.id }) } }) { Icon(Icons.Default.Delete, "Remove") }
                    }
                }
            }
        }
    }

    when (dialog) {
        "paste" -> PasteTimetableDialog(store) { dialog = null }
        "slot" -> SlotDialog(null, data, store) { dialog = null }
        "holiday" -> HolidayDialog(store) { dialog = null }
        "cancel", "move" -> ChangeClassDialog(dialog == "move", data, store) { dialog = null }
        "extra" -> ExtraClassDialog(data, store) { dialog = null }
    }
    editSlot?.let { SlotDialog(it, data, store) { editSlot = null } }
}

private fun describe(ex: ScheduleException, data: CampusData): String {
    val slot = data.slots.firstOrNull { it.id == ex.slotId }
    val subject = data.subjects.firstOrNull { it.id == (ex.subjectId ?: slot?.subjectId) }?.name ?: ""
    val d = runCatching { LocalDate.parse(ex.date).format(DateTimeFormatter.ofPattern("EEE d MMM")) }.getOrDefault(ex.date)
    return when (ex.kind) {
        ExceptionKind.HOLIDAY -> "Holiday · $d" + if (ex.note.isNotBlank()) " · ${ex.note}" else ""
        ExceptionKind.CANCEL -> "Cancelled · $subject · $d ${slot?.let { clock(it.start) } ?: ""}"
        ExceptionKind.RESCHEDULE -> "Moved · $subject · $d → ${ex.newDate} ${clock(ex.start)}–${clock(ex.end)}"
        ExceptionKind.EXTRA -> "Extra · $subject · $d ${clock(ex.start)}–${clock(ex.end)}" + if (ex.room.isNotBlank()) " · ${ex.room}" else ""
    }
}

@Composable
private fun PasteTimetableDialog(store: CampusStore, close: () -> Unit) {
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
private fun SubjectPicker(data: CampusData, selected: Long?, onPick: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRowCompat(data.subjects) { s -> FilterChip(selected == s.id, { onPick(s.id) }, label = { Text(s.name) }) }
    }
}

@Composable
private fun <T> LazyRowCompat(items: List<T>, content: @Composable (T) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(items) { content(it) } }
}

@Composable
private fun TimeFields(start: String, end: String, onStart: (String) -> Unit, onEnd: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(start, onStart, label = { Text("Start (09:00)") }, singleLine = true, modifier = Modifier.weight(1f), isError = parseClock(start) == null)
        OutlinedTextField(end, onEnd, label = { Text("End (10:00)") }, singleLine = true, modifier = Modifier.weight(1f), isError = parseClock(end) == null)
    }
}

@Composable
private fun SlotDialog(existing: TimetableSlot?, data: CampusData, store: CampusStore, close: () -> Unit) {
    var subjectId by remember { mutableStateOf(existing?.subjectId ?: data.subjects.firstOrNull()?.id) }
    var newSubject by remember { mutableStateOf("") }
    var day by remember { mutableIntStateOf(existing?.day ?: LocalDate.now().dayOfWeek.value) }
    var start by remember { mutableStateOf(existing?.let { clock(it.start) } ?: "09:00") }
    var end by remember { mutableStateOf(existing?.let { clock(it.end) } ?: "10:00") }
    var room by remember { mutableStateOf(existing?.room ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: ClassType.LECTURE) }
    AlertDialog(onDismissRequest = close, title = { Text(if (existing == null) "Add class" else "Edit class") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SubjectPicker(data, subjectId) { subjectId = it; newSubject = "" }
            OutlinedTextField(newSubject, { newSubject = it }, label = { Text("…or new subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyRowCompat((1..7).toList()) { d -> FilterChip(day == d, { day = d }, label = { Text(DayOfWeek.of(d).name.take(3)) }) }
            TimeFields(start, end, { start = it }, { end = it })
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { ClassType.values().forEach { t -> FilterChip(type == t, { type = t }, label = { Text(t.label) }) } }
            OutlinedTextField(room, { room = it }, label = { Text("Room") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        Button(onClick = {
            val s = parseClock(start); val e = parseClock(end)
            if (s == null || e == null || e <= s) return@Button
            store.update { d ->
                var subjects = d.subjects
                val sid = if (newSubject.isNotBlank()) store.nextId().also { subjects = subjects + Subject(it, newSubject.trim(), colorHex = AttendanceEngine.palette[subjects.size % AttendanceEngine.palette.size]) } else subjectId ?: return@update d
                val slot = TimetableSlot(existing?.id ?: store.nextId(), sid, day, s, e, room.trim(), type)
                d.copy(subjects = subjects, slots = d.slots.filterNot { it.id == slot.id } + slot)
            }
            close()
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun HolidayDialog(store: CampusStore, close: () -> Unit) {
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

@Composable
private fun ChangeClassDialog(reschedule: Boolean, data: CampusData, store: CampusStore, close: () -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var picked by remember { mutableStateOf<ClassOccurrence?>(null) }
    var newDate by remember { mutableStateOf(LocalDate.now()) }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    val classes = AttendanceEngine.occurrences(data, date).filter { it.source == "Regular" }
    AlertDialog(onDismissRequest = close, title = { Text(if (reschedule) "Reschedule a class" else "Cancel a class") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DateButton("On", date) { date = it; picked = null }
            if (classes.isEmpty()) Text("No regular classes that day.", style = MaterialTheme.typography.bodySmall)
            classes.forEach { o ->
                val s = data.subjects.firstOrNull { it.id == o.subjectId }
                FilterChip(picked?.key == o.key, { picked = o; start = clock(o.start); end = clock(o.end); room = o.room; newDate = date }, label = { Text("${clock(o.start)} ${s?.name}") })
            }
            if (reschedule && picked != null) {
                DateButton("Move to", newDate) { newDate = it }
                TimeFields(start, end, { start = it }, { end = it })
                OutlinedTextField(room, { room = it }, label = { Text("Room") }, singleLine = true)
            }
        }
    }, confirmButton = {
        Button(enabled = picked != null, onClick = {
            val o = picked ?: return@Button
            val ex = if (reschedule) {
                val s = parseClock(start) ?: return@Button; val e = parseClock(end) ?: return@Button
                ScheduleException(store.nextId(), ExceptionKind.RESCHEDULE, date.toString(), slotId = o.slotId, newDate = newDate.toString(), start = s, end = e, room = room.trim())
            } else ScheduleException(store.nextId(), ExceptionKind.CANCEL, date.toString(), slotId = o.slotId)
            store.update { d -> d.copy(exceptions = d.exceptions + ex, marks = d.marks - o.key) }
            close()
        }) { Text(if (reschedule) "Move" else "Cancel class") }
    }, dismissButton = { TextButton(onClick = close) { Text("Close") } })
}

@Composable
private fun ExtraClassDialog(data: CampusData, store: CampusStore, close: () -> Unit) {
    var subjectId by remember { mutableStateOf(data.subjects.firstOrNull()?.id) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var start by remember { mutableStateOf("17:00") }
    var end by remember { mutableStateOf("18:00") }
    var room by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ClassType.LECTURE) }
    AlertDialog(onDismissRequest = close, title = { Text("Extra class") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SubjectPicker(data, subjectId) { subjectId = it }
            DateButton("On", date) { date = it }
            TimeFields(start, end, { start = it }, { end = it })
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { ClassType.values().forEach { t -> FilterChip(type == t, { type = t }, label = { Text(t.label) }) } }
            OutlinedTextField(room, { room = it }, label = { Text("Room") }, singleLine = true)
        }
    }, confirmButton = {
        Button(enabled = subjectId != null, onClick = {
            val s = parseClock(start) ?: return@Button; val e = parseClock(end) ?: return@Button
            store.update { d -> d.copy(exceptions = d.exceptions + ScheduleException(store.nextId(), ExceptionKind.EXTRA, date.toString(), subjectId = subjectId, start = s, end = e, room = room.trim(), type = type)) }
            close()
        }) { Text("Add") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
