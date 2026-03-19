package com.bizconnect.v2.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bizconnect.v2.data.local.db.entity.MessageTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageTemplateDao {
    @Query("SELECT * FROM message_templates ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<MessageTemplateEntity>>

    @Query("SELECT * FROM message_templates WHERE category = :category ORDER BY updatedAt DESC")
    fun getByCategory(category: String): Flow<List<MessageTemplateEntity>>

    @Query("SELECT * FROM message_templates WHERE id = :id")
    suspend fun getById(id: Long): MessageTemplateEntity?

    @Query("SELECT * FROM message_templates WHERE name LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<MessageTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: MessageTemplateEntity): Long

    @Update
    suspend fun update(template: MessageTemplateEntity)

    @Query("DELETE FROM message_templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}
