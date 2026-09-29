package com.raunak.daytimeline.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.features.HabitFrequency
import com.raunak.daytimeline.features.OfflineHabit
import com.raunak.daytimeline.features.OfflineProductivityStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Opens the real app on the Focus, Priority matrix and Habits screens and saves screenshots (build/screens/). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class HomeScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun focus_matrix_and_habits() {
        val store = OfflineProductivityStore(ApplicationProvider.getApplicationContext())
        val today = LocalDate.now()
        store.saveHabit(OfflineHabit(0, "Solve 2 DSA problems", 7, "08:00", (1..7).toSet(), (1L..20L).filter { it % 6 != 0L }.map { today.minusDays(it).toString() }.toSet(), color = 0xFF4E79A7, note = "Placements in 6 months"))
        store.saveHabit(OfflineHabit(0, "Gym", 4, "18:30", (1..7).toSet(), setOf(today.minusDays(1).toString(), today.minusDays(3).toString()), frequency = HabitFrequency.WEEKLY.name, color = 0xFFF28E2B))
        store.saveHabit(OfflineHabit(0, "Call home", 7, "", (1..7).toSet(), setOf(today.minusDays(2).toString()), frequency = HabitFrequency.INTERVAL.name, intervalDays = 3, color = 0xFF9C6ADE))
        var activity: MainActivity? = null
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity = it }
        fun save(name: String) {
            compose.waitForIdle()
            val view = activity!!.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            File("build/screens").apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onAllNodesWithText("Focus")[0].performClick(); save("home-focus")
        assertThat(compose.onAllNodesWithText("Start focus").fetchSemanticsNodes()).isNotEmpty()
        compose.onAllNodes(hasText("Today") and hasClickAction())[0].performClick()
        compose.onAllNodesWithText("Priority matrix")[0].performClick(); save("home-matrix")
        assertThat(compose.onAllNodesWithText("Do first").fetchSemanticsNodes()).isNotEmpty()
        compose.onAllNodesWithText("Productivity")[0].performClick(); save("home-habits")
        assertThat(compose.onAllNodesWithText("Solve 2 DSA problems").fetchSemanticsNodes()).isNotEmpty()
        scenario.close()
    }
}
