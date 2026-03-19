package com.bizconnect.v2.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bizconnect.v2.data.local.db.entity.AiUsageEntity

@Dao
interface AiUsageDao {
    @Query("SELECT * FROM ai_usage WHERE date = :date")
    suspend fun getByDate(date: String): AiUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usage: AiUsageEntity)

    @Query("SELECT SUM(tokensUsed) FROM ai_usage WHERE date >= :startDate")
    suspend fun getTotalTokensSince(startDate: String): Int?

    @Query("SELECT SUM(tokensUsed) FROM ai_usage WHERE date = :date")
    suspend fun getTodayTokens(date: String): Int?
}
