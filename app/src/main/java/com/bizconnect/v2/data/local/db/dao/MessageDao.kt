package com.bizconnect.v2.data.local.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bizconnect.v2.data.local.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp DESC")
    fun getByThread(threadId: Long): PagingSource<Int, MessageEntity>

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp DESC")
    fun getByThreadFlow(threadId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp ASC LIMIT 1")
    suspend fun getFirstMessageInThread(threadId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    suspend fun getMessagesByThreadDirect(threadId: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getTotalCountSync(): Int

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByThread(threadId: Long): MessageEntity?

    @Query(
        "SELECT * FROM messages WHERE " +
        "body LIKE '%' || :query || '%' " +
        "ORDER BY timestamp DESC"
    )
    fun search(query: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(messages: List<MessageEntity>)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT * FROM messages WHERE systemSmsId = :systemSmsId LIMIT 1")
    suspend fun getBySystemSmsId(systemSmsId: Long): MessageEntity?

    @Query("UPDATE messages SET systemSmsId = :systemSmsId WHERE id = :id")
    suspend fun updateSystemSmsId(id: Long, systemSmsId: Long)

    @Update
    suspend fun update(message: MessageEntity)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun deleteByThread(threadId: Long)

    @Query("UPDATE messages SET read = 1, seen = 1 WHERE threadId = :threadId")
    suspend fun markAsRead(threadId: Long)

    @Query("UPDATE messages SET read = 1, seen = 1 WHERE id = :id")
    suspend fun markMessageAsRead(id: Long)

    @Query("SELECT * FROM messages WHERE read = 0 ORDER BY timestamp DESC")
    fun getUnreadMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadId = :threadId AND read = 0")
    fun getUnreadByThread(threadId: Long): Flow<List<MessageEntity>>

    @Query(
        "SELECT * FROM messages WHERE threadId = :threadId " +
        "AND timestamp >= :startTime AND timestamp <= :endTime " +
        "ORDER BY timestamp DESC"
    )
    fun getByDateRange(threadId: Long, startTime: Long, endTime: Long): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE threadId = :threadId")
    fun getMessageCount(threadId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE threadId = :threadId AND read = 0")
    fun getUnreadCount(threadId: Long): Flow<Int>

    @Query("SELECT * FROM messages WHERE isMms = 1 AND threadId = :threadId ORDER BY timestamp DESC")
    fun getMMSByThread(threadId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByType(type: Int, limit: Int): List<MessageEntity>

    @Query("UPDATE messages SET isLocked = 1 WHERE id = :id")
    suspend fun lockMessage(id: Long)

    @Query("UPDATE messages SET isLocked = 0 WHERE id = :id")
    suspend fun unlockMessage(id: Long)

    @Query("SELECT * FROM messages WHERE isLocked = 1 ORDER BY timestamp DESC")
    fun getLockedMessages(): Flow<List<MessageEntity>>
}
