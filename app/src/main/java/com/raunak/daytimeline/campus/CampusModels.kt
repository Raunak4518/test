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
    val type: ClassType = ClassType.LECTURE
)

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
    val notes: String = ""
)

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
    val studyGoalMinutes: Int = 300
)
