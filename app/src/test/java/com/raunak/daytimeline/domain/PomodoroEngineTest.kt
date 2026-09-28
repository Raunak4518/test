package com.raunak.daytimeline.domain

import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.data.PomodoroStateEntity
import org.junit.Test

class PomodoroEngineTest {
    @Test
    fun transitions_from_focus_to_break() {
        val state = PomodoroStateEntity(phase = "FOCUS", targetEpochMillis = System.currentTimeMillis() - 1000, running = true, focusMinutes = 25)
        val next = PomodoroEngine.tick(state)
        assertThat(next.phase).contains("BREAK")
    }
}
