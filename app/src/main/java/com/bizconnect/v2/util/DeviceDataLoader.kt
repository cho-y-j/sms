package com.bizconnect.v2.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.bizconnect.v2.ui.contacts.ContactItem
import com.bizconnect.v2.ui.conversation.ConversationItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Legacy loader that reads SMS/contacts directly from the device content provider.
 * Now superseded by Room DB + ViewModel pattern (SmsSyncManager syncs data into Room).
 * Kept for potential fallback use.
 */
object DeviceDataLoader {

    /**
     * Local message model for DeviceDataLoader (the UI Message class was removed).
     */
    data class Message(
        val id: String,
        val text: String,
        val isSent: Boolean,
        val timestamp: String,
        val imageUrl: String? = null,
        val date: String? = null
    )

    private const val TAG = "DeviceDataLoader"

    /**
     * Read SMS conversations from the device.
     * Reads all SMS sorted by date, keeps only the latest per thread.
     */
    // Contact name cache to avoid repeated lookups
    private val contactCache = mutableMapOf<String, String?>()

    fun loadConversations(context: Context): List<ConversationItem> {
        val threadMap = linkedMapOf<Long, ConversationItem>()

        try {
            // Pre-load contact cache
            if (contactCache.isEmpty()) {
                preloadContacts(context)
            }

            // Only read recent 500 messages for speed
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 500"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val threadId = it.getLongSafe(Telephony.Sms.THREAD_ID)
                    if (threadId == 0L || threadMap.containsKey(threadId)) continue

                    val address = it.getStringSafe(Telephony.Sms.ADDRESS) ?: continue
                    val body = it.getStringSafe(Telephony.Sms.BODY) ?: ""
                    val date = it.getLongSafe(Telephony.Sms.DATE)

                    // Use cache - no individual DB query per thread
                    val contactName = getCachedContactName(context, address)

                    threadMap[threadId] = ConversationItem(
                        id = threadId,
                        contactName = contactName,
                        lastMessage = body.take(100),
                        timestamp = formatTimestamp(date),
                        unreadCount = 0
                    )
                }
            }

            Log.d(TAG, "Loaded ${threadMap.size} conversations")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load conversations", e)
        }

        return threadMap.values.toList()
    }

    /**
     * Pre-load all contacts into cache for fast lookup
     */
    private fun preloadContacts(context: Context) {
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val number = it.getStringSafe(ContactsContract.CommonDataKinds.Phone.NUMBER) ?: continue
                    val name = it.getStringSafe(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME) ?: continue
                    // Normalize: remove spaces, dashes
                    val normalized = number.replace(Regex("[\\s\\-()]"), "")
                    contactCache[normalized] = name
                    // Also store last 8 digits for matching
                    if (normalized.length >= 8) {
                        contactCache[normalized.takeLast(8)] = name
                    }
                }
            }
            Log.d(TAG, "Pre-loaded ${contactCache.size} contact entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to preload contacts", e)
        }
    }

    /**
     * Fast contact name lookup using cache
     */
    private fun getCachedContactName(context: Context, phoneNumber: String): String {
        val normalized = phoneNumber.replace(Regex("[\\s\\-()]"), "")
        // Try exact match
        contactCache[normalized]?.let { return it }
        // Try last 8 digits
        if (normalized.length >= 8) {
            contactCache[normalized.takeLast(8)]?.let { return it }
        }
        // Fallback: direct lookup (and cache result)
        val name = getContactName(context, phoneNumber)
        contactCache[normalized] = name
        return name ?: phoneNumber
    }

    /**
     * Load messages for a specific thread
     */
    fun loadMessages(context: Context, threadId: String): List<Message> {
        val messages = mutableListOf<Message>()
        var lastDate = ""

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.ADDRESS
                ),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId),
                "${Telephony.Sms.DATE} ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getStringSafe(Telephony.Sms._ID) ?: continue
                    val body = it.getStringSafe(Telephony.Sms.BODY) ?: ""
                    val date = it.getLongSafe(Telephony.Sms.DATE)
                    val type = it.getIntSafe(Telephony.Sms.TYPE)

                    // type: 1=inbox(received), 2=sent
                    val isSent = type == Telephony.Sms.MESSAGE_TYPE_SENT

                    val dateStr = SimpleDateFormat("yyyy년 M월 d일 EEEE", Locale.KOREA).format(Date(date))
                    val timeStr = SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(date))

                    val showDate = if (dateStr != lastDate) {
                        lastDate = dateStr
                        dateStr
                    } else null

                    messages.add(
                        Message(
                            id = id,
                            text = body,
                            isSent = isSent,
                            timestamp = timeStr,
                            date = showDate
                        )
                    )
                }
            }

            Log.d(TAG, "Loaded ${messages.size} messages for thread $threadId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load messages for thread $threadId", e)
        }

        return messages
    }

    /**
     * Get phone number for a thread
     */
    fun getThreadPhoneNumber(context: Context, threadId: String): String {
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId),
                "${Telephony.Sms.DATE} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getStringSafe(Telephony.Sms.ADDRESS) ?: ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get phone for thread $threadId", e)
        }
        return ""
    }

    /**
     * Get contact name for a thread
     */
    fun getThreadContactName(context: Context, threadId: String): String {
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId),
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val address = it.getStringSafe(Telephony.Sms.ADDRESS) ?: return threadId
                    return getContactName(context, address) ?: address
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get contact for thread $threadId", e)
        }
        return threadId
    }

    /**
     * Count unread messages in a thread
     */
    private fun countUnread(context: Context, threadId: Long): Int {
        return try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
                null
            )
            val count = cursor?.count ?: 0
            cursor?.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Load contacts from the device
     */
    fun loadContacts(context: Context): List<ContactItem> {
        val contacts = mutableListOf<ContactItem>()

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            val seen = mutableSetOf<String>()
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getStringSafe(ContactsContract.CommonDataKinds.Phone.CONTACT_ID) ?: continue
                    val name = it.getStringSafe(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME) ?: continue
                    val phone = it.getStringSafe(ContactsContract.CommonDataKinds.Phone.NUMBER) ?: continue
                    val photoUri = it.getStringSafe(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                    if (seen.add(id)) {
                        contacts.add(
                            ContactItem(
                                id = id.toLongOrNull() ?: 0L,
                                name = name,
                                phone = phone,
                                photoUrl = photoUri
                            )
                        )
                    }
                }
            }

            Log.d(TAG, "Loaded ${contacts.size} contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load contacts", e)
        }

        return contacts
    }

    /**
     * Look up contact name by phone number
     */
    private fun getContactName(context: Context, phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatTimestamp(millis: Long): String {
        if (millis == 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - millis

        return when {
            diff < 60_000 -> "방금"
            diff < 3_600_000 -> "${diff / 60_000}분 전"
            diff < 86_400_000 -> {
                SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(millis))
            }
            diff < 604_800_000 -> {
                val days = arrayOf("일", "월", "화", "수", "목", "금", "토")
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
                "${days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]}요일"
            }
            else -> SimpleDateFormat("M/d", Locale.KOREA).format(Date(millis))
        }
    }

    private fun Cursor.getStringSafe(column: String): String? {
        val idx = getColumnIndex(column)
        return if (idx >= 0) getString(idx) else null
    }

    private fun Cursor.getLongSafe(column: String): Long {
        val idx = getColumnIndex(column)
        return if (idx >= 0) getLong(idx) else 0L
    }

    private fun Cursor.getIntSafe(column: String): Int {
        val idx = getColumnIndex(column)
        return if (idx >= 0) getInt(idx) else 0
    }
}
