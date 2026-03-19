package com.bizconnect.v2.data.remote.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SendSmsRequest(
    val customerId: String,
    val message: String,
    val scheduledTime: Long? = null,
    val priority: String = "NORMAL"
)

@Serializable
data class SendSmsResponse(
    val success: Boolean,
    val taskId: String,
    val message: String,
    val scheduledTime: Long? = null
)

@Serializable
data class SmsLogDto(
    val id: String,
    val taskId: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val message: String,
    val status: String, // SENT, FAILED, PENDING
    val sentAt: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long
)

@Serializable
data class PaginatedResponse<T>(
    val success: Boolean,
    val data: List<T> = emptyList(),
    val pagination: PaginationInfo
)

@Serializable
data class PaginationInfo(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int
)

@Serializable
data class SmsStatsDto(
    val totalSent: Int,
    val totalFailed: Int,
    val totalPending: Int,
    val successRate: Double,
    val lastSentAt: Long? = null
)

@Serializable
data class DailyLimitDto(
    val daily: Int,
    val used: Int,
    val remaining: Int,
    val resetAt: Long,
    val isPremium: Boolean
)
