package com.raunak.daytimeline.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.raunak.daytimeline.alarm.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class AlarmScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun save(view: android.view.View, name: String) {
        compose.waitForIdle()
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File("build/screens").apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun alarm_list_editor_and_ringing() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = AlarmPersistentStore(ctx)
        store.save(AlarmPresets.heavySleeper(6, 45).copy(id = 1, repeatDays = setOf(2, 3, 4, 5, 6)).toPersistent())
        store.save(AlarmPersistentConfig(2, 8, 30, "Weekend", repeatDays = setOf(1, 7), missionChain = listOf(AlarmMissionCatalog.default(AlarmMissionType.TYPING)), enabled = false))
        compose.setContent { ChronoraThemeBase(false) { Surface(Modifier.fillMaxSize()) { AlarmCenter(ctx) {} } } }
        save(compose.activity.window.decorView, "alarm-list")
        compose.onAllNodesWithText("Heavy sleeper")[0].performClick()
        save(compose.activity.window.decorView, "alarm-editor")
        val intent = android.content.Intent(ctx, AlarmRingingActivity::class.java).putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, 1L).putExtra(AlarmRingingActivity.EXTRA_TEST_MODE, true)
        ActivityScenario.launch<AlarmRingingActivity>(intent).use { sc ->
            sc.onActivity { a ->
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                val v = a.window.decorView
                v.measure(android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2580, android.view.View.MeasureSpec.EXACTLY))
                v.layout(0, 0, 1200, 2580)
                val bmp = Bitmap.createBitmap(1200, 2580, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                File("build/screens").apply { mkdirs() }.resolve("alarm-ringing.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
