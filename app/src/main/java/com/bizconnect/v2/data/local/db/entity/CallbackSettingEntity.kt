package com.bizconnect.v2.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "callback_settings")
data class CallbackSettingEntity(
    @PrimaryKey val userId: String,
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
    val onOutgoingEnabled: Boolean = false,
    val onOutgoingMessage: String = "",
    val onOutgoingImageUrl: String? = null,
    val onOutgoingTemplateId: Long? = null,
    val businessCardEnabled: Boolean = false,
    val businessCardImageUrl: String? = null,
    val throttleInterval: Int = 5000,
    val excludedCategoryIds: String = "",
    val onEndTemplateId: Long? = null,
    val onMissedTemplateId: Long? = null,
    val onBusyTemplateId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
