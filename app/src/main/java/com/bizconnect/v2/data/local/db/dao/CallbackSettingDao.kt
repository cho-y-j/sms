package com.bizconnect.v2.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bizconnect.v2.data.local.db.entity.CallbackSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallbackSettingDao {
    @Query("SELECT * FROM callback_settings WHERE userId = :userId")
    fun get(userId: String): Flow<CallbackSettingEntity?>

    @Query("SELECT * FROM callback_settings WHERE userId = :userId LIMIT 1")
    suspend fun getSync(userId: String): CallbackSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: CallbackSettingEntity)

    @Update
    suspend fun update(setting: CallbackSettingEntity)

    @Delete
    suspend fun delete(setting: CallbackSettingEntity)

    @Query("DELETE FROM callback_settings WHERE userId = :userId")
    suspend fun deleteByUserId(userId: String)

    @Query("UPDATE callback_settings SET autoCallbackEnabled = :enabled WHERE userId = :userId")
    suspend fun updateAutoCallbackEnabled(userId: String, enabled: Boolean)

    @Query("UPDATE callback_settings SET onEndEnabled = :enabled, onEndMessage = :message WHERE userId = :userId")
    suspend fun updateOnEnd(userId: String, enabled: Boolean, message: String)

    @Query("UPDATE callback_settings SET onMissedEnabled = :enabled, onMissedMessage = :message WHERE userId = :userId")
    suspend fun updateOnMissed(userId: String, enabled: Boolean, message: String)

    @Query("UPDATE callback_settings SET onBusyEnabled = :enabled, onBusyMessage = :message WHERE userId = :userId")
    suspend fun updateOnBusy(userId: String, enabled: Boolean, message: String)

    @Query("UPDATE callback_settings SET businessCardEnabled = :enabled, businessCardImageUrl = :imageUrl WHERE userId = :userId")
    suspend fun updateBusinessCard(userId: String, enabled: Boolean, imageUrl: String?)

    @Query("UPDATE callback_settings SET throttleInterval = :interval WHERE userId = :userId")
    suspend fun updateThrottleInterval(userId: String, interval: Int)

    @Query("UPDATE callback_settings SET manualMode = :manual WHERE userId = :userId")
    suspend fun updateManualMode(userId: String, manual: Boolean)

    @Query("UPDATE callback_settings SET blockedNumbers = :blocked WHERE userId = :userId")
    suspend fun updateBlockedNumbers(userId: String, blocked: String)

    @Query("UPDATE callback_settings SET updatedAt = :timestamp WHERE userId = :userId")
    suspend fun updateTimestamp(userId: String, timestamp: Long)

    @Query("SELECT autoCallbackEnabled FROM callback_settings WHERE userId = :userId")
    suspend fun isAutoCallbackEnabled(userId: String): Boolean?

    @Query("SELECT onEndEnabled FROM callback_settings WHERE userId = :userId")
    suspend fun isOnEndEnabled(userId: String): Boolean?
}
