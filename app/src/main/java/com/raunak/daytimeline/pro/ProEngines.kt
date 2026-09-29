package com.raunak.daytimeline.pro

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.sqrt

// ---------------------------------------------------------------- Universal search

enum class SearchKind { TASK, NOTE, JOURNAL, HABIT, GOAL, ROUTINE, QUESTION, DEADLINE, SUBJECT, COMPANY }

data class SearchItem(
    val kind: SearchKind,
    val id: String,
    val title: String,
    val body: String = "",
    val date: LocalDate? = null,
    val tags: Set<String> = emptySet(),
    val done: Boolean? = null
)

data class SearchQuery(
    val terms: List<String>,
    val kinds: Set<SearchKind>,
    val tags: Set<String>,
    val from: LocalDate?,
    val to: LocalDate?,
    val done: Boolean?
)

/**
 * Offline search across everything with light natural-language filters:
 * "unfinished dsa tasks", "notes #exam", "everything yesterday", "journal last week", "done this week".
 */
object UniversalSearch {
    private val kindWords = mapOf(
        "task" to SearchKind.TASK, "tasks" to SearchKind.TASK, "todo" to SearchKind.TASK, "todos" to SearchKind.TASK,
        "note" to SearchKind.NOTE, "notes" to SearchKind.NOTE,
        "journal" to SearchKind.JOURNAL, "journals" to SearchKind.JOURNAL, "diary" to SearchKind.JOURNAL,
        "habit" to SearchKind.HABIT, "habits" to SearchKind.HABIT,
        "goal" to SearchKind.GOAL, "goals" to SearchKind.GOAL,
        "routine" to SearchKind.ROUTINE, "routines" to SearchKind.ROUTINE,
        "question" to SearchKind.QUESTION, "questions" to SearchKind.QUESTION, "problem" to SearchKind.QUESTION, "problems" to SearchKind.QUESTION, "topics" to SearchKind.QUESTION,
        "deadline" to SearchKind.DEADLINE, "deadlines" to SearchKind.DEADLINE, "assignments" to SearchKind.DEADLINE, "exams" to SearchKind.DEADLINE,
        "subject" to SearchKind.SUBJECT, "subjects" to SearchKind.SUBJECT, "class" to SearchKind.SUBJECT, "classes" to SearchKind.SUBJECT,
        "company" to SearchKind.COMPANY, "companies" to SearchKind.COMPANY
    )
    private val stopWords = setOf("all", "everything", "i", "did", "my", "the", "a", "an", "from", "of", "in", "on", "with", "involving", "about", "show", "find")

