package com.raunak.daytimeline.campus

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class TimetableEditingTest {
    private val mon = LocalDate.of(2026, 1, 5) // Monday
    private var nextId = 100L
    private fun id() = nextId++

    private val base = CampusData(
        semester = Semester("S", "2026-01-05", "2026-02-27"),
        subjects = listOf(Subject(1, "DSA"), Subject(2, "ML")),
        slots = listOf(
            TimetableSlot(10, 1, 1, 9 * 60, 10 * 60),
            TimetableSlot(11, 1, 1, 11 * 60, 12 * 60),   // two DSA classes on Monday
            TimetableSlot(12, 1, 3, 9 * 60, 10 * 60),
            TimetableSlot(20, 2, 2, 14 * 60, 16 * 60, type = ClassType.LAB)
        )
    )

    @Test
    fun several_classes_per_day_repeat_until_semester_end() {
        val mondays = AttendanceEngine.occurrencesBetween(base, mon, LocalDate.of(2026, 3, 31)).filter { it.subjectId == 1L && it.date.dayOfWeek.value == 1 }
        assertThat(mondays).hasSize(16) // 8 Mondays × 2 classes, none after Feb 27
        assertThat(mondays.maxOf { it.date }).isEqualTo(LocalDate.of(2026, 2, 23))
    }

    @Test
    fun week_edit_from_today_keeps_past_classes_and_marks() {
        val marks = mapOf(AttendanceEngine.key(mon, 11) to Mark.PRESENT)
        val data = base.copy(marks = marks)
        val from = mon.plusWeeks(2) // Jan 19
        val drafts = listOf(
            SlotDraft(1, 9 * 60, 10 * 60),        // unchanged
            SlotDraft(1, 12 * 60, 13 * 60),       // 11:00 moved to 12:00
            SlotDraft(5, 10 * 60, 11 * 60)        // new Friday class
        )                                          // Wednesday removed
        val slots = AttendanceEngine.applyWeekSchedule(data.slots, 1, drafts, from, ::id)
        val updated = data.copy(slots = slots)
        // Unchanged slot keeps its id (and so its attendance)
        assertThat(slots.single { it.id == 10L }.validUntil).isNull()
        // Changed and removed slots end the day before the change
        assertThat(slots.single { it.id == 11L }.validUntil).isEqualTo("2026-01-18")
        assertThat(slots.single { it.id == 12L }.validUntil).isEqualTo("2026-01-18")
        // Past week still shows the old 11:00 class with its mark
        val pastMonday = AttendanceEngine.occurrences(updated, mon).filter { it.subjectId == 1L }
        assertThat(pastMonday.map { it.start }).containsExactly(9 * 60, 11 * 60).inOrder()
        assertThat(updated.marks[pastMonday[1].key]).isEqualTo(Mark.PRESENT)
        // From the change on: 9:00 and 12:00 Monday, Friday 10:00, no Wednesday
        assertThat(AttendanceEngine.occurrences(updated, from).filter { it.subjectId == 1L }.map { it.start }).containsExactly(9 * 60, 12 * 60).inOrder()
        assertThat(AttendanceEngine.occurrences(updated, from.plusDays(2)).filter { it.subjectId == 1L }).isEmpty()
        assertThat(AttendanceEngine.occurrences(updated, from.plusDays(4)).single().start).isEqualTo(10 * 60)
        assertThat(AttendanceEngine.occurrences(updated, mon.plusDays(4)).filter { it.subjectId == 1L }).isEmpty()
        // Other subjects untouched
        assertThat(slots.single { it.id == 20L }).isEqualTo(base.slots.last())
    }

    @Test
    fun week_edit_for_whole_semester_replaces_everywhere() {
        val slots = AttendanceEngine.applyWeekSchedule(base.slots, 1, listOf(SlotDraft(2, 8 * 60, 9 * 60)), null, ::id)
        assertThat(slots.filter { it.subjectId == 1L }.map { it.day }).containsExactly(2)
        assertThat(slots.single { it.subjectId == 1L }.validFrom).isNull()
    }

    @Test
    fun clash_detection() {
        val clashing = base.slots + TimetableSlot(30, 2, 1, 9 * 60 + 30, 10 * 60 + 30)
        val c = AttendanceEngine.conflicts(clashing)
        assertThat(c).hasSize(1)
        assertThat(c.single().first.id).isEqualTo(10L)
        assertThat(AttendanceEngine.conflicts(base.slots)).isEmpty()
    }

    @Test
    fun library_uses_big_free_slots_as_whole_sessions() {
        val data = base.copy(settings = CampusSettings(librarySlotMinutes = 90, meals = emptyList()))
        // Monday: classes 9–10 and 11–12 → the 10:10–10:50 gap is too short; 12:10–22:00 is one slot
        val windows = LibraryPlanner.freeWindows(data, mon)
        assertThat(windows).containsExactly(Window(12 * 60 + 10, 22 * 60))
        val blocks = LibraryPlanner.planBlocks(windows, listOf("DSA practice"), split = false)
        assertThat(blocks).containsExactly(LibraryPlanner.Block(12 * 60 + 10, 22 * 60, "DSA practice"))
    }
}
