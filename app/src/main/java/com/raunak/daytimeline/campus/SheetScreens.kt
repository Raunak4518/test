package com.raunak.daytimeline.campus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
internal fun SheetsTab() {
    val context = LocalContext.current
    val store = remember { CampusStore.get(context) }
    val sheets by store.sheets.collectAsStateWithLifecycle()
    val data by store.data.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    val current = sheets.firstOrNull { it.id == open }
    if (current != null) { SheetDetail(current, store) { open = null }; return }
    if (reviewing) { ReviewQueue(sheets, store) { reviewing = false }; return }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val review = SheetEngine.reviewQueue(sheets, today)
            val week = (0L..6L).sumOf { SheetEngine.doneOn(sheets, today.minusDays(it)) }
            SectionCard("Study sheets", "${SheetEngine.doneOn(sheets, today)} done today · $week this week · ${review.size} due for revision", action = { TextButton(onClick = { creating = true }) { Text("New") } }) {
                if (review.isNotEmpty()) Button(onClick = { reviewing = true }) { Text("Revise ${review.size} due items") }
                if (sheets.isNotEmpty()) Heatmap(sheets.flatMap { s -> s.items.mapNotNull { it.doneDate } }.groupingBy { it }.eachCount(), today)
            }
        }
        items(sheets, key = { it.id }) { s ->
            val st = SheetEngine.stats(s, today)
            Card(Modifier.fillMaxWidth().clickable { open = s.id }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.Bold)
                            Text("${s.kind.label}" + (s.subjectId?.let { id -> " · " + (data.subjects.firstOrNull { it.id == id }?.name ?: "") } ?: "") + (s.examDate?.let { " · exam in ${daysUntil(it)} days" } ?: ""), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${st.percent}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { st.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${st.done}/${st.total} · today ${st.doneToday}/${s.dailyTarget} · streak ${st.streak}d" + if (st.dueForReview > 0) " · ${st.dueForReview} to revise" else "", style = MaterialTheme.typography.bodySmall)
                    if (st.byDifficulty.isNotEmpty()) Text(st.byDifficulty.entries.joinToString("   ") { "${it.key.label} ${it.value.first}/${it.value.second}" }, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (creating) NewSheetDialog(store, data) { creating = false }
}

/** Last 15 weeks of completed items, one cell per day. */
@Composable
private fun Heatmap(perDay: Map<String, Int>, today: LocalDate) {
    val weeks = 15
    val start = today.minusWeeks((weeks - 1).toLong()).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (0 until weeks).forEach { w ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                (0 until 7).forEach { d ->
                    val day = start.plusDays((w * 7 + d).toLong())
                    val n = perDay[day.toString()] ?: 0
                    val color = when {
                        day.isAfter(today) -> Color.Transparent
                        n == 0 -> empty
                        else -> base.copy(alpha = (0.3f + 0.2f * n).coerceAtMost(1f))
                    }
                    Surface(color = color, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.size(12.dp)) {}
                }
            }
        }
    }
}

@Composable
private fun NewSheetDialog(store: CampusStore, data: CampusData, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SheetKind.SUBJECT) }
    var subjectId by remember { mutableStateOf<Long?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text("New sheet") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("Ready-made", style = MaterialTheme.typography.labelLarge) }
            items(CampusStore.templates) { (asset, title, k) ->
                OutlinedButton(onClick = { store.updateSheets { it + store.template(asset, title, k) }; close() }, modifier = Modifier.fillMaxWidth()) { Text(title) }
            }
            item { HorizontalDivider(); Text("Your own (syllabus, question list…)", style = MaterialTheme.typography.labelLarge) }
            item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(SheetKind.values().toList()) { k -> FilterChip(kind == k, { kind = k }, label = { Text(k.label) }) } } }
            if (data.subjects.isNotEmpty()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(data.subjects) { s -> FilterChip(subjectId == s.id, { subjectId = if (subjectId == s.id) null else s.id; if (name.isBlank()) name = s.name }, label = { Text(s.name) }) } }
            }
            item {
                OutlinedTextField(text, { text = it }, label = { Text("Paste topics / questions (optional)") }, placeholder = { Text("# Unit 1\nSearch algorithms\nA* and heuristics\n# Unit 2\n- Bayes nets | https://…") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                Text("Lines starting with # or ending with : become sections. Add \"| link\" and \"| E/M/H\" if you like.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }, confirmButton = {
        Button(enabled = name.isNotBlank(), onClick = {
            val id = store.nextId()
            store.updateSheets { it + StudySheet(id, name.trim(), kind, SheetEngine.parse(text, id + 1), subjectId = subjectId, dailyTarget = if (kind == SheetKind.DSA) 3 else 2) }
            close()
        }) { Text("Create") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun SheetDetail(sheet: StudySheet, store: CampusStore, back: () -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val st = SheetEngine.stats(sheet, today)
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<ItemStatus?>(null) }
    var diffFilter by remember { mutableStateOf<Difficulty?>(null) }
    var section by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SheetItem?>(null) }
    val filtered = sheet.items.filter { i ->
        (query.isBlank() || i.title.contains(query, true) || i.notes.contains(query, true)) &&
            (statusFilter == null || i.status == statusFilter) && (diffFilter == null || i.difficulty == diffFilter) && (section == null || i.section == section)
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) { Text(sheet.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${st.done}/${st.total} · ${st.percent}% · streak ${st.streak}d", style = MaterialTheme.typography.bodySmall) }
                var renaming by remember { mutableStateOf(false) }
                TextButton(onClick = { renaming = true }) { Text("Rename") }
                TextButton(onClick = { adding = true }) { Text("Add") }
                if (renaming) {
                    var name by remember { mutableStateOf(sheet.name) }
                    AlertDialog(onDismissRequest = { renaming = false }, title = { Text("Rename sheet") }, text = {
                        OutlinedTextField(name, { name = it }, singleLine = true)
                    }, confirmButton = {
                        Button(onClick = { if (name.isNotBlank()) store.updateSheet(sheet.id) { it.copy(name = name.trim()) }; renaming = false }) { Text("Save") }
                    }, dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } })
                }
            }
        }
        item {
            SectionCard("Progress") {
                Stepper("Daily target", "${sheet.dailyTarget}", { store.updateSheet(sheet.id) { it.copy(dailyTarget = (it.dailyTarget - 1).coerceAtLeast(0)) } }, { store.updateSheet(sheet.id) { it.copy(dailyTarget = it.dailyTarget + 1) } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateButton("Exam", sheet.examDate?.let { LocalDate.parse(it) } ?: today.plusDays(30)) { d -> store.updateSheet(sheet.id) { it.copy(examDate = d.toString()) } }
                }
                sheet.examDate?.let { ex ->
                    val left = st.total - st.done
                    val days = (daysUntil(ex, today) ?: 0).coerceAtLeast(1)
                    Text("${daysUntil(ex, today)} days to exam · ${left} left → ${"%.1f".format(left.toDouble() / days)} per day", style = MaterialTheme.typography.bodySmall)
                    var showPlan by remember { mutableStateOf(false) }
                    val buffer = store.data.value.settings.examBufferDays
                    TextButton(onClick = { showPlan = !showPlan }) { Text(if (showPlan) "Hide revision plan" else "Show day-by-day revision plan") }
                    if (showPlan) {
                        runCatching { LocalDate.parse(ex) }.getOrNull()?.let { exam ->
                            StudyEngines.revisionPlan(sheet.items, exam, today, buffer).toSortedMap().forEach { (day, items) ->
                                Text("${day.dayOfWeek.name.take(3)} ${day}: " + items.joinToString { it.title }, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Last $buffer day(s) before the exam are kept for full revision (change in Settings).", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                st.sections.forEach { p ->
                    Row(Modifier.fillMaxWidth().clickable { section = if (section == p.section) null else p.section }, verticalAlignment = Alignment.CenterVertically) {
                        Text(p.section, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = if (section == p.section) FontWeight.Bold else FontWeight.Normal)
                        LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.width(80.dp))
                        Text("  ${p.done}/${p.total}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(ItemStatus.values().toList()) { s -> FilterChip(statusFilter == s, { statusFilter = if (statusFilter == s) null else s }, label = { Text(s.label) }) }
                items(Difficulty.values().toList()) { d -> FilterChip(diffFilter == d, { diffFilter = if (diffFilter == d) null else d }, label = { Text(d.label) }) }
            }
        }
        var lastSection = ""
        filtered.forEachIndexed { index, item ->
            if (item.section != lastSection) { val sec = item.section; item(key = "h$index") { Text(sec, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }; lastSection = sec }
            item(key = "i${item.id}") { ItemRow(sheet, item, store, onEdit = { editing = item }) { openUrl(context, item.url) } }
        }
        item { TextButton(onClick = { store.updateSheets { l -> l.filterNot { it.id == sheet.id } }; back() }) { Text("Delete sheet", color = MaterialTheme.colorScheme.error) } }
    }
    if (adding) AddItemsDialog(sheet, store) { adding = false }
    editing?.let { ItemDialog(sheet, it, store) { editing = null } }
}

@Composable
private fun ItemRow(sheet: StudySheet, item: SheetItem, store: CampusStore, onEdit: () -> Unit, onOpen: () -> Unit) {
    val today = LocalDate.now()
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { onEdit() }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(item.status.done, { on -> store.updateItem(sheet.id, item.id) { SheetEngine.setStatus(it, if (on) ItemStatus.SOLVED else ItemStatus.TODO, today, store.gaps) } })
        Column(Modifier.weight(1f)) {
            Text(item.title, textDecoration = if (item.status.done) TextDecoration.LineThrough else null, style = MaterialTheme.typography.bodyMedium)
            val meta = listOfNotNull(
                item.difficulty?.label,
                item.status.takeIf { it != ItemStatus.TODO && it != ItemStatus.SOLVED }?.label,
                item.nextReview?.takeIf { item.status.done }?.let { if (it <= today.toString()) "revise now" else "revise $it" },
                item.revisions.takeIf { it > 0 }?.let { "×$it revised" },
                item.notes.takeIf { it.isNotBlank() }?.let { "📝" }
            )
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = when (item.difficulty) { Difficulty.EASY -> Color(0xFF2E7D32); Difficulty.HARD -> Color(0xFFC62828); else -> MaterialTheme.colorScheme.onSurfaceVariant })
        }
        IconButton(onClick = { store.updateItem(sheet.id, item.id) { it.copy(starred = !it.starred) } }) { Icon(if (item.starred) Icons.Default.Star else Icons.Default.StarBorder, "Star") }
        if (item.url.isNotBlank()) TextButton(onClick = onOpen) { Text("Open") }
        Box {
            TextButton(onClick = { menu = true }) { Text("⋯") }
            DropdownMenu(menu, { menu = false }) {
                ItemStatus.values().forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { store.updateItem(sheet.id, item.id) { SheetEngine.setStatus(it, s, today, store.gaps) }; menu = false }) }
                if (item.status.done) DropdownMenuItem(text = { Text("Revised today") }, onClick = { store.updateItem(sheet.id, item.id) { SheetEngine.markRevised(it, today, store.gaps) }; menu = false })
            }
        }
    }
}

@Composable
private fun ItemDialog(sheet: StudySheet, item: SheetItem, store: CampusStore, close: () -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var url by remember { mutableStateOf(item.url) }
    var notes by remember { mutableStateOf(item.notes) }
    var minutes by remember { mutableStateOf(if (item.minutes == 0) "" else item.minutes.toString()) }
    var diff by remember { mutableStateOf(item.difficulty) }
    AlertDialog(onDismissRequest = close, title = { Text("Edit") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") })
            OutlinedTextField(url, { url = it }, label = { Text("Link") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Difficulty.values().forEach { d -> FilterChip(diff == d, { diff = if (diff == d) null else d }, label = { Text(d.label) }) } }
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Minutes taken") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes: approach, mistakes, pattern") }, minLines = 4)
        }
    }, confirmButton = {
        Button(onClick = { store.updateItem(sheet.id, item.id) { it.copy(title = title.trim().ifBlank { it.title }, url = url.trim(), notes = notes, minutes = minutes.toIntOrNull() ?: 0, difficulty = diff) }; close() }) { Text("Save") }
    }, dismissButton = {
        TextButton(onClick = { store.updateSheet(sheet.id) { s -> s.copy(items = s.items.filterNot { it.id == item.id }) }; close() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
    })
}

@Composable
private fun AddItemsDialog(sheet: StudySheet, store: CampusStore, close: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val section = sheet.items.lastOrNull()?.section ?: "General"
    AlertDialog(onDismissRequest = close, title = { Text("Add to ${sheet.name}") }, text = {
        Column {
            OutlinedTextField(text, { text = it }, placeholder = { Text("One per line\n# New section\nQuestion title | https://link | M") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            Text("Items without a section go under \"$section\".", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        Button(onClick = { store.updateSheet(sheet.id) { s -> s.copy(items = s.items + SheetEngine.parse(text, store.nextId(), section)) }; close() }) { Text("Add") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun ReviewQueue(sheets: List<StudySheet>, store: CampusStore, back: () -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val queue = SheetEngine.reviewQueue(sheets, today)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
                Text("Revision · ${queue.size} due", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("Re-solve from memory. Gaps grow " + store.gaps.joinToString(" → ") + " days each time you get it right (change in Campus → Settings).", style = MaterialTheme.typography.bodySmall)
        }
        items(queue, key = { "${it.first.id}-${it.second.id}" }) { (s, item) ->
            Card { Column(Modifier.padding(12.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold)
                Text("${s.name} · ${item.section}" + if (item.notes.isNotBlank()) "\n${item.notes}" else "", style = MaterialTheme.typography.bodySmall)
                Row {
                    if (item.url.isNotBlank()) TextButton(onClick = { openUrl(context, item.url) }) { Text("Open") }
                    TextButton(onClick = { store.updateItem(s.id, item.id) { SheetEngine.markRevised(it, today, store.gaps) } }) { Text("Got it") }
                    TextButton(onClick = { store.updateItem(s.id, item.id) { it.copy(status = ItemStatus.REVISE, nextReview = today.plusDays(1).toString()) } }) { Text("Struggled — tomorrow") }
                }
            } }
        }
        if (queue.isEmpty()) item { Text("Nothing due. 🎉") }
    }
}
