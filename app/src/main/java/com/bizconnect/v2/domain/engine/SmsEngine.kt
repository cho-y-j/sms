package com.bizconnect.v2.domain.engine

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * Core SMS/LMS/MMS sending engine.
 * As the default SMS app, this has full system-level access to the SMS/MMS framework.
 *
 * Handles:
 * - SMS: Text messages ≤ 160 characters (or 70 Korean chars)
 * - LMS: Long messages via multipart SMS or MMS framework (> 160 chars)
 * - MMS: Messages with image attachments
 */
class SmsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository,
    private val smsLogDao: SmsLogDao,
    private val dailyLimitDao: DailyLimitDao,
    private val appPreferences: AppPreferences,
    private val mmsHelper: MmsHelper
) {
    companion object {
        const val SMS_MAX_LENGTH = 70       // Korean characters
        const val SMS_MAX_BYTES = 160       // bytes for standard alphabet
        const val SMS_KOREAN_MAX_BYTES = 90 // bytes for Korean (3 bytes per char)
        const val LMS_MAX_LENGTH = 2000     // Korean characters
        const val MMS_MAX_SIZE = 300 * 1024 // 300KB image limit

        private const val TAG = "SmsEngine"
    }

    /**
     * Determine message type based on content and attachments
     */
    fun getMessageType(body: String, hasImage: Boolean): MessageType {
        if (hasImage) return MessageType.MMS

        val byteLength = body.toByteArray(Charsets.UTF_8).size
        return if (byteLength > SMS_MAX_BYTES) MessageType.LMS else MessageType.SMS
    }

    /**
     * Send SMS (short message ≤ 160 bytes / 70 Korean characters)
     * Uses SmsManager.sendTextMessage directly for single segment
     */
    suspend fun sendSms(
        address: String,
        body: String,
        subscriptionId: Int = SmsManager.getDefaultSmsSubscriptionId()
    ): SendResult = withContext(Dispatchers.IO) {
        return@withContext try {
            // Validate inputs
            if (address.isBlank() || body.isBlank()) {
                return@withContext SendResult(
                    success = false,
                    messageType = MessageType.SMS,
                    errorMessage = "Empty address or body",
                    errorCode = -1
                )
            }

            // Check daily limit
            val userId = appPreferences.getUserId() ?: "default" ?: "default"
            val limitStatus = checkDailyLimit(userId)
            if (limitStatus.isLimitReached) {
                return@withContext SendResult(
                    success = false,
                    messageType = MessageType.SMS,
                    errorMessage = "Daily limit reached: ${limitStatus.currentCount}/${limitStatus.maxCount}",
                    errorCode = -2
                )
            }

            val smsManager = getSmsManager(subscriptionId)
            val messageId = UUID.randomUUID().toString()

            val sentIntent = createPendingIntent(messageId, "SMS_SENT")
            val deliveryIntent = createPendingIntent(messageId, "SMS_DELIVERED")

            smsManager.sendTextMessage(
                address,
                null, // no sender specified
                body,
                sentIntent,
                deliveryIntent
            )

            // Log message
            logMessage(messageId, address, body, MessageType.SMS, success = true)
            incrementDailyCount(userId)

            SendResult(
                success = true,
                messageId = messageId.hashCode().toLong(),
                messageType = MessageType.SMS,
                errorMessage = null,
                errorCode = null
            )
        } catch (e: SecurityException) {
            SendResult(
                success = false,
                messageType = MessageType.SMS,
                errorMessage = "Permission denied: ${e.message}",
                errorCode = -3
            )
        } catch (e: Exception) {
            SendResult(
                success = false,
                messageType = MessageType.SMS,
                errorMessage = "Send failed: ${e.message}",
                errorCode = -4
            )
        }
    }

    /**
     * Send LMS (Long Message Service > 160 bytes / 70 Korean characters)
     * For Korean carriers: uses multipart SMS if ≤ 3 parts, otherwise triggers MMS framework
     * System handles conversion to carrier-specific format
     */
    suspend fun sendLms(
        address: String,
        body: String,
        subscriptionId: Int = SmsManager.getDefaultSmsSubscriptionId()
    ): SendResult = withContext(Dispatchers.IO) {
        return@withContext try {
            if (address.isBlank() || body.isBlank()) {
                return@withContext SendResult(
                    success = false,
                    messageType = MessageType.LMS,
                    errorMessage = "Empty address or body",
                    errorCode = -1
                )
            }

            val userId = appPreferences.getUserId() ?: "default"
            val limitStatus = checkDailyLimit(userId)
            if (limitStatus.isLimitReached) {
                return@withContext SendResult(
                    success = false,
                    messageType = MessageType.LMS,
                    errorMessage = "Daily limit reached",
                    errorCode = -2
                )
            }

            val smsManager = getSmsManager(subscriptionId)
            val messageId = UUID.randomUUID().toString()

            // Split message into parts (153 bytes per part for multipart)
            val parts = smsManager.divideMessage(body)

            // Create pending intents for each part
            val sentIntents = ArrayList<PendingIntent>()
            val deliveryIntents = ArrayList<PendingIntent>()

            repeat(parts.size) { index ->
                sentIntents.add(createPendingIntent("$messageId-$index", "SMS_SENT"))
                deliveryIntents.add(createPendingIntent("$messageId-$index", "SMS_DELIVERED"))
            }

            // Send multipart message
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                smsManager.sendMultipartTextMessage(
                    address,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                // Fallback for older API levels
                for (i in parts.indices) {
                    smsManager.sendTextMessage(
                        address,
                        null,
                        parts[i],
                        sentIntents[i],
                        deliveryIntents[i]
                    )
                }
            }

            // Log message
            logMessage(messageId, address, body, MessageType.LMS, success = true)
            incrementDailyCount(userId)

            SendResult(
                success = true,
                messageId = messageId.hashCode().toLong(),
                messageType = MessageType.LMS,
                errorMessage = null,
                errorCode = null
            )
        } catch (e: SecurityException) {
            SendResult(
                success = false,
                messageType = MessageType.LMS,
                errorMessage = "Permission denied: ${e.message}",
                errorCode = -3
            )
        } catch (e: Exception) {
            SendResult(
                success = false,
                messageType = MessageType.LMS,
                errorMessage = "Send failed: ${e.message}",
                errorCode = -4
            )
        }
    }

    /**
     * Send MMS (Multimedia Message Service with image attachment)
     * Uses SmsManager.sendMultimediaMessage() (API 21+)
     * As default SMS app, we have access to system MMS PDU composition
     */
    suspend fun sendMms(
        address: String,
        body: String,
        imageUri: android.net.Uri,
        subscriptionId: Int = SmsManager.getDefaultSmsSubscriptionId()
    ): SendResult = withContext(Dispatchers.IO) {
        return@withContext try {
            if (address.isBlank()) {
                return@withContext SendResult(
                    success = false,
                    messageType = MessageType.MMS,
                    errorMessage = "Empty address",
                    errorCode = -1
                )
            }

            val userId = appPreferences.getUserId() ?: "default"
            val limitStatus = checkDailyLimit(userId)
            if (limitStatus.isLimitReached) {
                return@withContext SendResult(
                    success = false,
                    messageType = MessageType.MMS,
                    errorMessage = "Daily limit reached",
                    errorCode = -2
                )
            }

            // Compress image if needed
            val compressedImageUri = mmsHelper.compressImage(imageUri, MMS_MAX_SIZE)

            // Build MMS PDU
            val mmsPdu = mmsHelper.buildMmsPdu(
                recipientAddress = address,
                subject = null,
                body = body,
                imageUri = compressedImageUri,
                imageMimeType = "image/jpeg"
            )

            val smsManager = getSmsManager(subscriptionId)
            val messageId = UUID.randomUUID().toString()

            val sentIntent = createPendingIntent(messageId, "MMS_SENT")
            val deliveryIntent = createPendingIntent(messageId, "MMS_DELIVERED")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                smsManager.sendMultimediaMessage(
                    context,
                    compressedImageUri,
                    null,
                    null,
                    sentIntent
                )
            } else {
                return@withContext SendResult(
                    success = false,
                    messageType = MessageType.MMS,
                    errorMessage = "MMS requires API 21+",
                    errorCode = -5
                )
            }

            // Save sent MMS to conversation thread
            val threadId = getOrCreateThreadId(address)
            mmsHelper.saveSentMms(address, body, compressedImageUri, threadId)

            // Log message
            logMessage(messageId, address, body, MessageType.MMS, success = true)
            incrementDailyCount(userId)

            SendResult(
                success = true,
                messageId = messageId.hashCode().toLong(),
                messageType = MessageType.MMS,
                errorMessage = null,
                errorCode = null
            )
        } catch (e: SecurityException) {
            SendResult(
                success = false,
                messageType = MessageType.MMS,
                errorMessage = "Permission denied: ${e.message}",
                errorCode = -3
            )
        } catch (e: Exception) {
            SendResult(
                success = false,
                messageType = MessageType.MMS,
                errorMessage = "Send failed: ${e.message}",
                errorCode = -4
            )
        }
    }

    /**
     * Send message with automatic type detection based on content and attachments
     */
    suspend fun sendMessage(
        address: String,
        body: String,
        imageUri: android.net.Uri? = null,
        subscriptionId: Int = SmsManager.getDefaultSmsSubscriptionId()
    ): SendResult = withContext(Dispatchers.IO) {
        val messageType = getMessageType(body, imageUri != null)

        return@withContext when {
            messageType == MessageType.MMS && imageUri != null -> {
                sendMms(address, body, imageUri, subscriptionId)
            }
            messageType == MessageType.LMS -> {
                sendLms(address, body, subscriptionId)
            }
            else -> {
                sendSms(address, body, subscriptionId)
            }
        }
    }

    /**
     * Check daily sending limit for user
     */
    suspend fun checkDailyLimit(userId: String): DailyLimitStatus = withContext(Dispatchers.IO) {
        val maxCount = appPreferences.getDailyLimitCount()
        val limitMode = appPreferences.getLimitMode()

        val currentCount = smsLogDao.getDailyCount(userId)
        val isLimitReached = currentCount >= maxCount

        DailyLimitStatus(
            currentCount = currentCount,
            maxCount = maxCount,
            limitMode = limitMode,
            isLimitReached = isLimitReached
        )
    }

    /**
     * Increment daily send count after successful message send
     */
    suspend fun incrementDailyCount(userId: String) = withContext(Dispatchers.IO) {
        dailyLimitDao.incrementCount(userId)
    }

    /**
     * Get SmsManager for the specified subscription
     */
    private fun getSmsManager(subscriptionId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    /**
     * Create a PendingIntent for SMS delivery callbacks
     */
    private fun createPendingIntent(messageId: String, action: String): PendingIntent {
        val intent = android.content.Intent(action).apply {
            putExtra("messageId", messageId)
            `package` = context.packageName
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(context, messageId.hashCode(), intent, flags)
    }

    /**
     * Log message to database
     */
    private suspend fun logMessage(
        messageId: String,
        address: String,
        body: String,
        type: MessageType,
        success: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            val log = SmsLogEntry(
                id = messageId,
                address = address,
                body = body,
                messageType = type.name,
                success = success,
                timestamp = System.currentTimeMillis()
            )
            smsLogDao.insert(log)
        } catch (e: Exception) {
            // Logging failure should not crash the send operation
            e.printStackTrace()
        }
    }

    /**
     * Get or create thread ID for conversation
     */
    private fun getOrCreateThreadId(address: String): Long {
        val uri = android.net.Uri.parse("content://mms-sms/threadID")
        val cursor = context.contentResolver.query(
            uri,
            null,
            "recipient_ids = ?",
            arrayOf(address),
            null
        )

        return try {
            if (cursor?.moveToFirst() == true) {
                cursor.getLong(0)
            } else {
                // Create new thread
                val newThreadUri = context.contentResolver.insert(
                    android.net.Uri.parse("content://mms-sms/threadID"),
                    android.content.ContentValues().apply {
                        put("recipient_ids", address)
                    }
                )
                newThreadUri?.lastPathSegment?.toLongOrNull() ?: 0L
            }
        } finally {
            cursor?.close()
        }
    }

    /**
     * Send SMS from a TaskEntity
     * Used by ApprovalManager to send approved tasks
     */
    suspend fun sendSms(task: com.bizconnect.v2.data.local.db.entity.TaskEntity): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = sendMessage(task.customerPhone, task.messageContent)
            result.success
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to send SMS for task ${task.id}", e)
            false
        }
    }
}

/**
 * Enum for message types
 */
enum class MessageType {
    SMS,   // Single/short message
    LMS,   // Long message (multipart SMS)
    MMS    // Multimedia message (image/attachment)
}

/**
 * Result of a send operation
 */
data class SendResult(
    val success: Boolean,
    val messageId: Long? = null,
    val messageType: MessageType,
    val errorMessage: String? = null,
    val errorCode: Int? = null
)

/**
 * Daily limit status for user
 */
data class DailyLimitStatus(
    val currentCount: Int,
    val maxCount: Int,
    val limitMode: String,
    val isLimitReached: Boolean
)
