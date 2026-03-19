package com.bizconnect.v2.data.local.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bizconnect.v2.data.local.db.entity.SmsLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {
    @Query("SELECT * FROM sms_logs WHERE userId = :userId ORDER BY sentAt DESC")
    fun getAll(userId: String): PagingSource<Int, SmsLogEntity>

    @Query("SELECT * FROM sms_logs WHERE userId = :userId ORDER BY sentAt DESC")
    fun getAllFlow(userId: String): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE userId = :userId AND sentAt >= :startDate AND sentAt <= :endDate ORDER BY sentAt DESC")
    fun getByDate(userId: String, startDate: Long, endDate: Long): Flow<List<SmsLogEntity>>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE userId = :userId AND DATE(sentAt / 1000, 'unixepoch') = :date")
    fun getTodayCount(userId: String, date: String): Flow<Int>

    @Query("SELECT * FROM sms_logs WHERE id = :id")
    suspend fun getById(id: String): SmsLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SmsLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<SmsLogEntity>)

    @Update
    suspend fun update(log: SmsLogEntity)

    @Delete
    suspend fun delete(log: SmsLogEntity)

    @Query("DELETE FROM sms_logs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM sms_logs WHERE userId = :userId AND syncedAt IS NULL")
    suspend fun getUnsyncedLogs(userId: String): List<SmsLogEntity>

    @Query("UPDATE sms_logs SET syncedAt = :timestamp WHERE id = :id")
    suspend fun markAsSynced(id: String, timestamp: Long)

    @Query("SELECT * FROM sms_logs WHERE userId = :userId AND status = 'sent' ORDER BY sentAt DESC LIMIT :limit")
    suspend fun getRecentSent(userId: String, limit: Int): List<SmsLogEntity>

    @Query("SELECT * FROM sms_logs WHERE userId = :userId AND status = 'failed' ORDER BY sentAt DESC")
    fun getFailedLogs(userId: String): Flow<List<SmsLogEntity>>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE userId = :userId AND DATE(sentAt / 1000, 'unixepoch') = :date")
    suspend fun getDailyCount(userId: String, date: String): Int

    @Query("SELECT DISTINCT phoneNumber FROM sms_logs WHERE userId = :userId ORDER BY sentAt DESC LIMIT :limit")
    suspend fun getRecentRecipients(userId: String, limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE userId = :userId")
    fun getTotalCount(userId: String): Flow<Int>

    @Query("SELECT * FROM sms_logs WHERE userId = :userId AND taskId = :taskId ORDER BY sentAt DESC")
    suspend fun getByTask(userId: String, taskId: String): List<SmsLogEntity>

    @Query("SELECT * FROM sms_logs WHERE userId = :userId AND type = :type ORDER BY sentAt DESC")
    fun getByType(userId: String, type: String): Flow<List<SmsLogEntity>>
}
