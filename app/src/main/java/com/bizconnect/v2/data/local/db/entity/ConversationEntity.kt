package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val threadId: Long,
    val recipientAddress: String,
    val recipientName: String?,
    val snippet: String,
    val snippetType: Int,
    val messageCount: Int,
    val unreadCount: Int,
    val lastMessageTimestamp: Long,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isBlocked: Boolean = false,
    val isArchived: Boolean = false,
    val read: Boolean = true,
    val draftText: String? = null,
    val photoUri: String? = null,
    val aiSummary: String? = null,
    val aiSummaryDate: Long? = null,
    val aiEmotion: String? = null
)
