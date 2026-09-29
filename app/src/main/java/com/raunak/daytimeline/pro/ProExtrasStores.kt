package com.raunak.daytimeline.pro

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raunak.daytimeline.data.PomodoroStateEntity
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Stores completed and abandoned focus sessions that grow the Focus Garden. */
class GardenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_garden", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun sessions(): List<GardenSession> = try {
        prefs.getString("sessions", null)?.let { gson.fromJson<List<GardenSession>>(it, object : TypeToken<List<GardenSession>>() {}.type) } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    private fun add(session: GardenSession) { prefs.edit().putString("sessions", gson.toJson(sessions() + session)).apply() }

    /**
     * Called by every Pomodoro ticker (view model and foreground service). The finished focus phase's
     * end time is used as a key so a session is only recorded once, whichever ticker sees it first.
     */
    @Synchronized
    fun onTransition(before: PomodoroStateEntity, after: PomodoroStateEntity) {
        if (before.phase != "FOCUS" || after.phase == "FOCUS" || after.phase == "IDLE") return
        val key = before.targetEpochMillis
        if (prefs.getLong("last_key", 0) == key) return
        prefs.edit().putLong("last_key", key).apply()
        add(GardenSession(LocalDate.now().toString(), before.focusMinutes, true, before.taskId))
    }

    /** A focus phase reset after at least a minute withers a plant. */
    fun onAbandon(state: PomodoroStateEntity, now: Long = System.currentTimeMillis()) {
        if (state.phase != "FOCUS") return
        val remaining = if (state.running) (state.targetEpochMillis - now) / 1000 else state.remainingSeconds
        val focused = ((state.focusMinutes * 60L - remaining) / 60).toInt()
        if (focused >= 1) add(GardenSession(LocalDate.now().toString(), focused, false, state.taskId))
    }
}

data class PrivateEntry(val id: Long, val date: String, val sealed: String)

/** Journal entries encrypted with an AES-256-GCM key that never leaves the Android Keystore. */
class PrivateJournalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_private_journal", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun raw(): List<PrivateEntry> = try {
        prefs.getString("entries", null)?.let { gson.fromJson<List<PrivateEntry>>(it, object : TypeToken<List<PrivateEntry>>() {}.type) } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun add(text: String) {
        val entry = PrivateEntry(System.currentTimeMillis(), LocalDate.now().toString(), JournalCrypto.encrypt(key(), text))
        prefs.edit().putString("entries", gson.toJson(raw() + entry)).apply()
    }

    fun delete(id: Long) { prefs.edit().putString("entries", gson.toJson(raw().filterNot { it.id == id })).apply() }

    /** Decrypted entries, newest first. */
    fun entries(): List<Triple<Long, String, String>> {
        val k = runCatching { key() }.getOrNull() ?: return emptyList()
        return raw().sortedByDescending { it.id }.map { Triple(it.id, it.date, JournalCrypto.decrypt(k, it.sealed) ?: "(cannot decrypt)") }
    }

    companion object { private const val ALIAS = "chronora_private_journal" }
}
