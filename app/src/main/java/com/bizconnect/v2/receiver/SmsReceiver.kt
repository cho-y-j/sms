package com.bizconnect.v2.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.bizconnect.v2.data.local.db.dao.ConversationDao
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.dao.MessageDao
import com.bizconnect.v2.data.local.db.dao.SpamFilterDao
import com.bizconnect.v2.data.local.db.entity.ConversationEntity
import com.bizconnect.v2.data.local.db.entity.MessageEntity
import com.bizconnect.v2.domain.ai.AiAssistant
import com.bizconnect.v2.util.NotificationUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject
    lateinit var messageDao: MessageDao

    @Inject
    lateinit var conversationDao: ConversationDao

    @Inject
    lateinit var contactDao: ContactDao

    @Inject
    lateinit var spamFilterDao: SpamFilterDao

    @Inject
    lateinit var notificationUtil: NotificationUtil

    @Inject
    lateinit var aiAssistant: AiAssistant

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val pdus = intent.getSerializableExtra("pdus") as? Array<*> ?: return
        val format = intent.getStringExtra("format") ?: ""
        val smsMessages = Array(pdus.size) { i ->
            SmsMessage.createFromPdu(pdus[i] as ByteArray, format)
        }

        // Combine multipart messages by originating address
        val combined = mutableMapOf<String, StringBuilder>()
        var timestamp = System.currentTimeMillis()
        smsMessages.forEach { msg ->
            val address = msg.originatingAddress ?: return@forEach
            combined.getOrPut(address) { StringBuilder() }.append(msg.messageBody ?: "")
            timestamp = msg.timestampMillis
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                combined.forEach { (address, bodyBuilder) ->
                    val body = bodyBuilder.toString()

                    // Run spam check, thread ID lookup, contact name, and notification setting in parallel
                    val spamDeferred = async {
                        try {
                            spamFilterDao.isSpam(address, body) || spamFilterDao.isNumberBlocked(address)
                        } catch (e: Exception) { false }
                    }

                    val threadIdDeferred = async {
                        try {
                            Telephony.Threads.getOrCreateThreadId(context, address)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not get thread ID, using address hash", e)
                            address.hashCode().toLong() and 0x7FFFFFFFL
                        }
                    }

                    val contactNameDeferred = async {
                        contactDao.getByPhone(address)?.name
                            ?: lookupContactName(context, address)
                    }

                    val notifEnabledDeferred = async {
                        try {
                            val prefs = dataStore.data.first()
                            prefs[booleanPreferencesKey("notifications_enabled")] ?: true
                        } catch (e: Exception) { true }
                    }

                    // Await all parallel results
                    val isSpam = spamDeferred.await()
                    val threadId = threadIdDeferred.await()
                    val contactName = contactNameDeferred.await()
                    val notificationsEnabled = notifEnabledDeferred.await()

                    // Show notification FIRST (user-facing, latency-critical)
                    if (!isSpam && notificationsEnabled) {
                        notificationUtil.showNewMessageNotification(
                            threadId = threadId,
                            senderName = contactName ?: address,
                            senderAddress = address,
                            messageText = body
                        )
                    }

                    // Check for appointment keywords → AI detect → calendar notification
                    if (!isSpam && hasAppointmentKeywords(body)) {
                        try {
                            Log.d(TAG, "Appointment keywords detected, checking with AI...")
                            val appointment = aiAssistant.extractAppointmentFromText(body)
                            if (appointment != null) {
                                Log.d(TAG, "Appointment found: ${appointment.date} ${appointment.time}")
                                notificationUtil.showAppointmentNotification(
                                    senderName = contactName ?: address,
                                    appointment = appointment
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Appointment detection failed: ${e.message}")
                        }
                    }

                    // Default SMS app contract: messages delivered via SMS_DELIVER are NOT
                    // auto-stored by the system — the default app IS the authoritative store
                    // and must write them to the system provider itself. Do this FIRST, then
                    // mirror into Room carrying the returned _id so the ContentObserver dedups
                    // (mirrors the send path in SmsSender: provider write → id → Room).
                    var systemSmsId = 0L
                    try {
                        val smsUri = context.contentResolver.insert(
                            Telephony.Sms.Inbox.CONTENT_URI,
                            ContentValues().apply {
                                put(Telephony.Sms.ADDRESS, address)
                                put(Telephony.Sms.BODY, body)
                                put(Telephony.Sms.DATE, timestamp)
                                put(Telephony.Sms.DATE_SENT, timestamp)
                                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                                put(Telephony.Sms.READ, 0)
                                put(Telephony.Sms.SEEN, 0)
                                put(Telephony.Sms.THREAD_ID, threadId)
                            }
                        )
                        systemSmsId = smsUri?.lastPathSegment?.toLongOrNull() ?: 0L
                    } catch (e: Exception) {
                        // Throws if we are not (yet) the default SMS app — expected; the
                        // user is prompted to set us as default. Room still holds the message.
                        Log.w(TAG, "Could not write incoming SMS to system provider", e)
                    }

                    val messageEntity = MessageEntity(
                        threadId = threadId,
                        address = address,
                        body = body,
                        timestamp = timestamp,
                        type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                        read = false,
                        seen = false,
                        systemSmsId = systemSmsId
                    )
                    messageDao.insert(messageEntity)

                    updateConversation(threadId, address, contactName, body, timestamp)

                    Log.d(TAG, "SMS received from $address, spam=$isSpam")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateConversation(
        threadId: Long,
        address: String,
        contactName: String?,
        body: String,
        timestamp: Long
    ) {
        val existing = conversationDao.getByIdSync(threadId)
        if (existing != null) {
            val updated = existing.copy(
                snippet = body,
                snippetType = Telephony.Sms.MESSAGE_TYPE_INBOX,
                messageCount = existing.messageCount + 1,
                unreadCount = existing.unreadCount + 1,
                lastMessageTimestamp = timestamp,
                read = false
            )
            conversationDao.update(updated)
        } else {
            conversationDao.insert(ConversationEntity(
                threadId = threadId,
                recipientAddress = address,
                recipientName = contactName,
                snippet = body,
                snippetType = Telephony.Sms.MESSAGE_TYPE_INBOX,
                messageCount = 1,
                unreadCount = 1,
                lastMessageTimestamp = timestamp,
                read = false
            ))
        }
    }

    private fun lookupContactName(context: Context, address: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Quick keyword check before calling AI (no network needed).
     */
    private fun hasAppointmentKeywords(text: String): Boolean {
        val keywords = listOf(
            "시", "분", "일", "월", "요일", "내일", "모레", "오늘",
            "다음주", "이번주", "만나", "약속", "미팅", "회의",
            "방문", "오전", "오후", "점심", "저녁", "아침"
        )
        return keywords.any { text.contains(it) }
    }

    companion object {
        const val TAG = "SmsReceiver"
    }
}
