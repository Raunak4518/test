package com.raunak.daytimeline.campus

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StudyEnginesTest {
    private val utc = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 1, 10)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).toInstant(utc).toEpochMilli()

    @Test
    fun study_minutes_per_day_and_subject() {
        val dsa = Subject(1, "DSA")
        val sessions = listOf(
            StudySession(at(day, 10), at(day, 11, 30), 1, ""),
            StudySession(at(day, 23), at(day.plusDays(1), 1), null, "Project"),
            StudySession(at(day.plusDays(1), 9), null, 1, "")
        )
        assertThat(StudyEngines.minutesOn(sessions, day, utc)).isEqualTo(150)
        assertThat(StudyEngines.minutesOn(sessions, day.plusDays(1), utc, now = at(day.plusDays(1), 10))).isEqualTo(120)
        val by = StudyEngines.bySubject(sessions, listOf(dsa), day, day.plusDays(1), utc, now = at(day.plusDays(1), 10))
        assertThat(by).containsExactly("DSA" to 150, "Project" to 120).inOrder()
    }

    @Test
    fun marks_predict_grade_and_what_is_needed() {
        val cutoffs = CampusSettings().gradeCutoffs
        val a = listOf(
            Assessment(1, 7, "Minor 1", 20.0, 16.0, 15.0),
            Assessment(2, 7, "Mid-sem", 30.0, 21.0, 30.0),
            Assessment(3, 7, "End-sem", 100.0, null, 55.0)
        )
        val m = StudyEngines.marks(7, a, cutoffs)!!
        assertThat(m.weightDone).isWithin(1e-9).of(45.0)
        assertThat(m.earnedPercent).isWithin(1e-9).of(33.0) // 12 + 21
        assertThat(m.projectedPercent!!).isWithin(1e-6).of(73.333333)
        assertThat(m.predictedGrade).isEqualTo("BB")
        val (grade, need) = m.neededForNext!!
        assertThat(grade).isEqualTo("AB")
        assertThat(need).isWithin(1e-6).of((80 - 33.0) / 55.0 * 100)
        assertThat(StudyEngines.marks(8, a, cutoffs)).isNull()
    }

    @Test
    fun revision_plan_spreads_topics_before_buffer() {
        val items = (1..10).map { SheetItem(it.toLong(), "U", "T$it") } + SheetItem(99, "U", "Done", status = ItemStatus.SOLVED)
        val plan = StudyEngines.revisionPlan(items, exam = day.plusDays(7), today = day, bufferDays = 2)
        // Study days: day .. day+4 (5 days), 2 items each
        assertThat(plan.keys).containsExactly(day, day.plusDays(1), day.plusDays(2), day.plusDays(3), day.plusDays(4))
        assertThat(plan.values.sumOf { it.size }).isEqualTo(10)
        assertThat(plan.getValue(day).map { it.title }).containsExactly("T1", "T2").inOrder()
        // Exam tomorrow: everything today
        assertThat(StudyEngines.revisionPlan(items, day.plusDays(1), day, 2).getValue(day)).hasSize(10)
    }

    @Test
    fun sleep_from_screen_off_and_first_unlock() {
        val prev = day.minusDays(1)
        val offs = listOf(at(prev, 21), at(prev, 23, 40), at(day, 2))
        val unlocks = listOf(at(prev, 22), at(day, 1, 55), at(day, 7, 10), at(day, 9))
        val s = StudyEngines.sleepFor(day, offs, unlocks, utc)!!
        assertThat(s.sleptAt).isEqualTo(at(day, 2))
        assertThat(s.wokeAt).isEqualTo(at(day, 7, 10))
        assertThat(s.minutes).isEqualTo(310)
        assertThat(StudyEngines.sleepFor(day, emptyList(), unlocks, utc)).isNull()
    }

    @Test
    fun deadline_quick_add() {
        val kinds = CampusSettings().deadlineKinds
        val subjects = listOf(Subject(1, "ML"), Subject(2, "Computer Networks", code = "CN"))
        // 2026-01-10 is a Saturday; "fri" = 2026-01-16
        val d = StudyEngines.parseDeadline("ML assignment due fri 11pm", day, kinds, subjects, 5)!!
        assertThat(d.label).isEqualTo("Assignment")
        assertThat(d.subjectId).isEqualTo(1L)
        assertThat(d.date).isEqualTo("2026-01-16")
        assertThat(d.minute).isEqualTo(23 * 60)
        val q = StudyEngines.parseDeadline("CN Quiz tomorrow", day, kinds, subjects, 6)!!
        assertThat(q.label).isEqualTo("Quiz")
        assertThat(q.subjectId).isEqualTo(2L)
        assertThat(q.minute).isEqualTo(23 * 60 + 59)
        assertThat(q.date).isEqualTo("2026-01-11")
    }

    @Test
    fun weekly_review() {
        val data = CampusData(
            scoreHistory = listOf(ScoreEntry(day.toString(), 80, emptyMap()), ScoreEntry(day.minusDays(1).toString(), 60, emptyMap()), ScoreEntry(day.minusDays(9).toString(), 10, emptyMap())),
            wakeLogs = listOf(WakeLog(day.toString(), 1000, 1000 + 60_000), WakeLog(day.minusDays(1).toString(), 1000, 1000 + 60 * 60_000)),
            sleepLog = listOf(SleepEntry(day.toString(), 0, 7 * 3_600_000L))
        )
        val w = StudyEngines.week(data, emptyList(), day)
        assertThat(w.avgScore).isEqualTo(70)
        assertThat(w.bestDay).isEqualTo(day.toString())
        assertThat(w.onTimeWakes).isEqualTo(1)
        assertThat(w.wakeDays).isEqualTo(2)
        assertThat(w.avgSleep).isEqualTo(420)
    }
}
