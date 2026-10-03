package com.raunak.daytimeline.productivity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.raunak.daytimeline.features.*
import com.raunak.daytimeline.ui.*
import java.text.DateFormat
import java.util.Date

/** Soft note colours (tinted over the card so they work in light and dark). 0 = plain. */
val NoteColors = listOf(0L, 0xFFF28B82, 0xFFFBBC04, 0xFFFFF475, 0xFFCCFF90, 0xFFA7FFEB, 0xFFAECBFA, 0xFFD7AEFB, 0xFFE6C9A8)

@Composable
fun noteTint(color: Long): Color {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    return if (color == 0L) base else Color(color).copy(alpha = if (isSystemInDarkTheme()) .22f else .45f).compositeOver(base)
}

/** Keep-style notes: search, label filter, pinned first, two-column colour cards, tickable checklists. */
@Composable
fun NotesSection(notes: List<OfflineNote>, store: OfflineProductivityStore) {
    var query by remember { mutableStateOf("") }
    var label by remember { mutableStateOf<String?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<OfflineNote?>(null) }
    val labels = remember(notes) { (notes.map { it.folder } + notes.flatMap { NoteEngine.tags(it) }).filter { it.isNotBlank() }.distinct().sorted() }
    val shown = notes.filter { it.archived == showArchived && NoteEngine.matches(it, query) && (label == null || it.folder == label || label in NoteEngine.tags(it)) }
        .sortedWith(compareByDescending<OfflineNote> { it.pinned }.thenByDescending { it.updatedAt })
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        SectionHeader("Notes") { TextButton(onClick = { editing = OfflineNote(0, "", "", emptySet(), 0) }) { Text("New note") } }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search notes") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(label == null && !showArchived, { label = null; showArchived = false }, label = { Text("All") })
            labels.forEach { l -> FilterChip(label == l, { label = if (label == l) null else l }, label = { Text(l) }) }
            FilterChip(showArchived, { showArchived = !showArchived }, label = { Text("Archive") }, leadingIcon = { Icon(Icons.Default.Archive, null, Modifier.size(16.dp)) })
        }
        if (shown.isEmpty()) EmptyState(if (query.isBlank()) "No notes here" else "No matches", "Notes can be text or a checklist. Link notes with [[Title]].")
        // Two balanced columns (masonry-like): each note goes to the shorter column.
        val left = mutableListOf<OfflineNote>(); val right = mutableListOf<OfflineNote>()
        var lh = 0; var rh = 0
        shown.forEach { n -> val h = 2 + n.body.length / 40 + n.body.lines().size.coerceAtMost(8); if (lh <= rh) { left += n; lh += h } else { right += n; rh += h } }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) { left.forEach { NoteCard(it, store) { editing = it } } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) { right.forEach { NoteCard(it, store) { editing = it } } }
        }
    }
    editing?.let { n -> NoteEditor(n, notes, store) { editing = null } }
}

