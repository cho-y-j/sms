package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index("userId"), Index("status"), Index("scheduledAt")]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val customerId: String? = null,
    val customerPhone: String,
    val customerName: String?,
    val messageContent: String,
    val type: String,
    val status: String,
    val priority: Int = 0,
    val imageUrl: String? = null,
    val localImagePath: String? = null,
    val isMms: Boolean = false,
    val scheduledAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val isNotified: Boolean = false
)
