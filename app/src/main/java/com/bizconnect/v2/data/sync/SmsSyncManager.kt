package com.bizconnect.v2.data.sync

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.dao.ConversationDao
import com.bizconnect.v2.data.local.db.dao.MessageDao
import com.bizconnect.v2.data.local.db.entity.ContactEntity
import com.bizconnect.v2.data.local.db.entity.ConversationEntity
import com.bizconnect.v2.data.local.db.entity.MessageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private const val TAG = "SmsSyncManager"
        private const val BATCH_SIZE = 500
        private val KEY_INITIAL_SYNC_COMPLETE = booleanPreferencesKey("initial_sync_complete")
        private val KEY_LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")

        private val SMS_PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.SEEN,
            Telephony.Sms.STATUS
        )
    }

    private var lastSyncTime = 0L
    private val MIN_SYNC_INTERVAL = 30_000L // 30초 최소 간격

    suspend fun performInitialSyncIfNeeded() {
        // 30초 이내 재호출 방지
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < MIN_SYNC_INTERVAL) {
            Log.d(TAG, "Sync skipped (${(now - lastSyncTime) / 1000}s since last sync)")
            return
        }
        lastSyncTime = now

        val roomCount = try { messageDao.getTotalCountSync() } catch (_: Exception) { 0 }
        val systemCount = getSystemSmsCount()
        Log.d(TAG, "Sync check: Room=$roomCount, System=$systemCount")

        if (roomCount == 0 || (systemCount > 0 && roomCount < systemCount - 10)) {
            Log.d(TAG, "DB needs full sync (Room=$roomCount vs System=$systemCount)")
            dataStore.edit { prefs -> prefs[KEY_INITIAL_SYNC_COMPLETE] = false }
            performFullSync()
        } else {
            performIncrementalSync()
        }
    }

    private fun getSystemSmsCount(): Int {
        return try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("COUNT(*) as count"),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            } ?: 0
        } catch (_: Exception) { 0 }
    }

    suspend fun performFullSync() {
        Log.d(TAG, "Starting full SMS sync")
        try {
            // Clear existing data to avoid stale entries
            try {
                messageDao.deleteAll()
                conversationDao.deleteAll()
            } catch (_: Exception) {}

            val messages = readSmsFromProvider(sinceTimestamp = null)
            Log.d(TAG, "Read ${messages.size} messages from system SMS")

            // Sync contacts first (for name lookup)
            syncContacts()

            // Build and insert conversations FIRST (MessageEntity has FK to ConversationEntity)
            // Preserve existing AI summaries
            val conversations = buildConversations(messages)
            val preservedConversations = conversations.map { conv ->
                val existing = conversationDao.getByIdSync(conv.threadId)
                if (existing != null) {
                    conv.copy(
                        aiSummary = existing.aiSummary,
                        aiSummaryDate = existing.aiSummaryDate,
                        aiEmotion = existing.aiEmotion
                    )
                } else conv
            }
            preservedConversations.chunked(BATCH_SIZE).forEach { batch ->
                conversationDao.insertAll(batch)
            }
            Log.d(TAG, "Inserted ${conversations.size} conversations")

            // Then batch insert messages
            messages.chunked(BATCH_SIZE).forEach { batch ->
                messageDao.insertAllIgnore(batch)
            }

            // Save sync state
            dataStore.edit { prefs ->
                prefs[KEY_INITIAL_SYNC_COMPLETE] = true
                prefs[KEY_LAST_SYNC_TIMESTAMP] = System.currentTimeMillis()
            }
            Log.d(TAG, "Full sync complete: ${messages.size} messages, ${conversations.size} conversations")
        } catch (e: Exception) {
            Log.e(TAG, "Error during full sync", e)
        }
    }

    suspend fun performIncrementalSync() {
        val prefs = dataStore.data.first()
        var lastSync = prefs[KEY_LAST_SYNC_TIMESTAMP] ?: 0L

        // 기본 앱이 아닐 때는 최근 24시간 메시지를 전체 비교 (다른 앱이 수신 처리)
        val isDefaultApp = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        if (!isDefaultApp) {
            lastSync = System.currentTimeMillis() - 86400_000 // 24시간 전부터
        }
        Log.d(TAG, "Starting incremental sync since $lastSync (default=$isDefaultApp)")

        try {
            val smsMessages = readSmsFromProvider(sinceTimestamp = lastSync)
            val mmsMessages = readMmsFromProvider(sinceTimestamp = lastSync)
            val rcsMessages = readRcsFromProvider(sinceTimestamp = lastSync)

            // 발신 메시지(type=2)는 동기화에서 제외 — sendSms/sendMms에서 이미 Room에 저장함
            // 수신 메시지(type=1)만 동기화 → 중복 문제 근본 해결
            val receivedOnly = (smsMessages + mmsMessages + rcsMessages).filter { it.type == 1 }
            val messages = receivedOnly
            Log.d(TAG, "Read ${smsMessages.size} SMS + ${mmsMessages.size} MMS + ${rcsMessages.size} RCS → 수신만 ${messages.size}건")

            if (messages.isEmpty()) return

            // 수신 메시지 중복 체크
            val newMessages = messages.filter { msg ->
                if (msg.systemSmsId != 0L) {
                    val existing = messageDao.getBySystemSmsId(msg.systemSmsId)
                    if (existing != null) return@filter false
                }
                // address + timestamp 3초 이내 → 중복
                val existingInThread = messageDao.getMessagesByThreadDirect(msg.threadId)
                val isDuplicate = existingInThread.any { existing ->
                    Math.abs(existing.timestamp - msg.timestamp) < 3000
                }
                !isDuplicate
            }
            Log.d(TAG, "After dedup: ${newMessages.size} new messages (filtered ${messages.size - newMessages.size} duplicates)")

            if (newMessages.isEmpty()) return

            // Update affected conversations FIRST (FK constraint)
            val affectedThreadIds = newMessages.map { it.threadId }.distinct()
            for (threadId in affectedThreadIds) {
                updateConversationForThread(threadId, newMessages.filter { it.threadId == threadId })
            }

            // Then batch insert new messages
            newMessages.chunked(BATCH_SIZE).forEach { batch ->
                messageDao.insertAllIgnore(batch)
            }

            // Update timestamp
            dataStore.edit { prefs ->
                prefs[KEY_LAST_SYNC_TIMESTAMP] = System.currentTimeMillis()
            }
            Log.d(TAG, "Incremental sync complete: ${messages.size} messages")
        } catch (e: Exception) {
            Log.e(TAG, "Error during incremental sync", e)
        }
    }

    suspend fun syncContacts() {
        Log.d(TAG, "Starting contact sync")
        try {
            val contacts = mutableListOf<ContactEntity>()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
            )

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val thumbIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: continue
                    val number = cursor.getString(numberIdx) ?: continue
                    val photoUri = cursor.getString(photoIdx)
                    val thumbUri = cursor.getString(thumbIdx)

                    contacts.add(
                        ContactEntity(
                            id = id,
                            name = name,
                            phoneNumber = number,
                            normalizedNumber = normalizeNumber(number),
                            photoUri = photoUri,
                            thumbnailUri = thumbUri,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
            }

            // Batch insert with REPLACE
            contacts.chunked(BATCH_SIZE).forEach { batch ->
                contactDao.insertAll(batch)
            }
            Log.d(TAG, "Contact sync complete: ${contacts.size} contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Error during contact sync", e)
        }
    }

    fun lookupContactName(address: String): String? {
        // Try PhoneLookup as fallback
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PhoneLookup failed for $address", e)
        }
        return null
    }

    private fun readSmsFromProvider(sinceTimestamp: Long?): List<MessageEntity> {
        val messages = mutableListOf<MessageEntity>()
        val selection = if (sinceTimestamp != null) "${Telephony.Sms.DATE} > ?" else null
        val selectionArgs = if (sinceTimestamp != null) arrayOf(sinceTimestamp.toString()) else null

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            SMS_PROJECTION,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val threadIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            val seenIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.SEEN)
            val statusIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)

            while (cursor.moveToNext()) {
                val smsId = cursor.getLong(idIdx)
                val threadId = cursor.getLong(threadIdx)
                val address = cursor.getString(addressIdx) ?: ""
                val body = cursor.getString(bodyIdx)
                val date = cursor.getLong(dateIdx)
                val type = cursor.getInt(typeIdx)
                val read = cursor.getInt(readIdx) == 1
                val seen = cursor.getInt(seenIdx) == 1
                val status = cursor.getInt(statusIdx)

                messages.add(
                    MessageEntity(
                        systemSmsId = smsId,
                        threadId = threadId,
                        address = address,
                        body = body,
                        timestamp = date,
                        type = type,
                        read = read,
                        seen = seen,
                        status = status
                    )
                )
            }
        }
        return messages
    }

    /**
     * 삼성 RCS/채팅 메시지 읽기 (content://im/chat)
     * 삼성 기기에서 RCS로 수신된 메시지는 content://sms에 없고 content://im/chat에 있음
     */
    private fun readRcsFromProvider(sinceTimestamp: Long?): List<MessageEntity> {
        val messages = mutableListOf<MessageEntity>()
        try {
            val uri = Uri.parse("content://im/chat")
            val selection = if (sinceTimestamp != null) "date > ?" else null
            val selectionArgs = if (sinceTimestamp != null) arrayOf(sinceTimestamp.toString()) else null

            context.contentResolver.query(
                uri,
                arrayOf("_id", "thread_id", "address", "body", "date", "type", "message_type"),
                selection,
                selectionArgs,
                "date ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("_id")
                val threadIdx = cursor.getColumnIndexOrThrow("thread_id")
                val addressIdx = cursor.getColumnIndexOrThrow("address")
                val bodyIdx = cursor.getColumnIndexOrThrow("body")
                val dateIdx = cursor.getColumnIndexOrThrow("date")
                val typeIdx = cursor.getColumnIndexOrThrow("type")

                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx) ?: continue
                    val body = cursor.getString(bodyIdx)
                    val timestamp = cursor.getLong(dateIdx)
                    val type = cursor.getInt(typeIdx) // 1=received, 2=sent
                    val systemId = cursor.getLong(idIdx)
                    val threadId = cursor.getLong(threadIdx)

                    // threadId를 SMS와 통합하기 위해 재생성
                    val unifiedThreadId = try {
                        Telephony.Threads.getOrCreateThreadId(context, address)
                    } catch (e: Exception) {
                        address.hashCode().toLong() and 0x7FFFFFFFL
                    }

                    messages.add(MessageEntity(
                        threadId = unifiedThreadId,
                        address = address,
                        body = body,
                        timestamp = timestamp,
                        type = type,
                        read = type == 2, // sent = read
                        seen = type == 2,
                        systemSmsId = systemId + 1_000_000_000L, // RCS ID에 오프셋 추가 (SMS ID와 충돌 방지)
                        status = Telephony.Sms.STATUS_COMPLETE
                    ))
                }
            }
        } catch (e: Exception) {
            // content://im/chat은 삼성 기기에서만 사용 가능
            Log.d(TAG, "RCS provider not available: ${e.message}")
        }
        return messages
    }

    private suspend fun buildConversations(messages: List<MessageEntity>): List<ConversationEntity> {
        // Pre-build contact name cache from DB for fast lookup
        val contactNameCache = mutableMapOf<String, String?>()
        try {
            val allContacts = contactDao.getAllSync()
            for (c in allContacts) {
                contactNameCache[c.normalizedNumber] = c.name
                // Also cache by last 8 digits
                val norm = c.normalizedNumber
                if (norm.length >= 8) {
                    contactNameCache[norm.takeLast(8)] = c.name
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not preload contacts for name lookup", e)
        }

        val grouped = messages.groupBy { it.threadId }
        return grouped.map { (threadId, threadMessages) ->
            val sorted = threadMessages.sortedByDescending { it.timestamp }
            val latestMessage = sorted.first()
            val address = latestMessage.address
            val normalized = normalizeNumber(address)

            // Fast cache lookup instead of individual DB query per thread
            val contactName = contactNameCache[normalized]
                ?: (if (normalized.length >= 8) contactNameCache[normalized.takeLast(8)] else null)

            ConversationEntity(
                threadId = threadId,
                recipientAddress = address,
                recipientName = contactName,
                snippet = latestMessage.body ?: "",
                snippetType = latestMessage.type,
                messageCount = threadMessages.size,
                unreadCount = threadMessages.count { !it.read },
                lastMessageTimestamp = latestMessage.timestamp,
                read = threadMessages.all { it.read }
            )
        }
    }

    private suspend fun updateConversationForThread(threadId: Long, newMessages: List<MessageEntity>) {
        val latestMessage = newMessages.maxByOrNull { it.timestamp } ?: return
        val address = latestMessage.address
        val contactName = contactDao.getByPhone(normalizeNumber(address))?.name
            ?: lookupContactName(address)

        val existing = conversationDao.getByIdSync(threadId)
        if (existing != null) {
            val updated = existing.copy(
                snippet = latestMessage.body ?: "",
                snippetType = latestMessage.type,
                messageCount = existing.messageCount + newMessages.size,
                unreadCount = existing.unreadCount + newMessages.count { !it.read },
                lastMessageTimestamp = latestMessage.timestamp,
                read = newMessages.all { it.read }
            )
            conversationDao.update(updated)
        } else {
            conversationDao.insert(ConversationEntity(
                threadId = threadId,
                recipientAddress = address,
                recipientName = contactName,
                snippet = latestMessage.body ?: "",
                snippetType = latestMessage.type,
                messageCount = newMessages.size,
                unreadCount = newMessages.count { !it.read },
                lastMessageTimestamp = latestMessage.timestamp,
                read = newMessages.all { it.read }
            ))
        }
    }

    /**
     * Read MMS messages from system content provider.
     */
    private fun readMmsFromProvider(sinceTimestamp: Long?): List<MessageEntity> {
        val messages = mutableListOf<MessageEntity>()
        try {
            // MMS dates are in seconds, not milliseconds
            val sinceSeconds = if (sinceTimestamp != null) sinceTimestamp / 1000 else null
            val selection = if (sinceSeconds != null) "${Telephony.Mms.DATE} > ?" else null
            val selectionArgs = if (sinceSeconds != null) arrayOf(sinceSeconds.toString()) else null

            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.THREAD_ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.READ,
                    Telephony.Mms.SEEN,
                    Telephony.Mms.SUBJECT
                ),
                selection, selectionArgs,
                "${Telephony.Mms.DATE} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
                val threadIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
                val boxIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.READ)
                val seenIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.SEEN)
                val subjectIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.SUBJECT)

                while (cursor.moveToNext()) {
                    val mmsId = cursor.getLong(idIdx)
                    val threadId = cursor.getLong(threadIdx)
                    val date = cursor.getLong(dateIdx) * 1000 // seconds → ms
                    val msgBox = cursor.getInt(boxIdx)
                    val read = cursor.getInt(readIdx) == 1
                    val seen = cursor.getInt(seenIdx) == 1
                    val subject = cursor.getString(subjectIdx)

                    // Get address from addr table
                    val address = getMmsAddress(mmsId, msgBox)
                    if (address.isBlank()) continue

                    // Get text and image from part table
                    val (textBody, imagePath) = getMmsParts(mmsId)

                    // type: 1=received(inbox), 2=sent
                    val type = if (msgBox == Telephony.Mms.MESSAGE_BOX_SENT) 2 else 1

                    val body = textBody ?: subject ?: "[MMS]"

                    messages.add(
                        MessageEntity(
                            systemSmsId = mmsId + 1_000_000_000, // offset to avoid SMS ID collision
                            threadId = threadId,
                            address = address,
                            body = body,
                            timestamp = date,
                            type = type,
                            read = read,
                            seen = seen,
                            isMms = true,
                            attachmentPath = imagePath,
                            attachmentMimeType = if (imagePath != null) "image/jpeg" else null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading MMS: ${e.message}", e)
        }
        Log.d(TAG, "Read ${messages.size} MMS messages from provider")
        return messages
    }

    /**
     * Get sender/recipient address for an MMS message.
     */
    private fun getMmsAddress(mmsId: Long, msgBox: Int): String {
        return try {
            // For received: type=137 (FROM), for sent: type=151 (TO)
            val addrType = if (msgBox == Telephony.Mms.MESSAGE_BOX_SENT) "151" else "137"
            context.contentResolver.query(
                Uri.parse("content://mms/$mmsId/addr"),
                arrayOf(Telephony.Mms.Addr.ADDRESS),
                "${Telephony.Mms.Addr.TYPE} = ?",
                arrayOf(addrType),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val addr = cursor.getString(0) ?: ""
                    // Filter out "insert-address-token" placeholder
                    if (addr.contains("insert-address")) "" else addr
                } else ""
            } ?: ""
        } catch (e: Exception) { "" }
    }

    /**
     * Get text body and image path from MMS parts.
     */
    private fun getMmsParts(mmsId: Long): Pair<String?, String?> {
        var textBody: String? = null
        var imagePath: String? = null

        try {
            context.contentResolver.query(
                Uri.parse("content://mms/$mmsId/part"),
                arrayOf("_id", "ct", "text", "_data"),
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("_id")
                val ctIdx = cursor.getColumnIndexOrThrow("ct")
                val textIdx = cursor.getColumnIndex("text")
                val dataIdx = cursor.getColumnIndex("_data")

                while (cursor.moveToNext()) {
                    val contentType = cursor.getString(ctIdx) ?: continue

                    when {
                        contentType == "text/plain" -> {
                            textBody = if (textIdx >= 0) cursor.getString(textIdx) else null
                            // If text is null, try reading from data
                            if (textBody == null) {
                                val partId = cursor.getLong(idIdx)
                                textBody = readMmsPartText(partId)
                            }
                        }
                        contentType.startsWith("image/") -> {
                            val partId = cursor.getLong(idIdx)
                            // Save image to internal storage
                            imagePath = saveMmsPartImage(partId, mmsId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading MMS parts: ${e.message}")
        }
        return Pair(textBody, imagePath)
    }

    private fun readMmsPartText(partId: Long): String? {
        return try {
            val partUri = Uri.parse("content://mms/part/$partId")
            context.contentResolver.openInputStream(partUri)?.use { stream ->
                stream.bufferedReader().readText()
            }
        } catch (e: Exception) { null }
    }

    private fun saveMmsPartImage(partId: Long, mmsId: Long): String? {
        return try {
            val partUri = Uri.parse("content://mms/part/$partId")
            val inputStream = context.contentResolver.openInputStream(partUri) ?: return null
            val dir = java.io.File(context.filesDir, "received_mms")
            dir.mkdirs()
            val file = java.io.File(dir, "mms_${mmsId}_${partId}.jpg")
            if (!file.exists()) {
                file.outputStream().use { out -> inputStream.copyTo(out) }
            }
            inputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving MMS image: ${e.message}")
            null
        }
    }

    private fun normalizeNumber(number: String): String {
        var result = number.trim()
        // Remove all non-digit characters except leading +
        result = if (result.startsWith("+")) {
            "+" + result.filter { it.isDigit() }
        } else {
            result.filter { it.isDigit() }
        }
        // Korean: convert +82 to 0
        if (result.startsWith("+82")) {
            result = "0" + result.substring(3)
        }
        return result
    }
}
