package com.raunak.daytimeline

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private val AInk = Color(0xFF17221E)
private val ASage = Color(0xFF55786A)
private val AMuted = Color(0xFF74807A)
private val ACard = Color(0xFFFFFDF8)
private val ABg = Color(0xFFF4F1E9)

class AdvancedStore(context: Context) {
    private val prefs = context.getSharedPreferences("advanced_productivity", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _routines = MutableStateFlow(read("routines", defaultRoutines()))
    val routines = _routines.asStateFlow()
    private val _journal = MutableStateFlow(read("journal", emptyList<JournalEntry>()))
    val journal = _journal.asStateFlow()
    private val _timeLogs = MutableStateFlow(read("timelogs", emptyList<TimeLog>()))
    val timeLogs = _timeLogs.asStateFlow()

    fun addRoutine(name: String, steps: List<RoutineStep>) = update(_routines, "routines") { it + Routine(System.currentTimeMillis(), name, steps) }
    fun deleteRoutine(id: Long) = update(_routines, "routines") { it.filterNot { r -> r.id == id } }
    fun saveJournal(entry: JournalEntry) = update(_journal, "journal") { list -> (list.filterNot { it.date == entry.date } + entry).sortedByDescending { it.date }.take(365) }
    fun startTimer(label: String) = update(_timeLogs, "timelogs") { it + TimeLog(System.currentTimeMillis(), label, System.currentTimeMillis(), 0) }
    fun stopTimer(id: Long) = update(_timeLogs, "timelogs") { list -> list.map { if (it.id == id && it.end == 0L) it.copy(end = System.currentTimeMillis()) else it } }
    private fun <T> update(flow: MutableStateFlow<List<T>>, key: String, f: (List<T>) -> List<T>) { val next = f(flow.value); flow.value = next; prefs.edit().putString(key, gson.toJson(next)).apply() }
    private inline fun <reified T> read(key: String, fallback: T): T = try { prefs.getString(key, null)?.let { gson.fromJson<T>(it, object : TypeToken<T>() {}.type) } ?: fallback } catch (_: Exception) { fallback }
    private fun defaultRoutines() = listOf(
        Routine(1, "Morning reset", listOf(RoutineStep("Hydrate",5), RoutineStep("Move",20), RoutineStep("Shower",15), RoutineStep("Plan",10))),
        Routine(2, "Deep work block", listOf(RoutineStep("Plan outcome",5), RoutineStep("Focus",50), RoutineStep("Break",10), RoutineStep("Focus",50), RoutineStep("Review",5))),
        Routine(3, "Night shutdown", listOf(RoutineStep("Clear desk",5), RoutineStep("Review day",10), RoutineStep("Plan tomorrow",10), RoutineStep("Wind down",20)))
    )
}

data class Routine(val id: Long, val name: String, val steps: List<RoutineStep>)
data class RoutineStep(val name: String, val minutes: Int)
data class JournalEntry(val date: String, val mood: Int, val energy: Int, val wins: String, val blockers: String, val gratitude: String)
data class TimeLog(val id: Long, val label: String, val start: Long, val end: Long)
data class UsageStat(val packageName: String, val millis: Long)

@Composable
fun AdvancedHub(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AdvancedStore(context.applicationContext) }
    val routines by store.routines.collectAsState()
    val journal by store.journal.collectAsState()
    val logs by store.timeLogs.collectAsState()
    var section by rememberSaveable { mutableStateOf("Overview") }
    val sections = listOf("Overview", "Routines", "Journal", "Time", "Device")
    MaterialTheme(colorScheme = lightColorScheme(background = ABg, surface = ACard, primary = ASage, onSurface = AInk)) {
        Scaffold(topBar = { TopAppBar(title = { Text("Productivity Lab", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClose) { Icon(Icons.Default.Close, "Close") } }) }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                ScrollableTabRow(selectedTabIndex = sections.indexOf(section), edgePadding = 12.dp) { sections.forEach { label -> Tab(section == label, { section = label }, text = { Text(label) }) } }
                when (section) {
                    "Overview" -> AdvancedOverview(routines, journal, logs)
                    "Routines" -> RoutineScreen(routines, store)
                    "Journal" -> JournalScreen(journal, store)
                    "Time" -> TimeTrackingScreen(logs, store)
                    else -> DeviceInsights(context)
                }
            }
        }
    }
}

