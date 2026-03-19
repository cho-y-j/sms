package com.bizconnect.v2.domain.model

data class Message(
    val id: Long = 0,
    val threadId: Long,
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
) {
    companion object {
        const val TYPE_INBOX = 1
        const val TYPE_SENT = 2
        const val TYPE_DRAFT = 3
        const val TYPE_OUTBOX = 4
        const val TYPE_FAILED = 5
        const val TYPE_QUEUED = 6

        const val STATUS_NONE = -1
        const val STATUS_COMPLETE = 0
        const val STATUS_PENDING = 32
        const val STATUS_FAILED = 64
    }
}
