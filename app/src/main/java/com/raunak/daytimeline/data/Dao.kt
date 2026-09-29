package com.raunak.daytimeline.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE dateEpochDay = :dateEpochDay OR recurrenceType != 'NONE' ORDER BY startMinute")
    fun observeForDate(dateEpochDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY dateEpochDay, startMinute")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY dateEpochDay, startMinute")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun delete(taskId: Long)

    @Query("SELECT * FROM tasks WHERE dateEpochDay = :dateEpochDay")
    suspend fun forExactDate(dateEpochDay: Long): List<TaskEntity>
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items WHERE taskId = :taskId ORDER BY position, id")
    fun observeForTask(taskId: Long): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE taskId IN (:taskIds)")
    suspend fun forTasks(taskIds: List<Long>): List<ChecklistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChecklistItemEntity): Long

    @Update
    suspend fun update(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_items WHERE id = :itemId")
    suspend fun delete(itemId: Long)

    @Query("DELETE FROM checklist_items WHERE taskId = :taskId")
    suspend fun clearTask(taskId: Long)
}

@Dao
interface PomodoroDao {
    @Query("SELECT * FROM pomodoro_state WHERE id = 1")
    fun observe(): Flow<PomodoroStateEntity?>

    @Query("SELECT * FROM pomodoro_state WHERE id = 1")
    suspend fun current(): PomodoroStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PomodoroStateEntity)

    @Query("DELETE FROM pomodoro_state")
    suspend fun clear()
}
