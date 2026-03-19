package com.bizconnect.v2.data.repository

import com.bizconnect.v2.data.local.db.BizConnectDatabase
import com.bizconnect.v2.data.local.db.entity.CallbackSettingEntity
import com.bizconnect.v2.domain.model.CallbackSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CallbackSettingRepository(private val database: BizConnectDatabase) {
    private val callbackSettingDao = database.callbackSettingDao()

    fun get(userId: String): Flow<CallbackSetting?> =
        callbackSettingDao.get(userId).map { entity ->
            entity?.toDomain()
        }

    suspend fun getSync(userId: String): CallbackSetting? =
        callbackSettingDao.getSync(userId)?.toDomain()

    suspend fun insert(setting: CallbackSetting) =
        callbackSettingDao.insert(setting.toEntity())

    suspend fun update(setting: CallbackSetting) =
        callbackSettingDao.update(setting.toEntity())

    suspend fun delete(setting: CallbackSetting) =
        callbackSettingDao.delete(setting.toEntity())

    suspend fun deleteByUserId(userId: String) =
        callbackSettingDao.deleteByUserId(userId)

    suspend fun updateAutoCallbackEnabled(userId: String, enabled: Boolean) =
        callbackSettingDao.updateAutoCallbackEnabled(userId, enabled)

    suspend fun updateOnEnd(userId: String, enabled: Boolean, message: String) =
        callbackSettingDao.updateOnEnd(userId, enabled, message)

    suspend fun updateOnMissed(userId: String, enabled: Boolean, message: String) =
        callbackSettingDao.updateOnMissed(userId, enabled, message)

    suspend fun updateOnBusy(userId: String, enabled: Boolean, message: String) =
        callbackSettingDao.updateOnBusy(userId, enabled, message)

    suspend fun updateBusinessCard(userId: String, enabled: Boolean, imageUrl: String?) =
        callbackSettingDao.updateBusinessCard(userId, enabled, imageUrl)

    suspend fun updateThrottleInterval(userId: String, interval: Int) =
        callbackSettingDao.updateThrottleInterval(userId, interval)

    suspend fun updateTimestamp(userId: String) =
        callbackSettingDao.updateTimestamp(userId, System.currentTimeMillis())

    suspend fun isAutoCallbackEnabled(userId: String): Boolean =
        callbackSettingDao.isAutoCallbackEnabled(userId) ?: false

    suspend fun isOnEndEnabled(userId: String): Boolean =
        callbackSettingDao.isOnEndEnabled(userId) ?: false

    suspend fun getOrCreateDefault(userId: String): CallbackSetting {
        val existing = getSync(userId)
        return existing ?: CallbackSetting(userId = userId).also {
            insert(it)
        }
    }

    private fun CallbackSettingEntity.toDomain() = CallbackSetting(
        userId = userId,
        autoCallbackEnabled = autoCallbackEnabled,
        onEndEnabled = onEndEnabled,
        onEndMessage = onEndMessage,
        onEndImageUrl = onEndImageUrl,
        onMissedEnabled = onMissedEnabled,
        onMissedMessage = onMissedMessage,
        onMissedImageUrl = onMissedImageUrl,
        onBusyEnabled = onBusyEnabled,
        onBusyMessage = onBusyMessage,
        onBusyImageUrl = onBusyImageUrl,
        businessCardEnabled = businessCardEnabled,
        businessCardImageUrl = businessCardImageUrl,
        throttleInterval = throttleInterval,
        updatedAt = updatedAt
    )

    private fun CallbackSetting.toEntity() = CallbackSettingEntity(
        userId = userId,
        autoCallbackEnabled = autoCallbackEnabled,
        onEndEnabled = onEndEnabled,
        onEndMessage = onEndMessage,
        onEndImageUrl = onEndImageUrl,
        onMissedEnabled = onMissedEnabled,
        onMissedMessage = onMissedMessage,
        onMissedImageUrl = onMissedImageUrl,
        onBusyEnabled = onBusyEnabled,
        onBusyMessage = onBusyMessage,
        onBusyImageUrl = onBusyImageUrl,
        businessCardEnabled = businessCardEnabled,
        businessCardImageUrl = businessCardImageUrl,
        throttleInterval = throttleInterval,
        updatedAt = updatedAt
    )
}
