package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "daily_limits",
    primaryKeys = ["userId", "date"],
    indices = [Index("userId"), Index("date")]
)
data class DailyLimitEntity(
    val userId: String,
    val date: String,
    val sentCount: Int = 0,
    val limitMode: String = "safe",
    val updatedAt: Long = System.currentTimeMillis()
)
