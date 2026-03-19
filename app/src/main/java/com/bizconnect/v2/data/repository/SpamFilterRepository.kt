package com.bizconnect.v2.data.repository

import com.bizconnect.v2.data.local.db.dao.SpamFilterDao
import com.bizconnect.v2.data.local.db.entity.SpamFilterEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SpamFilterRepository @Inject constructor(
    private val spamFilterDao: SpamFilterDao
) {
    fun getAll(): Flow<List<SpamFilterEntity>> =
        spamFilterDao.getAll()

    suspend fun getActiveFilters(): List<SpamFilterEntity> =
        spamFilterDao.getActiveFilters()

    fun getByType(type: String): Flow<List<SpamFilterEntity>> =
        spamFilterDao.getByType(type)

    suspend fun getFilterById(id: Long): SpamFilterEntity? =
        spamFilterDao.getById(id)

    suspend fun insertFilter(type: String, value: String) {
        val filter = SpamFilterEntity(
            id = java.util.UUID.randomUUID().toString(),
            type = type,
            pattern = value,
            isActive = true
        )
        spamFilterDao.insert(filter)
    }

    suspend fun insertFilters(filters: List<SpamFilterEntity>) =
        spamFilterDao.insertAll(filters)

    suspend fun updateFilter(filter: SpamFilterEntity) =
        spamFilterDao.update(filter)

    suspend fun deleteFilter(filter: SpamFilterEntity) =
        spamFilterDao.delete(filter)

    suspend fun deleteFilterById(id: Long) =
        spamFilterDao.deleteById(id)

    suspend fun deleteAllFilters() =
        spamFilterDao.deleteAll()

    suspend fun enableFilter(id: Long) =
        spamFilterDao.enable(id)

    suspend fun disableFilter(id: Long) =
        spamFilterDao.disable(id)

    suspend fun isNumberBlocked(address: String): Boolean =
        spamFilterDao.isNumberBlocked(address)

    suspend fun isKeywordBlocked(body: String): Boolean =
        spamFilterDao.isKeywordBlocked(body)

    suspend fun isSpam(address: String, body: String): Boolean =
        spamFilterDao.isSpam(address, body)

    fun getActiveCount(): Flow<Int> =
        spamFilterDao.getActiveCount()

    suspend fun addNumberFilter(number: String) =
        insertFilter("number", number)

    suspend fun addKeywordFilter(keyword: String) =
        insertFilter("keyword", keyword)

    suspend fun addPatternFilter(pattern: String) =
        insertFilter("pattern", pattern)
}