@Composable private fun AdvancedOverview(routines: List<Routine>, journal: List<JournalEntry>, logs: List<TimeLog>) {
    val today = LocalDate.now()
    val todayLogs = logs.filter { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.start), java.time.ZoneId.systemDefault()).toLocalDate() == today }
    val minutes = todayLogs.sumOf { endOrNow(it) - it.start }.coerceAtLeast(0) / 60000
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Productivity cockpit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Deep local tools. No account, backend or paid service.", color = AMuted) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("Routines", routines.size.toString(), Modifier.weight(1f)); Metric("Actual time", "${minutes}m", Modifier.weight(1f)); Metric("Journal", journal.size.toString(), Modifier.weight(1f)) } }
        item { FeatureCard(Icons.Default.AutoAwesome, "Daily reflection", "Record mood, energy, wins, blockers and gratitude so your review has real context.") }
        item { FeatureCard(Icons.Default.Timelapse, "Actual time tracking", "Measure elapsed work independently from planned blocks and compare plan against reality.") }
        item { FeatureCard(Icons.Default.Repeat, "Routines", "Reusable multi-step sequences with explicit durations rather than decorative labels.") }
        item { FeatureCard(Icons.Default.PhoneAndroid, "Device awareness", "Optionally inspect Android screen-time statistics locally to identify distraction patterns.") }
    }
}

@Composable private fun Metric(label: String, value: String, modifier: Modifier = Modifier) { Surface(RoundedCornerShape(18.dp), color = ACard, modifier = modifier) { Column(Modifier.padding(13.dp)) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, color = AMuted, style = MaterialTheme.typography.labelSmall) } } }
@Composable private fun FeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) { Surface(RoundedCornerShape(22.dp), color = ACard) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) { Icon(icon, null, tint = ASage); Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(body, color = AMuted, style = MaterialTheme.typography.bodySmall) } } } }

@Composable private fun RoutineScreen(routines: List<Routine>, store: AdvancedStore) {
    var name by remember { mutableStateOf("") }; var showAdd by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Column { Text("Routines", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Reusable duration-aware sequences", color = AMuted) }; IconButton({ showAdd = true }) { Icon(Icons.Default.Add, "Add routine") } } }
        items(routines, key = { it.id }) { r -> Surface(RoundedCornerShape(22.dp), color = ACard) { Column(Modifier.padding(16.dp)) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(r.name, fontWeight = FontWeight.SemiBold); Text("${r.steps.sumOf { it.minutes }} min", color = ASage) }; r.steps.forEachIndexed { i, s -> Text("${i + 1}. ${s.name} · ${s.minutes}m", color = AMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp)) }; TextButton({ store.deleteRoutine(r.id) }) { Text("Delete") } } } }
    }
    if (showAdd) AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("Create routine") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Routine name") }) }, confirmButton = { TextButton({ if (name.isNotBlank()) { store.addRoutine(name, listOf(RoutineStep("Focus",25), RoutineStep("Break",5), RoutineStep("Focus",25))); name = ""; showAdd = false } }) { Text("Create") } }, dismissButton = { TextButton({ showAdd = false }) { Text("Cancel") } })
}

