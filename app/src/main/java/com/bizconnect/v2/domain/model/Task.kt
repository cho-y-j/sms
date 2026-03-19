package com.bizconnect.v2.domain.model

data class Task(
    val id: String,
    val userId: String,
    val customerId: String? = null,
    val customerPhone: String,
    val customerName: String?,
    val messageContent: String,
    val type: String,
    val status: String,
    val priority: Int = 0,
    val imageUrl: String? = null,
    val localImagePath: String? = null,
    val isMms: Boolean = false,
    val scheduledAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val isNotified: Boolean = false
) {
    companion object {
        const val TYPE_SEND_SMS = "send_sms"
        const val TYPE_SEND_MMS = "send_mms"
        const val TYPE_CALLBACK_ENDED = "callback_ended"
        const val TYPE_CALLBACK_MISSED = "callback_missed"
        const val TYPE_CALLBACK_BUSY = "callback_busy"

        const val STATUS_PENDING = "pending"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
    }
}
