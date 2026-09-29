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
    }

    @Test fun light() = shoot(false)
    @Test fun dark() = shoot(true)
}