@Composable private fun JournalScreen(entries: List<JournalEntry>, store: AdvancedStore) {
    var editor by remember { mutableStateOf(false) }; var wins by remember { mutableStateOf("") }; var blockers by remember { mutableStateOf("") }; var gratitude by remember { mutableStateOf("") }
    val today = LocalDate.now().toString()
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Column { Text("Daily journal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Reflection becomes searchable history.", color = AMuted) }; IconButton({ editor = true }) { Icon(Icons.Default.EditNote, "Write") } } }
        items(entries, key = { it.date }) { e -> Surface(RoundedCornerShape(22.dp), color = ACard) { Column(Modifier.padding(16.dp)) { Text(e.date, fontWeight = FontWeight.Bold); Text("Mood ${e.mood}/5 · Energy ${e.energy}/5", color = ASage); if (e.wins.isNotBlank()) Text("Wins: ${e.wins}"); if (e.blockers.isNotBlank()) Text("Blockers: ${e.blockers}"); if (e.gratitude.isNotBlank()) Text("Grateful for: ${e.gratitude}") } } }
    }
    if (editor) AlertDialog(onDismissRequest = { editor = false }, title = { Text("Today's reflection") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(wins, { wins = it }, label = { Text("Wins") }); OutlinedTextField(blockers, { blockers = it }, label = { Text("Blockers") }); OutlinedTextField(gratitude, { gratitude = it }, label = { Text("Gratitude") }) } }, confirmButton = { TextButton({ store.saveJournal(JournalEntry(today,5,5,wins,blockers,gratitude)); editor = false }) { Text("Save") } }, dismissButton = { TextButton({ editor = false }) { Text("Cancel") } })
}

@Composable private fun TimeTrackingScreen(logs: List<TimeLog>, store: AdvancedStore) {
    var label by remember { mutableStateOf("") }; val running = logs.lastOrNull { it.end == 0L }; val today = logs.filter { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.start), java.time.ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Actual time", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Compare planned blocks with real elapsed work.", color = AMuted) }
        item { Surface(RoundedCornerShape(22.dp), color = ACard) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("What are you working on?") }); Button({ if (label.isNotBlank() && running == null) { store.startTimer(label); label = "" } else if (running != null) store.stopTimer(running.id) }, Modifier.fillMaxWidth()) { Icon(if (running == null) Icons.Default.PlayArrow else Icons.Default.Stop, null); Text(if (running == null) "Start timer" else "Stop · ${running.label}") } } } }
        items(today, key = { it.id }) { log -> Surface(RoundedCornerShape(18.dp), color = ACard) { Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.SpaceBetween) { Column { Text(log.label, fontWeight = FontWeight.SemiBold); Text(formatDuration(endOrNow(log)-log.start), color = AMuted) }; if (log.end == 0L) Text("LIVE", color = Color(0xFFB85F50), fontWeight = FontWeight.Bold) } } }
    }
}

@Composable private fun DeviceInsights(context: Context) {
    val allowed = remember { hasUsageAccess(context) }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Device focus", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Optional on-device screen-time intelligence.", color = AMuted) }
        if (!allowed) item { Surface(RoundedCornerShape(22.dp), color = ACard) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Usage access is off", fontWeight = FontWeight.SemiBold); Text("Android requires explicit user approval before an app can inspect other apps' usage statistics.", color = AMuted); Button({ context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Open usage access settings") } } } }
        else { val stats = remember { queryUsage(context) }; items(stats.take(10)) { UsageRow(it) } }
    }
}

@Composable private fun UsageRow(s: UsageStat) { Surface(RoundedCornerShape(16.dp), color = ACard) { Row(Modifier.fillMaxWidth().padding(13.dp), Arrangement.SpaceBetween) { Text(s.packageName, fontWeight = FontWeight.SemiBold); Text(formatDuration(s.millis), color = ASage) } } }
private fun hasUsageAccess(context: Context): Boolean { val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager; return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED }
private fun queryUsage(context: Context): List<UsageStat> = try { val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager; val end = System.currentTimeMillis(); val start = end - TimeUnit.DAYS.toMillis(1); manager.queryAndAggregateUsageStats(start, end).values.filter { it.totalTimeInForeground > 0 }.sortedByDescending { it.totalTimeInForeground }.map { UsageStat(it.packageName, it.totalTimeInForeground) } } catch (_: Exception) { emptyList() }
private fun endOrNow(l: TimeLog) = if (l.end == 0L) System.currentTimeMillis() else l.end
private fun formatDuration(ms: Long): String { val m = ms.coerceAtLeast(0) / 60000; return "${m / 60}h ${m % 60}m" }