@Composable
fun NoteCard(n: OfflineNote, store: OfflineProductivityStore, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), colors = CardDefaults.cardColors(containerColor = noteTint(n.color))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(n.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (n.pinned) Icon(Icons.Default.PushPin, "Pinned", Modifier.size(16.dp), tint = Chronora.muted)
            }
            if (NoteEngine.isChecklist(n.body)) {
                val items = NoteEngine.sorted(n.body)
                items.filter { it.checked != null }.take(6).forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { store.toggleNoteLine(n.id, l.index) }) {
                        Icon(if (l.checked == true) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null, Modifier.size(18.dp), tint = Chronora.muted)
                        Text(" " + l.text, style = MaterialTheme.typography.bodySmall, textDecoration = if (l.checked == true) TextDecoration.LineThrough else null, color = if (l.checked == true) Chronora.muted else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val (d, t) = NoteEngine.progress(n.body)
                if (t > 6 || d > 0) Text("$d/$t done", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
            } else if (n.body.isNotBlank()) Text(n.body, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
            val chips = (listOf(n.folder).filter { it.isNotBlank() && it != "General" } + NoteEngine.tags(n)).distinct()
            if (chips.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { chips.take(2).forEach { Pill(it, MaterialTheme.colorScheme.primary) } }
        }
    }
}

/** Full-screen editor: title, body or checklist, colour, label, tags, pin, archive, links. */
@Composable
fun NoteEditor(note: OfflineNote, all: List<OfflineNote>, store: OfflineProductivityStore, close: () -> Unit) {
    val confirm = com.raunak.daytimeline.ui.rememberConfirm()
    var title by remember { mutableStateOf(note.title) }
    var body by remember { mutableStateOf(note.body) }
    var folder by remember { mutableStateOf(note.folder.ifBlank { "General" }) }
    var tags by remember { mutableStateOf(NoteEngine.tags(note).joinToString(", ")) }
    var color by remember { mutableLongStateOf(note.color) }
    var pinned by remember { mutableStateOf(note.pinned) }
    var newItem by remember { mutableStateOf("") }
    fun current() = note.copy(title = title, body = body, folder = folder.trim().ifBlank { "General" }, tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet(), color = color, pinned = pinned)
    fun saveAndClose() { store.saveNote(current()); close() }
    Dialog(onDismissRequest = ::saveAndClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = noteTint(color)) {
            Column {
                ChronoraTopBar(if (note.id == 0L) "New note" else "Note", ::saveAndClose, subtitle = if (note.updatedAt > 0) "Edited " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.updatedAt)) else null) {
                    IconButton(onClick = { pinned = !pinned }) { Icon(Icons.Default.PushPin, if (pinned) "Unpin" else "Pin", tint = if (pinned) MaterialTheme.colorScheme.primary else Chronora.muted) }
                    IconButton(onClick = { body = NoteEngine.toggleChecklist(body) }) { Icon(Icons.Default.Checklist, "Checklist on/off") }
                    if (note.id != 0L) {
                        IconButton(onClick = { store.saveNote(current().copy(archived = !note.archived)); close() }) { Icon(if (note.archived) Icons.Default.Unarchive else Icons.Default.Archive, if (note.archived) "Unarchive" else "Archive") }
                        IconButton(onClick = { confirm.ask("this note") { store.deleteNote(note.id); close() } }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(title, { title = it }, placeholder = { Text("Title") }, textStyle = MaterialTheme.typography.titleLarge, colors = clearField(), modifier = Modifier.fillMaxWidth())
                    if (NoteEngine.isChecklist(body)) {
                        NoteEngine.sorted(body).filter { it.checked != null }.forEach { l ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(l.checked == true, { body = NoteEngine.toggleLine(body, l.index) })
                                Text(l.text, Modifier.weight(1f), textDecoration = if (l.checked == true) TextDecoration.LineThrough else null)
                                IconButton(onClick = { body = body.lines().filterIndexed { i, _ -> i != l.index }.joinToString("\n") }) { Icon(Icons.Default.Close, "Remove") }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(newItem, { newItem = it }, placeholder = { Text("List item") }, singleLine = true, modifier = Modifier.weight(1f))
                            IconButton(onClick = { if (newItem.isNotBlank()) { body = (body.trimEnd() + "\n[ ] " + newItem.trim()).trimStart(); newItem = "" } }) { Icon(Icons.Default.Add, "Add item") }
                        }
                    } else TextField(body, { body = it }, placeholder = { Text("Note — link other notes with [[Title]]") }, colors = clearField(), modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        NoteColors.forEach { c ->
                            Box(Modifier.size(30.dp).clip(CircleShape).background(if (c == 0L) MaterialTheme.colorScheme.surface else Color(c)).border(if (c == color) 3.dp else 1.dp, if (c == color) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape).clickable { color = c })
                        }
                    }
                    OutlinedTextField(folder, { folder = it }, label = { Text("Label / folder") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tags, { tags = it }, label = { Text("Tags, comma separated") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val links = NoteEngine.links(current(), all)
                    val back = all.filter { it.id != note.id && note.title.isNotBlank() && (it.body.contains("[[${note.title}]]", true) || it.body.contains(note.title, true)) }
                    if (links.isNotEmpty()) Text("Links to: " + links.joinToString { it.title }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    if (back.isNotEmpty()) Text("Mentioned in: " + back.joinToString { it.title }, style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                }
            }
        }
    }
}

@Composable
private fun clearField() = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
