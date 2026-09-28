package com.raunak.daytimeline.domain

import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.data.TaskEntity
import org.junit.Test

class ConflictDetectorTest {
    @Test
    fun overlap_minutes_calculated() {
        val candidate = TaskEntity(title = "A", dateEpochDay = 0, startMinute = 600, endMinute = 720)
        val existing = listOf(TaskEntity(id = 1, title = "B", dateEpochDay = 0, startMinute = 690, endMinute = 750))
        assertThat(ConflictDetector.maxOverlapMinutes(candidate, existing)).isEqualTo(30)
    }
}
