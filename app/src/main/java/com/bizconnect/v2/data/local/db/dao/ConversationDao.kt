package com.bizconnect.v2.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bizconnect.v2.data.local.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query(
        "SELECT * FROM conversations " +
        "ORDER BY isPinned DESC, lastMessageTimestamp DESC"
    )
    fun getAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE threadId = :threadId")
    fun getById(threadId: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE threadId = :threadId")
    suspend fun getByIdSync(threadId: Long): ConversationEntity?

    @Query(
        "SELECT * FROM conversations WHERE " +
        "recipientName LIKE '%' || :query || '%' OR " +
        "recipientAddress LIKE '%' || :query || '%' OR " +
        "snippet LIKE '%' || :query || '%' OR " +
        "aiSummary LIKE '%' || :query || '%' " +
        "ORDER BY isPinned DESC, lastMessageTimestamp DESC"
    )
    fun search(query: String): Flow<List<ConversationEntity>>

    @Query("SELECT SUM(unreadCount) FROM conversations")
    fun getUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Delete
    suspend fun delete(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE threadId = :threadId")
    suspend fun deleteById(threadId: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("UPDATE conversations SET read = 1, unreadCount = 0 WHERE threadId = :threadId")
    suspend fun markAsRead(threadId: Long)

    @Query("UPDATE conversations SET isPinned = 1 WHERE threadId = :threadId")
    suspend fun pin(threadId: Long)

    @Query("UPDATE conversations SET isPinned = 0 WHERE threadId = :threadId")
    suspend fun unpin(threadId: Long)

    @Query("UPDATE conversations SET isMuted = 1 WHERE threadId = :threadId")
    suspend fun mute(threadId: Long)

    @Query("UPDATE conversations SET isMuted = 0 WHERE threadId = :threadId")
    suspend fun unmute(threadId: Long)

    @Query("UPDATE conversations SET isBlocked = 1 WHERE threadId = :threadId")
    suspend fun block(threadId: Long)

    @Query("UPDATE conversations SET isBlocked = 0 WHERE threadId = :threadId")
    suspend fun unblock(threadId: Long)

    @Query("UPDATE conversations SET isArchived = 1 WHERE threadId = :threadId")
    suspend fun archive(threadId: Long)

    @Query("UPDATE conversations SET isArchived = 0 WHERE threadId = :threadId")
    suspend fun unarchive(threadId: Long)

    @Query("UPDATE conversations SET draftText = :draftText WHERE threadId = :threadId")
    suspend fun updateDraft(threadId: Long, draftText: String?)

    @Query("UPDATE conversations SET aiSummary = :summary, aiSummaryDate = :date, aiEmotion = :emotion WHERE threadId = :threadId")
    suspend fun updateAiSummary(threadId: Long, summary: String?, date: Long?, emotion: String?)

    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    fun getActiveConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isArchived = 1 ORDER BY lastMessageTimestamp DESC")
    fun getArchivedConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT COUNT(*) FROM conversations")
    fun getTotalCount(): Flow<Int>
}
