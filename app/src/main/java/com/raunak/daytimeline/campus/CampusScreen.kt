package com.raunak.daytimeline.campus

import com.raunak.daytimeline.ui.*

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.AppContainer
import com.raunak.daytimeline.data.TaskEntity
import com.raunak.daytimeline.pro.FocusGarden
import com.raunak.daytimeline.pro.FocusGuardStore
import com.raunak.daytimeline.pro.GardenStore
import com.raunak.daytimeline.wellbeing.UsageRepository
import com.raunak.daytimeline.wellbeing.WellbeingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Lets other parts of the app (e.g. a notification) open a Campus tab by name. */
object CampusNav {
    private val _requested = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val requested: kotlinx.coroutines.flow.StateFlow<String?> = _requested
    fun open(tab: String) { _requested.value = tab }
    fun consume() { _requested.value = null }
}

/** The student home: classes, attendance, study sheets, wake-up, library, exams, CGPA, placements. */
@Composable
fun CampusScreen(modifier: Modifier = Modifier) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    val tabs = CampusSections.map { it.first }
    val requested by CampusNav.requested.collectAsStateWithLifecycle()
    LaunchedEffect(requested) { requested?.let { name -> tabs.indexOf(name).takeIf { it >= 0 }?.let { tab = it }; CampusNav.consume() } }
    androidx.activity.compose.BackHandler(enabled = tab != 0) { tab = 0 }
    Column(modifier.fillMaxSize()) {
        if (tab == 0) {
            // Sections as a row of icon shortcuts instead of twelve text tabs.
            androidx.compose.foundation.lazy.LazyRow(Modifier.testTag("campusNav"), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(CampusSections.size - 1) { j ->
                    val (label, icon) = CampusSections[j + 1]
                    Column(Modifier.width(72.dp).clip(RoundedCornerShape(16.dp)).clickable { tab = j + 1 }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { tab = 0 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Campus") }
                Icon(CampusSections[tab].second, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("  " + CampusSections[tab].first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        when (tab) {
            0 -> TodayTab { tab = it }
            1 -> com.raunak.daytimeline.classroom.ClassroomTab()
            2 -> AttendanceTab()
            3 -> TimetableTab()
            4 -> SheetsTab()
            5 -> DeadlinesTab()
            6 -> WakeTab()
            7 -> LibraryTab()
            8 -> CgpaTab()
            9 -> PlacementTab()
            10 -> DisciplineGate()
            11 -> CampusSettingsTab()
        }
    }
}

private val CampusSections = listOf(
    "Today" to Icons.Default.Today, "Classroom" to Icons.Default.Class, "Attendance" to Icons.Default.HowToReg,
    "Timetable" to Icons.Default.TableChart, "Sheets" to Icons.Default.FactCheck, "Exams & tasks" to Icons.Default.Assignment,
    "Wake-up" to Icons.Default.WbSunny, "Library" to Icons.Default.LocalLibrary, "CGPA" to Icons.Default.Grade,
    "Placements" to Icons.Default.Work, "Discipline" to Icons.Default.SelfImprovement, "Settings" to Icons.Default.Settings
)

// ------------------------------------------------------------------ shared UI





internal fun parseClock(text: String): Int? {
    val t = text.trim().lowercase().replace(".", ":")
    val m = Regex("""^(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$""").find(t) ?: return null
    var h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toIntOrNull() ?: 0
    if (m.groupValues[3] == "pm" && h in 1..11) h += 12
    if (m.groupValues[3] == "am" && h == 12) h = 0
    if (h !in 0..23 || min !in 0..59) return null
    return h * 60 + min
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateButton(label: String, date: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("$label ${date.format(DateTimeFormatter.ofPattern("EEE d MMM"))}") }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / 86_400_000L)) }; open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }
}

internal fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
internal fun percentColor(p: Double, required: Int, margin: Int = 5): Color = when {
    p >= required + margin -> Chronora.colors.good
    p >= required -> Chronora.colors.warn
    else -> Chronora.colors.bad
}

// ------------------------------------------------------------------ Today

@Composable
private fun TodayTab(goTo: (Int) -> Unit) {
    val context = LocalContext.current
    val store = remember { CampusStore.get(context) }
    val data by store.data.collectAsStateWithLifecycle()
    val sheets by store.sheets.collectAsStateWithLifecycle()
    val discipline by DisciplineStore.get(context).state.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var screenMinutes by remember { mutableStateOf<Int?>(null) }
    var planned by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { CampusScheduler.rescheduleAll(context); while (true) { now = LocalDateTime.now(); delay(30_000) } }
    LaunchedEffect(Unit) { screenMinutes = withContext(Dispatchers.IO) { runCatching { if (com.raunak.daytimeline.pro.UsageAccess.granted(context)) UsageRepository.todayTotals(context).second else null }.getOrNull() } }

    val today = now.toLocalDate()
    val minute = now.hour * 60 + now.minute
    val classes = AttendanceEngine.occurrences(data, today)
    val subjectOf = data.subjects.associateBy { it.id }
    val gardenMinutes = remember(now) { FocusGarden.summarize(GardenStore(context).sessions(), today).todayMinutes }
    val libraryMinutes = LibraryPlanner.minutesIn(data.librarySessions, today)
    val dsaSheets = sheets.filter { it.kind == SheetKind.DSA }
    val solvedToday = SheetEngine.doneOn(sheets, today)
    val target = dsaSheets.sumOf { it.dailyTarget }.coerceAtLeast(if (sheets.isEmpty()) 0 else 2)
    val wakeLog = data.wakeLogs.lastOrNull { it.date == today.toString() }
    val parts = DailyScore.compute(
        settings = data.settings,
        wakeOnTime = wakeLog?.dismissedAt?.let { it <= wakeLog.target + data.settings.onTimeToleranceMinutes * 60_000L },
        classesAttended = classes.count { data.marks[it.key]?.attended == true },
        classesTotal = classes.count { data.marks[it.key]?.counts != false && it.end <= minute },
        studyMinutes = maxOf(gardenMinutes, libraryMinutes, StudyEngines.minutesOn(data.studySessions, today)),
        studyGoal = data.studyGoalMinutes,
        problemsSolved = solvedToday,
        problemTarget = target,
        screenMinutes = screenMinutes,
        screenGoal = WellbeingStore(context).config.screenTimeGoalMinutes,
        disciplineKept = DisciplineEngine.keptToday(discipline, today)
    )
    val score = DailyScore.total(parts)
    // Keep today's score so trends and weekly reviews have history.
    LaunchedEffect(score, today) {
        val entry = ScoreEntry(today.toString(), score, parts.associate { it.label to it.earned.toInt() })
        if (data.scoreHistory.lastOrNull { it.date == entry.date } != entry) {
            store.update { d -> d.copy(scoreHistory = (d.scoreHistory.filterNot { it.date == entry.date } + entry).takeLast(400)) }
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (data.subjects.isEmpty()) item {
            SectionCard("Set up your semester", "Add your weekly timetable once — attendance, alarms, reminders and library plans follow from it.") {
                Button(onClick = { goTo(3) }) { Text("Add timetable") }
            }
        }
        item {
            HeroCard {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Today's score", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelMedium)
                            Text("$score / 100", color = Chronora.colors.onHero, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(data.settings.grade(score), color = Chronora.colors.heroAccent, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    }
                    parts.forEach { p ->
                        Row { Text(p.label, color = Chronora.colors.heroMuted, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text("${p.detail}  ${p.earned.toInt()}/${p.max}", color = Chronora.colors.onHero, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        item {
            val next = classes.firstOrNull { it.end > minute }
            SectionCard(
                if (classes.isEmpty()) "No classes today" else "Classes · ${classes.size}",
                next?.let { o -> if (o.start > minute) "Next: ${subjectOf[o.subjectId]?.name} in ${hm(o.start - minute)}" + (o.room.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") else "Now: ${subjectOf[o.subjectId]?.name} until ${clock(o.end)}" }
            ) {
                classes.forEach { o ->
                    val s = subjectOf[o.subjectId] ?: return@forEach
                    ClassRow(o, s, data.marks[o.key], past = o.end <= minute) { m -> store.mark(o.key, m) }
                }
            }
        }
        item { com.raunak.daytimeline.classroom.ClassroomTodayCard { goTo(1) } }
        val meals = Mess.meals(data, today)
        if (meals.isNotEmpty()) item {
            val missed = Mess.clashes(data, today)
            SectionCard("Mess", Mess.status(data, now)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    meals.forEach { m ->
                        val open = minute in m.start until m.end
                        val over = minute >= m.end
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.SemiBold, color = if (open) Chronora.colors.good else if (over) Chronora.muted else MaterialTheme.colorScheme.onSurface)
                            Text("${clock(m.start)}–${clock(m.end)}", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                        }
                    }
                }
                if (missed.isNotEmpty()) Text("Classes cover the whole ${missed.joinToString(" and ") { it.name.lowercase() }} window today — carry something to eat.", color = Chronora.colors.warn, style = MaterialTheme.typography.bodySmall)
            }
        }
        val pending = AttendanceEngine.unmarked(data, today, minute).filter { it.date != today }
        if (pending.isNotEmpty()) item {
            SectionCard("Unmarked classes · ${pending.size}", "Mark them so your percentages stay right") {
                pending.take(5).forEach { o -> subjectOf[o.subjectId]?.let { s -> ClassRow(o, s, null, past = true, showDate = true) { m -> store.mark(o.key, m) } } }
                if (pending.size > 5) TextButton(onClick = { goTo(2) }) { Text("See all") }
            }
        }
        item {
            val windows = LibraryPlanner.freeWindows(data, today, fromMinute = minute)
            SectionCard("Library", LibraryPlanner.status(data.library, now)) {
                if (windows.isEmpty()) Text("No free library slots left today.", style = MaterialTheme.typography.bodySmall)
                else Text("Free: " + windows.joinToString { "${clock(it.start)}–${clock(it.end)}" } + " · ${hm(windows.sumOf { it.minutes })}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = windows.isNotEmpty(), onClick = {
                        val topics = studyTopics(data, sheets, today)
                        val blocks = LibraryPlanner.planBlocks(windows, topics, data.settings.focusBlockMinutes, data.settings.breakMinutes, data.settings.splitLibraryIntoBlocks)
                        scope.launch(Dispatchers.IO) {
                            val repo = AppContainer(context).repository
                            blocks.forEach { b -> repo.addTask(TaskEntity(title = "Library · ${b.title}", dateEpochDay = today.toEpochDay(), startMinute = b.start, endMinute = b.end, category = "Study", pomodoroEnabled = true, tags = "library,deep")) }
                        }
                        planned = "Added ${blocks.size} study blocks to today's timeline"
                    }) { Text("Plan my library time") }
                    OutlinedButton(onClick = { goTo(7) }) { Text("Check in") }
                }
                if (planned.isNotBlank()) Text(planned, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            val review = SheetEngine.reviewQueue(sheets, today)
            val nextItem = dsaSheets.firstNotNullOfOrNull { s -> SheetEngine.next(s)?.let { s to it } }
            SectionCard("Practice", "$solvedToday/$target solved today · ${review.size} due for revision") {
                LinearProgressIndicator(progress = { if (target == 0) 0f else (solvedToday.toFloat() / target).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth())
                nextItem?.let { (s, item) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Next: ${item.title}", fontWeight = FontWeight.SemiBold); Text("${item.section} · ${item.difficulty?.label ?: ""}", style = MaterialTheme.typography.bodySmall) }
                        if (item.url.isNotBlank()) TextButton(onClick = { openUrl(context, item.url) }) { Text("Open") }
                        TextButton(onClick = { store.updateItem(s.id, item.id) { SheetEngine.setStatus(it, ItemStatus.SOLVED, today, store.gaps) } }) { Text("Solved") }
                    }
                }
                if (sheets.isEmpty()) TextButton(onClick = { goTo(4) }) { Text("Add the DSA sheet") }
                sheets.filter { it.examDate != null }.forEach { s ->
                    val exam = runCatching { LocalDate.parse(s.examDate) }.getOrNull() ?: return@forEach
                    if (exam.isBefore(today)) return@forEach
                    val todays = StudyEngines.revisionPlan(s.items, exam, today, data.settings.examBufferDays)[today].orEmpty()
                    if (todays.isNotEmpty()) {
                        Text("${s.name} · exam in ${daysUntil(s.examDate!!, today)} days — today:", style = MaterialTheme.typography.labelLarge)
                        todays.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(false, { store.updateItem(s.id, item.id) { SheetEngine.setStatus(it, ItemStatus.SOLVED, today, store.gaps) } })
                                Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        val upcoming = data.deadlines.filter { !it.done && (daysUntil(it.date, today) ?: 99) in 0..7 }.sortedBy { it.date + clock(it.minute) }
        if (upcoming.isNotEmpty()) item {
            SectionCard("Due this week") {
                upcoming.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(false, { store.update { c -> c.copy(deadlines = c.deadlines.map { if (it.id == d.id) it.copy(done = true) else it }) }; com.raunak.daytimeline.classroom.ClassroomSync.deadlineDone(context, d.notes) })
                        Column(Modifier.weight(1f)) {
                            Text(d.title, fontWeight = FontWeight.SemiBold)
                            Text("${d.label} · ${dueLabel(d, today)}" + (d.subjectId?.let { " · " + (subjectOf[it]?.name ?: "") } ?: ""), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            val plan = CampusScheduler.nextWake(data, now)
            SectionCard("Wake-up", plan?.let { "Alarm ${if (it.at.toLocalDate() == today) "today" else it.at.dayOfWeek.name.lowercase().replaceFirstChar { c -> c.uppercase() }} ${it.at.toLocalTime().withSecond(0)} · ${it.label}" } ?: "No wake alarm set") {
                val logs = data.wakeLogs.takeLast(14)
                if (logs.isNotEmpty()) {
                    val onTime = logs.count { it.dismissedAt != null && it.dismissedAt <= it.target + data.settings.onTimeToleranceMinutes * 60_000L }
                    Text("On time $onTime of the last ${logs.size} mornings", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { goTo(6) }) { Text("Wake-up settings & readiness") }
            }
        }
        item { StudyTimerCard(data, store) }
        item { WeeklyReviewCard(data, sheets, today) }
        item {
            SectionCard("Lock in", "Block distracting apps right now") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.settings.lockInMinutes.forEach { m ->
                        OutlinedButton(onClick = { FocusGuardStore(context).update { it.copy(sessionUntil = System.currentTimeMillis() + m * 60_000L) } }) { Text(hm(m)) }
                    }
                }
                Text("Uses your App blocker list.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

internal fun dueLabel(d: Deadline, today: LocalDate): String = when (val n = daysUntil(d.date, today)) {
    null -> d.date
    0L -> "today ${clock(d.minute)}"
    1L -> "tomorrow ${clock(d.minute)}"
    else -> if (n < 0) "overdue" else "in $n days"
}

/** Topics for library blocks: DSA first, then subjects with the nearest exams/deadlines, then revision. */
internal fun studyTopics(data: CampusData, sheets: List<StudySheet>, today: LocalDate): List<String> {
    val out = mutableListOf<String>()
    if (sheets.any { it.kind == SheetKind.DSA }) out += "DSA practice"
    data.deadlines.filter { !it.done && (daysUntil(it.date, today) ?: 99) in 0..10 }.sortedBy { it.date }.forEach { d ->
        out += (d.subjectId?.let { id -> data.subjects.firstOrNull { it.id == id }?.name } ?: d.title) + " · ${d.label}"
    }
    if (SheetEngine.reviewQueue(sheets, today).isNotEmpty()) out += "Revision queue"
    sheets.filter { it.kind == SheetKind.SUBJECT }.forEach { out += it.name }
    if (out.size < 2) out += data.subjects.map { "${it.name} revision" }
    return out.distinct().ifEmpty { listOf("Deep work") }
}

@Composable
internal fun ClassRow(o: ClassOccurrence, s: Subject, mark: Mark?, past: Boolean, showDate: Boolean = false, onMark: (Mark?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Color(s.colorHex), shape = RoundedCornerShape(4.dp), modifier = Modifier.width(4.dp).height(36.dp)) {}
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name + if (o.type != ClassType.LECTURE) " (${o.type.label})" else "", fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(if (showDate) o.date.format(DateTimeFormatter.ofPattern("EEE d MMM")) else null, "${clock(o.start)}–${clock(o.end)}", o.room.ifBlank { null }, o.source.takeIf { it != "Regular" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (past || mark != null) {
            listOf(Mark.PRESENT to "P", Mark.ABSENT to "A", Mark.NO_CLASS to "–").forEach { (m, short) ->
                FilterChip(mark == m, { onMark(if (mark == m) null else m) }, label = { Text(short) }, modifier = Modifier.padding(start = 2.dp))
            }
        } else Text("upcoming", style = MaterialTheme.typography.labelSmall)
    }
}

internal fun nowMinute() = LocalTime.now().let { it.hour * 60 + it.minute }

/** Start/stop a study timer on a subject or topic; shows today's total and this week per subject. */
@Composable
private fun StudyTimerCard(data: CampusData, store: CampusStore) {
    val running = data.studySessions.lastOrNull()?.takeIf { it.end == null }
    var subjectId by remember { mutableStateOf<Long?>(null) }
    var topic by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) { while (running != null) { delay(30_000); tick++ } }
    val today = LocalDate.now()
    val now = System.currentTimeMillis() + tick * 0
    SectionCard("Study timer", "Today ${hm(StudyEngines.minutesOn(data.studySessions, today, now = now))}") {
        if (running != null) {
            val name = running.subjectId?.let { id -> data.subjects.firstOrNull { it.id == id }?.name } ?: running.topic.ifBlank { "Study" }
            Text("$name · ${hm(StudyEngines.minutes(running, now))}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { store.update { d -> d.copy(studySessions = d.studySessions.dropLast(1) + running.copy(end = System.currentTimeMillis())) } }) { Text("Stop") }
        } else {
            if (data.subjects.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(data.subjects, key = { it.id }) { s -> FilterChip(subjectId == s.id, { subjectId = if (subjectId == s.id) null else s.id }, label = { Text(s.name) }) }
            }
            OutlinedTextField(topic, { topic = it }, label = { Text("Topic (optional): DSA, project, revision…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { store.update { d -> d.copy(studySessions = (d.studySessions + StudySession(System.currentTimeMillis(), null, subjectId, topic.trim())).takeLast(3000)) } }) { Text("Start") }
        }
        val week = StudyEngines.bySubject(data.studySessions, data.subjects, today.minusDays(6), today, now = now)
        if (week.isNotEmpty()) Text("This week: " + week.joinToString { "${it.first} ${hm(it.second)}" }, style = MaterialTheme.typography.bodySmall)
    }
}

/** Last 7 days at a glance: score trend, study hours, problems, wake-ups and sleep. */
@Composable
private fun WeeklyReviewCard(data: CampusData, sheets: List<StudySheet>, today: LocalDate) {
    val w = StudyEngines.week(data, sheets, today)
    val days = (6L downTo 0L).map { today.minusDays(it) }
    val scores = days.map { d -> data.scoreHistory.firstOrNull { it.date == d.toString() }?.score }
    SectionCard("This week", "Average score ${w.avgScore ?: "—"}" + (w.bestDay?.let { " · best day ${LocalDate.parse(it).dayOfWeek.name.take(3).lowercase()}" } ?: "")) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEachIndexed { i, d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    val v = scores[i] ?: 0
                    Text(scores[i]?.toString() ?: "–", style = MaterialTheme.typography.labelSmall)
                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = if (d == today) 1f else 0.55f), shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp), modifier = Modifier.fillMaxWidth().height((36 * v / 100).coerceAtLeast(2).dp)) {}
                    Text(d.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            "Studied ${hm(w.studyMinutes)} · solved ${w.problems} · woke on time ${w.onTimeWakes}/${w.wakeDays}" + (w.avgSleep?.let { " · slept ${hm(it)} a night" } ?: ""),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
