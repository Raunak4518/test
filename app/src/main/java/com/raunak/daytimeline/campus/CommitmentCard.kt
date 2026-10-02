package com.raunak.daytimeline.campus

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.filter.FilterCategory
import com.raunak.daytimeline.filter.FilterLock
import com.raunak.daytimeline.filter.UpstreamDns
import com.raunak.daytimeline.filter.WebFilterStore
import com.raunak.daytimeline.filter.WebFilterVpnService
import com.raunak.daytimeline.pro.FocusGuardService
import com.raunak.daytimeline.ui.*
import com.raunak.daytimeline.wellbeing.WellbeingStore
import kotlinx.coroutines.delay
import java.time.LocalDate

private val Durations = listOf(3, 7, 14, 30, 60, 90)

/** End time of an active commitment lock, or null; settings screens use it to hide anything that could undo it. */
@Composable
fun rememberLockedUntil(): Long? {
    val context = LocalContext.current
    val state by remember { DisciplineStore.get(context.applicationContext).state }.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    return state.commit.until.takeIf { Commitment.active(state.commit, now) }
}

/** Shown instead of settings while the lock is on. */
@Composable
fun LockedPanel(until: Long, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Lock, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
        }
        Text("Locked", style = MaterialTheme.typography.headlineSmall)
        Text("Until ${Commitment.dateText(until)}", color = Chronora.muted)
    }
}

/** Social, video, games and news apps that are installed: the default all-day block list. */
private fun distractingApps(context: android.content.Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val known = setOf("com.instagram.android", "com.google.android.youtube", "com.zhiliaoapp.musically", "com.snapchat.android", "com.twitter.android", "com.reddit.frontpage",
        "com.facebook.katana", "com.pinterest", "com.tumblr", "com.discord", "com.netflix.mediaclient", "in.startv.hotstar", "com.mxtech.videoplayer.ad", "com.sharechat.app", "in.mohalla.video", "com.moj.app")
    val cats = setOf(android.content.pm.ApplicationInfo.CATEGORY_SOCIAL, android.content.pm.ApplicationInfo.CATEGORY_VIDEO, android.content.pm.ApplicationInfo.CATEGORY_GAME, android.content.pm.ApplicationInfo.CATEGORY_NEWS)
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
        .filter { it.packageName != context.packageName && (it.packageName in known || it.category in cats) }
        .map { it.packageName to pm.getApplicationLabel(it).toString() }.sortedBy { it.second.lowercase() }
}

/**
 * The commitment lock: pick shields and a length, confirm, and nothing can be loosened until the end date.
 * While it runs, the card shows the countdown and lets you only add time.
 */
@Composable
internal fun CommitmentCard(s: DisciplineState, store: DisciplineStore) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }
    if (Commitment.active(s.commit, now)) LockedView(s, store, now) else SetupView(s, store)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LockedView(s: DisciplineState, store: DisciplineStore, now: Long) {
    val context = LocalContext.current
    val c = s.commit
    var extend by remember { mutableStateOf<Int?>(null) }
    val total = (c.until - c.started).coerceAtLeast(1)
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = Chronora.colors.onHero)
            Text("  Commitment lock", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.labelLarge)
        }
        Text("${Commitment.daysLeft(c, now)} days left", color = Chronora.colors.onHero, style = MaterialTheme.typography.headlineLarge)
        Text("Until ${Commitment.dateText(c.until)} · nothing can be loosened before then", color = Chronora.colors.heroMuted, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(progress = { ((now - c.started).toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = Chronora.colors.heroAccent, trackColor = Color.White.copy(alpha = .18f))
        val today = s.shieldLog[LocalDate.now().toString()] ?: 0
        val all = s.shieldLog.values.sum()
        Text("Blocked $today today · $all in total", color = Chronora.colors.onHero, style = MaterialTheme.typography.labelLarge)
        if (c.letter.isNotBlank()) Text("“${c.letter}”", color = Chronora.colors.onHero, style = MaterialTheme.typography.titleMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }
    if (c.blockedApps.isNotEmpty()) SectionCard("Blocked all day · ${c.blockedApps.size}", icon = Icons.Default.Block) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            c.blockedApps.forEach { pkg -> Box { com.raunak.daytimeline.wellbeing.AppIcon(pkg, 40.dp); Box(Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .4f))) } }
        }
    }
    SectionCard("Active shields", icon = Icons.Default.Shield) {
        Shields(c).forEach { (icon, label, on) -> if (on) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = Chronora.colors.good); Text("  $label", style = MaterialTheme.typography.bodyLarge)
        } }
        Text("Add time", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30).forEach { d -> FilledTonalButton(onClick = { extend = d }, shape = RoundedCornerShape(50)) { Text("+$d days") } }
        }
        Checklist()
    }
    extend?.let { d ->
        AlertDialog(onDismissRequest = { extend = null }, icon = { Icon(Icons.Default.Lock, null) }, title = { Text("Add $d days?") },
            text = { Text("New end: ${Commitment.dateText(c.until + d * 86_400_000L)}") },
            confirmButton = { Button(onClick = {
                val next = c.copy(until = c.until + d * 86_400_000L)
                store.update { it.copy(commit = Commitment.tighten(it.commit, next)) }
                val fs = WebFilterStore(context)
                fs.config = fs.config.copy(commitUntil = maxOf(fs.config.commitUntil, next.until))
                Feedback.show("Locked until ${Commitment.dateText(next.until)}")
                extend = null
            }) { Text("Add") } }, dismissButton = { TextButton(onClick = { extend = null }) { Text("Cancel") } })
    }
}

