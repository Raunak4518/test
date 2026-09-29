package com.raunak.daytimeline.productivity

import com.google.common.truth.Truth.assertThat
import com.raunak.daytimeline.domain.TaskModel
import org.junit.Test
import java.time.LocalDate

class ChronoraPowerCenterTest {
    @Test
    fun icsExportContainsEventAndEscapedFields() {
        val task = TaskModel(1, "Deep, work", LocalDate.of(2026, 9, 29), 9 * 60, 10 * 60, "Study", 0, 2, "A;B", true, "dsa", "NONE", 0, false, "NONE", "")
        val ics = ChronoraExport.ics(listOf(task))
        assertThat(ics).contains("BEGIN:VCALENDAR")
        assertThat(ics).contains("SUMMARY:Deep\\, work")
        assertThat(ics).contains("DESCRIPTION:A\\;B")
        assertThat(ics).contains("UID:1@chronora")
        assertThat(ics).contains("END:VCALENDAR")
    }

    @Test
    fun csvExportHasHeaderAndQuotedValues() {
        val task = TaskModel(2, "Task, one", LocalDate.of(2026, 9, 29), 600, 630, "Other", 0, 1, "note", false, "tag", "NONE", 0, true, "NONE", "")
        val csv = ChronoraExport.csv(listOf(task))
        assertThat(csv).startsWith("id,date,title,start,end,priority,completed,tags,notes")
        assertThat(csv).contains(""Task, one"")
        assertThat(csv).contains(""true"")
    }
}
