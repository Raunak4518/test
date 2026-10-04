package com.raunak.daytimeline.trackers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How a tracker is logged. */
enum class TrackerType(val label: String) {
    CHECK("Done"), COUNT("Count"), AMOUNT("Amount"), DURATION("Minutes"), RATING("Rating"), CHOICE("Options")
}

/** Over what span the target applies. */
enum class TrackerPeriod(val label: String) { DAY("Per day"), WEEK("Per week"), MONTH("Per month") }

/** Reach the target, stay under it, or just log. */
enum class TrackerGoal(val label: String) { AT_LEAST("At least"), AT_MOST("At most"), NONE("Just log") }

/** Where values come from: typed in, or read from the phone automatically. */
enum class TrackerSource(val label: String) { MANUAL("Manual"), SCREEN_TIME("Screen time"), APP_USAGE("App usage"), FOCUS("Focus time") }

data class Tracker(
    val id: Long,
    val name: String,
    val emoji: String = "✅",
    val color: Long = 0xFF4F5BD5,
    val group: String = "",
    val type: TrackerType = TrackerType.CHECK,
    val unit: String = "",
    val target: Double = 1.0,
    val goal: TrackerGoal = TrackerGoal.AT_LEAST,
    val period: TrackerPeriod = TrackerPeriod.DAY,
    /** ISO days it's due (1 = Monday). */
    val days: Set<Int> = (1..7).toSet(),
    /** One-tap amounts for AMOUNT / DURATION / COUNT (e.g. 250, 500 ml). */
    val quickAmounts: List<Double> = emptyList(),
    /** Options for CHOICE (e.g. Healthy, Okay, Junk) and which of them count as a success. */
    val choices: List<String> = emptyList(),
    val goodChoices: Set<String> = emptySet(),
    /** Reminder times, minutes of the day. */
    val reminders: List<Int> = emptyList(),
    /** Optional window it should happen in (e.g. breakfast 08:00–09:00); -1 = any time. */
    val windowStart: Int = -1,
    val windowEnd: Int = -1,
    val source: TrackerSource = TrackerSource.MANUAL,
    val packages: Set<String> = emptySet(),
    val archived: Boolean = false,
    val order: Int = 0,
    /** Why it matters; shown in reminders and when a streak is at risk. */
    val why: String = "",
    /** Missed days that can be covered each month without losing the streak. */
    val freezesPerMonth: Int = 2,
    /** Days covered by a freeze. */
    val frozen: Set<String> = emptySet(),
    /** Evening nudge when today isn't done yet and a streak would break; -1 = off. */
    val saverMinute: Int = -1,
    /** Celebrate streak milestones (3, 7, 14, 30… days). */
    val celebrate: Boolean = true
) {
    val auto: Boolean get() = source != TrackerSource.MANUAL

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun normalized() = copy(
        emoji = emoji ?: "✅", group = group ?: "", unit = unit ?: "", type = type ?: TrackerType.CHECK, goal = goal ?: TrackerGoal.AT_LEAST,
        period = period ?: TrackerPeriod.DAY, days = (days ?: emptySet()).ifEmpty { (1..7).toSet() }, quickAmounts = quickAmounts ?: emptyList(),
        choices = choices ?: emptyList(), goodChoices = goodChoices ?: emptySet(), reminders = reminders ?: emptyList(),
        source = source ?: TrackerSource.MANUAL, packages = packages ?: emptySet(), why = why ?: "", frozen = frozen ?: emptySet()
    )
}

data class TrackerEntry(
    val id: Long,
    val trackerId: Long,
    val date: String,
    val minute: Int,
    val value: Double,
    val choice: String? = null,
    val note: String = ""
)

/** Pure logic: totals, success, progress and streaks. */
object TrackerEngine {
    fun periodStart(p: TrackerPeriod, d: LocalDate): LocalDate = when (p) {
        TrackerPeriod.DAY -> d
        TrackerPeriod.WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        TrackerPeriod.MONTH -> d.withDayOfMonth(1)
    }

    fun scheduled(t: Tracker, d: LocalDate) = t.period != TrackerPeriod.DAY || d.dayOfWeek.value in t.days

