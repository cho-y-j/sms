package com.bizconnect.v2.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bizconnect.v2.data.local.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status IN ('pending', 'processing') ORDER BY priority DESC, createdAt ASC")
    fun getPendingTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getByUser(userId: String, limit: Int): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE tasks SET status = :status, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET status = :status, errorMessage = :errorMessage, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateStatusWithError(id: String, status: String, errorMessage: String?, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM tasks WHERE scheduledAt IS NOT NULL AND status IN ('pending', 'processing') ORDER BY scheduledAt ASC")
    fun getScheduledTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE scheduledAt IS NOT NULL AND scheduledAt <= :currentTime AND status IN ('pending', 'processing') ORDER BY scheduledAt ASC")
    suspend fun getDueScheduledTasks(currentTime: Long): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE status IN ('pending', 'processing')")
    fun getPendingCount(): Flow<Int>

    @Query("UPDATE tasks SET isNotified = 1 WHERE id = :id")
    suspend fun markAsNotified(id: String)

    @Query("UPDATE tasks SET retryCount = retryCount + 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun incrementRetryCount(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM tasks WHERE userId = :userId AND status = 'completed' ORDER BY completedAt DESC LIMIT 100")
    suspend fun getRecentCompleted(userId: String): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'failed' AND userId = :userId")
    fun getFailedCount(userId: String): Flow<Int>

    @Query("SELECT * FROM tasks WHERE customerId = :customerId ORDER BY createdAt DESC")
    suspend fun getByCustomer(customerId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status IN ('pending', 'processing') AND priority > 0 ORDER BY priority DESC, scheduledAt ASC")
    suspend fun getHighPriorityTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 'PENDING' ORDER BY priority DESC, createdAt ASC LIMIT :limit")
    suspend fun getPendingTasksLimited(limit: Int): List<TaskEntity>

    @Query("UPDATE tasks SET status = :status, updatedAt = :timestamp WHERE status = 'PENDING'")
    suspend fun updateAllPendingStatus(status: String, timestamp: Long = System.currentTimeMillis())
}
