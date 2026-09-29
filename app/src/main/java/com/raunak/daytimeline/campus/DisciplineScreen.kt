package com.raunak.daytimeline.campus

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.filter.FilterCategory
import com.raunak.daytimeline.filter.FilterLock
import com.raunak.daytimeline.filter.UpstreamDns
import com.raunak.daytimeline.filter.WebFilterStore
import com.raunak.daytimeline.filter.WebFilterVpnService
import com.raunak.daytimeline.pro.BlockSchedule
import com.raunak.daytimeline.pro.FocusGuardService
import com.raunak.daytimeline.pro.FocusGuardStore
import com.raunak.daytimeline.wellbeing.WellbeingStore
import kotlinx.coroutines.delay
import java.time.LocalDate

private const val GUARD_SCHEDULE_ID = 770_101L

/** Asks for the phone's PIN/fingerprint before showing the Discipline tab. */
@Composable
internal fun DisciplineGate() {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { unlocked = it.resultCode == Activity.RESULT_OK }
    fun ask() {
        val intent = keyguard.createConfirmDeviceCredentialIntent("Discipline", "Private — confirm it's you")
        if (intent == null || !keyguard.isKeyguardSecure) unlocked = true else launcher.launch(intent)
    }
    LaunchedEffect(Unit) { ask() }
    if (unlocked) DisciplineTab() else Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Lock, null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Private section", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { ask() }) { Text("Unlock") }
    }
}

@Composable
private fun DisciplineTab() {
    val context = LocalContext.current
    val store = remember { DisciplineStore.get(context) }
    val s by store.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000) } }
    var sos by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }

    if (s.started == 0L) { SetupCard(store); return }
    val ins = DisciplineEngine.insights(s, now)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17221E))) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${ins.currentDays}", color = Color.White, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                    Text(if (ins.currentDays == 1) "day strong" else "days strong", color = Color(0xFFB9CCC2))
                    Text("${ins.currentHours % 24}h into day ${ins.currentDays + 1} · best ${ins.bestDays} days", color = Color(0xFFD5E0DA), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { (ins.currentDays.toFloat() / ins.nextMilestone).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Color(0xFFA8C7B7), trackColor = Color.White.copy(alpha = .15f))
                    Text("Next milestone: ${ins.nextMilestone} days", color = Color(0xFFD5E0DA), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Button(onClick = { sos = true }, modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B))) {
                Text("I'm having an urge — help me now", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            val today = LocalDate.now()
            SectionCard("Today", s.checkIns[today.toString()]?.let { if (it) "Checked in: stayed on track" else "Checked in: slipped" } ?: "Not checked in yet") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { store.checkIn(today, true) }) { Text("Stayed on track") }
                    OutlinedButton(onClick = { resetting = true }) { Text("I slipped") }
                }
                Text("${ins.cleanDaysThisMonth} clean days this month. A slip resets the counter, not your progress — your best and your insights stay.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { ProtectionCard(s, store) }
        item {
            SectionCard("Why I'm doing this", "Shown to you during every urge") {
                s.reasons.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• $r", Modifier.weight(1f))
                        IconButton(onClick = { store.update { it.copy(reasons = it.reasons - r) } }) { Icon(Icons.Default.Close, "Remove") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(reason, { reason = it }, label = { Text("Add a reason") }, placeholder = { Text("Get placed. Clear head. Be proud of myself.") }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (reason.isNotBlank()) { store.update { it.copy(reasons = it.reasons + reason.trim()) }; reason = "" } }) { Text("Add") }
                }
            }
        }
        item {
            SectionCard("Patterns", "${s.urges.size} urges logged" + (ins.resistRate?.let { " · $it% resisted" } ?: "")) {
                if (ins.riskiestHours.isNotEmpty()) Text("Riskiest hours: " + ins.riskiestHours.joinToString { "%02d:00".format(it) }, fontWeight = FontWeight.SemiBold)
                if (s.urges.isNotEmpty() || s.resets.isNotEmpty()) HourBars(ins.urgesByHour)
                if (ins.topTriggers.isNotEmpty()) Text("Top triggers: " + ins.topTriggers.joinToString { "${it.first} (${it.second})" }, style = MaterialTheme.typography.bodySmall)
                s.resets.takeLast(3).reversed().filter { it.lesson.isNotBlank() }.forEach { Text("Lesson: ${it.lesson}", style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            SectionCard("Settings") {
                SwitchRow("Daily ${clock(s.settings.checkInMinute)} check-in (notification says only \"Daily check-in\")", s.dailyCheckIn) { v -> store.update { it.copy(dailyCheckIn = v) }; CampusScheduler.rescheduleAll(context) }
                DisciplineSettingsEditor(s.settings) { next -> store.update { it.copy(settings = next) }; CampusScheduler.rescheduleAll(context) }
                Text("This section is protected by your phone lock, stored separately and never included in backups.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (sos) UrgeSos(s, store) { sos = false }
    if (resetting) ResetDialog(store) { resetting = false }
}

@Composable
private fun SetupCard(store: DisciplineStore) {
    var daysAgo by remember { mutableIntStateOf(0) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("Discipline", "A private streak tracker for a habit you want to quit, with an urge SOS, pattern insights and automatic protection during your risky hours.") {
                Stepper("Current streak (days)", "$daysAgo", { daysAgo = (daysAgo - 1).coerceAtLeast(0) }, { daysAgo++ })
                Button(onClick = {
                    val now = System.currentTimeMillis()
                    store.update { it.copy(started = now - daysAgo * 86_400_000L, streakStart = now - daysAgo * 86_400_000L) }
                }) { Text("Start") }
            }
        }
    }
}

@Composable
private fun HourBars(values: IntArray) {
    val color = MaterialTheme.colorScheme.error
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    Column {
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val slot = size.width / 24
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height))
            values.forEachIndexed { i, v ->
                if (v == 0) return@forEachIndexed
                val h = size.height * v / max
                drawRoundRect(color, Offset(i * slot + 1, size.height - h), Size(slot - 2, h), CornerRadius(3f, 3f))
            }
        }
        Row { listOf("00", "06", "12", "18").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) } }
    }
}

