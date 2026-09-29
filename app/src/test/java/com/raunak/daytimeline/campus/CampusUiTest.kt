package com.raunak.daytimeline.campus

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Renders every Campus tab with realistic data to catch runtime crashes in the UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CampusUiTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun seed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = CampusStore.get(context)
        val today = LocalDate.now()
        val (subjects, slots) = AttendanceEngine.parseTimetable(
            (1..6).joinToString("\n") { d -> listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[d - 1] + " 9-10 DSA L 203\n" + listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[d - 1] + " 2-4pm ML Lab LAB-2" },
            emptyList(), store.nextId()
        )
        store.update {
            it.copy(
                semester = Semester("Sem 5", today.minusDays(20).toString(), today.plusDays(60).toString()),
                subjects = subjects, slots = slots,
                exceptions = listOf(ScheduleException(store.nextId(), ExceptionKind.EXTRA, today.toString(), subjectId = subjects.first().id, start = 18 * 60, end = 19 * 60)),
                deadlines = listOf(Deadline(store.nextId(), "ML assignment 2", DeadlineKind.ASSIGNMENT, today.plusDays(2).toString(), subjectId = subjects[1].id), Deadline(store.nextId(), "Mid-sem", DeadlineKind.MIDSEM, today.plusDays(9).toString()))
            )
        }
        store.updateSheets { listOf(store.template("dsa.txt", "DSA — 196 must-do problems", SheetKind.DSA)) }
        store.updateCompanies { listOf(Company(store.nextId(), "Acme", "SDE Intern", stage = PlacementStage.OA, nextDate = today.plusDays(1).toString())) }
        store.updateSemesters { listOf(SemesterResult(1, listOf(Course("Maths", 4, "AA")))) }
    }

    @Test
    fun every_tab_renders() {
        compose.setContent { CampusScreen() }
        compose.onNodeWithText("Today's score").assertExists()
        listOf("Attendance", "Timetable", "Sheets", "Exams & tasks", "Wake-up", "Library", "CGPA", "Placements", "Discipline").forEach { tab ->
            compose.onAllNodesWithText(tab)[0].performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Start").assertExists() // Discipline setup (no device lock in tests)
        compose.onAllNodesWithText("Settings")[0].performScrollTo().performClick()
        compose.onNodeWithText("Revision gaps (days) — first after solving, then after each revision").assertExists()
    }

    @Test
    fun attendance_and_timetable_details() {
        compose.setContent { CampusScreen() }
        compose.onAllNodesWithText("Attendance")[0].performClick()
        compose.onNodeWithText("All present").performClick()
        compose.waitForIdle()
        val data = CampusStore.get(ApplicationProvider.getApplicationContext()).data.value
        assertThat(data.marks.values.count { it == Mark.PRESENT }).isGreaterThan(5)
        compose.onAllNodesWithText("Timetable")[0].performClick()
        compose.onNodeWithText("Changes").performClick()
        compose.onNodeWithText("Holidays (date range)").performClick()
        compose.onNodeWithText("Holiday / no classes").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        // Subject week editor opens from the Subjects view
        compose.onNodeWithText("Subjects").performClick()
        compose.onAllNodesWithText("Edit week")[0].performClick()
        compose.onNodeWithText("Weekly schedule").assertExists()
        compose.onNodeWithText("Monday").assertExists()
    }

    @Test
    fun sheet_detail_and_discipline_flow() {
        compose.setContent { CampusScreen() }
        compose.onAllNodesWithText("Sheets")[0].performClick()
        compose.onNodeWithText("DSA — 196 must-do problems").performClick()
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Two Sum"))
        compose.onNodeWithText("Two Sum").assertExists()
        compose.onAllNodesWithText("Discipline")[0].performScrollTo().performClick()
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("I'm having an urge — help me now").assertExists().performClick()
        val sos = compose.onNodeWithTag("sos")
        sos.performScrollToNode(hasText("It passed"))
        compose.onNodeWithText("It passed").performClick()
        sos.performScrollToNode(hasText("Log it — I won"))
        compose.onNodeWithText("Log it — I won").performClick()
        compose.waitForIdle()
        assertThat(DisciplineStore.get(ApplicationProvider.getApplicationContext()).state.value.urges).hasSize(1)
    }
}
