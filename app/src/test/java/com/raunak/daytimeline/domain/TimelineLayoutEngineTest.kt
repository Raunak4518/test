package com.raunak.daytimeline.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class TimelineLayoutEngineTest {
    @Test
    fun overlapping_tasks_get_columns() {
        val date = LocalDate.of(2026,1,1)
        val tasks = listOf(
            TaskModel(1,"A",date,600,720,"Other",0xFF000000,1,"",false,"","NONE",0,false,"NONE",""),
            TaskModel(2,"B",date,660,780,"Other",0xFF000000,1,"",false,"","NONE",0,false,"NONE","")
        )
        val placed = TimelineLayoutEngine.place(tasks)
        assertThat(placed).hasSize(2)
        assertThat(placed.map { it.columns }.max()).isEqualTo(2)
    }
}
