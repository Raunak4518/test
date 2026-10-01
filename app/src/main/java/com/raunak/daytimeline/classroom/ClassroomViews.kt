package com.raunak.daytimeline.classroom

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.Identity
import com.raunak.daytimeline.AppContainer
import com.raunak.daytimeline.campus.*
import com.raunak.daytimeline.data.TaskEntity
import com.raunak.daytimeline.ui.*
import com.raunak.daytimeline.wellbeing.WellbeingNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private fun ago(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when { m < 1 -> "just now"; m < 60 -> "${m}m ago"; m < 1440 -> "${m / 60}h ago"; else -> "${m / 1440}d ago" }
}

@Composable
private fun urgencyColor(r: Ranked): Color = when {
    r.reason.startsWith("Overdue") -> Chronora.colors.bad
    r.reason.startsWith("Due in") -> Chronora.colors.warn
    r.reason == "New message" || r.reason == "Important" -> MaterialTheme.colorScheme.primary
    else -> Chronora.muted
}

/** Compact card for Campus → Today: what Classroom needs from you now. */
@Composable
fun ClassroomTodayCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ClassroomStore.get(context) }
    val data by store.data.collectAsStateWithLifecycle()
    val now = LocalDateTime.now()
    val ranked = remember(data) { ClassroomBrain.rank(data.items, now, data.settings) }
    if (data.items.isEmpty()) return
    val targets = remember(data) { ClassroomBrain.studyTargets(data.items, now, data.settings) }
    SectionCard("Classroom", ClassroomBrain.digest(data.items, now, data.settings, System.currentTimeMillis() - 86_400_000L).first(), action = { TextButton(onClick = onOpen) { Text("Open") } }) {
        ranked.take(3).forEach { r -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.item.kind.icon)
            Text("  ${r.item.title}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.reason, style = MaterialTheme.typography.labelSmall, color = urgencyColor(r))
        } }
        if (targets.isNotEmpty()) Text("🎯 Study today: " + targets.joinToString(" · ") { "${it.minutesToday}m ${it.item.title.take(18)}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ClassroomTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ClassroomStore.get(context) }
    val campus = remember { CampusStore.get(context) }
    val data by store.data.collectAsStateWithLifecycle()
    val campusData by campus.data.collectAsStateWithLifecycle()
    val s = data.settings
    val now = LocalDateTime.now()
    val nowMs = System.currentTimeMillis()
    var filter by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<ClassItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pasting by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ClassroomSync.schedule(context) }

    fun afterToken(token: String?) {
        if (token == null) { message = "Google didn't return access. Check the setup steps below."; return }
        store.settings { it.copy(connected = true) }
        busy = true
        scope.launch {
            val r = ClassroomSync.syncNow(context, token)
            busy = false
            message = r.fold({ "Synced $it items from Google Classroom" }, { "Sync failed: ${it.message}" })
            ClassroomSync.schedule(context)
        }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) afterToken(runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data).accessToken }.getOrNull())
        else message = "Sign-in cancelled"
    }
    fun connect() {
        val activity = context as? Activity ?: return
        message = "Opening Google sign-in…"
        Identity.getAuthorizationClient(activity).authorize(ClassroomAuth.request())
            .addOnSuccessListener { r ->
                if (r.hasResolution()) r.pendingIntent?.let { consent.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
                else afterToken(r.accessToken)
            }
            .addOnFailureListener { message = "Couldn't start Google sign-in: ${it.message}" }
    }

    val ranked = remember(data) { ClassroomBrain.rank(data.items, now, s) }
    val targets = remember(data) { ClassroomBrain.studyTargets(data.items, now, s) }
    val suggestions = remember(data, campusData.subjects) { ClassroomBrain.suggestions(data.items, campusData.subjects, s, now.toLocalDate(), data.handledSuggestions) }
    val filters = listOf("Needs you", "To do", "Messages", "Announcements", "Materials", "Grades", "Done", "All")
    val shown: List<ClassItem> = when (filter) {
        0 -> ranked.map { it.item }
        1 -> data.items.filter { ClassroomBrain.isOpenWork(it, nowMs) }.sortedBy { it.dueAt ?: Long.MAX_VALUE }
        2 -> data.items.filter { it.kind == ClassKind.COMMENT && !it.hidden }
        3 -> data.items.filter { it.kind == ClassKind.ANNOUNCEMENT && !it.hidden }
        4 -> data.items.filter { it.kind == ClassKind.MATERIAL && !it.hidden }
        5 -> data.items.filter { it.kind == ClassKind.GRADE && !it.hidden }
        6 -> data.items.filter { it.done || it.state == WorkState.SUBMITTED || it.state == WorkState.RETURNED }
        else -> data.items.filter { !it.hidden }
    }.filter { query.isBlank() || it.title.contains(query, true) || it.course.contains(query, true) || it.body.contains(query, true) }
    val reasons = ranked.associate { it.item.id to it }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val open = data.items.filter { ClassroomBrain.isOpenWork(it, nowMs) }
            val overdue = open.count { (it.dueAt ?: Long.MAX_VALUE) < nowMs }
            val soon = open.count { it.dueAt != null && it.dueAt >= nowMs && it.dueAt - nowMs < s.urgentHours * 3_600_000L }
            val unread = data.items.count { !it.read && !it.hidden && it.kind in setOf(ClassKind.COMMENT, ClassKind.ANNOUNCEMENT, ClassKind.GRADE, ClassKind.MATERIAL) }
            HeroCard {
                Text("Classroom", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    listOf("Pending" to "${open.size}", "Due ≤${s.urgentHours}h" to "$soon", "Overdue" to "$overdue", "Unread" to "$unread").forEach { (l, v) ->
                        Column { Text(v, color = Chronora.colors.onHero, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(l, color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                ranked.firstOrNull()?.let { Text("Most urgent: ${it.item.course} · ${it.item.title} — ${it.reason}", color = Chronora.colors.onHero, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                if (targets.isNotEmpty()) Text("🎯 Study today ${targets.sumOf { it.minutesToday }} min to stay on track", color = Chronora.colors.heroAccent, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { ConnectionCard(s, busy, message, onConnect = ::connect, onSync = {
            busy = true
            scope.launch { val r = ClassroomSync.syncNow(context); busy = false; message = r.fold({ "Synced $it items" }, { "Sync failed: ${it.message}" }) }
        }, onDisconnect = { store.settings { it.copy(connected = false) }; ClassroomSync.schedule(context); message = "Disconnected (items you already have stay)" }) }
        if (suggestions.isNotEmpty()) item {
            SectionCard("Spotted in announcements", "One tap to update Chronora") {
                suggestions.take(5).forEach { sg ->
                    val subject = campusData.subjects.firstOrNull { it.id == sg.subjectId }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${sg.course}: “${sg.text.take(90)}”", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${sg.kind.action} · ${com.raunak.daytimeline.productivity.relativeDay(sg.date)}" + (subject?.let { " · ${it.name}" } ?: ""), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { message = applySuggestion(context, sg, data); store.update { it.copy(handledSuggestions = it.handledSuggestions + sg.id) } }, enabled = subject != null || sg.kind == Suggestion.Kind.EXAM || sg.kind == Suggestion.Kind.EXTENDED) { Text("Apply") }
                            TextButton(onClick = { store.update { it.copy(handledSuggestions = it.handledSuggestions + sg.id) } }) { Text("Ignore") }
                        }
                        if (subject == null && sg.kind in setOf(Suggestion.Kind.CANCEL_CLASS, Suggestion.Kind.EXTRA_CLASS)) Text("Link “${sg.course}” to a subject in settings below to apply this.", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
                    }
                }
            }
        }
        if (targets.isNotEmpty()) item {
            SectionCard("Today's study targets", "Work spread evenly until each due date · cap ${s.dailyStudyCapMinutes} min/day") {
                targets.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${t.item.kind.icon} ${t.item.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("${t.item.course} · due ${ClassroomBrain.dayWord(t.item.dueAt!!, now)} · ~${t.totalMinutes} min in all", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                        }
                        Text("${t.minutesToday}m", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Button(onClick = { message = planTargets(context, targets, campusData) }) { Text("Put them on today's timeline") }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                filters.forEachIndexed { i, f -> FilterChip(filter == i, { filter = i }, label = { Text(f) }) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search Classroom") }, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { pasting = true }) { Text("Paste") }
            }
        }
        if (shown.isEmpty()) item {
            EmptyState(if (data.items.isEmpty()) "Nothing from Classroom yet" else "All clear here",
                if (data.items.isEmpty()) "Turn on notification capture or connect Google Classroom above. You can also paste an assignment or message." else "Nothing matches this filter.")
        }
        items(shown.take(150), key = { it.id }) { i -> ItemCard(i, reasons[i.id], campusData.subjects, s) { open = i; if (!i.read) store.item(i.id) { it.copy(read = true) } } }
        item {
            SectionCard("Classroom settings", action = { TextButton(onClick = { settingsOpen = !settingsOpen }) { Text(if (settingsOpen) "Done" else "Edit") } }) {
                if (!settingsOpen) Text("Alerts: " + (if (s.instantAlerts) "urgent items at once" else "digest only") + " · digest " + (if (s.digestOff) "off" else clock(s.digestMinute)) + " · deadlines " + (if (s.autoDeadlines) "auto" else "off"), style = MaterialTheme.typography.bodySmall)
                else ClassroomSettingsEditor(s, data, campusData.subjects, store) { ClassroomSync.schedule(context) }
            }
        }
    }
    open?.let { i -> ItemDialog(i, campusData.subjects, s, store) { open = null } }
    if (pasting) PasteDialog { text, course ->
        ClassroomBrain.fromNotification(ClassroomBrain.CLASSROOM_PACKAGE, course, text, text, course, System.currentTimeMillis(), LocalDateTime.now())?.let { ClassroomSync.ingest(context, listOf(it.copy(id = "p" + it.id.drop(1)))) }
        pasting = false
    }
}

@Composable
private fun ConnectionCard(s: ClassroomSettings, busy: Boolean, message: String, onConnect: () -> Unit, onSync: () -> Unit, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    val listener = WellbeingNotificationListener.enabled(context)
    val classroomInstalled = context.packageManager.getLaunchIntentForPackage(ClassroomBrain.CLASSROOM_PACKAGE) != null
    var help by remember { mutableStateOf(false) }
    val store = remember { ClassroomStore.get(context) }
    SectionCard("Connections") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("From Classroom notifications", fontWeight = FontWeight.SemiBold)
                Text(when {
                    !s.captureNotifications -> "Off"
                    !listener -> "Needs notification access"
                    !classroomInstalled -> "On — install the Google Classroom app (Classroom e-mails in Gmail also work)"
                    else -> "On — new posts are picked up instantly"
                }, style = MaterialTheme.typography.bodySmall, color = if (listener && s.captureNotifications) Chronora.colors.good else Chronora.muted)
            }
            if (!listener) TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Grant") }
            else Switch(s.captureNotifications, { v -> store.settings { it.copy(captureNotifications = v) } })
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Google Classroom sync", fontWeight = FontWeight.SemiBold)
                Text(when {
                    !s.connected -> "Not connected — sees every course, due date and whether you've turned it in"
                    s.lastError != null -> "Problem: ${s.lastError}"
                    s.lastSync > 0 -> "Synced ${ago(s.lastSync)} · every ${s.syncEveryHours}h"
                    else -> "Connected"
                }, style = MaterialTheme.typography.bodySmall, color = if (s.connected && s.lastError == null) Chronora.colors.good else if (s.lastError != null) Chronora.colors.bad else Chronora.muted)
            }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else if (s.connected) TextButton(onClick = onSync) { Text("Sync now") } else Button(onClick = onConnect) { Text("Connect") }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Row {
            TextButton(onClick = { help = !help }) { Text(if (help) "Hide setup help" else "Setup help") }
            if (s.connected) TextButton(onClick = onDisconnect) { Text("Disconnect") }
        }
        if (help) {
            val sha = remember { ClassroomAuth.signingSha1(context) }
            Text("One-time setup (about 5 minutes):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            listOf(
                "1. On a computer open console.cloud.google.com and create a project (any name).",
                "2. APIs & Services → Library → enable “Google Classroom API”.",
                "3. OAuth consent screen → External → add your college e-mail under Test users.",
                "4. Credentials → Create credentials → OAuth client ID → Android.",
                "5. Package name: ${context.packageName}",
                "6. SHA-1: $sha",
                "7. Save, wait a few minutes, then tap Connect here and pick your college account.",
                "If Google says your organisation blocks this app, the college admin hasn't allowed third-party access — notification capture still works."
            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("SHA-1", sha))
            }) { Text("Copy SHA-1") }
        }
    }
}

@Composable
private fun ItemCard(i: ClassItem, r: Ranked?, subjects: List<Subject>, s: ClassroomSettings, onClick: () -> Unit) {
    val subject = ClassroomBrain.matchSubject(i.course, subjects, s.courseMap)
    val done = i.done || i.state == WorkState.SUBMITTED || i.state == WorkState.RETURNED
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(subject?.let { Color(it.colorHex).copy(alpha = .18f) } ?: MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text(i.kind.icon, fontSize = 18.sp) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(i.title, fontWeight = if (i.read) FontWeight.Normal else FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (done) Chronora.muted else MaterialTheme.colorScheme.onSurface)
                Text(listOfNotNull(subject?.name ?: i.course, i.kind.label, i.dueAt?.let { "due " + ClassroomBrain.dayWord(it, LocalDateTime.now()) }, ago(i.postedAt)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Chronora.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (i.body.isNotBlank() && i.kind in setOf(ClassKind.ANNOUNCEMENT, ClassKind.COMMENT)) Text(i.body, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (i.state != WorkState.NONE) com.raunak.daytimeline.productivity.Pill(if (i.done && i.state == WorkState.PENDING) "Done" else i.state.label,
                    when { done -> Chronora.colors.good; i.state == WorkState.LATE -> Chronora.colors.bad; else -> MaterialTheme.colorScheme.primary })
                r?.let { Text(it.reason, style = MaterialTheme.typography.labelSmall, color = urgencyColor(it), modifier = Modifier.padding(top = 4.dp)) }
                if (i.source == "API") Text("synced", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
            }
        }
    }
}

@Composable
private fun ItemDialog(i: ClassItem, subjects: List<Subject>, s: ClassroomSettings, store: ClassroomStore, close: () -> Unit) {
    val context = LocalContext.current
    val subject = ClassroomBrain.matchSubject(i.course, subjects, s.courseMap)
    val work = i.kind in setOf(ClassKind.ASSIGNMENT, ClassKind.QUIZ, ClassKind.QUESTION)
    AlertDialog(onDismissRequest = close, title = { Text("${i.kind.icon} ${i.title}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(listOfNotNull(i.course, subject?.let { "→ ${it.name}" }, i.kind.label).joinToString(" · "), color = Chronora.muted, style = MaterialTheme.typography.bodySmall)
            i.dueAt?.let { Text("Due " + ClassroomBrain.dayWord(it, LocalDateTime.now()), fontWeight = FontWeight.SemiBold) }
            if (i.points != null) Text("Points: ${i.points.toInt()}" + (i.grade?.let { " · your grade ${it}" } ?: ""))
            if (work && ClassroomBrain.isOpenWork(i, System.currentTimeMillis())) ClassroomBrain.studyTargets(listOf(i), LocalDateTime.now(), s).firstOrNull()?.let {
                Text("Plan: about ${it.totalMinutes} min of work · ${it.minutesToday} min today keeps you on track", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            if (i.body.isNotBlank()) Text(i.body)
            Text((if (i.source == "API") "From Google Classroom sync" else "From a Classroom notification") + " · posted ${ago(i.postedAt)}", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (work) AssistChip(onClick = { store.item(i.id) { it.copy(done = !it.done) }; ClassroomSync.syncDeadlines(context); close() }, label = { Text(if (i.done) "Not done" else "Mark done") })
                AssistChip(onClick = { store.item(i.id) { it.copy(snoozedUntil = System.currentTimeMillis() + 86_400_000L) }; close() }, label = { Text("Snooze a day") })
                AssistChip(onClick = { store.item(i.id) { it.copy(hidden = !it.hidden) }; ClassroomSync.syncDeadlines(context); close() }, label = { Text(if (i.hidden) "Unhide" else "Hide") })
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val intent = i.link.takeIf { it.startsWith("http") }?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) } ?: context.packageManager.getLaunchIntentForPackage(ClassroomBrain.CLASSROOM_PACKAGE)
            intent?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }) { Text("Open in Classroom") }
    }, dismissButton = { TextButton(onClick = close) { Text("Close") } })
}

@Composable
private fun PasteDialog(onAdd: (String, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { onAdd("", "") }, title = { Text("Paste from Classroom") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(course, { course = it }, label = { Text("Course (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(text, { text = it }, label = { Text("Text") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { Button(enabled = text.isNotBlank(), onClick = { onAdd(text, course) }) { Text("Add") } }, dismissButton = { TextButton(onClick = { onAdd("", "") }) { Text("Cancel") } })
}

@Composable
private fun ClassroomSettingsEditor(s: ClassroomSettings, data: ClassroomData, subjects: List<Subject>, store: ClassroomStore, rescheduled: () -> Unit) {
    fun save(t: (ClassroomSettings) -> ClassroomSettings) { store.settings(t); rescheduled() }
    SwitchRow("Add work as deadlines", s.autoDeadlines) { v -> save { it.copy(autoDeadlines = v) } }
    SwitchRow("Instant alerts for urgent items", s.instantAlerts) { v -> save { it.copy(instantAlerts = v) } }
    Stepper("Urgent = due within", "${s.urgentHours}h", { save { it.copy(urgentHours = (s.urgentHours - 12).coerceAtLeast(6)) } }, { save { it.copy(urgentHours = s.urgentHours + 12) } })
    SwitchRow("Morning digest", !s.digestOff) { v -> save { it.copy(digestOff = !v) } }
    if (!s.digestOff) Stepper("Digest time", clock(s.digestMinute), { save { it.copy(digestMinute = (s.digestMinute - 15).coerceAtLeast(15)) } }, { save { it.copy(digestMinute = (s.digestMinute + 15).coerceAtMost(24 * 60 - 15)) } })
    Stepper("Sync every", "${s.syncEveryHours}h", { save { it.copy(syncEveryHours = (s.syncEveryHours - 1).coerceAtLeast(1)) } }, { save { it.copy(syncEveryHours = (s.syncEveryHours + 1).coerceAtMost(24)) } })
    Text("Expected work (for study targets)", style = MaterialTheme.typography.labelLarge)
    listOf(ClassKind.ASSIGNMENT, ClassKind.QUIZ, ClassKind.QUESTION, ClassKind.MATERIAL).forEach { k ->
        val v = s.effort(k)
        Stepper(k.label, "$v min", { save { it.copy(effortMinutes = it.effortMinutes + (k.name to (v - 10).coerceAtLeast(0))) } }, { save { it.copy(effortMinutes = it.effortMinutes + (k.name to v + 10)) } })
    }
    Stepper("Most study per day from Classroom", "${s.dailyStudyCapMinutes} min", { save { it.copy(dailyStudyCapMinutes = (s.dailyStudyCapMinutes - 15).coerceAtLeast(15)) } }, { save { it.copy(dailyStudyCapMinutes = s.dailyStudyCapMinutes + 15) } })
    ListEditor("Important words in posts", s.importantWords) { v -> save { it.copy(importantWords = v) } }
    val courses = data.items.map { it.course }.filter { it != "Classroom" }.distinct().sorted()
    if (courses.isNotEmpty()) {
        Text("Courses → subjects", style = MaterialTheme.typography.labelLarge)
        courses.forEach { c ->
            val current = ClassroomBrain.matchSubject(c, subjects, s.courseMap)
            Text(c + (if (c in s.courseMap) "" else if (current != null) "  (auto)" else ""), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                subjects.forEach { sub -> FilterChip(current?.id == sub.id, { save { it.copy(courseMap = it.courseMap + (c to sub.id)) } }, label = { Text(sub.name) }) }
            }
        }
    }
}

/** Applies an announcement suggestion; returns a message for the user. */
private fun applySuggestion(context: android.content.Context, sg: Suggestion, data: ClassroomData): String {
    val campus = CampusStore.get(context)
    val d = campus.data.value
    return when (sg.kind) {
        Suggestion.Kind.CANCEL_CLASS -> {
            val occ = AttendanceEngine.occurrences(d, sg.date).filter { it.subjectId == sg.subjectId && it.slotId != null }
            if (occ.isEmpty()) "No ${d.subjects.firstOrNull { it.id == sg.subjectId }?.name ?: "class"} class on that day"
            else { campus.update { c -> c.copy(exceptions = c.exceptions + occ.map { ScheduleException(campus.nextId(), ExceptionKind.CANCEL, sg.date.toString(), slotId = it.slotId, note = "Cancelled (Classroom)") }) }; "Marked ${occ.size} class(es) cancelled on ${sg.date}" }
        }
        Suggestion.Kind.EXTRA_CLASS -> {
            val start = DateFinder.time(sg.text)?.let { it.hour * 60 + it.minute } ?: 17 * 60
            campus.update { c -> c.copy(exceptions = c.exceptions + ScheduleException(campus.nextId(), ExceptionKind.EXTRA, sg.date.toString(), subjectId = sg.subjectId, start = start, end = start + 60, note = "Extra class (Classroom)")) }
            "Added an extra class on ${sg.date} at ${clock(start)}"
        }
        Suggestion.Kind.EXAM -> {
            val t = DateFinder.time(sg.text)?.let { it.hour * 60 + it.minute } ?: 9 * 60
            campus.update { c -> c.copy(deadlines = c.deadlines + Deadline(campus.nextId(), "${sg.course}: ${sg.text.take(60)}", DeadlineKind.QUIZ, sg.date.toString(), t, sg.subjectId, notes = "From a Classroom announcement", kindLabel = "Test")) }
            "Added the test to Exams & tasks for ${sg.date}"
        }
        Suggestion.Kind.EXTENDED -> {
            val store = ClassroomStore.get(context)
            val target = data.items.filter { it.course == sg.course && ClassroomBrain.isOpenWork(it, System.currentTimeMillis()) && it.dueAt != null }.minByOrNull { it.dueAt!! }
                ?: return "No pending work in ${sg.course} to move"
            store.item(target.id) { it.copy(dueAt = sg.date.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
            ClassroomSync.syncDeadlines(context)
            "Moved “${target.title}” to ${sg.date}"
        }
    }
}

/** Puts today's study targets into free time on the day timeline. */
private fun planTargets(context: android.content.Context, targets: List<StudyTarget>, campus: CampusData): String {
    val now = LocalDateTime.now()
    val windows = LibraryPlanner.freeWindows(campus, now.toLocalDate(), fromMinute = now.hour * 60 + now.minute, minMinutes = 15).toMutableList()
    val placed = mutableListOf<TaskEntity>()
    for (t in targets) {
        val w = windows.firstOrNull { it.minutes >= t.minutesToday } ?: windows.maxByOrNull { it.minutes } ?: break
        val len = minOf(t.minutesToday, w.minutes)
        if (len < 15) break
        placed += TaskEntity(title = "${t.item.kind.icon} ${t.item.title}", dateEpochDay = now.toLocalDate().toEpochDay(), startMinute = w.start, endMinute = w.start + len,
            category = "Study", priority = if (t.daysLeft <= 1) 3 else 2, notes = "${t.item.course} · from Classroom", pomodoroEnabled = true, tags = "classroom")
        windows[windows.indexOf(w)] = Window(w.start + len + 5, w.end)
    }
    if (placed.isEmpty()) return "No free time left today — try tomorrow morning"
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { val repo = AppContainer(context).repository; placed.forEach { repo.addTask(it) } }
    return "Added ${placed.size} study block(s) to today's timeline"
}
