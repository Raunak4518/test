package com.raunak.daytimeline.campus

import com.raunak.daytimeline.ui.*

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * Local storage for everything college: timetable, attendance, deadlines, library, wake-up,
 * study sheets, CGPA and placements. One process-wide instance keeps every screen in sync.
 */
class CampusStore private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("chronora_campus", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _data = MutableStateFlow((read<CampusData>("data") ?: CampusData()).normalized())
    val data: StateFlow<CampusData> = _data
    private val _sheets = MutableStateFlow(read<List<StudySheet>>("sheets") ?: emptyList())
    val sheets: StateFlow<List<StudySheet>> = _sheets
    private val _semesters = MutableStateFlow(read<List<SemesterResult>>("cgpa") ?: emptyList())
    val semesters: StateFlow<List<SemesterResult>> = _semesters
    private val _companies = MutableStateFlow(read<List<Company>>("companies") ?: emptyList())
    val companies: StateFlow<List<Company>> = _companies

    fun update(transform: (CampusData) -> CampusData) {
        val next = transform(_data.value).normalized()
        _data.value = next
        write("data", next)
        CampusScheduler.rescheduleAll(app)
    }

    fun updateSheets(transform: (List<StudySheet>) -> List<StudySheet>) { _sheets.value = transform(_sheets.value); write("sheets", _sheets.value) }
    fun updateSheet(id: Long, transform: (StudySheet) -> StudySheet) = updateSheets { l -> l.map { if (it.id == id) transform(it) else it } }
    fun updateItem(sheetId: Long, itemId: Long, transform: (SheetItem) -> SheetItem) =
        updateSheet(sheetId) { s -> s.copy(items = s.items.map { if (it.id == itemId) transform(it) else it }) }
    fun updateSemesters(transform: (List<SemesterResult>) -> List<SemesterResult>) { _semesters.value = transform(_semesters.value); write("cgpa", _semesters.value) }
    fun updateCompanies(transform: (List<Company>) -> List<Company>) { _companies.value = transform(_companies.value); write("companies", _companies.value); CampusScheduler.rescheduleAll(app) }

    val gaps: List<Int> get() = _data.value.settings.reviewGaps

    fun mark(key: String, mark: Mark?) = update { d -> d.copy(marks = if (mark == null) d.marks - key else d.marks + (key to mark)) }

    fun template(asset: String, name: String, kind: SheetKind): StudySheet {
        val text = runCatching { app.assets.open("sheets/$asset").bufferedReader().use { it.readText() } }.getOrDefault("")
        val id = nextId()
        return StudySheet(id, name, kind, SheetEngine.parse(text, id + 1), dailyTarget = if (kind == SheetKind.DSA) 3 else 2)
    }

    /** Unique ids spaced 10 000 apart, so a batch (a parsed sheet or timetable) can use id, id+1, id+2… safely. */
    fun nextId(): Long = ids.addAndGet(10_000)

    private inline fun <reified T> read(key: String): T? = try {
        prefs.getString(key, null)?.let { gson.fromJson<T>(it, object : TypeToken<T>() {}.type) }
    } catch (_: Exception) { null }

    private fun write(key: String, value: Any) = prefs.edit().putString(key, gson.toJson(value)).apply()

    companion object {
        private val ids = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() * 10_000)
        @Volatile private var instance: CampusStore? = null
        fun get(context: Context): CampusStore = instance ?: synchronized(this) { instance ?: CampusStore(context).also { instance = it } }

        val templates = listOf(
            Triple("dsa.txt", "DSA — 196 must-do problems", SheetKind.DSA),
            Triple("cs_core.txt", "CS core: OS, DBMS, CN, OOP", SheetKind.PLACEMENT),
            Triple("aiml.txt", "AI/ML interview prep", SheetKind.PLACEMENT),
            Triple("aptitude.txt", "Aptitude, reasoning & HR", SheetKind.PLACEMENT),
            Triple("resume.txt", "Resume, projects & profiles", SheetKind.PLACEMENT)
        )
    }
}

/** Private store for the Discipline module, in its own file and excluded from backups. */
class DisciplineStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_d", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _state = MutableStateFlow(
        (try { prefs.getString("s", null)?.let { gson.fromJson(it, DisciplineState::class.java) } } catch (_: Exception) { null } ?: DisciplineState()).normalized()
    )
    val state: StateFlow<DisciplineState> = _state

    fun update(transform: (DisciplineState) -> DisciplineState) {
        _state.value = transform(_state.value).normalized()
        prefs.edit().putString("s", gson.toJson(_state.value)).apply()
    }

    fun checkIn(date: LocalDate, clean: Boolean) = update { it.copy(checkIns = it.checkIns + (date.toString() to clean)) }

    companion object {
        @Volatile private var instance: DisciplineStore? = null
        fun get(context: Context): DisciplineStore = instance ?: synchronized(this) { instance ?: DisciplineStore(context).also { instance = it } }
    }
}
