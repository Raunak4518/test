package com.raunak.daytimeline.campus

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CampusEnginesTest {
    // 2026-01-05 is a Monday
    private val mon = LocalDate.of(2026, 1, 5)
    private val dsa = Subject(1, "DSA")
    private val ml = Subject(2, "ML", countByHours = true)
    private val base = CampusData(
        semester = Semester("Sem 5", "2026-01-05", "2026-01-30"),
        subjects = listOf(dsa, ml),
        slots = listOf(
            TimetableSlot(10, 1, 1, 9 * 60, 10 * 60, "203"),
            TimetableSlot(11, 2, 1, 14 * 60, 16 * 60, "LAB-2", ClassType.LAB),
            TimetableSlot(12, 1, 3, 11 * 60, 12 * 60, "203")
        ),
        // These tests cover class-only planning; meals are covered in MessTest.
        settings = CampusSettings(meals = emptyList()),
        wake = WakeConfig(ignoreBreakfast = true)
    )

    @Test
    fun weekly_occurrences_with_holiday_cancel_reschedule_and_extra() {
        assertThat(AttendanceEngine.occurrences(base, mon).map { it.subjectId }).containsExactly(1L, 2L).inOrder()
        val holiday = base.copy(exceptions = listOf(ScheduleException(1, ExceptionKind.HOLIDAY, mon.toString())))
        assertThat(AttendanceEngine.occurrences(holiday, mon)).isEmpty()

        val changed = base.copy(exceptions = listOf(
            ScheduleException(2, ExceptionKind.CANCEL, mon.toString(), slotId = 11),
            ScheduleException(3, ExceptionKind.RESCHEDULE, mon.toString(), slotId = 10, newDate = mon.plusDays(1).toString(), start = 15 * 60, end = 16 * 60),
            ScheduleException(4, ExceptionKind.EXTRA, mon.plusDays(5).toString(), subjectId = 2, start = 10 * 60, end = 11 * 60)
        ))
        assertThat(AttendanceEngine.occurrences(changed, mon)).isEmpty()
        val tue = AttendanceEngine.occurrences(changed, mon.plusDays(1)).single()
        assertThat(tue.subjectId).isEqualTo(1L)
        assertThat(tue.start).isEqualTo(15 * 60)
        assertThat(tue.source).isEqualTo("Rescheduled")
        assertThat(AttendanceEngine.occurrences(changed, mon.plusDays(5)).single().source).isEqualTo("Extra")
        // Outside the semester nothing happens
        assertThat(AttendanceEngine.occurrences(base, mon.minusDays(7))).isEmpty()
    }

    @Test
    fun attendance_math_safe_skips_and_must_attend() {
        // 4 Mondays + 4 Wednesdays of DSA in the semester (Jan 5–30)
        val marks = mapOf(
            AttendanceEngine.key(mon, 10) to Mark.PRESENT,
            AttendanceEngine.key(mon.plusDays(2), 12) to Mark.PRESENT,
            AttendanceEngine.key(mon.plusDays(7), 10) to Mark.ABSENT,
            AttendanceEngine.key(mon.plusDays(9), 12) to Mark.NO_CLASS
        )
        val data = base.copy(marks = marks)
        val st = AttendanceEngine.subjectStats(data, dsa, mon.plusDays(10))
        assertThat(st.attended).isEqualTo(2)
        assertThat(st.conducted).isEqualTo(3)
        assertThat(st.mustAttend).isEqualTo(1) // (0.75*3-2)/0.25 = 1
        assertThat(st.safeToSkip).isEqualTo(0)
        assertThat(st.remaining).isEqualTo(4) // Jan 19, 21, 26, 28

        val good = AttendanceEngine.subjectStats(base.copy(marks = marks - AttendanceEngine.key(mon.plusDays(7), 10)), dsa.copy(priorAttended = 10, priorConducted = 10), mon.plusDays(10))
        assertThat(good.attended).isEqualTo(12)
        assertThat(good.conducted).isEqualTo(12)
        assertThat(good.safeToSkip).isEqualTo(4) // 12/0.75 - 12
        assertThat(good.unmarked).isEqualTo(1)
    }

    @Test
    fun labs_can_count_by_hours() {
        val data = base.copy(marks = mapOf(AttendanceEngine.key(mon, 11) to Mark.PRESENT))
        val st = AttendanceEngine.subjectStats(data, ml, mon.plusDays(1))
        assertThat(st.attended).isEqualTo(2)
        assertThat(st.conducted).isEqualTo(2)
    }

    @Test
    fun parses_pasted_timetable() {
        val (subjects, slots) = AttendanceEngine.parseTimetable(
            """
            Mon 9-10 DSA L 203
            Mon 2-4pm ML Lab LAB-2
            wed 11:10-12:05 CN T
            Tue 2-3 DSA 204
            garbage line
            """.trimIndent(), emptyList(), 100
        )
        assertThat(subjects.map { it.name }).containsExactly("DSA", "ML", "CN")
        assertThat(slots).hasSize(4)
        assertThat(slots[0].start).isEqualTo(9 * 60)
        assertThat(slots[0].room).isEqualTo("203")
        assertThat(slots[1].type).isEqualTo(ClassType.LAB)
        assertThat(slots[1].start).isEqualTo(14 * 60)
        assertThat(slots[1].room).isEqualTo("LAB-2")
        assertThat(slots[2].type).isEqualTo(ClassType.TUTORIAL)
        assertThat(slots[2].day).isEqualTo(3)
        assertThat(slots[3].start).isEqualTo(14 * 60) // afternoon without am/pm
        assertThat(slots[3].subjectId).isEqualTo(slots[0].subjectId)
    }

    @Test
    fun library_windows_between_classes() {
        val windows = LibraryPlanner.freeWindows(base, mon)
        // Open 9:30–22:00, classes 9–10 and 14–16 with 10-minute buffers
        assertThat(windows).containsExactly(Window(10 * 60 + 10, 13 * 60 + 50), Window(16 * 60 + 10, 22 * 60)).inOrder()
        val sat = LibraryPlanner.freeWindows(base, mon.plusDays(5))
        assertThat(sat).containsExactly(Window(9 * 60 + 30, 17 * 60))
        val blocks = LibraryPlanner.planBlocks(listOf(Window(600, 720)), listOf("DSA", "ML"))
        assertThat(blocks.map { it.title }).containsExactly("DSA", "ML").inOrder()
        assertThat(blocks[0].end - blocks[0].start).isEqualTo(50)
    }

    @Test
    fun wake_plan_follows_first_class_and_free_days() {
        val plan = CampusScheduler.nextWake(base, LocalDateTime.of(2026, 1, 4, 22, 0))!!
        assertThat(plan.at).isEqualTo(LocalDateTime.of(2026, 1, 5, 7, 45))
        assertThat(plan.label).contains("DSA")
        // Tuesday has no classes → free-day wake time
        val tue = CampusScheduler.nextWake(base, LocalDateTime.of(2026, 1, 5, 12, 0))!!
        assertThat(tue.at).isEqualTo(LocalDateTime.of(2026, 1, 6, 8, 30))
        val holiday = base.copy(exceptions = listOf(ScheduleException(1, ExceptionKind.HOLIDAY, "2026-01-05")), wake = WakeConfig(freeDayWake = null))
        assertThat(CampusScheduler.nextWake(holiday, LocalDateTime.of(2026, 1, 4, 22, 0))!!.at.toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 7))
    }

    @Test
    fun sheets_parse_progress_and_spaced_revision() {
        val items = SheetEngine.parse("# Arrays\nTwo Sum | two-sum | E\n- Merge Intervals (https://x.com/m) | M\nBinary Search:\n1. Koko | koko-eating-bananas | H", 1)
        assertThat(items.map { it.section }).containsExactly("Arrays", "Arrays", "Binary Search").inOrder()
        assertThat(items[0].url).isEqualTo("https://leetcode.com/problems/two-sum/")
        assertThat(items[1].url).isEqualTo("https://x.com/m")
        assertThat(items[1].title).isEqualTo("Merge Intervals")
        assertThat(items[2].difficulty).isEqualTo(Difficulty.HARD)

        val today = LocalDate.of(2026, 1, 10)
        val solved = SheetEngine.setStatus(items[0], ItemStatus.SOLVED, today)
        assertThat(solved.nextReview).isEqualTo("2026-01-13")
        val revised = SheetEngine.markRevised(solved, today.plusDays(3))
        assertThat(revised.nextReview).isEqualTo("2026-01-20")
        val sheet = StudySheet(1, "DSA", SheetKind.DSA, listOf(solved, items[1], items[2]))
        val stats = SheetEngine.stats(sheet, today)
        assertThat(stats.done).isEqualTo(1)
        assertThat(stats.doneToday).isEqualTo(1)
        assertThat(stats.streak).isEqualTo(1)
        assertThat(SheetEngine.next(sheet)?.title).isEqualTo("Merge Intervals")
        assertThat(SheetEngine.reviewQueue(listOf(sheet), today.plusDays(3))).hasSize(1)
    }

    @Test
    fun cgpa_and_target() {
        val sems = listOf(
            SemesterResult(1, listOf(Course("Maths", 4, "AA"), Course("Physics", 4, "BB"))),
            SemesterResult(2, listOf(Course("DSA", 4, "AB"), Course("DE", 4, null)))
        )
        assertThat(Cgpa.sgpa(sems[0].courses)).isWithin(1e-9).of(9.0)
        assertThat(Cgpa.cgpa(sems)).isWithin(1e-9).of(9.0)
        assertThat(Cgpa.earnedCredits(sems)).isEqualTo(12)
        assertThat(Cgpa.requiredAverage(sems, 9.5, 12)).isWithin(1e-9).of(10.0)
        assertThat(Cgpa.requiredAverage(sems, 9.9, 4)).isNull()
    }

    @Test
    fun daily_score() {
        val parts = DailyScore.compute(CampusSettings(), true, 3, 4, 150, 300, 3, 3, 120, 180, true)
        assertThat(DailyScore.total(parts)).isEqualTo(82) // 15+15+12.5+15+10+15 of 100
        assertThat(DailyScore.grade(82)).isEqualTo("A")
        // Parts that can't be measured are left out rather than counted as zero
        assertThat(DailyScore.total(DailyScore.compute(CampusSettings(), null, 0, 0, 300, 300, 0, 0, null, 180, null))).isEqualTo(100)
        // Custom weights: a zero weight drops that part entirely
        val custom = CampusSettings(scoreWake = 0, scoreStudy = 50)
        val p2 = DailyScore.compute(custom, false, 0, 0, 300, 300, 0, 0, null, 180, null)
        assertThat(p2.map { it.label }).containsExactly("Deep study")
        assertThat(custom.grade(95)).isEqualTo("S")
        assertThat(CampusSettings(gradeBands = listOf(GradeBand(70, "Beast"), GradeBand(0, "Try again"))).grade(71)).isEqualTo("Beast")
    }

    @Test
    fun editable_settings_drive_the_engines() {
        // Custom revision gaps
        val gaps = listOf(1, 2, 4)
        val today = LocalDate.of(2026, 1, 10)
        val item = SheetEngine.setStatus(SheetItem(1, "S", "T"), ItemStatus.SOLVED, today, gaps)
        assertThat(item.nextReview).isEqualTo("2026-01-11")
        assertThat(SheetEngine.markRevised(item, today, gaps).nextReview).isEqualTo("2026-01-12")
        // Custom grade scale (e.g. O/A+/A) with a max of 10
        val scale = listOf(GradePoint("O", 10.0), GradePoint("A+", 9.0), GradePoint("A", 8.0))
        assertThat(Cgpa.sgpa(listOf(Course("X", 3, "O"), Course("Y", 3, "a+")), scale)).isWithin(1e-9).of(9.5)
        assertThat(Cgpa.pointsFor("AA", scale)).isNull()
        // Library planner buffers and minimum window come from settings
        val tight = base.copy(settings = CampusSettings(walkBufferMinutes = 0, librarySlotMinutes = 300, meals = emptyList()))
        assertThat(LibraryPlanner.freeWindows(tight, mon)).containsExactly(Window(16 * 60, 22 * 60))
        // Afternoon cut-off for pasted times
        val (_, slots) = AttendanceEngine.parseTimetable("Mon 9-10 DSA", emptyList(), 1, afternoonBeforeHour = 10)
        assertThat(slots.single().start).isEqualTo(21 * 60)
        // Placement stages are free text
        val companies = listOf(Company(1, "A", stageLabel = "Shortlisted"), Company(2, "B"))
        val funnel = PlacementStats.funnel(companies, listOf("Wishlist", "Shortlisted"))
        assertThat(funnel["Shortlisted"]).isEqualTo(1)
        assertThat(funnel["Wishlist"]).isEqualTo(1)
    }

    @Test
    fun data_saved_by_older_versions_gets_defaults() {
        val gson = com.google.gson.Gson()
        val old = gson.fromJson("{\"studyGoalMinutes\":240,\"subjects\":[],\"slots\":[],\"exceptions\":[],\"marks\":{},\"deadlines\":[],\"librarySessions\":[],\"wakeLogs\":[]}", CampusData::class.java).normalized()
        assertThat(old.studyGoalMinutes).isEqualTo(240)
        assertThat(old.settings.reviewGaps).containsExactly(3, 7, 15, 30, 60).inOrder()
        assertThat(old.settings.batteryCheckEveryMinutes).isEqualTo(30)
        val partial = gson.fromJson("{\"settings\":{\"focusBlockMinutes\":45}}", CampusData::class.java).normalized()
        assertThat(partial.settings.focusBlockMinutes).isEqualTo(45)
        assertThat(partial.settings.deadlineKinds).isNotEmpty()
        assertThat(partial.settings.missedAlarmRecoveryHours).isEqualTo(3)
        val disc = gson.fromJson("{\"started\":5,\"streakStart\":5}", DisciplineState::class.java).normalized()
        assertThat(disc.settings.triggers).isNotEmpty()
        assertThat(disc.settings.urgeTimerMinutes).isEqualTo(10)
        assertThat(disc.urges).isEmpty()
        val deadline = Deadline(1, "x", DeadlineKind.MIDSEM, "2026-01-01")
        assertThat(deadline.label).isEqualTo("Mid-sem")
        assertThat(deadline.copy(kindLabel = "Viva").label).isEqualTo("Viva")
    }

    @Test
    fun discipline_streaks_and_patterns() {
        val day = 86_400_000L
        val start = 1_000 * day
        val s = DisciplineState(started = start, streakStart = start + 10 * day, resets = listOf(ResetLog(start + 10 * day, "Bored", "")),
            urges = listOf(UrgeLog(start + 11 * day + 23 * 3_600_000L, 4, "Alone late at night", true), UrgeLog(start + 12 * day + 23 * 3_600_000L, 3, "Alone late at night", false)))
        val ins = DisciplineEngine.insights(s, start + 15 * day, java.time.ZoneOffset.UTC)
        assertThat(ins.currentDays).isEqualTo(5)
        assertThat(ins.bestDays).isEqualTo(10)
        assertThat(ins.nextMilestone).isEqualTo(7)
        assertThat(ins.resistRate).isEqualTo(50)
        assertThat(ins.riskiestHours).contains(23)
        assertThat(ins.topTriggers.first().first).isEqualTo("Alone late at night")
        assertThat(DisciplineEngine.inRiskWindow(s, 23 * 60 + 30)).isTrue()
        assertThat(DisciplineEngine.inRiskWindow(s, 12 * 60)).isFalse()
    }
}
