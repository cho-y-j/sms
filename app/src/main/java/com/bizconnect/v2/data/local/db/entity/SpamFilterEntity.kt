package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "spam_filters",
    indices = [Index("type"), Index("isActive")]
)
data class SpamFilterEntity(
    @PrimaryKey val id: String,
    val type: String,
    val pattern: String,
    val value: String = pattern, // For backward compatibility
    val reason: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "system"
)