    fun parse(raw: String, today: LocalDate): SearchQuery {
        var text = " " + raw.lowercase().trim() + " "
        var from: LocalDate? = null
        var to: LocalDate? = null
        fun range(phrase: String, f: LocalDate, t: LocalDate) {
            if (text.contains(" $phrase ")) { text = text.replace(" $phrase ", " "); from = f; to = t }
        }
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        range("last week", monday.minusWeeks(1), monday.minusDays(1))
        range("this week", monday, monday.plusDays(6))
        range("next week", monday.plusWeeks(1), monday.plusDays(13))
        range("last month", today.minusMonths(1).withDayOfMonth(1), today.withDayOfMonth(1).minusDays(1))
        range("this month", today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
        range("yesterday", today.minusDays(1), today.minusDays(1))
        range("tomorrow", today.plusDays(1), today.plusDays(1))
        range("today", today, today)

        var done: Boolean? = null
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        val kinds = mutableSetOf<SearchKind>()
        val tags = mutableSetOf<String>()
        val terms = mutableListOf<String>()
        for (w in words) {
            when {
                w in setOf("unfinished", "open", "pending", "incomplete", "todo:open") -> done = false
                w in setOf("done", "finished", "completed", "complete") -> done = true
                w.startsWith("#") && w.length > 1 -> tags += w.drop(1)
                kindWords.containsKey(w) -> kinds += kindWords.getValue(w)
                w in stopWords -> Unit
                else -> terms += w
            }
        }
        return SearchQuery(terms, kinds, tags, from, to, done)
    }

    fun search(items: List<SearchItem>, raw: String, today: LocalDate): List<SearchItem> {
        if (raw.isBlank()) return emptyList()
        val q = parse(raw, today)
        return items.mapNotNull { item ->
            if (q.kinds.isNotEmpty() && item.kind !in q.kinds) return@mapNotNull null
            if (q.done != null && item.done != null && item.done != q.done) return@mapNotNull null
            if (q.done != null && item.done == null) return@mapNotNull null
            if (q.from != null && (item.date == null || item.date < q.from || item.date > q.to)) return@mapNotNull null
            if (q.tags.isNotEmpty() && q.tags.none { t -> item.tags.any { it.equals(t, true) } }) return@mapNotNull null
            val title = item.title.lowercase()
            val body = item.body.lowercase()
            var score = 1
            for (term in q.terms) {
                score += when {
                    title.split(Regex("\\W+")).contains(term) -> 5
                    title.contains(term) -> 3
                    item.tags.any { it.equals(term, true) } -> 3
                    body.contains(term) -> 1
                    else -> return@mapNotNull null
                }
            }
            item to score
        }.sortedWith(compareByDescending<Pair<SearchItem, Int>> { it.second }.thenByDescending { it.first.date })
            .map { it.first }
    }
}

// ---------------------------------------------------------------- Focus garden (gamification)

data class GardenSession(
    val date: String,
    val minutes: Int,
    val completed: Boolean,
    val taskId: Long? = null,
    /** When the session started (epoch ms); 0 for sessions saved by older versions. */
    val startedAt: Long = 0,
    val tag: String? = null,
    val intention: String? = null,
    val interruptions: Int = 0,
    /** 1–5 self-rating after the session (0 = not rated). */
    val rating: Int = 0,
    val note: String? = null,
    val flow: Boolean = false
)

enum class Plant(val label: String, val emoji: String, val minMinutes: Int) {
    SPROUT("Sprout", "🌱", 0), BUSH("Bush", "🌿", 20), TREE("Tree", "🌳", 25), PINE("Pine", "🌲", 45), PALM("Palm", "🌴", 60), BLOSSOM("Blossom", "🌸", 90)
}

data class GardenSummary(
    val xp: Int,
    val level: Int,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int,
    val plants: List<Plant>,
    val withered: Int,
    val focusDayStreak: Int,
    val bestStreak: Int,
    val todayMinutes: Int,
    val badges: List<String>
)

object FocusGarden {
    fun plantFor(minutes: Int): Plant = Plant.values().last { minutes >= it.minMinutes }

    /** Level n needs 25·n² XP in total; one XP per focused minute, +5 per completed session. */
    fun levelFor(xp: Int): Int = sqrt(xp / 25.0).toInt() + 1

    fun summarize(sessions: List<GardenSession>, today: LocalDate): GardenSummary {
        val completed = sessions.filter { it.completed }
        val xp = completed.sumOf { it.minutes + 5 }
        val level = levelFor(xp)
        val base = 25 * (level - 1) * (level - 1)
        val next = 25 * level * level
        val days = completed.map { LocalDate.parse(it.date) }.toSortedSet()
        var streak = 0
        var cursor = if (today in days) today else today.minusDays(1)
        while (cursor in days) { streak++; cursor = cursor.minusDays(1) }
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (d in days) { run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1; best = maxOf(best, run); prev = d }
        val todayMinutes = completed.filter { it.date == today.toString() }.sumOf { it.minutes }
        val totalMinutes = completed.sumOf { it.minutes }
        val badges = buildList {
            if (completed.isNotEmpty()) add("First focus")
            if (completed.size >= 10) add("10 sessions")
            if (completed.size >= 100) add("Century")
            if (totalMinutes >= 600) add("10 hours focused")
            if (totalMinutes >= 6000) add("100 hours focused")
            if (best >= 7) add("7-day streak")
            if (best >= 30) add("30-day streak")
            if (todayMinutes >= 240) add("Deep-work day (4h)")
            if (completed.any { it.minutes >= 90 }) add("Marathon (90m)")
        }
        return GardenSummary(xp, level, xp - base, next - base, completed.map { plantFor(it.minutes) }, sessions.count { !it.completed }, streak, best, todayMinutes, badges)
    }
}

// ---------------------------------------------------------------- Energy-aware planning

enum class Energy { HIGH, MEDIUM, LOW }

data class PlanTask(val id: Long, val durationMinutes: Int, val energy: Energy, val priority: Int)
data class PlanGap(val start: Int, val end: Int)
data class PlanSlot(val taskId: Long, val start: Int, val end: Int)

object EnergyPlanner {
    fun energyFromTags(tags: String): Energy {
        val t = tags.lowercase().split(',', ' ').map { it.trim().removePrefix("#") }
        return when {
            t.any { it in setOf("high", "hard", "deep", "energy:high", "focus") } -> Energy.HIGH
            t.any { it in setOf("low", "easy", "light", "energy:low", "admin", "shallow") } -> Energy.LOW
            else -> Energy.MEDIUM
        }
    }

