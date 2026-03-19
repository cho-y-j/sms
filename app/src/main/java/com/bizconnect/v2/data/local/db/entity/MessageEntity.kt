package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["threadId"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("threadId"), Index("timestamp"), Index("systemSmsId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val systemSmsId: Long = 0,
    val address: String,
    val body: String?,
    val timestamp: Long,
    val type: Int,
    val read: Boolean = false,
    val seen: Boolean = false,
    val status: Int = -1,
    val isMms: Boolean = false,
    val mmsSubject: String? = null,
    val mmsContentType: String? = null,
    val attachmentPath: String? = null,
    val attachmentMimeType: String? = null,
    val simSlot: Int = 0,
    val isLocked: Boolean = false
)