    /** The value that counts for one entry list: ticks, sums, the average rating or the number of good choices. */
    fun value(t: Tracker, entries: List<TrackerEntry>): Double = when (t.type) {
        // One tick per day: a weekly "3×" target counts different days.
        TrackerType.CHECK -> entries.map { it.date }.distinct().size.toDouble()
        TrackerType.RATING -> if (entries.isEmpty()) 0.0 else entries.map { it.value }.average()
        TrackerType.CHOICE -> entries.count { it.choice != null && (t.goodChoices.isEmpty() || it.choice in t.goodChoices) }.toDouble()
        else -> entries.sumOf { it.value }
    }

    fun dayEntries(t: Tracker, all: List<TrackerEntry>, d: LocalDate) = all.filter { it.trackerId == t.id && it.date == d.toString() }

    /** Value for the period that contains [d] (the day itself for daily trackers). */
    fun periodValue(t: Tracker, all: List<TrackerEntry>, d: LocalDate): Double {
        val from = periodStart(t.period, d)
        val list = all.filter { it.trackerId == t.id }.filter { val x = LocalDate.parse(it.date); !x.isBefore(from) && !x.isAfter(d) }
        return value(t, list)
    }

    fun met(t: Tracker, v: Double): Boolean = when (t.goal) {
        TrackerGoal.AT_LEAST -> v >= t.target
        TrackerGoal.AT_MOST -> v <= t.target
        TrackerGoal.NONE -> v > 0
    }

    /** 0..1 for rings. "At most" fills as you approach the limit. */
    fun progress(t: Tracker, v: Double): Float = when {
        t.goal == TrackerGoal.NONE -> if (v > 0) 1f else 0f
        t.target <= 0 -> if (v > 0) 1f else 0f
        else -> (v / t.target).toFloat().coerceIn(0f, 1f)
    }

