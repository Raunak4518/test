package com.raunak.daytimeline.wellbeing

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.filter.WebFilterStore
import com.raunak.daytimeline.pro.FocusGuardConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnlockLimitTest {
    private val now = LocalDateTime.of(2026, 3, 11, 15, 0)

    @Test
    fun unlock_limit_blocks_after_limit_except_allowed() {
        val c = WellbeingConfig(unlockLimit = 40, bedtime = BedtimeConfig(allowedPackages = setOf("com.whatsapp")))
        assertThat(WellbeingEngine.decide(c, emptyMap(), "com.instagram.android", now, 0, UsageSnapshot(unlocksToday = 40))).isEqualTo(WellbeingDecision.Allow)
        assertThat(WellbeingEngine.decide(c, emptyMap(), "com.instagram.android", now, 0, UsageSnapshot(unlocksToday = 41))).isInstanceOf(WellbeingDecision.Block::class.java)
        assertThat(WellbeingEngine.decide(c, emptyMap(), "com.whatsapp", now, 0, UsageSnapshot(unlocksToday = 41))).isEqualTo(WellbeingDecision.Allow)
        assertThat(WellbeingEngine.decide(c.copy(unlockLimit = 0), emptyMap(), "x", now, 0, UsageSnapshot(unlocksToday = 999))).isEqualTo(WellbeingDecision.Allow)
    }

    @Test
    fun unlocks_are_counted_per_day() {
        val store = WellbeingStore(ApplicationProvider.getApplicationContext())
        store.recordUnlock(); store.recordUnlock()
        assertThat(store.unlocksToday()).isEqualTo(2)
    }

    @Test
    fun filter_query_stats_accumulate() {
        val store = WebFilterStore(ApplicationProvider.getApplicationContext())
        store.addQueryStats(10, mapOf("a" to 4, "b" to 6))
        store.addQueryStats(5, mapOf("a" to 5))
        assertThat(store.queriesToday()).isEqualTo(15)
        assertThat(store.appQueriesToday()).containsExactly("a", 9, "b", 6)
    }

    @Test
    fun old_guard_config_has_empty_phrase() {
        val c = com.google.gson.Gson().fromJson("{\"lockedMode\":true}", FocusGuardConfig::class.java).normalized()
        assertThat(c.unlockPhrase).isEmpty()
        assertThat(c.lockedMode).isTrue()
    }
}
