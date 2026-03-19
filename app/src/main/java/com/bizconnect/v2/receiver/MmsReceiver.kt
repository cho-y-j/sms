package com.bizconnect.v2.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.bizconnect.v2.data.local.db.dao.ConversationDao
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.dao.MessageDao
import com.bizconnect.v2.data.local.db.dao.SpamFilterDao
import com.bizconnect.v2.data.local.db.entity.ConversationEntity
import com.bizconnect.v2.data.local.db.entity.MessageEntity
import com.bizconnect.v2.util.NotificationUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MmsReceiver : BroadcastReceiver() {
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

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contentType = intent.type ?: return@launch
                if (contentType != "application/vnd.wap.mms-message") return@launch

                val address = "MMS"
                val body = "[MMS Message]"
                val timestamp = System.currentTimeMillis()

                // Check spam
                val isSpam = try {
                    spamFilterDao.isNumberBlocked(address)
                } catch (e: Exception) {
                    false
                }

                // Get thread ID
                val threadId = try {
                    Telephony.Threads.getOrCreateThreadId(context, address)
                } catch (e: Exception) {
                    address.hashCode().toLong() and 0x7FFFFFFFL
                }

                // Insert message as MMS
                val messageEntity = MessageEntity(
                    threadId = threadId,
                    address = address,
                    body = body,
                    timestamp = timestamp,
                    type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                    read = false,
                    seen = false,
                    isMms = true,
                    mmsContentType = contentType
                )
                messageDao.insert(messageEntity)

                // Look up contact name
                val contactName = contactDao.getByPhone(address)?.name
                    ?: lookupContactName(context, address)

                // Update conversation
                val conversation = ConversationEntity(
                    threadId = threadId,
                    recipientAddress = address,
                    recipientName = contactName,
                    snippet = body,
                    snippetType = Telephony.Sms.MESSAGE_TYPE_INBOX,
                    messageCount = 1,
                    unreadCount = 1,
                    lastMessageTimestamp = timestamp,
                    read = false
                )
                conversationDao.insert(conversation)

                if (!isSpam) {
                    notificationUtil.showNewMessageNotification(
                        threadId = threadId,
                        senderName = contactName ?: address,
                        senderAddress = address,
                        messageText = body
                    )
                }

                Log.d(TAG, "MMS received")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing MMS", e)
            } finally {
                pendingResult.finish()
            }
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

    companion object {
        const val TAG = "MmsReceiver"
    }
}