    /** The day a tracker was created (ids are creation timestamps). */
    fun created(t: Tracker): LocalDate = runCatching { java.time.Instant.ofEpochMilli(t.id).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.getOrDefault(LocalDate.MIN)

    /**
     * Consecutive successful periods ending now. The current period counts once it's met but never breaks
     * the streak while still in progress; days off and days before the tracker existed are skipped.
     */
    fun streak(t: Tracker, all: List<TrackerEntry>, today: LocalDate, valueOf: (LocalDate) -> Double = { periodValue(t, all, it) }): Int {
        val since = created(t)
        var d = periodStart(t.period, today)
        var n = 0
        var current = true
        repeat(400) {
            val end = lastDayOf(t, d, today)
            if (end.isBefore(since)) return n
            // Days off and frozen days neither count nor break the streak.
            if (t.period == TrackerPeriod.DAY && (!scheduled(t, d) || d.toString() in t.frozen)) { d = previous(t, d); return@repeat }
            if (met(t, valueOf(end))) n++ else if (!current) return n
            current = false
            d = previous(t, d)
        }
        return n
    }

    /** The longest run ever, scanning back up to [days] periods. */
    fun bestStreak(t: Tracker, all: List<TrackerEntry>, today: LocalDate, valueOf: (LocalDate) -> Double = { periodValue(t, all, it) }): Int {
        val since = created(t)
        var d = periodStart(t.period, today)
        var run = 0; var best = 0
        repeat(400) {
            val end = lastDayOf(t, d, today)
            if (end.isBefore(since)) return maxOf(best, run)
            val skip = t.period == TrackerPeriod.DAY && (!scheduled(t, d) || d.toString() in t.frozen)
            if (!skip) { if (met(t, valueOf(end))) { run++; best = maxOf(best, run) } else if (d != periodStart(t.period, today)) run = 0 }
            d = previous(t, d)
        }
        return maxOf(best, run)
    }

    /** Freezes still available this month. */
    fun freezesLeft(t: Tracker, today: LocalDate): Int =
        (t.freezesPerMonth - t.frozen.count { runCatching { LocalDate.parse(it) }.getOrNull()?.let { d -> d.year == today.year && d.month == today.month } == true }).coerceAtLeast(0)

    /** Daily tracker due today, not done yet, with a streak that breaks at midnight. */
    fun atRisk(t: Tracker, all: List<TrackerEntry>, today: LocalDate, valueOf: (LocalDate) -> Double = { periodValue(t, all, it) }): Int {
        if (t.period != TrackerPeriod.DAY || t.goal == TrackerGoal.AT_MOST || !scheduled(t, today) || met(t, valueOf(today))) return 0
        return streak(t, all, today, valueOf)
    }

    /**
     * Yesterday was missed but the run before it can still be saved with a freeze: returns that run's length,
     * or 0 when there is nothing to save.
     */
    fun rescuable(t: Tracker, all: List<TrackerEntry>, today: LocalDate, valueOf: (LocalDate) -> Double = { periodValue(t, all, it) }): Int {
        if (t.period != TrackerPeriod.DAY || t.auto) return 0
        val y = today.minusDays(1)
        if (!scheduled(t, y) || y.toString() in t.frozen || met(t, valueOf(y)) || y.isBefore(created(t))) return 0
        if (freezesLeft(t, today) <= 0) return 0
        val before = streak(t.copy(frozen = t.frozen + y.toString()), all, y.minusDays(1), valueOf)
        return before
    }

    /** The last seven days, oldest first, for the chain under each tracker. */
    enum class Dot { DONE, MISSED, FROZEN, OFF, TODAY_OPEN, BEFORE }
    fun chain(t: Tracker, all: List<TrackerEntry>, today: LocalDate, valueOf: (LocalDate) -> Double = { value(t, dayEntries(t, all, it)) }): List<Pair<LocalDate, Dot>> =
        (6L downTo 0L).map { today.minusDays(it) }.map { d ->
            d to when {
                d.isBefore(created(t)) -> Dot.BEFORE
                d.toString() in t.frozen -> Dot.FROZEN
                t.period == TrackerPeriod.DAY && !scheduled(t, d) -> Dot.OFF
                (if (t.period == TrackerPeriod.DAY) met(t, valueOf(d)) else valueOf(d) > 0) -> Dot.DONE
                d == today -> Dot.TODAY_OPEN
                else -> Dot.MISSED
            }
        }

    val milestones = listOf(3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 365)

    /** A cheering line for a streak length. */
    fun cheer(streak: Int, name: String): String = when {
        streak in milestones -> listOf("🏆 $streak days of $name! That's real discipline.", "🎉 $streak-day milestone! Look how far you've come.", "🔥 $streak in a row. You're building who you want to be.").random()
        streak >= 30 -> "🔥 $streak days. This is who you are now."
        streak >= 7 -> listOf("🔥 $streak days strong. Keep the chain going!", "🔥 $streak and counting. Proud of you.").random()
        streak >= 2 -> listOf("🔥 $streak days in a row. Nice!", "🔥 $streak! Tomorrow makes it ${streak + 1}.").random()
        streak == 1 -> listOf("✅ Day one. Every streak starts here.", "✅ Done! Come back tomorrow for day 2.").random()
        else -> "✅ Logged. Small steps count."
    }

    /** A nudge for a reminder that hasn't been done yet. */
    fun nudge(t: Tracker, streak: Int): String = when {
        streak >= 2 -> "🔥 Keep your $streak-day streak alive" + if (t.why.isNotBlank()) ". ${t.why}" else ""
        t.why.isNotBlank() -> "Remember why: ${t.why}"
        else -> listOf("A small step now still counts.", "Two minutes is enough to start.", "Future you will be glad you did this.", "Start a streak today.").random()
    }

    private fun previous(t: Tracker, d: LocalDate) = when (t.period) { TrackerPeriod.DAY -> d.minusDays(1); TrackerPeriod.WEEK -> d.minusWeeks(1); TrackerPeriod.MONTH -> d.minusMonths(1) }
    private fun lastDayOf(t: Tracker, start: LocalDate, today: LocalDate) = when (t.period) {
        TrackerPeriod.DAY -> start
        TrackerPeriod.WEEK -> minOf(start.plusDays(6), today)
        TrackerPeriod.MONTH -> minOf(start.plusMonths(1).minusDays(1), today)
    }

    fun format(t: Tracker, v: Double): String = when (t.type) {
        TrackerType.CHECK -> if (v > 0) "Done" else "Not yet"
        TrackerType.RATING -> if (v > 0) "%.1f".format(v) else "—"
        TrackerType.DURATION -> mins(v.toInt())
        else -> num(v) + if (t.unit.isNotBlank()) " ${t.unit}" else ""
    }

    fun targetText(t: Tracker): String = when {
        t.goal == TrackerGoal.NONE -> ""
        t.type == TrackerType.CHECK -> if (t.period == TrackerPeriod.DAY) "" else "${num(t.target)}× ${t.period.label.lowercase()}"
        t.type == TrackerType.DURATION -> (if (t.goal == TrackerGoal.AT_MOST) "≤ " else "") + mins(t.target.toInt())
        else -> (if (t.goal == TrackerGoal.AT_MOST) "≤ " else "") + num(t.target) + if (t.unit.isNotBlank()) " ${t.unit}" else ""
    }

    fun num(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(v)
    fun mins(m: Int) = if (m >= 60) "${m / 60}h${if (m % 60 > 0) " ${m % 60}m" else ""}" else "${m}m"
}

/** Ready-made trackers; meal times follow the hostel mess timings when given. */
object TrackerTemplates {
    data class Pack(val name: String, val emoji: String, val trackers: (Long, Map<String, Pair<Int, Int>>) -> List<Tracker>)

    private val reelsApps = setOf("com.instagram.android", "com.google.android.youtube", "com.zhiliaoapp.musically", "com.snapchat.android", "app.revanced.android.youtube", "com.facebook.katana")

    val packs = listOf(
        Pack("Food", "🍽️") { id, meals ->
            fun meal(i: Int, name: String, emoji: String, def: Pair<Int, Int>): Tracker {
                val (s, e) = meals[name] ?: def
                return Tracker(id + i, name, emoji, 0xFFEF6A45, "Food", TrackerType.CHOICE, choices = listOf("Healthy", "Okay", "Junk", "Skipped"), goodChoices = setOf("Healthy", "Okay"),
                    reminders = listOf((e - 15).coerceAtLeast(s)), windowStart = s, windowEnd = e, order = i)
            }
            listOf(
                meal(0, "Breakfast", "🍳", 8 * 60 to 9 * 60),
                meal(1, "Lunch", "🍛", 12 * 60 + 30 to 14 * 60),
                meal(2, "Dinner", "🍲", 19 * 60 + 30 to 21 * 60),
                Tracker(id + 3, "Water", "💧", 0xFF2F8FE0, "Food", TrackerType.AMOUNT, "ml", 3000.0, quickAmounts = listOf(250.0, 500.0), reminders = listOf(10 * 60, 13 * 60, 16 * 60, 19 * 60), order = 3),
                Tracker(id + 4, "Fruits", "🍎", 0xFF22A06B, "Food", TrackerType.COUNT, "servings", 2.0, quickAmounts = listOf(1.0), order = 4)
            )
        },
        Pack("Morning walk", "🚶") { id, _ -> listOf(Tracker(id, "Morning walk", "🚶", 0xFF0E9F9A, "Health", TrackerType.DURATION, "min", 20.0, quickAmounts = listOf(10.0, 20.0), reminders = listOf(6 * 60 + 30))) },
        Pack("Sleep", "😴") { id, _ -> listOf(Tracker(id, "Sleep", "😴", 0xFF5B6BB0, "Health", TrackerType.AMOUNT, "h", 7.5, quickAmounts = listOf(6.0, 7.0, 8.0))) },
        Pack("Exercise", "💪") { id, _ -> listOf(Tracker(id, "Workout", "💪", 0xFFE5486B, "Health", TrackerType.CHECK, target = 4.0, period = TrackerPeriod.WEEK)) },
        Pack("Mood", "🙂") { id, _ -> listOf(Tracker(id, "Mood", "🙂", 0xFFDB8F12, "Mind", TrackerType.RATING, target = 3.0, reminders = listOf(21 * 60 + 30))) },
        Pack("Reading", "📖") { id, _ -> listOf(Tracker(id, "Reading", "📖", 0xFF7C5CE6, "Mind", TrackerType.DURATION, "min", 20.0, quickAmounts = listOf(10.0, 30.0))) },
        Pack("Short videos (auto)", "📵") { id, _ -> listOf(Tracker(id, "Short videos", "📵", 0xFFE0434C, "Phone", TrackerType.DURATION, "min", 30.0, TrackerGoal.AT_MOST, source = TrackerSource.APP_USAGE, packages = reelsApps)) },
        Pack("Screen time (auto)", "📱") { id, _ -> listOf(Tracker(id, "Screen time", "📱", 0xFF2F8FE0, "Phone", TrackerType.DURATION, "min", 180.0, TrackerGoal.AT_MOST, source = TrackerSource.SCREEN_TIME)) },
        Pack("Focus (auto)", "🎯") { id, _ -> listOf(Tracker(id, "Focus", "🎯", 0xFFEF6A45, "Study", TrackerType.DURATION, "min", 120.0, source = TrackerSource.FOCUS)) }
    )
}

/** How wins are celebrated: every completion, only milestones, or not at all. */
enum class CelebrateMode(val label: String) { EVERY("Every win"), MILESTONES("Milestones"), OFF("Off") }

/** Celebration settings; [cheers] are the user's own lines, mixed in with the built-in ones. */
data class CelebrationPrefs(val mode: CelebrateMode = CelebrateMode.EVERY, val cheers: List<String> = emptyList(), val autoCloseSeconds: Int = 6) {
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun normalized() = copy(mode = mode ?: CelebrateMode.EVERY, cheers = cheers ?: emptyList())
}

class TrackerStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chronora_trackers", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _trackers = MutableStateFlow(read<List<Tracker>>("trackers").orEmpty().map { it.normalized() })
    private val _entries = MutableStateFlow(read<List<TrackerEntry>>("entries").orEmpty())
    val trackers: StateFlow<List<Tracker>> = _trackers
    val entries: StateFlow<List<TrackerEntry>> = _entries
    private val _celebration = MutableStateFlow(read<CelebrationPrefs>("celebration")?.normalized() ?: CelebrationPrefs())
    val celebration: StateFlow<CelebrationPrefs> = _celebration
    fun saveCelebration(p: CelebrationPrefs) { _celebration.value = p; write("celebration", p) }

    private inline fun <reified T> read(key: String): T? = runCatching { prefs.getString(key, null)?.let { gson.fromJson<T>(it, object : TypeToken<T>() {}.type) } }.getOrNull()
    private fun write(key: String, v: Any) = prefs.edit().putString(key, gson.toJson(v)).apply()

    fun save(t: Tracker) {
        val list = _trackers.value.filterNot { it.id == t.id } + t
        _trackers.value = list.sortedWith(compareBy({ it.group }, { it.order }, { it.id })); write("trackers", _trackers.value)
    }
    fun saveAll(ts: List<Tracker>) { ts.forEach(::save) }
    fun delete(id: Long) {
        _trackers.value = _trackers.value.filterNot { it.id == id }; write("trackers", _trackers.value)
        _entries.value = _entries.value.filterNot { it.trackerId == id }; write("entries", _entries.value)
    }

    fun log(t: Tracker, date: LocalDate, value: Double, choice: String? = null, minute: Int = java.time.LocalTime.now().let { it.hour * 60 + it.minute }): TrackerEntry {
        val e = TrackerEntry(System.nanoTime(), t.id, date.toString(), minute, value, choice)
        _entries.value = _entries.value + e; write("entries", _entries.value)
        return e
    }
    fun remove(entryId: Long) { _entries.value = _entries.value.filterNot { it.id == entryId }; write("entries", _entries.value) }
    fun restore(e: TrackerEntry) { _entries.value = _entries.value + e; write("entries", _entries.value) }
    /** For CHECK: remove today's tick if present, otherwise add one. Returns true when now done. */
    fun toggle(t: Tracker, date: LocalDate): Boolean {
        val today = _entries.value.filter { it.trackerId == t.id && it.date == date.toString() }
        return if (today.isNotEmpty()) { _entries.value = _entries.value - today.toSet(); write("entries", _entries.value); false } else { log(t, date, 1.0); true }
    }

    companion object {
        @Volatile private var instance: TrackerStore? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: TrackerStore(context).also { instance = it } }
    }
}
