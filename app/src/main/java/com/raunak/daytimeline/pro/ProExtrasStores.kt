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

/** Stores completed and abandoned focus sessions that grow the Focus Garden and feed the focus reports. */
class GardenStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("chronora_garden", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun sessions(): List<GardenSession> = try {
        prefs.getString("sessions", null)?.let { gson.fromJson<List<GardenSession>>(it, object : TypeToken<List<GardenSession>>() {}.type) } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    private fun save(list: List<GardenSession>) { prefs.edit().putString("sessions", gson.toJson(list.takeLast(5000))).apply() }

    fun add(session: GardenSession) = save(sessions() + session)

    /** Replaces the session with the same start time (used for ratings and edits). */
    fun update(session: GardenSession) = save(sessions().map { if (it.startedAt == session.startedAt && it.startedAt != 0L) session else it })

    fun delete(session: GardenSession) = save(sessions().filterNot { it == session })

    /** A finished session with the current tag, intention and interruption count, which are then cleared. */
    private fun record(minutes: Int, completed: Boolean, taskId: Long?, startedAt: Long, flow: Boolean) {
        val p = FocusPrefs(app)
        add(GardenSession(LocalDate.now().toString(), minutes, completed, taskId, startedAt, p.tag.ifBlank { null }, p.intention.ifBlank { null }, p.interruptions().count { it >= startedAt }, flow = flow))
        p.clearInterruptions()
    }

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
        record(before.focusMinutes, true, before.taskId, key - before.focusMinutes * 60_000L, false)
    }

    /** A Flowtime session ended; shorter than the minimum it counts as withered. */
    @Synchronized
    fun onFlowStopped(state: PomodoroStateEntity, minutes: Int, minMinutes: Int) {
        if (minutes < 1) { FocusPrefs(app).clearInterruptions(); return }
        record(minutes, minutes >= minMinutes, state.taskId, state.targetEpochMillis, true)
    }

    /** A focus phase reset after at least a minute withers a plant. */
    fun onAbandon(state: PomodoroStateEntity, now: Long = System.currentTimeMillis()) {
        if (state.phase == com.raunak.daytimeline.domain.PomodoroEngine.FLOW) {
            val m = (com.raunak.daytimeline.domain.PomodoroEngine.flowElapsed(state, now) / 60).toInt()
            onFlowStopped(state, m, FocusPrefs(app).config.flowMinMinutes)
            return
        }
        if (state.phase != "FOCUS" || state.targetEpochMillis == 0L && !state.running && state.remainingSeconds >= state.focusMinutes * 60L) return
        val remaining = if (state.running) (state.targetEpochMillis - now) / 1000 else state.remainingSeconds
        val focused = ((state.focusMinutes * 60L - remaining) / 60).toInt()
        if (focused >= 1) record(focused, false, state.taskId, now - focused * 60_000L, false)
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
