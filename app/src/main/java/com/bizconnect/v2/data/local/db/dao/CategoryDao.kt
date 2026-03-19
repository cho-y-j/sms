package com.bizconnect.v2.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bizconnect.v2.data.local.db.entity.CategoryEntity
import com.bizconnect.v2.data.local.db.entity.ContactCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    // === Categories ===

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun getAllFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllSync(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    // === Contact-Category mapping ===

    @Query("SELECT categoryId FROM contact_categories WHERE contactPhone = :phone")
    suspend fun getCategoryIdsForContact(phone: String): List<Long>

    @Query("SELECT categoryId FROM contact_categories WHERE contactPhone = :phone")
    fun getCategoryIdsForContactFlow(phone: String): Flow<List<Long>>

    @Query("SELECT contactPhone FROM contact_categories WHERE categoryId = :categoryId")
    suspend fun getContactPhonesForCategory(categoryId: Long): List<String>

    @Query("SELECT contactPhone FROM contact_categories WHERE categoryId = :categoryId")
    fun getContactPhonesForCategoryFlow(categoryId: Long): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addContactToCategory(mapping: ContactCategoryEntity)

    @Query("DELETE FROM contact_categories WHERE contactPhone = :phone AND categoryId = :categoryId")
    suspend fun removeContactFromCategory(phone: String, categoryId: Long)

    @Query("DELETE FROM contact_categories WHERE contactPhone = :phone")
    suspend fun removeContactFromAllCategories(phone: String)

    @Query("SELECT COUNT(*) FROM contact_categories WHERE categoryId = :categoryId")
    suspend fun getContactCountForCategory(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM contact_categories WHERE categoryId = :categoryId")
    fun getContactCountForCategoryFlow(categoryId: Long): Flow<Int>
}
