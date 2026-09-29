package com.raunak.daytimeline.productivity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.raunak.daytimeline.features.*
import com.raunak.daytimeline.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/** Routines (Routinery-style): ordered steps with a timer each, played one step at a time. */
@Composable
fun RoutinesSection(routines: List<OfflineRoutine>, store: OfflineProductivityStore) {
    var playing by remember { mutableStateOf<OfflineRoutine?>(null) }
    var editing by remember { mutableStateOf<OfflineRoutine?>(null) }
    var adding by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        SectionHeader("Routines") { TextButton(onClick = { adding = true }) { Text("New routine") } }
        routines.filterNot { it.archived }.forEach { r ->
            val doneToday = r.lastCompletedDate == today.toString()
            val now = LocalTime.now().let { it.hour * 60 + it.minute }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.inner), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Text("${r.steps.size} steps · ${RoutineEngine.totalMinutes(r)} min · start now, done by ${hm(RoutineEngine.endsAt(r, now))}", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                        }
                        val streak = RoutineEngine.streak(r, today)
                        if (streak > 0) Pill("🔥 $streak", Chronora.colors.warn)
                    }
                    Text(r.steps.joinToString("  →  ") { it.title }, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { playing = r }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(if (doneToday) "Run again" else "Start") }
                        if (doneToday) Pill("Done today", Chronora.colors.good) else TextButton(onClick = { store.setRoutineCompleted(r.id) }) { Text("Mark done") }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { editing = r }) { Icon(Icons.Default.Edit, "Edit routine") }
                    }
                }
            }
        }
    }
    playing?.let { r -> RoutinePlayer(r, onFinish = { store.setRoutineCompleted(r.id); playing = null }) { playing = null } }
    if (adding) RoutineEditor(null, { store.saveRoutine(it); adding = false }) { adding = false }
    editing?.let { r -> RoutineEditor(r, { store.saveRoutine(it); editing = null }, onDelete = { store.deleteRoutine(r.id); editing = null }) { editing = null } }
}

/** Full-screen player: big countdown per step, next step preview, pause, skip, +1 minute. */
@Composable
fun RoutinePlayer(r: OfflineRoutine, onFinish: () -> Unit, close: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var left by remember { mutableLongStateOf(r.steps.firstOrNull()?.minutes?.times(60L) ?: 0L) }
    var total by remember { mutableLongStateOf(left) }
    var running by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    fun next() {
        if (index + 1 >= r.steps.size) { finished = true; running = false; return }
        index++; left = r.steps[index].minutes * 60L; total = left
    }
    LaunchedEffect(running, index) {
        while (running && !finished) { delay(1000); if (left > 0) left-- else next() }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                ChronoraTopBar(r.name, close, subtitle = if (finished) "Finished" else "Step ${index + 1} of ${r.steps.size}")
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (finished) {
                        Spacer(Modifier.height(40.dp))
                        Text("🎉", style = MaterialTheme.typography.displayLarge)
                        Text("${r.name} complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${r.steps.size} steps · ${RoutineEngine.totalMinutes(r)} minutes", color = Chronora.muted)
                        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Save & close") }
                        return@Column
                    }
                    val step = r.steps[index]
                    LinearProgressIndicator(progress = { (index + 1f - left.toFloat() / total.coerceAtLeast(1)) / r.steps.size }, modifier = Modifier.fillMaxWidth())
                    Text(step.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (step.note.isNotBlank()) Text(step.note, color = Chronora.muted, textAlign = TextAlign.Center)
                    val ring = MaterialTheme.colorScheme.primary
                    val track = MaterialTheme.colorScheme.surfaceVariant
                    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val s = 14.dp.toPx(); val arc = Size(size.width - s, size.height - s); val tl = Offset(s / 2, s / 2)
                            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(s))
                            drawArc(ring, -90f, 360f * (1f - left.toFloat() / total.coerceAtLeast(1)), false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
                        }
                        Text("%02d:%02d".format(left / 60, left % 60), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { left += 60; total += 60 }) { Text("+1") }
                        Button(onClick = { running = !running }, modifier = Modifier.height(52.dp)) { Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (running) "Pause" else "Resume") }
                        FilledTonalIconButton(onClick = { next() }) { Icon(Icons.Default.Check, "Step done") }
                    }
                    r.steps.getOrNull(index + 1)?.let { Text("Next: ${it.title} · ${it.minutes} min", color = Chronora.muted) }
                }
            }
        }
    }
}

@Composable
fun RoutineEditor(initial: OfflineRoutine?, onSave: (OfflineRoutine) -> Unit, onDelete: (() -> Unit)? = null, close: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    val steps = remember { mutableStateListOf<OfflineRoutineStep>().apply { addAll(initial?.steps ?: listOf(OfflineRoutineStep("", 5))) } }
    AlertDialog(onDismissRequest = close, title = { Text(if (initial == null) "New routine" else "Edit routine") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name (Morning, Before class…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            steps.forEachIndexed { i, s ->
                Card { Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(s.title, { steps[i] = s.copy(title = it) }, label = { Text("Step ${i + 1}") }, singleLine = true, modifier = Modifier.weight(1f))
                        Column {
                            IconButton(onClick = { if (i > 0) { steps.removeAt(i); steps.add(i - 1, s) } }, Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = { if (i < steps.size - 1) { steps.removeAt(i); steps.add(i + 1, s) } }, Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                        }
                    }
                    Stepper("Minutes", "${s.minutes}", { steps[i] = s.copy(minutes = (s.minutes - 1).coerceAtLeast(1)) }, { steps[i] = s.copy(minutes = (s.minutes + 1).coerceAtMost(240)) })
                    TextButton(onClick = { steps.removeAt(i) }) { Text("Remove step", color = Chronora.colors.bad) }
                } }
            }
            OutlinedButton(onClick = { steps.add(OfflineRoutineStep("", 5)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Add step") }
            Text("Total ${steps.sumOf { it.minutes }} min", color = Chronora.muted)
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete routine", color = Chronora.colors.bad) }
        }
    }, confirmButton = {
        val clean = steps.filter { it.title.isNotBlank() }
        Button(enabled = name.isNotBlank() && clean.isNotEmpty(), onClick = { onSave((initial ?: OfflineRoutine(0, name, clean, false)).copy(name = name, steps = clean)) }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
