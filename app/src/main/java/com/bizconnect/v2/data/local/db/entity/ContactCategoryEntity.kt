package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "contact_categories",
    primaryKeys = ["contactPhone", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId"), Index("contactPhone")]
)
data class ContactCategoryEntity(
    val contactPhone: String,  // normalized phone number
    val categoryId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
