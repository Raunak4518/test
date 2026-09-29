package com.raunak.daytimeline.filter

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

enum class AppRule(val label: String) { ALLOW("Allowed"), BLOCK_ALL("No internet"), BLOCK_MOBILE("Wi-Fi only"), BLOCK_WIFI("Mobile data only") }

data class WebFilterConfig(
    val enabled: Boolean = false,
    val categories: Set<FilterCategory> = FilterCategory.values().filter { it.defaultOn }.toSet(),
    val keywordBlocking: Boolean = true,
    val safeSearch: Boolean = true,
    val youtubeRestricted: Boolean = true,
    val blockBypass: Boolean = true,
    val upstream: UpstreamDns = UpstreamDns.CLOUDFLARE_FAMILY,
    val customBlocked: Set<String> = emptySet(),
    val allowed: Set<String> = emptySet(),
    /** Per-app firewall rules keyed by package name. */
    val appRules: Map<String, AppRule> = emptyMap(),
    val startOnBoot: Boolean = true,
    /** Commitment lock: turning protection down requires waiting this many minutes. */
    val lockDelayMinutes: Int = 0,
    val pendingUnlockAt: Long = 0,
    val importedSources: List<String> = emptyList(),
    /** Your changes to the bundled lists, keyed by category name. */
    val categoryAdded: Map<String, Set<String>> = emptyMap(),
    val categoryRemoved: Map<String, Set<String>> = emptyMap(),
    val keywordsAdded: Set<String> = emptySet(),
    val keywordsRemoved: Set<String> = emptySet(),
    val bypassAdded: Set<String> = emptySet(),
    val bypassRemoved: Set<String> = emptySet(),
    /** Download sources shown under "Bigger blocklists" (label to URL). */
    val listSources: List<ListSourceCfg> = defaultListSources
)

data class ListSourceCfg(val label: String, val url: String)

