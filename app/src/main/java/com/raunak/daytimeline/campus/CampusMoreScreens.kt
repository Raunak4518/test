package com.raunak.daytimeline.campus

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.alarm.AlarmMissionType
import com.raunak.daytimeline.pro.FocusGuardStore
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

// ------------------------------------------------------------------ Exams & tasks

@Composable
internal fun DeadlinesTab() {
    val context = LocalContext.current
    val store = remember { CampusStore.get(context) }
    val data by store.data.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val open = data.deadlines.filter { !it.done }.sortedBy { it.date + clock(it.minute) }
    val exams = open.filter { data.settings.isExam(it.label) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (exams.isNotEmpty()) item {
            SectionCard("Exam countdown") {
                exams.take(6).forEach { e ->
                    Row { Text("${e.title}" + (e.subjectId?.let { id -> " · " + (data.subjects.firstOrNull { it.id == id }?.name ?: "") } ?: ""), Modifier.weight(1f)); Text("${daysUntil(e.date, today)} days", fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            SectionCard("Assignments, quizzes & exams", "${open.size} open · reminders " + data.settings.deadlineReminderHours.joinToString { "${it}h" } + " before", action = { TextButton(onClick = { adding = true }) { Text("Add") } }) {
                open.forEach { d -> DeadlineRow(d, data, today, store) }
                if (open.isEmpty()) Text("Nothing pending.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showDone = !showDone }) { Text(if (showDone) "Hide completed" else "Show completed") }
                if (showDone) data.deadlines.filter { it.done }.sortedByDescending { it.date }.take(30).forEach { d -> DeadlineRow(d, data, today, store) }
            }
        }
    }
    if (adding) DeadlineDialog(data, store) { adding = false }
}

@Composable
private fun DeadlineRow(d: Deadline, data: CampusData, today: LocalDate, store: CampusStore) {
    val overdue = !d.done && (daysUntil(d.date, today) ?: 0) < 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(d.done, { on -> store.update { c -> c.copy(deadlines = c.deadlines.map { if (it.id == d.id) it.copy(done = on) else it }) } })
        Column(Modifier.weight(1f)) {
            Text(d.title, fontWeight = FontWeight.SemiBold)
            Text("${d.label} · ${dueLabel(d, today)} · ${d.date}" + (d.subjectId?.let { id -> " · " + (data.subjects.firstOrNull { it.id == id }?.name ?: "") } ?: ""), style = MaterialTheme.typography.bodySmall, color = if (overdue) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { store.update { c -> c.copy(deadlines = c.deadlines.filterNot { it.id == d.id }) } }) { Icon(Icons.Default.Delete, "Delete") }
    }
}

@Composable
private fun DeadlineDialog(data: CampusData, store: CampusStore, close: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(data.settings.deadlineKinds.first()) }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(3)) }
    var time by remember { mutableStateOf("23:59") }
    var subjectId by remember { mutableStateOf<Long?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text("Add deadline or exam") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(data.settings.deadlineKinds) { k -> FilterChip(kind == k, { kind = k; if (title.isBlank() && data.settings.isExam(k)) title = k }, label = { Text(k) }) } }
            Text("Edit these types in Campus → Settings.", style = MaterialTheme.typography.labelSmall)
            if (data.subjects.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(data.subjects) { s -> FilterChip(subjectId == s.id, { subjectId = if (subjectId == s.id) null else s.id }, label = { Text(s.name) }) } }
            DateButton("Due", date) { date = it }
            OutlinedTextField(time, { time = it }, label = { Text("Time") }, singleLine = true, isError = parseClock(time) == null)
        }
    }, confirmButton = {
        Button(enabled = title.isNotBlank() && parseClock(time) != null, onClick = {
            store.update { d -> d.copy(deadlines = d.deadlines + Deadline(store.nextId(), title.trim(), DeadlineKind.OTHER, date.toString(), parseClock(time)!!, subjectId, kindLabel = kind)) }
            close()
        }) { Text("Add") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

// ------------------------------------------------------------------ Wake-up

@Composable
internal fun WakeTab() {
    val context = LocalContext.current
    val store = remember { CampusStore.get(context) }
    val data by store.data.collectAsStateWithLifecycle()
    val w = data.wake
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }
    fun save(next: WakeConfig) = store.update { it.copy(wake = next) }
    val plan = CampusScheduler.nextWake(data, LocalDateTime.now())
    val stepPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { CampusScheduler.rescheduleAll(context) }
    val armed = remember(data.wake.missions, tick) { CampusScheduler.usableMissions(context, w.missions.mapNotNull { runCatching { AlarmMissionType.valueOf(it) }.getOrNull() }) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("Next wake-up", plan?.let { "${it.at.toLocalDate()} at ${it.at.toLocalTime().withSecond(0)}" } ?: "Off") {
                plan?.let { Text(it.label, fontWeight = FontWeight.SemiBold) }
                SwitchRow("Automatic alarm before my first class", w.enabled) { save(w.copy(enabled = it)) }
                Text("Changes every day with your timetable, holidays, cancellations and extra classes.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { ReadinessCard(tick) }
        item {
            SectionCard("Alarm") {
                Stepper("Minutes before first class", hm(w.minutesBeforeFirstClass), { save(w.copy(minutesBeforeFirstClass = (w.minutesBeforeFirstClass - 5).coerceAtLeast(10))) }, { save(w.copy(minutesBeforeFirstClass = w.minutesBeforeFirstClass + 5)) })
                Stepper("Free days (no classes)", w.freeDayWake?.let { clock(it) } ?: "No alarm",
                    { save(w.copy(freeDayWake = w.freeDayWake?.let { if (it <= 5 * 60) null else it - 15 })) },
                    { save(w.copy(freeDayWake = (w.freeDayWake ?: (7 * 60 + 45)) + 15)) })
                Text("Wake-up challenges (all must be done to stop it)", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(listOf(AlarmMissionType.WALK, AlarmMissionType.MATH, AlarmMissionType.SQUAT, AlarmMissionType.SHAKE, AlarmMissionType.TYPING, AlarmMissionType.MEMORY, AlarmMissionType.BARCODE, AlarmMissionType.PHOTO)) { t ->
                        FilterChip(t.name in w.missions, {
                            save(w.copy(missions = if (t.name in w.missions) (w.missions - t.name).ifEmpty { listOf("MATH") } else w.missions + t.name))
                            if (t == AlarmMissionType.WALK && Build.VERSION.SDK_INT >= 29) stepPermission.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                        }, label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                Text("Armed tomorrow: " + armed.joinToString(" → ") { it.name.lowercase() }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("Walk/squat get you out of bed. Barcode/photo need a reference registered for \"First class\" in Alarms (e.g. the bathroom mirror) — until then they're skipped. Holding the dismiss bar still works as a safety net, and the \"still awake?\" check follows.", style = MaterialTheme.typography.bodySmall)
                Stepper("Backup alarm after", if (w.backupMinutes == 0) "Off" else "${w.backupMinutes}m", { save(w.copy(backupMinutes = (w.backupMinutes - 1).coerceAtLeast(0))) }, { save(w.copy(backupMinutes = w.backupMinutes + 1)) })
                Stepper("\"Still awake?\" check after", if (w.wakeCheckMinutes == 0) "Off" else "${w.wakeCheckMinutes}m", { save(w.copy(wakeCheckMinutes = (w.wakeCheckMinutes - 5).coerceAtLeast(0))) }, { save(w.copy(wakeCheckMinutes = w.wakeCheckMinutes + 5)) })
                Stepper("Snoozes allowed", "${data.settings.wakeSnoozes}", { store.update { it.copy(settings = it.settings.copy(wakeSnoozes = (it.settings.wakeSnoozes - 1).coerceAtLeast(0))) } }, { store.update { it.copy(settings = it.settings.copy(wakeSnoozes = (it.settings.wakeSnoozes + 1).coerceAtMost(10))) } })
                Stepper("Snooze length", "${data.settings.wakeSnoozeMinutes}m", { store.update { it.copy(settings = it.settings.copy(wakeSnoozeMinutes = (it.settings.wakeSnoozeMinutes - 1).coerceAtLeast(1))) } }, { store.update { it.copy(settings = it.settings.copy(wakeSnoozeMinutes = (it.settings.wakeSnoozeMinutes + 1).coerceAtMost(60))) } })
                Stepper("Hold to dismiss (safety net)", "${data.settings.wakeHoldToDismissSeconds}s", { store.update { it.copy(settings = it.settings.copy(wakeHoldToDismissSeconds = (it.settings.wakeHoldToDismissSeconds - 1).coerceAtLeast(1))) } }, { store.update { it.copy(settings = it.settings.copy(wakeHoldToDismissSeconds = (it.settings.wakeHoldToDismissSeconds + 1).coerceAtMost(5))) } })
                Stepper("Sleep reminder (hours before)", "${w.sleepHours}h", { save(w.copy(sleepHours = (w.sleepHours - 1).coerceAtLeast(0))) }, { save(w.copy(sleepHours = (w.sleepHours + 1).coerceAtMost(12))) })
            }
        }
        item {
            SectionCard("If the phone might die", "Alarms can't ring on a switched-off phone, so Chronora prevents it and recovers") {
                Stepper("Night battery warning below", "${w.batteryThreshold}%", { save(w.copy(batteryThreshold = (w.batteryThreshold - 5).coerceAtLeast(10))) }, { save(w.copy(batteryThreshold = (w.batteryThreshold + 5).coerceAtMost(90))) })
                val st = data.settings
                Stepper("Check battery every", "${st.batteryCheckEveryMinutes}m", { store.update { it.copy(settings = st.copy(batteryCheckEveryMinutes = (st.batteryCheckEveryMinutes - 5).coerceAtLeast(5))) } }, { store.update { it.copy(settings = st.copy(batteryCheckEveryMinutes = st.batteryCheckEveryMinutes + 5)) } })
                Stepper("Ring after power-on, up to", "${st.missedAlarmRecoveryHours}h late", { store.update { it.copy(settings = st.copy(missedAlarmRecoveryHours = (st.missedAlarmRecoveryHours - 1).coerceAtLeast(1))) } }, { store.update { it.copy(settings = st.copy(missedAlarmRecoveryHours = st.missedAlarmRecoveryHours + 1)) } })
                Text("• From your sleep reminder until the alarm, a loud warning if the phone isn't charging and is low.\n• If the phone restarts overnight, a fallback alarm rings even before you unlock it.\n• If the phone was off at alarm time, it rings the moment the phone turns back on.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("Class reminders") {
                Stepper("Leave-now reminder", "${w.leaveMinutes}m before", { save(w.copy(leaveMinutes = (w.leaveMinutes - 1).coerceAtLeast(0))) }, { save(w.copy(leaveMinutes = w.leaveMinutes + 1)) })
                Stepper("Class reminder", "${w.classReminderMinutes}m before", { save(w.copy(classReminderMinutes = (w.classReminderMinutes - 5).coerceAtLeast(0))) }, { save(w.copy(classReminderMinutes = w.classReminderMinutes + 5)) })
                SwitchRow("Ask \"did you attend?\" after each class", w.askAttendanceAfterClass) { save(w.copy(askAttendanceAfterClass = it)) }
            }
        }
        item {
            val logs = data.wakeLogs.takeLast(30).reversed()
            val tol = data.settings.onTimeToleranceMinutes
            SectionCard("Wake-up history", if (logs.isEmpty()) "No mornings logged yet" else "On time ${logs.count { it.dismissedAt != null && it.dismissedAt <= it.target + tol * 60_000L }} of ${logs.size} (within ${tol}m)") {
                logs.take(10).forEach { l ->
                    val late = l.dismissedAt?.let { ((it - l.target) / 60_000L).toInt() }
                    Text("${l.date} · " + when { late == null -> "missed"; late <= tol -> "on time"; else -> "${hm(late)} late" }, style = MaterialTheme.typography.bodySmall, color = if (late != null && late <= tol) Color(0xFF2E7D32) else Color(0xFFC62828))
                }
            }
        }
    }
}

@Composable
private fun ReadinessCard(tick: Int) {
    val context = LocalContext.current
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val am = context.getSystemService(AlarmManager::class.java)
    val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    val batteryOk = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    val fullScreen = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val audio = context.getSystemService(AudioManager::class.java)
    val volume = audio.getStreamVolume(AudioManager.STREAM_ALARM) * 100 / audio.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
    val bm = context.getSystemService(BatteryManager::class.java)
    val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val charging = bm.isCharging
    tick.hashCode()
    SectionCard("Will tomorrow's alarm ring?") {
        Check("Exact alarms allowed", exact) { open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
        Check("Not killed by battery optimisation", batteryOk) { open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) }
        Check("Full-screen alarm allowed", fullScreen) { if (Build.VERSION.SDK_INT >= 34) open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))) }
        Check("Alarm volume $volume%", volume >= 60) { audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), AudioManager.FLAG_SHOW_UI) }
        Check("Battery $level%" + if (charging) " · charging" else "", charging || level >= 60, fixLabel = null) {}
        Text("Some phones (Xiaomi, Oppo, Vivo, Realme, Samsung) also need Chronora allowed to auto-start and run in the background — check the phone's battery/app settings.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Open app settings") }
    }
}

@Composable
private fun Check(label: String, ok: Boolean, fixLabel: String? = "Fix", onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text((if (ok) "✓ " else "✗ ") + label, Modifier.weight(1f), color = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828))
        if (!ok && fixLabel != null) TextButton(onClick = onFix) { Text(fixLabel) }
    }
}

// ------------------------------------------------------------------ Library

@Composable
internal fun LibraryTab() {
    val context = LocalContext.current
    val store = remember { CampusStore.get(context) }
    val data by store.data.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(20_000) } }
    val today = now.toLocalDate()
    val active = data.librarySessions.lastOrNull()?.takeIf { it.end == null }
    val h = data.library
    var blockApps by remember { mutableStateOf(true) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("Library", LibraryPlanner.status(h, now)) {
                if (active == null) {
                    Button(onClick = {
                        store.update { it.copy(librarySessions = (it.librarySessions + LibrarySession(System.currentTimeMillis(), null)).takeLast(500)) }
                        if (blockApps) LibraryPlanner.openWindow(h, today)?.let { w ->
                            val until = today.atStartOfDay().plusMinutes(w.end.toLong()).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            if (until > System.currentTimeMillis()) FocusGuardStore(context).update { it.copy(sessionUntil = until) }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Check in") }
                    SwitchRow("Block distracting apps while I'm in the library", blockApps) { blockApps = it }
                } else {
                    val mins = ((System.currentTimeMillis() - active.start) / 60_000L).toInt()
                    Text("Checked in for ${hm(mins)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = {
                        store.update { d -> d.copy(librarySessions = d.librarySessions.dropLast(1) + active.copy(end = System.currentTimeMillis())) }
                        FocusGuardStore(context).update { if (it.lockedMode) it else it.copy(sessionUntil = 0) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Check out") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Today", hm(LibraryPlanner.minutesIn(data.librarySessions, today)), Modifier.weight(1f))
                    Stat("This week", hm((0L..6L).sumOf { LibraryPlanner.minutesIn(data.librarySessions, today.minusDays(it)) }), Modifier.weight(1f))
                    Stat("Days this month", "${(0 until today.dayOfMonth).count { LibraryPlanner.minutesIn(data.librarySessions, today.minusDays(it.toLong())) >= data.settings.libraryVisitMinutes }}", Modifier.weight(1f))
                }
            }
        }
        item {
            SectionCard("Free library slots this week", "Gaps between classes while the library is open") {
                (0L..6L).map { today.plusDays(it) }.forEach { d ->
                    val windows = LibraryPlanner.freeWindows(data, d, fromMinute = if (d == today) now.hour * 60 + now.minute else 0)
                    Text("${d.dayOfWeek.name.take(3)} · " + if (windows.isEmpty()) "—" else windows.joinToString { "${clock(it.start)}–${clock(it.end)}" } + "  (${hm(windows.sumOf { it.minutes })})", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            fun save(next: LibraryHours) = store.update { it.copy(library = next) }
            SectionCard("Opening hours") {
                Stepper("Weekdays open", clock(h.weekdayOpen), { save(h.copy(weekdayOpen = h.weekdayOpen - 15)) }, { save(h.copy(weekdayOpen = h.weekdayOpen + 15)) })
                Stepper("Weekdays close", clock(h.weekdayClose), { save(h.copy(weekdayClose = h.weekdayClose - 15)) }, { save(h.copy(weekdayClose = (h.weekdayClose + 15).coerceAtMost(24 * 60))) })
                Stepper("Weekends open", clock(h.weekendOpen), { save(h.copy(weekendOpen = h.weekendOpen - 15)) }, { save(h.copy(weekendOpen = h.weekendOpen + 15)) })
                Stepper("Weekends close", clock(h.weekendClose), { save(h.copy(weekendClose = h.weekendClose - 15)) }, { save(h.copy(weekendClose = (h.weekendClose + 15).coerceAtMost(24 * 60))) })
                SwitchRow("Saturday counts as weekend", h.saturdayIsWeekend) { save(h.copy(saturdayIsWeekend = it)) }
                SwitchRow("Closed on holidays", h.closedOnHolidays) { save(h.copy(closedOnHolidays = it)) }
                Stepper("Daily study goal", hm(data.studyGoalMinutes), { store.update { it.copy(studyGoalMinutes = (it.studyGoalMinutes - 30).coerceAtLeast(30)) } }, { store.update { it.copy(studyGoalMinutes = it.studyGoalMinutes + 30) } })
            }
        }
    }
}

// ------------------------------------------------------------------ CGPA

@Composable
internal fun CgpaTab() {
    val context = LocalContext.current
    val store = remember { CampusStore.get(context) }
    val semesters by store.semesters.collectAsStateWithLifecycle()
    val data by store.data.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf("8.5") }
    var remaining by remember { mutableStateOf("80") }
    var editing by remember { mutableStateOf<Int?>(null) }
    var scaleOpen by remember { mutableStateOf(false) }
    val scale = data.settings.gradeScale

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val cg = Cgpa.cgpa(semesters, scale)
            SectionCard("CGPA ${cg?.let { "%.2f".format(it) } ?: "—"}", "${Cgpa.earnedCredits(semesters, scale)} credits graded · " + scale.joinToString(" ") { "${it.letter}=${fmt(it.points)}" }, action = { TextButton(onClick = { scaleOpen = true }) { Text("Grade scale") } }) {
                semesters.sortedBy { it.number }.forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable { editing = s.number }, verticalAlignment = Alignment.CenterVertically) {
                        Text("Semester ${s.number}", Modifier.weight(1f))
                        Text("SGPA ${Cgpa.sgpa(s.courses, scale)?.let { "%.2f".format(it) } ?: "—"} · ${s.courses.sumOf { it.credits }} cr", fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { val n = (semesters.maxOfOrNull { it.number } ?: 0) + 1; store.updateSemesters { it + SemesterResult(n, emptyList()) }; editing = n }) { Text("Add semester") }
                    if (data.subjects.isNotEmpty()) OutlinedButton(onClick = {
                        val n = (semesters.maxOfOrNull { it.number } ?: 0) + 1
                        store.updateSemesters { it + SemesterResult(n, data.subjects.map { s -> Course(s.name, s.credits, null) }) }; editing = n
                    }) { Text("From current subjects") }
                }
            }
        }
        item {
            SectionCard("Target planner") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(target, { target = it }, label = { Text("Target CGPA") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(remaining, { remaining = it.filter(Char::isDigit) }, label = { Text("Credits left") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                val t = target.toDoubleOrNull(); val r = remaining.toIntOrNull()
                if (t != null && r != null) {
                    val need = Cgpa.requiredAverage(semesters, t, r, scale)
                    Text(if (need == null) "Not reachable with $r credits left — even the top grade everywhere wouldn't get there." else "You need an average SGPA of ${"%.2f".format(need)} over the remaining $r credits.", fontWeight = FontWeight.SemiBold)
                }
                Text("Tip: fill the current semester with expected grades to see where you'll land.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    editing?.let { n -> semesters.firstOrNull { it.number == n }?.let { SemesterDialog(it, store, scale) { editing = null } } }
    if (scaleOpen) GradeScaleDialog(scale, onSave = { v -> store.update { it.copy(settings = it.settings.copy(gradeScale = v)) } }) { scaleOpen = false }
}

private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

@Composable
private fun GradeScaleDialog(scale: List<GradePoint>, onSave: (List<GradePoint>) -> Unit, close: () -> Unit) {
    var rows by remember { mutableStateOf(scale.map { it.letter to fmt(it.points) }) }
    AlertDialog(onDismissRequest = close, title = { Text("Grade scale") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 460.dp)) {
            item { Text("Letter grades and their points. Plain numbers (e.g. 8.5) are also accepted as grades.", style = MaterialTheme.typography.bodySmall) }
            items(rows.indices.toList()) { i ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(rows[i].first, { v -> rows = rows.toMutableList().also { it[i] = v to rows[i].second } }, label = { Text("Grade") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(rows[i].second, { v -> rows = rows.toMutableList().also { it[i] = rows[i].first to v } }, label = { Text("Points") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { rows = rows.toMutableList().also { it.removeAt(i) } }) { Icon(Icons.Default.Delete, "Remove") }
                }
            }
            item { TextButton(onClick = { rows = rows + ("" to "") }) { Text("Add grade") } }
            item { TextButton(onClick = { rows = Cgpa.defaultScale.map { it.letter to fmt(it.points) } }) { Text("Reset to AA–FF (10-point)") } }
        }
    }, confirmButton = {
        Button(onClick = {
            val parsed = rows.mapNotNull { (l, p) -> p.toDoubleOrNull()?.takeIf { l.isNotBlank() }?.let { GradePoint(l.trim(), it) } }
            if (parsed.isNotEmpty()) onSave(parsed.sortedByDescending { it.points })
            close()
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun SemesterDialog(sem: SemesterResult, store: CampusStore, scale: List<GradePoint>, close: () -> Unit) {
    var courses by remember { mutableStateOf(sem.courses) }
    AlertDialog(onDismissRequest = close, title = { Text("Semester ${sem.number} · SGPA ${Cgpa.sgpa(courses, scale)?.let { "%.2f".format(it) } ?: "—"}") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 480.dp)) {
            items(courses.indices.toList()) { i ->
                val c = courses[i]
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(c.name, { v -> courses = courses.toMutableList().also { it[i] = c.copy(name = v) } }, label = { Text("Course") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(c.credits.toString(), { v -> courses = courses.toMutableList().also { it[i] = c.copy(credits = v.filter(Char::isDigit).toIntOrNull() ?: 0) } }, label = { Text("Cr") }, singleLine = true, modifier = Modifier.width(56.dp))
                        IconButton(onClick = { courses = courses.toMutableList().also { it.removeAt(i) } }) { Icon(Icons.Default.Delete, "Remove") }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(scale.map { it.letter }) { g -> FilterChip(c.grade == g, { courses = courses.toMutableList().also { it[i] = c.copy(grade = if (c.grade == g) null else g) } }, label = { Text(g) }) }
                    }
                }
            }
            item { TextButton(onClick = { courses = courses + Course("", 4, null) }) { Text("Add course") } }
            item { TextButton(onClick = { store.updateSemesters { l -> l.filterNot { it.number == sem.number } }; close() }) { Text("Delete semester", color = MaterialTheme.colorScheme.error) } }
        }
    }, confirmButton = {
        Button(onClick = { store.updateSemesters { l -> l.map { if (it.number == sem.number) it.copy(courses = courses.filter { c -> c.name.isNotBlank() }) else it } }; close() }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

// ------------------------------------------------------------------ Placements

@Composable
internal fun PlacementTab() {
    val context = LocalContext.current
    val store = remember { CampusStore.get(context) }
    val companies by store.companies.collectAsStateWithLifecycle()
    val data by store.data.collectAsStateWithLifecycle()
    val stages = data.settings.placementStages
    var editing by remember { mutableStateOf<Company?>(null) }
    val today = LocalDate.now()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val funnel = PlacementStats.funnel(companies, stages)
            SectionCard("Placement tracker", "${companies.size} companies", action = { TextButton(onClick = { editing = Company(store.nextId(), "", stageLabel = stages.first()) }) { Text("Add") } }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(funnel.keys.toList()) { st -> Stat(st, "${funnel[st] ?: 0}") } }
                PlacementStats.upcoming(companies, today).take(5).forEach { c ->
                    Text("${c.nextDate} ${clock(c.nextMinute)} · ${c.name}: ${c.nextEvent.ifBlank { c.stageName }}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                Text("Prep sheets (CS core, AI/ML, aptitude, resume) are in the Sheets tab.", style = MaterialTheme.typography.bodySmall)
            }
        }
        (stages + companies.map { it.stageName }).distinct().forEach { stage ->
            val list = companies.filter { it.stageName.equals(stage, true) }
            if (list.isNotEmpty()) {
                item(key = "stage-$stage") { Text(stage, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                items(list, key = { it.id }) { c ->
                    Card(Modifier.fillMaxWidth().clickable { editing = c }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.name, fontWeight = FontWeight.Bold)
                            Text(listOf(c.role, c.ctc, c.nextDate?.let { "${c.nextEvent.ifBlank { "Next" }} $it ${clock(c.nextMinute)}" } ?: "").filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    editing?.let { CompanyDialog(it, store, stages) { editing = null } }
}

@Composable
private fun CompanyDialog(c: Company, store: CampusStore, stages: List<String>, close: () -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var role by remember { mutableStateOf(c.role) }
    var ctc by remember { mutableStateOf(c.ctc) }
    var stage by remember { mutableStateOf(c.stageName) }
    var nextDate by remember { mutableStateOf(c.nextDate?.let { LocalDate.parse(it) }) }
    var time by remember { mutableStateOf(clock(c.nextMinute)) }
    var event by remember { mutableStateOf(c.nextEvent) }
    var link by remember { mutableStateOf(c.link) }
    var notes by remember { mutableStateOf(c.notes) }
    AlertDialog(onDismissRequest = close, title = { Text(if (c.name.isBlank()) "Add company" else c.name) }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 500.dp)) {
            item { OutlinedTextField(name, { name = it }, label = { Text("Company") }, singleLine = true) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(role, { role = it }, label = { Text("Role") }, singleLine = true, modifier = Modifier.weight(1f)); OutlinedTextField(ctc, { ctc = it }, label = { Text("CTC / stipend") }, singleLine = true, modifier = Modifier.weight(1f)) } }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(stages) { s -> FilterChip(stage == s, { stage = s }, label = { Text(s) }) } } }
            item { OutlinedTextField(event, { event = it }, label = { Text("Next step (OA, Interview round 1…)") }, singleLine = true) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { DateButton("On", nextDate ?: LocalDate.now().plusDays(3)) { nextDate = it }; Spacer(Modifier.width(6.dp)); OutlinedTextField(time, { time = it }, label = { Text("Time") }, singleLine = true, modifier = Modifier.width(96.dp)) } }
            item { OutlinedTextField(link, { link = it }, label = { Text("Link") }, singleLine = true) }
            item { OutlinedTextField(notes, { notes = it }, label = { Text("Notes: questions asked, contacts…") }, minLines = 3) }
            if (c.name.isNotBlank()) item { TextButton(onClick = { store.updateCompanies { l -> l.filterNot { it.id == c.id } }; close() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
        }
    }, confirmButton = {
        Button(enabled = name.isNotBlank(), onClick = {
            val updated = c.copy(name = name.trim(), role = role.trim(), ctc = ctc.trim(), stageLabel = stage, nextDate = nextDate?.toString(), nextMinute = parseClock(time) ?: c.nextMinute, nextEvent = event.trim(), link = link.trim(), notes = notes)
            store.updateCompanies { l -> l.filterNot { it.id == c.id } + updated }
            close()
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
