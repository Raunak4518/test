package com.raunak.daytimeline.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class EisenhowerEngineTest {
    private val today = LocalDate.of(2026, 3, 10)
    private fun task(id: Long, days: Long, priority: Int, done: Boolean = false) =
        TaskModel(id, "T$id", today.plusDays(days), 600, 660, "Other", 0, priority, "", false, "", "NONE", 0, done, "NONE", "")

    @Test
    fun sorts_into_quadrants_with_editable_rules() {
        val tasks = listOf(task(1, 0, 3), task(2, 5, 3), task(3, -2, 0), task(4, 6, 1), task(5, 0, 3, done = true), task(6, 40, 3))
        val g = EisenhowerEngine.group(tasks, today, urgentDays = 1, importantPriority = 2, horizonDays = 14)
        assertThat(g[Quadrant.DO]!!.map { it.id }).containsExactly(1L)
        assertThat(g[Quadrant.SCHEDULE]!!.map { it.id }).containsExactly(2L)
        assertThat(g[Quadrant.DELEGATE]!!.map { it.id }).containsExactly(3L)
        assertThat(g[Quadrant.DROP]!!.map { it.id }).containsExactly(4L)
        val wider = EisenhowerEngine.group(tasks, today, urgentDays = 7, importantPriority = 1, horizonDays = 14)
        assertThat(wider[Quadrant.DO]!!.map { it.id }).containsExactly(1L, 2L, 4L)
    }
}
