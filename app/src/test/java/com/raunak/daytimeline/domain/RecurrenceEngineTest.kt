package com.raunak.daytimeline.domain

import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.data.TaskEntity
import org.junit.Test
import java.time.LocalDate

class RecurrenceEngineTest {
    @Test
    fun weekday_rule_excludes_weekend() {
        val task = TaskEntity(title = "DSA", dateEpochDay = LocalDate.of(2026,1,1).toEpochDay(), startMinute = 600, endMinute = 660, recurrenceType = "WEEKDAYS")
        assertThat(RecurrenceEngine.occursOn(task, LocalDate.of(2026,1,5))).isTrue()
        assertThat(RecurrenceEngine.occursOn(task, LocalDate.of(2026,1,4))).isFalse()
    }
}
