package com.raunak.daytimeline.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.raunak.daytimeline.campus.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Renders key screens in light and dark so the design system can be checked by eye (build/screens/). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun seed() {
        val store = CampusStore.get(ApplicationProvider.getApplicationContext())
        val today = LocalDate.now()
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
        val (subjects, slots) = AttendanceEngine.parseTimetable(
            days.joinToString("\n") { "$it 9-10 DSA L 203\n$it 10-11 ML L 204\n$it 2-4pm CN Lab LAB-2" }, emptyList(), store.nextId()
        )
        store.update { it.copy(semester = Semester("Sem 5", today.minusDays(30).toString(), today.plusDays(60).toString()), subjects = subjects, slots = slots) }
        store.updateSemesters { listOf(SemesterResult(1, listOf(Course("Maths", 4, "AB"), Course("Physics", 4, "BB"))), SemesterResult(2, listOf(Course("DSA", 4, "AA"), Course("OS", 3, "AB")))) }
        store.updateCompanies { listOf(Company(1, "Google", "SWE intern", "₹1.2L/mo", stageLabel = "Applied", excitement = 5, minCgpa = 7.0, nextDate = today.plusDays(2).toString(), nextEvent = "OA"),
            Company(2, "Amazon", "SDE", stageLabel = "Interview", excitement = 4, prep = "[x] LP stories\n[ ] System design"), Company(3, "Flipkart", "SDE 1", stageLabel = "Wishlist", applyBy = today.plusDays(4).toString())) }
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val zone = java.time.ZoneId.systemDefault()
        fun at(d: Long, h: Int) = today.plusDays(d).atTime(h, 59).atZone(zone).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()
        com.raunak.daytimeline.classroom.ClassroomSync.ingest(ctx, listOf(
            com.raunak.daytimeline.classroom.ClassItem("w1", "API", com.raunak.daytimeline.classroom.ClassKind.QUIZ, "ML", "Quiz 2: Regression", dueAt = at(1, 10), postedAt = nowMs - 3_600_000, state = com.raunak.daytimeline.classroom.WorkState.PENDING),
            com.raunak.daytimeline.classroom.ClassItem("w2", "API", com.raunak.daytimeline.classroom.ClassKind.ASSIGNMENT, "CN Lab", "Lab 4 – Socket programming", dueAt = at(4, 23), postedAt = nowMs - 86_400_000, state = com.raunak.daytimeline.classroom.WorkState.PENDING),
            com.raunak.daytimeline.classroom.ClassItem("w3", "API", com.raunak.daytimeline.classroom.ClassKind.ASSIGNMENT, "DSA", "Tree traversal problems", dueAt = at(-1, 23), postedAt = nowMs - 3 * 86_400_000, state = com.raunak.daytimeline.classroom.WorkState.LATE),
            com.raunak.daytimeline.classroom.ClassItem("a1", "API", com.raunak.daytimeline.classroom.ClassKind.ANNOUNCEMENT, "CN", "No class tomorrow", "There will be no class tomorrow due to the department seminar.", postedAt = nowMs - 1_800_000),
            com.raunak.daytimeline.classroom.ClassItem("c1", "NOTIFICATION", com.raunak.daytimeline.classroom.ClassKind.COMMENT, "ML", "Prof. Rao added a private comment", "Please add the loss curves and resubmit.", postedAt = nowMs - 600_000)
        ))
        store.updateSheets { listOf(store.template("dsa.txt", "DSA — 196 must-do problems", SheetKind.DSA)) }
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shoot(dark: Boolean) {
        seed()
        compose.setContent { ChronoraThemeBase(dark) { Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) { CampusScreen() } } }
        val mode = if (dark) "dark" else "light"
        save("campus-today-$mode")
        compose.onAllNodesWithText("Attendance")[0].performClick(); save("campus-attendance-$mode")
        compose.onAllNodesWithText("Timetable")[0].performClick(); save("campus-timetable-$mode")
        compose.onAllNodesWithText("Classroom")[0].performScrollTo().performClick(); save("campus-classroom-$mode")
        compose.onAllNodesWithText("CGPA")[0].performScrollTo().performClick(); save("campus-cgpa-$mode")
        compose.onAllNodesWithText("Placements")[0].performScrollTo().performClick(); save("campus-placements-$mode")
    }

    @Test fun light() = shoot(false)
    @Test fun dark() = shoot(true)
}
