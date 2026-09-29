package com.raunak.daytimeline.campus

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class AttendanceExtrasTest {
    private val mon = LocalDate.of(2026, 1, 5)
    private val dsa = Subject(1, "DSA")
    private val base = CampusData(
        semester = Semester("Sem", "2026-01-05", "2026-01-30"),
        subjects = listOf(dsa),
        slots = listOf(TimetableSlot(10, 1, 1, 9 * 60, 10 * 60, "203"), TimetableSlot(12, 1, 3, 11 * 60, 12 * 60, "203"))
    )

    @Test
    fun only_mark_bunks_counts_unmarked_as_present() {
        val data = base.copy(marks = mapOf(AttendanceEngine.key(mon, 10) to Mark.ABSENT), settings = CampusSettings(assumePresent = true))
        val st = AttendanceEngine.subjectStats(data, dsa, mon.plusDays(10))
        assertThat(st.conducted).isEqualTo(4)
        assertThat(st.attended).isEqualTo(3)
        assertThat(st.unmarked).isEqualTo(0)
        assertThat(AttendanceEngine.unmarked(data, mon.plusDays(10), 0)).isEmpty()
        val off = AttendanceEngine.subjectStats(data.copy(settings = CampusSettings()), dsa, mon.plusDays(10))
        assertThat(off.unmarked).isEqualTo(3)
    }

    @Test
    fun what_if_calculator() {
        val st = AttendanceEngine.subjectStats(base.copy(marks = mapOf(AttendanceEngine.key(mon, 10) to Mark.PRESENT, AttendanceEngine.key(mon.plusDays(2), 12) to Mark.PRESENT, AttendanceEngine.key(mon.plusDays(7), 10) to Mark.PRESENT)), dsa, mon.plusDays(8))
        assertThat(AttendanceEngine.whatIf(st, 0, 1)).isWithin(0.01).of(75.0)
        assertThat(AttendanceEngine.whatIf(st, 0, 2)).isWithin(0.01).of(60.0)
        assertThat(AttendanceEngine.toRecover(st, 0, 2)).isEqualTo(3) // 3/5 → 6/8 = 75%
        assertThat(AttendanceEngine.toRecover(st, 0, 1)).isEqualTo(0)
    }

    @Test
    fun wake_briefing_quote_and_lines() {
        val s = CampusSettings(wakeQuotes = listOf("A", "B"))
        assertThat(WakeBriefing.quote(s, mon)).isNotEqualTo(WakeBriefing.quote(s, mon.plusDays(1)))
        assertThat(WakeBriefing.quote(CampusSettings(wakeQuotes = emptyList()), mon)).isNull()
        val data = base.copy(deadlines = listOf(Deadline(5, "ML report", DeadlineKind.ASSIGNMENT, mon.toString(), 23 * 60)))
        val lines = WakeBriefing.lines(data, mon)
        assertThat(lines.first()).isEqualTo("First class: DSA at 09:00 · 203 · 1 today")
        assertThat(lines[1]).contains("ML report")
        assertThat(WakeBriefing.lines(data.copy(settings = CampusSettings(wakeBriefingOff = true)), mon)).isEmpty()
    }
}
