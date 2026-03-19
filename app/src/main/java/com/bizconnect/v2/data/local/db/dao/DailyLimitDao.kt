package com.bizconnect.v2.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bizconnect.v2.data.local.db.entity.DailyLimitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyLimitDao {
    @Query("SELECT * FROM daily_limits WHERE userId = :userId AND date = :date")
    suspend fun get(userId: String, date: String): DailyLimitEntity?

    @Query("SELECT * FROM daily_limits WHERE userId = :userId AND date = :date")
    fun getFlow(userId: String, date: String): Flow<DailyLimitEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(limit: DailyLimitEntity)

    @Update
    suspend fun update(limit: DailyLimitEntity)

    @Delete
    suspend fun delete(limit: DailyLimitEntity)

    @Query("UPDATE daily_limits SET sentCount = sentCount + 1, updatedAt = :timestamp WHERE userId = :userId AND date = :date")
    suspend fun incrementCount(userId: String, date: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE daily_limits SET sentCount = :count, updatedAt = :timestamp WHERE userId = :userId AND date = :date")
    suspend fun setCount(userId: String, date: String, count: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE daily_limits SET limitMode = :mode WHERE userId = :userId AND date = :date")
    suspend fun setLimitMode(userId: String, date: String, mode: String)

    @Query("SELECT sentCount FROM daily_limits WHERE userId = :userId AND date = :date")
    suspend fun getSentCount(userId: String, date: String): Int?

    @Query("SELECT limitMode FROM daily_limits WHERE userId = :userId AND date = :date")
    suspend fun getLimitMode(userId: String, date: String): String?

    @Query("SELECT * FROM daily_limits WHERE userId = :userId ORDER BY date DESC LIMIT 30")
    fun getLast30Days(userId: String): Flow<List<DailyLimitEntity>>

    @Query("DELETE FROM daily_limits WHERE userId = :userId AND date < :date")
    suspend fun deleteOlderThan(userId: String, date: String)

    @Query("SELECT COUNT(*) FROM daily_limits WHERE userId = :userId")
    fun getTotalRecords(userId: String): Flow<Int>
}
