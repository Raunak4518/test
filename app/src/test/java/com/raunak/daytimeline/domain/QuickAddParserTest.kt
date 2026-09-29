package com.raunak.daytimeline.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class QuickAddParserTest {
    // 2026-01-01 is a Thursday
    private val today = LocalDate.of(2026, 1, 1)

    @Test
    fun parses_range_and_pomodoro() {
        val parsed = QuickAddParser.parse("DSA 7-9 pomodoro", today)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.title).isEqualTo("DSA")
        assertThat(parsed.startMinute).isEqualTo(7 * 60)
        assertThat(parsed.endMinute).isEqualTo(9 * 60)
        assertThat(parsed.pomodoro).isTrue()
    }

    @Test
    fun parses_tomorrow_at_time_for_duration() {
        val parsed = QuickAddParser.parse("Study tomorrow at 7pm for 90 minutes", today)!!
        assertThat(parsed.title).isEqualTo("Study")
        assertThat(parsed.date).isEqualTo(today.plusDays(1))
        assertThat(parsed.startMinute).isEqualTo(19 * 60)
        assertThat(parsed.endMinute).isEqualTo(20 * 60 + 30)
        // Without am/pm, "at 1".."at 6" are read as afternoon/evening
        assertThat(QuickAddParser.parse("Gym at 5", today)!!.startMinute).isEqualTo(17 * 60)
        assertThat(QuickAddParser.parse("Walk at 7", today)!!.startMinute).isEqualTo(7 * 60)
    }

    @Test
    fun parses_pm_time_and_hours() {
        val parsed = QuickAddParser.parse("Study 7pm for 2 hours", today)!!
        assertThat(parsed.title).isEqualTo("Study")
        assertThat(parsed.startMinute).isEqualTo(19 * 60)
        assertThat(parsed.endMinute).isEqualTo(21 * 60)
    }

    @Test
    fun parses_every_monday_recurrence() {
        val parsed = QuickAddParser.parse("Gym every monday and thursday 18:00", today)!!
        assertThat(parsed.title).isEqualTo("Gym")
        assertThat(parsed.recurrenceType).isEqualTo("CUSTOM_DAYS")
        assertThat(parsed.recurrenceDays).isEqualTo("1,4")
        assertThat(parsed.startMinute).isEqualTo(18 * 60)
        assertThat(parsed.date).isEqualTo(today)
    }

    @Test
    fun parses_weekday_name_as_next_occurrence() {
        val parsed = QuickAddParser.parse("Submit report next monday", today)!!
        assertThat(parsed.title).isEqualTo("Submit report")
        assertThat(parsed.date).isEqualTo(LocalDate.of(2026, 1, 5))
    }

    @Test
    fun parses_priority_tags_and_reminder() {
        val parsed = QuickAddParser.parse("Revise CN p1 #exam #cn 10am remind me 15m before", today)!!
        assertThat(parsed.title).isEqualTo("Revise CN")
        assertThat(parsed.priority).isEqualTo(3)
        assertThat(parsed.tags).isEqualTo("exam,cn")
        assertThat(parsed.startMinute).isEqualTo(10 * 60)
        assertThat(parsed.reminderMode).isEqualTo("BEFORE")
        assertThat(parsed.reminderOffsetMinutes).isEqualTo(15)
    }

    @Test
    fun parses_daily_and_weekdays() {
        assertThat(QuickAddParser.parse("Water plants daily", today)!!.recurrenceType).isEqualTo("DAILY")
        val standup = QuickAddParser.parse("Standup every weekday 9:30 15m", today)!!
        assertThat(standup.recurrenceType).isEqualTo("WEEKDAYS")
        assertThat(standup.startMinute).isEqualTo(9 * 60 + 30)
        assertThat(standup.endMinute).isEqualTo(9 * 60 + 45)
        assertThat(standup.title).isEqualTo("Standup")
    }

    @Test
    fun parses_iso_date_and_in_days() {
        assertThat(QuickAddParser.parse("Exam 2026-03-10", today)!!.date).isEqualTo(LocalDate.of(2026, 3, 10))
        assertThat(QuickAddParser.parse("Call mom in 3 days", today)!!.date).isEqualTo(today.plusDays(3))
    }

    @Test
    fun from_to_range() {
        val parsed = QuickAddParser.parse("Deep work from 9 to 11:30", today)!!
        assertThat(parsed.title).isEqualTo("Deep work")
        assertThat(parsed.startMinute).isEqualTo(9 * 60)
        assertThat(parsed.endMinute).isEqualTo(11 * 60 + 30)
    }

    @Test
    fun blank_returns_null() {
        assertThat(QuickAddParser.parse("   ", today)).isNull()
    }
}
