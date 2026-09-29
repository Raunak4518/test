package com.raunak.daytimeline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.features.*
import java.time.LocalDate

/** Kept for older call sites; the full editor lives in [com.raunak.daytimeline.productivity.TaskEditor]. */
@Composable
fun TaskEditorDialog(vm: PlannerViewModel, initial: TaskModel?, close: () -> Unit) =
    com.raunak.daytimeline.productivity.TaskEditor(vm, initial, initial?.date ?: vm.currentDate.value, close)

@Composable
fun CalendarDialog(date: LocalDate, vm: PlannerViewModel, close: () -> Unit) {
    var month by remember { mutableStateOf(date.withDayOfMonth(1)) }
    val offset=month.dayOfWeek.value-1
    val days=month.lengthOfMonth()
    AlertDialog(onDismissRequest=close,title={Text("Calendar")},text={
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){IconButton(onClick={month=month.minusMonths(1)}){Icon(Icons.Default.ChevronLeft,null)};Text(month.month.toString()+" "+month.year);IconButton(onClick={month=month.plusMonths(1)}){Icon(Icons.Default.ChevronRight,null)}}
            Row(Modifier.fillMaxWidth()){listOf("M","T","W","T","F","S","S").forEach{Text(it,Modifier.weight(1f))}}
            var day=1
            repeat(6){row->Row(Modifier.fillMaxWidth()){repeat(7){col->val i=row*7+col;if(i<offset||day>days)Spacer(Modifier.weight(1f).height(42.dp))else{val d=LocalDate.of(month.year,month.month,day++);OutlinedButton(onClick={vm.selectDate(d);close()},modifier=Modifier.weight(1f).padding(1.dp),contentPadding=PaddingValues(0.dp)){Text(d.dayOfMonth.toString())}}}}}
            OutlinedButton(onClick={ { vm.onToday(); close() } },modifier=Modifier.fillMaxWidth()){Text("Today")}
        }
    },confirmButton={TextButton(onClick=close){Text("Close")}})
}

@Composable
fun TaskSearchDialog(tasks: List<TaskModel>, vm: PlannerViewModel, close: () -> Unit) {
    var query by remember{mutableStateOf("")};var includeDone by remember{mutableStateOf(true)};var priority by remember{mutableIntStateOf(0)};var editing by remember{mutableStateOf<TaskModel?>(null)}
    val filtered=tasks.filter{(query.isBlank()||it.title.contains(query,true)||it.notes.contains(query,true)||it.tags.contains(query,true))&&(includeDone||!it.completed)&&(priority==0||it.priority==priority)}
    AlertDialog(onDismissRequest=close,title={Text("Search tasks")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        OutlinedTextField(query,{query=it},label={Text("Title, notes or tags")},modifier=Modifier.fillMaxWidth(),leadingIcon={Icon(Icons.Default.Search,null)})
        Row{FilterChip(includeDone,{includeDone=!includeDone},label={Text("Completed")});(1..3).forEach{p->FilterChip(priority==p,{priority=if(priority==p)0 else p},label={Text("P"+p)},modifier=Modifier.padding(start=4.dp))}}
        filtered.take(30).forEach{t->ListItem(headlineContent={Text(t.title)},supportingContent={Text(t.date.toString()+" · "+clock(t.startMinute))},trailingContent={IconButton(onClick={editing=t}){Icon(Icons.Default.Edit,null)}})}
        if(filtered.isEmpty())Text("No matches")
    }},confirmButton={TextButton(onClick=close){Text("Close")}})
    editing?.let{TaskEditorDialog(vm,it){editing=null}}
}

@Composable
fun NotesManagerDialog(notes: List<OfflineNote>, store: OfflineProductivityStore, close: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<OfflineNote?>(null) }
    var creating by remember { mutableStateOf(false) }
    val list = notes.filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) || it.tags.any { tag -> tag.contains(query, true) } }
    AlertDialog(onDismissRequest = close, title = { Text("Notes") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
            list.take(20).forEach { note ->
                ListItem(headlineContent = { Text(note.title) }, supportingContent = { Text(note.body.take(70)) }, trailingContent = { Row {
                    IconButton(onClick = { editing = note }) { Icon(Icons.Default.Edit, null) }
                    IconButton(onClick = { store.deleteNote(note.id) }) { Icon(Icons.Default.Delete, null) }
                } })
            }
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("New note") }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("Close") } })
    if (creating || editing != null) NoteEditDialog(editing, store) { creating = false; editing = null }
}

