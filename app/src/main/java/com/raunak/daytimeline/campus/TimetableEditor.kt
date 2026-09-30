package com.raunak.daytimeline.campus

import com.raunak.daytimeline.ui.*

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private fun dayName(d: Int) = DayOfWeek.of(d).name.lowercase().replaceFirstChar { it.uppercase() }
private fun shortDay(d: Int) = DayOfWeek.of(d).name.take(3).lowercase().replaceFirstChar { it.uppercase() }
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM")

/** Tap-to-pick time using the Material time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeButton(minute: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text(clock(minute)) }
    if (open) {
        val context = LocalContext.current
        val state = rememberTimePickerState(minute / 60 % 24, minute % 60, DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute); open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state) }
        )
    }
}

@Composable
internal fun TimetableTab() {
    val context = LocalContext.current
    val store = remember { CampusStore.get(context) }
    val data by store.data.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var weekStart by remember { mutableStateOf(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
    var dayEditor by remember { mutableStateOf<LocalDate?>(null) }
    var subjectEditor by remember { mutableStateOf<Long?>(null) }
    var newSubject by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var semesterOpen by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard(data.semester.name, "${data.semester.start} → ${data.semester.end} · every weekly class repeats until the end date", action = {
                TextButton(onClick = { semesterOpen = !semesterOpen }) { Text(if (semesterOpen) "Done" else "Edit") }
            }) {
                if (semesterOpen) {
                    var name by remember(data.semester.name) { mutableStateOf(data.semester.name) }
                    OutlinedTextField(name, { name = it }, label = { Text("Semester name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { store.update { it.copy(semester = it.semester.copy(name = name.trim().ifBlank { "Semester" })) } }) { Text("Save name") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DateButton("Start", LocalDate.parse(data.semester.start)) { d -> store.update { it.copy(semester = it.semester.copy(start = d.toString())) } }
                        DateButton("End", LocalDate.parse(data.semester.end)) { d -> store.update { it.copy(semester = it.semester.copy(end = d.toString())) } }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Week", "Subjects", "Changes").forEachIndexed { i, t -> FilterChip(mode == i, { mode = i }, label = { Text(t) }) }
            }
        }
        when (mode) {
            0 -> {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Week of ${weekStart.format(dayFmt)}" + AttendanceEngine.rotationWeek(data, weekStart).let { if (it > 0) " · Week " + AttendanceEngine.weekLetter(it) else "" }, fontWeight = FontWeight.Bold)
                            if (weekStart != today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) TextButton(onClick = { weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }) { Text("This week") }
                        }
                        IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }) { Text("›", style = MaterialTheme.typography.titleLarge) }
                    }
                }
                item { WeekGrid(data, weekStart, onDay = { dayEditor = it }) }
                val days = (0L..6L).map { weekStart.plusDays(it) }
                val clashes = days.flatMap { d -> AttendanceEngine.dayConflicts(AttendanceEngine.occurrences(data, d)).map { d to it } }
                if (clashes.isNotEmpty()) item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Overlapping classes", fontWeight = FontWeight.Bold)
                            clashes.forEach { (d, p) -> Text("${d.format(dayFmt)}: ${nameOf(data, p.first.subjectId)} ${clock(p.first.start)} and ${nameOf(data, p.second.subjectId)} ${clock(p.second.start)}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                items(days, key = { it.toString() }) { d ->
                    val occ = AttendanceEngine.occurrences(data, d)
                    val holiday = data.exceptions.firstOrNull { it.kind == ExceptionKind.HOLIDAY && it.date == d.toString() }
                    val changes = data.exceptions.count { it.kind != ExceptionKind.HOLIDAY && (it.date == d.toString() || it.newDate == d.toString()) }
                    Card(Modifier.fillMaxWidth().clickable { dayEditor = d }, border = if (d == today) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.format(dayFmt) + if (d == today) " · today" else "", fontWeight = FontWeight.Bold)
                                Text(
                                    when {
                                        holiday != null -> "Holiday" + holiday.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                                        occ.isEmpty() -> "No classes"
                                        else -> occ.joinToString(" · ") { "${clock(it.start)} ${nameOf(data, it.subjectId)}" }
                                    } + if (changes > 0) " · $changes change(s)" else "",
                                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text("Edit", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            1 -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { newSubject = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Subject") }
                        OutlinedButton(onClick = { dialog = "paste" }) { Text("Paste whole week") }
                    }
                }
                val current = AttendanceEngine.currentSlots(data, today)
                val clashes = AttendanceEngine.conflicts(current)
                if (clashes.isNotEmpty()) item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Clashes in the weekly timetable", fontWeight = FontWeight.Bold)
                            clashes.forEach { (a, b) -> Text("${shortDay(a.day)}: ${nameOf(data, a.subjectId)} ${clock(a.start)}–${clock(a.end)} and ${nameOf(data, b.subjectId)} ${clock(b.start)}–${clock(b.end)}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                items(data.subjects, key = { it.id }) { s ->
                    val mine = current.filter { it.subjectId == s.id }.sortedWith(compareBy({ it.day }, { it.start }))
                    val hours = mine.sumOf { it.end - it.start }
                    Card(Modifier.fillMaxWidth().clickable { subjectEditor = s.id }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color(s.colorHex), shape = RoundedCornerShape(4.dp), modifier = Modifier.width(6.dp).height(44.dp)) {}
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name + if (s.code.isNotBlank()) " · ${s.code}" else "", fontWeight = FontWeight.Bold)
                                Text(if (mine.isEmpty()) "No weekly classes yet — tap to add" else "${mine.size} classes · ${hm(hours)} a week", style = MaterialTheme.typography.bodySmall)
                                mine.groupBy { it.day }.forEach { (day, l) ->
                                    Text("${shortDay(day)}  " + l.joinToString(", ") { "${clock(it.start)}–${clock(it.end)}${if (it.type != ClassType.LECTURE) " ${it.type.short}" else ""}" }, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text("Edit week", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            else -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { dialog = "holiday" }) { Text("Holidays (date range)") }
                    }
                }
                val list = data.exceptions.sortedBy { it.newDate ?: it.date }
                if (list.isEmpty()) item { Text("No changes yet.") }
                items(list, key = { it.id }) { ex ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(describeChange(ex, data), Modifier.weight(1f).clickable { dayEditor = LocalDate.parse(ex.date) }, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { store.update { d -> d.copy(exceptions = d.exceptions.filterNot { it.id == ex.id }) } }) { Icon(Icons.Default.Delete, "Undo change") }
                    }
                }
            }
        }
    }

    when (dialog) {
        "paste" -> PasteTimetableDialog(store) { dialog = null }
        "holiday" -> HolidayDialog(store) { dialog = null }
    }
    if (newSubject) SubjectWeekEditor(null, data, store, onClose = { newSubject = false })
    subjectEditor?.let { id -> SubjectWeekEditor(data.subjects.firstOrNull { it.id == id }, data, store, onClose = { subjectEditor = null }) }
    dayEditor?.let { d -> DayEditor(d, data, store, onEditWeek = { sid -> dayEditor = null; subjectEditor = sid }) { dayEditor = null } }
}

private fun nameOf(data: CampusData, subjectId: Long) = data.subjects.firstOrNull { it.id == subjectId }?.name ?: "?"

internal fun describeChange(ex: ScheduleException, data: CampusData): String {
    val slot = data.slots.firstOrNull { it.id == ex.slotId }
    val subject = data.subjects.firstOrNull { it.id == (ex.subjectId ?: slot?.subjectId) }?.name ?: ""
    val d = runCatching { LocalDate.parse(ex.date).format(dayFmt) }.getOrDefault(ex.date)
    return when (ex.kind) {
        ExceptionKind.HOLIDAY -> "Holiday · $d" + if (ex.note.isNotBlank()) " · ${ex.note}" else ""
        ExceptionKind.CANCEL -> "Cancelled · $subject · $d ${slot?.let { clock(it.start) } ?: ""}"
        ExceptionKind.RESCHEDULE -> "Moved · $subject · $d → " + (ex.newDate?.let { runCatching { LocalDate.parse(it).format(dayFmt) }.getOrDefault(it) } ?: d) + " ${clock(ex.start)}–${clock(ex.end)}"
        ExceptionKind.EXTRA -> "Extra · $subject · $d ${clock(ex.start)}–${clock(ex.end)}" + if (ex.room.isNotBlank()) " · ${ex.room}" else ""
    }
}

/** A visual week: one column per day, classes placed by time, holidays shaded. Tap a day to edit it. */
@Composable
private fun WeekGrid(data: CampusData, weekStart: LocalDate, onDay: (LocalDate) -> Unit) {
    val days = (0L..6L).map { weekStart.plusDays(it) }
    val occ = days.associateWith { AttendanceEngine.occurrences(data, it) }
    val shown = days.filter { d -> d.dayOfWeek.value <= 5 || occ.getValue(d).isNotEmpty() }
    val all = occ.values.flatten()
    val startHour = ((all.minOfOrNull { it.start } ?: (8 * 60)) / 60).coerceAtMost(9)
    val endHour = (((all.maxOfOrNull { it.end } ?: (17 * 60)) + 59) / 60).coerceAtLeast(startHour + 8)
    val hourHeight = 44.dp
    val today = LocalDate.now()
    Card {
        Column(Modifier.padding(8.dp)) {
            Row {
                Spacer(Modifier.width(30.dp))
                shown.forEach { d ->
                    Column(Modifier.weight(1f).clickable { onDay(d) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(shortDay(d.dayOfWeek.value), style = MaterialTheme.typography.labelMedium, fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal, color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(Modifier.height(hourHeight * (endHour - startHour))) {
                Column(Modifier.width(30.dp)) {
                    (startHour until endHour).forEach { h -> Box(Modifier.height(hourHeight)) { Text("%02d".format(h), style = MaterialTheme.typography.labelSmall) } }
                }
                shown.forEach { d ->
                    val holiday = data.exceptions.any { it.kind == ExceptionKind.HOLIDAY && it.date == d.toString() }
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(horizontal = 1.dp)
                            .background(if (holiday) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(4.dp))
                            .clickable { onDay(d) }
                    ) {
                        (startHour until endHour).forEach { h ->
                            HorizontalDivider(Modifier.offset(y = hourHeight * (h - startHour)), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                        if (holiday) Text("Holiday", Modifier.align(Alignment.Center), style = MaterialTheme.typography.labelSmall)
                        occ.getValue(d).forEach { o ->
                            val s = data.subjects.firstOrNull { it.id == o.subjectId } ?: return@forEach
                            val top = hourHeight * ((o.start - startHour * 60) / 60f)
                            val height = (hourHeight * ((o.end - o.start) / 60f)).coerceAtLeast(18.dp)
                            val mark = data.marks[o.key]
                            Surface(
                                color = Color(s.colorHex).copy(alpha = if (mark == Mark.ABSENT) 0.45f else 0.9f),
                                shape = RoundedCornerShape(4.dp),
                                border = if (o.source != "Regular") BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface) else null,
                                modifier = Modifier.fillMaxWidth().offset(y = top).height(height).clickable { onDay(d) }
                            ) {
                                Column(Modifier.padding(2.dp)) {
                                    Text(s.code.ifBlank { s.name }, color = Chronora.colors.onHero, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 11.sp)
                                    if (height > 30.dp) Text(clock(o.start), color = Chronora.colors.onHero.copy(alpha = 0.85f), fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Everything about one subject's week: several slots per day, times, rooms, types. */
@Composable
private fun SubjectWeekEditor(subject: Subject?, data: CampusData, store: CampusStore, onClose: () -> Unit) {
    val today = LocalDate.now()
    val existing = subject?.let { s -> AttendanceEngine.currentSlots(data, today).filter { it.subjectId == s.id } }.orEmpty()
    var name by remember { mutableStateOf(subject?.name ?: "") }
    var code by remember { mutableStateOf(subject?.code ?: "") }
    var faculty by remember { mutableStateOf(subject?.faculty ?: "") }
    var color by remember { mutableLongStateOf(subject?.colorHex ?: AttendanceEngine.palette[data.subjects.size % AttendanceEngine.palette.size]) }
    val drafts = remember { mutableStateListOf<SlotDraft>().apply { addAll(existing.sortedWith(compareBy({ it.day }, { it.start })).map { SlotDraft(it.day, it.start, it.end, it.room, it.type, it.rotationWeek) }) } }
    var fromToday by remember { mutableStateOf(true) }
    var copyFrom by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val semStart = runCatching { LocalDate.parse(data.semester.start) }.getOrDefault(today)
    val others = AttendanceEngine.currentSlots(data, today).filter { it.subjectId != subject?.id }
    val invalid = drafts.any { it.end <= it.start }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            ChronoraTopBar(if (subject == null) "New subject" else "Weekly schedule", onClose) {
                Button(enabled = name.isNotBlank() && !invalid, onClick = {
                            store.update { d ->
                                val sid = subject?.id ?: store.nextId()
                                val updated = (subject ?: Subject(sid, name.trim())).copy(name = name.trim(), code = code.trim(), faculty = faculty.trim(), colorHex = color)
                                val from = if (subject != null && fromToday && today.isAfter(semStart)) today else null
                                d.copy(
                                    subjects = if (subject == null) d.subjects + updated else d.subjects.map { if (it.id == sid) updated else it },
                                    slots = AttendanceEngine.applyWeekSchedule(d.slots, sid, drafts.toList(), from) { store.nextId() }
                                )
                            }
                            onClose()
                        }) { Text("Save") }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(name, { name = it }, label = { Text("Subject name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(code, { code = it }, label = { Text("Code") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(faculty, { faculty = it }, label = { Text("Faculty") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        AttendanceEngine.palette.forEach { c ->
                            Surface(color = Color(c), shape = RoundedCornerShape(50), border = if (c == color) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null, modifier = Modifier.size(26.dp).clickable { color = c }) {}
                        }
                    }
                }
                item {
                    if (subject != null && existing.isNotEmpty() && today.isAfter(semStart)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(fromToday, { fromToday = true }); Text("Apply from today (past attendance stays as it was)") }
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(!fromToday, { fromToday = false }); Text("Correct the whole semester") }
                    }
                }
                (1..7).forEach { day ->
                    val dayDrafts = drafts.withIndex().filter { it.value.day == day }
                    item(key = "day$day") {
                        Card {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(dayName(day), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    if (dayDrafts.isNotEmpty()) TextButton(onClick = { copyFrom = day }) { Text("Copy to…") }
                                    TextButton(onClick = {
                                        val last = dayDrafts.maxByOrNull { it.value.end }?.value
                                        val start = last?.end ?: (9 * 60)
                                        val len = last?.let { it.end - it.start } ?: 60
                                        drafts += SlotDraft(day, start, (start + len).coerceAtMost(24 * 60 - 1), last?.room ?: "", last?.type ?: ClassType.LECTURE)
                                    }) { Icon(Icons.Default.Add, null); Text("Add") }
                                }
                                if (dayDrafts.isEmpty()) Text("No class", style = MaterialTheme.typography.bodySmall)
                                dayDrafts.forEach { (index, slot) ->
                                    SlotRow(slot, others.filter { it.day == day }, data, onChange = { drafts[index] = it }, onDelete = { drafts.removeAt(index) })
                                }
                            }
                        }
                    }
                }
                if (subject != null) item {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete subject", color = MaterialTheme.colorScheme.error) }
                }
            }
            }
        }
    }
    copyFrom?.let { src ->
        var targets by remember { mutableStateOf(setOf<Int>()) }
        AlertDialog(onDismissRequest = { copyFrom = null }, title = { Text("Copy ${dayName(src)}'s classes to") }, text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..7).filter { it != src }.forEach { d -> FilterChip(d in targets, { targets = if (d in targets) targets - d else targets + d }, label = { Text(shortDay(d).take(2)) }) }
                }
                Text("Replaces the classes already on those days.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            Button(onClick = {
                val source = drafts.filter { it.day == src }
                drafts.removeAll { it.day in targets }
                targets.forEach { t -> drafts += source.map { it.copy(day = t) } }
                copyFrom = null
            }) { Text("Copy") }
        }, dismissButton = { TextButton(onClick = { copyFrom = null }) { Text("Cancel") } })
    }
    if (confirmDelete && subject != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${subject.name}?") },
        text = { Text("Removes the subject, its weekly classes and its attendance history.") },
        confirmButton = { Button(onClick = { store.update { d -> d.copy(subjects = d.subjects.filterNot { it.id == subject.id }, slots = d.slots.filterNot { it.subjectId == subject.id }) }; confirmDelete = false; onClose() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}

@Composable
private fun SlotRow(slot: SlotDraft, sameDayOthers: List<TimetableSlot>, data: CampusData, onChange: (SlotDraft) -> Unit, onDelete: () -> Unit) {
    val clash = sameDayOthers.firstOrNull { it.start < slot.end && slot.start < it.end && (it.rotationWeek == 0 || slot.rotationWeek == 0 || it.rotationWeek == slot.rotationWeek) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TimeButton(slot.start, { s -> onChange(slot.copy(start = s, end = (s + (slot.end - slot.start).coerceAtLeast(10)).coerceAtMost(24 * 60 - 1))) })
            Text("–")
            TimeButton(slot.end, { e -> onChange(slot.copy(end = e)) })
            Text(if (slot.end > slot.start) hm(slot.end - slot.start) else "!", style = MaterialTheme.typography.labelSmall, color = if (slot.end > slot.start) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Remove class") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ClassType.values().forEach { t -> FilterChip(slot.type == t, { onChange(slot.copy(type = t)) }, label = { Text(t.label) }) }
        }
        val weeks = data.settings.rotationWeeks
        if (weeks > 1) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(slot.rotationWeek == 0, { onChange(slot.copy(rotationWeek = 0)) }, label = { Text("Every week") })
            (1..weeks).forEach { w -> FilterChip(slot.rotationWeek == w, { onChange(slot.copy(rotationWeek = w)) }, label = { Text("Week " + AttendanceEngine.weekLetter(w)) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(slot.room, { onChange(slot.copy(room = it)) }, label = { Text("Room") }, singleLine = true, modifier = Modifier.weight(1f))
            listOf(50, 60, 120).forEach { len -> AssistChip({ onChange(slot.copy(end = (slot.start + len).coerceAtMost(24 * 60 - 1))) }, label = { Text(hm(len)) }) }
        }
        clash?.let { Text("Clashes with ${nameOf(data, it.subjectId)} ${clock(it.start)}–${clock(it.end)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
        HorizontalDivider()
    }
}

/** One date: cancel, move, re-time or add classes, mark a holiday, undo changes, mark attendance. */
@Composable
private fun DayEditor(date: LocalDate, data: CampusData, store: CampusStore, onEditWeek: (Long) -> Unit, onClose: () -> Unit) {
    val ds = date.toString()
    val occ = AttendanceEngine.occurrences(data, date)
    val holiday = data.exceptions.filter { it.kind == ExceptionKind.HOLIDAY && it.date == ds }
    val removedHere = data.exceptions.filter { (it.kind == ExceptionKind.CANCEL || (it.kind == ExceptionKind.RESCHEDULE && (it.newDate ?: it.date) != ds)) && it.date == ds }
    var editing by remember { mutableStateOf<ClassOccurrence?>(null) }
    var adding by remember { mutableStateOf(false) }
    val now = java.time.LocalDateTime.now()
    val minute = now.hour * 60 + now.minute

    fun exceptionIdOf(o: ClassOccurrence) = o.key.substringAfter("|x", "").toLongOrNull()

    fun cancel(o: ClassOccurrence) = store.update { d ->
        val exId = exceptionIdOf(o)
        val ex = d.exceptions.firstOrNull { it.id == exId }
        val exceptions = when {
            ex == null && o.slotId != null -> d.exceptions + ScheduleException(store.nextId(), ExceptionKind.CANCEL, ds, slotId = o.slotId)
            ex?.kind == ExceptionKind.EXTRA -> d.exceptions - ex
            // A moved class that is then cancelled: cancel it on its original day instead.
            ex?.kind == ExceptionKind.RESCHEDULE -> d.exceptions - ex + ScheduleException(store.nextId(), ExceptionKind.CANCEL, ex.date, slotId = ex.slotId)
            else -> d.exceptions
        }
        d.copy(exceptions = exceptions, marks = d.marks - o.key)
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            ChronoraTopBar(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), onClose)
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                }
                item {
                    SwitchRow("Holiday / no classes this day", holiday.isNotEmpty()) { on ->
                        store.update { d -> d.copy(exceptions = if (on) d.exceptions + ScheduleException(store.nextId(), ExceptionKind.HOLIDAY, ds) else d.exceptions.filterNot { it.kind == ExceptionKind.HOLIDAY && it.date == ds }) }
                    }
                }
                if (occ.isEmpty()) item { Text(if (holiday.isNotEmpty()) "Holiday — no classes." else "No classes this day.") }
                items(occ, key = { it.key }) { o ->
                    val s = data.subjects.firstOrNull { it.id == o.subjectId } ?: return@items
                    Card {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val past = date.isBefore(now.toLocalDate()) || (date == now.toLocalDate() && o.end <= minute)
                            ClassRow(o, s, data.marks[o.key], past) { m -> store.mark(o.key, m) }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { editing = o }) { Text("Change time/room") }
                                TextButton(onClick = { cancel(o) }) { Text("Cancel class", color = MaterialTheme.colorScheme.error) }
                                if (o.source == "Rescheduled") TextButton(onClick = {
                                    val exId = exceptionIdOf(o)
                                    store.update { d -> d.copy(exceptions = d.exceptions.filterNot { it.id == exId }, marks = d.marks - o.key) }
                                }) { Text("Undo change") }
                                TextButton(onClick = { onEditWeek(s.id) }) { Text("Edit week") }
                            }
                        }
                    }
                }
                if (removedHere.isNotEmpty()) item { Text("Cancelled or moved away", style = MaterialTheme.typography.labelLarge) }
                items(removedHere, key = { "r${it.id}" }) { ex ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(describeChange(ex, data), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textDecoration = if (ex.kind == ExceptionKind.CANCEL) TextDecoration.LineThrough else null)
                        TextButton(onClick = { store.update { d -> d.copy(exceptions = d.exceptions.filterNot { it.id == ex.id }) } }) { Text("Undo") }
                    }
                }
                item {
                    Button(onClick = { adding = true }, enabled = data.subjects.isNotEmpty()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Extra class this day") }
                }
            }
            }
        }
    }

    editing?.let { o ->
        ClassEditDialog(
            title = "Change ${nameOf(data, o.subjectId)} on ${date.format(dayFmt)}",
            data = data, subjectId = o.subjectId, lockSubject = true,
            date = date, start = o.start, end = o.end, room = o.room, type = o.type,
            onSave = { newDate, _, st, en, room, type ->
                store.update { d ->
                    val exId = exceptionIdOf(o)
                    val ex = d.exceptions.firstOrNull { it.id == exId }
                    val exceptions = when {
                        ex == null && o.slotId != null -> d.exceptions + ScheduleException(store.nextId(), ExceptionKind.RESCHEDULE, ds, slotId = o.slotId, newDate = newDate.toString(), start = st, end = en, room = room, type = type)
                        ex?.kind == ExceptionKind.EXTRA -> d.exceptions.map { if (it.id == ex.id) it.copy(date = newDate.toString(), start = st, end = en, room = room, type = type) else it }
                        ex?.kind == ExceptionKind.RESCHEDULE -> d.exceptions.map { if (it.id == ex.id) it.copy(newDate = newDate.toString(), start = st, end = en, room = room) else it }
                        else -> d.exceptions
                    }
                    d.copy(exceptions = exceptions, marks = d.marks - o.key)
                }
                editing = null
            },
            onClose = { editing = null }
        )
    }
    if (adding) ClassEditDialog(
        title = "Extra class", data = data, subjectId = data.subjects.firstOrNull()?.id, lockSubject = false,
        date = date, start = 17 * 60, end = 18 * 60, room = "", type = ClassType.LECTURE,
        onSave = { newDate, sid, st, en, room, type ->
            if (sid != null) store.update { d -> d.copy(exceptions = d.exceptions + ScheduleException(store.nextId(), ExceptionKind.EXTRA, newDate.toString(), subjectId = sid, start = st, end = en, room = room, type = type)) }
            adding = false
        },
        onClose = { adding = false }
    )
}

@Composable
private fun ClassEditDialog(
    title: String, data: CampusData, subjectId: Long?, lockSubject: Boolean,
    date: LocalDate, start: Int, end: Int, room: String, type: ClassType,
    onSave: (LocalDate, Long?, Int, Int, String, ClassType) -> Unit, onClose: () -> Unit
) {
    var sid by remember { mutableStateOf(subjectId) }
    var d by remember { mutableStateOf(date) }
    var st by remember { mutableIntStateOf(start) }
    var en by remember { mutableIntStateOf(end) }
    var r by remember { mutableStateOf(room) }
    var t by remember { mutableStateOf(type) }
    AlertDialog(onDismissRequest = onClose, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!lockSubject) LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(data.subjects, key = { it.id }) { s -> FilterChip(sid == s.id, { sid = s.id }, label = { Text(s.name) }) }
            }
            DateButton("On", d) { d = it }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TimeButton(st, { v -> en = (v + (en - st).coerceAtLeast(10)).coerceAtMost(24 * 60 - 1); st = v })
                Text("–")
                TimeButton(en, { en = it })
                Text(if (en > st) hm(en - st) else "!", style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { ClassType.values().forEach { ct -> FilterChip(t == ct, { t = ct }, label = { Text(ct.label) }) } }
            OutlinedTextField(r, { r = it }, label = { Text("Room") }, singleLine = true)
        }
    }, confirmButton = {
        Button(enabled = en > st && sid != null, onClick = { onSave(d, sid, st, en, r.trim(), t) }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}
