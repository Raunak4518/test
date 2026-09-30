package com.raunak.daytimeline.home

import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.domain.TaskModel
import com.raunak.daytimeline.wellbeing.AddictionLevel
import org.junit.Test
import java.time.LocalDate

class TimelineModelTest {
    private val day = LocalDate.of(2026, 9, 30)
    private fun task(id: Long, s: Int, e: Int, p: Int = 1, tags: String = "", done: Boolean = false) =
        TaskModel(id, "T$id", day, s, e, "", 0xFF000000, p, "", false, tags, "NONE", 0, done, "NONE", "")
    private fun ev(key: String, s: Int, e: Int, kind: EventKind = EventKind.TASK) = TimelineEvent(key, kind, key, "", s, e, 0)

    @Test
    fun gaps_fill_free_time_between_events() {
        val rows = TimelineBuilder.withGaps(listOf(ev("a", 9 * 60, 10 * 60), ev("b", 12 * 60, 13 * 60)), 8 * 60, 14 * 60, 30)
        assertThat(rows.map { it.kind to it.start }).containsExactly(
            EventKind.FREE to 480, EventKind.TASK to 540, EventKind.FREE to 600, EventKind.TASK to 720, EventKind.FREE to 780
        ).inOrder()
        assertThat(TimelineBuilder.freeMinutes(rows)).isEqualTo(60 + 120 + 60)
        // Short gaps are skipped.
        assertThat(TimelineBuilder.withGaps(listOf(ev("a", 490, 600)), 480, 600, 30).none { it.kind == EventKind.FREE }).isTrue()
    }

    @Test
    fun now_marker_sits_before_the_first_future_row() {
        val rows = listOf(ev("f", 360, 480, EventKind.FREE), ev("b", 480, 540), ev("c", 600, 660))
        assertThat(TimelineBuilder.nowIndex(rows, 458)).isEqualTo(1)
        assertThat(TimelineBuilder.nowIndex(rows, 500)).isEqualTo(2)
        assertThat(TimelineBuilder.nowIndex(rows, 700)).isEqualTo(3)
    }

    @Test
    fun overlapping_events_share_lanes() {
        val placed = GridLayout.lanes(listOf(ev("a", 540, 600), ev("b", 570, 630), ev("c", 700, 760))).associateBy { it.event.key }
        assertThat(placed["a"]!!.lanes).isEqualTo(2)
        assertThat(placed["b"]!!.lane).isEqualTo(1)
        assertThat(placed["c"]!!.lanes).isEqualTo(1)
        assertThat(placed["c"]!!.lane).isEqualTo(0)
    }

    @Test
    fun auto_plan_avoids_classes_and_keeps_fixed_tasks() {
        val tasks = listOf(task(1, 0, 60, p = 1), task(2, 0, 30, p = 3), task(3, 13 * 60, 14 * 60, tags = "fixed"), task(4, 0, 30, done = true))
        val plan = AutoPlanner.plan(tasks, busy = listOf(9 * 60 to 11 * 60), dayStart = 9 * 60, dayEnd = 23 * 60, from = 0, buffer = 10)
        // Highest priority first, right after the class plus buffer.
        assertThat(plan[2]).isEqualTo(11 * 60 + 10 to 11 * 60 + 40)
        assertThat(plan[1]).isEqualTo(11 * 60 + 50 to 12 * 60 + 50)
        assertThat(plan.keys).containsNoneOf(3L, 4L)
    }

    @Test
    fun addiction_level_scales_with_goal() {
        assertThat(AddictionLevel.of(30, 180)).isEqualTo(AddictionLevel.CHAMPION)
        assertThat(AddictionLevel.of(170, 180)).isEqualTo(AddictionLevel.FIT)
        assertThat(AddictionLevel.of(400, 180)).isEqualTo(AddictionLevel.ADDICTED)
        assertThat(AddictionLevel.of(170, 120)).isEqualTo(AddictionLevel.HABITUAL)
    }
}
