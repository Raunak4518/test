package com.raunak.daytimeline.filter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebFilterTest {
    private fun queryPacket(name: String, type: Int = DnsPacket.TYPE_A, id: Int = 0x1234): ByteArray {
        val dns = DnsPacket.buildQuery(id, name, type)
        val p = ByteArray(28 + dns.size)
        p[0] = 0x45
        p[2] = (p.size ushr 8).toByte(); p[3] = p.size.toByte()
        p[9] = 17
        byteArrayOf(10, 111, 222.toByte(), 2).copyInto(p, 12)
        byteArrayOf(10, 111, 222.toByte(), 1).copyInto(p, 16)
        p[20] = (40000 ushr 8).toByte(); p[21] = 40000.toByte()
        p[23] = 53
        val udp = 8 + dns.size
        p[24] = (udp ushr 8).toByte(); p[25] = udp.toByte()
        dns.copyInto(p, 28)
        return p
    }

    @Test
    fun parses_query_from_ip_packet() {
        val q = DnsPacket.parse(queryPacket("WWW.Example.com"))!!
        assertThat(q.name).isEqualTo("www.example.com")
        assertThat(q.type).isEqualTo(DnsPacket.TYPE_A)
        assertThat(q.id).isEqualTo(0x1234)
        assertThat(q.sourcePort).isEqualTo(40000)
        assertThat(DnsPacket.parse(queryPacket("x.com").also { it[9] = 6 })).isNull()
    }

    @Test
    fun nxdomain_and_a_answers_round_trip() {
        val q = DnsPacket.parse(queryPacket("blocked.com"))!!
        val nx = DnsPacket.answer(q, DnsPacket.RCODE_NXDOMAIN)
        assertThat(nx[3].toInt() and 0x0F).isEqualTo(3)
        assertThat(nx[0]).isEqualTo(0x12.toByte())
        val ok = DnsPacket.answer(q, 0, listOf(byteArrayOf(216.toByte(), 239.toByte(), 38, 120)))
        val ips = DnsPacket.aRecords(ok)
        assertThat(ips.map { DnsPacket.ip(it) }).containsExactly("216.239.38.120")

        val packet = DnsPacket.wrapResponse(q, ok)
        assertThat(DnsPacket.ipChecksum(packet)).isEqualTo(((packet[10].toInt() and 0xFF) shl 8) or (packet[11].toInt() and 0xFF))
        // Reply goes back from the virtual resolver to the app's port
        assertThat(packet.copyOfRange(12, 16)).isEqualTo(q.destIp)
        assertThat(((packet[22].toInt() and 0xFF) shl 8) or (packet[23].toInt() and 0xFF)).isEqualTo(40000)
    }

    private val filter = DomainFilter(
        categories = mapOf(FilterCategory.ADULT to setOf("pornhub.com"), FilterCategory.GAMBLING to setOf("bet365.com")),
        custom = setOf("reddit.com"),
        allow = setOf("safe.reddit.com")
    )

    @Test
    fun blocks_listed_domains_and_subdomains() {
        assertThat(filter.decide("www.pornhub.com")).isInstanceOf(FilterVerdict.Block::class.java)
        assertThat(filter.decide("bet365.com")).isEqualTo(FilterVerdict.Block("Gambling & betting"))
        assertThat(filter.decide("old.reddit.com")).isEqualTo(FilterVerdict.Block("Custom blocklist"))
        assertThat(filter.decide("safe.reddit.com")).isEqualTo(FilterVerdict.Allow)
        assertThat(filter.decide("wikipedia.org")).isEqualTo(FilterVerdict.Allow)
        assertThat(filter.decide("dns.google")).isEqualTo(FilterVerdict.Block("DNS/VPN bypass"))
    }

    @Test
    fun keyword_heuristic_avoids_common_false_positives() {
        assertThat(filter.decide("free-porn-videos.net")).isEqualTo(FilterVerdict.Block("Adult keyword"))
        assertThat(filter.decide("hot.sex-cams.org")).isEqualTo(FilterVerdict.Block("Adult keyword"))
        assertThat(filter.decide("anything.xxx")).isEqualTo(FilterVerdict.Block("Adult keyword"))
        listOf("essex.ac.uk", "sussex.gov.uk", "javascript.info", "analytics.google.com", "unisex-clothing.com", "middlesex.edu").forEach {
            assertThat(filter.decide(it)).isEqualTo(FilterVerdict.Allow)
        }
    }

    @Test
    fun safe_search_rewrites() {
        assertThat(filter.decide("www.google.com")).isEqualTo(FilterVerdict.Rewrite("forcesafesearch.google.com"))
        assertThat(filter.decide("google.co.in")).isEqualTo(FilterVerdict.Rewrite("forcesafesearch.google.com"))
        assertThat(filter.decide("mail.google.com")).isEqualTo(FilterVerdict.Allow)
        assertThat(filter.decide("www.bing.com")).isEqualTo(FilterVerdict.Rewrite("strict.bing.com"))
        assertThat(filter.decide("m.youtube.com")).isEqualTo(FilterVerdict.Rewrite("restrictmoderate.youtube.com"))
    }

    @Test
    fun parses_hosts_plain_and_adblock_lists() {
        val text = """
            # comment
            0.0.0.0 bad.example
            127.0.0.1 localhost
            ::1 ip6-evil.example
            plain.example.org
            ||ads.example.net^
            ! adblock comment
            not a domain
        """.trimIndent()
        assertThat(DomainFilter.parseList(text)).containsExactly("bad.example", "ip6-evil.example", "plain.example.org", "ads.example.net")
    }

    @Test
    fun lock_blocks_loosening_until_wait_passes() {
        val locked = WebFilterConfig(enabled = true, lockDelayMinutes = 30)
        assertThat(FilterLock.isLoosening(locked, locked.copy(enabled = false))).isTrue()
        assertThat(FilterLock.isLoosening(locked, locked.copy(customBlocked = setOf("x.com")))).isFalse()
        assertThat(FilterLock.canLoosen(locked, 1_000)).isFalse()
        val requested = FilterLock.requestUnlock(locked, 1_000)
        assertThat(FilterLock.canLoosen(requested, 1_000 + 29 * 60_000L)).isFalse()
        assertThat(FilterLock.canLoosen(requested, 1_000 + 31 * 60_000L)).isTrue()
        assertThat(FilterLock.canLoosen(requested, 1_000 + 45 * 60_000L)).isFalse()
        assertThat(FilterLock.appBlocked(AppRule.BLOCK_MOBILE, onWifi = false)).isTrue()
        assertThat(FilterLock.appBlocked(AppRule.BLOCK_MOBILE, onWifi = true)).isFalse()
    }
}
