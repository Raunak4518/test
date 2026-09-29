package com.raunak.daytimeline.classroom

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ClassKind(val label: String, val icon: String) {
    ASSIGNMENT("Assignment", "📝"), QUIZ("Quiz", "🧪"), QUESTION("Question", "❓"), MATERIAL("Material", "📎"),
    ANNOUNCEMENT("Announcement", "📣"), COMMENT("Message", "💬"), GRADE("Grade", "🏅"), OTHER("Update", "🔔")
}

enum class WorkState(val label: String) { NONE(""), PENDING("To do"), SUBMITTED("Turned in"), LATE("Missing"), RETURNED("Returned") }

/** One thing from Google Classroom: coursework, material, announcement, message or grade. */
data class ClassItem(
    val id: String,
    /** "API" (Google Classroom sync) or "NOTIFICATION" (read from the Classroom app's notifications). */
    val source: String,
    val kind: ClassKind,
    val course: String,
    val title: String,
    val body: String = "",
    val dueAt: Long? = null,
    val postedAt: Long,
    val link: String = "",
    val state: WorkState = WorkState.NONE,
    val points: Double? = null,
    val grade: Double? = null,
    val read: Boolean = false,
    /** Marked done in Chronora (for items the sync can't see, e.g. from notifications). */
    val done: Boolean = false,
    val snoozedUntil: Long = 0,
    val hidden: Boolean = false,
    val firstSeen: Long = postedAt
)

data class ClassroomSettings(
    val captureNotifications: Boolean = true,
    /** Pending work with a due date becomes a Campus deadline (with its reminders). */
    val autoDeadlines: Boolean = true,
    /** Alert at once for urgent items; everything else waits for the digest. */
    val instantAlerts: Boolean = true,
    /** Work due within this many hours counts as urgent. */
    val urgentHours: Int = 48,
    val digestMinute: Int = 7 * 60 + 30,
    val digestOff: Boolean = false,
    /** Minutes of work to expect per kind (for study targets). */
    val effortMinutes: Map<String, Int> = mapOf("ASSIGNMENT" to 120, "QUIZ" to 90, "QUESTION" to 20, "MATERIAL" to 30),
    val dailyStudyCapMinutes: Int = 180,
    /** Course name → Campus subject id, when automatic matching guesses wrong. */
    val courseMap: Map<String, Long> = emptyMap(),
    val importantWords: List<String> = listOf("quiz", "test", "exam", "viva", "mid sem", "minor", "deadline", "extended", "postponed", "cancelled", "canceled", "no class", "extra class", "submit", "venue", "marks", "attendance", "lab"),
    val syncEveryHours: Int = 3,
    val connected: Boolean = false,
    val lastSync: Long = 0,
    val lastError: String? = null
) {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun normalized(): ClassroomSettings {
        val d = ClassroomSettings()
        return copy(
            urgentHours = if (urgentHours > 0) urgentHours else d.urgentHours,
            digestMinute = if (digestMinute in 1 until 24 * 60) digestMinute else d.digestMinute,
            effortMinutes = (effortMinutes ?: emptyMap()).let { d.effortMinutes + it },
            dailyStudyCapMinutes = if (dailyStudyCapMinutes > 0) dailyStudyCapMinutes else d.dailyStudyCapMinutes,
            courseMap = courseMap ?: emptyMap(),
            importantWords = importantWords ?: d.importantWords,
            syncEveryHours = if (syncEveryHours > 0) syncEveryHours else d.syncEveryHours
        )
    }

    fun effort(kind: ClassKind) = effortMinutes[kind.name] ?: 0
}

data class ClassroomData(
    val items: List<ClassItem> = emptyList(),
    val settings: ClassroomSettings = ClassroomSettings(),
    /** Suggestions already applied or dismissed (by id). */
    val handledSuggestions: Set<String> = emptySet()
) {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun normalized() = copy(items = items ?: emptyList(), settings = (settings ?: ClassroomSettings()).normalized(), handledSuggestions = handledSuggestions ?: emptySet())
}

/** Local store for everything Classroom; one instance per process. */
class ClassroomStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_classroom", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _data = MutableStateFlow(runCatching { gson.fromJson(prefs.getString("data", null), ClassroomData::class.java) }.getOrNull()?.normalized() ?: ClassroomData())
    val data: StateFlow<ClassroomData> = _data

    @Synchronized
    fun update(transform: (ClassroomData) -> ClassroomData) {
        val next = transform(_data.value).normalized()
        _data.value = next
        prefs.edit().putString("data", gson.toJson(next)).apply()
    }

    fun settings(transform: (ClassroomSettings) -> ClassroomSettings) = update { it.copy(settings = transform(it.settings)) }

    fun item(id: String, transform: (ClassItem) -> ClassItem) = update { d -> d.copy(items = d.items.map { if (it.id == id) transform(it) else it }) }

    /**
     * Adds new items and refreshes known ones. Items from the sync are the source of truth for state;
     * local flags (done, read, hidden, snooze) are kept. Returns the items that are new.
     */
    fun merge(incoming: List<ClassItem>): List<ClassItem> {
        val fresh = mutableListOf<ClassItem>()
        update { d ->
            val byId = d.items.associateBy { it.id }.toMutableMap()
            incoming.forEach { n ->
                val old = byId[n.id] ?: d.items.firstOrNull { ClassroomBrain.sameThing(it, n) }
                if (old == null) { byId[n.id] = n; fresh += n }
                else {
                    val keepSource = if (old.source == "API" && n.source != "API") old else n
                    byId.remove(old.id)
                    byId[keepSource.id] = keepSource.copy(read = old.read, done = old.done || n.state == WorkState.SUBMITTED || n.state == WorkState.RETURNED,
                        hidden = old.hidden, snoozedUntil = old.snoozedUntil, firstSeen = old.firstSeen,
                        dueAt = keepSource.dueAt ?: old.dueAt, body = keepSource.body.ifBlank { old.body })
                }
            }
            d.copy(items = byId.values.sortedByDescending { it.postedAt }.take(1500))
        }
        return fresh
    }

    companion object {
        @Volatile private var instance: ClassroomStore? = null
        fun get(context: Context): ClassroomStore = instance ?: synchronized(this) { instance ?: ClassroomStore(context).also { instance = it } }
    }
}
