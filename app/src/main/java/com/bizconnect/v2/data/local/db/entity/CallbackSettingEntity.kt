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
    // 발송 방식: false=자동 발송, true=수동(통화 종료 후 알림으로 확인 뒤 발송)
    val manualMode: Boolean = false,
    // 자동발송 금지 번호 (콤마 구분, 정규화된 번호). 자동·수동 모드 모두에서 제외.
    val blockedNumbers: String = "",
    val excludedCategoryIds: String = "",
    val onEndTemplateId: Long? = null,
    val onMissedTemplateId: Long? = null,
    val onBusyTemplateId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
