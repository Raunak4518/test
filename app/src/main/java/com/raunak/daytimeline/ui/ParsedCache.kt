package com.raunak.daytimeline.ui

import java.util.concurrent.ConcurrentHashMap

/**
 * Stores re-read their JSON settings on every access, and the blocking service reads them on every
 * accessibility event. This remembers the last parse per key and only parses again when the stored
 * text changes, so frequent reads cost a string comparison instead of a full JSON parse.
 */
object ParsedCache {
    private val map = ConcurrentHashMap<String, Pair<String, Any?>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, raw: String?, parse: (String) -> T?): T? {
        if (raw == null) return null
        map[key]?.let { (r, v) -> if (r === raw || r == raw) return v as T? }
        val v = parse(raw)
        map[key] = raw to v
        return v
    }
}
