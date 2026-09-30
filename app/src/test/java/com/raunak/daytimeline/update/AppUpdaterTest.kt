package com.raunak.daytimeline.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun parses_github_release() {
        val json = """{"tag_name":"v142","name":"Chronora 1.0.142","body":"feat: alarms","published_at":"2026-09-30T10:00:00Z",
            "assets":[{"name":"notes.txt","browser_download_url":"x","size":1},{"name":"chronora-142.apk","browser_download_url":"https://github.com/Raunak4518/test/releases/download/v142/chronora-142.apk","size":24000000}]}"""
        val r = AppUpdater.parseRelease(json)!!
        assertThat(r.versionCode).isEqualTo(142)
        assertThat(r.apkUrl).endsWith("chronora-142.apk")
        assertThat(r.sizeBytes).isEqualTo(24000000)
        assertThat(r.notes).isEqualTo("feat: alarms")
        assertThat(AppUpdater.parseRelease("""{"tag_name":"v3","assets":[]}""")).isNull()
        assertThat(AppUpdater.versionFromTag("v17")).isEqualTo(17)
        assertThat(AppUpdater.versionFromTag("nightly")).isNull()
    }
}
