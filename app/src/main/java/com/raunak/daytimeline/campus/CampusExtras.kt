package com.raunak.daytimeline.campus

import com.raunak.daytimeline.ui.*

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.pro.UsageAccess
import com.raunak.daytimeline.wellbeing.UsageEventType
import com.raunak.daytimeline.wellbeing.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Shares a per-subject summary plus every marked class as CSV. */
internal fun shareAttendance(context: Context, data: CampusData, stats: List<SubjectAttendance>) {
    fun cell(v: Any?) = "\"" + v.toString().replace("\"", "\"\"") + "\""
    val csv = buildString {
        append("Subject,Code,Attended,Held,Percent,Required,Safe to skip,Must attend\n")
        stats.forEach { s -> append(listOf(s.subject.name, s.subject.code, s.attended, s.conducted, "%.1f".format(s.percent), s.subject.requiredPercent, s.safeToSkip, s.mustAttend).joinToString(",") { cell(it) }).append('\n') }
        append("\nDate,Time,Subject,Type,Room,Mark\n")
        val start = runCatching { LocalDate.parse(data.semester.start) }.getOrDefault(LocalDate.now().minusMonths(4))
        AttendanceEngine.occurrencesBetween(data, start, LocalDate.now()).forEach { o ->
            val mark = data.marks[o.key] ?: return@forEach
            append(listOf(o.date, "${clock(o.start)}-${clock(o.end)}", data.subjects.firstOrNull { it.id == o.subjectId }?.name, o.type.label, o.room, mark.label).joinToString(",") { cell(it) }).append('\n')
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Attendance — ${data.semester.name}")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share attendance").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Type a deadline in plain words and add it. */
@Composable
internal fun QuickDeadlineCard(data: CampusData, store: CampusStore) {
    val today = LocalDate.now()
    var quick by remember { mutableStateOf("") }
    val preview = remember(quick, data.settings.deadlineKinds, data.subjects) { StudyEngines.parseDeadline(quick, today, data.settings.deadlineKinds, data.subjects, 0) }
    SectionCard("Quick add", "e.g. \"ML assignment due fri 11pm\", \"CN quiz next monday 10am\"") {
        OutlinedTextField(quick, { quick = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Type a deadline") })
        preview?.takeIf { quick.isNotBlank() }?.let { p ->
            Text("${p.label} · ${p.title} · ${p.date} ${clock(p.minute)}" + (p.subjectId?.let { id -> " · " + (data.subjects.firstOrNull { it.id == id }?.name ?: "") } ?: ""), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { store.update { d -> d.copy(deadlines = d.deadlines + p.copy(id = store.nextId())) }; quick = "" }) { Text("Add") }
        }
    }
}

/** Sleep estimated from when the screen went off at night and the first unlock in the morning. */
@Composable
internal fun SleepCard(data: CampusData, store: CampusStore) {
    val context = LocalContext.current
    val goal = data.settings.sleepGoalMinutes
    LaunchedEffect(Unit) {
        val entries = withContext(Dispatchers.IO) {
            if (!UsageAccess.granted(context)) return@withContext emptyList<SleepEntry>()
            val today = LocalDate.now()
            val from = today.minusDays(8).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val events = UsageRepository.events(context, from, System.currentTimeMillis())
            val offs = events.filter { it.type == UsageEventType.SCREEN_OFF }.map { it.time }
            val ons = events.filter { it.type == UsageEventType.UNLOCK }.map { it.time }
            (0L..6L).mapNotNull { StudyEngines.sleepFor(today.minusDays(it), offs, ons) }
        }
        if (entries.isNotEmpty() && entries.any { e -> data.sleepLog.none { it == e } }) {
            store.update { d -> d.copy(sleepLog = (d.sleepLog.filterNot { e -> entries.any { it.date == e.date } } + entries).sortedBy { it.date }.takeLast(120)) }
        }
    }
    val nights = data.sleepLog.takeLast(7).reversed()
    val next = CampusScheduler.nextWake(data, LocalDateTime.now())
    SectionCard("Sleep", if (nights.isEmpty()) "Needs usage access (More → Screen time)" else "Average ${hm(nights.map { it.minutes }.average().toInt())} · goal ${hm(goal)}") {
        next?.let { Text("For ${hm(goal)} before your ${it.at.toLocalTime().withSecond(0)} alarm, be asleep by ${it.at.minusMinutes(goal.toLong()).toLocalTime().withSecond(0)}.", fontWeight = FontWeight.SemiBold) }
        nights.forEach { n ->
            Text("${n.date} · ${StudyEngines.clockOf(n.sleptAt)} → ${StudyEngines.clockOf(n.wokeAt)} · ${hm(n.minutes)}", style = MaterialTheme.typography.bodySmall, color = if (n.minutes >= goal) Chronora.colors.good else Chronora.colors.bad)
        }
        Stepper("Sleep goal", hm(goal), { store.update { it.copy(settings = it.settings.copy(sleepGoalMinutes = (goal - 15).coerceAtLeast(240))) } }, { store.update { it.copy(settings = it.settings.copy(sleepGoalMinutes = (goal + 15).coerceAtMost(720))) } })
    }
}

/** Internal marks per subject → weighted percentage, predicted grade and what you need for the next grade. */
@Composable
internal fun MarksCard(data: CampusData, store: CampusStore) {
    val confirm = com.raunak.daytimeline.ui.rememberConfirm()
    var adding by remember { mutableStateOf<Long?>(null) }
    SectionCard("Internal marks", "Minors, mid-sems, quizzes, assignments → predicted grade") {
        if (data.subjects.isEmpty()) Text("Add subjects in the Timetable tab first.", style = MaterialTheme.typography.bodySmall)
        data.subjects.forEach { s ->
            val list = data.assessments.filter { it.subjectId == s.id }
            val sum = StudyEngines.marks(s.id, data.assessments, data.settings.gradeCutoffs)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontWeight = FontWeight.SemiBold)
                    Text(sum?.let { m ->
                        (m.projectedPercent?.let { "%.1f".format(it) + "% so far" } ?: "No marks yet") + " · ${"%.0f".format(m.weightDone)}% of grade done" +
                            (m.predictedGrade?.let { " · predicted $it" } ?: "") +
                            (m.neededForNext?.let { (g, p) -> " · ${"%.0f".format(p)}% needed in the rest for $g" } ?: "")
                    } ?: "No assessments yet", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { adding = s.id }) { Text("Add") }
            }
            list.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("  ${a.name} · ${a.obtained?.let { fmtNum(it) } ?: "—"}/${fmtNum(a.maxMarks)} · weight ${fmtNum(a.weightPercent)}%", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { confirm.ask("this assessment") { store.update { d -> d.copy(assessments = d.assessments.filterNot { it.id == a.id }) } } }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        }
        Text("Grade cut-offs are editable in Campus → Settings.", style = MaterialTheme.typography.labelSmall)
    }
    adding?.let { sid ->
        var name by remember { mutableStateOf("") }
        var max by remember { mutableStateOf("") }
        var got by remember { mutableStateOf("") }
        var weight by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { adding = null }, title = { Text("Add assessment") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (Minor 1, Mid-sem…)") }, singleLine = true)
                OutlinedTextField(max, { max = it }, label = { Text("Out of") }, singleLine = true)
                OutlinedTextField(got, { got = it }, label = { Text("Marks obtained (blank if not out yet)") }, singleLine = true)
                OutlinedTextField(weight, { weight = it }, label = { Text("Weight in final grade (%)") }, singleLine = true)
            }
        }, confirmButton = {
            Button(enabled = name.isNotBlank() && max.toDoubleOrNull() != null && weight.toDoubleOrNull() != null, onClick = {
                store.update { d -> d.copy(assessments = d.assessments + Assessment(store.nextId(), sid, name.trim(), max.toDouble(), got.toDoubleOrNull(), weight.toDouble())) }
                adding = null
            }) { Text("Save") }
        }, dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } })
    }
}

private fun fmtNum(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
