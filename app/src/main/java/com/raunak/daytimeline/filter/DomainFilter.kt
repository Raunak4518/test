package com.raunak.daytimeline.filter

enum class FilterCategory(val label: String, val asset: String, val defaultOn: Boolean) {
    ADULT("Adult & porn", "filter/adult.txt", true),
    GAMBLING("Gambling & betting", "filter/gambling.txt", true),
    DATING("Dating & hookup", "filter/dating.txt", false),
    SOCIAL("Social media", "filter/social.txt", false),
    VIDEO("Video & streaming", "filter/video.txt", false),
    ADS("Ads & trackers", "filter/ads.txt", false)
}

/** Upstream resolvers. "Family" resolvers add a second, constantly updated adult/malware filter. */
enum class UpstreamDns(val label: String, val primary: String, val secondary: String) {
    CLOUDFLARE_FAMILY("Cloudflare for Families (blocks adult + malware)", "1.1.1.3", "1.0.0.3"),
    CLEANBROWSING_FAMILY("CleanBrowsing Family (adult, mixed content, proxies)", "185.228.168.168", "185.228.169.168"),
    ADGUARD_FAMILY("AdGuard Family (ads + adult)", "94.140.14.15", "94.140.15.16"),
    CLOUDFLARE_SECURITY("Cloudflare malware blocking", "1.1.1.2", "1.0.0.2"),
    QUAD9("Quad9 (malware blocking)", "9.9.9.9", "149.112.112.112"),
    CLOUDFLARE("Cloudflare (no filtering)", "1.1.1.1", "1.0.0.1"),
    GOOGLE("Google (no filtering)", "8.8.8.8", "8.8.4.4")
}

sealed class FilterVerdict {
    object Allow : FilterVerdict()
    data class Block(val reason: String) : FilterVerdict()
    /** Answer with the addresses of [target] (SafeSearch / restricted mode). */
    data class Rewrite(val target: String) : FilterVerdict()
}

/**
 * Decides what happens to a looked-up domain. Pure and fast: every rule is a hash lookup on the
 * domain and its parents, so a query costs a handful of set probes even with 100k+ entries.
 */