@Composable
private fun NoteEditDialog(note: OfflineNote?, store: OfflineProductivityStore, close: () -> Unit) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var body by remember { mutableStateOf(note?.body ?: "") }
    var tags by remember { mutableStateOf(note?.tags?.joinToString(", ") ?: "") }
    AlertDialog(onDismissRequest = close, title = { Text(if (note == null) "New note" else "Edit note") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") })
            OutlinedTextField(body, { body = it }, label = { Text("Body") }, minLines = 5)
            OutlinedTextField(tags, { tags = it }, label = { Text("Tags") })
        }
    }, confirmButton = { Button(onClick = {
        val ts = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (note == null) store.addNote(title, body, ts) else store.updateNote(note.id, title, body, ts)
        close()
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
fun GoalManagerDialog(goals: List<OfflineGoal>, store: OfflineProductivityStore, close: () -> Unit) {
    var editing by remember { mutableStateOf<OfflineGoal?>(null) }
    var creating by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = close, title = { Text("Goals & milestones") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            goals.forEach { goal ->
                Card { Column(Modifier.padding(10.dp)) {
                    Text(goal.title)
                    Text(goal.progress.toString() + "/" + goal.target + (goal.deadline?.let { " · due " + it } ?: ""))
                    LinearProgressIndicator(progress = { goal.progress.toFloat() / goal.target })
                    goal.milestones.forEachIndexed { index, milestone -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• " + milestone, Modifier.weight(1f))
                        IconButton(onClick = { store.removeGoalMilestone(goal.id, index) }) { Icon(Icons.Default.Close, null) }
                    } }
                    Row {
                        TextButton(onClick = { store.setGoalProgress(goal.id, goal.progress + 1) }) { Text("+1") }
                        TextButton(onClick = { editing = goal }) { Text("Edit") }
                        TextButton(onClick = { store.deleteGoal(goal.id) }) { Text("Delete") }
                    }
                } }
            }
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("New goal") }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("Close") } })
    if (creating || editing != null) GoalEditDialog(editing, store) { creating = false; editing = null }
}

@Composable
private fun GoalEditDialog(goal: OfflineGoal?, store: OfflineProductivityStore, close: () -> Unit) {
    var title by remember { mutableStateOf(goal?.title ?: "") }
    var target by remember { mutableStateOf((goal?.target ?: 100).toString()) }
    var deadline by remember { mutableStateOf(goal?.deadline ?: "") }
    var milestones by remember { mutableStateOf(goal?.milestones?.joinToString("\n") ?: "") }
    AlertDialog(onDismissRequest = close, title = { Text(if (goal == null) "New goal" else "Edit goal") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Goal") })
            OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Target") })
            OutlinedTextField(deadline, { deadline = it }, label = { Text("Deadline YYYY-MM-DD") })
            OutlinedTextField(milestones, { milestones = it }, label = { Text("Milestones, one per line") }, minLines = 3)
        }
    }, confirmButton = { Button(onClick = {
        val d = runCatching { LocalDate.parse(deadline) }.getOrNull()
        val ms = milestones.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (goal == null) store.addGoal(title, target.toIntOrNull() ?: 1, d) else store.updateGoal(goal.id, title, target.toIntOrNull() ?: goal.target, d, ms)
        close()
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
fun JournalHistoryDialog(journal: List<OfflineJournalEntry>, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("Journal history") }, text = {
        LazyColumn(modifier = Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(journal.sortedByDescending { it.date }.take(90)) { entry ->
                Card { Column(Modifier.padding(10.dp)) {
                    Text(entry.date)
                    Text("Mood " + entry.mood + "/5 · Energy " + entry.energy + "/5")
                    if (entry.wins.isNotBlank()) Text("Wins: " + entry.wins)
                    if (entry.blockers.isNotBlank()) Text("Blockers: " + entry.blockers)
                    if (entry.gratitude.isNotBlank()) Text("Gratitude: " + entry.gratitude)
                    if (entry.note.isNotBlank()) Text(entry.note)
                } }
            }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("Close") } })
}

private fun clock(m:Int)="%02d:%02d".format((m/60).coerceIn(0,23),(m%60).coerceIn(0,59))
private fun parseClock(v:String):Int{val p=v.trim().split(":");return((p.getOrNull(0)?.toIntOrNull()?:0)*60+(p.getOrNull(1)?.toIntOrNull()?:0)).coerceIn(0,1439)}
