package com.raunak.daytimeline.filter

import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import com.raunak.daytimeline.ui.*
import com.raunak.daytimeline.wellbeing.Expandable

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_IMPORT = 400_000

/** Web filter & firewall: DNS filtering VPN, categories, SafeSearch, per-app rules and a commitment lock. */
@Composable
fun WebFilterScreen() {
    val context = LocalContext.current
    val store = remember { WebFilterStore(context) }
    var config by remember { mutableStateOf(store.config) }
    val confirm = rememberConfirm()
    var running by remember { mutableStateOf(WebFilterVpnService.running) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { while (true) { running = WebFilterVpnService.running; now = System.currentTimeMillis(); delay(1000) } }

    val offGuard = rememberOffGuard()
    fun apply(next: WebFilterConfig) {
        store.config = next
        config = next
        if (next.enabled) WebFilterVpnService.start(context, reload = true) else WebFilterVpnService.stop(context)
    }
    /** Stricter changes apply at once; anything looser waits out the lock and then the turn-off protection. */
    fun commit(next: WebFilterConfig) {
        val old = store.config
        val loosening = FilterLock.isLoosening(old, next)
        if (loosening && !FilterLock.canLoosen(old, System.currentTimeMillis())) {
            Feedback.show(if (old.commitUntil > System.currentTimeMillis()) "Locked: only stricter changes" else "Locked: request an unlock and wait ${old.lockDelayMinutes} min")
            return
        }
        if (loosening) offGuard.ask(if (old.enabled && !next.enabled) "the web filter" else "this protection") { apply(next) } else apply(next)
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) { commit(config.copy(enabled = true)); Feedback.show("🛡️ Web filter is on. You're protected.") } else Feedback.show("The filter needs VPN permission to work")
    }

    fun turnOn() {
        val intent = VpnService.prepare(context)
        if (intent != null) consent.launch(intent) else { commit(config.copy(enabled = true)); Feedback.show("🛡️ Web filter is on. You're protected.") }
    }

    val fileImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val domains = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { DomainFilter.parseList(it.readText()) } }.getOrNull()
            }
            if (domains.isNullOrEmpty()) { Feedback.show("No sites found in that file"); return@launch }
            withContext(Dispatchers.IO) { store.addImported(domains.take(MAX_IMPORT).toSet(), uri.lastPathSegment ?: "file") }
            Feedback.show("Imported ${domains.size} sites")
            if (config.enabled) WebFilterVpnService.start(context, reload = true)
            config = store.config
        }
    }

    val locked = config.commitUntil > now
    ScreenList {
        item {
            ModeHero(on = config.enabled, title = "Web filter", icon = Icons.Default.Shield, onLabel = if (locked) "LOCKED" else "ON",
                status = when {
                    !config.enabled -> "Tap to block harmful sites"
                    !running -> "Starting…"
                    else -> "${store.blockedToday()} blocked today"
                } + if (locked) "\n🔒 Until ${com.raunak.daytimeline.campus.Commitment.dateText(config.commitUntil)}" else "",
                onToggle = { if (config.enabled) commit(config.copy(enabled = false)) else turnOn() })
        }
        item {
            SectionCard("What to block", icon = Icons.Default.Block) {
                FilterCategory.values().forEach { c ->
                    SwitchRow(c.label, c in config.categories) { on -> commit(config.copy(categories = if (on) config.categories + c else config.categories - c)) }
                }
            }
        }
        item {
            SectionCard("Safe browsing", icon = Icons.Default.VerifiedUser) {
                SwitchRow("SafeSearch", config.safeSearch) { commit(config.copy(safeSearch = it)) }
                SwitchRow("YouTube Restricted", config.youtubeRestricted) { commit(config.copy(youtubeRestricted = it)) }
                SwitchRow("Keyword blocking", config.keywordBlocking) { commit(config.copy(keywordBlocking = it)) }
                SwitchRow("Block VPNs and proxies", config.blockBypass) { commit(config.copy(blockBypass = it)) }
            }
        }
        item { SitesCard(config, ::commit) }
        item { FilterInsights(store, running, now) }
        item {
            SectionCard("Advanced", icon = Icons.Default.Tune) {
                Expandable("Make it hard to switch off") {
                    Step(1, "Private DNS: Off or Automatic") { runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    Step(2, "Always-on VPN + block without VPN") { runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    Text("Extra wait before changes", style = MaterialTheme.typography.bodyLarge)
                    val delays = listOf(0, 5, 30, 120, 1440)
                    PillTabs(delays.map { m -> if (m == 0) "None" else if (m >= 60) "${m / 60}h" else "${m}m" }, delays.indexOf(config.lockDelayMinutes).coerceAtLeast(0)) { i -> commit(config.copy(lockDelayMinutes = delays[i])) }
                    if (config.lockDelayMinutes > 0) {
                        val at = config.pendingUnlockAt
                        when {
                            FilterLock.canLoosen(config, now) -> StatusText("Unlocked for ${((at + 10 * 60_000L - now) / 60_000L).coerceAtLeast(0)} min", ok = true)
                            at > now -> StatusText("Unlocks in ${formatWait(at - now)}", ok = null)
                            !locked -> OutlinedButton(onClick = { commit(FilterLock.requestUnlock(config, System.currentTimeMillis())) }) { Text("Request unlock") }
                        }
                    }
                    Text("Turn-off protection", style = MaterialTheme.typography.bodyLarge)
                    com.raunak.daytimeline.wellbeing.OffGuardSettings { what, action -> offGuard.ask(what, action) }
                }
                Expandable("Test a site") {
                    var test by remember { mutableStateOf("") }
                    val verdict = remember(test, config) { if (test.contains('.')) store.buildFilter(config).decide(test) else null }
                    OutlinedTextField(test, { test = it.trim() }, placeholder = { Text("example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
                    verdict?.let { StatusText(when (it) { is FilterVerdict.Block -> "Blocked · ${it.reason}"; is FilterVerdict.Rewrite -> "Allowed in safe mode"; FilterVerdict.Allow -> "Allowed" }, ok = it !is FilterVerdict.Block) }
                }
                Expandable("Blocked log") { BlockLogCard(store, config, running, now) { commit(it) } }
                Expandable("DNS server") {
                    UpstreamDns.values().forEach { u ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { commit(config.copy(upstream = u)) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(config.upstream == u, { commit(config.copy(upstream = u)) })
                            Text(u.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Expandable("Bigger blocklists · ${store.importedCount()} sites") {
                    var busy by remember { mutableStateOf(false) }
                    config.listSources.forEach { src ->
                        OutlinedButton(enabled = !busy, onClick = {
                            busy = true; Feedback.show("Downloading ${src.label}…")
                            scope.launch {
                                val domains = withContext(Dispatchers.IO) { runCatching { download(src.url) }.getOrNull() }
                                if (domains.isNullOrEmpty()) Feedback.show("Download failed. Check the connection.")
                                else {
                                    withContext(Dispatchers.IO) { store.addImported(domains.take(MAX_IMPORT).toSet(), src.label) }
                                    Feedback.show("Added ${domains.size} sites from ${src.label}")
                                    config = store.config
                                    if (config.enabled) WebFilterVpnService.start(context, reload = true)
                                }
                                busy = false
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Text("  ${src.label}") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { fileImport.launch(arrayOf("text/*", "application/octet-stream", "*/*")) }) { Text("Import file") }
                        if (store.importedCount() > 0) TextButton(onClick = {
                            val old = store.config
                            if (!FilterLock.canLoosen(old, System.currentTimeMillis())) Feedback.show("Locked: request an unlock first")
                            else confirm.ask("all imported sites") {
                                store.clearImported(); config = store.config
                                if (config.enabled) WebFilterVpnService.start(context, reload = true)
                            }
                        }) { Text("Clear imported", color = Chronora.colors.bad) }
                    }
                }
                Expandable("Edit the built-in lists") { ListOverridesCard(config, store) { commit(it) } }
                Expandable("App firewall") { AppFirewallCard(config) { rules -> commit(config.copy(appRules = rules)) } }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String, open: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(text, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = open) { Text("Open") }
    }
}

/** The user's own block and allow lists, as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SitesCard(config: WebFilterConfig, commit: (WebFilterConfig) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    val list = if (tab == 0) config.customBlocked else config.allowed
    SectionCard("Your sites", icon = Icons.Default.Language) {
        PillTabs(listOf("Always block", "Always allow"), tab) { tab = it }
        if (list.isEmpty()) Text("None", color = Chronora.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.sorted().forEach { d ->
                TextChip(d) { commit(if (tab == 0) config.copy(customBlocked = config.customBlocked - d) else config.copy(allowed = config.allowed - d)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, placeholder = { Text("site.com") }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small)
            FilledTonalIconButton(onClick = {
                val d = DomainFilter.parseList(input.removePrefix("https://").removePrefix("http://").substringBefore('/')).firstOrNull()?.removePrefix("www.")
                if (d == null) Feedback.show("Type a site like example.com")
                else { commit(if (tab == 0) config.copy(customBlocked = config.customBlocked + d) else config.copy(allowed = config.allowed + d)); input = "" }
            }) { Icon(Icons.Default.Add, "Add site") }
        }
    }
}

/** RethinkDNS-style overview: lookups, blocked share, top blocked sites and the busiest apps. */
@Composable
private fun FilterInsights(store: WebFilterStore, running: Boolean, now: Long) {
    val context = LocalContext.current
    val queries = remember(running, now / 10_000) { store.queriesToday() }
    val blocked = remember(running, now / 10_000) { store.blockedToday() }
    val apps = remember(running, now / 10_000) { store.appQueriesToday() }
    val today = remember(running, now / 10_000) { store.log().filter { java.time.Instant.ofEpochMilli(it.time).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now() } }
    fun label(pkg: String) = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg.substringAfterLast('.'))
    SectionCard("Today", icon = Icons.Default.Insights) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Lookups", "$queries", Modifier.weight(1f))
            StatTile("Blocked", "$blocked", Modifier.weight(1f))
            StatTile("Share", if (queries > 0) "${100 * blocked / queries}%" else "—", Modifier.weight(1f))
        }
        val topDomains = today.groupingBy { it.domain }.eachCount().entries.sortedByDescending { it.value }.take(5)
        if (topDomains.isNotEmpty()) {
            Text("Most blocked", style = MaterialTheme.typography.labelLarge)
            topDomains.forEach { Text("${it.key} ×${it.value}", style = MaterialTheme.typography.bodySmall) }
        }
        val blockedByApp = today.filter { it.app.isNotBlank() }.groupingBy { it.app }.eachCount()
        val topApps = apps.entries.sortedByDescending { it.value }.take(5)
        if (topApps.isNotEmpty()) {
            Text("Busiest apps", style = MaterialTheme.typography.labelLarge)
            topApps.forEach { (pkg, n) -> Text("${label(pkg)} · $n lookups" + (blockedByApp[pkg]?.let { " · $it blocked" } ?: ""), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Searchable log; tap an entry to always allow that site (the commitment lock still applies). */
@Composable
private fun BlockLogCard(store: WebFilterStore, config: WebFilterConfig, running: Boolean, now: Long, commit: (WebFilterConfig) -> Unit) {
    val confirm = com.raunak.daytimeline.ui.rememberConfirm()
    var query by remember { mutableStateOf("") }
    var cleared by remember { mutableIntStateOf(0) }
    val log = remember(running, now / 10_000, cleared) { store.log() }
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }
    val shown = log.filter { query.isBlank() || it.domain.contains(query, true) || it.app.contains(query, true) || it.reason.contains(query, true) }.take(80)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (log.isNotEmpty()) TextButton(onClick = { confirm.ask("the blocked log") { store.clearLog(); cleared++ } }) { Text("Clear log") }
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
        if (shown.isEmpty()) Text(if (log.isEmpty()) "Nothing blocked yet" else "No matches", style = MaterialTheme.typography.bodySmall)
        var limit by remember { mutableIntStateOf(10) }
        var allow by remember { mutableStateOf<String?>(null) }
        shown.take(limit).forEach { e ->
            val base = e.domain.removePrefix("www.")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = base !in config.allowed) { allow = base }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.domain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text("${fmt.format(Date(e.time))} · ${e.reason}" + if (e.app.isNotBlank()) " · ${e.app.substringAfterLast('.')}" else "", style = MaterialTheme.typography.bodySmall, color = Chronora.muted, maxLines = 1)
                }
                if (base in config.allowed) Text("Allowed", style = MaterialTheme.typography.labelSmall, color = Chronora.colors.good)
            }
        }
        if (shown.size > limit) TextButton(onClick = { limit += 20 }) { Text("Show more") }
        allow?.let { d ->
            AlertDialog(onDismissRequest = { allow = null }, title = { Text("Always allow $d?") }, text = { Text("It won't be blocked again.") },
                confirmButton = { TextButton(onClick = { commit(config.copy(allowed = config.allowed + d)); allow = null }) { Text("Allow") } },
                dismissButton = { TextButton(onClick = { allow = null }) { Text("Keep blocking") } })
        }
    }
}

private fun formatWait(ms: Long): String {
    val m = ms / 60_000L
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m ${(ms / 1000) % 60}s"
}

private fun download(url: String): Set<String> {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 60_000
    return conn.inputStream.bufferedReader().use { DomainFilter.parseList(it.readText()) }.also { conn.disconnect() }
}


@Composable
private fun AppFirewallCard(config: WebFilterConfig, onChange: (Map<String, AppRule>) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val apps = remember {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }
            .sortedBy { it.second.lowercase() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (Build.VERSION.SDK_INT >= 29) "Cut an app off the internet, or allow only Wi-Fi or mobile data" else "Needs Android 10 or newer", style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
        val ruled = apps.filter { config.appRules[it.first]?.let { r -> r != AppRule.ALLOW } == true }
        ruled.forEach { (pkg, label) -> AppRuleRow(label, config.appRules[pkg] ?: AppRule.ALLOW) { r -> onChange(if (r == AppRule.ALLOW) config.appRules - pkg else config.appRules + (pkg to r)) } }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide app list" else "Add rules for apps (${apps.size})") }
        if (expanded) {
            OutlinedTextField(query, { query = it }, label = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            apps.filter { query.isBlank() || it.second.contains(query, true) }.take(80).forEach { (pkg, label) ->
                AppRuleRow(label, config.appRules[pkg] ?: AppRule.ALLOW) { r -> onChange(if (r == AppRule.ALLOW) config.appRules - pkg else config.appRules + (pkg to r)) }
            }
        }
    }
}

@Composable
private fun AppRuleRow(label: String, rule: AppRule, onRule: (AppRule) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), maxLines = 1)
        Box {
            AssistChip({ menu = true }, label = { Text(rule.label) })
            DropdownMenu(menu, { menu = false }) {
                AppRule.values().forEach { r -> DropdownMenuItem(text = { Text(r.label) }, onClick = { onRule(r); menu = false }) }
            }
        }
    }
}

/** Lets every bundled list be changed: category domains, adult keywords, bypass list and download sources. */
@Composable
private fun ListOverridesCard(config: WebFilterConfig, store: WebFilterStore, commit: (WebFilterConfig) -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FilterCategory.values().forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${c.label} · ${store.effectiveCategory(c, config).size} sites", Modifier.weight(1f))
                TextButton(onClick = { editing = c.name }) { Text("Edit") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Adult keywords · ${store.effectiveKeywords(config).size}", Modifier.weight(1f)); TextButton(onClick = { editing = "keywords" }) { Text("Edit") } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Bypass services · ${store.effectiveBypass(config).size}", Modifier.weight(1f)); TextButton(onClick = { editing = "bypass" }) { Text("Edit") } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Download sources · ${config.listSources.size}", Modifier.weight(1f)); TextButton(onClick = { editing = "sources" }) { Text("Edit") } }
    }
    when (val e = editing) {
        null -> Unit
        "keywords" -> OverrideDialog("Adult keywords", DomainFilter.defaultAdultWords.toSet(), config.keywordsAdded, config.keywordsRemoved, isValid = { it.length >= 3 && it.all(Char::isLetterOrDigit) },
            onSave = { add, remove -> commit(config.copy(keywordsAdded = add, keywordsRemoved = remove)) }) { editing = null }
        "bypass" -> OverrideDialog("Bypass services", DomainFilter.bypass, config.bypassAdded, config.bypassRemoved, isValid = { it.contains('.') },
            onSave = { add, remove -> commit(config.copy(bypassAdded = add, bypassRemoved = remove)) }) { editing = null }
        "sources" -> SourcesDialog(config.listSources, onSave = { commit(config.copy(listSources = it)) }) { editing = null }
        else -> {
            val c = FilterCategory.valueOf(e)
            val bundled = remember(e) { store.categorySet(c) }
            OverrideDialog(c.label, bundled, config.categoryAdded[e] ?: emptySet(), config.categoryRemoved[e] ?: emptySet(), isValid = { it.contains('.') },
                onSave = { add, remove -> commit(config.copy(categoryAdded = config.categoryAdded + (e to add), categoryRemoved = config.categoryRemoved + (e to remove))) }) { editing = null }
        }
    }
}

@Composable
private fun OverrideDialog(title: String, bundled: Set<String>, added: Set<String>, removed: Set<String>, isValid: (String) -> Boolean, onSave: (Set<String>, Set<String>) -> Unit, close: () -> Unit) {
    var add by remember { mutableStateOf(added) }
    var remove by remember { mutableStateOf(removed) }
    var input by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, { input = it.trim().lowercase() }, label = { Text("Add") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val v = input.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
                        if (isValid(v)) { if (v in bundled) remove = remove - v else add = add + v; input = "" }
                    }) { Icon(Icons.Default.Add, "Add") }
                }
            }
            if (add.isNotEmpty()) item { Text("Added by you", style = MaterialTheme.typography.labelLarge) }
            items(add.sorted()) { v -> Row(verticalAlignment = Alignment.CenterVertically) { Text(v, Modifier.weight(1f)); IconButton(onClick = { add = add - v }) { Icon(Icons.Default.Close, "Remove") } } }
            item {
                Text("Built-in (${bundled.size}) — untick to remove", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(query, { query = it.trim().lowercase() }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            items(bundled.filter { query.isBlank() || it.contains(query) }.sorted().take(300)) { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(v !in remove, { on -> remove = if (on) remove - v else remove + v })
                    Text(v)
                }
            }
        }
    }, confirmButton = { Button(onClick = { onSave(add, remove); close() }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun SourcesDialog(sources: List<ListSourceCfg>, onSave: (List<ListSourceCfg>) -> Unit, close: () -> Unit) {
    var list by remember { mutableStateOf(sources) }
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Download sources") }, text = {
        LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(list) { s -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(s.label); Text(s.url, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                IconButton(onClick = { list = list - s }) { Icon(Icons.Default.Close, "Remove") }
            } }
            item {
                OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it.trim() }, label = { Text("URL of a hosts or domain list") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { if (url.startsWith("https://") && label.isNotBlank()) { list = list + ListSourceCfg(label.trim(), url); label = ""; url = "" } }) { Text("Add source") }
                TextButton(onClick = { list = defaultListSources }) { Text("Restore defaults") }
            }
        }
    }, confirmButton = { Button(onClick = { onSave(list); close() }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
