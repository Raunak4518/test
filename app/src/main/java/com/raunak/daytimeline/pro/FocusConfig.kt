package com.raunak.daytimeline.pro

import android.content.Context
import com.google.gson.Gson

/** Everything about how the focus timer behaves; all editable in Focus → Timer settings. */
data class FocusConfig(
    /** "POMODORO" (count down) or "FLOW" (count up, break = focused ÷ divisor). */
    val mode: String = "POMODORO",
    val autoStartBreaks: Boolean = true,
    /** Off by default so the next focus waits for you (Focus To-Do's default). */
    val autoStartFocus: Boolean = false,
    val dailyGoalMinutes: Int = 240,
    val flowBreakDivisor: Int = 5,
    /** Flow sessions shorter than this don't grow a plant. */
    val flowMinMinutes: Int = 10,
    val tickSound: Boolean = false,
    val vibrate: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** Ask for a 1–5 rating and a note after each focus session. */
    val reflect: Boolean = true,
    /** Deep focus: during a focus session every app except [strictAllowed] is blocked (needs Focus Guard's accessibility service). */
    val strict: Boolean = false,
    val strictAllowed: Set<String> = emptySet(),
    val tags: List<String> = listOf("Study", "DSA", "Assignment", "Revision", "Project", "Reading"),
    /** Estimated pomodoros per task id. */
    val taskEstimates: Map<String, Int> = emptyMap()
) {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun normalized(): FocusConfig {
        val d = FocusConfig()
        return copy(
            mode = mode ?: d.mode,
            dailyGoalMinutes = if (dailyGoalMinutes > 0) dailyGoalMinutes else d.dailyGoalMinutes,
            flowBreakDivisor = if (flowBreakDivisor > 0) flowBreakDivisor else d.flowBreakDivisor,
            flowMinMinutes = if (flowMinMinutes > 0) flowMinMinutes else d.flowMinMinutes,
            strictAllowed = strictAllowed ?: emptySet(),
            tags = tags ?: d.tags,
            taskEstimates = taskEstimates ?: emptyMap()
        )
    }

    val flow get() = mode == "FLOW"
    fun estimate(taskId: Long) = taskEstimates[taskId.toString()] ?: 0
}

/** Focus settings plus the in-progress session's tag, intention and interruptions. */
class FocusPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_focus_timer", Context.MODE_PRIVATE)
    private val gson = Gson()

    var config: FocusConfig
        get() = runCatching { gson.fromJson(prefs.getString("config", null), FocusConfig::class.java) }.getOrNull()?.normalized() ?: FocusConfig()
        set(v) = prefs.edit().putString("config", gson.toJson(v)).apply()

    fun update(transform: (FocusConfig) -> FocusConfig) { config = transform(config) }

    var tag: String
        get() = prefs.getString("tag", "") ?: ""
        set(v) = prefs.edit().putString("tag", v).apply()

    var intention: String
        get() = prefs.getString("intention", "") ?: ""
        set(v) = prefs.edit().putString("intention", v).apply()

    /** Times (epoch ms) the "Distracted" button was pressed in the current focus session. */
    fun interruptions(): List<Long> = prefs.getString("interruptions", "")!!.split(',').mapNotNull { it.toLongOrNull() }

    fun addInterruption(at: Long = System.currentTimeMillis()) = prefs.edit().putString("interruptions", (interruptions() + at).joinToString(",")).apply()

    fun clearInterruptions() = prefs.edit().remove("interruptions").apply()

    /** Session start time of the last reflection shown (so it is asked once). */
    var reflectedUpTo: Long
        get() = prefs.getLong("reflected", 0)
        set(v) = prefs.edit().putLong("reflected", v).apply()
}
