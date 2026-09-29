package com.raunak.daytimeline.features

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LifeEnginesTest {
    private val today = LocalDate.of(2026, 3, 11)

    @Test
    fun goal_pace_and_projection() {
        val g = OfflineGoal(1, "Read", 20, 100, today.plusDays(10).toString(), emptyList(), false, createdDate = today.minusDays(10).toString(),
            log = (0L..6L).map { GoalLog(today.minusDays(it).toString(), 2) })
        val st = GoalEngine.status(g, today)
        assertThat(st.expected).isEqualTo(50)
        assertThat(st.pace).isEqualTo(Pace.BEHIND)
        assertThat(st.neededPerDay!!).isWithin(0.01).of(80.0 / 11)
        assertThat(st.recentPerDay).isWithin(0.01).of(2.0)
        assertThat(st.projectedFinish).isEqualTo(today.plusDays(40))
        assertThat(GoalEngine.status(g.copy(progress = 60), today).pace).isEqualTo(Pace.AHEAD)
        assertThat(GoalEngine.status(g.copy(progress = 100), today).pace).isEqualTo(Pace.DONE)
        val project = g.copy(kind = "PROJECT", milestones = listOf("a", "b", "c", "d"), milestoneDone = setOf(0, 1))
        assertThat(GoalEngine.progress(project)).isEqualTo(2 to 4)
        assertThat(GoalEngine.history(g, today, 7).last()).isEqualTo(14)
    }

    @Test
    fun checklist_notes() {
        val body = "[ ] Milk\n[x] Eggs\n[ ] Bread"
        assertThat(NoteEngine.isChecklist(body)).isTrue()
        assertThat(NoteEngine.progress(body)).isEqualTo(1 to 3)
        assertThat(NoteEngine.toggleLine(body, 0)).startsWith("[x] Milk")
        assertThat(NoteEngine.sorted(body).last().text).isEqualTo("Eggs")
        assertThat(NoteEngine.toggleChecklist("a\nb")).isEqualTo("[ ] a\n[ ] b")
        assertThat(NoteEngine.toggleChecklist(body)).isEqualTo("Milk\nEggs\nBread")
        val a = OfflineNote(1, "DBMS", "see [[Normal forms]]", emptySet(), 0)
        val b = OfflineNote(2, "Normal forms", "1NF 2NF", emptySet(), 0)
        assertThat(NoteEngine.links(a, listOf(a, b)).map { it.id }).containsExactly(2L)
    }

    @Test
    fun journal_stats() {
        val e = (0L..9L).map { OfflineJournalEntry(today.minusDays(it).toString(), if (it % 2 == 0L) 5 else 2, 3, "", "", "", "", activities = if (it % 2 == 0L) listOf("Gym") else listOf("Social media")) }
        val s = JournalEngine.stats(e, today)
        assertThat(s.streak).isEqualTo(10)
        assertThat(s.entries).isEqualTo(10)
        assertThat(s.activityMood.first().first).isEqualTo("Gym")
        assertThat(s.activityMood.last().first).isEqualTo("Social media")
        assertThat(s.moodCounts).containsExactly(0, 5, 0, 0, 5).inOrder()
        assertThat(JournalEngine.prompt(listOf("a", "b"), today)).isNotNull()
        val old = com.google.gson.Gson().fromJson("{\"date\":\"2026-03-01\",\"mood\":3,\"energy\":3,\"wins\":\"\",\"blockers\":\"\",\"gratitude\":\"\",\"note\":\"\"}", OfflineJournalEntry::class.java)
        assertThat(JournalEngine.activities(old)).isEmpty()
    }

    @Test
    fun time_reports() {
        val zone = ZoneId.systemDefault()
        fun at(h: Int) = today.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        val entries = listOf(
            OfflineTimeEntry(1, "DSA", 10, at(9), at(11), "", setOf("deep")),
            OfflineTimeEntry(2, "Mail", null, at(12), at(12) + 30 * 60_000, "", emptySet()),
            OfflineTimeEntry(3, "DSA", 10, at(14), null, "", setOf("deep"))
        )
        val now = at(15)
        val p = TimeEngine.byProject(entries, listOf(OfflineProject(10, "Placements", 0xFF000000, emptyList(), null)), today, today, now)
        assertThat(p.first().label).isEqualTo("Placements")
        assertThat(p.first().minutes).isEqualTo(180)
        assertThat(TimeEngine.byTag(entries, today, today, now).first().minutes).isEqualTo(180)
        assertThat(TimeEngine.recent(entries).map { it.id }).containsExactly(2L, 1L).inOrder()
        assertThat(TimeEngine.perDay(entries, today.minusDays(1), 2, now)).containsExactly(0L, 210L).inOrder()
    }

    @Test
    fun routine_position_and_streak() {
        val r = OfflineRoutine(1, "Morning", listOf(OfflineRoutineStep("Water", 2), OfflineRoutineStep("Stretch", 5)), false, completionDates = setOf(today.toString(), today.minusDays(1).toString()))
        assertThat(RoutineEngine.position(r, 30)).isEqualTo(0 to 90L)
        assertThat(RoutineEngine.position(r, 180)).isEqualTo(1 to 240L)
        assertThat(RoutineEngine.position(r, 7 * 60)).isNull()
        assertThat(RoutineEngine.streak(r, today)).isEqualTo(2)
        assertThat(RoutineEngine.endsAt(r, 23 * 60 + 58)).isEqualTo(5)
    }

    @Test
    fun old_settings_get_defaults() {
        val s = com.google.gson.Gson().fromJson("{\"theme\":\"DARK\"}", OfflineSettings::class.java).normalized()
        assertThat(s.theme).isEqualTo("DARK")
        assertThat(s.journalActivities).isNotEmpty()
        assertThat(s.moodLabels).hasSize(5)
        assertThat(s.matrixImportantPriority).isEqualTo(2)
    }
}
