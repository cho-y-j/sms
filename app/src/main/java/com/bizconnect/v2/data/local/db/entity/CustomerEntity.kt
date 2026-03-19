package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [Index("userId"), Index("normalizedPhone"), Index("groupId")]
)
data class CustomerEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val phone: String,
    val normalizedPhone: String,
    val groupId: String? = null,
    val groupName: String? = null,
    val birthday: String? = null,
    val anniversary: String? = null,
    val memo: String? = null,
    val industryType: String? = null,
    val callbackEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)
