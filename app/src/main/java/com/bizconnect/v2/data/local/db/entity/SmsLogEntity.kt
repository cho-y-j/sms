package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_logs",
    indices = [Index("userId"), Index("taskId"), Index("sentAt")]
)
data class SmsLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val taskId: String? = null,
    val phoneNumber: String,
    val message: String,
    val type: String,
    val status: String,
    val isMms: Boolean = false,
    val imageUrl: String? = null,
    val sentAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null
)
