package com.raunak.daytimeline.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.MainActivity
import com.raunak.daytimeline.features.HabitFrequency
import com.raunak.daytimeline.features.OfflineHabit
import com.raunak.daytimeline.features.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Opens the real app on its main screens and saves screenshots (build/screens/). */
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
        val repo = com.raunak.daytimeline.AppContainer(ApplicationProvider.getApplicationContext()).repository
        kotlinx.coroutines.runBlocking {
            repo.quickAdd("Revise DBMS normalisation 7-8:30pm #exam !1", today)
            repo.quickAdd("Submit ML assignment 11pm !1 remind 1h before", today.plusDays(1))
            repo.quickAdd("Gym 6pm daily #health", today)
            repo.quickAdd("LeetCode contest sunday 8am #dsa !2", today)
            repo.quickAdd("Read CN chapter 4 tomorrow 4pm", today)
            repo.quickAdd("Pay hostel fees 10am !1", today.minusDays(2))
        }
        store.saveGoal(OfflineGoal(0, "Solve 300 LeetCode problems", 120, 300, today.plusDays(90).toString(), emptyList(), false, unit = "problems", createdDate = today.minusDays(40).toString(),
            log = (0L..20L).map { GoalLog(today.minusDays(it).toString(), 3) }, why = "Placement season"))
        store.saveGoal(OfflineGoal(0, "Final-year project", 0, 4, today.plusDays(60).toString(), listOf("Literature survey", "Dataset", "Model", "Report"), false, milestoneDone = setOf(0), kind = "PROJECT", color = 0xFF9C6ADE))
        store.saveNote(OfflineNote(0, "Groceries", "[ ] Milk\n[x] Eggs\n[ ] Maggi\n[ ] Coffee", emptySet(), 0, color = 0xFFFBBC04))
        store.saveNote(OfflineNote(0, "OS viva", "Deadlock: mutual exclusion, hold and wait, no preemption, circular wait. See [[Scheduling]].", setOf("exam"), 0, folder = "College", pinned = true))
        store.saveNote(OfflineNote(0, "Scheduling", "FCFS, SJF, RR (quantum), priority. Convoy effect.", setOf("exam"), 0, folder = "College", color = 0xFFAECBFA))
        (0L..12L).forEach { store.saveJournal(OfflineJournalEntry(today.minusDays(it).toString(), listOf(4, 3, 5, 2, 4)[(it % 5).toInt()], 3, "", "", "", "", activities = listOf("Study", "Gym", "Friends").take((it % 3 + 1).toInt()))) }
        val proj = OfflineProject(0, "Placements", 0xFF5A6CF3, emptyList(), null); store.saveProject(proj)
        store.startTimer("Graphs revision", store.projects.value.firstOrNull()?.id, setOf("dsa"))
        val garden = com.raunak.daytimeline.pro.GardenStore(ApplicationProvider.getApplicationContext())
        val zone = java.time.ZoneId.systemDefault()
        (0L..20L).forEach { d ->
            val day = today.minusDays(d)
            listOf(9 to 50, 14 to 25, 21 to 45).take((d % 3 + 1).toInt()).forEachIndexed { i, (h, m) ->
                garden.add(com.raunak.daytimeline.pro.GardenSession(day.toString(), m, !(d == 3L && i == 1), null, day.atTime(h, 10).atZone(zone).toInstant().toEpochMilli(),
                    listOf("DSA", "Study", "Reading")[i], interruptions = (d % 3).toInt(), rating = (3 + i).coerceAtMost(5)))
            }
        }
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
        fun nav(label: String) = compose.onAllNodes(hasText(label) and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("nav")))[0].performClick()
        fun back() = compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Back"))[0].performClick()
        compose.mainClock.advanceTimeBy(3000); save("home-timeline")
        compose.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction())[0].performScrollToNode(hasText("Revise DBMS", substring = true)); save("home-timeline-evening")
        nav("Plan"); compose.mainClock.advanceTimeBy(1500); save("plan-day")
        compose.onAllNodesWithText("Week")[0].performClick(); save("plan-week")
        compose.onAllNodesWithText("Agenda")[0].performClick(); save("plan-agenda")
        compose.onAllNodesWithText("Matrix")[0].performClick(); save("plan-matrix")
        assertThat(compose.onAllNodesWithText("Do first").fetchSemanticsNodes()).isNotEmpty()
        nav("Focus"); save("home-focus")
        compose.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction())[0].performScrollToNode(hasText("Start focus")); save("home-focus-start")
        compose.onAllNodesWithText("Focus mode")[0].performClick(); save("focus-mode")
        compose.onAllNodesWithText("Stats")[0].performClick(); save("focus-stats")
        compose.onAllNodesWithText("Timer")[0].performClick()
        compose.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction())[0].performScrollToNode(hasText("Start focus"))
        compose.onAllNodesWithText("Start focus")[0].performClick(); compose.mainClock.advanceTimeBy(1500); save("focus-running")
        assertThat(compose.onAllNodesWithText("Start focus").fetchSemanticsNodes()).isEmpty()
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Stop"))[0].performClick()
        compose.onAllNodesWithText("Give up")[0].performClick()
        nav("Campus"); save("campus")
        nav("You"); save("you")
        compose.onAllNodesWithText("Habits")[0].performClick(); save("home-habits")
        assertThat(compose.onAllNodesWithText("Solve 2 DSA problems").fetchSemanticsNodes()).isNotEmpty()
        listOf("Goals", "Notes", "Journal", "Time log").forEach { t -> compose.onAllNodesWithText(t)[0].performClick(); save("home-" + t.lowercase().replace(' ', '-')) }
        back()
        compose.onAllNodesWithText("Screen time")[0].performClick(); save("screen-time")
        compose.onAllNodesWithText("Blocker")[0].performClick(); save("screen-blocker")
        back()
        scenario.close()
    }
}
