package com.bizconnect.v2.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bizconnect.v2.data.local.db.entity.ScheduledMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledAt ASC")
    fun getAll(): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE isActive = 1 ORDER BY scheduledAt ASC")
    fun getActive(): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE isActive = 1 ORDER BY scheduledAt ASC LIMIT 1")
    suspend fun getNextScheduled(): ScheduledMessageEntity?

    @Query("SELECT * FROM scheduled_messages WHERE isActive = 1 AND scheduledAt <= :currentTime ORDER BY scheduledAt ASC")
    suspend fun getDueMessages(currentTime: Long): List<ScheduledMessageEntity>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id")
    suspend fun getById(id: String): ScheduledMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ScheduledMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ScheduledMessageEntity>)

    @Update
    suspend fun update(message: ScheduledMessageEntity)

    @Delete
    suspend fun delete(message: ScheduledMessageEntity)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE scheduled_messages SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: String)

    @Query("UPDATE scheduled_messages SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: String)

    @Query("UPDATE scheduled_messages SET lastSentAt = :timestamp WHERE id = :id")
    suspend fun updateLastSentAt(id: String, timestamp: Long)

    @Query("SELECT * FROM scheduled_messages WHERE userId = :userId ORDER BY scheduledAt DESC")
    fun getByUser(userId: String): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT COUNT(*) FROM scheduled_messages WHERE isActive = 1")
    fun getActiveCount(): Flow<Int>

    @Query("SELECT * FROM scheduled_messages WHERE repeatType != 'none' AND isActive = 1 ORDER BY scheduledAt ASC")
    suspend fun getRecurringMessages(): List<ScheduledMessageEntity>

    @Query("SELECT * FROM scheduled_messages WHERE repeatType = 'none' AND isActive = 1 AND scheduledAt <= :currentTime ORDER BY scheduledAt ASC")
    suspend fun getOneTimeMessages(currentTime: Long): List<ScheduledMessageEntity>
}
