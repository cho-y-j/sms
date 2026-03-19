package com.bizconnect.v2.domain.model

data class Conversation(
    val threadId: Long,
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
    val draftText: String? = null,
    val photoUri: String? = null
)
