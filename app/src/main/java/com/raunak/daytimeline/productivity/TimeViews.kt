package com.raunak.daytimeline.productivity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raunak.daytimeline.features.*
import com.raunak.daytimeline.ui.*
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private fun hms(ms: Long) = (ms / 1000).let { "%d:%02d:%02d".format(it / 3600, (it / 60) % 60, it % 60) }
private fun hmin(m: Long) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
private fun clockOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }

/** Toggl-style tracker: one running timer, projects and tags, recent restarts, reports by project/tag/day. */
@Composable
fun TimeSection(entries: List<OfflineTimeEntry>, projects: List<OfflineProject>, store: OfflineProductivityStore) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val running = entries.firstOrNull { it.endEpochMillis == null }
    LaunchedEffect(running?.id) { while (running != null) { now = System.currentTimeMillis(); delay(1000) } }
    var label by remember { mutableStateOf("") }
    var project by remember { mutableStateOf<Long?>(null) }
    var tags by remember { mutableStateOf("") }
    var range by remember { mutableIntStateOf(0) } // 0 today, 1 week, 2 month
    var editing by remember { mutableStateOf<OfflineTimeEntry?>(null) }
    var newProject by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val from = when (range) { 0 -> today; 1 -> today.with(DayOfWeek.MONDAY); else -> today.withDayOfMonth(1) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        HeroCard {
            if (running != null) {
                Text(running.label, color = Chronora.colors.onHero, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(projects.firstOrNull { it.id == running.projectId }?.name, running.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }, "since ${clockOf(running.startEpochMillis)}").joinToString(" · "), color = Chronora.colors.heroMuted, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(hms(now - running.startEpochMillis), color = Chronora.colors.onHero, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    FilledIconButton(onClick = { store.stopTimeEntry(running.id) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Chronora.colors.bad), modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Stop, "Stop", tint = Color.White) }
                }
            } else {
                Text("Not tracking", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelLarge)
                Text("${hmin(TimeEngine.inRange(entries, today, today, now).sumOf { it.second })} today", color = Chronora.colors.onHero, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        SectionCard("Start a timer") {
            OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("What are you working on?") })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(project == null, { project = null }, label = { Text("No project") })
                projects.forEach { p -> FilterChip(project == p.id, { project = p.id }, label = { Text(p.name) }, leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(p.color))) }) }
                AssistChip(onClick = { newProject = true }, label = { Text("+ Project") })
            }
            OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Tags: deep, dsa, college") })
            Button(onClick = { store.startTimer(label.ifBlank { "Untitled" }, project, tags.split(',', ' ').map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.toSet()); label = "" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (running != null) "Switch to this" else "Start")
            }
            val recent = TimeEngine.recent(entries)
            if (recent.isNotEmpty()) {
                Text("Continue", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                recent.forEach { e ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { store.startTimer(e.label, e.projectId, e.tags) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(projects.firstOrNull { it.id == e.projectId }?.let { Color(it.color) } ?: Chronora.muted))
                        Text("  " + e.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.PlayArrow, "Continue ${e.label}", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        PillTabs(listOf("Today", "Week", "Month"), range) { range = it }
        val byProject = TimeEngine.byProject(entries, projects, from, today, now)
        val total = byProject.sumOf { it.minutes }
        SectionCard("Report · ${hmin(total)}", if (range == 0) null else "Daily average ${hmin(total / (java.time.temporal.ChronoUnit.DAYS.between(from, today) + 1))}") {
            if (range != 0) {
                val days = (java.time.temporal.ChronoUnit.DAYS.between(from, today) + 1).toInt()
                val per = TimeEngine.perDay(entries, from, days, now)
                val max = (per.maxOrNull() ?: 0L).coerceAtLeast(60)
                Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                    per.forEach { m -> Box(Modifier.weight(1f).height((70f * m / max).coerceAtLeast(3f).dp).clip(RoundedCornerShape(3.dp)).background(if (m > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)) }
                }
            }
            byProject.forEach { s -> Bar(s.label, s.minutes, total, s.color?.let { Color(it) } ?: Chronora.muted) }
            val byTag = TimeEngine.byTag(entries, from, today, now)
            if (byTag.size > 1 || byTag.firstOrNull()?.label != "#untagged") {
                Text("By tag", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                byTag.take(6).forEach { s -> Bar(s.label, s.minutes, total, MaterialTheme.colorScheme.primary) }
            }
            if (total == 0L) Text("Nothing tracked in this period.", color = Chronora.muted)
        }
        SectionHeader("Entries") { TextButton(onClick = { editing = OfflineTimeEntry(0, "", null, System.currentTimeMillis() - 3_600_000, System.currentTimeMillis(), "", emptySet()) }) { Text("Add manually") } }
        TimeEngine.inRange(entries, from, today, now).map { it.first }.sortedByDescending { it.startEpochMillis }.groupBy { TimeEngine.dayOf(it) }.forEach { (d, list) ->
            Text(relativeDay(d, today) + " · " + hmin(list.sumOf { TimeEngine.minutes(it, now) }), style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
            list.forEach { e ->
                Card(Modifier.fillMaxWidth().clickable { editing = e }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(projects.firstOrNull { it.id == e.projectId }?.let { Color(it.color) } ?: Chronora.muted))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(e.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${clockOf(e.startEpochMillis)}–${e.endEpochMillis?.let(::clockOf) ?: "now"}" + (e.tags.takeIf { it.isNotEmpty() }?.joinToString(" ", " · ") { "#$it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                        }
                        Text(hmin(TimeEngine.minutes(e, now)), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    if (newProject) {
        var name by remember { mutableStateOf("") }
        var color by remember { mutableLongStateOf(TaskColors[1]) }
        AlertDialog(onDismissRequest = { newProject = false }, title = { Text("New project") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TaskColors.forEach { c -> Box(Modifier.size(26.dp).clip(CircleShape).background(Color(c)).clickable { color = c }.then(if (c == color) Modifier.background(Color.White.copy(alpha = .35f)) else Modifier)) } }
            }
        }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { store.saveProject(OfflineProject(0, name, color, emptyList(), null)); newProject = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { newProject = false }) { Text("Cancel") } })
    }
    editing?.let { e -> TimeEntryEditor(e, projects, store) { editing = null } }
}

@Composable
private fun Bar(label: String, minutes: Long, total: Long, color: Color) {
    Column {
        Row { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text(hmin(minutes) + "  " + (if (total > 0) "${100 * minutes / total}%" else ""), style = MaterialTheme.typography.bodySmall, color = Chronora.muted) }
        LinearProgressIndicator(progress = { if (total == 0L) 0f else minutes.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun TimeEntryEditor(e: OfflineTimeEntry, projects: List<OfflineProject>, store: OfflineProductivityStore, close: () -> Unit) {
    val confirm = com.raunak.daytimeline.ui.rememberConfirm()
    val zone = ZoneId.systemDefault()
    fun fmt(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone).toString().take(16).replace('T', ' ')
    fun parse(s: String) = runCatching { LocalDateTime.parse(s.trim().replace(' ', 'T')).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    var label by remember { mutableStateOf(e.label) }
    var start by remember { mutableStateOf(fmt(e.startEpochMillis)) }
    var end by remember { mutableStateOf(e.endEpochMillis?.let(::fmt) ?: "") }
    var project by remember { mutableStateOf(e.projectId) }
    var tags by remember { mutableStateOf(e.tags.joinToString(", ")) }
    val s = parse(start); val f = if (end.isBlank()) null else parse(end)
    val ok = label.isNotBlank() && s != null && (end.isBlank() || (f != null && f > s))
    AlertDialog(onDismissRequest = close, title = { Text(if (e.id == 0L) "Add time" else "Edit entry") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(label, { label = it }, label = { Text("Description") }, singleLine = true)
            com.raunak.daytimeline.ui.PickerField("Start", start, { start = it }, time = true)
            com.raunak.daytimeline.ui.PickerField(if (end.isBlank()) "End · still running" else "End", end, { end = it }, time = true, clearable = true)
            if (end.isNotBlank() && (f == null || s == null || f <= s)) Text("End must be after start", color = Chronora.colors.bad, style = MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(project == null, { project = null }, label = { Text("No project") })
                projects.forEach { p -> FilterChip(project == p.id, { project = p.id }, label = { Text(p.name) }) }
            }
            OutlinedTextField(tags, { tags = it }, label = { Text("Tags") }, singleLine = true)
            if (e.id != 0L) TextButton(onClick = { confirm.ask("this entry") { store.deleteTimeEntry(e.id); close() } }) { Text("Delete entry", color = Chronora.colors.bad) }
        }
    }, confirmButton = {
        Button(enabled = ok, onClick = { store.saveTimeEntry(e.copy(label = label.trim(), startEpochMillis = s!!, endEpochMillis = f, projectId = project, tags = tags.split(',', ' ').map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.toSet())); close() }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
