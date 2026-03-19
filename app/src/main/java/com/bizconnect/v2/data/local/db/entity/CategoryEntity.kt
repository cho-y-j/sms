package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int = 0xFF1976D2.toInt(), // default blue
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
