package com.raunak.daytimeline.productivity

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ChronoraTemplateTask(val title: String, val minutes: Int, val pomodoro: Boolean = false)
data class ChronoraTemplate(val id: Long, val name: String, val tasks: List<ChronoraTemplateTask>)

class ChronoraPlanningStore(context: Context) {
    private val prefs = context.getSharedPreferences("chronora_planning", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val templateType = object : TypeToken<List<ChronoraTemplate>>() {}.type
    private val dependencyType = object : TypeToken<Map<String, Set<Long>>>() {}.type

    fun templates(): List<ChronoraTemplate> {
        val raw = prefs.getString("templates", null) ?: return defaults()
        return runCatching { gson.fromJson<List<ChronoraTemplate>>(raw, templateType) ?: defaults() }.getOrDefault(defaults())
    }
    fun saveTemplates(value: List<ChronoraTemplate>) = prefs.edit().putString("templates", gson.toJson(value)).apply()
    fun addTemplate(name: String, tasks: List<ChronoraTemplateTask>) {
        saveTemplates(templates() + ChronoraTemplate(System.currentTimeMillis(), name.trim(), tasks))
    }
    fun deleteTemplate(id: Long) = saveTemplates(templates().filterNot { it.id == id })

    fun dependencies(): Map<Long, Set<Long>> {
        val raw = prefs.getString("dependencies", null) ?: return emptyMap()
        val map: Map<String, Set<Long>> = runCatching { gson.fromJson(raw, dependencyType) ?: emptyMap() }.getOrDefault(emptyMap())
        return map.mapKeys { it.key.toLongOrNull() ?: -1L }.filterKeys { it >= 0 }
    }
    fun setDependencies(taskId: Long, ids: Set<Long>) {
        val next = dependencies().toMutableMap()
        next[taskId] = ids
        prefs.edit().putString("dependencies", gson.toJson(next.mapKeys { it.key.toString() })).apply()
    }

    private fun defaults() = listOf(
        ChronoraTemplate(1, "Deep Work", listOf(ChronoraTemplateTask("Plan", 10), ChronoraTemplateTask("Focus", 50, true), ChronoraTemplateTask("Break", 10), ChronoraTemplateTask("Focus", 50, true), ChronoraTemplateTask("Review", 10))),
        ChronoraTemplate(2, "Study Block", listOf(ChronoraTemplateTask("Recall", 15, true), ChronoraTemplateTask("Study", 45, true), ChronoraTemplateTask("Break", 10), ChronoraTemplateTask("Practice", 45, true))),
        ChronoraTemplate(3, "Morning Reset", listOf(ChronoraTemplateTask("Hydrate", 5), ChronoraTemplateTask("Move", 20), ChronoraTemplateTask("Plan", 10)))
    )
}