class DomainFilter(
    private val categories: Map<FilterCategory, Set<String>>,
    private val custom: Set<String> = emptySet(),
    private val allow: Set<String> = emptySet(),
    private val keywordBlocking: Boolean = true,
    private val safeSearch: Boolean = true,
    private val youtubeRestricted: Boolean = true,
    private val blockBypass: Boolean = true
) {
    fun decide(rawName: String): FilterVerdict {
        val name = rawName.trim().trimEnd('.').lowercase()
        if (name.isEmpty() || !name.contains('.')) return FilterVerdict.Allow
        if (matches(name, allow)) return FilterVerdict.Allow

        if (safeSearch || youtubeRestricted) rewriteFor(name)?.let { return it }
        if (blockBypass && matches(name, bypass)) return FilterVerdict.Block("DNS/VPN bypass")
        if (matches(name, custom)) return FilterVerdict.Block("Custom blocklist")
        for ((category, set) in categories) if (matches(name, set)) return FilterVerdict.Block(category.label)
        if (keywordBlocking && FilterCategory.ADULT in categories && adultKeyword(name)) return FilterVerdict.Block("Adult keyword")
        return FilterVerdict.Allow
    }

    private fun rewriteFor(name: String): FilterVerdict.Rewrite? {
        if (safeSearch) {
            if (isGoogleSearch(name)) return FilterVerdict.Rewrite("forcesafesearch.google.com")
            if (name == "bing.com" || name == "www.bing.com") return FilterVerdict.Rewrite("strict.bing.com")
            if (name == "duckduckgo.com" || name == "www.duckduckgo.com" || name == "start.duckduckgo.com" || name == "html.duckduckgo.com") return FilterVerdict.Rewrite("safe.duckduckgo.com")
            if (name == "yandex.com" || name == "www.yandex.com" || name == "yandex.ru" || name == "www.yandex.ru") return FilterVerdict.Rewrite("familysearch.yandex.ru")
        }
        if (youtubeRestricted && name in youtubeHosts) return FilterVerdict.Rewrite("restrictmoderate.youtube.com")
        return null
    }

    private fun isGoogleSearch(name: String): Boolean {
        val bare = name.removePrefix("www.")
        if (!bare.startsWith("google.")) return false
        val tld = bare.removePrefix("google.")
        // google.com, google.co.in, google.com.au … but not mail.google.com or other subdomains
        return tld.matches(Regex("[a-z]{2,3}(\\.[a-z]{2})?"))
    }

    companion object {
        /** True when [name] or any parent domain is in [set]. */
        fun matches(name: String, set: Set<String>): Boolean {
            if (set.isEmpty()) return false
            var n = name
            while (true) {
                if (n in set) return true
                val dot = n.indexOf('.')
                if (dot < 0 || dot == n.lastIndexOf('.')) return false
                n = n.substring(dot + 1)
            }
        }

        private val youtubeHosts = setOf("www.youtube.com", "m.youtube.com", "youtube.com", "youtubei.googleapis.com", "youtube.googleapis.com", "www.youtube-nocookie.com")

        /** DNS-over-HTTPS/TLS resolvers, public proxies and VPNs that would bypass the filter. */
        val bypass = setOf(
            "dns.google", "dns.google.com", "8888.google", "cloudflare-dns.com", "mozilla.cloudflare-dns.com", "one.one.one.one", "1dot1dot1dot1.cloudflare-dns.com",
            "dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net", "doh.opendns.com", "doh.familyshield.opendns.com",
            "dns.adguard.com", "dns.adguard-dns.com", "dns-unfiltered.adguard.com", "unfiltered.adguard-dns.com", "doh.cleanbrowsing.org",
            "dns.nextdns.io", "doh.mullvad.net", "dns.mullvad.net", "doh.dns.sb", "dns.twnic.tw", "doh.libredns.gr", "dns.controld.com", "freedns.controld.com",
            "chrome.cloudflare-dns.com", "doh.xfinity.com", "dns.alidns.com", "doh.pub", "dns.digitale-gesellschaft.ch", "use-application-dns.net",
            "hide.me", "hidemyass.com", "proxysite.com", "croxyproxy.com", "kproxy.com", "hidester.com", "4everproxy.com", "vpnbook.com",
            "protonvpn.com", "windscribe.com", "hotspotshield.com", "tunnelbear.com", "psiphon.ca", "psiphon3.com", "ultrasurf.us", "torproject.org"
        )

        private val adultWords = listOf("porn", "xxx", "xvideo", "xnxx", "hentai", "nsfw", "camgirl", "camwhore", "sexcam", "livesex", "sexchat", "nude", "nudes", "milf", "bdsm", "fetish", "escort", "onlyfan", "erotic", "boobs", "blowjob", "pussy", "cumshot", "gangbang", "threesome", "stripchat", "chaturbat", "brazzers", "redtube", "youporn", "spankbang", "rule34", "jav")
        private val adultTokenExact = setOf("sex", "sexy", "xxx", "porno", "jav", "nsfw", "18plus")
        private val safeContaining = listOf("essex", "sussex", "middlesex", "wessex", "sexton", "sextant", "unisex", "javascript", "java", "javelin", "javier", "escortcar", "adultedu", "adulteducation", "scunthorpe", "therapist", "analytics", "cocktail", "shitake", "document")

        /** Heuristic for adult domains missing from lists, avoiding common false positives. */
        fun adultKeyword(name: String): Boolean {
            val labels = name.split('.')
            val host = labels.dropLast(1).joinToString(".") // ignore the TLD
            val cleaned = safeContaining.fold(host) { acc, safe -> acc.replace(safe, "") }
            if (adultWords.any { w -> w.length >= 4 && cleaned.contains(w) }) return true
            val tokens = cleaned.split('.', '-', '_').filter { it.isNotBlank() }
            if (tokens.any { it in adultTokenExact }) return true
            val tld = labels.last()
            return tld in setOf("xxx", "porn", "adult", "sex")
        }

        /** Parses hosts files ("0.0.0.0 example.com"), plain lists and AdBlock "||example.com^" rules. */
        fun parseList(text: String): Set<String> {
            val out = HashSet<String>()
            text.lineSequence().forEach { raw ->
                var line = raw.substringBefore('#').substringBefore('!').trim()
                if (line.isEmpty() || line.startsWith("[")) return@forEach
                if (line.startsWith("||")) line = line.removePrefix("||").substringBefore('^').substringBefore('/')
                val parts = line.split(Regex("\\s+"))
                val host = (if (parts.size >= 2 && (parts[0].contains(':') || parts[0].count { it == '.' } == 3 && parts[0].all { it.isDigit() || it == '.' })) parts[1] else parts[0])
                    .lowercase().trimEnd('.').removePrefix("*.")
                if (host.contains('.') && host !in setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "0.0.0.0") &&
                    host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) out += host
            }
            return out
        }
    }
}
