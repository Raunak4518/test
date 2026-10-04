package com.raunak.daytimeline.money

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.ui.*
import com.raunak.daytimeline.wellbeing.ClockRow
import com.raunak.daytimeline.wellbeing.Expandable
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private fun nowMinute() = LocalTime.now().let { it.hour * 60 + it.minute }

/** Spent today and safe-to-spend, for the Today tab. Tap opens Money. */
@Composable
fun MoneyStrip(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    val d by MoneyStore.get(context).data.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val cur = d.settings.currency
    val safe = kotlin.math.floor(MoneyEngine.safeToSpendToday(d, today))
    val spent = MoneyEngine.spentOn(d, today)
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onOpen).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Default.AccountBalanceWallet, Color(0xFF22A06B), 40.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Safe to spend today", style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
            Text(MoneyEngine.format(safe, cur), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (safe <= 0) Chronora.colors.bad else MaterialTheme.colorScheme.onSurface)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Spent today", style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
            Text(MoneyEngine.format(spent, cur), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Money: overview, activity, plans and friends. */
@Composable
fun MoneyScreen() {
    val context = LocalContext.current
    val store = remember { MoneyStore.get(context) }
    val d by store.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf<Txn?>(null) }
    var split by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val remind = store.processRecurring(LocalDate.now())
        if (remind.isNotEmpty()) Feedback.show("${remind.size} payment${if (remind.size > 1) "s" else ""} due · see Plan")
        MoneyReminders.scheduleAll(context)
    }
    fun newTxn() = Txn(0, 0.0, date = LocalDate.now().toString(), minute = nowMinute(), walletId = d.wallets.first().id, categoryId = d.categories.first { !it.income }.id)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PillTabs(listOf("Overview", "Activity", "Plan", "Friends"), tab, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { tab = it }
            when (tab) {
                0 -> Overview(d, store, onAdd = { adding = it ?: newTxn() }, onTab = { tab = it })
                1 -> Activity(d, store) { adding = it }
                2 -> PlanTab(d, store)
                else -> FriendsTab(d, store) { split = true }
            }
        }
        ExtendedFloatingActionButton(onClick = { if (tab == 3) split = true else adding = newTxn() }, icon = { Icon(if (tab == 3) Icons.Default.CallSplit else Icons.Default.Add, null) },
            text = { Text(if (tab == 3) "Split a bill" else "Add") }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    }
    adding?.let { t -> AddSheet(t, d, store) { adding = null } }
    if (split) AddSheet(newTxn(), d, store, startSplit = true) { split = false }
}

