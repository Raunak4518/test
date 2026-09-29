package com.raunak.daytimeline.campus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FullBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun round_trip_restores_campus_and_keeps_private_data_out_by_default() = runTest {
        context.getSharedPreferences("chronora_campus", Context.MODE_PRIVATE).edit().putString("data", "{\"studyGoalMinutes\":123}").putInt("n", 7).commit()
        context.getSharedPreferences("chronora_d", Context.MODE_PRIVATE).edit().putString("s", "secret").commit()

        val json = FullBackup.export(context, includePrivate = false)
        assertThat(json).contains("chronora_campus")
        assertThat(json).doesNotContain("secret")

        context.getSharedPreferences("chronora_campus", Context.MODE_PRIVATE).edit().clear().commit()
        val result = FullBackup.import(context, json)
        assertThat(result.isSuccess).isTrue()
        val prefs = context.getSharedPreferences("chronora_campus", Context.MODE_PRIVATE)
        assertThat(prefs.getString("data", null)).isEqualTo("{\"studyGoalMinutes\":123}")
        assertThat(prefs.getInt("n", 0)).isEqualTo(7)

        val withPrivate = FullBackup.export(context, includePrivate = true)
        assertThat(withPrivate).contains("secret")
        assertThat(FullBackup.import(context, "{\"app\":\"Other\"}").isFailure).isTrue()
    }
}
