package com.bizconnect.v2.data.remote.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class TaskDto(
    val id: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val message: String,
    val scheduledTime: Long,
    val status: String, // PENDING, SENT, FAILED, CANCELLED
    val messageType: String, // SMS, WHATSAPP, EMAIL
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val approvedAt: Long? = null,
    val sentAt: Long? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class CreateTaskRequest(
    val customerId: String,
    val message: String,
    val scheduledTime: Long,
    val messageType: String = "SMS",
    val tags: List<String> = emptyList()
)

@Serializable
data class BatchTaskRequest(
    val tasks: List<CreateTaskRequest>,
    val priority: String = "NORMAL" // LOW, NORMAL, HIGH
)

@Serializable
data class UpdateStatusRequest(
    val status: String,
    val errorMessage: String? = null
)