// ------------------------------------------------------------------ overview

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(d: MoneyData, store: MoneyStore, onAdd: (Txn?) -> Unit, onTab: (Int) -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val today = LocalDate.now()
    val cur = d.settings.currency
    val month = MoneyEngine.monthRange(today, d.settings.monthStartDay)
    val spentMonth = MoneyEngine.spent(d, month)
    val safe = kotlin.math.floor(MoneyEngine.safeToSpendToday(d, today))
    val left = d.settings.monthlyBudget - spentMonth
    val projected = MoneyEngine.projected(d, today)
    fun f(v: Double) = MoneyEngine.format(v, cur)
    var setBalance by remember { mutableStateOf<Wallet?>(null) }
    setBalance?.let { w ->
        var value by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { setBalance = null }, title = { Text("${w.emoji} ${w.name}") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How much is in it right now?", color = Chronora.muted)
                AmountField(value, { value = it }, "Current balance")
            }
        }, confirmButton = {
            TextButton(enabled = value.toDoubleOrNull() != null, onClick = {
                // Shift the starting balance so today's balance matches what's really there.
                val diff = value.toDouble() - MoneyEngine.balance(d, w)
                store.update { x -> x.copy(wallets = x.wallets.map { if (it.id == w.id) it.copy(opening = it.opening + diff) else it }) }
                setBalance = null; Feedback.show("Balance updated")
            }) { Text("Save") }
        }, dismissButton = { TextButton(onClick = { setBalance = null }) { Text("Cancel") } })
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            HeroCard {
                Text("Safe to spend today", style = MaterialTheme.typography.labelLarge, color = Chronora.colors.heroMuted)
                Text(f(safe), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                val p by animateFloatAsState((spentMonth / d.settings.monthlyBudget.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f), tween(800), label = "month")
                LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = if (left < 0) Color(0xFFFF8A80) else Color.White, trackColor = Color.White.copy(alpha = .22f), drawStopIndicator = {})
                Text(if (left >= 0) "${f(left)} left this month · ${MoneyEngine.daysLeft(d, today)} days to go" else "${f(-left)} over budget this month", color = Chronora.colors.heroMuted)
                if (spentMonth > 0 && projected > d.settings.monthlyBudget * 1.02) Text("⚠️ At this pace: ${f(projected)} by month end", style = MaterialTheme.typography.labelLarge)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                d.settings.quick.forEach { q ->
                    val cat = d.categories.firstOrNull { it.id == q.categoryId }
                    AssistChip(onClick = {
                        val t = Txn(store.nextId(), q.amount, TxnType.EXPENSE, q.categoryId, d.wallets.first().id, date = today.toString(), minute = nowMinute(), note = q.label)
                        store.add(t); haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        Feedback.show("${q.label} ${f(q.amount)} · ${f(MoneyEngine.safeToSpendToday(store.data.value, today))} still safe today") { store.remove(t.id) }
                        MoneyReminders.checkAlerts(context)
                    }, label = { Text("${cat?.emoji ?: ""} ${q.label} ${f(q.amount)}") }, shape = RoundedCornerShape(50))
                }
                AssistChip(onClick = { onAdd(null) }, label = { Text("Other") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }, shape = RoundedCornerShape(50))
            }
        }
        if (d.detected.isNotEmpty()) item {
            SectionCard("Spotted payments", icon = Icons.Default.NotificationsActive) {
                d.detected.sortedByDescending { it.time }.forEach { det ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable {
                            store.update { x -> x.copy(detected = x.detected.filterNot { it.id == det.id }) }
                            onAdd(Txn(0, det.amount, TxnType.EXPENSE, MoneyReminders.guessCategory(d, det.payee), d.wallets.first().id, date = today.toString(), minute = nowMinute(), note = det.payee))
                        }) {
                            Text(f(det.amount) + if (det.payee.isNotBlank()) " · ${det.payee}" else "", style = MaterialTheme.typography.bodyLarge)
                            Text(det.app + " · tap to edit", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                        }
                        FilledTonalButton(onClick = { MoneyReminders.addDetected(context, det.id); Feedback.show("Added") }) { Text("Add") }
                        IconButton(onClick = { store.update { x -> x.copy(detected = x.detected.filterNot { it.id == det.id }) } }) { Icon(Icons.Default.Close, "Ignore") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val streak = MoneyEngine.noSpendStreak(d, today, includeToday = false)
                StatTile("Spent today", f(MoneyEngine.spentOn(d, today)), Modifier.weight(1f))
                StatTile("No-spend streak", if (streak > 0) "🔥 $streak" else "—", Modifier.weight(1f), detail = if (MoneyEngine.spentOn(d, today) == 0.0) "Today still clean" else null)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                d.wallets.forEach { w ->
                    Column(Modifier.width(150.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).clickable { setBalance = w }.padding(14.dp)) {
                        Text("${w.emoji} ${w.name}", style = MaterialTheme.typography.labelLarge, color = Chronora.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val bal = MoneyEngine.balance(d, w)
                        Text(f(bal), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (bal < 0) Chronora.colors.bad else MaterialTheme.colorScheme.onSurface)
                        if (bal < 0 && w.opening == 0.0) Text("Tap to set balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { CategoryCard(d, month) }
        item { DailyChart(d, today) }
        item {
            val last = MoneyEngine.monthRange(month.first.minusDays(1), d.settings.monthStartDay)
            val dayIndex = java.time.temporal.ChronoUnit.DAYS.between(month.first, today)
            val lastSoFar = MoneyEngine.spent(d, last.first to minOf(last.first.plusDays(dayIndex), last.second))
            if (lastSoFar > 0) {
                val diff = spentMonth - lastSoFar
                SectionCard("Compared with last month", icon = Icons.Default.CompareArrows) {
                    StatusText(if (diff <= 0) "${f(-diff)} less than at this point last month" else "${f(diff)} more than at this point last month", ok = diff <= 0)
                }
            }
        }
        val dueSoon = d.recurring.filter { it.enabled && LocalDate.parse(it.nextDate) <= today.plusDays(7) }.sortedBy { it.nextDate }
        if (dueSoon.isNotEmpty()) item {
            SectionCard("Coming up", icon = Icons.Default.EventRepeat, action = { TextButton(onClick = { onTab(2) }) { Text("All") } }) {
                dueSoon.forEach { r -> RecurringRow(r, d, store) }
            }
        }
        val ledger = MoneyEngine.ledger(d)
        if (ledger.isNotEmpty()) item {
            val owed = ledger.values.filter { it > 0 }.sum(); val owe = -ledger.values.filter { it < 0 }.sum()
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).clickable { onTab(3) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("👥", fontSize = 26.sp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    if (owed > 0) Text("Friends owe you ${f(owed)}", color = Chronora.colors.good, fontWeight = FontWeight.SemiBold)
                    if (owe > 0) Text("You owe ${f(owe)}", color = Chronora.colors.bad, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    }
}

/** Donut by category plus each category's spend against its budget. */
@Composable
private fun CategoryCard(d: MoneyData, month: Pair<LocalDate, LocalDate>) {
    val cur = d.settings.currency
    val rows = d.categories.filter { !it.income }.map { it to MoneyEngine.spent(d, month, it.id) }.filter { it.second > 0 || it.first.budget > 0 }.sortedByDescending { it.second }
    val total = rows.sumOf { it.second }
    SectionCard("Where it went", icon = Icons.Default.PieChart) {
        if (total <= 0) { Text("Nothing spent yet this month", color = Chronora.muted); return@SectionCard }
        val sweep by animateFloatAsState(1f, tween(900), label = "donut")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    var start = -90f
                    val w = 22.dp.toPx(); val sz = Size(size.width - w, size.height - w)
                    rows.filter { it.second > 0 }.forEach { (c, v) ->
                        val a = (v / total * 360f).toFloat() * sweep
                        drawArc(Color(c.color), start, (a - 2f).coerceAtLeast(.5f), false, Offset(w / 2, w / 2), sz, style = Stroke(w))
                        start += a
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(MoneyEngine.format(total, cur), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("this month", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.filter { it.second > 0 }.take(5).forEach { (c, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c.color)))
                        Text("  ${c.emoji} ${c.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(v / total * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
                    }
                }
            }
        }
        rows.filter { it.first.budget > 0 }.forEach { (c, v) ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row {
                    Text("${c.emoji} ${c.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${MoneyEngine.format(v, cur)} / ${MoneyEngine.format(c.budget, cur)}", style = MaterialTheme.typography.labelMedium, color = if (v > c.budget) Chronora.colors.bad else Chronora.muted)
                }
                com.raunak.daytimeline.wellbeing.UsageBar(v.toInt(), c.budget.toInt())
            }
        }
    }
}

/** The last 14 days as bars, with the daily allowance as a dashed line. */
@Composable
private fun DailyChart(d: MoneyData, today: LocalDate) {
    val days = (13L downTo 0L).map { today.minusDays(it) }
    val values = days.map { MoneyEngine.spentOn(d, it) }
    val month = MoneyEngine.monthRange(today, d.settings.monthStartDay)
    val perDay = d.settings.monthlyBudget / (java.time.temporal.ChronoUnit.DAYS.between(month.first, month.second) + 1)
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(perDay * 1.2).coerceAtLeast(1.0)
    var picked by remember { mutableIntStateOf(13) }
    val bar = MaterialTheme.colorScheme.primary; val over = Chronora.colors.bad; val grid = MaterialTheme.colorScheme.outlineVariant
    SectionCard("Last 14 days", icon = Icons.Default.BarChart) {
        Text("${days[picked].format(DateTimeFormatter.ofPattern("EEE d MMM"))} · ${MoneyEngine.format(values[picked], d.settings.currency)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                Box(Modifier.weight(1f).fillMaxHeight((v / max).toFloat().coerceIn(.02f, 1f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background((if (v > perDay) over else bar).copy(alpha = if (i == picked) 1f else .55f)).clickable { picked = i })
            }
        }
        Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f)) }
        Text("Daily budget ${MoneyEngine.format(perDay, d.settings.currency)} · red = over", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
    }
}

// ------------------------------------------------------------------ activity

@Composable
private fun Activity(d: MoneyData, store: MoneyStore, onEdit: (Txn) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    val cur = d.settings.currency
    val cats = d.categories.associateBy { it.id }
    val wallets = d.wallets.associateBy { it.id }
    val list = d.txns.filter { t ->
        (filter == 0 || (filter == 1 && t.type == TxnType.EXPENSE) || (filter == 2 && t.type == TxnType.INCOME) || (filter == 3 && t.type == TxnType.TRANSFER)) &&
            (query.isBlank() || t.note.contains(query, true) || cats[t.categoryId]?.name?.contains(query, true) == true || MoneyEngine.format(t.amount, "").contains(query))
    }.sortedWith(compareByDescending<Txn> { it.date }.thenByDescending { it.minute })
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(8.dp))
            PillTabs(listOf("All", "Spent", "Income", "Moves"), filter) { filter = it }
        }
        if (list.isEmpty()) item { EmptyState("Nothing here yet", "Tap Add or a quick chip to log a spend.") }
        list.groupBy { it.date }.forEach { (date, items) ->
            item(key = "h$date") {
                val day = LocalDate.parse(date)
                Row(Modifier.padding(top = 8.dp)) {
                    Text(com.raunak.daytimeline.productivity.relativeDay(day) + " · " + day.format(DateTimeFormatter.ofPattern("d MMM")), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(MoneyEngine.format(items.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }, cur), style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                }
            }
            items(items, key = { it.id }) { t ->
                val c = cats[t.categoryId]
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable { onEdit(t) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(c?.color ?: 0xFF636A7E).copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                        Text(if (t.type == TxnType.TRANSFER) "🔄" else c?.emoji ?: "📦", fontSize = 20.sp)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(t.note.ifBlank { if (t.type == TxnType.TRANSFER) "${wallets[t.walletId]?.name} → ${wallets[t.toWalletId]?.name}" else c?.name ?: "" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(c?.name?.takeIf { t.note.isNotBlank() && t.type != TxnType.TRANSFER }, wallets[t.walletId]?.name, "%02d:%02d".format(t.minute / 60, t.minute % 60),
                            t.splitWith.takeIf { it.isNotEmpty() }?.let { "split with ${it.joinToString()}" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Chronora.muted, maxLines = 1)
                    }
                    Text((when (t.type) { TxnType.INCOME -> "+"; TxnType.EXPENSE -> "-"; else -> "" }) + MoneyEngine.format(t.amount, cur), fontWeight = FontWeight.SemiBold,
                        color = when (t.type) { TxnType.INCOME -> Chronora.colors.good; TxnType.EXPENSE -> MaterialTheme.colorScheme.onSurface; else -> Chronora.muted })
                }
            }
        }
    }
}

// ------------------------------------------------------------------ add / edit

/** Big amount with a keypad, then type, category, wallet, note, date and an optional split. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddSheet(initial: Txn, d: MoneyData, store: MoneyStore, startSplit: Boolean = false, close: () -> Unit) {
    val context = LocalContext.current
    val editing = initial.id != 0L
    var t by remember { mutableStateOf(initial) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) MoneyEngine.format(initial.amount, "").replace(",", "") else "") }
    var friends by remember { mutableStateOf(initial.splitWith.toSet()) }
    var newFriend by remember { mutableStateOf("") }
    var splitOn by remember { mutableStateOf(startSplit || initial.splitWith.isNotEmpty()) }
    val known = (d.debts.map { it.person.trim() } + d.txns.flatMap { it.splitWith }).filter { it.isNotBlank() }.distinct()
    val cur = d.settings.currency
    val value = amount.toDoubleOrNull() ?: 0.0
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                PillTabs(listOf("Spent", "Got", "Move"), t.type.ordinal) { i ->
                    val ty = TxnType.values()[i]
                    t = t.copy(type = ty, categoryId = if (ty == TxnType.INCOME) d.categories.first { it.income }.id else if (d.categories.firstOrNull { it.id == t.categoryId }?.income == true) d.categories.first { !it.income }.id else t.categoryId,
                        toWalletId = if (ty == TxnType.TRANSFER) d.wallets.firstOrNull { it.id != t.walletId }?.id ?: 0 else 0)
                }
            }
            item {
                Text(cur + (amount.ifBlank { "0" }), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 52.sp, fontWeight = FontWeight.Black,
                    color = if (t.type == TxnType.INCOME) Chronora.colors.good else MaterialTheme.colorScheme.onSurface)
                if (splitOn && friends.isNotEmpty() && value > 0) Text("Your share ${MoneyEngine.format(value - MoneyEngine.share(value, friends.size) * friends.size, cur)} · each friend owes ${MoneyEngine.format(MoneyEngine.share(value, friends.size), cur)}",
                    Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Chronora.muted, style = MaterialTheme.typography.bodySmall)
            }
            item { Keypad { k -> amount = when (k) { "⌫" -> amount.dropLast(1); "." -> if ("." in amount) amount else amount.ifBlank { "0" } + "."; else -> if (amount.substringAfter('.', "").length >= 2 && "." in amount) amount else (amount + k).trimStart('0').ifBlank { "0" }.let { if (it.startsWith(".")) "0$it" else it } } } }
            if (t.type != TxnType.TRANSFER) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.categories.filter { it.income == (t.type == TxnType.INCOME) }.forEach { c ->
                        FilterChip(t.categoryId == c.id, { t = t.copy(categoryId = c.id) }, label = { Text("${c.emoji} ${c.name}") }, shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(c.color).copy(alpha = .22f)))
                    }
                }
            }
            item {
                Text(if (t.type == TxnType.TRANSFER) "From" else "Paid with", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { d.wallets.forEach { w -> FilterChip(t.walletId == w.id, { t = t.copy(walletId = w.id) }, label = { Text("${w.emoji} ${w.name}") }) } }
                if (t.type == TxnType.TRANSFER) {
                    Text("To", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { d.wallets.filter { it.id != t.walletId }.forEach { w -> FilterChip(t.toWalletId == w.id, { t = t.copy(toWalletId = w.id) }, label = { Text("${w.emoji} ${w.name}") }) } }
                }
            }
            item { OutlinedTextField(t.note, { t = t.copy(note = it) }, placeholder = { Text("What for? (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) }
            item {
                val today = LocalDate.now()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(t.date == today.toString(), { t = t.copy(date = today.toString()) }, label = { Text("Today") })
                    FilterChip(t.date == today.minusDays(1).toString(), { t = t.copy(date = today.minusDays(1).toString()) }, label = { Text("Yesterday") })
                    PickerField("Date", t.date, { v -> if (v.isNotBlank()) t = t.copy(date = v) }, modifier = Modifier.weight(1f))
                }
            }
            if (t.type == TxnType.EXPENSE) item {
                SwitchRow("Split with friends", splitOn) { splitOn = it }
                if (splitOn) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (known + friends).distinct().forEach { p -> FilterChip(p in friends, { friends = if (p in friends) friends - p else friends + p }, label = { Text(p) }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newFriend, { newFriend = it }, placeholder = { Text("Friend's name") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                        FilledTonalIconButton(onClick = { if (newFriend.isNotBlank()) { friends = friends + newFriend.trim(); newFriend = "" } }) { Icon(Icons.Default.PersonAdd, "Add friend") }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (editing) OutlinedButton(onClick = {
                        store.remove(initial.id); close(); Feedback.show("Deleted") { store.add(initial) }
                    }, modifier = Modifier.height(54.dp)) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Chronora.colors.bad) }
                    Button(enabled = value > 0 && (t.type != TxnType.TRANSFER || t.toWalletId != 0L), modifier = Modifier.weight(1f).height(54.dp), onClick = {
                        val split = splitOn && t.type == TxnType.EXPENSE && friends.isNotEmpty()
                        val each = if (split) MoneyEngine.share(value, friends.size) else 0.0
                        // Your expense is your share; what friends owe goes to the ledger.
                        val mine = if (split) value - each * friends.size else value
                        val id = if (editing) initial.id else store.nextId()
                        val saved = t.copy(id = id, amount = mine, splitWith = if (split) friends.toList() else emptyList())
                        store.update { x ->
                            val txns = x.txns.filterNot { it.id == id } + saved
                            val debts = if (split && !editing) x.debts + friends.mapIndexed { i, p -> Debt(id + i + 1, p, each, t.note.ifBlank { "Split" }, t.date) } else x.debts
                            x.copy(txns = txns, debts = debts)
                        }
                        MoneyReminders.checkAlerts(context)
                        close()
                        val safe = MoneyEngine.safeToSpendToday(store.data.value, LocalDate.now())
                        Feedback.show(when {
                            t.type != TxnType.EXPENSE -> "Saved"
                            split -> "Split! ${friends.size} friend${if (friends.size > 1) "s" else ""} owe you ${MoneyEngine.format(each, cur)} each"
                            safe > 0 -> "Saved · ${MoneyEngine.format(safe, cur)} still safe today"
                            else -> "Saved · today's budget is used up"
                        })
                    }) { Text(if (editing) "Save changes" else "Save", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))
                        .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onKey(k) }, contentAlignment = Alignment.Center) {
                        if (k == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, "Delete digit") else Text(k, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ plan: budgets, recurring, goals, wait list, settings

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanTab(d: MoneyData, store: MoneyStore) {
    val context = LocalContext.current
    val cur = d.settings.currency
    val confirm = rememberConfirm()
    var newRecurring by remember { mutableStateOf(false) }
    var newGoal by remember { mutableStateOf(false) }
    var newWish by remember { mutableStateOf(false) }
    var addToGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var editCat by remember { mutableStateOf<MoneyCategory?>(null) }
    fun f(v: Double) = MoneyEngine.format(v, cur)
    fun settings(s: MoneySettings) { store.update { it.copy(settings = s) }; MoneyReminders.scheduleAll(context) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionCard("Budget", icon = Icons.Default.Savings) {
                val steps = 500.0
                Stepper("Monthly budget", f(d.settings.monthlyBudget), { settings(d.settings.copy(monthlyBudget = (d.settings.monthlyBudget - steps).coerceAtLeast(steps))) }, { settings(d.settings.copy(monthlyBudget = d.settings.monthlyBudget + steps)) })
                Stepper("Month starts on", "Day ${d.settings.monthStartDay}", { settings(d.settings.copy(monthStartDay = (d.settings.monthStartDay - 1).coerceAtLeast(1))) }, { settings(d.settings.copy(monthStartDay = (d.settings.monthStartDay + 1).coerceAtMost(28))) })
                Text("Per category", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                d.categories.filter { !it.income }.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Stepper("${c.emoji} ${c.name}", if (c.budget <= 0) "No limit" else f(c.budget),
                                { store.update { x -> x.copy(categories = x.categories.map { if (it.id == c.id) it.copy(budget = (it.budget - 100).coerceAtLeast(0.0)) else it }) } },
                                { store.update { x -> x.copy(categories = x.categories.map { if (it.id == c.id) it.copy(budget = it.budget + 100) else it }) } })
                        }
                        IconButton(onClick = { editCat = c }) { Icon(Icons.Default.Edit, "Edit ${c.name}", Modifier.size(18.dp)) }
                    }
                }
                TextButton(onClick = { editCat = MoneyCategory(store.nextId(), "", "🏷️", 0xFF4F5BD5) }) { Text("+ New category") }
            }
        }
        item {
            SectionCard("Repeating", icon = Icons.Default.EventRepeat, action = { TextButton(onClick = { newRecurring = true }) { Text("Add") } }) {
                if (d.recurring.isEmpty()) Text("Recharge, subscriptions, mess fee, allowance…", color = Chronora.muted)
                d.recurring.sortedBy { it.nextDate }.forEach { r ->
                    RecurringRow(r, d, store, onDelete = { confirm.ask(r.name) { store.update { x -> x.copy(recurring = x.recurring.filterNot { it.id == r.id }) } } })
                }
            }
        }
        item {
            SectionCard("Savings goals", icon = Icons.Default.Flag, action = { TextButton(onClick = { newGoal = true }) { Text("Add") } }) {
                if (d.goals.isEmpty()) Text("Save up for something you want", color = Chronora.muted)
                d.goals.forEach { g ->
                    val p by animateFloatAsState((g.saved / g.target.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f), tween(700), label = "goal")
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { addToGoal = g }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${g.emoji} ${g.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text("${f(g.saved)} / ${f(g.target)}", style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
                        }
                        LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)), color = Chronora.colors.good, drawStopIndicator = {})
                        if (g.saved >= g.target) Text("🎉 Reached!", color = Chronora.colors.good, style = MaterialTheme.typography.labelLarge)
                        else if (g.deadline.isNotBlank()) runCatching { LocalDate.parse(g.deadline) }.getOrNull()?.let { dl ->
                            val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dl).coerceAtLeast(1)
                            Text("Put aside ${f((g.target - g.saved) / days)} a day to make it by ${dl.format(DateTimeFormatter.ofPattern("d MMM"))}", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                        }
                    }
                }
            }
        }
        item {
            val saved = d.wishes.filter { it.decision == "skip" }.sumOf { it.price }
            SectionCard("Wait list", icon = Icons.Default.HourglassTop, action = { TextButton(onClick = { newWish = true }) { Text("Add") } }) {
                Text("Want something? Park it for ${d.settings.waitHours}h first. Most urges pass.", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                if (saved > 0) Text("💚 ${f(saved)} saved by waiting", color = Chronora.colors.good, fontWeight = FontWeight.SemiBold)
                val now = System.currentTimeMillis()
                d.wishes.filter { it.decision.isBlank() }.forEach { w ->
                    val ready = now - w.addedAt >= d.settings.waitHours * 3_600_000L
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${w.name} · ${f(w.price)}", style = MaterialTheme.typography.bodyLarge)
                            Text(if (ready) "Still want it?" else "Decide in ${((w.addedAt + d.settings.waitHours * 3_600_000L - now) / 3_600_000L) + 1}h", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                        }
                        if (ready) {
                            TextButton(onClick = {
                                store.update { x -> x.copy(wishes = x.wishes.map { if (it.id == w.id) it.copy(decision = "buy") else it },
                                    txns = x.txns + Txn(store.nextId(), w.price, TxnType.EXPENSE, x.categories.firstOrNull { it.name == "Shopping" }?.id ?: x.categories.first { !it.income }.id, x.wallets.first().id, date = LocalDate.now().toString(), minute = nowMinute(), note = w.name)) }
                                Feedback.show("Enjoy it! Logged ${f(w.price)}")
                            }) { Text("Buy") }
                        }
                        FilledTonalButton(onClick = { store.update { x -> x.copy(wishes = x.wishes.map { if (it.id == w.id) it.copy(decision = "skip") else it }) }; Feedback.show("💚 ${f(w.price)} saved. Nice self-control!") }) { Text("Skip it") }
                    }
                }
            }
        }
        item {
            SectionCard("Wallets", icon = Icons.Default.AccountBalanceWallet) {
                d.wallets.forEach { w ->
                    Stepper("${w.emoji} ${w.name} · starting balance", f(w.opening),
                        { store.update { x -> x.copy(wallets = x.wallets.map { if (it.id == w.id) it.copy(opening = it.opening - 100) else it }) } },
                        { store.update { x -> x.copy(wallets = x.wallets.map { if (it.id == w.id) it.copy(opening = it.opening + 100) else it }) } })
                }
                var name by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, placeholder = { Text("New wallet (e.g. Card)") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                    FilledTonalIconButton(onClick = { if (name.isNotBlank()) { store.update { x -> x.copy(wallets = x.wallets + Wallet(store.nextId(), name.trim(), "💳")) }; name = "" } }) { Icon(Icons.Default.Add, "Add wallet") }
                }
            }
        }
        item {
            SectionCard("Settings", icon = Icons.Default.Tune) {
                val s = d.settings
                SwitchRow("Evening reminder to log", s.reminderOn) { settings(s.copy(reminderOn = it)) }
                if (s.reminderOn) ClockRow("Reminder at", s.reminderMinute) { settings(s.copy(reminderMinute = it)) }
                SwitchRow("Spot payments from UPI apps", s.detectUpi) { settings(s.copy(detectUpi = it)) }
                if (s.detectUpi && !com.raunak.daytimeline.wellbeing.WellbeingNotificationListener.enabled(context))
                    TextButton(onClick = { com.raunak.daytimeline.wellbeing.openSettings(context, android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text("Allow notification access") }
                Text("Warn at", style = MaterialTheme.typography.bodyLarge)
                val pcts = listOf(50, 70, 80, 90)
                PillTabs(pcts.map { "$it%" }, pcts.indexOf(s.alertPercent).coerceAtLeast(0)) { settings(s.copy(alertPercent = pcts[it])) }
                Text("Wait list time", style = MaterialTheme.typography.bodyLarge)
                val waits = listOf(12, 24, 48, 72)
                PillTabs(waits.map { "${it}h" }, waits.indexOf(s.waitHours).coerceAtLeast(0)) { settings(s.copy(waitHours = waits[it])) }
                Expandable("Quick spend buttons") { QuickEditor(d, store) }
                Expandable("Currency") {
                    PillTabs(listOf("₹", "$", "€", "£"), listOf("₹", "$", "€", "£").indexOf(s.currency).coerceAtLeast(0)) { settings(s.copy(currency = listOf("₹", "$", "€", "£")[it])) }
                }
            }
        }
    }
    if (newRecurring) RecurringDialog(d, store) { newRecurring = false }
    if (newGoal) GoalDialog(store) { newGoal = false }
    if (newWish) WishDialog(store) { newWish = false }
    addToGoal?.let { g -> GoalMoneyDialog(g, d, store) { addToGoal = null } }
    editCat?.let { c -> CategoryDialog(c, d, store) { editCat = null } }
}

@Composable
private fun RecurringRow(r: Recurring, d: MoneyData, store: MoneyStore, onDelete: (() -> Unit)? = null) {
    val cur = d.settings.currency
    val due = LocalDate.parse(r.nextDate)
    val today = LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(d.categories.firstOrNull { it.id == r.categoryId }?.emoji ?: "🔁", fontSize = 22.sp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text("${r.name} · ${MoneyEngine.format(r.amount, cur)}", style = MaterialTheme.typography.bodyLarge)
            Text((if (r.everyDays > 0) "Every ${r.everyDays} days" else "Monthly on ${r.dayOfMonth}") + " · " +
                when { due < today -> "overdue"; due == today -> "today"; due == today.plusDays(1) -> "tomorrow"; else -> due.format(DateTimeFormatter.ofPattern("d MMM")) } + if (r.autoAdd) " · auto" else "",
                style = MaterialTheme.typography.bodySmall, color = if (due <= today) Chronora.colors.warn else Chronora.muted)
        }
        if (!r.autoAdd && due <= today.plusDays(1)) FilledTonalButton(onClick = { store.payRecurring(r, due); Feedback.show("${r.name} logged") }) { Text(if (r.type == TxnType.INCOME) "Got it" else "Paid") }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete ${r.name}") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickEditor(d: MoneyData, store: MoneyStore) {
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cat by remember { mutableLongStateOf(d.categories.first { !it.income }.id) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        d.settings.quick.forEach { q -> TextChip("${q.label} ${MoneyEngine.format(q.amount, d.settings.currency)}") { store.update { x -> x.copy(settings = x.settings.copy(quick = x.settings.quick - q)) } } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(label, { label = it }, placeholder = { Text("Chai") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
        OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, placeholder = { Text("15") }, singleLine = true, modifier = Modifier.width(90.dp), shape = RoundedCornerShape(14.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        d.categories.filter { !it.income }.forEach { c -> FilterChip(cat == c.id, { cat = c.id }, label = { Text(c.emoji) }) }
    }
    Button(enabled = label.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0, onClick = {
        store.update { x -> x.copy(settings = x.settings.copy(quick = x.settings.quick + QuickSpend(label.trim(), amount.toDouble(), cat))) }; label = ""; amount = ""
    }) { Text("Add button") }
}

@Composable
private fun AmountField(value: String, onChange: (String) -> Unit, label: String) =
    OutlinedTextField(value, { onChange(it.filter { c -> c.isDigit() || c == '.' }) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringDialog(d: MoneyData, store: MoneyStore, close: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    var cat by remember { mutableLongStateOf(d.categories.firstOrNull { it.name.startsWith("Recharge") }?.id ?: d.categories.first().id) }
    var monthly by remember { mutableStateOf(true) }
    var every by remember { mutableIntStateOf(28) }
    var day by remember { mutableIntStateOf(LocalDate.now().dayOfMonth.coerceAtMost(28)) }
    var auto by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = close, title = { Text("Repeating payment") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { PillTabs(listOf("Payment", "Income"), if (income) 1 else 0) { income = it == 1; cat = d.categories.first { c -> c.income == income }.id } }
            item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, placeholder = { Text(if (income) "Pocket money" else "Jio recharge") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { AmountField(amount, { amount = it }, "Amount") }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { d.categories.filter { it.income == income }.forEach { c -> FilterChip(cat == c.id, { cat = c.id }, label = { Text("${c.emoji} ${c.name}") }) } } }
            item { PillTabs(listOf("Monthly", "Every N days"), if (monthly) 0 else 1) { monthly = it == 0 } }
            item {
                if (monthly) Stepper("On day", "$day", { day = (day - 1).coerceAtLeast(1) }, { day = (day + 1).coerceAtMost(28) })
                else Stepper("Every", "$every days", { every = (every - 1).coerceAtLeast(1) }, { every = (every + 1).coerceAtMost(365) })
            }
            item { SwitchRow("Add it by itself", auto) { auto = it } }
        }
    }, confirmButton = {
        TextButton(enabled = name.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0, onClick = {
            val today = LocalDate.now()
            val first = if (monthly) today.withDayOfMonth(day).let { if (it < today) it.plusMonths(1) else it } else today
            store.update { x -> x.copy(recurring = x.recurring + Recurring(store.nextId(), name.trim(), amount.toDouble(), cat, x.wallets.first().id, if (income) TxnType.INCOME else TxnType.EXPENSE,
                if (monthly) 0 else every, day, first.toString(), auto)) }
            MoneyReminders.scheduleAll(context); close(); Feedback.show("${name.trim()} added")
        }) { Text("Add") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun GoalDialog(store: MoneyStore, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎧") }
    var target by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Savings goal") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("🎧", "📱", "💻", "✈️", "🎁", "👟", "🏍️", "📚", "🎮", "💰").forEach { e -> Box(Modifier.size(40.dp).clip(CircleShape).background(if (emoji == e) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).clickable { emoji = e }, contentAlignment = Alignment.Center) { Text(e, fontSize = 20.sp) } }
            }
            OutlinedTextField(name, { name = it }, label = { Text("For") }, placeholder = { Text("New headphones") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            AmountField(target, { target = it }, "Target")
            PickerField("By (optional)", deadline, { deadline = it }, clearable = true)
        }
    }, confirmButton = {
        TextButton(enabled = name.isNotBlank() && (target.toDoubleOrNull() ?: 0.0) > 0, onClick = {
            store.update { x -> x.copy(goals = x.goals + SavingsGoal(store.nextId(), name.trim(), emoji, target.toDouble(), deadline = deadline)) }; close()
        }) { Text("Add") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun GoalMoneyDialog(g: SavingsGoal, d: MoneyData, store: MoneyStore, close: () -> Unit) {
    val confirm = rememberConfirm()
    var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("${g.emoji} ${g.name}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${MoneyEngine.format(g.saved, d.settings.currency)} of ${MoneyEngine.format(g.target, d.settings.currency)} saved", color = Chronora.muted)
            AmountField(amount, { amount = it }, "Put aside")
            TextButton(onClick = { confirm.ask(g.name) { store.update { x -> x.copy(goals = x.goals.filterNot { it.id == g.id }) }; close() } }) { Text("Delete goal", color = Chronora.colors.bad) }
        }
    }, confirmButton = {
        TextButton(enabled = (amount.toDoubleOrNull() ?: 0.0) > 0, onClick = {
            val v = amount.toDouble()
            store.update { x -> x.copy(goals = x.goals.map { if (it.id == g.id) it.copy(saved = it.saved + v) else it }) }
            close(); Feedback.show(if (g.saved + v >= g.target) "🎉 Goal reached! ${g.name} is yours." else "💚 ${MoneyEngine.format(g.target - g.saved - v, d.settings.currency)} to go")
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun WishDialog(store: MoneyStore, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Wait before buying") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("What") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            AmountField(price, { price = it }, "Price")
        }
    }, confirmButton = {
        TextButton(enabled = name.isNotBlank() && (price.toDoubleOrNull() ?: 0.0) > 0, onClick = {
            store.update { x -> x.copy(wishes = x.wishes + Wish(store.nextId(), name.trim(), price.toDouble(), System.currentTimeMillis())) }; close()
            Feedback.show("Parked. Decide later with a clear head.")
        }) { Text("Park it") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun CategoryDialog(c: MoneyCategory, d: MoneyData, store: MoneyStore, close: () -> Unit) {
    val confirm = rememberConfirm()
    val isNew = d.categories.none { it.id == c.id }
    var cat by remember { mutableStateOf(c) }
    val colors = listOf(0xFFEF6A45, 0xFFDB8F12, 0xFF2F8FE0, 0xFF0E9F9A, 0xFF7C5CE6, 0xFF5B6BB0, 0xFFE5486B, 0xFFE0434C, 0xFF22A06B, 0xFF636A7E)
    val emojis = listOf("🍔", "☕", "🛺", "📶", "📚", "🧺", "🎉", "🛍️", "💊", "📦", "🏋️", "🎬", "🍕", "🚌", "💇", "🎁", "🏷️")
    AlertDialog(onDismissRequest = close, title = { Text(if (isNew) "New category" else "Edit category") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(cat.name, { cat = cat.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                emojis.forEach { e -> Box(Modifier.size(38.dp).clip(CircleShape).background(if (cat.emoji == e) Color(cat.color).copy(alpha = .25f) else Color.Transparent).clickable { cat = cat.copy(emoji = e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 20.sp) } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                colors.forEach { col -> Box(Modifier.size(28.dp).clip(CircleShape).background(Color(col)).border(3.dp, if (cat.color == col) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape).clickable { cat = cat.copy(color = col) }) }
            }
            if (!isNew && d.txns.none { it.categoryId == c.id }) TextButton(onClick = { confirm.ask(c.name) { store.update { x -> x.copy(categories = x.categories.filterNot { it.id == c.id }) }; close() } }) { Text("Delete", color = Chronora.colors.bad) }
        }
    }, confirmButton = {
        TextButton(enabled = cat.name.isNotBlank(), onClick = { store.update { x -> x.copy(categories = x.categories.filterNot { it.id == cat.id } + cat.copy(name = cat.name.trim())) }; close() }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

// ------------------------------------------------------------------ friends

@Composable
private fun FriendsTab(d: MoneyData, store: MoneyStore, onSplit: () -> Unit) {
    val cur = d.settings.currency
    val ledger = MoneyEngine.ledger(d)
    var owe by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            val owed = ledger.values.filter { it > 0 }.sum(); val mine = -ledger.values.filter { it < 0 }.sum()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("They owe you", MoneyEngine.format(owed, cur), Modifier.weight(1f))
                StatTile("You owe", MoneyEngine.format(mine, cur), Modifier.weight(1f))
            }
        }
        item { OutlinedButton(onClick = { owe = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.SwapHoriz, null); Text("  Record money borrowed or lent") } }
        if (ledger.isEmpty()) item { EmptyState("All settled", "Split a bill and it shows up here.") }
        items(ledger.entries.sortedByDescending { kotlin.math.abs(it.value) }.toList(), key = { it.key }) { (person, net) ->
            val history = d.debts.filter { it.person.trim() == person && !it.settled }.sortedByDescending { it.date }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text(person.take(1).uppercase(), fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(person, style = MaterialTheme.typography.titleMedium)
                        Text(if (net > 0) "owes you ${MoneyEngine.format(net, cur)}" else "you owe ${MoneyEngine.format(-net, cur)}", color = if (net > 0) Chronora.colors.good else Chronora.colors.bad, fontWeight = FontWeight.SemiBold)
                    }
                    FilledTonalButton(onClick = {
                        val ids = history.map { it.id }.toSet()
                        store.update { x -> x.copy(debts = x.debts.map { if (it.id in ids) it.copy(settled = true) else it }) }
                        Feedback.show("Settled with $person ✓") { store.update { x -> x.copy(debts = x.debts.map { if (it.id in ids) it.copy(settled = false) else it }) } }
                    }) { Text("Settle") }
                }
                history.take(4).forEach { h -> Text("${h.note} · ${MoneyEngine.format(h.amount, cur)} · ${LocalDate.parse(h.date).format(DateTimeFormatter.ofPattern("d MMM"))}", style = MaterialTheme.typography.bodySmall, color = Chronora.muted) }
            }
        }
    }
    if (owe) OweDialog(d, store) { owe = false }
}

@Composable
private fun OweDialog(d: MoneyData, store: MoneyStore, close: () -> Unit) {
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var iOwe by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = close, title = { Text("Borrowed or lent") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillTabs(listOf("I lent", "I borrowed"), if (iOwe) 1 else 0) { iOwe = it == 1 }
            OutlinedTextField(person, { person = it }, label = { Text("Friend") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            AmountField(amount, { amount = it }, "Amount")
            OutlinedTextField(note, { note = it }, label = { Text("For (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        TextButton(enabled = person.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0, onClick = {
            val v = amount.toDouble() * if (iOwe) -1 else 1
            store.update { x -> x.copy(debts = x.debts + Debt(store.nextId(), person.trim(), v, note.ifBlank { if (iOwe) "Borrowed" else "Lent" }, LocalDate.now().toString())) }; close()
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