private fun Shields(c: CommitLock): List<Triple<ImageVector, String, Boolean>> = listOf(
    Triple(Icons.Default.Public, "Web filter can't be switched off", true),
    Triple(Icons.Default.VisibilityOff, "Content shield", c.contentShield),
    Triple(Icons.Default.Tab, "No private tabs", c.noPrivateTabs),
    Triple(Icons.Default.Language, "One browser only", c.oneBrowser),
    Triple(Icons.Default.SlowMotionVideo, "No short-video feeds", c.noShortVideos),
    Triple(Icons.Default.Bedtime, "Night shield", c.nightShield),
    Triple(Icons.Default.Sync, "Filter restarts by itself", c.keepFilterOn)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupView(s: DisciplineState, store: DisciplineStore) {
    val context = LocalContext.current
    val suggested = remember { distractingApps(context) }
    var c by remember { mutableStateOf(s.commit.copy(nightStart = s.riskStart, nightEnd = s.riskEnd, blockedApps = s.commit.blockedApps.ifEmpty { suggested.map { it.first }.toSet() })) }
    var days by remember { mutableIntStateOf(30) }
    var confirming by remember { mutableStateOf(false) }
    var pickNight by remember { mutableStateOf(false) }
    val browsers = remember {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), 0).map { it.activityInfo.packageName }.distinct()
            .map { it to runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrDefault(it) }
    }

    fun lockNow() {
        val nowMs = System.currentTimeMillis()
        val until = nowMs + days * 86_400_000L
        val next = c.copy(until = until, started = nowMs, browser = if (browsers.none { it.first == c.browser }) browsers.firstOrNull()?.first ?: c.browser else c.browser)
        store.update { it.copy(commit = Commitment.tighten(it.commit, next, nowMs)) }
        // Web filter: everything on, family DNS, and no loosening until the end date.
        val fs = WebFilterStore(context)
        val old = fs.config
        val tightened = old.copy(enabled = true, categories = old.categories + FilterCategory.ADULT, keywordBlocking = true, safeSearch = true, youtubeRestricted = true, blockBypass = true,
            upstream = if (old.upstream.name.contains("FAMILY")) old.upstream else UpstreamDns.CLOUDFLARE_FAMILY, commitUntil = maxOf(old.commitUntil, until))
        if (!FilterLock.isLoosening(old, tightened)) fs.config = tightened
        WebFilterVpnService.start(context, reload = true)
        WellbeingStore(context).update { it.copy(strictMode = true) }
        Feedback.show("Locked for $days days")
    }
    val vpn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == Activity.RESULT_OK) lockNow() }

    SectionCard("Commitment lock", icon = Icons.Default.Lock) {
        Text("Turn it on once. It can't be undone until the date you pick.", style = MaterialTheme.typography.bodyMedium)
        Text("Block all day · ${c.blockedApps.size}", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            suggested.forEach { (pkg, label) ->
                FilterChip(pkg in c.blockedApps, { c = c.copy(blockedApps = if (pkg in c.blockedApps) c.blockedApps - pkg else c.blockedApps + pkg) }, label = { Text(label) },
                    leadingIcon = { com.raunak.daytimeline.wellbeing.AppIcon(pkg, 18.dp) })
            }
        }
        SwitchRow("Content shield", c.contentShield) { c = c.copy(contentShield = it) }
        SwitchRow("No private tabs", c.noPrivateTabs) { c = c.copy(noPrivateTabs = it) }
        SwitchRow("One browser only", c.oneBrowser) { c = c.copy(oneBrowser = it) }
        if (c.oneBrowser && browsers.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            browsers.forEach { (pkg, label) -> FilterChip(c.browser == pkg, { c = c.copy(browser = pkg) }, label = { Text(label) }) }
        }
        SwitchRow("No short-video feeds", c.noShortVideos) { c = c.copy(noShortVideos = it) }
        SwitchRow("Night shield", c.nightShield) { c = c.copy(nightShield = it) }
        if (c.nightShield) {
            SwitchRow("Night ends at my alarm", c.nightFromAlarm) { c = c.copy(nightFromAlarm = it) }
            if (!c.nightFromAlarm) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pickNight = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Night", Modifier.weight(1f)); Text("${clock(c.nightStart)} – ${clock(c.nightEnd)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        OutlinedTextField(c.letter, { c = c.copy(letter = it.take(160)) }, Modifier.fillMaxWidth(), placeholder = { Text("A line to future you") }, shape = RoundedCornerShape(14.dp), minLines = 2)
        Text("Length", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Durations.forEach { d -> FilterChip(days == d, { days = d }, label = { Text("$d days") }, shape = RoundedCornerShape(50)) }
        }
        Checklist()
        Button(onClick = { confirming = true }, enabled = FocusGuardService.isEnabled(context), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Default.Lock, null); Text("  Lock for $days days")
        }
    }
    if (confirming) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { confirming = false }, icon = { Icon(Icons.Default.Lock, null) }, title = { Text("Lock until ${Commitment.dateText(System.currentTimeMillis() + days * 86_400_000L)}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No turning off, no shortening. Type “I commit”.", textAlign = TextAlign.Start)
                    OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(enabled = typed.trim().equals("I commit", ignoreCase = true), onClick = {
                confirming = false
                val i = VpnService.prepare(context)
                if (i != null) vpn.launch(i) else lockNow()
            }) { Text("Lock") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Not now") } })
    }
    // Night times: pick the start, then the end.
    var nightStep by remember { mutableIntStateOf(0) }
    if (pickNight) com.raunak.daytimeline.trackers.TimeDialog(if (nightStep == 0) c.nightStart else c.nightEnd, { pickNight = false; nightStep = 0 }) { m ->
        if (nightStep == 0) { c = c.copy(nightStart = m); nightStep = 1 } else { c = c.copy(nightEnd = m); nightStep = 0; pickNight = false }
    }
}

/** What makes the lock hard to get around; each opens the right settings page. */
@Composable
private fun Checklist() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2000); tick++ } }
    val dpm = remember { context.getSystemService(DevicePolicyManager::class.java) }
    val admin = remember { ComponentName(context, com.raunak.daytimeline.protection.DayTimelineDeviceAdminReceiver::class.java) }
    val items = remember(tick) {
        listOf(
            Triple("Blocking service", FocusGuardService.isEnabled(context)) { Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS) },
            Triple("Uninstall protection", dpm.isAdminActive(admin)) { Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin) },
            Triple("Always-on VPN", false) { Intent(android.provider.Settings.ACTION_VPN_SETTINGS) },
            Triple("Private DNS off", false) { Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS) }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Make it airtight", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        items.forEach { (label, done, intent) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = !done) { runCatching { context.startActivity(intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(if (done) Chronora.colors.good else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White)
                }
                Text("  $label", Modifier.weight(1f))
                if (!done) Icon(Icons.Default.ChevronRight, null, tint = Chronora.muted)
            }
        }
    }
}
