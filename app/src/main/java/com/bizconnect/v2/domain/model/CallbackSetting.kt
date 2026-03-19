package com.bizconnect.v2.domain.model

data class CallbackSetting(
    val userId: String,
    val autoCallbackEnabled: Boolean = false,
    val onEndEnabled: Boolean = false,
    val onEndMessage: String = "",
    val onEndImageUrl: String? = null,
    val onMissedEnabled: Boolean = false,
    val onMissedMessage: String = "",
    val onMissedImageUrl: String? = null,
    val onBusyEnabled: Boolean = false,
    val onBusyMessage: String = "",
    val onBusyImageUrl: String? = null,
    val businessCardEnabled: Boolean = false,
    val businessCardImageUrl: String? = null,
    val throttleInterval: Int = 5000,
    val updatedAt: Long = System.currentTimeMillis()
)
