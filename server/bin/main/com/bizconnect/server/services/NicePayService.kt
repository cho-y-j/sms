package com.bizconnect.server.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

/**
 * NICE Pay 결제 게이트웨이 서비스
 */
class NicePayService {
    companion object {
        private const val SDK_URL = "https://pay.nicepay.co.kr/v1/js/"
        val CLIENT_ID = System.getenv("NICEPAY_CLIENT_ID") ?: "R2_89b0448a77264cbd9e0f7ccd6a1421f7"
        val SECRET_KEY = System.getenv("NICEPAY_SECRET_KEY") ?: "dc2fa52d9880444eaa54820455bb9370"
        val MODE = System.getenv("NICEPAY_MODE") ?: "sandbox"

        private fun getApiBase(): String {
            return if (MODE == "production") "https://api.nicepay.co.kr/v1"
            else "https://sandbox-api.nicepay.co.kr/v1"
        }
    }

    private val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 30000
            endpoint { connectTimeout = 5000 }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Crypto
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun generateBasicAuth(): String {
        val credentials = Base64.getEncoder().encodeToString("$CLIENT_ID:$SECRET_KEY".toByteArray())
        return "Basic $credentials"
    }

    fun generateEdiDate(): String = Instant.now().toString()

    fun approvalSignData(tid: String, amount: Int, ediDate: String): String =
        sha256("$tid$amount$ediDate$SECRET_KEY")

    fun cancelSignData(tid: String, ediDate: String): String =
        sha256("$tid$ediDate$SECRET_KEY")

    fun verifyAuthSignature(authToken: String, clientId: String, amount: Int, signature: String): Boolean =
        sha256("$authToken$clientId$amount$SECRET_KEY") == signature

    fun verifyResponseSignature(tid: String, amount: Int, ediDate: String, signature: String): Boolean =
        sha256("$tid$amount$ediDate$SECRET_KEY") == signature

    // SDK Params
    data class SdkParams(
        val clientId: String, val method: String, val orderId: String,
        val amount: Int, val goodsName: String, val returnUrl: String,
        val buyerName: String?, val buyerEmail: String?, val buyerTel: String?,
        val sdkUrl: String
    )

    fun createSdkParams(
        orderId: String, amount: Int, goodsName: String, method: String,
        returnUrl: String, buyerName: String? = null, buyerEmail: String? = null, buyerTel: String? = null
    ): SdkParams = SdkParams(CLIENT_ID, method, orderId, amount, goodsName, returnUrl, buyerName, buyerEmail, buyerTel, SDK_URL)

    // Approval
    data class ApprovalResult(
        val success: Boolean, val tid: String? = null, val orderId: String? = null,
        val status: String? = null, val amount: Int = 0, val balanceAmt: Int = 0,
        val approveNo: String? = null, val channel: String? = null,
        val paidAt: String? = null, val cardInfo: String? = null,
        val resultCode: String? = null, val resultMsg: String? = null
    )

