package com.raunak.daytimeline.pro

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmbientAndLocationTest {
    @Test
    fun every_sound_produces_bounded_non_silent_audio() {
        AmbientSound.values().forEach { sound ->
            val generator = AmbientNoiseGenerator(sound)
            val buffer = generator.fill(ShortArray(44_100), 1f)
            assertThat(buffer.any { it.toInt() != 0 }).isTrue()
            val silent = generator.fill(ShortArray(1_000), 0f)
            assertThat(silent.all { it.toInt() == 0 }).isTrue()
        }
    }

    @Test
    fun distance_is_accurate() {
        // Surat railway station to SVNIT, roughly 7 km
        val d = LocationReminderRules.distanceMeters(21.2049, 72.8411, 21.1634, 72.7853)
        assertThat(d).isWithin(700.0).of(7_300.0)
    }

    @Test
    fun trigger_rules_and_cooldown() {
        val r = LocationReminder(1, "Submit assignment", "College", 0.0, 0.0, trigger = PlaceTrigger.ARRIVE)
        assertThat(LocationReminderRules.shouldNotify(r, entering = true, nowMillis = 1_000_000_000)).isTrue()
        assertThat(LocationReminderRules.shouldNotify(r, entering = false, nowMillis = 1_000_000_000)).isFalse()
        val fired = LocationReminderRules.afterFired(r, 1_000_000_000)
        assertThat(LocationReminderRules.shouldNotify(fired, true, 1_000_000_000 + 60_000)).isFalse()
        val once = LocationReminderRules.afterFired(r.copy(repeat = false), 5)
        assertThat(once.enabled).isFalse()
    }
}
