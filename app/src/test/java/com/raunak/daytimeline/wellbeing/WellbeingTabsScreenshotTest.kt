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
        compose.mainClock.advanceTimeBy(1500)
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
}
