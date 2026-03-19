package com.bizconnect.v2.domain.model

data class SmsLog(
    val id: String,
    val userId: String,
    val taskId: String? = null,
    val phoneNumber: String,
    val message: String,
    val type: String,
    val status: String,
    val isMms: Boolean = false,
    val imageUrl: String? = null,
    val sentAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null
) {
    companion object {
        const val STATUS_SENT = "sent"
        const val STATUS_FAILED = "failed"
    }
}
