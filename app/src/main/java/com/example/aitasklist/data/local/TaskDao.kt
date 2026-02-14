package com.example.aitasklist.data.local

import com.example.aitasklist.model.Task

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllTasksSync(): List<Task>
    
    @Query("SELECT * FROM tasks WHERE isDeleted = 1 ORDER BY createdAt DESC")
    fun getDeletedTasks(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Update
    suspend fun updateTasks(tasks: List<Task>)

    @Query("DELETE FROM tasks WHERE isCompleted = 1")
    suspend fun deleteCompletedTasks()

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("UPDATE tasks SET isCompleted = 1 WHERE id = :taskId")
    suspend fun markTaskCompleted(taskId: String)

    @Query("SELECT * FROM tasks WHERE calendarEventId = :eventId AND isDeleted = 0 LIMIT 1")
    suspend fun getTaskByCalendarEventId(eventId: Long): Task?
    
    @Query("SELECT * FROM tasks WHERE calendarEventId = :eventId LIMIT 1")
    suspend fun getTaskByCalendarEventIdIncludingDeleted(eventId: Long): Task?

    @Query("SELECT * FROM tasks WHERE content = :title AND scheduledDate >= :startTime AND scheduledDate <= :endTime LIMIT 1")
    suspend fun findTaskByTitleAndDate(title: String, startTime: Long, endTime: Long): Task?

    @Query("SELECT * FROM tasks WHERE content = :title AND (scheduledDate = 0 OR scheduledDate IS NULL) LIMIT 1")
    suspend fun findUnscheduledTaskByTitle(title: String): Task?

    @Query("DELETE FROM tasks WHERE isDeleted = 1 AND scheduledDate > 0 AND scheduledDate < :minTimestamp")
    suspend fun deleteSoftDeletedOlderThan(minTimestamp: Long)

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND isCompleted = 0 AND scheduledDate >= :startTime AND scheduledDate <= :endTime ORDER BY scheduledDate ASC")
    suspend fun getTasksInRange(startTime: Long, endTime: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND isCompleted = 0 AND scheduledDate > 0 AND scheduledDate < :now ORDER BY scheduledDate ASC")
    suspend fun getOverdueTasks(now: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND isCompleted = 0 AND (scheduledDate = 0 OR scheduledDate IS NULL)")
    suspend fun getUnscheduledTasks(): List<Task>
}
