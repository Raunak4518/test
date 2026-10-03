package com.raunak.daytimeline

import com.raunak.daytimeline.ui.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.features.OfflineProductivityStore
import com.raunak.daytimeline.features.streak
import com.raunak.daytimeline.productivity.LocalProductivityAnalytics
import com.raunak.daytimeline.productivity.SmartPlanningEngine
import com.raunak.daytimeline.productivity.ChronoraPowerCenter
import java.time.LocalDate
import java.time.LocalDateTime

private val HomeInk: Color @Composable get() = Chronora.colors.hero
private val HomeBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val HomeSage: Color @Composable get() = MaterialTheme.colorScheme.primary
private val HomeMuted: Color @Composable get() = Chronora.muted

private data class NavTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val accent: Color)
private val Tabs = listOf(
    NavTab("Today", Icons.Default.ViewTimeline, Palette.indigo), NavTab("Plan", Icons.Default.CalendarViewDay, Palette.violet),
    NavTab("Focus", Icons.Default.Timer, Palette.coral), NavTab("Campus", Icons.Default.School, Palette.teal), NavTab("You", Icons.Default.Person, Palette.amber)
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PowerHome(onOpenAlarms: () -> Unit = {}, onOpenCommandCenter: () -> Unit = {}, openQuickAdd: Boolean = false, onQuickAddHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val app = remember(context) { AppContainer(context.applicationContext) }
    val vm: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory(app))
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val allTasks by vm.allTasks.collectAsStateWithLifecycle()
    val date by vm.currentDate.collectAsStateWithLifecycle()
    val pomo by vm.pomodoro.collectAsStateWithLifecycle()
    val productivity = remember(context) { OfflineProductivityStore(context.applicationContext) }
    val habits by productivity.habits.collectAsStateWithLifecycle()
    val goals by productivity.goals.collectAsStateWithLifecycle()
    val routines by productivity.routines.collectAsStateWithLifecycle()
    val entries by productivity.timeEntries.collectAsStateWithLifecycle()
    val journal by productivity.journal.collectAsStateWithLifecycle()
    val notes by productivity.notes.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addAt by remember { mutableStateOf<Pair<LocalDate, Int?>?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<String?>(null) }
    var editTask by remember { mutableStateOf<TaskModel?>(null) }
    val updateRequested by com.raunak.daytimeline.update.UpdateNav.requested.collectAsStateWithLifecycle()
    val trackerOpen by com.raunak.daytimeline.trackers.TrackerNav.open.collectAsStateWithLifecycle()
    LaunchedEffect(trackerOpen) { if (trackerOpen) { page = "trackers"; com.raunak.daytimeline.trackers.TrackerNav.open.value = false } }
    LaunchedEffect(openQuickAdd) { if (openQuickAdd) { page = "tool:QUICK_ADD"; onQuickAddHandled() } }
    val open: (String) -> Unit = { route ->
        when (route) {
            "alarms" -> onOpenAlarms()
            "command" -> onOpenCommandCenter()
            "update" -> com.raunak.daytimeline.update.UpdateNav.requested.value = true
            "settings" -> dialog = "tools"
            else -> page = route
        }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        Feedback.messages.collect { m ->
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val r = snackbar.showSnackbar(m.text, actionLabel = if (m.undo != null) "Undo" else null, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) m.undo?.invoke()
            }
        }
    }

    SectionTheme(Tabs[tab].accent) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = RoundedCornerShape(16.dp)) } },
        topBar = {
            ChronoraTopBar(if (tab == 0) "Today" else Tabs[tab].label, null) {
                IconButton(onClick = { page = "tool:SEARCH" }) { Icon(Icons.Default.Search, "Search") }
                IconButton(onClick = onOpenAlarms) { Icon(Icons.Default.Alarm, "Alarms") }
                if (tab == 4) IconButton(onClick = { page = "settingsPage" }) { Icon(Icons.Default.Settings, "Settings") }
            }
        },
        bottomBar = {
            NavigationBar(Modifier.testTag("nav"), containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Tabs.forEachIndexed { i, t ->
                    NavigationBarItem(tab == i, { tab = i }, icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = t.accent, selectedTextColor = t.accent, indicatorColor = t.accent.copy(alpha = .15f)))
                }
            }
        },
        floatingActionButton = {
            if (tab <= 1) FloatingActionButton(onClick = { addAt = date to null }, shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Icon(Icons.Default.Add, "Add task") }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.animation.Crossfade(tab, label = "tab") { t ->
                when (t) {
                    0 -> com.raunak.daytimeline.home.HomeScreen(vm, { editTask = it }, { d, m -> addAt = d to m }, onOpenFocusMode = { tab = 2 }, onOpenHabits = { page = "prod:0" }, onOpenTrackers = { page = "trackers" }) { tab = 2 }
                    1 -> com.raunak.daytimeline.home.PlanScreen(vm, productivity, { editTask = it }, { d, m -> addAt = d to m }) { page = "calendar" }
                    2 -> com.raunak.daytimeline.productivity.FocusTab(pomo, tasks, allTasks, vm)
                    3 -> com.raunak.daytimeline.campus.CampusScreen(Modifier)
                    else -> com.raunak.daytimeline.home.MoreScreen(open)
                }
            }
        }
    }
    }

    addAt?.let { (d, m) -> com.raunak.daytimeline.productivity.TaskEditor(vm, null, d, m) { addAt = null } }
    editTask?.let { t -> com.raunak.daytimeline.productivity.TaskEditor(vm, t, t.date) { editTask = null } }
    when (val p = page) {
        null -> Unit
        "calendar" -> com.raunak.daytimeline.productivity.CalendarPage(vm, date, { editTask = it }) { page = null }
        "trackers" -> FullScreenPage("Trackers", { page = null }, accent = Palette.teal) { com.raunak.daytimeline.trackers.TrackersScreen() }
        "focusmode" -> FullScreenPage("Focus mode", { page = null }, accent = Palette.coral) { com.raunak.daytimeline.pro.FocusModeScreen() }
        "settingsPage" -> FullScreenPage("Settings", { page = null }, accent = Palette.amber) { com.raunak.daytimeline.home.SettingsList { r -> page = null; open(r) } }
        "power" -> ChronoraPowerCenter(allTasks, productivity, { page = null }) { vm.selectDate(it); page = null }
        else -> when {
            p.startsWith("wellbeing:") -> SectionTheme(Palette.sky) { com.raunak.daytimeline.wellbeing.WellbeingHub(p.substringAfter(":").toIntOrNull() ?: 0) { page = null } }
            p.startsWith("prod:") -> FullScreenPage(ProductivityLabels[p.substringAfter(":").toIntOrNull() ?: 0], { page = null }, accent = Palette.green) {
                ProductivityScreen(habits, goals, routines, entries, journal, notes, productivity, p.substringAfter(":").toIntOrNull() ?: 0) { dialog = it }
            }
            p.startsWith("tool:") -> runCatching { com.raunak.daytimeline.pro.ProTool.valueOf(p.substringAfter(":")) }.getOrNull()
                ?.let { com.raunak.daytimeline.pro.ProToolPage(vm, it) { page = null } }
        }
    }
    if (updateRequested) com.raunak.daytimeline.update.UpdateDialog { com.raunak.daytimeline.update.UpdateNav.requested.value = false }
    if (dialog?.startsWith("editHabit:") == true) {
        val id = dialog!!.substringAfter(":").toLongOrNull()
        val habit = habits.firstOrNull { it.id == id }
        if (habit != null) com.raunak.daytimeline.productivity.HabitEditorDialog(habit, { productivity.saveHabit(it); dialog = null }) { dialog = null }
    }
    when (dialog) {
        "habit" -> com.raunak.daytimeline.productivity.HabitEditorDialog(null, { productivity.saveHabit(it); dialog = null }) { dialog = null }
        "tools" -> OfflinePowerTools(productivity) { dialog = null }
    }
}

