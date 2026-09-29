package com.raunak.daytimeline.campus

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class CampusExtrasTest {
    private val mon = LocalDate.of(2026, 1, 5)
    private val dsa = Subject(1, "DSA")
    private val base = CampusData(
        semester = Semester("Sem", "2026-01-05", "2026-02-28"),
        subjects = listOf(dsa),
        slots = listOf(
            TimetableSlot(10, 1, 1, 9 * 60, 10 * 60, rotationWeek = 0),
            TimetableSlot(11, 1, 1, 11 * 60, 12 * 60, rotationWeek = 1),
            TimetableSlot(12, 1, 1, 11 * 60, 12 * 60, "LAB", ClassType.LAB, rotationWeek = 2)
        )
    )

    @Test
    fun rotating_weeks() {
        val ab = base.copy(settings = CampusSettings(rotationWeeks = 2))
        assertThat(AttendanceEngine.rotationWeek(ab, mon)).isEqualTo(1)
        assertThat(AttendanceEngine.rotationWeek(ab, mon.plusDays(7))).isEqualTo(2)
        assertThat(AttendanceEngine.rotationWeek(ab, mon.plusDays(14))).isEqualTo(1)
        assertThat(AttendanceEngine.occurrences(ab, mon).map { it.slotId }).containsExactly(10L, 11L).inOrder()
        assertThat(AttendanceEngine.occurrences(ab, mon.plusDays(7)).map { it.slotId }).containsExactly(10L, 12L).inOrder()
        // Anchor moved: this week becomes A
        val shifted = ab.copy(settings = ab.settings.copy(rotationStart = mon.plusDays(7).toString()))
        assertThat(AttendanceEngine.rotationWeek(shifted, mon.plusDays(7))).isEqualTo(1)
        // Rotation off: every slot every week, A and B slots don't count as clashing with each other
        assertThat(AttendanceEngine.occurrences(base, mon)).hasSize(3)
        assertThat(AttendanceEngine.conflicts(base.slots)).isEmpty()
        assertThat(AttendanceEngine.weekLetter(2)).isEqualTo("B")
    }

    @Test
    fun cgpa_helpers() {
        val sems = listOf(SemesterResult(1, listOf(Course("A", 4, "AA"), Course("B", 4, "BB"))))
        assertThat(Cgpa.percent(9.0, CampusSettings())).isWithin(0.001).of(82.5)
        val (lo, hi) = Cgpa.bounds(sems, 8, Cgpa.defaultScale)!!
        assertThat(hi).isWithin(0.001).of(9.5)
        assertThat(lo).isWithin(0.001).of(6.5)
        assertThat(Cgpa.cutoffs(listOf("Most=7.0", "bad", "Top=8.5"))).containsExactly("Most" to 7.0, "Top" to 8.5).inOrder()
    }

    @Test
    fun placement_stats() {
        val stages = CampusSettings().placementStages
        val today = mon
        val c = Company(1, "X", stageLabel = "Wishlist", applyBy = today.plusDays(3).toString(), minCgpa = 7.5)
        val moved = PlacementStats.move(c, "Applied", today)
        assertThat(PlacementStats.history(moved).single().stage).isEqualTo("Applied")
        assertThat(PlacementStats.move(moved, "Applied", today)).isEqualTo(moved)
        val list = listOf(c, moved.copy(id = 2), Company(3, "Y", stageLabel = "Interview"), Company(4, "Z", stageLabel = "Offer"), Company(5, "W", stageLabel = "Rejected"))
        val s = PlacementStats.summary(list, stages)
        assertThat(s.applied).isEqualTo(4)
        assertThat(s.offers).isEqualTo(1)
        assertThat(s.rejected).isEqualTo(1)
        assertThat(s.responded).isEqualTo(2)
        assertThat(PlacementStats.eligible(c, 7.4)).isFalse()
        assertThat(PlacementStats.eligible(c, 7.5)).isTrue()
        assertThat(PlacementStats.applyDeadlines(list, stages, today).map { it.id }).containsExactly(1L)
        val old = com.google.gson.Gson().fromJson("{\"id\":9,\"name\":\"Old\"}", Company::class.java)
        assertThat(PlacementStats.history(old)).isEmpty()
    }
}

class MessTest {
    private val mon = LocalDate.of(2026, 1, 5)
    private val dsa = Subject(1, "DSA")
    private fun data(vararg slots: TimetableSlot) = CampusData(semester = Semester("Sem", "2026-01-05", "2026-02-28"), subjects = listOf(dsa), slots = slots.toList())

    @Test
    fun default_mess_times_and_status() {
        val d = data()
        assertThat(Mess.meals(d, mon).map { it.name }).containsExactly("Breakfast", "Lunch", "Dinner").inOrder()
        assertThat(Mess.status(d, mon.atTime(13, 15))).startsWith("Lunch open · closes 14:00")
        assertThat(Mess.status(d, mon.atTime(15, 0))).startsWith("Next: Dinner 19:30")
        assertThat(Mess.status(d, mon.atTime(22, 0))).contains("Breakfast tomorrow 08:00")
    }

    @Test
    fun study_windows_keep_time_to_eat() {
        // Class 12:30–13:30 leaves lunch at 13:30–14:00
        val d = data(TimetableSlot(1, 1, 1, 12 * 60 + 30, 13 * 60 + 30))
        val lunch = Mess.reserved(d, mon, AttendanceEngine.occurrences(d, mon).map { com.raunak.daytimeline.campus.Window(it.start, it.end) }).first { it.first.name == "Lunch" }.second
        assertThat(lunch.start).isEqualTo(13 * 60 + 30)
        val free = LibraryPlanner.freeWindows(d, mon, buffer = 0, minMinutes = 1)
        assertThat(free.none { it.start < 14 * 60 && it.end > 13 * 60 + 30 }).isTrue()
        assertThat(Mess.clashes(d, mon)).isEmpty()
        val blocked = data(TimetableSlot(1, 1, 1, 12 * 60 + 30, 14 * 60))
        assertThat(Mess.clashes(blocked, mon).map { it.name }).containsExactly("Lunch")
    }

    @Test
    fun wake_leaves_time_for_breakfast() {
        // 10:30 class: normal wake 09:15, but breakfast closes 09:00 → wake 09:00 − 30 − 25 = 08:05
        val d = data(TimetableSlot(1, 1, 1, 10 * 60 + 30, 11 * 60 + 30))
        val plan = CampusScheduler.nextWake(d, mon.atTime(0, 1))!!
        assertThat(plan.at).isEqualTo(mon.atTime(8, 5))
        assertThat(plan.label).contains("Breakfast")
        val off = d.copy(wake = d.wake.copy(ignoreBreakfast = true))
        assertThat(CampusScheduler.nextWake(off, mon.atTime(0, 1))!!.at).isEqualTo(mon.atTime(9, 15))
        // 9:00 class: 07:45 is already early enough
        val early = data(TimetableSlot(1, 1, 1, 9 * 60, 10 * 60))
        assertThat(CampusScheduler.nextWake(early, mon.atTime(0, 1))!!.at).isEqualTo(mon.atTime(7, 45))
    }
}
