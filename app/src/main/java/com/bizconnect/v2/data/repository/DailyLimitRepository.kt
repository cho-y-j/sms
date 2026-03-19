package com.bizconnect.v2.data.repository

import com.bizconnect.v2.data.local.db.BizConnectDatabase
import com.bizconnect.v2.data.local.db.entity.DailyLimitEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyLimitRepository(private val database: BizConnectDatabase) {
    private val dailyLimitDao = database.dailyLimitDao()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun get(userId: String, date: String): DailyLimitEntity? =
        dailyLimitDao.get(userId, date)

    fun getFlow(userId: String, date: String): Flow<DailyLimitEntity?> =
        dailyLimitDao.getFlow(userId, date)

    suspend fun insert(limit: DailyLimitEntity) =
        dailyLimitDao.insert(limit)

    suspend fun update(limit: DailyLimitEntity) =
        dailyLimitDao.update(limit)

    suspend fun delete(limit: DailyLimitEntity) =
        dailyLimitDao.delete(limit)

    suspend fun incrementCount(userId: String, date: String) =
        dailyLimitDao.incrementCount(userId, date)

    suspend fun setCount(userId: String, date: String, count: Int) =
        dailyLimitDao.setCount(userId, date, count)

    suspend fun setLimitMode(userId: String, date: String, mode: String) =
        dailyLimitDao.setLimitMode(userId, date, mode)

    suspend fun getSentCount(userId: String, date: String): Int =
        dailyLimitDao.getSentCount(userId, date) ?: 0

    suspend fun getLimitMode(userId: String, date: String): String =
        dailyLimitDao.getLimitMode(userId, date) ?: "safe"

    fun getLast30Days(userId: String): Flow<List<DailyLimitEntity>> =
        dailyLimitDao.getLast30Days(userId)

    suspend fun deleteOlderThan(userId: String, date: String) =
        dailyLimitDao.deleteOlderThan(userId, date)

    fun getTotalRecords(userId: String): Flow<Int> =
        dailyLimitDao.getTotalRecords(userId)

    suspend fun getTodayDate(): String =
        dateFormat.format(Date())

    suspend fun getOrCreateToday(userId: String, limitMode: String = "safe"): DailyLimitEntity {
        val today = getTodayDate()
        val existing = get(userId, today)
        return existing ?: DailyLimitEntity(
            userId = userId,
            date = today,
            sentCount = 0,
            limitMode = limitMode
        ).also {
            insert(it)
        }
    }

    suspend fun incrementTodayCount(userId: String) {
        val today = getTodayDate()
        val existing = get(userId, today)
        if (existing != null) {
            incrementCount(userId, today)
        } else {
            insert(
                DailyLimitEntity(
                    userId = userId,
                    date = today,
                    sentCount = 1,
                    limitMode = "safe"
                )
            )
        }
    }

    suspend fun canSendToday(userId: String, limitMode: String): Boolean {
        val today = getTodayDate()
        val limit = get(userId, today) ?: return true

        val maxSend = when (limitMode) {
            "safe" -> 199
            "max" -> 499
            else -> 199
        }

        return limit.sentCount < maxSend
    }

    suspend fun getRemainingToday(userId: String, limitMode: String): Int {
        val today = getTodayDate()
        val limit = get(userId, today) ?: return getMaxLimit(limitMode)

        val maxSend = getMaxLimit(limitMode)
        return (maxSend - limit.sentCount).coerceAtLeast(0)
    }

    private fun getMaxLimit(limitMode: String): Int =
        when (limitMode) {
            "safe" -> 199
            "max" -> 499
            else -> 199
        }
}
