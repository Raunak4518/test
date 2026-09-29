@Composable
fun PremiumHome() {
    val app = remember { AppContainer(androidx.compose.ui.platform.LocalContext.current.applicationContext) }
    val vm: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory(app))
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val date by vm.currentDate.collectAsStateWithLifecycle()
    val pomo by vm.pomodoro.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var add by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TaskModel?>(null) }

    MaterialTheme(colorScheme = lightColorScheme(background = Cream, surface = Card, primary = Sage, onSurface = Ink)) {
        Scaffold(
            containerColor = Cream,
            bottomBar = {
                NavigationBar(containerColor = Card) {
                    listOf(Icons.Default.CalendarToday to "Today", Icons.Default.Timer to "Focus", Icons.Default.Insights to "Insights", Icons.Default.Tune to "Settings").forEachIndexed { i, p ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(p.first, null) }, label = { Text(p.second) })
                    }
                }
            },
            floatingActionButton = {
                if (tab == 0) FloatingActionButton(onClick = { add = true }, containerColor = Ink, contentColor = Color.White) { Icon(Icons.Default.Add, null) }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (tab) {
                    0 -> Timeline(tasks, date, vm) { selected = it }
                    1 -> Focus(pomo, tasks, vm)
                    2 -> Insights(tasks)
                    3 -> Settings(vm)
                }
            }
        }
    }
    if (add) AddSheet(vm) { add = false }
    selected?.let { TaskSheet(it, vm) { selected = null } }
}

@Composable
private fun Timeline(tasks: List<TaskModel>, date: LocalDate, vm: PlannerViewModel, onTask: (TaskModel) -> Unit) {
    val total = tasks.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    val done = tasks.filter { it.completed }.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    val pct = if (total == 0) 0 else done * 100 / total
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (date == LocalDate.now()) "Good day." else "Your plan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = Muted)
                }
                Box(Modifier.size(46.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) { Text("$pct%", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        item {
            Surface(RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.background(Brush.linearGradient(listOf(Ink, Color(0xFF315046)))).padding(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("TODAY", color = Color(0xFFB9CCC2), style = MaterialTheme.typography.labelMedium)
                        Text("${tasks.count { !it.completed }} things left", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("${total / 60}h ${total % 60}m planned · ${done / 60}h ${done % 60}m done", color = Color(0xFFC9D3D1))
                        LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Color(0xFFA8C7B7), trackColor = Color.White.copy(alpha = 0.12f))
                    }
                }
            }
        }
        item { DateStrip(date, vm) }
        item { Text("Timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (tasks.isEmpty()) {
            item {
                Surface(RoundedCornerShape(24.dp), color = Card) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Spa, null, tint = Sage, modifier = Modifier.size(42.dp))
                        Text("A quiet day", fontWeight = FontWeight.SemiBold)
                        Text("Add something when you are ready.", color = Muted)
                    }
                }
            }
        } else {
            items(tasks.sortedBy { it.startMinute }, key = { it.id }) { task ->
                TaskCard(task, onClick = { onTask(task) }, onComplete = { vm.toggleComplete(task, !task.completed) })
            }
        }
        item { Spacer(Modifier.height(70.dp)) }
    }
}

@Composable
private fun DateStrip(date: LocalDate, vm: PlannerViewModel) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (-3..3).forEach { offset ->
            val day = date.plusDays(offset.toLong())
            val selected = offset == 0
            Surface(RoundedCornerShape(18.dp), color = if (selected) Ink else Card, onClick = { if (offset < 0) vm.onPrevDay(); if (offset > 0) vm.onNextDay() }) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.format(DateTimeFormatter.ofPattern("EEE")), color = if (selected) Color.White else Muted)
                    Text(day.dayOfMonth.toString(), color = if (selected) Color.White else Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TaskCard(t: TaskModel, onClick: () -> Unit, onComplete: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = Card, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(64.dp).clip(CircleShape).background(Color(t.colorHex)))
            Column(Modifier.weight(1f).padding(start = 13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${tm(t.startMinute)} — ${tm(t.endMinute)} · ${t.endMinute - t.startMinute} min", color = Muted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (t.pomodoroEnabled) Chip("Pomodoro", Icons.Default.Timer)
                    if (t.recurrenceType != "NONE") Chip("Repeats", Icons.Default.Repeat)
                    if (t.notes.isNotBlank()) Chip("Notes", Icons.Default.Notes)
                }
            }
            Checkbox(checked = t.completed, onCheckedChange = { onComplete() })
        }
    }
}

@Composable private fun Chip(s:String,icon:androidx.compose.ui.graphics.vector.ImageVector){Surface(RoundedCornerShape(50),color=Color(0xFFEAF0EC)){Row(Modifier.padding(horizontal=7.dp,vertical=4.dp),Alignment.CenterVertically){Icon(icon,null,tint=Sage,Modifier.size(13.dp));Spacer(Modifier.width(4.dp));Text(s,color=Sage,style=MaterialTheme.typography.labelSmall)}}}

