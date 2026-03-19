package com.bizconnect.v2.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bizconnect.v2.data.local.db.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE userId = :userId AND isDeleted = 0 ORDER BY name ASC")
    fun getAll(userId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phone = :phone OR normalizedPhone = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE groupId = :groupId AND isDeleted = 0 ORDER BY name ASC")
    fun getByGroup(groupId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE userId = :userId AND birthday LIKE :monthDay AND isDeleted = 0 ORDER BY name ASC")
    fun getBirthdaysToday(userId: String, monthDay: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE userId = :userId AND anniversary LIKE :monthDay AND isDeleted = 0 ORDER BY name ASC")
    fun getAnniversariesToday(userId: String, monthDay: String): Flow<List<CustomerEntity>>

    @Query(
        "SELECT * FROM customers WHERE " +
        "userId = :userId AND isDeleted = 0 AND (" +
        "name LIKE '%' || :query || '%' OR " +
        "phone LIKE '%' || :query || '%' OR " +
        "normalizedPhone LIKE '%' || :query || '%' OR " +
        "memo LIKE '%' || :query || '%'" +
        ") ORDER BY name ASC"
    )
    fun search(userId: String, query: String): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Delete
    suspend fun delete(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE customers SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: String, timestamp: Long)

    @Query("SELECT * FROM customers WHERE userId = :userId AND syncedAt IS NULL AND isDeleted = 0")
    suspend fun getUnsyncedCustomers(userId: String): List<CustomerEntity>

    @Query("SELECT COUNT(*) FROM customers WHERE userId = :userId AND isDeleted = 0")
    fun getTotalCount(userId: String): Flow<Int>

    @Query("SELECT * FROM customers WHERE userId = :userId AND callbackEnabled = 1 AND isDeleted = 0 ORDER BY name ASC")
    fun getCallbackEnabledCustomers(userId: String): Flow<List<CustomerEntity>>

    @Query("UPDATE customers SET syncedAt = :timestamp WHERE id = :id")
    suspend fun markAsSynced(id: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM customers WHERE userId = :userId AND isDeleted = 0 AND groupId = :groupId")
    fun getGroupMemberCount(userId: String, groupId: String): Flow<Int>
}