@Composable
private fun ProtectionCard(s: DisciplineState, store: DisciplineStore) {
    val context = LocalContext.current
    val filterStore = remember { WebFilterStore(context) }
    val guardStore = remember { FocusGuardStore(context) }
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { delay(2000); tick++ } }
    val filter = remember(tick) { filterStore.config }
    val guard = remember(tick) { guardStore.config }
    val filterOn = filter.enabled && WebFilterVpnService.running && FilterCategory.ADULT in filter.categories
    val strong = filter.keywordBlocking && filter.safeSearch && filter.blockBypass && filter.upstream.name.contains("FAMILY")
    val locked = filter.lockDelayMinutes >= s.settings.protectionLockMinutes
    val schedule = guard.schedules.firstOrNull { it.id == GUARD_SCHEDULE_ID }
    val strict = WellbeingStore(context).config.strictMode

    fun applyFilter() {
        val old = filterStore.config
        val next = old.copy(
            enabled = true,
            categories = old.categories + FilterCategory.ADULT,
            keywordBlocking = true, safeSearch = true, youtubeRestricted = true, blockBypass = true,
            upstream = if (old.upstream.name.contains("FAMILY")) old.upstream else UpstreamDns.CLOUDFLARE_FAMILY,
            lockDelayMinutes = maxOf(old.lockDelayMinutes, s.settings.protectionLockMinutes)
        )
        // Only ever tightens, so it is allowed even while locked.
        if (!FilterLock.isLoosening(old, next)) filterStore.config = next
        WebFilterVpnService.start(context, reload = true)
        msg = "Web protection on · changes locked for ${hm(s.settings.protectionLockMinutes)}"
    }
    val vpn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == Activity.RESULT_OK) applyFilter() }

    SectionCard("Protection", "Make the easy path hard") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (filterOn && strong && locked) "✓ " else "✗ ") + "Web filter: adult sites, keywords, SafeSearch, bypass blocking, family DNS, ${hm(s.settings.protectionLockMinutes)} lock", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (!(filterOn && strong && locked)) TextButton(onClick = { val i = VpnService.prepare(context); if (i != null) vpn.launch(i) else applyFilter() }) { Text("Turn on") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (schedule != null) "✓ " else "✗ ") + "Risk hours ${clock(s.riskStart)}–${clock(s.riskEnd)}: block browsers & social apps", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                val pkgs = s.settings.guardedApps.ifEmpty { browsersAndSocial(context) }
                if (s.settings.guardedApps.isEmpty()) store.update { it.copy(settings = it.settings.copy(guardedApps = pkgs)) }
                guardStore.update { g ->
                    g.copy(
                        blockedPackages = g.blockedPackages + pkgs,
                        schedules = g.schedules.filterNot { it.id == GUARD_SCHEDULE_ID } + BlockSchedule(GUARD_SCHEDULE_ID, "Night focus", (1..7).toSet(), s.riskStart, s.riskEnd)
                    )
                }
                msg = "Blocking ${pkgs.size} apps every night ${clock(s.riskStart)}–${clock(s.riskEnd)}"
            }) { Text(if (schedule == null) "Turn on" else "Update") }
        }
        Stepper("Risk hours start", clock(s.riskStart), { store.update { it.copy(riskStart = (it.riskStart - 30 + 1440) % 1440) } }, { store.update { it.copy(riskStart = (it.riskStart + 30) % 1440) } })
        Stepper("Risk hours end", clock(s.riskEnd), { store.update { it.copy(riskEnd = (it.riskEnd - 30 + 1440) % 1440) } }, { store.update { it.copy(riskEnd = (it.riskEnd + 30) % 1440) } })
        if (DisciplineEngine.insights(s, System.currentTimeMillis()).riskiestHours.isNotEmpty()) Text("Your logged urges suggest: " + DisciplineEngine.insights(s, System.currentTimeMillis()).riskiestHours.joinToString { "%02d:00".format(it) }, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (FocusGuardService.isEnabled(context)) "✓ " else "✗ ") + "App blocking service on", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (!FocusGuardService.isEnabled(context)) TextButton(onClick = { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Enable") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (strict) "✓ " else "✗ ") + "Strict mode (can't switch protection off during a block)", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (!strict) TextButton(onClick = { WellbeingStore(context).update { it.copy(strictMode = true) }; tick++ }) { Text("Turn on") }
        }
        Text("Also: keep the phone out of bed — charge it across the room. Set Always-on VPN in Web filter for a filter that can't be closed.", style = MaterialTheme.typography.bodySmall)
        if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
}

private fun browsersAndSocial(context: android.content.Context): Set<String> {
    val pm = context.packageManager
    val browsers = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), 0).map { it.activityInfo.packageName }
    val social = listOf("com.instagram.android", "com.twitter.android", "com.reddit.frontpage", "com.snapchat.android", "com.zhiliaoapp.musically", "com.tumblr", "com.discord", "org.telegram.messenger", "com.facebook.katana", "com.pinterest")
    val installed = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet()
    return (browsers + social).filter { it in installed && it != context.packageName }.toSet()
}

