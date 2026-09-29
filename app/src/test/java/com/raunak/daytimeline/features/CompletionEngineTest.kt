package com.raunak.daytimeline.features

import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.domain.TaskModel
import org.junit.Test
import java.time.LocalDate

class CompletionEngineTest {
    private fun task(id: Long, done: Boolean = false, start: Int = 60, end: Int = 120) =
        TaskModel(id, "T$id", LocalDate.of(2026, 9, 29), start, end, "Other", 0L, 1, "", false, "", "NONE", 0, done, "NONE", "")

    @Test fun dependencyGraphRejectsCycles() {
        val graph = DependencyGraph(listOf(TaskDependency(2, 1), TaskDependency(3, 2)))
        assertThat(graph.wouldCycle(1, 3)).isTrue()
        assertThat(graph.wouldCycle(3, 1)).isFalse()
    }

    @Test fun dependencyGraphFindsBlockers() {
        val a = task(1, done = false)
        val b = task(2, done = false)
        val graph = DependencyGraph(listOf(TaskDependency(2, 1)))
        assertThat(graph.isBlocked(b, listOf(a, b))).isTrue()
        assertThat(graph.blockers(b, listOf(a, b))).containsExactly(a)
    }

    @Test fun recurrencePlannerSupportsWeekdays() {
        val start = LocalDate.of(2026, 9, 28)
        val dates = RecurrencePlanner.occurrences(start, "WEEKDAYS", count = 5)
        assertThat(dates).containsExactly(
            LocalDate.of(2026, 9, 28),
            LocalDate.of(2026, 9, 29),
            LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 2)
        ).inOrder()
    }

    @Test fun srsFailedCardReturnsTomorrow() {
        val card = StudyCard(1, "Q", "A")
        val graded = SrsEngine.grade(card, 1, LocalDate.of(2026, 9, 29))
        assertThat(graded.dueEpochDay).isEqualTo(LocalDate.of(2026, 9, 30).toEpochDay())
        assertThat(graded.repetitions).isEqualTo(0)
    }

    @Test fun workloadIsBounded() {
        val tasks = (1..20).map { task(it.toLong(), start = 60, end = 180, done = false) }
        assertThat(WorkloadEngine.score(tasks, LocalDate.of(2026, 9, 29))).isAtMost(100)
    }

    @Test fun icsRoundTripParsesEvents() {
        val ics = IcsCodec.export(listOf(task(1)))
        val parsed = IcsCodec.importSummaries(ics)
        assertThat(parsed).hasSize(1)
        assertThat(parsed.first().title).isEqualTo("T1")
    }
}
