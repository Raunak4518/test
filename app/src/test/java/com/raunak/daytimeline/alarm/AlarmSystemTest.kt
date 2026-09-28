package com.raunak.daytimeline.alarm

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime

class AlarmSystemTest {
    @Test fun missionBuilderAddsAndRemoves() {
        val first = AlarmMissionCatalog.default(AlarmMissionType.MATH)
        val list = AlarmMissionBuilder.add(listOf(first), AlarmMissionType.TYPING)
        assertThat(list).hasSize(2)
        assertThat(AlarmMissionBuilder.remove(list, 0)).hasSize(1)
    }

    @Test fun flowRequiresMissionTarget() {
        val flow = AlarmAlarmFlow(listOf(AlarmMission(AlarmMissionType.SHAKE, target = 3)))
        assertThat(flow.recordProgress(2)).isFalse()
        assertThat(flow.isDismissed()).isFalse()
        assertThat(flow.recordProgress(3)).isTrue()
        assertThat(flow.isDismissed()).isTrue()
    }

    @Test fun policyClampsUnsafeValues() {
        val p = AlarmMissionPolicy(maxSnoozes = 999, snoozeMinutes = -4, timeoutMinutes = 999).validated()
        assertThat(p.maxSnoozes).isEqualTo(20)
        assertThat(p.snoozeMinutes).isEqualTo(1)
        assertThat(p.timeoutMinutes).isEqualTo(120)
    }

    @Test fun nextOccurrenceIsFuture() {
        val now = LocalDateTime.of(2026, 9, 29, 12, 0)
        val c = AlarmPersistentConfig(1, 8, 0, repeatDays = setOf(3))
        val next = AlarmSchedulePlanner.nextOccurrence(c, now)
        assertThat(next).isGreaterThan(now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    @Test fun editorRejectsInvalidValues() {
        val e = AlarmEditorModel(hour = 30, label = "")
        assertThat(e.validate()).contains("Hour must be 0–23")
        assertThat(e.validate()).contains("Alarm name cannot be empty")
    }
}
