package com.raunak.daytimeline.campus

import java.time.LocalDate

enum class ClassType(val label: String, val short: String) { LECTURE("Lecture", "L"), LAB("Lab", "P"), TUTORIAL("Tutorial", "T") }

data class Subject(
    val id: Long,
    val name: String,
    val code: String = "",
    val faculty: String = "",
    val colorHex: Long = 0xFF55786A,
    val credits: Int = 4,
    val requiredPercent: Int = 75,
    /** Count a class by its length in hours (2-hour lab = 2) instead of once per session. */
    val countByHours: Boolean = false,
    /** Classes attended/held before tracking started, so mid-semester setup stays accurate. */
    val priorAttended: Int = 0,
    val priorConducted: Int = 0
)

/** A weekly repeating class. [day] is ISO 1 = Monday … 7 = Sunday; times are minutes of day. */
data class TimetableSlot(
    val id: Long,
    val subjectId: Long,
    val day: Int,
    val start: Int,
    val end: Int,
    val room: String = "",
    val type: ClassType = ClassType.LECTURE,
    /** First date this weekly slot applies (null = from semester start). */
    val validFrom: String? = null,
    /** Last date this weekly slot applies (null = until semester end). */
    val validUntil: String? = null
) {
    fun activeOn(date: LocalDate): Boolean {
        val d = date.toString()
        return (validFrom == null || d >= validFrom) && (validUntil == null || d <= validUntil)
    }
}

/** One weekly slot being edited in the subject week editor. */
data class SlotDraft(val day: Int, val start: Int, val end: Int, val room: String = "", val type: ClassType = ClassType.LECTURE)

enum class ExceptionKind { HOLIDAY, CANCEL, RESCHEDULE, EXTRA }

/**
 * A one-off change to the weekly timetable.
 * - HOLIDAY: no classes on [date].
 * - CANCEL: slot [slotId] does not happen on [date].
 * - RESCHEDULE: slot [slotId] on [date] moves to [newDate] [start]–[end].
 * - EXTRA: an additional class of [subjectId] on [date] [start]–[end].
 */
data class ScheduleException(
    val id: Long,
    val kind: ExceptionKind,
    val date: String,
    val slotId: Long? = null,
    val subjectId: Long? = null,
    val newDate: String? = null,
    val start: Int = 0,
    val end: Int = 0,
    val room: String = "",
    val type: ClassType = ClassType.LECTURE,
    val note: String = ""
)

enum class Mark(val label: String, val counts: Boolean, val attended: Boolean) {
    PRESENT("Present", true, true),
    LATE("Late", true, true),
    ABSENT("Absent", true, false),
    /** Class did not happen after all (teacher absent, etc.). */
    NO_CLASS("No class", false, false)
}

data class ClassOccurrence(
    val key: String,
    val date: LocalDate,
    val subjectId: Long,
    val start: Int,
    val end: Int,
    val room: String,
    val type: ClassType,
    val slotId: Long?,
    val source: String,
    val note: String = ""
) {
    val hours: Int get() = ((end - start + 30) / 60).coerceAtLeast(1)
}

data class Semester(
    val name: String = "Semester",
    val start: String = LocalDate.now().toString(),
    val end: String = LocalDate.now().plusMonths(4).toString()
)

enum class DeadlineKind(val label: String) { ASSIGNMENT("Assignment"), QUIZ("Quiz"), MIDSEM("Mid-sem"), ENDSEM("End-sem"), LAB("Lab record"), PROJECT("Project"), PRESENTATION("Presentation"), OTHER("Other") }

data class Deadline(
    val id: Long,
    val title: String,
    val kind: DeadlineKind,
    val date: String,
    val minute: Int = 23 * 60 + 59,
    val subjectId: Long? = null,
    val done: Boolean = false,
    val notes: String = "",
    /** Editable kind name (from Settings → deadline types); older entries fall back to [kind]. */
    val kindLabel: String? = null
) {
    val label: String get() = kindLabel ?: kind.label
}

data class LibraryHours(
    val weekdayOpen: Int = 9 * 60 + 30,
    val weekdayClose: Int = 22 * 60,
    val weekendOpen: Int = 9 * 60 + 30,
    val weekendClose: Int = 17 * 60,
    val saturdayIsWeekend: Boolean = true,
    val closedOnHolidays: Boolean = false
)

data class LibrarySession(val start: Long, val end: Long?)

