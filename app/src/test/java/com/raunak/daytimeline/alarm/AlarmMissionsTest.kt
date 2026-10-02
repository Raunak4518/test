package com.raunak.daytimeline.alarm

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmMissionsTest {
    @Test
    fun math_answers_are_right_at_every_level() {
        val rnd = Random(7)
        (1..5).forEach { d ->
            repeat(50) {
                val p = AlarmChallenges.math(d, rnd)
                val value = p.text.split(" + ").sumOf { term -> term.split(" × ").map { it.trim().toInt() }.reduce(Int::times) }
                assertThat(p.answer).isEqualTo(value)
                assertThat(p.answer).isGreaterThan(0)
            }
        }
    }

    @Test
    fun memory_grows_with_difficulty() {
        assertThat(AlarmChallenges.memorySize(1)).isEqualTo(3 to 3)
        assertThat(AlarmChallenges.memorySize(5)).isEqualTo(5 to 9)
        val p = AlarmChallenges.memoryPattern(4, Random(1))
        assertThat(p).hasSize(7)
        assertThat(p.all { it in 0 until 16 }).isTrue()
    }

    @Test
    fun typing_matching() {
        assertThat(AlarmChallenges.typedOk("  wake UP   now ", "Wake up now")).isTrue()
        assertThat(AlarmChallenges.typedOk("wake up", "Wake up now")).isFalse()
        assertThat(AlarmChallenges.typedPrefix("Wake ux", "Wake up")).isEqualTo(6)
        val phrases = listOf("a b", "a much longer phrase to type here", "medium length one")
        assertThat(AlarmChallenges.phrase(1, phrases, Random(1))).isEqualTo("a b")
        assertThat(AlarmChallenges.phrase(5, phrases, Random(1))).isEqualTo("a much longer phrase to type here")
    }

    @Test
    fun mission_defaults_and_summary() {
        val math = AlarmMissionCatalog.default(AlarmMissionType.MATH, 3)
        assertThat(math.target).isEqualTo(3)
        assertThat(AlarmChallenges.summary(math)).isEqualTo("Normal · 3 problems")
        assertThat(AlarmChallenges.summary(AlarmMissionCatalog.default(AlarmMissionType.TAP, 2))).isEqualTo("40 taps")
        // The flow advances once a mission's rounds are done.
        val flow = AlarmAlarmFlow(listOf(math, AlarmMissionCatalog.default(AlarmMissionType.MEMORY)))
        assertThat(flow.recordProgress(3)).isFalse()
        assertThat(flow.missionIndex()).isEqualTo(1)
        assertThat(flow.recordProgress(2)).isTrue()
    }

    @Test
    fun saves_new_fields_and_alarms_without_missions() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = AlarmPersistentStore(ctx)
        store.save(AlarmPersistentConfig(42, 6, 30, "Nap", missionChain = emptyList(), volume = 70, vibrationPattern = "HEARTBEAT", briefing = false, soundName = "Beep"))
        val a = store.find(42)!!
        assertThat(a.missionChain).isEmpty()
        assertThat(a.volume).isEqualTo(70)
        assertThat(a.vibrationPattern).isEqualTo("HEARTBEAT")
        assertThat(a.briefing).isFalse()
        assertThat(a.soundName).isEqualTo("Beep")
        // Old saved alarms (no mission key) still get a math mission
        ctx.getSharedPreferences("offline_alarms", 0).edit().putString("alarms", "[{\"id\":7,\"hour\":7,\"minute\":0}]").commit()
        assertThat(store.find(7)!!.missionChain.single().type).isEqualTo(AlarmMissionType.MATH)
        // Old alarms ring at full volume, locked, with the loudness boost on.
        assertThat(store.find(7)!!.volume).isEqualTo(100)
        assertThat(store.find(7)!!.volumeLock).isTrue()
        assertThat(store.find(7)!!.boost).isEqualTo(2)
        store.save(AlarmPersistentConfig(43, 7, 0, volumeLock = false, boost = 0))
        assertThat(store.find(43)!!.volumeLock).isFalse()
        assertThat(store.find(43)!!.boost).isEqualTo(0)
    }

    @Test
    fun wake_record_and_stats() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val h = AlarmHistoryStore(ctx)
        val zone = ZoneId.systemDefault()
        val cfg = AlarmPersistentConfig(5, 7, 0)
        val rang = LocalDate.now().atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        h.onRing(5, rang)
        h.onSnooze(5); h.onSnooze(5)
        h.onRing(5, rang + 600_000) // snooze re-ring keeps the first start
        h.onDismiss(cfg, 45, rang + 14 * 60_000)
        val r = h.all().single()
        assertThat(r.snoozes).isEqualTo(2)
        assertThat(r.lateMinutes).isEqualTo(14)
        assertThat(r.firstRangAt).isEqualTo(rang)
        assertThat(h.snoozes(5)).isEqualTo(0)
        val s = AlarmHistoryStore.stats(listOf(r, r.copy(dismissedAt = r.scheduledAt + 60_000)))
        assertThat(s.onTimeRate).isEqualTo(50)
        assertThat(s.onTimeStreak).isEqualTo(1)
    }
}
