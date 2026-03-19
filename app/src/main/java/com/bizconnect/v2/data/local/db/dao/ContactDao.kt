package com.bizconnect.v2.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bizconnect.v2.data.local.db.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    suspend fun getAllSync(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): ContactEntity?

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phone OR normalizedNumber = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE name LIKE '%' || :name || '%' ORDER BY name ASC")
    fun getByName(name: String): Flow<List<ContactEntity>>

    @Query(
        "SELECT * FROM contacts WHERE " +
        "name LIKE '%' || :query || '%' OR " +
        "phoneNumber LIKE '%' || :query || '%' OR " +
        "normalizedNumber LIKE '%' || :query || '%' " +
        "ORDER BY name ASC"
    )
    fun search(query: String): Flow<List<ContactEntity>>

    @Query(
        "SELECT * FROM contacts WHERE " +
        "name LIKE '%' || :query || '%' OR " +
        "phoneNumber LIKE '%' || :query || '%' OR " +
        "normalizedNumber LIKE '%' || :query || '%' " +
        "ORDER BY name ASC LIMIT 20"
    )
    suspend fun searchSync(query: String): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM contacts")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT * FROM contacts WHERE photoUri IS NOT NULL ORDER BY name ASC")
    fun getContactsWithPhoto(): Flow<List<ContactEntity>>

    @Query("UPDATE contacts SET lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateLastUpdated(id: Long, timestamp: Long)

    @Query("SELECT * FROM contacts WHERE lastUpdated < :olderThan ORDER BY lastUpdated ASC")
    suspend fun getOldContacts(olderThan: Long): List<ContactEntity>
}