/** Wake-up automation for the first class of each day. */
data class WakeConfig(
    val enabled: Boolean = true,
    /** Alarm this many minutes before the first class. */
    val minutesBeforeFirstClass: Int = 75,
    /** Wake time on days without classes (null = no alarm), minutes of day. */
    val freeDayWake: Int? = 8 * 60 + 30,
    val missions: List<String> = listOf("WALK", "MATH"),
    val backupMinutes: Int = 4,
    val wakeCheckMinutes: Int = 10,
    /** Sleep reminder this many hours before the alarm. */
    val sleepHours: Int = 7,
    /** Warn when battery is under this % at night and the phone isn't charging. */
    val batteryThreshold: Int = 50,
    /** "Leave now" reminder this many minutes before a class. */
    val leaveMinutes: Int = 12,
    val classReminderMinutes: Int = 10,
    val askAttendanceAfterClass: Boolean = true
)

data class WakeLog(val date: String, val target: Long, val dismissedAt: Long?)

/** Timed study on a subject or topic (Campus → Today → Study timer). */
data class StudySession(val start: Long, val end: Long?, val subjectId: Long?, val topic: String)

/** An internal assessment: minor, mid-sem, quiz, assignment… with its weight in the final grade. */
data class Assessment(val id: Long, val subjectId: Long, val name: String, val maxMarks: Double, val obtained: Double?, val weightPercent: Double)

/** One day's final score, saved for trends and weekly reviews. */
data class ScoreEntry(val date: String, val score: Int, val parts: Map<String, Int>)

/** Estimated night of sleep from screen-off / first unlock. */
data class SleepEntry(val date: String, val sleptAt: Long, val wokeAt: Long) { val minutes: Int get() = ((wokeAt - sleptAt) / 60_000L).toInt() }

data class GradeCutoff(val minPercent: Int, val grade: String)

data class GradePoint(val letter: String, val points: Double)
data class GradeBand(val minScore: Int, val letter: String)

/** Every tunable number and list used by Campus, editable in Campus → Settings. */
data class CampusSettings(
    /** Spaced-revision gaps in days: first after solving, then after each revision. */
    val reviewGaps: List<Int> = listOf(3, 7, 15, 30, 60),
    val focusBlockMinutes: Int = 50,
    val breakMinutes: Int = 10,
    /** Walking time kept free before and after each class when finding library slots. */
    val walkBufferMinutes: Int = 10,
    /** Only free gaps at least this long count as library time. */
    val librarySlotMinutes: Int = 90,
    /** Split each library slot into focus blocks with breaks instead of one long session. */
    val splitLibraryIntoBlocks: Boolean = false,
    val libraryCloseReminderMinutes: Int = 30,
    /** A library visit counts toward "days this month" after this many minutes. */
    val libraryVisitMinutes: Int = 30,
    val deadlineReminderHours: List<Int> = listOf(24, 3),
    val companyReminderHours: List<Int> = listOf(18, 2),
    val deadlineKinds: List<String> = listOf("Assignment", "Quiz", "Mid-sem", "End-sem", "Lab record", "Project", "Presentation", "Viva", "Other"),
    val examKinds: List<String> = listOf("Quiz", "Mid-sem", "End-sem", "Viva"),
    val placementStages: List<String> = listOf("Wishlist", "Applied", "Online test", "Interview", "HR", "Offer", "Rejected"),
    val scoreWake: Int = 15,
    val scoreClasses: Int = 20,
    val scoreStudy: Int = 25,
    val scoreProblems: Int = 15,
    val scoreScreen: Int = 10,
    val scoreDiscipline: Int = 15,
    val onTimeToleranceMinutes: Int = 10,
    val gradeBands: List<GradeBand> = listOf(GradeBand(90, "S"), GradeBand(80, "A"), GradeBand(65, "B"), GradeBand(50, "C"), GradeBand(35, "D"), GradeBand(0, "F")),
    /** Attendance within this many points above the requirement shows as a warning. */
    val attendanceMargin: Int = 5,
    /** In pasted timetables, times without am/pm before this hour are read as afternoon. */
    val afternoonBeforeHour: Int = 8,
    val lockInMinutes: List<Int> = listOf(60, 120, 180),
    /** Predicted grade from weighted internal marks (percent → grade). */
    val gradeCutoffs: List<GradeCutoff> = listOf(GradeCutoff(90, "AA"), GradeCutoff(80, "AB"), GradeCutoff(70, "BB"), GradeCutoff(60, "BC"), GradeCutoff(50, "CC"), GradeCutoff(45, "CD"), GradeCutoff(40, "DD"), GradeCutoff(0, "FF")),
    /** Days before an exam kept free for full revision when spreading topics out. */
    val examBufferDays: Int = 2,
    val sleepGoalMinutes: Int = 7 * 60 + 30,
    /** Ring on boot if the phone was off at wake time, up to this many hours late. */
    val missedAlarmRecoveryHours: Int = 3,
    val batteryCheckEveryMinutes: Int = 30,
    val wakeSnoozes: Int = 1,
    val wakeSnoozeMinutes: Int = 5,
    val wakeHoldToDismissSeconds: Int = 5,
    val gradeScale: List<GradePoint> = listOf(
        GradePoint("AA", 10.0), GradePoint("AB", 9.0), GradePoint("BB", 8.0), GradePoint("BC", 7.0),
        GradePoint("CC", 6.0), GradePoint("CD", 5.0), GradePoint("DD", 4.0), GradePoint("FF", 0.0)
    )
) {
    /** Fills anything missing from data saved by an older version (Gson leaves such fields null/0). */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun normalized(): CampusSettings {
        val d = CampusSettings()
        fun pos(v: Int, def: Int) = if (v > 0) v else def
        return copy(
            reviewGaps = (reviewGaps ?: d.reviewGaps).filter { it > 0 }.ifEmpty { d.reviewGaps },
            focusBlockMinutes = pos(focusBlockMinutes, d.focusBlockMinutes),
            breakMinutes = if (breakMinutes >= 0) breakMinutes else d.breakMinutes,
            librarySlotMinutes = pos(librarySlotMinutes, d.librarySlotMinutes),
            libraryCloseReminderMinutes = if (libraryCloseReminderMinutes >= 0) libraryCloseReminderMinutes else d.libraryCloseReminderMinutes,
            libraryVisitMinutes = pos(libraryVisitMinutes, d.libraryVisitMinutes),
            deadlineReminderHours = deadlineReminderHours ?: d.deadlineReminderHours,
            companyReminderHours = companyReminderHours ?: d.companyReminderHours,
            deadlineKinds = (deadlineKinds ?: d.deadlineKinds).ifEmpty { d.deadlineKinds },
            examKinds = examKinds ?: d.examKinds,
            placementStages = (placementStages ?: d.placementStages).ifEmpty { d.placementStages },
            gradeBands = (gradeBands ?: d.gradeBands).ifEmpty { d.gradeBands },
            lockInMinutes = (lockInMinutes ?: d.lockInMinutes).filter { it > 0 }.ifEmpty { d.lockInMinutes },
            missedAlarmRecoveryHours = pos(missedAlarmRecoveryHours, d.missedAlarmRecoveryHours),
            batteryCheckEveryMinutes = pos(batteryCheckEveryMinutes, d.batteryCheckEveryMinutes),
            wakeSnoozeMinutes = pos(wakeSnoozeMinutes, d.wakeSnoozeMinutes),
            wakeHoldToDismissSeconds = pos(wakeHoldToDismissSeconds, d.wakeHoldToDismissSeconds),
            gradeScale = (gradeScale ?: d.gradeScale).ifEmpty { d.gradeScale },
            gradeCutoffs = (gradeCutoffs ?: d.gradeCutoffs).ifEmpty { d.gradeCutoffs },
            examBufferDays = if (examBufferDays >= 0) examBufferDays else d.examBufferDays,
            sleepGoalMinutes = if (sleepGoalMinutes > 0) sleepGoalMinutes else d.sleepGoalMinutes
        )
    }

    fun grade(score: Int): String = gradeBands.sortedByDescending { it.minScore }.firstOrNull { score >= it.minScore }?.letter ?: "–"
    fun isExam(kind: String) = examKinds.any { it.equals(kind, true) }
}