    /**
     * Places tasks into free gaps so high-energy work lands in the peak window, low-energy work
     * lands outside it, and higher priority goes first. Returns only the tasks that fit.
     */
    fun plan(tasks: List<PlanTask>, gaps: List<PlanGap>, peakStart: Int = 9 * 60, peakEnd: Int = 13 * 60, breakMinutes: Int = 5): List<PlanSlot> {
        val free = gaps.filter { it.end > it.start }.map { intArrayOf(it.start, it.end) }.sortedBy { it[0] }.toMutableList()
        val ordered = tasks.sortedWith(compareBy<PlanTask> { it.energy.ordinal }.thenByDescending { it.priority }.thenByDescending { it.durationMinutes })
        val result = mutableListOf<PlanSlot>()
        for (task in ordered) {
            fun inPeak(s: Int) = s < peakEnd && s + task.durationMinutes > peakStart
            val candidates = free.filter { it[1] - it[0] >= task.durationMinutes }.map { gap ->
                // earliest start inside this gap, nudged into (or out of) the peak window when possible
                val start = when (task.energy) {
                    Energy.HIGH -> if (gap[0] < peakStart && peakStart + task.durationMinutes <= gap[1]) peakStart else gap[0]
                    Energy.LOW -> if (inPeak(gap[0]) && maxOf(gap[0], peakEnd) + task.durationMinutes <= gap[1]) maxOf(gap[0], peakEnd) else gap[0]
                    Energy.MEDIUM -> gap[0]
                }
                gap to start
            }
            val pick = when (task.energy) {
                Energy.HIGH -> candidates.minWithOrNull(compareBy<Pair<IntArray, Int>> { if (inPeak(it.second)) 0 else 1 }.thenBy { it.second })
                Energy.LOW -> candidates.minWithOrNull(compareBy<Pair<IntArray, Int>> { if (inPeak(it.second)) 1 else 0 }.thenBy { it.second })
                Energy.MEDIUM -> candidates.minByOrNull { it.second }
            } ?: continue
            val (gap, start) = pick
            val end = start + task.durationMinutes
            result += PlanSlot(task.id, start, end)
            free.remove(gap)
            if (start - gap[0] >= 5) free += intArrayOf(gap[0], start)
            if (gap[1] - (end + breakMinutes) >= 5) free += intArrayOf(end + breakMinutes, gap[1])
            free.sortBy { it[0] }
        }
        return result.sortedBy { it.start }
    }
}

// ---------------------------------------------------------------- Private (encrypted) journal

/** AES-256-GCM helper; the key lives in the Android Keystore on device and is injected here for testing. */
object JournalCrypto {
    fun encrypt(key: SecretKey, plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(iv) + ":" + java.util.Base64.getEncoder().encodeToString(data)
    }

    fun decrypt(key: SecretKey, sealed: String): String? = runCatching {
        val (iv, data) = sealed.split(':').let { java.util.Base64.getDecoder().decode(it[0]) to java.util.Base64.getDecoder().decode(it[1]) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()
}

// ---------------------------------------------------------------- Website blocking

object WebsiteRules {
    /** Normalises "https://www.YouTube.com/watch?v=1" → "youtube.com/watch?v=1". */
    fun normalize(url: String): String = url.trim().lowercase()
        .removePrefix("http://").removePrefix("https://").removePrefix("www.").removePrefix("m.")

    fun hostOf(url: String): String = normalize(url).substringBefore('/').substringBefore('?').substringBefore(':')

    /** A rule "reddit.com" matches reddit.com and any subdomain; "youtube.com/shorts" matches that path prefix. */
    fun matches(url: String, rules: Set<String>): String? {
        if (url.isBlank()) return null
        val norm = normalize(url)
        val host = hostOf(url)
        if (!host.contains('.')) return null
        return rules.firstOrNull { raw ->
            val rule = normalize(raw).trimEnd('/')
            if (rule.isBlank()) return@firstOrNull false
            if (rule.contains('/')) norm.startsWith(rule) || norm.contains(".$rule")
            else host == rule || host.endsWith(".$rule")
        }
    }
}
