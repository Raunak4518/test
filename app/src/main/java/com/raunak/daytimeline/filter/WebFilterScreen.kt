package com.raunak.daytimeline.filter

import com.raunak.daytimeline.ui.*

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
    var message by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(WebFilterVpnService.running) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { while (true) { running = WebFilterVpnService.running; now = System.currentTimeMillis(); delay(1000) } }

    fun commit(next: WebFilterConfig) {
        val old = store.config
        if (FilterLock.isLoosening(old, next) && !FilterLock.canLoosen(old, System.currentTimeMillis())) {
            message = "Protection is locked. Request an unlock and wait ${old.lockDelayMinutes} min to loosen it."
            return
        }
        store.config = next
        config = next
        message = ""
        if (next.enabled) WebFilterVpnService.start(context, reload = true) else WebFilterVpnService.stop(context)
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) commit(config.copy(enabled = true)) else message = "VPN permission is needed for the filter to see DNS lookups."
    }

    fun turnOn() {
        val intent = VpnService.prepare(context)
        if (intent != null) consent.launch(intent) else commit(config.copy(enabled = true))
    }

    val fileImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val domains = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { DomainFilter.parseList(it.readText()) } }.getOrNull()
            }
            if (domains.isNullOrEmpty()) { message = "No domains found in that file"; return@launch }
            withContext(Dispatchers.IO) { store.addImported(domains.take(MAX_IMPORT).toSet(), uri.lastPathSegment ?: "file") }
            message = "Imported ${domains.size} domains"
            if (config.enabled) WebFilterVpnService.start(context, reload = true)
            config = store.config
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Web filter & firewall", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (running) "Active · ${store.blockedToday()} blocked today · ${store.blockedTotal()} total" else "Off", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(config.enabled && running, { on -> if (on) turnOn() else commit(config.copy(enabled = false)) })
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            } }
        }
        item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Make it hard to bypass", fontWeight = FontWeight.Bold)
                Text("1. Set Private DNS to Off or Automatic (a custom Private DNS hostname skips the filter).", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Open network settings") }
                Text("2. In VPN settings, tap ⚙ next to Chronora and turn on Always-on VPN and Block connections without VPN.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Open VPN settings") }
                Text("3. Set a lock delay below so switching off takes time, and enable Chronora Protection (device admin) to resist uninstalling.", style = MaterialTheme.typography.bodySmall)
            } }
        }
        item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Block categories", fontWeight = FontWeight.Bold)
                FilterCategory.values().forEach { c ->
                    SwitchRow(c.label, c in config.categories) { on -> commit(config.copy(categories = if (on) config.categories + c else config.categories - c)) }
                }
                HorizontalDivider()
                SwitchRow("Adult keyword detection (catches unlisted sites)", config.keywordBlocking) { commit(config.copy(keywordBlocking = it)) }
                SwitchRow("Force SafeSearch (Google, Bing, DuckDuckGo, Yandex)", config.safeSearch) { commit(config.copy(safeSearch = it)) }
                SwitchRow("YouTube Restricted Mode", config.youtubeRestricted) { commit(config.copy(youtubeRestricted = it)) }
                SwitchRow("Block DNS-over-HTTPS, proxies and VPN sites", config.blockBypass) { commit(config.copy(blockBypass = it)) }
            } }
        }
        item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Upstream DNS", fontWeight = FontWeight.Bold)
                UpstreamDns.values().forEach { u ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(config.upstream == u, { commit(config.copy(upstream = u)) })
                        Column { Text(u.label, style = MaterialTheme.typography.bodyMedium); Text("${u.primary} · ${u.secondary}", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            } }
        }
        item {
            DomainListCard("Always block", "Your own blocked domains (subdomains included)", config.customBlocked,
                onAdd = { d -> commit(config.copy(customBlocked = config.customBlocked + d)) },
                onRemove = { d -> commit(config.copy(customBlocked = config.customBlocked - d)) })
        }
        item {
            DomainListCard("Always allow", "Never blocked, even if a list or keyword matches", config.allowed,
                onAdd = { d -> commit(config.copy(allowed = config.allowed + d)) },
                onRemove = { d -> commit(config.copy(allowed = config.allowed - d)) })
        }
        item {
            var busy by remember { mutableStateOf(false) }
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bigger blocklists", fontWeight = FontWeight.Bold)
                Text("Imported: ${store.importedCount()} domains" + if (config.importedSources.isNotEmpty()) " from ${config.importedSources.size} source(s)" else "", style = MaterialTheme.typography.bodySmall)
                config.listSources.forEach { src ->
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true; message = "Downloading ${src.label}…"
                        scope.launch {
                            val domains = withContext(Dispatchers.IO) { runCatching { download(src.url) }.getOrNull() }
                            if (domains.isNullOrEmpty()) message = "Download failed — check the connection"
                            else {
                                withContext(Dispatchers.IO) { store.addImported(domains.take(MAX_IMPORT).toSet(), src.label) }
                                message = "Added ${domains.size} domains from ${src.label}"
                                config = store.config
                                if (config.enabled) WebFilterVpnService.start(context, reload = true)
                            }
                            busy = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(src.label) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { fileImport.launch(arrayOf("text/*", "application/octet-stream", "*/*")) }) { Text("Import file") }
                    TextButton(onClick = {
                        val old = store.config
                        if (old.lockDelayMinutes > 0 && !FilterLock.canLoosen(old, System.currentTimeMillis())) { message = "Locked: request an unlock first"; return@TextButton }
                        store.clearImported(); config = store.config
                        if (config.enabled) WebFilterVpnService.start(context, reload = true)
                    }) { Text("Clear imported") }
                }
            } }
        }
        item { ListOverridesCard(config, store) { commit(it) } }
        item { AppFirewallCard(config) { rules -> commit(config.copy(appRules = rules)) } }
        item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Commitment lock", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 30, 120, 1440).forEach { m ->
                        FilterChip(config.lockDelayMinutes == m, { commit(config.copy(lockDelayMinutes = m)) }, label = { Text(if (m == 0) "Off" else if (m >= 60) "${m / 60}h" else "${m}m") })
                    }
                }
                if (config.lockDelayMinutes > 0) {
                    val at = config.pendingUnlockAt
                    when {
                        FilterLock.canLoosen(config, now) -> Text("Unlocked for ${((at + 10 * 60_000L - now) / 60_000L).coerceAtLeast(0)} more minutes — make your changes now.", color = MaterialTheme.colorScheme.primary)
                        at > now -> Text("Unlocks in ${formatWait(at - now)}")
                        else -> OutlinedButton(onClick = { commit(FilterLock.requestUnlock(config, System.currentTimeMillis())) }) { Text("Request unlock") }
                    }
                }
            } }
        }
        item {
            var test by remember { mutableStateOf("") }
            val verdict = remember(test, config) { if (test.contains('.')) store.buildFilter(config).decide(test) else null }
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Test a website", fontWeight = FontWeight.Bold)
                OutlinedTextField(test, { test = it.trim() }, label = { Text("example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                verdict?.let {
                    Text(
                        when (it) {
                            is FilterVerdict.Block -> "Blocked — ${it.reason}"
                            is FilterVerdict.Rewrite -> "Allowed in safe mode via ${it.target}"
                            FilterVerdict.Allow -> "Allowed by Chronora" + if (config.upstream.name.contains("FAMILY")) " (the family DNS may still block it)" else ""
                        },
                        color = if (it is FilterVerdict.Block) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            } }
        }
        item { FilterInsights(store, running, now) }
        item { BlockLogCard(store, config, running, now) { commit(it) } }
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
    SectionCard("Today", "Lookups seen by the filter since midnight") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Lookups", "$queries", Modifier.weight(1f))
            Stat("Blocked", "$blocked", Modifier.weight(1f), color = Chronora.colors.bad)
            Stat("Blocked share", if (queries > 0) "${100 * blocked / queries}%" else "—", Modifier.weight(1f))
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
    var query by remember { mutableStateOf("") }
    var cleared by remember { mutableIntStateOf(0) }
    val log = remember(running, now / 10_000, cleared) { store.log() }
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }
    val shown = log.filter { query.isBlank() || it.domain.contains(query, true) || it.app.contains(query, true) || it.reason.contains(query, true) }.take(80)
    SectionCard("Blocked log", "${log.size} recent · tap a site to allow it", action = { TextButton(onClick = { store.clearLog(); cleared++ }) { Text("Clear") } }) {
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search site, app or reason") }, modifier = Modifier.fillMaxWidth())
        if (shown.isEmpty()) Text(if (log.isEmpty()) "Nothing blocked yet" else "No matches", style = MaterialTheme.typography.bodySmall)
        shown.forEach { e ->
            var menu by remember(e.time, e.domain) { mutableStateOf(false) }
            Box {
                Column(Modifier.fillMaxWidth().clickable { menu = true }.padding(vertical = 4.dp)) {
                    Text(e.domain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${fmt.format(Date(e.time))} · ${e.reason}" + if (e.app.isNotBlank()) " · ${e.app.substringAfterLast('.')}" else "", style = MaterialTheme.typography.bodySmall)
                }
                DropdownMenu(menu, { menu = false }) {
                    val base = e.domain.removePrefix("www.")
                    if (base !in config.allowed) DropdownMenuItem(text = { Text("Always allow $base") }, onClick = { menu = false; commit(config.copy(allowed = config.allowed + base)) })
                    DropdownMenuItem(text = { Text("Keep blocking") }, onClick = { menu = false })
                }
            }
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
private fun DomainListCard(title: String, subtitle: String, domains: Set<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
        domains.sorted().forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d, Modifier.weight(1f))
                IconButton(onClick = { onRemove(d) }) { Icon(Icons.Default.Close, "Remove") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, label = { Text("domain.com") }, singleLine = true, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val d = DomainFilter.parseList(input.removePrefix("https://").removePrefix("http://").substringBefore('/')).firstOrNull()
                if (d != null) { onAdd(d.removePrefix("www.")); input = "" }
            }) { Icon(Icons.Default.Add, "Add") }
        }
    } }
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
    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("App firewall", fontWeight = FontWeight.Bold)
        Text(
            if (Build.VERSION.SDK_INT >= 29) "Cut an app off from the internet entirely, or allow it only on Wi-Fi or only on mobile data. Apps can't look up any server while blocked."
            else "Per-app rules need Android 10 or newer.",
            style = MaterialTheme.typography.bodySmall
        )
        val ruled = apps.filter { config.appRules[it.first]?.let { r -> r != AppRule.ALLOW } == true }
        ruled.forEach { (pkg, label) -> AppRuleRow(label, config.appRules[pkg] ?: AppRule.ALLOW) { r -> onChange(if (r == AppRule.ALLOW) config.appRules - pkg else config.appRules + (pkg to r)) } }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide app list" else "Add rules for apps (${apps.size})") }
        if (expanded) {
            OutlinedTextField(query, { query = it }, label = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            apps.filter { query.isBlank() || it.second.contains(query, true) }.take(80).forEach { (pkg, label) ->
                AppRuleRow(label, config.appRules[pkg] ?: AppRule.ALLOW) { r -> onChange(if (r == AppRule.ALLOW) config.appRules - pkg else config.appRules + (pkg to r)) }
            }
        }
    } }
}

@Composable
private fun AppRuleRow(label: String, rule: AppRule, onRule: (AppRule) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
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
    Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Edit the built-in lists", fontWeight = FontWeight.Bold)
        FilterCategory.values().forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${c.label} · ${store.effectiveCategory(c, config).size} sites", Modifier.weight(1f))
                TextButton(onClick = { editing = c.name }) { Text("Edit") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Adult keywords · ${store.effectiveKeywords(config).size}", Modifier.weight(1f)); TextButton(onClick = { editing = "keywords" }) { Text("Edit") } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Bypass services · ${store.effectiveBypass(config).size}", Modifier.weight(1f)); TextButton(onClick = { editing = "bypass" }) { Text("Edit") } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Download sources · ${config.listSources.size}", Modifier.weight(1f)); TextButton(onClick = { editing = "sources" }) { Text("Edit") } }
    } }
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