    suspend fun approve(tid: String, amount: Int): ApprovalResult {
        val ediDate = generateEdiDate()
        val signData = approvalSignData(tid, amount, ediDate)

        val body = JsonObject(mapOf(
            "amount" to JsonPrimitive(amount),
            "ediDate" to JsonPrimitive(ediDate),
            "signData" to JsonPrimitive(signData)
        ))

        return try {
            val response = httpClient.post("${getApiBase()}/payments/$tid") {
                header("Authorization", generateBasicAuth())
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            val responseBody = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val resultCode = jsonResponse["resultCode"]?.jsonPrimitive?.content ?: ""

            if (resultCode == "0000") {
                ApprovalResult(
                    success = true,
                    tid = jsonResponse["tid"]?.jsonPrimitive?.contentOrNull,
                    orderId = jsonResponse["orderId"]?.jsonPrimitive?.contentOrNull,
                    status = jsonResponse["status"]?.jsonPrimitive?.contentOrNull,
                    amount = jsonResponse["amount"]?.jsonPrimitive?.intOrNull ?: amount,
                    balanceAmt = jsonResponse["balanceAmt"]?.jsonPrimitive?.intOrNull ?: amount,
                    approveNo = jsonResponse["approveNo"]?.jsonPrimitive?.contentOrNull,
                    channel = jsonResponse["channel"]?.jsonPrimitive?.contentOrNull,
                    paidAt = jsonResponse["paidAt"]?.jsonPrimitive?.contentOrNull,
                    cardInfo = jsonResponse["card"]?.toString(),
                    resultCode = resultCode,
                    resultMsg = jsonResponse["resultMsg"]?.jsonPrimitive?.contentOrNull
                )
            } else {
                ApprovalResult(success = false, resultCode = resultCode,
                    resultMsg = jsonResponse["resultMsg"]?.jsonPrimitive?.contentOrNull)
            }
        } catch (e: Exception) {
            netCancel(tid, amount)
            ApprovalResult(success = false, resultCode = "TIMEOUT", resultMsg = e.message)
        }
    }

    // Cancel
    data class CancelResult(
        val success: Boolean, val cancelledTid: String? = null,
        val balanceAmt: Int = 0, val resultCode: String? = null, val resultMsg: String? = null
    )

    suspend fun cancel(tid: String, reason: String, orderId: String, cancelAmt: Int? = null): CancelResult {
        val ediDate = generateEdiDate()
        val signData = cancelSignData(tid, ediDate)

        val bodyMap = mutableMapOf<String, JsonElement>(
            "reason" to JsonPrimitive(reason),
            "orderId" to JsonPrimitive(orderId),
            "ediDate" to JsonPrimitive(ediDate),
            "signData" to JsonPrimitive(signData)
        )
        cancelAmt?.let { bodyMap["cancelAmt"] = JsonPrimitive(it) }

        return try {
            val response = httpClient.post("${getApiBase()}/payments/$tid/cancel") {
                header("Authorization", generateBasicAuth())
                contentType(ContentType.Application.Json)
                setBody(JsonObject(bodyMap).toString())
            }
            val responseBody = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val resultCode = jsonResponse["resultCode"]?.jsonPrimitive?.content ?: ""

            CancelResult(
                success = resultCode == "0000",
                cancelledTid = jsonResponse["cancelledTid"]?.jsonPrimitive?.contentOrNull,
                balanceAmt = jsonResponse["balanceAmt"]?.jsonPrimitive?.intOrNull ?: 0,
                resultCode = resultCode,
                resultMsg = jsonResponse["resultMsg"]?.jsonPrimitive?.contentOrNull
            )
        } catch (e: Exception) {
            CancelResult(success = false, resultCode = "ERROR", resultMsg = e.message)
        }
    }

    // Net Cancel (망취소)
    private suspend fun netCancel(tid: String, amount: Int) {
        try {
            val ediDate = generateEdiDate()
            val signData = sha256("$tid$ediDate$SECRET_KEY")
            val body = JsonObject(mapOf(
                "orderAmount" to JsonPrimitive(amount),
                "orderId" to JsonPrimitive(tid),
                "ediDate" to JsonPrimitive(ediDate),
                "signData" to JsonPrimitive(signData)
            ))
            httpClient.post("${getApiBase()}/payments/netcancel") {
                header("Authorization", generateBasicAuth())
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (_: Exception) { }
    }

    // Inquiry
    suspend fun inquiryByTid(tid: String): JsonObject? {
        return try {
            val response = httpClient.get("${getApiBase()}/payments/$tid") {
                header("Authorization", generateBasicAuth())
            }
            json.parseToJsonElement(response.bodyAsText()).jsonObject
        } catch (_: Exception) { null }
    }

    // Checkout HTML
    fun generateCheckoutHtml(sdkParams: SdkParams): String = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BizConnect 결제</title>
    <script src="${sdkParams.sdkUrl}"></script>
</head>
<body>
<script>
    AUTHNICE.requestPay({
        clientId: '${sdkParams.clientId}',
        method: '${sdkParams.method}',
        orderId: '${sdkParams.orderId}',
        amount: ${sdkParams.amount},
        goodsName: '${sdkParams.goodsName.replace("'", "\\'")}',
        returnUrl: '${sdkParams.returnUrl}',
        ${sdkParams.buyerName?.let { "buyerName: '${it.replace("'", "\\'")}', " } ?: ""}
        ${sdkParams.buyerEmail?.let { "buyerEmail: '$it', " } ?: ""}
        ${sdkParams.buyerTel?.let { "buyerTel: '$it', " } ?: ""}
        fnError: function(result) {
            window.location.href = 'bizconnect://payment/error?code=' + result.resultCode + '&msg=' + encodeURIComponent(result.resultMsg);
        }
    });
</script>
</body>
</html>
    """.trimIndent()
}