val defaultListSources = listOf(
    ListSourceCfg("Adult sites (StevenBlack porn-only)", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"),
    ListSourceCfg("Gambling (StevenBlack gambling-only)", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling-only/hosts"),
    ListSourceCfg("Social media (StevenBlack social-only)", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social-only/hosts"),
    ListSourceCfg("Ads + malware (StevenBlack unified)", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
)

data class BlockLogEntry(val time: Long, val domain: String, val reason: String, val app: String)

/** Local storage for the web filter; imported lists live in a plain file under app storage. */
class WebFilterStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("chronora_web_filter", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val importedFile = File(appContext.filesDir, "filter_imported.txt")

    var config: WebFilterConfig
        get() = try { prefs.getString("config", null)?.let { gson.fromJson(it, WebFilterConfig::class.java) }?.normalized() ?: WebFilterConfig() } catch (_: Exception) { WebFilterConfig() }
        set(v) = prefs.edit().putString("config", gson.toJson(v)).apply()

    fun update(transform: (WebFilterConfig) -> WebFilterConfig) { config = transform(config) }

    /** Gson leaves missing collections null when reading older JSON; fill them in. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun WebFilterConfig.normalized() = copy(
        categories = if (categories == null) WebFilterConfig().categories else categories,
        customBlocked = customBlocked ?: emptySet(),
        allowed = allowed ?: emptySet(),
        appRules = appRules ?: emptyMap(),
        importedSources = importedSources ?: emptyList(),
        upstream = upstream ?: UpstreamDns.CLOUDFLARE_FAMILY,
        categoryAdded = categoryAdded ?: emptyMap(),
        categoryRemoved = categoryRemoved ?: emptyMap(),
        keywordsAdded = keywordsAdded ?: emptySet(),
        keywordsRemoved = keywordsRemoved ?: emptySet(),
        bypassAdded = bypassAdded ?: emptySet(),
        bypassRemoved = bypassRemoved ?: emptySet(),
        listSources = listSources ?: defaultListSources
    )

    fun imported(): Set<String> = runCatching { DomainFilter.parseList(importedFile.readText()) }.getOrDefault(emptySet())
    fun importedCount(): Int = runCatching { importedFile.useLines { l -> l.count { it.isNotBlank() } } }.getOrDefault(0)

    fun addImported(domains: Set<String>, source: String) {
        val merged = imported() + domains
        importedFile.writeText(merged.joinToString("\n"))
        update { it.copy(importedSources = (it.importedSources + source).distinct()) }
    }

    fun clearImported() { importedFile.delete(); update { it.copy(importedSources = emptyList()) } }

    fun categorySet(category: FilterCategory): Set<String> =
        runCatching { appContext.assets.open(category.asset).bufferedReader().use { DomainFilter.parseList(it.readText()) } }.getOrDefault(emptySet())

    /** Bundled list for [category] with your additions and removals applied. */
    fun effectiveCategory(category: FilterCategory, c: WebFilterConfig = config): Set<String> =
        categorySet(category) - (c.categoryRemoved[category.name] ?: emptySet()) + (c.categoryAdded[category.name] ?: emptySet())

    fun effectiveKeywords(c: WebFilterConfig = config): List<String> =
        (DomainFilter.defaultAdultWords - c.keywordsRemoved + c.keywordsAdded).distinct()

    fun effectiveBypass(c: WebFilterConfig = config): Set<String> = DomainFilter.bypass - c.bypassRemoved + c.bypassAdded

    fun buildFilter(c: WebFilterConfig = config) = DomainFilter(
        categories = c.categories.associateWith { effectiveCategory(it, c) },
        keywords = effectiveKeywords(c),
        bypassSet = effectiveBypass(c),
        custom = c.customBlocked + imported(),
        allow = c.allowed,
        keywordBlocking = c.keywordBlocking,
        safeSearch = c.safeSearch,
        youtubeRestricted = c.youtubeRestricted,
        blockBypass = c.blockBypass
    )

    fun log(): List<BlockLogEntry> = try {
        prefs.getString("log", null)?.let { gson.fromJson<List<BlockLogEntry>>(it, object : TypeToken<List<BlockLogEntry>>() {}.type) } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun appendLog(entries: List<BlockLogEntry>) {
        if (entries.isEmpty()) return
        prefs.edit().putString("log", gson.toJson((entries.reversed() + log()).take(300))).apply()
        val today = java.time.LocalDate.now().toString()
        val count = if (prefs.getString("count_date", "") == today) prefs.getInt("count", 0) else 0
        prefs.edit().putString("count_date", today).putInt("count", count + entries.size).putLong("total", prefs.getLong("total", 0) + entries.size).apply()
    }

    /** Adds lookups counted by the filter; per-day totals, reset each day. */
    fun addQueryStats(count: Int, perApp: Map<String, Int>) {
        val today = java.time.LocalDate.now().toString()
        val same = prefs.getString("q_date", "") == today
        val apps = (if (same) appQueriesToday() else emptyMap()).toMutableMap()
        perApp.forEach { (k, v) -> apps[k] = (apps[k] ?: 0) + v }
        prefs.edit().putString("q_date", today).putInt("q_count", (if (same) prefs.getInt("q_count", 0) else 0) + count).putString("q_apps", gson.toJson(apps)).apply()
    }

    fun queriesToday(): Int = if (prefs.getString("q_date", "") == java.time.LocalDate.now().toString()) prefs.getInt("q_count", 0) else 0

    fun appQueriesToday(): Map<String, Int> = if (prefs.getString("q_date", "") != java.time.LocalDate.now().toString()) emptyMap() else try {
        prefs.getString("q_apps", null)?.let { gson.fromJson<Map<String, Int>>(it, object : TypeToken<Map<String, Int>>() {}.type) } ?: emptyMap()
    } catch (_: Exception) { emptyMap() }

    fun blockedToday(): Int = if (prefs.getString("count_date", "") == java.time.LocalDate.now().toString()) prefs.getInt("count", 0) else 0
    fun blockedTotal(): Long = prefs.getLong("total", 0)
    fun clearLog() = prefs.edit().remove("log").apply()
}

/** Rules for loosening protection under the commitment lock. */
object FilterLock {
    /** Changes that reduce protection (turning off, removing categories, allowing domains). */
    fun isLoosening(old: WebFilterConfig, new: WebFilterConfig): Boolean =
        (old.enabled && !new.enabled) ||
            !new.categories.containsAll(old.categories) ||
            (old.keywordBlocking && !new.keywordBlocking) ||
            (old.safeSearch && !new.safeSearch) ||
            (old.youtubeRestricted && !new.youtubeRestricted) ||
            (old.blockBypass && !new.blockBypass) ||
            !new.customBlocked.containsAll(old.customBlocked) ||
            !old.allowed.containsAll(new.allowed) ||
            new.lockDelayMinutes < old.lockDelayMinutes ||
            (old.upstream.name.contains("FAMILY") && !new.upstream.name.contains("FAMILY")) ||
            new.categoryRemoved.any { (k, v) -> !(old.categoryRemoved[k] ?: emptySet()).containsAll(v) } ||
            old.categoryAdded.any { (k, v) -> !(new.categoryAdded[k] ?: emptySet()).containsAll(v) } ||
            !old.keywordsRemoved.containsAll(new.keywordsRemoved) || !new.keywordsAdded.containsAll(old.keywordsAdded) ||
            !old.bypassRemoved.containsAll(new.bypassRemoved) || !new.bypassAdded.containsAll(old.bypassAdded)

    /** True when a loosening change may be applied now. */
    fun canLoosen(config: WebFilterConfig, now: Long): Boolean =
        config.lockDelayMinutes == 0 || (config.pendingUnlockAt in 1..now && now - config.pendingUnlockAt < 10 * 60_000L)

    fun requestUnlock(config: WebFilterConfig, now: Long) = config.copy(pendingUnlockAt = now + config.lockDelayMinutes * 60_000L)

    /** Firewall decision for an app's DNS lookup given the current network. */
    fun appBlocked(rule: AppRule?, onWifi: Boolean): Boolean = when (rule) {
        AppRule.BLOCK_ALL -> true
        AppRule.BLOCK_MOBILE -> !onWifi
        AppRule.BLOCK_WIFI -> onWifi
        else -> false
    }
}
