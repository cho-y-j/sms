package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity

@Entity(tableName = "ai_usage", primaryKeys = ["date"])
data class AiUsageEntity(
    val date: String, // "2026-03-19"
    val tokensUsed: Int = 0,
    val requestCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
