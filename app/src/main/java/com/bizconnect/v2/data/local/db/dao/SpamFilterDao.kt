package com.bizconnect.v2.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bizconnect.v2.data.local.db.entity.SpamFilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpamFilterDao {
    @Query("SELECT * FROM spam_filters ORDER BY type ASC")
    fun getAll(): Flow<List<SpamFilterEntity>>

    @Query("SELECT * FROM spam_filters WHERE isActive = 1 ORDER BY type ASC")
    suspend fun getActiveFilters(): List<SpamFilterEntity>

    @Query("SELECT * FROM spam_filters WHERE type = :type AND isActive = 1")
    fun getByType(type: String): Flow<List<SpamFilterEntity>>

    @Query("SELECT * FROM spam_filters WHERE id = :id")
    suspend fun getById(id: Long): SpamFilterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(filter: SpamFilterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(filters: List<SpamFilterEntity>)

    @Update
    suspend fun update(filter: SpamFilterEntity)

    @Delete
    suspend fun delete(filter: SpamFilterEntity)

    @Query("DELETE FROM spam_filters WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM spam_filters")
    suspend fun deleteAll()

    @Query("UPDATE spam_filters SET isActive = 1 WHERE id = :id")
    suspend fun enable(id: Long)

    @Query("UPDATE spam_filters SET isActive = 0 WHERE id = :id")
    suspend fun disable(id: Long)

    @Query(
        "SELECT CASE WHEN EXISTS (" +
        "SELECT 1 FROM spam_filters WHERE isActive = 1 AND type = 'number' AND value = :address" +
        ") THEN 1 ELSE 0 END"
    )
    suspend fun isNumberBlocked(address: String): Boolean

    @Query(
        "SELECT CASE WHEN EXISTS (" +
        "SELECT 1 FROM spam_filters WHERE isActive = 1 AND type = 'keyword' AND :body LIKE '%' || value || '%'" +
        ") THEN 1 ELSE 0 END"
    )
    suspend fun isKeywordBlocked(body: String): Boolean

    @Query(
        "SELECT CASE WHEN " +
        "(:address IN (SELECT value FROM spam_filters WHERE isActive = 1 AND type = 'number')) OR " +
        "(:body LIKE '%' || (SELECT value FROM spam_filters WHERE isActive = 1 AND type = 'keyword' LIMIT 1) || '%') " +
        "THEN 1 ELSE 0 END"
    )
    suspend fun isSpam(address: String, body: String): Boolean

    @Query("SELECT COUNT(*) FROM spam_filters WHERE isActive = 1")
    fun getActiveCount(): Flow<Int>

    @Query("SELECT * FROM spam_filters WHERE isActive = 1")
    suspend fun getAllActive(): List<SpamFilterEntity>

    @Query("SELECT * FROM spam_filters WHERE type = 'number' AND value = :address AND isActive = 1 LIMIT 1")
    suspend fun getBlockedNumber(address: String): SpamFilterEntity?

    @Query("SELECT * FROM spam_filters WHERE type = 'number' AND isActive = 1")
    suspend fun getBlockedNumbers(): List<SpamFilterEntity>

    @Query("SELECT * FROM spam_filters WHERE type = 'keyword' AND isActive = 1")
    suspend fun getKeywordFilters(): List<SpamFilterEntity>

    @Query("SELECT COUNT(*) FROM spam_filters WHERE isActive = 1 AND createdAt >= :since")
    suspend fun getBlockedCountSince(since: Long): Int

    @Query("DELETE FROM spam_filters WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM spam_filters WHERE type = :type AND isActive = 1")
    suspend fun getByTypeList(type: String): List<SpamFilterEntity>
}
