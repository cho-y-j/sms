package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scheduled_messages",
    indices = [Index("userId"), Index("scheduledAt")]
)
data class ScheduledMessageEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val recipients: String,
    val recipientNames: String?,
    val message: String,
    val imageUrl: String? = null,
    val localImagePath: String? = null,
    val isMms: Boolean = false,
    val scheduledAt: Long,
    val repeatType: String = "none",
    val isActive: Boolean = true,
    val lastSentAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
