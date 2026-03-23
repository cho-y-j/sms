package com.bizconnect.server.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Wideshot SMS API 서비스
 * SMS/LMS/알림톡 발송
 */
class WideshotSmsService {
    companion object {
        val API_URL = System.getenv("WIDESHOT_API_URL") ?: "https://apimsg.wideshot.co.kr"
        val API_KEY = System.getenv("WIDESHOT_API_KEY") ?: ""
        val CALLBACK = System.getenv("WIDESHOT_CALLBACK") ?: ""

        val SENDER_KEY = System.getenv("SEJONG_SENDER_KEY") ?: ""
        val TEMPLATE_CODE = System.getenv("SEJONG_TEMPLATE_CODE") ?: ""

        const val COST_SMS = 8
        const val COST_LMS = 25
        const val COST_MMS = 50
        const val COST_ALIMTALK = 7
    }

    private val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 30000
            endpoint { connectTimeout = 10000 }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    data class CostInfo(val cost: Int, val msgType: String)

    fun calculateCost(message: String, msgType: String? = null): CostInfo {
        if (msgType == "alimtalk") return CostInfo(COST_ALIMTALK, "alimtalk")
        val byteLen = message.toByteArray(Charsets.UTF_8).size
        return if (byteLen <= 90) CostInfo(COST_SMS, "sms") else CostInfo(COST_LMS, "lms")
    }

    data class SendResult(
        val success: Boolean, val sendCode: String? = null,
        val method: String = "api", val error: String? = null,
        val msgType: String = "sms", val cost: Int = 0,
        val errorCode: String? = null, val errorMessage: String? = null
    )

    suspend fun sendMessage(
        phone: String, message: String, callback: String? = null, subject: String? = null
    ): SendResult {
        if (API_KEY.isBlank()) return SendResult(success = false, error = "Wideshot API key not configured")

        val costInfo = calculateCost(message)
        val senderNumber = callback ?: CALLBACK
        val endpoint = if (costInfo.msgType == "sms") "/api/v1/message/sms" else "/api/v1/message/lms"

        return try {
            val userKey = "biz_${System.currentTimeMillis()}"
            val response = httpClient.submitForm(
                url = "$API_URL$endpoint",
                formParameters = Parameters.build {
                    append("callback", senderNumber)
                    append("receiverTelNo", phone.replace("-", ""))
                    append("contents", message)
                    append("userKey", userKey)
                    if (costInfo.msgType == "lms" && subject != null) append("subject", subject)
                }
            ) {
                header("sejongApiKey", API_KEY)
            }

            val body = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val code = jsonResponse["code"]?.jsonPrimitive?.contentOrNull

            if (code == "200") {
                SendResult(
                    success = true,
                    sendCode = jsonResponse["sendCode"]?.jsonPrimitive?.contentOrNull,
                    msgType = costInfo.msgType,
                    cost = costInfo.cost
                )
            } else {
                val errMsg = jsonResponse["message"]?.jsonPrimitive?.contentOrNull ?: "Send failed ($code)"
                // Balance warning detection
                if (errMsg.contains("잔액") || errMsg.contains("balance", ignoreCase = true) || errMsg.contains("충전")) {
                    println("CRITICAL: Wideshot balance warning - $errMsg (code=$code)")
                }
                SendResult(success = false,
                    error = errMsg,
                    msgType = costInfo.msgType,
                    errorCode = code,
                    errorMessage = errMsg)
            }
        } catch (e: Exception) {
            val errMsg = e.message ?: "Unknown exception"
            // Balance warning detection
            if (errMsg.contains("잔액") || errMsg.contains("balance", ignoreCase = true) || errMsg.contains("충전")) {
                println("CRITICAL: Wideshot balance warning - $errMsg")
            }
            SendResult(success = false, error = errMsg, msgType = costInfo.msgType,
                errorCode = "EXCEPTION", errorMessage = errMsg)
        }
    }

    suspend fun sendAlimtalk(
        phone: String, message: String, templateCode: String? = null,
        senderKey: String? = null, fallbackType: String = "sms"
    ): SendResult {
        if (API_KEY.isBlank()) return SendResult(success = false, error = "Wideshot API key not configured")

        val useSenderKey = senderKey ?: SENDER_KEY
        val useTemplateCode = templateCode ?: TEMPLATE_CODE
        val nextType = when (fallbackType) { "sms" -> "7"; "lms" -> "8"; else -> "0" }

        return try {
            val response = httpClient.submitForm(
                url = "$API_URL/api/v3/message/alimtalk",
                formParameters = Parameters.build {
                    append("callback", CALLBACK)
                    append("dst_addr", phone.replace("-", ""))
                    append("text", message)
                    append("sender_key", useSenderKey)
                    append("template_code", useTemplateCode)
                    append("next_type", nextType)
                }
            ) {
                header("sejongApiKey", API_KEY)
            }

            val body = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val code = jsonResponse["code"]?.jsonPrimitive?.contentOrNull

            if (code == "200") {
                SendResult(success = true,
                    sendCode = jsonResponse["sendCode"]?.jsonPrimitive?.contentOrNull,
                    msgType = "alimtalk", cost = COST_ALIMTALK)
            } else {
                SendResult(success = false, error = "AlimTalk failed: $code", msgType = "alimtalk")
            }
        } catch (e: Exception) {
            SendResult(success = false, error = e.message, msgType = "alimtalk")
        }
    }

    data class BatchResult(
        val total: Int, val success: Int, val failed: Int,
        val totalCost: Int, val results: List<SendResult>
    )

    suspend fun sendBatch(
        phones: List<String>, message: String, callback: String? = null, subject: String? = null
    ): BatchResult {
        val results = mutableListOf<SendResult>()
        var successCount = 0; var failedCount = 0; var totalCost = 0

        for (phone in phones) {
            val result = sendMessage(phone, message, callback, subject)
            results.add(result)
            if (result.success) { successCount++; totalCost += result.cost } else failedCount++
        }

        return BatchResult(phones.size, successCount, failedCount, totalCost, results)
    }

    suspend fun checkResult(sendCode: String): JsonObject? {
        if (API_KEY.isBlank()) return null
        return try {
            val response = httpClient.get("$API_URL/api/v3/message/result?sendCode=$sendCode") {
                header("sejongApiKey", API_KEY)
            }
            json.parseToJsonElement(response.bodyAsText()).jsonObject
        } catch (_: Exception) { null }
    }
}
