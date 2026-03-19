package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [Index("phoneNumber"), Index("normalizedNumber")]
)
data class ContactEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val photoUri: String?,
    val thumbnailUri: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)
