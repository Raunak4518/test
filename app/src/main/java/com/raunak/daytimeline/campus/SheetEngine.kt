package com.raunak.daytimeline.campus

import java.time.LocalDate

enum class SheetKind(val label: String) { DSA("Coding practice"), SUBJECT("Subject syllabus"), PLACEMENT("Placement prep"), CUSTOM("Custom") }
enum class Difficulty(val label: String) { EASY("Easy"), MEDIUM("Medium"), HARD("Hard") }
enum class ItemStatus(val label: String, val done: Boolean) { TODO("To do", false), ATTEMPTED("Attempted", false), SOLVED("Done", true), REVISE("Needs revision", false), SKIPPED("Skipped", false) }

data class SheetItem(
    val id: Long,
    val section: String,
    val title: String,
    val url: String = "",
    val difficulty: Difficulty? = null,
    val status: ItemStatus = ItemStatus.TODO,
    val notes: String = "",
    val doneDate: String? = null,
    val revisions: Int = 0,
    val nextReview: String? = null,
    val minutes: Int = 0,
    val starred: Boolean = false
)

data class StudySheet(
    val id: Long,
    val name: String,
    val kind: SheetKind,
    val items: List<SheetItem>,
    val subjectId: Long? = null,
    val dailyTarget: Int = 3,
    val examDate: String? = null
)

data class SectionProgress(val section: String, val done: Int, val total: Int) { val percent get() = if (total == 0) 0 else 100 * done / total }

data class SheetStats(
    val done: Int,
    val total: Int,
    val byDifficulty: Map<Difficulty, Pair<Int, Int>>,
    val sections: List<SectionProgress>,
    val doneToday: Int,
    val dueForReview: Int,
    val streak: Int,
    val perDay: Map<String, Int>
) { val percent get() = if (total == 0) 0 else 100 * done / total }

object SheetEngine {
    /** Spaced-repetition gaps (days) after solving, then after each revision. */
    val reviewGaps = listOf(3, 7, 15, 30, 60)

    fun setStatus(item: SheetItem, status: ItemStatus, today: LocalDate, gaps: List<Int> = reviewGaps): SheetItem = when {
        status == ItemStatus.SOLVED && !item.status.done -> item.copy(status = status, doneDate = today.toString(), nextReview = today.plusDays((gaps.firstOrNull() ?: 3).toLong()).toString())
        !status.done -> item.copy(status = status, nextReview = if (status == ItemStatus.REVISE) today.toString() else item.nextReview)
        else -> item.copy(status = status)
    }

    fun markRevised(item: SheetItem, today: LocalDate, gaps: List<Int> = reviewGaps): SheetItem {
        val n = item.revisions + 1
        val g = gaps.ifEmpty { reviewGaps }
        val gap = g.getOrElse(n) { g.last() }
        return item.copy(status = ItemStatus.SOLVED, revisions = n, nextReview = today.plusDays(gap.toLong()).toString(), doneDate = item.doneDate ?: today.toString())
    }

    fun reviewQueue(sheets: List<StudySheet>, today: LocalDate): List<Pair<StudySheet, SheetItem>> = sheets.flatMap { s ->
        s.items.filter { it.nextReview != null && it.nextReview <= today.toString() && (it.status.done || it.status == ItemStatus.REVISE) }.map { s to it }
    }.sortedBy { it.second.nextReview }

    /** Next item to work on: continue the first unfinished section, easiest first within it. */
    fun next(sheet: StudySheet): SheetItem? {
        val open = sheet.items.filter { !it.status.done && it.status != ItemStatus.SKIPPED }
        val section = sheet.items.map { it.section }.distinct().firstOrNull { sec -> open.any { it.section == sec } } ?: return null
        return open.filter { it.section == section }.minWithOrNull(compareBy<SheetItem> { it.status != ItemStatus.ATTEMPTED }.thenBy { it.difficulty?.ordinal ?: 0 })
    }

    fun stats(sheet: StudySheet, today: LocalDate): SheetStats {
        val perDay = sheet.items.mapNotNull { it.doneDate }.groupingBy { it }.eachCount()
        var streak = 0
        var d = if ((perDay[today.toString()] ?: 0) >= 1) today else today.minusDays(1)
        while ((perDay[d.toString()] ?: 0) >= 1) { streak++; d = d.minusDays(1) }
        return SheetStats(
            done = sheet.items.count { it.status.done },
            total = sheet.items.size,
            byDifficulty = Difficulty.values().associateWith { diff -> sheet.items.filter { it.difficulty == diff }.let { l -> l.count { it.status.done } to l.size } }.filterValues { it.second > 0 },
            sections = sheet.items.map { it.section }.distinct().map { sec -> sheet.items.filter { it.section == sec }.let { l -> SectionProgress(sec, l.count { it.status.done }, l.size) } },
            doneToday = perDay[today.toString()] ?: 0,
            dueForReview = sheet.items.count { it.nextReview != null && it.nextReview <= today.toString() && (it.status.done || it.status == ItemStatus.REVISE) },
            streak = streak,
            perDay = perDay
        )
    }

    fun doneOn(sheets: List<StudySheet>, date: LocalDate) = sheets.sumOf { s -> s.items.count { it.doneDate == date.toString() } }

    /**
     * Builds items from text. Section headers are lines starting with "#" or ending with ":".
     * Items: "Title", "Title | https://link", "Title | slug | E/M/H" (slug → LeetCode link),
     * or "- Title (https://link)". Bullets and numbering are stripped.
     */
    fun parse(text: String, idStart: Long, defaultSection: String = "General"): List<SheetItem> {
        var section = defaultSection
        var id = idStart
        val out = mutableListOf<SheetItem>()
        for (raw in text.lines()) {
            var line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) { section = line.trimStart('#').trim().ifBlank { section }; continue }
            if (line.endsWith(":") && !line.contains("http")) { section = line.dropLast(1).trim(); continue }
            line = line.replace(Regex("""^([-*•]|\d+[.)])\s*"""), "")
            val parts = line.split('|').map { it.trim() }
            var title = parts[0]
            var url = ""
            var diff: Difficulty? = null
            parts.drop(1).forEach { p ->
                when {
                    p.startsWith("http") -> url = p
                    p.uppercase() in setOf("E", "EASY") -> diff = Difficulty.EASY
                    p.uppercase() in setOf("M", "MEDIUM") -> diff = Difficulty.MEDIUM
                    p.uppercase() in setOf("H", "HARD") -> diff = Difficulty.HARD
                    p.matches(Regex("[a-z0-9-]+")) && url.isEmpty() -> url = "https://leetcode.com/problems/$p/"
                }
            }
            Regex("""\((https?://[^)\s]+)\)""").find(title)?.let { url = it.groupValues[1]; title = title.removeRange(it.range).trim() }
            Regex("""https?://\S+""").find(title)?.let { if (url.isEmpty()) url = it.value; title = title.removeRange(it.range).trim() }
            if (title.isNotBlank()) out += SheetItem(id++, section, title, url, diff)
        }
        return out
    }
}
