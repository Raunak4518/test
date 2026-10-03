package com.raunak.daytimeline.campus

import com.raunak.daytimeline.ui.*

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.raunak.daytimeline.AppContainer
import java.io.File

/**
 * One-file backup of everything Chronora stores: timeline tasks, Campus (timetable, attendance,
 * sheets, CGPA, placements), habits/notes/journal, alarms, focus/blocking, wellbeing and web-filter
 * settings. The private Discipline data is included only when asked.
 */
object FullBackup {
    private val prefFiles = listOf(
        "chronora_campus", "chronora_completion", "chronora_dependencies", "chronora_focus_guard", "chronora_garden",
        "chronora_places", "chronora_planning", "chronora_sound", "chronora_study", "chronora_web_filter",
        "chronora_wellbeing", "chronora_classroom", "chronora_focus_timer", "offline_alarms", "offline_productivity_v2", "productivity_features", "chronora_trackers"
    )
    private const val PRIVATE_FILE = "chronora_d"
    private const val IMPORTED_LIST = "filter_imported.txt"

    suspend fun export(context: Context, includePrivate: Boolean): String {
        val root = JsonObject()
        root.addProperty("app", "Chronora")
        root.addProperty("version", 2)
        root.addProperty("createdAt", System.currentTimeMillis())
        val prefs = JsonObject()
        (prefFiles + if (includePrivate) listOf(PRIVATE_FILE) else emptyList()).forEach { name ->
            val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            if (all.isEmpty()) return@forEach
            val obj = JsonObject()
            all.forEach { (k, v) ->
                val e = JsonObject()
                when (v) {
                    is String -> { e.addProperty("t", "s"); e.addProperty("v", v) }
                    is Int -> { e.addProperty("t", "i"); e.addProperty("v", v) }
                    is Long -> { e.addProperty("t", "l"); e.addProperty("v", v) }
                    is Float -> { e.addProperty("t", "f"); e.addProperty("v", v) }
                    is Boolean -> { e.addProperty("t", "b"); e.addProperty("v", v) }
                    is Set<*> -> { e.addProperty("t", "ss"); e.add("v", com.google.gson.JsonArray().apply { v.forEach { add(it.toString()) } }) }
                    else -> return@forEach
                }
                obj.add(k, e)
            }
            prefs.add(name, obj)
        }
        root.add("prefs", prefs)
        root.addProperty("tasks", AppContainer(context).repository.exportJson())
        File(context.filesDir, IMPORTED_LIST).takeIf { it.exists() }?.let { root.addProperty("filterList", it.readText()) }
        return root.toString()
    }

    /** Restores a backup. Returns a short summary; the app should restart afterwards. */
    suspend fun import(context: Context, json: String): Result<String> = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("app")?.asString == "Chronora") { "Not a Chronora backup" }
        val prefs = root.getAsJsonObject("prefs")
        var files = 0
        val locked = Commitment.active(DisciplineStore.get(context).state.value.commit)
        val guarded = setOf(PRIVATE_FILE, "chronora_web_filter", "chronora_focus_guard", "chronora_wellbeing")
        prefs.entrySet().forEach { (name, obj) ->
            if (name !in prefFiles && name != PRIVATE_FILE) return@forEach
            if (locked && name in guarded) return@forEach
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            obj.asJsonObject.entrySet().forEach { (k, e) ->
                val o = e.asJsonObject
                val v = o.get("v")
                when (o.get("t").asString) {
                    "s" -> editor.putString(k, v.asString)
                    "i" -> editor.putInt(k, v.asInt)
                    "l" -> editor.putLong(k, v.asLong)
                    "f" -> editor.putFloat(k, v.asFloat)
                    "b" -> editor.putBoolean(k, v.asBoolean)
                    "ss" -> editor.putStringSet(k, v.asJsonArray.map { it.asString }.toSet())
                }
            }
            editor.commit()
            files++
        }
        root.get("tasks")?.asString?.let { AppContainer(context).repository.importJson(it) }
        root.get("filterList")?.asString?.let { File(context.filesDir, IMPORTED_LIST).writeText(it) }
        "Restored $files sections" + if (root.has("tasks")) " and your timeline" else ""
    }
}
