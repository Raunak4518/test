package com.raunak.daytimeline.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class QuickAddParserTest {
    @Test
    fun parses_range_and_pomodoro() {
        val parsed = QuickAddParser.parse("DSA 7-9 pomodoro", LocalDate.of(2026, 1, 1))
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.title).isEqualTo("DSA")
        assertThat(parsed.startMinute).isEqualTo(7 * 60)
        assertThat(parsed.endMinute).isEqualTo(9 * 60)
        assertThat(parsed.pomodoro).isTrue()
    }
}
