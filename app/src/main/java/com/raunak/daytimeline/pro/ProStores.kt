package com.raunak.daytimeline.pro

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Local JSON persistence for Focus Guard settings and runtime state. */
class FocusGuardStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_focus_guard", Context.MODE_PRIVATE)
    private val gson = Gson()

    var config: FocusGuardConfig
        get() = (read<FocusGuardConfig>("config") ?: FocusGuardConfig()).normalized()
        set(value) { write("config", value); state.value = value }

    var runtime: FocusGuardRuntime
        get() = read("runtime") ?: FocusGuardRuntime()
        set(value) = write("runtime", value)

    fun update(transform: (FocusGuardConfig) -> FocusGuardConfig) { config = transform(config) }

    private val state = MutableStateFlow(config)
    val configFlow: StateFlow<FocusGuardConfig> get() = state

    private inline fun <reified T> read(key: String): T? = try {
        prefs.getString(key, null)?.let { gson.fromJson<T>(it, object : TypeToken<T>() {}.type) }
    } catch (_: Exception) { null }

    private fun write(key: String, value: Any) { prefs.edit().putString(key, gson.toJson(value)).apply() }
}

class LocationReminderStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_places", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun all(): List<LocationReminder> = try {
        prefs.getString("reminders", null)?.let {
            gson.fromJson<List<LocationReminder>>(it, object : TypeToken<List<LocationReminder>>() {}.type)
        } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun saveAll(list: List<LocationReminder>) { prefs.edit().putString("reminders", gson.toJson(list)).apply() }

    fun upsert(reminder: LocationReminder) = saveAll(all().filterNot { it.id == reminder.id } + reminder)

    fun delete(id: Long) = saveAll(all().filterNot { it.id == id })

    fun places(): List<SavedPlace> = try {
        prefs.getString("places", null)?.let {
            gson.fromJson<List<SavedPlace>>(it, object : TypeToken<List<SavedPlace>>() {}.type)
        } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun savePlace(place: SavedPlace) {
        prefs.edit().putString("places", gson.toJson(places().filterNot { it.name.equals(place.name, true) } + place)).apply()
    }
}

class FocusSoundPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_sound", Context.MODE_PRIVATE)
    var sound: AmbientSound?
        get() = prefs.getString("sound", null)?.let { runCatching { AmbientSound.valueOf(it) }.getOrNull() }
        set(v) = prefs.edit().putString("sound", v?.name).apply()
    var volume: Float
        get() = prefs.getFloat("volume", 0.5f)
        set(v) = prefs.edit().putFloat("volume", v).apply()
    /** Block apps from the Focus Guard list while a Pomodoro focus phase runs. */
    var blockDuringFocus: Boolean
        get() = prefs.getBoolean("block_focus", true)
        set(v) = prefs.edit().putBoolean("block_focus", v).apply()
}
