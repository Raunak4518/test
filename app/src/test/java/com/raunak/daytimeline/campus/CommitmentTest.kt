package com.raunak.daytimeline.campus

import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.filter.FilterLock
import com.raunak.daytimeline.filter.WebFilterConfig
import org.junit.Test

class CommitmentTest {
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    @Test
    fun lock_can_only_grow() {
        val on = CommitLock(until = now + 30 * day, started = now, nightAllowed = setOf("a", "b"))
        val shorter = Commitment.tighten(on, on.copy(until = now + day, contentShield = false, nightShield = false, nightAllowed = setOf("a", "b", "c")), now)
        assertThat(shorter.until).isEqualTo(on.until)
        assertThat(shorter.contentShield).isTrue()
        assertThat(shorter.nightShield).isTrue()
        assertThat(shorter.nightAllowed).containsExactly("a", "b")
        assertThat(Commitment.tighten(on, on.copy(until = now + 60 * day), now).until).isEqualTo(now + 60 * day)
        assertThat(Commitment.daysLeft(on, now)).isEqualTo(30)
    }

    @Test
    fun night_window_follows_the_alarm() {
        val c = CommitLock(until = now + day, nightStart = 23 * 60, nightEnd = 6 * 60, nightFromAlarm = true)
        val w = Commitment.nightWindow(c, 6 * 60 + 30, 7 * 60 + 30)
        assertThat(w).isEqualTo(23 * 60 to 6 * 60 + 30)
        assertThat(Commitment.inWindow(w, 1 * 60)).isTrue()
        assertThat(Commitment.inWindow(w, 12 * 60)).isFalse()
        assertThat(Commitment.nightWindow(c.copy(nightFromAlarm = false), 6 * 60 + 30, 450)).isEqualTo(23 * 60 to 6 * 60)
    }

    @Test
    fun word_matching_needs_whole_words() {
        val words = listOf("xxx", "nude", "escort")
        assertThat(Commitment.explicitHits(listOf("Sussex weather today"), words)).isEqualTo(0)
        assertThat(Commitment.explicitHits(listOf("XXX videos", "nude pics"), words)).isEqualTo(2)
        assertThat(Commitment.explicitHits(listOf("escorting the bus"), words)).isEqualTo(0)
    }

    @Test
    fun filter_cannot_loosen_while_locked() {
        val cfg = WebFilterConfig(enabled = true, commitUntil = now + day)
        assertThat(FilterLock.canLoosen(cfg, now)).isFalse()
        assertThat(FilterLock.isLoosening(cfg, cfg.copy(commitUntil = now))).isTrue()
        assertThat(FilterLock.requestUnlock(cfg.copy(lockDelayMinutes = 60), now).pendingUnlockAt).isEqualTo(0L)
        assertThat(FilterLock.canLoosen(cfg.copy(commitUntil = now - 1), now)).isTrue()
    }
}
