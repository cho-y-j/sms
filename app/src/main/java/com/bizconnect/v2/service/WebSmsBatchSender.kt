package com.bizconnect.v2.service

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.bizconnect.v2.data.remote.TokenManager
import com.bizconnect.v2.util.NotificationUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 웹에서 요청한 문자를 사용자 폰으로 발송하는 공유 로직.
 *
 * FCM 서비스(웹 배치 푸시), 승인 알림 리시버, 앱 시작 시 폴러가 모두 이 클래스를 사용한다.
 * 발송은 항상 "사용자 확인 후"에만 호출되도록 호출부에서 보장한다(2건 이상 승인 알림 경유).
 */
@Singleton
class WebSmsBatchSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
    private val notificationUtil: NotificationUtil
) {
    companion object {
        private const val TAG = "WebSmsBatchSender"
        private const val SERVER_BASE_URL = "https://sm.on1.kr"
        private const val SMS_SEND_DELAY_MS = 3000L // 3초 간격 (분당 20건 throttle)
    }

    /** 특정 작업(jobId)의 문자 발송. 인라인 메시지가 있으면 그것을, 없으면 서버에서 조회. */
    suspend fun sendJob(jobId: String?, messagesJson: String?) = withContext(Dispatchers.IO) {
        val messages = parseInline(messagesJson) ?: jobId?.let { fetch("$SERVER_BASE_URL/api/user/sms/pending?jobId=$it") } ?: emptyList()
        sendList(messages)
    }

    /** 대기 중인 모든 웹 문자 발송 (앱 시작 폴러 / 작업 식별자 없는 경우). */
    suspend fun sendAllPending() = withContext(Dispatchers.IO) {
        sendList(fetch("$SERVER_BASE_URL/api/user/sms/pending"))
    }

    /** 대기 건수 조회 (승인 알림을 띄울지 판단용). */
    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) {
        fetch("$SERVER_BASE_URL/api/user/sms/pending").size
    }

    private suspend fun sendList(messages: List<JSONObject>) {
        if (messages.isEmpty()) {
            Log.w(TAG, "sendList: no messages")
            return
        }
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        var success = 0
        var failure = 0
        for ((index, msg) in messages.withIndex()) {
            val msgId = msg.optString("id")
            val phone = msg.optString("phone")
            val body = msg.optString("message")
            if (phone.isBlank() || body.isBlank()) {
                failure++
                reportStatus(msgId, "failed", "Empty phone number or message body")
                continue
            }
            if (index > 0) delay(SMS_SEND_DELAY_MS)
            try {
                val parts = smsManager.divideMessage(body)
                if (parts.size == 1) smsManager.sendTextMessage(phone, null, body, null, null)
                else smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                Log.d(TAG, "sent ${index + 1}/${messages.size} to $phone (id=$msgId)")
                success++
                runCatching { reportStatus(msgId, "sent", null) }
            } catch (e: Exception) {
                Log.e(TAG, "send failed to $phone (id=$msgId)", e)
                failure++
                runCatching { reportStatus(msgId, "failed", e.message ?: "Unknown send error") }
            }
        }
        // 발송 결과를 푸시로 알림 (1건 자동 발송도 "보냈어요"로 확인됨)
        if (success + failure > 0) {
            runCatching { notificationUtil.showSendResultNotification(success, failure) }
        }
    }

    private fun parseInline(messagesJson: String?): List<JSONObject>? {
        if (messagesJson.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(messagesJson)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) {
            Log.w(TAG, "parseInline failed", e)
            null
        }
    }

    private fun fetch(url: String): List<JSONObject> {
        return try {
            val resp = tokenManager.executeAuthenticated(Request.Builder().url(url).get())
            resp.use {
                if (!it.isSuccessful) {
                    Log.e(TAG, "fetch: HTTP ${it.code} for $url")
                    return emptyList()
                }
                val bodyStr = it.body?.string().orEmpty()
                if (bodyStr.isBlank()) return emptyList()
                try {
                    val arr = JSONArray(bodyStr)
                    (0 until arr.length()).map { i -> arr.getJSONObject(i) }
                } catch (_: Exception) {
                    val obj = JSONObject(bodyStr)
                    val arr = if (obj.has("data")) obj.getJSONArray("data") else obj.getJSONArray("messages")
                    (0 until arr.length()).map { i -> arr.getJSONObject(i) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetch failed for $url", e)
            emptyList()
        }
    }

    private fun reportStatus(messageId: String, status: String, error: String?) {
        if (messageId.isBlank()) return
        try {
            val json = JSONObject().apply {
                put("status", status)
                if (error != null) put("error", error)
            }
            val req = Request.Builder()
                .url("$SERVER_BASE_URL/api/user/sms/$messageId/status")
                .put(json.toString().toRequestBody("application/json".toMediaType()))
            tokenManager.executeAuthenticated(req).use { resp ->
                if (!resp.isSuccessful) Log.w(TAG, "reportStatus: HTTP ${resp.code} for id=$messageId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "reportStatus failed for id=$messageId", e)
        }
    }
}
