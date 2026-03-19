package com.bizconnect.v2.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.bizconnect.v2.data.local.db.BizConnectDatabase
import com.bizconnect.v2.data.local.db.entity.SmsLogEntity
import com.bizconnect.v2.domain.model.SmsLog
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class SmsLogRepository(private val database: BizConnectDatabase) {
    private val smsLogDao = database.smsLogDao()

    fun getAll(userId: String): Flow<PagingData<SmsLog>> =
        Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = {
                smsLogDao.getAll(userId)
            }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }

    fun getAllFlow(userId: String): Flow<List<SmsLog>> =
        smsLogDao.getAllFlow(userId).map { logs ->
            logs.map { it.toDomain() }
        }

    fun getByDate(userId: String, startDate: Long, endDate: Long): Flow<List<SmsLog>> =
        smsLogDao.getByDate(userId, startDate, endDate).map { logs ->
            logs.map { it.toDomain() }
        }

    fun getTodayCount(userId: String, date: String): Flow<Int> =
        smsLogDao.getTodayCount(userId, date)

    suspend fun getDailyCount(userId: String, date: String): Int =
        smsLogDao.getDailyCount(userId, date)

    suspend fun getLogById(id: String): SmsLog? =
        smsLogDao.getById(id)?.toDomain()

    suspend fun insertLog(log: SmsLog) =
        smsLogDao.insert(log.toEntity())

    suspend fun insertLogs(logs: List<SmsLog>) =
        smsLogDao.insertAll(logs.map { it.toEntity() })

    suspend fun createLog(
        userId: String,
        taskId: String?,
        phoneNumber: String,
        message: String,
        type: String,
        status: String,
        isMms: Boolean = false,
        imageUrl: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        val log = SmsLog(
            id = id,
            userId = userId,
            taskId = taskId,
            phoneNumber = phoneNumber,
            message = message,
            type = type,
            status = status,
            isMms = isMms,
            imageUrl = imageUrl
        )
        insertLog(log)
        return id
    }

    suspend fun updateLog(log: SmsLog) =
        smsLogDao.update(log.toEntity())

    suspend fun deleteLog(log: SmsLog) =
        smsLogDao.delete(log.toEntity())

    suspend fun deleteLogById(id: String) =
        smsLogDao.deleteById(id)

    suspend fun getUnsyncedLogs(userId: String): List<SmsLog> =
        smsLogDao.getUnsyncedLogs(userId).map { it.toDomain() }

    suspend fun markLogAsSynced(id: String) =
        smsLogDao.markAsSynced(id, System.currentTimeMillis())

    suspend fun getRecentSent(userId: String, limit: Int = 50): List<SmsLog> =
        smsLogDao.getRecentSent(userId, limit).map { it.toDomain() }

    fun getFailedLogs(userId: String): Flow<List<SmsLog>> =
        smsLogDao.getFailedLogs(userId).map { logs ->
            logs.map { it.toDomain() }
        }

    suspend fun getRecentRecipients(userId: String, limit: Int = 10): List<String> =
        smsLogDao.getRecentRecipients(userId, limit)

    fun getTotalCount(userId: String): Flow<Int> =
        smsLogDao.getTotalCount(userId)

    suspend fun getLogsByTask(userId: String, taskId: String): List<SmsLog> =
        smsLogDao.getByTask(userId, taskId).map { it.toDomain() }

    fun getLogsByType(userId: String, type: String): Flow<List<SmsLog>> =
        smsLogDao.getByType(userId, type).map { logs ->
            logs.map { it.toDomain() }
        }

    private fun SmsLogEntity.toDomain() = SmsLog(
        id = id,
        userId = userId,
        taskId = taskId,
        phoneNumber = phoneNumber,
        message = message,
        type = type,
        status = status,
        isMms = isMms,
        imageUrl = imageUrl,
        sentAt = sentAt,
        syncedAt = syncedAt
    )

    private fun SmsLog.toEntity() = SmsLogEntity(
        id = id,
        userId = userId,
        taskId = taskId,
        phoneNumber = phoneNumber,
        message = message,
        type = type,
        status = status,
        isMms = isMms,
        imageUrl = imageUrl,
        sentAt = sentAt,
        syncedAt = syncedAt
    )
}
