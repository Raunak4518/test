package com.raunak.daytimeline.pro

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import javax.crypto.KeyGenerator

class ProEnginesTest {
    private val today = LocalDate.of(2026, 1, 7) // Wednesday

    private val items = listOf(
        SearchItem(SearchKind.TASK, "1", "DSA binary search", date = today, tags = setOf("dsa"), done = false),
        SearchItem(SearchKind.TASK, "2", "DSA graphs", date = today.minusDays(1), tags = setOf("dsa"), done = true),
        SearchItem(SearchKind.NOTE, "3", "Binary search notes", "Mistakes with mid overflow", tags = setOf("dsa")),
        SearchItem(SearchKind.JOURNAL, "4", "Journal", "Research went well", date = today.minusDays(8))
    )

    @Test
    fun search_filters_by_kind_state_and_date() {
        assertThat(UniversalSearch.search(items, "unfinished dsa tasks", today).map { it.id }).containsExactly("1")
        assertThat(UniversalSearch.search(items, "everything I did yesterday", today).map { it.id }).containsExactly("2")
        assertThat(UniversalSearch.search(items, "binary", today).map { it.id }).containsExactly("1", "3")
        assertThat(UniversalSearch.search(items, "notes #dsa", today).map { it.id }).containsExactly("3")
        assertThat(UniversalSearch.search(items, "research last week", today).map { it.id }).containsExactly("4")
        assertThat(UniversalSearch.search(items, "   ", today)).isEmpty()
    }

    @Test
    fun garden_levels_streaks_and_plants() {
        val sessions = listOf(
            GardenSession(today.minusDays(2).toString(), 25, true),
            GardenSession(today.minusDays(1).toString(), 50, true),
            GardenSession(today.toString(), 90, true),
            GardenSession(today.toString(), 10, false)
        )
        val s = FocusGarden.summarize(sessions, today)
        assertThat(s.xp).isEqualTo(180)
        assertThat(s.level).isEqualTo(3)
        assertThat(s.focusDayStreak).isEqualTo(3)
        assertThat(s.withered).isEqualTo(1)
        assertThat(s.plants).containsExactly(Plant.TREE, Plant.PINE, Plant.BLOSSOM).inOrder()
        assertThat(s.badges).contains("Marathon (90m)")
        assertThat(FocusGarden.levelFor(0)).isEqualTo(1)
    }

    @Test
    fun energy_planner_puts_hard_work_in_peak_and_easy_work_outside() {
        val tasks = listOf(
            PlanTask(1, 60, Energy.LOW, 1),
            PlanTask(2, 90, Energy.HIGH, 2)
        )
        val slots = EnergyPlanner.plan(tasks, listOf(PlanGap(8 * 60, 18 * 60)))
        val hard = slots.first { it.taskId == 2L }
        val easy = slots.first { it.taskId == 1L }
        assertThat(hard.start).isEqualTo(9 * 60)
        assertThat(easy.end <= 9 * 60 || easy.start >= 13 * 60).isTrue()
        assertThat(EnergyPlanner.energyFromTags("dsa,deep")).isEqualTo(Energy.HIGH)
        assertThat(EnergyPlanner.energyFromTags("admin")).isEqualTo(Energy.LOW)
    }

    @Test
    fun energy_planner_skips_tasks_that_do_not_fit() {
        val slots = EnergyPlanner.plan(listOf(PlanTask(1, 120, Energy.MEDIUM, 1)), listOf(PlanGap(0, 60)))
        assertThat(slots).isEmpty()
    }

    @Test
    fun journal_crypto_round_trip() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val sealed = JournalCrypto.encrypt(key, "secret thoughts")
        assertThat(sealed).doesNotContain("secret")
        assertThat(JournalCrypto.decrypt(key, sealed)).isEqualTo("secret thoughts")
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThat(JournalCrypto.decrypt(other, sealed)).isNull()
    }

    @Test
    fun website_rules_match_domains_and_paths() {
        val rules = setOf("reddit.com", "youtube.com/shorts")
        assertThat(WebsiteRules.matches("https://old.reddit.com/r/android", rules)).isEqualTo("reddit.com")
        assertThat(WebsiteRules.matches("m.youtube.com/shorts/abc", rules)).isEqualTo("youtube.com/shorts")
        assertThat(WebsiteRules.matches("youtube.com/watch?v=1", rules)).isNull()
        assertThat(WebsiteRules.matches("notreddit.community", rules)).isNull()
        assertThat(WebsiteRules.matches("search terms", rules)).isNull()
    }
}
