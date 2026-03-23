package com.bizconnect.v2.domain.engine

import android.content.Context
import android.net.Uri
import android.telephony.TelephonyManager
import android.util.Log
import com.bizconnect.v2.data.local.db.dao.CallbackSettingDao
import com.bizconnect.v2.data.local.db.dao.CategoryDao
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.dao.CustomerDao
import com.bizconnect.v2.data.local.db.dao.MessageTemplateDao
import com.bizconnect.v2.data.local.db.dao.SmsLogDao
import com.bizconnect.v2.data.local.db.entity.SmsLogEntity
import com.bizconnect.v2.data.preferences.AppPreferences
import com.bizconnect.v2.util.PhoneNumberUtil
import com.bizconnect.v2.util.SmsSender
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages auto-callback SMS/MMS sending after phone calls.
 *
 * Flow:
 * 1. CallStateReceiver detects call state change
 * 2. CallDetector determines event type (ended/missed/busy)
 * 3. Loads callback settings from Room DB
 * 4. Checks if callback is enabled for this event type
 * 5. Finds customer info by phone number
 * 6. Processes template variables
 * 7. Sends SMS or MMS (with business card image)
 * 8. Logs the callback
 */
@Singleton
class CallbackEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsEngine: SmsEngine,
    private val smsSender: SmsSender,
    private val templateEngine: TemplateEngine,
    private val callbackSettingDao: CallbackSettingDao,
    private val categoryDao: CategoryDao,
    private val contactDao: ContactDao,
    private val customerDao: CustomerDao,
    private val messageTemplateDao: MessageTemplateDao,
    private val smsLogDao: SmsLogDao,
    private val appPreferences: AppPreferences,
    private val phoneNumberUtil: PhoneNumberUtil
) {
    companion object {
        private const val TAG = "CallbackEngine"
        private const val CALLBACK_TYPE = "callback"
    }

    // Throttle map to prevent duplicate callbacks
    private val lastCallbackTime = mutableMapOf<String, Long>()

    /**
     * Process callback for a call event
     */
    suspend fun processCallback(event: CallDetector.CallEvent): CallbackResult {
        return withContext(Dispatchers.IO) {
            try {
                // Get user ID from preferences (default to "default" for local-only mode)
                val userId = appPreferences.getUserId() ?: "default"

                // Check master toggle first
                val settings = callbackSettingDao.getSync(userId)
                if (settings == null || !settings.autoCallbackEnabled) {
                    Log.d(TAG, "Auto callback is OFF")
                    return@withContext CallbackResult(
                        success = false, eventType = event.type,
                        phoneNumber = event.phoneNumber,
                        message = "Auto callback disabled", error = null
                    )
                }

                // Check if callback should be sent for this contact
                if (!shouldSendCallback(event.phoneNumber, userId)) {
                    Log.d(TAG, "Callback disabled for ${event.phoneNumber}")
                    return@withContext CallbackResult(
                        success = false,
                        eventType = event.type,
                        phoneNumber = event.phoneNumber,
                        message = "Callback disabled for this contact",
                        error = null
                    )
                }

                // Check throttle
                val normalizedNumber = phoneNumberUtil.normalizeNumber(event.phoneNumber)
                val throttleKey = "$normalizedNumber:${event.type}"
                val lastTime = lastCallbackTime[throttleKey] ?: 0L
                val setting = callbackSettingDao.getSync(userId)
                val throttleInterval = (setting?.throttleInterval ?: 5000).toLong()

                if (System.currentTimeMillis() - lastTime < throttleInterval) {
                    Log.d(TAG, "Callback throttled for $normalizedNumber")
                    return@withContext CallbackResult(
                        success = false,
                        eventType = event.type,
                        phoneNumber = event.phoneNumber,
                        message = "Callback throttled",
                        error = null
                    )
                }

                lastCallbackTime[throttleKey] = System.currentTimeMillis()

                // Load settings
                val callbackSetting = callbackSettingDao.getSync(userId) ?: run {
                    Log.d(TAG, "No callback settings found for user")
                    return@withContext CallbackResult(
                        success = false,
                        eventType = event.type,
                        phoneNumber = event.phoneNumber,
                        message = "No callback settings configured",
                        error = null
                    )
                }

                // Check if callback is enabled for this event type
                val isEnabledForEvent = when (event.type) {
                    CallbackEventType.ENDED -> callbackSetting.onEndEnabled
                    CallbackEventType.MISSED -> callbackSetting.onMissedEnabled
                    CallbackEventType.BUSY -> callbackSetting.onBusyEnabled
                    CallbackEventType.OUTGOING -> callbackSetting.onOutgoingEnabled
                }

                if (!isEnabledForEvent) {
                    Log.d(TAG, "Callback not enabled for event type: ${event.type}")
                    return@withContext CallbackResult(
                        success = false,
                        eventType = event.type,
                        phoneNumber = event.phoneNumber,
                        message = "Callback not enabled for this event type",
                        error = null
                    )
                }

                // Build callback message
                val callbackMessage = buildCallbackMessage(
                    eventType = event.type,
                    phoneNumber = event.phoneNumber,
                    setting = callbackSetting
                )

                // Send callback
                val sendResult = sendCallback(callbackMessage)

                // Log callback
                logCallback(event.type, event.phoneNumber, sendResult)

                return@withContext CallbackResult(
                    success = sendResult.success,
                    eventType = event.type,
                    phoneNumber = event.phoneNumber,
                    message = callbackMessage.body,
                    error = sendResult.errorMessage
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error processing callback", e)
                return@withContext CallbackResult(
                    success = false,
                    eventType = event.type,
                    phoneNumber = event.phoneNumber,
                    message = null,
                    error = e.message
                )
            }
        }
    }

    /**
     * Check if callback should be sent for this contact/group
     */
    private suspend fun shouldSendCallback(
        phoneNumber: String,
        userId: String
    ): Boolean {
        return try {
            val normalizedNumber = phoneNumberUtil.normalizeNumber(phoneNumber)
            val customer = customerDao.getByPhone(normalizedNumber)

            // If customer exists, check if callback is enabled for them
            if (customer != null && !customer.callbackEnabled) {
                return false
            }

            // Check category exclusion
            val settings = callbackSettingDao.getSync(userId)
            if (settings != null && settings.excludedCategoryIds.isNotBlank()) {
                val excludedIds = settings.excludedCategoryIds.split(",").mapNotNull { it.trim().toLongOrNull() }
                if (excludedIds.isNotEmpty()) {
                    val callerCategories = categoryDao.getCategoryIdsForContact(normalizedNumber)
                    if (callerCategories.any { it in excludedIds }) {
                        Log.d(TAG, "Callback skipped: contact $normalizedNumber belongs to excluded category")
                        return false
                    }
                }
            }

            true
        } catch (e: Exception) {
            Log.w(TAG, "Error checking callback eligibility", e)
            true // Default to allowing if check fails
        }
    }

    /**
     * Build callback message with template variables filled
     */
    private suspend fun buildCallbackMessage(
        eventType: CallbackEventType,
        phoneNumber: String,
        setting: com.bizconnect.v2.data.local.db.entity.CallbackSettingEntity
    ): CallbackMessage {
        return withContext(Dispatchers.IO) {
            try {
                // Get message template and template ID for this event type
                val messageTemplate = when (eventType) {
                    CallbackEventType.ENDED -> setting.onEndMessage
                    CallbackEventType.MISSED -> setting.onMissedMessage
                    CallbackEventType.BUSY -> setting.onBusyMessage
                    CallbackEventType.OUTGOING -> setting.onOutgoingMessage
                }
                val templateId = when (eventType) {
                    CallbackEventType.ENDED -> setting.onEndTemplateId
                    CallbackEventType.MISSED -> setting.onMissedTemplateId
                    CallbackEventType.BUSY -> setting.onBusyTemplateId
                    CallbackEventType.OUTGOING -> setting.onOutgoingTemplateId
                }

                // Get contact name: CustomerDao → ContactDao → PhoneLookup
                val normalizedNumber = phoneNumberUtil.normalizeNumber(phoneNumber)
                val customer = customerDao.getByPhone(normalizedNumber)
                val contactName = customer?.name
                    ?: contactDao.getByPhone(normalizedNumber)?.name
                    ?: contactDao.getByPhone(phoneNumber)?.name
                    ?: lookupContactName(phoneNumber)

                Log.d(TAG, "Contact lookup for $phoneNumber: name=$contactName")

                // Build template context
                val templateContext = TemplateEngine.TemplateContext(
                    customerName = contactName,
                    phoneNumber = normalizedNumber,
                    companyName = customer?.industryType,
                    managerName = null,
                    customFields = emptyMap(),
                    referenceDate = System.currentTimeMillis()
                )

                // Process template
                val messageBody = templateEngine.process(messageTemplate, templateContext)

                // Determine image: template image → business card image → none
                var imageUri: Uri? = null

                // 1. Check template image
                if (templateId != null) {
                    val template = messageTemplateDao.getById(templateId)
                    if (template?.imageUri != null) {
                        val imgPath = template.imageUri
                        imageUri = if (imgPath.startsWith("/")) {
                            Uri.fromFile(java.io.File(imgPath))
                        } else {
                            Uri.parse(imgPath)
                        }
                    }
                }

                // 2. Fallback: business card image
                if (imageUri == null && setting.businessCardEnabled && setting.businessCardImageUrl != null) {
                    val cardPath = setting.businessCardImageUrl ?: ""
                    if (cardPath.isNotBlank()) {
                        imageUri = if (cardPath.startsWith("/")) {
                            Uri.fromFile(java.io.File(cardPath))
                        } else {
                            Uri.parse(cardPath)
                        }
                    }
                }

                val isMms = imageUri != null

                CallbackMessage(
                    address = phoneNumber,
                    body = messageBody,
                    imageUri = imageUri,
                    isMms = isMms,
                    eventType = eventType
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error building callback message", e)
                CallbackMessage(
                    address = phoneNumber,
                    body = "통화 감사합니다.",
                    imageUri = null,
                    isMms = false,
                    eventType = eventType
                )
            }
        }
    }

    /**
     * Send the callback via SmsSender (saves to Room DB + conversation list).
     * If MMS fails (e.g. WiFi only, no cellular), falls back to SMS text only.
     */
    private suspend fun sendCallback(message: CallbackMessage): SendResult {
        return try {
            var threadId = -1L
            var sentAsMms = false

            // Try MMS first if image attached
            if (message.isMms && message.imageUri != null) {
                threadId = smsSender.sendMmsWithImage(message.address, message.body, message.imageUri)
                sentAsMms = threadId > 0
            }

            // MMS failed or no image → send as SMS
            if (threadId <= 0) {
                Log.d(TAG, "Sending callback as SMS (MMS ${if (message.isMms) "failed" else "not needed"})")
                threadId = smsSender.sendSms(message.address, message.body)
            }

            if (threadId > 0) {
                SendResult(
                    success = true,
                    messageType = if (sentAsMms) MessageType.MMS else MessageType.SMS,
                    errorMessage = null,
                    errorCode = 0
                )
            } else {
                SendResult(
                    success = false,
                    messageType = MessageType.SMS,
                    errorMessage = "Send failed",
                    errorCode = -1
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending callback message", e)
            SendResult(
                success = false,
                messageType = MessageType.SMS,
                errorMessage = e.message,
                errorCode = -1
            )
        }
    }

    /**
     * Log callback to database
     */
    private suspend fun logCallback(
        eventType: CallbackEventType,
        phoneNumber: String,
        result: SendResult
    ) {
        return withContext(Dispatchers.IO) {
            try {
                val userId = appPreferences.getUserId() ?: return@withContext
                val logId = UUID.randomUUID().toString()

                val smsLog = SmsLogEntity(
                    id = logId,
                    userId = userId,
                    taskId = null,
                    phoneNumber = phoneNumber,
                    message = "Auto-callback (${eventType.name})",
                    type = CALLBACK_TYPE,
                    status = if (result.success) "sent" else "failed",
                    isMms = result.messageType == MessageType.MMS,
                    imageUrl = null,
                    sentAt = System.currentTimeMillis(),
                    syncedAt = null
                )

                smsLogDao.insert(smsLog)
                Log.d(TAG, "Callback logged: $logId for $phoneNumber")
            } catch (e: Exception) {
                Log.e(TAG, "Error logging callback", e)
            }
        }
    }

    /**
     * Lookup contact name from system contacts (fallback when not in Room DB)
     */
    private fun lookupContactName(phone: String): String? {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phone)
            )
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: Exception) { null }
    }
}

enum class CallbackEventType {
    ENDED,      // 수신 통화 종료 후
    MISSED,     // 부재중 전화
    BUSY,       // 통화중 거절
    OUTGOING    // 발신 통화 종료 후
}

data class CallbackMessage(
    val address: String,
    val body: String,
    val imageUri: Uri?,
    val isMms: Boolean,
    val eventType: CallbackEventType
)

data class CallbackResult(
    val success: Boolean,
    val eventType: CallbackEventType,
    val phoneNumber: String,
    val message: String?,
    val error: String? = null
)