data class CampusData(
    val semester: Semester = Semester(),
    val subjects: List<Subject> = emptyList(),
    val slots: List<TimetableSlot> = emptyList(),
    val exceptions: List<ScheduleException> = emptyList(),
    val marks: Map<String, Mark> = emptyMap(),
    val deadlines: List<Deadline> = emptyList(),
    val library: LibraryHours = LibraryHours(),
    val librarySessions: List<LibrarySession> = emptyList(),
    val wake: WakeConfig = WakeConfig(),
    val wakeLogs: List<WakeLog> = emptyList(),
    val studyGoalMinutes: Int = 300,
    val settings: CampusSettings = CampusSettings(),
    val studySessions: List<StudySession> = emptyList(),
    val assessments: List<Assessment> = emptyList(),
    val scoreHistory: List<ScoreEntry> = emptyList(),
    val sleepLog: List<SleepEntry> = emptyList()
) {
    /** Upgrades data saved by older versions so new fields have their defaults. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun normalized() = copy(
        settings = (settings ?: CampusSettings()).normalized(),
        subjects = subjects ?: emptyList(), slots = slots ?: emptyList(), exceptions = exceptions ?: emptyList(), marks = marks ?: emptyMap(),
        deadlines = deadlines ?: emptyList(), librarySessions = librarySessions ?: emptyList(), wakeLogs = wakeLogs ?: emptyList(),
        library = library ?: LibraryHours(), wake = wake ?: WakeConfig(), semester = semester ?: Semester(),
        studySessions = studySessions ?: emptyList(), assessments = assessments ?: emptyList(),
        scoreHistory = scoreHistory ?: emptyList(), sleepLog = sleepLog ?: emptyList()
    )
}
