package com.raunak.daytimeline.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val repo = TaskRepository(db.taskDao(), db.checklistDao(), db.pomodoroDao(), ReminderScheduler(context))
    private val today = LocalDate.now()

    @Test
    fun repeating_tasks_complete_per_day() = runBlocking {
        val id = repo.addTask(TaskEntity(title = "Gym", dateEpochDay = today.minusDays(3).toEpochDay(), startMinute = 1080, endMinute = 1140, recurrenceType = "DAILY"))
        repo.markComplete(id, true, today)
        assertThat(repo.observeTasks(today).first().single().completed).isTrue()
        assertThat(repo.observeTasks(today.plusDays(1)).first().single().completed).isFalse()
        val agenda = repo.observeAgenda(today.minusDays(5), today.plusDays(2)).first()
        assertThat(agenda.map { it.date }).containsExactly(today, today.plusDays(1), today.plusDays(2)).inOrder()
        repo.markComplete(id, false, today)
        assertThat(repo.observeTasks(today).first().single().completed).isFalse()
    }

    @Test
    fun edits_keep_completions_and_reschedule_moves_one_offs() = runBlocking {
        val id = repo.addTask(TaskEntity(title = "Fees", dateEpochDay = today.minusDays(2).toEpochDay(), startMinute = 600, endMinute = 630))
        repo.reschedule(id, today)
        assertThat(repo.byId(id)!!.dateEpochDay).isEqualTo(today.toEpochDay())
        val r = repo.addTask(TaskEntity(title = "Read", dateEpochDay = today.toEpochDay(), startMinute = 600, endMinute = 630, recurrenceType = "DAILY"))
        repo.markComplete(r, true, today)
        repo.updateTask(TaskEntity(id = r, title = "Read more", dateEpochDay = today.toEpochDay(), startMinute = 600, endMinute = 660, recurrenceType = "DAILY"))
        assertThat(repo.observeTasks(today).first().first { it.id == r }.completed).isTrue()
    }

    @Test
    fun migration_adds_column() {
        assertThat(AppDatabase.MIGRATION_1_2.startVersion).isEqualTo(1)
        assertThat(AppDatabase.MIGRATION_1_2.endVersion).isEqualTo(2)
    }
}
