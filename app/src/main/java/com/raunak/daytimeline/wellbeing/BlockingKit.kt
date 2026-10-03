package com.raunak.daytimeline.wellbeing

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.ui.Chronora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A launchable app on the phone. */
data class LauncherApp(val pkg: String, val label: String, val category: Int)

/** Installed launchable apps, read once per process (the package manager call is slow). */
object LauncherApps {
    @Volatile private var cache: List<LauncherApp>? = null
    fun get(context: android.content.Context): List<LauncherApp> = cache ?: run {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { LauncherApp(it.packageName, pm.getApplicationLabel(it).toString(), it.category) }
            .sortedBy { it.label.lowercase() }
            .also { cache = it }
    }
}

/** The app list, loaded off the main thread; empty for the first frame. */
@Composable
fun rememberLauncherApps(): List<LauncherApp> {
    val context = LocalContext.current
    val apps by produceState(emptyList<LauncherApp>()) { value = withContext(Dispatchers.IO) { LauncherApps.get(context) } }
    return apps
}

/** Package name → readable label, falling back to the package manager for apps without a launcher icon. */
@Composable
fun rememberAppNamer(apps: List<LauncherApp>): (String) -> String {
    val context = LocalContext.current
    val labels = remember(apps) { HashMap<String, String>().apply { apps.forEach { put(it.pkg, it.label) } } }
    return remember(labels) { { pkg: String -> labels.getOrPut(pkg) { WellbeingAlarmReceiver.label(context, pkg) } } }
}

/** Full-height searchable app list with icons. Tap rows to tick them, then Done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppChooser(title: String, selected: Set<String>, close: () -> Unit, onDone: (Set<String>) -> Unit) {
    val apps = rememberLauncherApps()
    var chosen by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search apps") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            val shown = apps.filter { query.isBlank() || it.label.contains(query, true) }
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 520.dp)) {
                if (apps.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                items(shown, key = { it.pkg }) { app ->
                    val on = app.pkg in chosen
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { chosen = if (on) chosen - app.pkg else chosen + app.pkg }
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.pkg, 36.dp)
                        Text(app.label, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Checkbox(on, { chosen = if (on) chosen - app.pkg else chosen + app.pkg })
                    }
                }
            }
            Button(onClick = { onDone(chosen) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (chosen.size == 1) "Done · 1 app" else "Done · ${chosen.size} apps")
            }
        }
    }
}

/** Chosen apps as icon chips with a remove cross, plus an Add chip at the end. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppChips(packages: Set<String>, name: (String) -> String, emptyText: String, addLabel: String = "Add apps", onRemove: (String) -> Unit, onAdd: () -> Unit) {
    if (packages.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = Chronora.muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        packages.sortedBy { name(it).lowercase() }.forEach { pkg ->
            Row(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 24.dp)
                Text(name(pkg), Modifier.padding(start = 8.dp).widthIn(max = 140.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onRemove(pkg) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Remove ${name(pkg)}", Modifier.size(16.dp)) }
            }
        }
        AssistChip(onClick = onAdd, label = { Text(addLabel) }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(50))
    }
}

/** Seven round day toggles, Monday first (1 = Monday). */
@Composable
fun DayCircles(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..7).forEach { d ->
            val on = d in days
            Box(Modifier.size(38.dp).clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onChange(if (on) days - d else days + d) }, contentAlignment = Alignment.Center) {
                Text(java.time.DayOfWeek.of(d).name.take(1), color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

fun dayText(days: Set<Int>): String = when {
    days.size == 7 -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
    days == setOf(6, 7) -> "Weekends"
    else -> days.sorted().joinToString(" ") { java.time.DayOfWeek.of(it).name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
}

fun clockText(minute: Int) = "%02d:%02d".format((minute / 60) % 24, minute % 60)
fun hmText(m: Int) = when { m <= 0 -> "0m"; m >= 60 && m % 60 == 0 -> "${m / 60}h"; m >= 60 -> "${m / 60}h ${m % 60}m"; else -> "${m}m" }

/** A setting whose value is a clock time: the chip opens the system time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockRow(label: String, minute: Int, enabled: Boolean = true, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled) { open = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else Chronora.muted)
        Text(clockText(minute), Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
    if (open) ClockDialog(minute, { open = false }) { onPick(it); open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockDialog(initial: Int, close: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState((initial / 60) % 24, initial % 60, true)
    AlertDialog(onDismissRequest = close, text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

/** A folded section: a tappable header with a chevron, content shown only when opened. */
@Composable
fun Expandable(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chev")
    Column {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Default.ExpandMore, if (open) "Hide" else "Show", Modifier.rotate(turn), tint = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(open) { Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }
    }
}

/** One missing permission: what it unlocks and a button that opens the right settings page. */
data class SetupStep(val label: String, val granted: Boolean, val open: () -> Unit)

/** Shown only while something is missing; lists just the missing permissions. */
@Composable
fun SetupBanner(steps: List<SetupStep>) {
    val missing = steps.filterNot { it.granted }
    if (missing.isEmpty()) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Chronora.colors.warn.copy(alpha = .14f))
        .border(1.dp, Chronora.colors.warn.copy(alpha = .35f), RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = Chronora.colors.warn)
            Text("  Finish setup to make this work", fontWeight = FontWeight.SemiBold)
        }
        missing.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = s.open) { Text("Allow") }
            }
        }
    }
}

/** A thin bar showing used against a limit; turns amber near the limit and red past it. */
@Composable
fun UsageBar(used: Int, limit: Int, modifier: Modifier = Modifier) {
    val ratio = if (limit <= 0) 0f else used.toFloat() / limit
    val color = when { ratio >= 1f -> Chronora.colors.bad; ratio >= .8f -> Chronora.colors.warn; else -> MaterialTheme.colorScheme.primary }
    LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
        color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
}

fun openSettings(context: android.content.Context, action: String) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
