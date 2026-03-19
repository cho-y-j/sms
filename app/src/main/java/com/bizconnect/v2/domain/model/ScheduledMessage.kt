package com.bizconnect.v2.domain.model

data class ScheduledMessage(
    val id: String,
    val userId: String,
    val recipients: List<String>,
    val recipientNames: List<String>?,
    val message: String,
    val imageUrl: String? = null,
    val localImagePath: String? = null,
    val isMms: Boolean = false,
    val scheduledAt: Long,
    val repeatType: String = "none",
    val isActive: Boolean = true,
    val lastSentAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val REPEAT_NONE = "none"
        const val REPEAT_DAILY = "daily"
        const val REPEAT_WEEKLY = "weekly"
        const val REPEAT_MONTHLY = "monthly"
        const val REPEAT_YEARLY = "yearly"
    }
}
