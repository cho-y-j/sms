package com.bizconnect.v2.data.remote

import com.bizconnect.v2.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BizConnect 서버 API 클라이언트
 * - SMS/LMS 발송 (Wideshot API 경유)
 * - 결제 (NICE Pay)
 * - 잔액/사용량 조회
 */
@Singleton
class BizConnectApiService @Inject constructor(
    private val appPreferences: AppPreferences
) {
    companion object {
        private const val BASE_URL = "https://sm.on1.kr"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun getToken(): String? = appPreferences.getAccessToken()

    // ============================================================
    // SMS 발송
    // ============================================================

    data class SmsSendResult(
        val success: Boolean,
        val sendCode: String? = null,
        val msgType: String = "sms",
        val cost: Int = 0,
        val error: String? = null
    )

    /**
     * 서버 API를 통한 SMS/LMS 발송 (Wideshot 경유)
     */
    suspend fun sendSms(
        phone: String,
        message: String,
        callback: String? = null,
        subject: String? = null
    ): SmsSendResult = withContext(Dispatchers.IO) {
        try {
            val token = getToken() ?: return@withContext SmsSendResult(false, error = "로그인 필요")

            val body = JSONObject().apply {
                put("phone", phone)
                put("message", message)
                callback?.let { put("callback", it) }
                subject?.let { put("subject", it) }
            }

            val request = Request.Builder()
                .url("$BASE_URL/api/sms/send")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            val json = JSONObject(responseBody)

            if (response.isSuccessful && json.optString("success") == "true") {
                SmsSendResult(
                    success = true,
                    sendCode = json.optString("sendCode"),
                    msgType = json.optString("msgType", "sms"),
                    cost = json.optString("cost", "0").toIntOrNull() ?: 0
                )
            } else {
                SmsSendResult(false, error = json.optString("error", "발송 실패"))
            }
        } catch (e: Exception) {
            SmsSendResult(false, error = e.message)
        }
    }

    /**
     * 서버 API를 통한 대량 SMS 발송
     */
    suspend fun sendBatchSms(
        phones: List<String>,
        message: String,
        callback: String? = null,
        subject: String? = null
    ): BatchSendResult = withContext(Dispatchers.IO) {
        try {
            val token = getToken() ?: return@withContext BatchSendResult(0, 0, 0, 0, "로그인 필요")

            val body = JSONObject().apply {
                put("phones", JSONArray(phones))
                put("message", message)
                callback?.let { put("callback", it) }
                subject?.let { put("subject", it) }
            }

            val request = Request.Builder()
                .url("$BASE_URL/api/sms/batch")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            val json = JSONObject(responseBody)

            if (response.isSuccessful) {
                BatchSendResult(
                    total = json.optString("total", "0").toIntOrNull() ?: 0,
                    success = json.optString("success", "0").toIntOrNull() ?: 0,
                    failed = json.optString("failed", "0").toIntOrNull() ?: 0,
                    totalCost = json.optString("totalCost", "0").toIntOrNull() ?: 0
                )
            } else {
                BatchSendResult(0, 0, 0, 0, json.optString("error", "대량 발송 실패"))
            }
        } catch (e: Exception) {
            BatchSendResult(0, 0, 0, 0, e.message)
        }
    }

    data class BatchSendResult(
        val total: Int,
        val success: Int,
        val failed: Int,
        val totalCost: Int,
        val error: String? = null
    )

    // ============================================================
    // 알림톡 발송
    // ============================================================

    suspend fun sendAlimtalk(
        phone: String,
        message: String,
        templateCode: String? = null
    ): SmsSendResult = withContext(Dispatchers.IO) {
        try {
            val token = getToken() ?: return@withContext SmsSendResult(false, error = "로그인 필요")

            val body = JSONObject().apply {
                put("phone", phone)
                put("message", message)
                templateCode?.let { put("templateCode", it) }
            }

            val request = Request.Builder()
                .url("$BASE_URL/api/sms/alimtalk")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            val json = JSONObject(responseBody)

            if (response.isSuccessful && json.optString("success") == "true") {
                SmsSendResult(
                    success = true,
                    sendCode = json.optString("sendCode"),
                    msgType = "alimtalk",
                    cost = json.optString("cost", "0").toIntOrNull() ?: 0
                )
            } else {
                SmsSendResult(false, error = json.optString("error", "알림톡 발송 실패"))
            }
        } catch (e: Exception) {
            SmsSendResult(false, error = e.message)
        }
    }

    // ============================================================
    // 잔액 조회
    // ============================================================

    data class BalanceInfo(
        val balance: Double = 0.0,
        val smsCost: Int = 8,
        val lmsCost: Int = 25,
        val mmsCost: Int = 50,
        val alimtalkCost: Int = 7
    )

    suspend fun getBalance(): BalanceInfo = withContext(Dispatchers.IO) {
        try {
            val token = getToken() ?: return@withContext BalanceInfo()

            val request = Request.Builder()
                .url("$BASE_URL/api/sms/balance")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            val json = JSONObject(responseBody)

            if (response.isSuccessful) {
                val pricing = json.optJSONObject("pricing")
                BalanceInfo(
                    balance = json.optString("balance", "0").toDoubleOrNull() ?: 0.0,
                    smsCost = pricing?.optString("sms", "8")?.toIntOrNull() ?: 8,
                    lmsCost = pricing?.optString("lms", "25")?.toIntOrNull() ?: 25,
                    mmsCost = pricing?.optString("mms", "50")?.toIntOrNull() ?: 50,
                    alimtalkCost = pricing?.optString("alimtalk", "7")?.toIntOrNull() ?: 7
                )
            } else {
                BalanceInfo()
            }
        } catch (_: Exception) {
            BalanceInfo()
        }
    }

    // ============================================================
    // 결제 준비
    // ============================================================

    data class PrepareResult(
        val success: Boolean,
        val checkoutUrl: String? = null,
        val orderId: String? = null,
        val error: String? = null
    )

    suspend fun preparePayment(
        amount: Int,
        goodsName: String,
        paymentType: String
    ): PrepareResult = withContext(Dispatchers.IO) {
        try {
            val token = getToken()

            val body = JSONObject().apply {
                put("amount", amount)
                put("goodsName", goodsName)
                put("method", "card")
                put("type", paymentType)
            }

            val requestBuilder = Request.Builder()
                .url("$BASE_URL/api/payment/prepare")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_TYPE))

            if (token != null) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: "{}"
            val json = JSONObject(responseBody)

            if (response.isSuccessful) {
                PrepareResult(
                    success = true,
                    checkoutUrl = json.optString("checkoutUrl"),
                    orderId = json.optString("orderId")
                )
            } else {
                PrepareResult(false, error = json.optString("error", "결제 준비 실패"))
            }
        } catch (e: Exception) {
            PrepareResult(false, error = e.message)
        }
    }

    // ============================================================
    // 발송 결과 조회
    // ============================================================

    suspend fun checkSendResult(sendCode: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val token = getToken() ?: return@withContext null

            val request = Request.Builder()
                .url("$BASE_URL/api/sms/result/$sendCode")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            JSONObject(body)
        } catch (_: Exception) { null }
    }
}