@Composable
private fun ResetDialog(store: DisciplineStore, close: () -> Unit) {
    var trigger by remember { mutableStateOf("") }
    var lesson by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Reset — and learn from it") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("No shame. What led to it?", style = MaterialTheme.typography.bodySmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(store.state.value.settings.triggers) { t -> FilterChip(trigger == t, { trigger = t }, label = { Text(t) }) } }
            OutlinedTextField(lesson, { lesson = it }, label = { Text("What will you do differently?") }, minLines = 2)
        }
    }, confirmButton = {
        Button(onClick = {
            store.update { DisciplineEngine.reset(it, System.currentTimeMillis(), trigger, lesson.trim()) }
            store.checkIn(LocalDate.now(), false)
            close()
        }) { Text("Reset streak") }
    }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

/** Urge surfing: breathe, remember why, act, and let the wave pass (usually 10–15 minutes). */
@Composable
private fun UrgeSos(s: DisciplineState, store: DisciplineStore, close: () -> Unit) {
    val ins = DisciplineEngine.insights(s, System.currentTimeMillis())
    var seconds by remember { mutableIntStateOf(s.settings.urgeTimerMinutes * 60) }
    var done by remember { mutableStateOf(setOf<Int>()) }
    var intensity by remember { mutableFloatStateOf(3f) }
    var trigger by remember { mutableStateOf("") }
    var finishing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (seconds > 0) { delay(1000); seconds-- } }
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "scale")

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101815)) {
            LazyColumn(Modifier.padding(20.dp).testTag("sos"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Day ${ins.currentDays}. Don't trade it for 10 minutes.", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                item {
                    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(170.dp).scale(scale).background(Color(0xFF55786A), CircleShape))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (scale > 0.78f) "breathe out" else "breathe in", color = Color.White)
                            Text("%d:%02d".format(seconds / 60, seconds % 60), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item { Text("Urges rise, peak and fade — usually within ${s.settings.urgeTimerMinutes}–15 minutes. Ride it out; you don't have to act on it.", color = Color(0xFFB9CCC2), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall) }
                if (s.reasons.isNotEmpty()) item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B26))) {
                        Column(Modifier.padding(14.dp).fillMaxWidth()) {
                            Text("Why you started", color = Color(0xFFA8C7B7), style = MaterialTheme.typography.labelLarge)
                            s.reasons.forEach { Text("• $it", color = Color.White) }
                        }
                    }
                }
                item { Text("Do one now:", color = Color(0xFFA8C7B7), style = MaterialTheme.typography.labelLarge) }
                items(s.settings.actions.indices.toList()) { i ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(i in done, { done = if (i in done) done - i else done + i })
                        Text(s.settings.actions[i], color = Color.White)
                    }
                }
                item {
                    if (!finishing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { finishing = true }) { Text("It passed") }
                        OutlinedButton(onClick = close) { Text("Close") }
                    } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("How strong was it? ${intensity.toInt()}/5", color = Color.White)
                        Slider(intensity, { intensity = it }, valueRange = 1f..5f, steps = 3)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(s.settings.triggers) { t -> FilterChip(trigger == t, { trigger = t }, label = { Text(t) }) } }
                        Button(onClick = {
                            store.update { it.copy(urges = (it.urges + UrgeLog(System.currentTimeMillis(), intensity.toInt(), trigger, true)).takeLast(1000)) }
                            close()
                        }) { Text("Log it — I won") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisciplineSettingsEditor(st: DisciplineSettings, save: (DisciplineSettings) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    Stepper("Check-in time", clock(st.checkInMinute), { save(st.copy(checkInMinute = ((st.checkInMinute - 15) + 1440) % 1440)) }, { save(st.copy(checkInMinute = (st.checkInMinute + 15) % 1440)) })
    Stepper("Urge timer", "${st.urgeTimerMinutes}m", { save(st.copy(urgeTimerMinutes = (st.urgeTimerMinutes - 1).coerceAtLeast(1))) }, { save(st.copy(urgeTimerMinutes = st.urgeTimerMinutes + 1)) })
    Stepper("Protection lock", hm(st.protectionLockMinutes), { save(st.copy(protectionLockMinutes = (st.protectionLockMinutes - 60).coerceAtLeast(5))) }, { save(st.copy(protectionLockMinutes = st.protectionLockMinutes + 60)) })
    ListEditor("Milestones (days)", st.milestones.map { it.toString() }, numeric = true) { save(st.copy(milestones = it.mapNotNull(String::toIntOrNull).filter { n -> n > 0 }.distinct().sorted())) }
    ListEditor("Triggers", st.triggers) { save(st.copy(triggers = it)) }
    ListEditor("Actions during an urge", st.actions) { save(st.copy(actions = it)) }
    TextButton(onClick = { picking = !picking }) { Text("Apps blocked in risk hours (${st.guardedApps.size})") }
    if (picking) {
        val apps = remember {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }.filter { it.packageName != context.packageName }
                .map { it.packageName to pm.getApplicationLabel(it).toString() }.sortedBy { it.second.lowercase() }
        }
        Column(Modifier.heightIn(max = 280.dp)) {
            LazyColumn { items(apps, key = { it.first }) { (pkg, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(pkg in st.guardedApps, { on -> save(st.copy(guardedApps = if (on) st.guardedApps + pkg else st.guardedApps - pkg)) })
                    Text(label)
                }
            } }
        }
        TextButton(onClick = { save(st.copy(guardedApps = browsersAndSocial(context))) }) { Text("Suggest browsers & social apps") }
        Text("Tap Update under Protection to apply the new list.", style = MaterialTheme.typography.labelSmall)
    }
}
