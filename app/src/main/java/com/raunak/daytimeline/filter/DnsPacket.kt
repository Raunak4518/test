package com.raunak.daytimeline.filter

import java.net.InetAddress

/** A DNS query taken out of an IPv4/UDP packet read from the VPN interface. */
data class DnsQuery(
    val sourceIp: ByteArray,
    val destIp: ByteArray,
    val sourcePort: Int,
    val destPort: Int,
    /** The raw DNS message (UDP payload). */
    val dns: ByteArray,
    val name: String,
    val type: Int
) {
    val id: Int get() = ((dns[0].toInt() and 0xFF) shl 8) or (dns[1].toInt() and 0xFF)
}

/**
 * Minimal, allocation-light IPv4 + UDP + DNS codec for a DNS-only VPN. Only what a filtering
 * resolver needs: read the question, answer it locally, or wrap an upstream answer.
 */
object DnsPacket {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val TYPE_HTTPS = 65
    const val RCODE_NXDOMAIN = 3

    fun parse(packet: ByteArray, length: Int = packet.size): DnsQuery? {
        if (length < 28) return null
        val version = (packet[0].toInt() and 0xF0) shr 4
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (packet[9].toInt() and 0xFF != 17) return null // UDP only
        if (length < ihl + 8 + 12) return null
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = u16(packet, ihl)
        val dstPort = u16(packet, ihl + 2)
        if (dstPort != 53) return null
        val udpLength = u16(packet, ihl + 4)
        val end = minOf(length, ihl + udpLength)
        if (end <= ihl + 8 + 12) return null
        val dns = packet.copyOfRange(ihl + 8, end)
        val (name, next) = readQuestionName(dns, 12) ?: return null
        if (next + 4 > dns.size) return null
        return DnsQuery(srcIp, dstIp, srcPort, dstPort, dns, name, u16(dns, next))
    }

    /** Reads an uncompressed name (questions never use compression). */
    private fun readQuestionName(dns: ByteArray, start: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var i = start
        var guard = 0
        while (i < dns.size && guard++ < 128) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) return sb.toString().lowercase() to (i + 1)
            if (len and 0xC0 != 0 || i + 1 + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..len) sb.append((dns[i + k].toInt() and 0xFF).toChar())
            i += 1 + len
        }
        return null
    }

    /** End offset of the question section (header + first question). */
    private fun questionEnd(dns: ByteArray): Int {
        var i = 12
        while (i < dns.size && dns[i].toInt() != 0) i += 1 + (dns[i].toInt() and 0xFF)
        return i + 1 + 4
    }

    /** A local answer: NXDOMAIN, an empty NOERROR, or A records with the given IPv4 addresses. */
    fun answer(query: DnsQuery, rcode: Int = 0, ipv4: List<ByteArray> = emptyList(), ttl: Int = 300): ByteArray {
        val qEnd = questionEnd(query.dns).coerceAtMost(query.dns.size)
        val answers = if (query.type == TYPE_A && rcode == 0) ipv4 else emptyList()
        val out = ByteArray(qEnd + answers.size * 16)
        System.arraycopy(query.dns, 0, out, 0, qEnd)
        val rd = query.dns[2].toInt() and 0x01
        out[2] = (0x80 or rd).toByte() // QR=1, opcode 0, RD copied
        out[3] = (0x80 or (rcode and 0x0F)).toByte() // RA=1
        put16(out, 4, 1)
        put16(out, 6, answers.size)
        put16(out, 8, 0)
        put16(out, 10, 0)
        var o = qEnd
        for (ip in answers) {
            put16(out, o, 0xC00C) // pointer to the question name
            put16(out, o + 2, TYPE_A)
            put16(out, o + 4, 1)
            out[o + 6] = (ttl ushr 24).toByte(); out[o + 7] = (ttl ushr 16).toByte(); out[o + 8] = (ttl ushr 8).toByte(); out[o + 9] = ttl.toByte()
            put16(out, o + 10, 4)
            System.arraycopy(ip, 0, out, o + 12, 4)
            o += 16
        }
        return out
    }

    /** Extracts IPv4 addresses from the answer section of an upstream response. */
    fun aRecords(dns: ByteArray): List<ByteArray> {
        if (dns.size < 12) return emptyList()
        val qd = u16(dns, 4)
        val an = u16(dns, 6)
        var i = 12
        repeat(qd) { i = skipName(dns, i) + 4 }
        val result = mutableListOf<ByteArray>()
        repeat(an) {
            if (i >= dns.size) return result
            i = skipName(dns, i)
            if (i + 10 > dns.size) return result
            val type = u16(dns, i)
            val rdLen = u16(dns, i + 8)
            val data = i + 10
            if (type == TYPE_A && rdLen == 4 && data + 4 <= dns.size) result += dns.copyOfRange(data, data + 4)
            i = data + rdLen
        }
        return result
    }

    private fun skipName(dns: ByteArray, start: Int): Int {
        var i = start
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) return i + 1
            if (len and 0xC0 == 0xC0) return i + 2
            i += 1 + len
        }
        return i
    }

    /** Builds a DNS query for [name]/[type] reusing [id]; used for SafeSearch rewrites. */
    fun buildQuery(id: Int, name: String, type: Int = TYPE_A): ByteArray {
        val labels = name.trimEnd('.').split('.')
        val out = ByteArray(12 + labels.sumOf { it.length + 1 } + 1 + 4)
        put16(out, 0, id)
        out[2] = 0x01 // RD
        put16(out, 4, 1)
        var o = 12
        for (l in labels) { out[o++] = l.length.toByte(); l.forEach { out[o++] = it.code.toByte() } }
        out[o++] = 0
        put16(out, o, type); put16(out, o + 2, 1)
        return out
    }

    /** Wraps a DNS message into an IPv4/UDP packet travelling back to the querying app. */
    fun wrapResponse(query: DnsQuery, dnsResponse: ByteArray): ByteArray {
        val total = 20 + 8 + dnsResponse.size
        val p = ByteArray(total)
        p[0] = 0x45
        put16(p, 2, total)
        p[8] = 64 // TTL
        p[9] = 17
        System.arraycopy(query.destIp, 0, p, 12, 4)
        System.arraycopy(query.sourceIp, 0, p, 16, 4)
        put16(p, 10, ipChecksum(p))
        put16(p, 20, query.destPort)
        put16(p, 22, query.sourcePort)
        put16(p, 24, 8 + dnsResponse.size)
        put16(p, 26, 0) // UDP checksum is optional over IPv4
        System.arraycopy(dnsResponse, 0, p, 28, dnsResponse.size)
        return p
    }

    fun ipChecksum(p: ByteArray): Int {
        var sum = 0L
        for (i in 0 until 20 step 2) if (i != 10) sum += u16(p, i)
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    fun ip(bytes: ByteArray): String = InetAddress.getByAddress(bytes).hostAddress ?: ""

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
    private fun put16(b: ByteArray, i: Int, v: Int) { b[i] = (v ushr 8).toByte(); b[i + 1] = v.toByte() }
}
