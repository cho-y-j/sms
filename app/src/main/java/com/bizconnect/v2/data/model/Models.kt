package com.bizconnect.v2.data.model

// Data models for BizConnect V2

data class Conversation(
    val id: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int,
    val isPinned: Boolean
)

data class Message(
    val id: String,
    val conversationId: String,
    val senderName: String,
    val body: String,
    val timestamp: Long,
    val isIncoming: Boolean,
    val isRead: Boolean,
    val attachmentUrl: String? = null
)

data class Task(
    val id: String,
    val conversationId: String,
    val title: String,
    val description: String,
    val dueDate: Long,
    val isCompleted: Boolean,
    val createdAt: Long,
    val completedAt: Long? = null
)

data class SpamFilter(
    val id: String,
    val blockedNumber: String? = null,
    val filterType: String,
    val reason: String,
    val keyword: String? = null,
    val createdAt: Long
)

data class Customer(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val category: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val lastUpdated: Long
)
