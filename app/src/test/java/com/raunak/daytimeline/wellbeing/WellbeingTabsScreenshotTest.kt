package com.raunak.daytimeline.wellbeing

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.pro.FocusGuardStore
import com.raunak.daytimeline.ui.ChronoraThemeBase
import com.raunak.daytimeline.ui.Palette
import com.raunak.daytimeline.ui.SectionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the blocker, limits, bedtime and web filter tabs and saves screenshots (build/screens/). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h2400dp-xxhdpi")
class WellbeingTabsScreenshotTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { ChronoraThemeBase(false) { SectionTheme(Palette.sky) { content() } } }
        compose.mainClock.advanceTimeBy(2500)
        compose.waitForIdle()
        val dir = File("build/screens").apply { mkdirs() }
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    @Test fun blocker() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        FocusGuardStore(ctx).update { it.copy(blockedPackages = setOf("com.instagram.android", "com.google.android.youtube"), blockedSites = setOf("reddit.com"),
            schedules = listOf(com.raunak.daytimeline.pro.BlockSchedule(1, "Study hours", setOf(1, 2, 3, 4, 5), 9 * 60, 13 * 60))) }
        shot("tab-blocker") { BlockerTab() }
        assertThat(compose.onAllNodesWithText("Study hours").fetchSemanticsNodes()).isNotEmpty()
        assertThat(compose.onAllNodesWithText("09:00–13:00 · Weekdays").fetchSemanticsNodes()).isNotEmpty()
    }

    @Test fun limits() { shot("tab-limits") { LimitsTab() }; assertThat(compose.onAllNodesWithText("App timers").fetchSemanticsNodes()).isNotEmpty() }

    @Test fun bedtime() {
        shot("tab-bedtime") { BedtimeTab() }
        // Clock times are shown as clock times, not as durations.
        assertThat(compose.onAllNodesWithText("23:00").fetchSemanticsNodes()).isNotEmpty()
        assertThat(compose.onAllNodesWithText("23h 0m").fetchSemanticsNodes()).isEmpty()
    }

    @Test fun webFilter() { shot("tab-webfilter") { com.raunak.daytimeline.filter.WebFilterScreen() }; assertThat(compose.onAllNodesWithText("What to block").fetchSemanticsNodes()).isNotEmpty() }

    @Test fun trackers() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.raunak.daytimeline.trackers.TrackerStore.get(ctx)
        val today = java.time.LocalDate.now()
        val created = today.minusDays(30).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val read = com.raunak.daytimeline.trackers.Tracker(created, "Reading", "📖", 0xFF7C5CE6, "Mind", why = "Become a better engineer")
        val walk = com.raunak.daytimeline.trackers.Tracker(created + 1, "Morning walk", "🚶", 0xFF0E9F9A, "Health")
        store.saveAll(listOf(read, walk))
        (1L..8L).forEach { store.log(read, today.minusDays(it), 1.0) }
        (2L..5L).forEach { store.log(walk, today.minusDays(it), 1.0) }
        shot("tab-trackers") { SectionTheme(Palette.teal) { com.raunak.daytimeline.trackers.TrackersScreen() } }
        // Reading has an 8-day streak at risk today; the walk missed yesterday and can be rescued.
        assertThat(compose.onAllNodesWithText("🔥 Do it today to keep your 8-day streak").fetchSemanticsNodes()).isNotEmpty()
        assertThat(compose.onAllNodesWithText("Use a freeze").fetchSemanticsNodes()).isNotEmpty()
    }

    @Test fun webFilterOn() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.raunak.daytimeline.filter.WebFilterStore(ctx)
        store.config = store.config.copy(enabled = true)
        shot("tab-webfilter-on") { com.raunak.daytimeline.filter.WebFilterScreen() }
        assertThat(compose.onAllNodesWithText("ON", substring = true).fetchSemanticsNodes()).isNotEmpty()
    }

    @Test fun celebration() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.raunak.daytimeline.trackers.TrackerStore.get(ctx)
        val today = java.time.LocalDate.now()
        val created = today.minusDays(30).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val walk = com.raunak.daytimeline.trackers.Tracker(created + 7, "Morning walk", "🚶", 0xFF0E9F9A, "Health")
        store.save(walk)
        (1L..6L).forEach { store.log(walk, today.minusDays(it), 1.0) }
        val before = com.raunak.daytimeline.trackers.TrackerEngine.streak(walk, store.entries.value, today)
        store.log(walk, today, 1.0)
        com.raunak.daytimeline.trackers.afterLog(store, walk, before, today, "done")
        val win = com.raunak.daytimeline.trackers.TrackerCelebration.event.value
        // Completing it fires the full-screen win with the new 7-day streak (a milestone).
        assertThat(win).isNotNull()
        assertThat(win!!.streak).isEqualTo(7)
        assertThat(win.milestone).isTrue()
        shot("celebration") { com.raunak.daytimeline.trackers.CelebrationScreen(win) {} }
        compose.mainClock.advanceTimeBy(3000)
        assertThat(compose.onAllNodesWithText("Keep going 🔥").fetchSemanticsNodes()).isNotEmpty()
        com.raunak.daytimeline.trackers.TrackerCelebration.event.value = null
    }

    @Test fun money() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.raunak.daytimeline.money.MoneyStore.get(ctx)
        val today = java.time.LocalDate.now()
        store.update { d ->
            fun s(a: Double, days: Long, cat: Long, note: String) = com.raunak.daytimeline.money.Txn(store.nextId() + days * 7 + cat, a, categoryId = cat, walletId = 1, date = today.minusDays(days).toString(), minute = 600, note = note)
            d.copy(txns = listOf(s(60.0, 0, 1, "Canteen"), s(15.0, 0, 2, "Chai"), s(240.0, 1, 7, "Movie"), s(80.0, 2, 3, "Auto"), s(450.0, 4, 1, "Dominos"), s(120.0, 6, 5, "Notebook")),
                debts = listOf(com.raunak.daytimeline.money.Debt(1, "Aman", 150.0, "Dominos", today.toString())),
                detected = listOf(com.raunak.daytimeline.money.Detected(9, 99.0, "Zepto", "GPay", System.currentTimeMillis())))
        }
        shot("money") { SectionTheme(Palette.green) { com.raunak.daytimeline.money.MoneyScreen() } }
        assertThat(compose.onAllNodesWithText("Safe to spend today").fetchSemanticsNodes()).isNotEmpty()
    }
}
