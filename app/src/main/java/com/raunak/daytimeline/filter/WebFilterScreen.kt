package com.raunak.daytimeline.filter

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

private data class ListSource(val label: String, val url: String)

private val onlineLists = listOf(
    ListSource("Adult sites (StevenBlack porn-only)", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"),
    ListSource("Gambling (StevenBlack gambling-only)", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling-only/hosts"),
    ListSource("Social media (StevenBlack social-only)", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social-only/hosts"),
    ListSource("Ads + malware (StevenBlack unified)", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
)

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
                Text("A local VPN that only carries DNS lookups. Blocked sites never resolve, in every app and browser. Nothing is sent to a Chronora server.", style = MaterialTheme.typography.bodySmall)
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
                    SwitchLine(c.label, c in config.categories) { on -> commit(config.copy(categories = if (on) config.categories + c else config.categories - c)) }
                }
                HorizontalDivider()
                SwitchLine("Adult keyword detection (catches unlisted sites)", config.keywordBlocking) { commit(config.copy(keywordBlocking = it)) }
                SwitchLine("Force SafeSearch (Google, Bing, DuckDuckGo, Yandex)", config.safeSearch) { commit(config.copy(safeSearch = it)) }
                SwitchLine("YouTube Restricted Mode", config.youtubeRestricted) { commit(config.copy(youtubeRestricted = it)) }
                SwitchLine("Block DNS-over-HTTPS, proxies and VPN sites", config.blockBypass) { commit(config.copy(blockBypass = it)) }
            } }
        }
        item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Upstream DNS", fontWeight = FontWeight.Bold)
                Text("Family resolvers add a second, constantly updated adult/malware filter on top of Chronora's lists.", style = MaterialTheme.typography.bodySmall)
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
                Text("Download once (needs internet), then filtering stays fully offline. Hosts files, plain lists and AdBlock ||domain^ rules are supported.", style = MaterialTheme.typography.bodySmall)
                onlineLists.forEach { src ->
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
        item { AppFirewallCard(config) { rules -> commit(config.copy(appRules = rules)) } }
        item {
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Commitment lock", fontWeight = FontWeight.Bold)
                Text("Loosening protection (turning off, removing categories, allowing sites, switching to an unfiltered DNS) needs a waiting period.", style = MaterialTheme.typography.bodySmall)
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
        item {
            val log = remember(running, now / 10_000) { store.log().take(60) }
            val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recently blocked", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { store.clearLog() }) { Text("Clear") }
                }
                if (log.isEmpty()) Text("Nothing blocked yet", style = MaterialTheme.typography.bodySmall)
                log.forEach { e -> Text("${fmt.format(Date(e.time))}  ${e.domain} · ${e.reason}" + if (e.app.isNotBlank()) " · ${e.app.substringAfterLast('.')}" else "", style = MaterialTheme.typography.bodySmall) }
            } }
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
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
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