private val ProductivityLabels = listOf("Habits", "Goals", "Routines", "Notes", "Journal", "Time log")

@Composable
private fun ProductivityScreen(habits: List<com.raunak.daytimeline.features.OfflineHabit>, goals: List<com.raunak.daytimeline.features.OfflineGoal>, routines: List<com.raunak.daytimeline.features.OfflineRoutine>, entries: List<com.raunak.daytimeline.features.OfflineTimeEntry>, journal: List<com.raunak.daytimeline.features.OfflineJournalEntry>, notes: List<com.raunak.daytimeline.features.OfflineNote>, store: OfflineProductivityStore, initialSection: Int = 0, openDialog: (String) -> Unit) {
    val today = LocalDate.now()
    val prefs by store.settings.collectAsStateWithLifecycle()
    val projects by store.projects.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableIntStateOf(initialSection) }
    val labels = ProductivityLabels
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = section, edgePadding = 12.dp, containerColor = Color.Transparent) {
            labels.forEachIndexed { i, l -> Tab(section == i, { section = i }, text = { Text(l) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (section) {
                0 -> {
                    item { com.raunak.daytimeline.productivity.HabitTodayHero(habits, today) }
                    item { SectionHeader("Habits") { TextButton(onClick = { openDialog("habit") }) { Text("Add") } } }
                    item { com.raunak.daytimeline.productivity.HabitList(habits, store, { openDialog("editHabit:${it.id}") }, today) }
                }
                1 -> item { com.raunak.daytimeline.productivity.GoalsSection(goals, store) }
                2 -> item { com.raunak.daytimeline.productivity.RoutinesSection(routines, store) }
                3 -> item { com.raunak.daytimeline.productivity.NotesSection(notes, store) }
                4 -> item { com.raunak.daytimeline.productivity.JournalSection(journal, prefs, store) }
                else -> item { com.raunak.daytimeline.productivity.TimeSection(entries, projects, store) }
            }
        }
    }
}


private fun clock(minutes: Int) = "%02d:%02d".format((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))
private fun parseClock(value: String): Int { val p = value.trim().split(":"); return ((p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)).coerceIn(0, 1439) }
