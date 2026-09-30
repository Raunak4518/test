package com.raunak.daytimeline.campus

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Starts the real activity: the app must open on the Home timeline without crashing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opens_on_home() {
        compose.waitForIdle()
        assertThat(compose.onAllNodesWithText("Home").fetchSemanticsNodes()).isNotEmpty()
        assertThat(compose.onAllNodesWithText("Campus").fetchSemanticsNodes()).isNotEmpty()
        assertThat(compose.onAllNodesWithText("Chronora").fetchSemanticsNodes()).isNotEmpty()
    }
}
