package com.bizconnect.v2.util

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import com.bizconnect.v2.data.local.db.dao.ConversationDao
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.dao.MessageDao
import com.bizconnect.v2.data.local.db.entity.ConversationEntity
import com.bizconnect.v2.data.local.db.entity.MessageEntity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val contactDao: ContactDao,
    private val dailyLimitDao: com.bizconnect.v2.data.local.db.dao.DailyLimitDao,
    private val appPreferences: com.bizconnect.v2.data.preferences.AppPreferences
) {
    private val TAG = "SmsSender"

    /**
     * Check daily limit and increment count.
     * Returns true if allowed, false if limit reached.
     */
    private suspend fun checkAndIncrementDailyLimit(): Boolean {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).format(java.util.Date())
        val userId = appPreferences.getUserId() ?: "default"
        val currentCount = dailyLimitDao.getSentCount(userId, today) ?: 0
        val limit = if (appPreferences.getSubscriptionTier() == "paid") {
            appPreferences.getPaidTierDailyLimit()
        } else {
            appPreferences.getDailyLimitCount()
        }

        if (currentCount >= limit) {
            Log.w(TAG, "Daily limit reached: $currentCount/$limit")
            return false
        }

        // Increment
        val existing = dailyLimitDao.get(userId, today)
        if (existing != null) {
            dailyLimitDao.incrementCount(userId, today, System.currentTimeMillis())
        } else {
            dailyLimitDao.insert(com.bizconnect.v2.data.local.db.entity.DailyLimitEntity(
                userId = userId, date = today, sentCount = 1
            ))
        }
        return true
    }

    suspend fun sendSms(phoneNumber: String, message: String): Long {
        val phone = phoneNumber.filter { it.isDigit() || it == '+' } // 정규화

        // 일일 한도 체크
        if (!checkAndIncrementDailyLimit()) {
            Log.w(TAG, "Daily limit exceeded, message blocked")
            return -2L // -2 = 한도 초과
        }

        return try {
            val timestamp = System.currentTimeMillis()
            val threadId = try {
                Telephony.Threads.getOrCreateThreadId(context, phone)
            } catch (e: Exception) {
                phoneNumber.hashCode().toLong() and 0x7FFFFFFFL
            }

            // 1. DB에 먼저 저장 → UI에 즉시 표시
            val msgId = messageDao.insert(MessageEntity(
                threadId = threadId, address = phoneNumber, body = message,
                timestamp = timestamp, type = Telephony.Sms.MESSAGE_TYPE_SENT,
                read = true, seen = true, status = Telephony.Sms.STATUS_PENDING
            ))

            val contactName = contactDao.getByPhone(phoneNumber)?.name ?: lookupContactName(phoneNumber)
            val existing = conversationDao.getByIdSync(threadId)
            if (existing != null) {
                conversationDao.update(existing.copy(
                    snippet = message, snippetType = Telephony.Sms.MESSAGE_TYPE_SENT,
                    messageCount = existing.messageCount + 1, lastMessageTimestamp = timestamp, read = true
                ))
            } else {
                conversationDao.insert(ConversationEntity(
                    threadId = threadId, recipientAddress = phoneNumber, recipientName = contactName,
                    snippet = message, snippetType = Telephony.Sms.MESSAGE_TYPE_SENT,
                    messageCount = 1, unreadCount = 0, lastMessageTimestamp = timestamp, read = true
                ))
            }

            // 2. 실제 발송 (백그라운드)
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            var sentAsMms = false

            if (parts.size > 1) {
                Log.d(TAG, "Long message (${message.length} chars, ${parts.size} parts) → trying MMS")
                sentAsMms = trySendViaDirectHttp(phone, message)
                if (!sentAsMms) {
                    Log.w(TAG, "MMS failed, falling back to multipart SMS")
                    smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                }
            } else {
                smsManager.sendTextMessage(phone, null, message, null, null)
            }

            // 3. 발송 완료 후 상태 업데이트
            var systemSmsId = 0L
            if (isDefaultSmsApp()) {
                try {
                    if (sentAsMms) {
                        saveSentMmsToProvider(phoneNumber, message, threadId, timestamp)
                    } else {
                        val smsUri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, ContentValues().apply {
                            put(Telephony.Sms.ADDRESS, phoneNumber)
                            put(Telephony.Sms.BODY, message)
                            put(Telephony.Sms.DATE, timestamp)
                            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                            put(Telephony.Sms.READ, 1)
                            put(Telephony.Sms.SEEN, 1)
                            put(Telephony.Sms.THREAD_ID, threadId)
                        })
                        systemSmsId = smsUri?.lastPathSegment?.toLongOrNull() ?: 0L
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not write to system provider", e)
                }
            }

            // 발송 완료 상태로 업데이트
            messageDao.getById(msgId)?.let { msg ->
                messageDao.update(msg.copy(
                    isMms = sentAsMms,
                    systemSmsId = systemSmsId,
                    status = Telephony.Sms.STATUS_COMPLETE
                ))
            }

            Log.d(TAG, "Message sent to $phoneNumber (mms=$sentAsMms)")
            threadId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to $phoneNumber", e)
            -1L
        }
    }

    /**
     * Send MMS with image attachment via direct HTTP to MMSC.
     */
    suspend fun sendMmsWithImage(phoneNumber: String, text: String, imageUri: Uri): Long {
        val phone = phoneNumber.filter { it.isDigit() || it == '+' } // 정규화

        if (!checkAndIncrementDailyLimit()) {
            Log.w(TAG, "Daily limit exceeded, MMS blocked")
            return -2L
        }

        return try {
            val timestamp = System.currentTimeMillis()
            val threadId = try {
                Telephony.Threads.getOrCreateThreadId(context, phone)
            } catch (e: Exception) {
                phoneNumber.hashCode().toLong() and 0x7FFFFFFFL
            }
            val displayText = text.ifEmpty { "[사진]" }

            // Copy image to internal storage for permanent access
            val permanentPath = copyImageToInternal(imageUri)
            val savedImagePath = permanentPath ?: imageUri.toString()

            // 1. DB에 먼저 저장 → UI에 즉시 표시 (이미지 포함)
            val msgId = messageDao.insert(MessageEntity(
                threadId = threadId, address = phoneNumber,
                body = displayText,
                timestamp = timestamp, type = Telephony.Sms.MESSAGE_TYPE_SENT,
                read = true, seen = true, isMms = true,
                status = Telephony.Sms.STATUS_PENDING,
                attachmentPath = savedImagePath,
                attachmentMimeType = "image/jpeg"
            ))

            val contactName = contactDao.getByPhone(phoneNumber)?.name ?: lookupContactName(phoneNumber)
            val existing = conversationDao.getByIdSync(threadId)
            if (existing != null) {
                conversationDao.update(existing.copy(
                    snippet = displayText, snippetType = Telephony.Sms.MESSAGE_TYPE_SENT,
                    messageCount = existing.messageCount + 1,
                    lastMessageTimestamp = timestamp, read = true
                ))
            } else {
                conversationDao.insert(ConversationEntity(
                    threadId = threadId, recipientAddress = phoneNumber,
                    recipientName = contactName, snippet = displayText,
                    snippetType = Telephony.Sms.MESSAGE_TYPE_SENT,
                    messageCount = 1, unreadCount = 0,
                    lastMessageTimestamp = timestamp, read = true
                ))
            }

            // 2. 실제 발송 (백그라운드)
            Log.d(TAG, "Sending MMS with image to $phone")
            val mmsc = getKoreanCarrierMmsc()
            var sent = false
            if (mmsc != null) {
                val imageBytes = compressImage(imageUri)
                if (imageBytes != null) {
                    val pduBytes = buildMmsPduWithImage(phone, text, imageBytes)
                    sent = sendPduViaHttp(pduBytes, mmsc)
                }
            }

            // 3. 발송 완료 상태 업데이트
            var systemMmsId = 0L
            if (isDefaultSmsApp()) {
                try { systemMmsId = saveSentMmsToProvider(phoneNumber, displayText, threadId, timestamp) }
                catch (e: Exception) { Log.w(TAG, "Save MMS provider failed", e) }
            }

            if (sent) {
                messageDao.getById(msgId)?.let { msg ->
                    messageDao.update(msg.copy(
                        status = Telephony.Sms.STATUS_COMPLETE,
                        systemSmsId = if (systemMmsId > 0) systemMmsId + 1_000_000_000 else 0L
                    ))
                }
                Log.d(TAG, "MMS with image sent to $phoneNumber (success=true)")
                threadId
            } else {
                // MMS 발송 실패 → DB에서 제거하고 -1 반환 (호출자가 SMS 폴백 가능)
                messageDao.getById(msgId)?.let { msg ->
                    messageDao.update(msg.copy(status = Telephony.Sms.STATUS_FAILED))
                }
                Log.w(TAG, "MMS with image FAILED to $phoneNumber, returning -1 for fallback")
                -1L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send MMS with image", e)
            -1L
        }
    }

    /**
     * Send MMS with multiple images in a single message.
     */
    suspend fun sendMmsWithImages(phoneNumber: String, text: String, imageUris: List<Uri>): Long {
        if (imageUris.size == 1) return sendMmsWithImage(phoneNumber, text, imageUris[0])

        return try {
            val timestamp = System.currentTimeMillis()
            val threadId = try {
                Telephony.Threads.getOrCreateThreadId(context, phoneNumber)
            } catch (e: Exception) {
                phoneNumber.hashCode().toLong() and 0x7FFFFFFFL
            }
            val displayText = text.ifEmpty { "[사진 ${imageUris.size}장]" }

            // 1. DB에 먼저 저장 → UI 즉시 표시
            messageDao.insert(MessageEntity(
                threadId = threadId, address = phoneNumber,
                body = displayText, timestamp = timestamp,
                type = Telephony.Sms.MESSAGE_TYPE_SENT,
                read = true, seen = true, isMms = true,
                status = Telephony.Sms.STATUS_PENDING,
                attachmentPath = imageUris[0].toString(),
                attachmentMimeType = "image/jpeg"
            ))

            val contactName = contactDao.getByPhone(phoneNumber)?.name ?: lookupContactName(phoneNumber)
            val existing = conversationDao.getByIdSync(threadId)
            if (existing != null) {
                conversationDao.update(existing.copy(
                    snippet = displayText, snippetType = Telephony.Sms.MESSAGE_TYPE_SENT,
                    messageCount = existing.messageCount + 1,
                    lastMessageTimestamp = timestamp, read = true
                ))
            } else {
                conversationDao.insert(ConversationEntity(
                    threadId = threadId, recipientAddress = phoneNumber,
                    recipientName = contactName, snippet = displayText,
                    snippetType = Telephony.Sms.MESSAGE_TYPE_SENT,
                    messageCount = 1, unreadCount = 0,
                    lastMessageTimestamp = timestamp, read = true
                ))
            }

            // 2. 이미지 압축 (총 280KB 이내로 분배)
            val maxPerImage = 280_000 / imageUris.size
            val compressedImages = imageUris.mapNotNull { uri ->
                compressImage(uri, maxPerImage)
            }
            Log.d(TAG, "Compressed ${compressedImages.size} images, total ${compressedImages.sumOf { it.size }} bytes")

            // 3. 다중 이미지 MMS PDU 생성 + 발송
            val mmsc = getKoreanCarrierMmsc()
            var sent = false
            if (mmsc != null && compressedImages.isNotEmpty()) {
                val pduBytes = buildMmsPduWithMultipleImages(phoneNumber, text, compressedImages)
                sent = sendPduViaHttp(pduBytes, mmsc)
            }

            if (isDefaultSmsApp()) {
                try { saveSentMmsToProvider(phoneNumber, displayText, threadId, timestamp) }
                catch (_: Exception) {}
            }

            Log.d(TAG, "MMS with ${compressedImages.size} images sent (success=$sent)")
            threadId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send multi-image MMS", e)
            -1L
        }
    }

    /**
     * Build MMS PDU with multiple image parts.
     */
    private fun buildMmsPduWithMultipleImages(recipient: String, text: String, images: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()

        // Headers
        out.write(0x8C); out.write(0x80)
        out.write(0x98); writeTextString(out, System.nanoTime().toString())
        out.write(0x8D); out.write(0x90)
        out.write(0x89); out.write(0x01); out.write(0x81)
        out.write(0x97); writeTextString(out, recipient)
        out.write(0x84); out.write(0xA3.toInt()) // Content-Type: multipart/mixed

        // Body
        val hasText = text.isNotBlank()
        writeUintvar(out, (if (hasText) 1 else 0) + images.size) // nEntries

        // Text part
        if (hasText) {
            val textBytes = text.toByteArray(Charsets.UTF_8)
            val textCt = byteArrayOf(0x03, 0x83.toByte(), 0x81.toByte(), 0xEA.toByte())
            writeUintvar(out, textCt.size)
            writeUintvar(out, textBytes.size)
            out.write(textCt)
            out.write(textBytes)
        }

        // Image parts
        val imageCt = byteArrayOf(0x9E.toByte()) // image/jpeg
        for (imageBytes in images) {
            writeUintvar(out, imageCt.size)
            writeUintvar(out, imageBytes.size)
            out.write(imageCt)
            out.write(imageBytes)
        }

        return out.toByteArray()
    }

    /**
     * Compress image to fit MMS size limit (300KB).
     */
    private fun compressImage(uri: Uri, maxBytes: Int = 280_000): ByteArray? {
        return try {
            // Handle both content:// URIs and file:// / absolute paths
            val inputStream = if (uri.scheme == "file" || uri.scheme == null) {
                val file = java.io.File(uri.path ?: return null)
                if (file.exists()) file.inputStream() else return null
            } else {
                context.contentResolver.openInputStream(uri) ?: return null
            }
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return null

            val maxDim = if (maxBytes < 100_000) 640 else 1024
            val resized = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else bitmap

            var quality = if (maxBytes < 100_000) 50 else 75
            var compressed: ByteArray
            do {
                val baos = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                compressed = baos.toByteArray()
                quality -= 10
            } while (compressed.size > maxBytes && quality > 10)

            Log.d(TAG, "Image: ${bitmap.width}x${bitmap.height} → ${resized.width}x${resized.height}, ${compressed.size} bytes")
            compressed
        } catch (e: Exception) {
            Log.e(TAG, "Image compression failed: ${e.message}")
            null
        }
    }

    // ============================================================
    // MMS via Direct HTTP POST to Korean carrier MMSC
    // ============================================================

    private fun trySendViaDirectHttp(phoneNumber: String, message: String): Boolean {
        return try {
            val mmsc = getKoreanCarrierMmsc()
            if (mmsc == null) {
                Log.e(TAG, "[DirectHTTP] Unknown carrier, cannot determine MMSC")
                return false
            }
            Log.d(TAG, "[DirectHTTP] Carrier: ${mmsc.carrierName}, MMSC: ${mmsc.url}")

            val pduBytes = buildTextOnlyMmsPdu(phoneNumber, message)
            sendPduViaHttp(pduBytes, mmsc)
        } catch (e: Exception) {
            Log.e(TAG, "[DirectHTTP] Failed: ${e.message}", e)
            false
        }
    }

    /**
     * Identify Korean carrier by MCC/MNC and return MMSC settings.
     */
    private fun getKoreanCarrierMmsc(): KoreanMmsc? {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
        val operator = tm.networkOperator ?: tm.simOperator ?: ""
        Log.d(TAG, "[DirectHTTP] Network operator: $operator")

        return when {
            // SKT (450/05)
            operator.startsWith("45005") -> KoreanMmsc(
                carrierName = "SKT",
                url = "http://omms.nate.com:9082/oma_mms",
                proxy = "smart.nate.com",
                port = 9093
            )
            // KT (450/08, 450/02)
            operator.startsWith("45008") || operator.startsWith("45002") -> KoreanMmsc(
                carrierName = "KT",
                url = "http://mmsc.ktfwing.com:9082",
                proxy = "",
                port = 80
            )
            // LG U+ (450/06)
            operator.startsWith("45006") -> KoreanMmsc(
                carrierName = "LG U+",
                url = "http://omammsc.uplus.co.kr:9084",
                proxy = "",
                port = 80
            )
            // KT MVNO (450/04)
            operator.startsWith("45004") -> KoreanMmsc(
                carrierName = "KT MVNO",
                url = "http://mmsc.ktfwing.com:9082",
                proxy = "",
                port = 80
            )
            // SKT MVNO (450/11, etc.)
            operator.startsWith("45011") -> KoreanMmsc(
                carrierName = "SKT MVNO",
                url = "http://omms.nate.com:9082/oma_mms",
                proxy = "smart.nate.com",
                port = 9093
            )
            else -> {
                Log.w(TAG, "[DirectHTTP] Unknown operator: $operator")
                null
            }
        }
    }

    private fun sendPduViaHttp(pduBytes: ByteArray, mmsc: KoreanMmsc): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val wifiManager = context.getSystemService(android.net.wifi.WifiManager::class.java)

        val activeNetwork = cm.activeNetwork
        val capabilities = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val isOnWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        Log.d(TAG, "[MMS] Network: ${if (isOnWifi) "WiFi" else "Cellular"}")

        if (!isOnWifi) {
            // Cellular: direct HTTP (fastest, most reliable)
            Log.d(TAG, "[MMS] Cellular → direct HTTP")
            return tryHttpPost(pduBytes, mmsc, null)
        }

        // WiFi: 1차 시스템 API 시도
        Log.d(TAG, "[MMS] WiFi → 1차: system API")
        val systemResult = sendViaSystemApi(pduBytes)
        if (systemResult) {
            Log.d(TAG, "[MMS] WiFi system API success")
            return true
        }

        // 1차 실패 → 2차: WiFi 끄고 셀룰러로 직접 HTTP
        Log.d(TAG, "[MMS] WiFi system API failed → 2차: WiFi OFF → cellular direct HTTP")
        try {
            @Suppress("DEPRECATION")
            val wifiDisabled = try {
                wifiManager?.isWifiEnabled = false
                true
            } catch (e: Exception) {
                Log.w(TAG, "[MMS] Cannot disable WiFi: ${e.message}")
                false
            }

            if (!wifiDisabled) {
                Log.w(TAG, "[MMS] WiFi control not available, cannot fallback to cellular")
                return false
            }

            Log.d(TAG, "[MMS] WiFi disabled, waiting for cellular...")
            Thread.sleep(3000)

            val result = tryHttpPost(pduBytes, mmsc, null)
            Log.d(TAG, "[MMS] Cellular direct HTTP result: $result")

            // WiFi 복원
            @Suppress("DEPRECATION")
            wifiManager?.isWifiEnabled = true
            Log.d(TAG, "[MMS] WiFi restored")

            return result
        } catch (e: Exception) {
            Log.e(TAG, "[MMS] Cellular fallback error: ${e.message}")
            @Suppress("DEPRECATION")
            try { wifiManager?.isWifiEnabled = true } catch (_: Exception) {}
            return false
        }
    }

    /**
     * Send MMS via system API (sendMultimediaMessage).
     * The system MMS service handles WiFi→cellular network switching internally.
     */
    private fun sendViaSystemApi(pduBytes: ByteArray): Boolean {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Write PDU to temp file
            val pduFile = java.io.File(context.cacheDir, "mms_${System.nanoTime()}.dat")
            pduFile.writeBytes(pduBytes)

            val pduUri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", pduFile
            )

            // Grant read permission to system MMS service
            for (pkg in listOf("com.android.mms.service", "com.samsung.android.mms.service")) {
                try { context.grantUriPermission(pkg, pduUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                catch (_: Exception) {}
            }

            // Send and wait for result
            val latch = java.util.concurrent.CountDownLatch(1)
            val success = java.util.concurrent.atomic.AtomicBoolean(false)

            val actionId = "com.bizconnect.v2.MMS_SENT_${System.nanoTime()}"
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                    val resultCode = resultCode
                    success.set(resultCode == android.app.Activity.RESULT_OK)
                    Log.d(TAG, "[MMS SystemAPI] Result code: $resultCode (0=success)")
                    latch.countDown()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, android.content.IntentFilter(actionId), android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, android.content.IntentFilter(actionId))
            }

            val sentIntent = android.app.PendingIntent.getBroadcast(
                context, System.nanoTime().toInt(),
                android.content.Intent(actionId),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_ONE_SHOT
            )

            smsManager.sendMultimediaMessage(context, pduUri, null, null, sentIntent)
            Log.d(TAG, "[MMS SystemAPI] sendMultimediaMessage dispatched, waiting 10s for result...")

            // Wait 10 seconds for result (enough for WiFi→cellular switch)
            val completed = latch.await(10, TimeUnit.SECONDS)

            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            pduFile.delete()

            if (completed) {
                val result = success.get()
                Log.d(TAG, "[MMS SystemAPI] Result: success=$result")
                result
            } else {
                // Timeout — assume success (system will deliver eventually)
                Log.d(TAG, "[MMS SystemAPI] Timeout, assuming success")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "[MMS SystemAPI] Error: ${e.message}")
            false
        }
    }

    /**
     * Attempt HTTP POST to MMSC with optional specific network.
     */
    private fun tryHttpPost(pduBytes: ByteArray, mmsc: KoreanMmsc, network: Network?): Boolean {
        return try {
            val url = URL(mmsc.url)

            val connection = if (mmsc.proxy.isNotBlank()) {
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(mmsc.proxy, mmsc.port))
                if (network != null) network.openConnection(url, proxy) as HttpURLConnection
                else url.openConnection(proxy) as HttpURLConnection
            } else {
                if (network != null) network.openConnection(url) as HttpURLConnection
                else url.openConnection() as HttpURLConnection
            }

            connection.apply {
                doOutput = true; doInput = true
                requestMethod = "POST"
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("Content-Type", "application/vnd.wap.mms-message")
                setRequestProperty("Accept", "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic")
                setRequestProperty("Content-Length", pduBytes.size.toString())
            }

            DataOutputStream(connection.outputStream).use { it.write(pduBytes); it.flush() }

            val responseCode = connection.responseCode
            val responseBytes = try { connection.inputStream.readBytes() } catch (_: Exception) {
                connection.errorStream?.readBytes() ?: byteArrayOf()
            }
            connection.disconnect()

            Log.d(TAG, "[DirectHTTP] Response: $responseCode, ${responseBytes.size} bytes")

            if (responseCode == HttpURLConnection.HTTP_OK && responseBytes.isNotEmpty()) {
                if (responseBytes.size >= 2 && responseBytes[0] == 0x8C.toByte() && responseBytes[1] == 0x81.toByte()) {
                    Log.d(TAG, "[DirectHTTP] MMS sent successfully (M-Send.conf)")
                    return true
                }
                Log.d(TAG, "[DirectHTTP] 200 OK, treating as success")
                return true
            }

            Log.w(TAG, "[DirectHTTP] Failed with response code: $responseCode")
            false
        } catch (e: Exception) {
            Log.e(TAG, "[DirectHTTP] HTTP error: ${e.message}")
            false
        }
    }

    private var mmsNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private fun acquireMmsNetwork(cm: ConnectivityManager): Network? {
        val latch = CountDownLatch(1)
        var mmsNetwork: Network? = null

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { mmsNetwork = network; latch.countDown() }
            override fun onUnavailable() { latch.countDown() }
        }
        mmsNetworkCallback = callback

        try {
            cm.requestNetwork(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                    .build(),
                callback,
                20000 // 20 second timeout (WiFi 환경에서 셀룰러 MMS 활성화에 10-15초 소요)
            )
            latch.await(21, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "MMS network request failed: ${e.message}")
        }

        Log.d(TAG, "MMS network: ${if (mmsNetwork != null) "acquired" else "unavailable, using default"}")
        return mmsNetwork
    }

    private fun releaseMmsNetwork(cm: ConnectivityManager) {
        mmsNetworkCallback?.let {
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
            mmsNetworkCallback = null
        }
    }

    // ============================================================
    // MMS PDU Builder
    // ============================================================

    /**
     * Build MMS PDU with text + image (multipart/mixed, 2 parts).
     */
    private fun buildMmsPduWithImage(recipient: String, text: String, imageBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()

        // === Headers ===
        out.write(0x8C); out.write(0x80)                    // Message-Type: m-send-req
        out.write(0x98); writeTextString(out, System.nanoTime().toString()) // Transaction-Id
        out.write(0x8D); out.write(0x90)                    // MMS-Version: 1.0
        out.write(0x89); out.write(0x01); out.write(0x81)   // From: insert-address-token
        out.write(0x97); writeTextString(out, recipient)     // To
        out.write(0x84); out.write(0xA3.toInt())             // Content-Type: multipart/mixed (LAST)

        // === Body ===
        val hasText = text.isNotBlank()
        writeUintvar(out, if (hasText) 2 else 1) // nEntries

        // Part 1: text/plain (if present)
        if (hasText) {
            val textBytes = text.toByteArray(Charsets.UTF_8)
            val textCt = byteArrayOf(0x03, 0x83.toByte(), 0x81.toByte(), 0xEA.toByte())
            writeUintvar(out, textCt.size)
            writeUintvar(out, textBytes.size)
            out.write(textCt)
            out.write(textBytes)
        }

        // Part 2: image/jpeg
        // Content-Type: image/jpeg (well-known 0x1E, short-integer = 0x9E)
        val imageCt = byteArrayOf(0x9E.toByte()) // image/jpeg as short-integer (constrained-media)
        writeUintvar(out, imageCt.size)     // HeadersLen = 1
        writeUintvar(out, imageBytes.size)  // DataLen
        out.write(imageCt)
        out.write(imageBytes)

        return out.toByteArray()
    }

    private fun buildTextOnlyMmsPdu(recipient: String, text: String): ByteArray {
        val out = ByteArrayOutputStream()

        out.write(0x8C); out.write(0x80)                    // Message-Type: m-send-req
        out.write(0x98); writeTextString(out, System.nanoTime().toString()) // Transaction-Id
        out.write(0x8D); out.write(0x90)                    // MMS-Version: 1.0
        out.write(0x89); out.write(0x01); out.write(0x81)   // From: insert-address-token
        out.write(0x97); writeTextString(out, recipient)     // To
        out.write(0x84); out.write(0xA3.toInt())             // Content-Type: multipart/mixed (LAST)

        // Body: 1 text part
        writeUintvar(out, 1)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val ct = byteArrayOf(0x03, 0x83.toByte(), 0x81.toByte(), 0xEA.toByte()) // text/plain; charset=utf-8
        writeUintvar(out, ct.size)        // HeadersLen
        writeUintvar(out, textBytes.size) // DataLen
        out.write(ct)
        out.write(textBytes)

        return out.toByteArray()
    }

    private fun writeTextString(out: ByteArrayOutputStream, text: String) {
        out.write(text.toByteArray(Charsets.US_ASCII)); out.write(0x00)
    }

    private fun writeUintvar(out: ByteArrayOutputStream, value: Int) {
        if (value < 0x80) { out.write(value); return }
        val bytes = mutableListOf<Int>()
        var v = value
        bytes.add(v and 0x7F); v = v shr 7
        while (v > 0) { bytes.add((v and 0x7F) or 0x80); v = v shr 7 }
        bytes.reversed().forEach { out.write(it) }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun saveSentMmsToProvider(address: String, body: String, threadId: Long, timestamp: Long): Long {
        try {
            val mmsUri = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, threadId)
                put(Telephony.Mms.DATE, timestamp / 1000)
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
                put(Telephony.Mms.READ, 1); put(Telephony.Mms.SEEN, 1)
                put("m_type", 128); put("v", 18)
                put("ct_t", "application/vnd.wap.multipart.mixed")
            }) ?: return 0L
            val mmsId = mmsUri.lastPathSegment?.toLongOrNull() ?: return 0L

            context.contentResolver.insert(Uri.parse("content://mms/$mmsId/part"), ContentValues().apply {
                put("mid", mmsId); put("ct", "text/plain"); put("chset", 106); put("text", body)
            })
            context.contentResolver.insert(Uri.parse("content://mms/$mmsId/addr"), ContentValues().apply {
                put(Telephony.Mms.Addr.MSG_ID, mmsId); put(Telephony.Mms.Addr.ADDRESS, address)
                put(Telephony.Mms.Addr.TYPE, 151); put(Telephony.Mms.Addr.CHARSET, 106)
            })
            return mmsId
        } catch (e: Exception) {
            Log.w(TAG, "Save MMS to provider failed", e)
            return 0L
        }
    }

    /**
     * Copy image to internal storage for permanent access (PhotoPicker URIs expire).
     */
    private fun copyImageToInternal(uri: Uri): String? {
        return try {
            val inputStream = if (uri.scheme == "file" || uri.scheme == null) {
                val file = java.io.File(uri.path ?: return null)
                if (file.exists()) return file.absolutePath // Already a local file
                else return null
            } else {
                context.contentResolver.openInputStream(uri) ?: return null
            }
            val dir = java.io.File(context.filesDir, "sent_images")
            dir.mkdirs()
            val file = java.io.File(dir, "img_${System.nanoTime()}.jpg")
            file.outputStream().use { out -> inputStream.copyTo(out) }
            inputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy image: ${e.message}")
            null
        }
    }

    private fun isDefaultSmsApp(): Boolean =
        try { Telephony.Sms.getDefaultSmsPackage(context) == context.packageName } catch (_: Exception) { false }

    private fun lookupContactName(address: String): String? = try {
        context.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) { null }

    private data class KoreanMmsc(
        val carrierName: String,
        val url: String,
        val proxy: String,
        val port: Int
    )
}