@Composable private fun Focus(p:com.raunak.daytimeline.data.PomodoroStateEntity,tasks:List<TaskModel>,vm:PlannerViewModel){Column(Modifier.fillMaxSize().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("FOCUS",color=Sage,fontWeight=FontWeight.Bold);Spacer(Modifier.height(22.dp));Surface(CircleShape,color=Ink,modifier=Modifier.size(245.dp)){Box(Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("%02d:%02d".format(p.remainingSeconds/60,p.remainingSeconds%60),color=Color.White,style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.Light);Text(p.phase,color=Color(0xFFB8C8C0))}}};Spacer(Modifier.height(22.dp));Text("One thing at a time.",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text("Focus without leaving the timeline.",color=Muted);Spacer(Modifier.height(20.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button({if(p.running)vm.pausePomodoro()else vm.resumePomodoro()}){Icon(if(p.running)Icons.Default.Pause else Icons.Default.PlayArrow,null);Spacer(Modifier.width(6.dp));Text(if(p.running)"Pause" else "Resume")};OutlinedButton(vm::resetPomodoro){Text("Reset")}};Spacer(Modifier.height(15.dp));tasks.take(4).forEach{t->ListItem({Text(t.title)},{Text("${tm(t.startMinute)} · ${t.endMinute-t.startMinute} min",color=Muted)}, {Icon(Icons.Default.RadioButtonUnchecked,null)},{IconButton({vm.startPomodoro(t.id)}){Icon(Icons.Default.PlayArrow,null)}})}}

@Composable
private fun Insights(tasks: List<TaskModel>) {
    val total = tasks.size
    val done = tasks.count { it.completed }
    val focus = tasks.count { it.pomodoroEnabled }
    val planned = tasks.sumOf { it.endMinute - it.startMinute }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Insights", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Your day, without noise.", color = Muted)
        }
        item { Stat("Completion", "$done / $total", if (total == 0) 0f else done.toFloat() / total, Icons.Default.CheckCircle) }
        item { Stat("Focus blocks", "$focus", if (total == 0) 0f else focus.toFloat() / total, Icons.Default.Timer) }
        item { Stat("Planned time", "${planned / 60}h ${planned % 60}m", 1f, Icons.Default.Schedule) }
        item { Text("Categories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(tasks.groupBy { it.category.ifBlank { "Other" } }.toList()) { entry ->
            val category = entry.first
            val list = entry.second
            ListItem(
                headlineContent = { Text(category) },
                supportingContent = { Text("${list.size} blocks · ${list.sumOf { it.endMinute - it.startMinute }} min", color = Muted) },
                leadingContent = { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(list.first().colorHex))) }
            )
        }
    }
}

@Composable
private fun Stat(t: String, v: String, p: Float, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(RoundedCornerShape(24.dp), color = Card) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t, color = Muted)
                Icon(icon, null, tint = Sage)
            }
            Text(v, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { p.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Sage, trackColor = Color(0xFFE7E5DF))
        }
    }
}

@Composable
private fun Settings(vm: PlannerViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Quiet controls for your day.", color = Muted) }
        item { Toggle("Auto-scroll to now", "Open today at the current time", settings.autoScrollNow) { vm.updateSettings { copy(autoScrollNow = it) } } }
        item { Toggle("Show completed", "Keep finished blocks visible", settings.showCompleted) { vm.updateSettings { copy(showCompleted = it) } } }
        item { Setting("Day starts", tm(settings.dayStartMinute), Icons.Default.WbSunny) }
        item { Setting("Day ends", tm(settings.dayEndMinute), Icons.Default.NightsStay) }
        item { Setting("Storage", "On-device · offline", Icons.Default.Lock) }
        item {
            Surface(RoundedCornerShape(22.dp), color = Color(0xFFE9F0EA)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiOff, null, tint = Sage)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Private by default", fontWeight = FontWeight.SemiBold)
                        Text("No server is required for your core day.", color = Color(0xFF5C6B63))
                    }
                }
            }
        }
    }
}

@Composable
private fun Toggle(t: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(RoundedCornerShape(22.dp), color = Card) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t, fontWeight = FontWeight.SemiBold)
                Text(sub, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun Setting(t: String, v: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(RoundedCornerShape(22.dp), color = Card) {
        ListItem(headlineContent = { Text(t) }, supportingContent = { Text(v, color = Muted) }, leadingContent = { Icon(icon, null, tint = Sage) }, trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(vm: PlannerViewModel, close: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var startText by remember { mutableStateOf("09:00") }
    var endText by remember { mutableStateOf("10:00") }
    var notes by remember { mutableStateOf("") }
    var pomo by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("New block", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(title, { title = it }, label = { Text("What are you doing?") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(startText, { startText = it }, label = { Text("Start") }, modifier = Modifier.weight(1f))
                OutlinedTextField(endText, { endText = it }, label = { Text("End") }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 45, 60, 90, 120).forEach { minutes ->
                    AssistChip(onClick = { endText = tm(pm(startText) + minutes) }, label = { Text(if (minutes < 60) "${minutes}m" else "${minutes / 60}h") })
                }
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Pomodoro")
                Switch(checked = pomo, onCheckedChange = { pomo = it })
            }
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val start = pm(startText)
                        val end = pm(endText).coerceAtLeast(start + 5)
                        vm.addOrUpdateTask(null, title.trim(), start, end, pomo, notes, 1, "NONE", "NONE", 10)
                        close()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(7.dp))
                Text("Add to my day")
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskSheet(t: TaskModel, vm: PlannerViewModel, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${tm(t.startMinute)} — ${tm(t.endMinute)}", color = Muted)
            if (t.notes.isNotBlank()) Text(t.notes)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.toggleComplete(t, !t.completed); close() }) { Text(if (t.completed) "Reopen" else "Complete") }
                OutlinedButton(onClick = { vm.duplicateTask(t); close() }) { Text("Duplicate") }
                OutlinedButton(onClick = { vm.deleteTask(t); close() }) { Text("Delete") }
            }
            if (t.pomodoroEnabled) {
                Button(onClick = { vm.startPomodoro(t.id); close() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Timer, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Start focus")
                }
            }
        }
    }
}
private fun tm(minutes: Int): String {
    val hour = (minutes / 60).coerceIn(0, 23)
    val minute = (minutes % 60).coerceIn(0, 59)
    return "%02d:%02d".format(hour, minute)
}

private fun pm(value: String): Int {
    val parts = value.trim().split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return (hour * 60 + minute).coerceIn(0, 1439)
}
