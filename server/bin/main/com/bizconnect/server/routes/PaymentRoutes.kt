package com.bizconnect.server.routes

import com.bizconnect.server.database.*
import com.bizconnect.server.services.NicePayService
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.request.receiveText
import io.ktor.server.request.receiveParameters
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondRedirect
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * NICE Pay 결제 라우트
 *
 * Flow:
 * 1. POST /api/payment/prepare → 결제 준비, SDK params 반환
 * 2. GET /api/payment/checkout/{orderId} → WebView용 결제 HTML
 * 3. POST /api/payment/approve → NICE 인증 콜백 수신 → 승인 API 호출
 * 4. POST /api/payment/{tid}/cancel → 결제 취소/환불
 * 5. GET /api/payment/{tid} → 결제 상태 조회
 * 6. POST /api/payment/webhook → NICE 웹훅 수신
 */
fun Route.paymentRoutes(nicePayService: NicePayService) {
    route("/api/payment") {

        // POST /prepare - 결제 준비 (앱에서 호출)
        post("/prepare") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()

                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject

                val orderId = json["orderId"]?.jsonPrimitive?.contentOrNull
                    ?: "BC-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
                val amount = json["amount"]?.jsonPrimitive?.intOrNull
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "amount required"))
                val goodsName = json["goodsName"]?.jsonPrimitive?.contentOrNull ?: "BizConnect 서비스"
                val method = json["method"]?.jsonPrimitive?.contentOrNull ?: "card"
                val buyerName = json["buyerName"]?.jsonPrimitive?.contentOrNull
                val buyerEmail = json["buyerEmail"]?.jsonPrimitive?.contentOrNull
                val buyerTel = json["buyerTel"]?.jsonPrimitive?.contentOrNull
                val paymentType = json["type"]?.jsonPrimitive?.contentOrNull ?: "credit_charge"

                // 서버의 approve 엔드포인트를 returnUrl로 설정
                val serverUrl = System.getenv("SERVER_PUBLIC_URL") ?: "https://sm.on1.kr"
                val returnUrl = "$serverUrl/api/payment/approve"

                // DB에 결제 준비 정보 저장
                val paymentId = UUID.randomUUID().toString()
                transaction {
                    NicePaymentsTable.insert {
                        it[id] = paymentId
                        it[NicePaymentsTable.orderId] = orderId
                        it[NicePaymentsTable.userId] = userId
                        it[NicePaymentsTable.amount] = amount
                        it[NicePaymentsTable.goodsName] = goodsName
                        it[payMethod] = method
                        it[NicePaymentsTable.returnUrl] = returnUrl
                        it[status] = "ready"
                        it[NicePaymentsTable.buyerName] = buyerName
                        it[NicePaymentsTable.buyerEmail] = buyerEmail
                        it[NicePaymentsTable.buyerTel] = buyerTel
                        it[NicePaymentsTable.paymentType] = paymentType
                        it[createdAt] = System.currentTimeMillis()
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }

                // SDK 파라미터 생성
                val sdkParams = nicePayService.createSdkParams(
                    orderId = orderId,
                    amount = amount,
                    goodsName = goodsName,
                    method = method,
                    returnUrl = returnUrl,
                    buyerName = buyerName,
                    buyerEmail = buyerEmail,
                    buyerTel = buyerTel
                )

                call.respond(HttpStatusCode.Created, mapOf(
                    "paymentId" to paymentId,
                    "orderId" to orderId,
                    "amount" to amount.toString(),
                    "status" to "ready",
                    "checkoutUrl" to "$serverUrl/api/payment/checkout/$orderId",
                    "sdkUrl" to sdkParams.sdkUrl,
                    "clientId" to sdkParams.clientId,
                    "method" to method,
                    "goodsName" to goodsName,
                    "returnUrl" to returnUrl,
                    "buyerName" to (buyerName ?: ""),
                    "buyerEmail" to (buyerEmail ?: ""),
                    "buyerTel" to (buyerTel ?: "")
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Prepare failed")))
            }
        }

        // GET /checkout/{orderId} - WebView용 결제 페이지 HTML
        get("/checkout/{orderId}") {
            try {
                val orderId = call.parameters["orderId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing orderId")

                val payment = transaction {
                    NicePaymentsTable.selectAll()
                        .where { NicePaymentsTable.orderId eq orderId }
                        .firstOrNull()
                }

                if (payment == null) {
                    call.respond(HttpStatusCode.NotFound, "Payment not found")
                    return@get
                }

                if (payment[NicePaymentsTable.status] != "ready") {
                    call.respond(HttpStatusCode.BadRequest, "Payment already processed")
                    return@get
                }

                val sdkParams = nicePayService.createSdkParams(
                    orderId = orderId,
                    amount = payment[NicePaymentsTable.amount],
                    goodsName = payment[NicePaymentsTable.goodsName],
                    method = payment[NicePaymentsTable.payMethod],
                    returnUrl = payment[NicePaymentsTable.returnUrl],
                    buyerName = payment[NicePaymentsTable.buyerName],
                    buyerEmail = payment[NicePaymentsTable.buyerEmail],
                    buyerTel = payment[NicePaymentsTable.buyerTel]
                )

                val html = nicePayService.generateCheckoutHtml(sdkParams)
                call.respondText(html, ContentType.Text.Html)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Checkout error: ${e.message}")
            }
        }

        // POST /approve - NICE 인증 콜백 (NicePay → 서버)
        post("/approve") {
            try {
                val params = call.receiveParameters()
                val authResultCode = params["authResultCode"] ?: ""
                val authResultMsg = params["authResultMsg"] ?: ""
                val tid = params["tid"] ?: ""
                val clientId = params["clientId"] ?: ""
                val orderId = params["orderId"] ?: ""
                val amount = params["amount"] ?: "0"
                val authToken = params["authToken"] ?: ""
                val signature = params["signature"] ?: ""

                val payment = transaction {
                    NicePaymentsTable.selectAll()
                        .where { NicePaymentsTable.orderId eq orderId }
                        .firstOrNull()
                }

                if (payment == null) {
                    call.respondRedirect("bizconnect://payment/error?code=NOT_FOUND&msg=Payment+not+found")
                    return@post
                }

                // 인증 실패
                if (authResultCode != "0000") {
                    transaction {
                        NicePaymentsTable.update({ NicePaymentsTable.orderId eq orderId }) {
                            it[status] = "failed"
                            it[resultCode] = authResultCode
                            it[resultMsg] = authResultMsg
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }
                    call.respondRedirect("bizconnect://payment/fail?orderId=$orderId&code=$authResultCode")
                    return@post
                }

                // 서명 검증
                val parsedAmount = amount.toIntOrNull() ?: 0
                if (!nicePayService.verifyAuthSignature(authToken, clientId, parsedAmount, signature)) {
                    transaction {
                        NicePaymentsTable.update({ NicePaymentsTable.orderId eq orderId }) {
                            it[status] = "failed"
                            it[resultCode] = "SIGNATURE_INVALID"
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }
                    call.respondRedirect("bizconnect://payment/fail?orderId=$orderId&code=SIGNATURE_INVALID")
                    return@post
                }

                // 금액 검증 (위변조 방지)
                val storedAmount = payment[NicePaymentsTable.amount]
                if (parsedAmount != storedAmount) {
                    transaction {
                        NicePaymentsTable.update({ NicePaymentsTable.orderId eq orderId }) {
                            it[status] = "failed"
                            it[resultCode] = "AMOUNT_MISMATCH"
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }
                    call.respondRedirect("bizconnect://payment/fail?orderId=$orderId&code=AMOUNT_MISMATCH")
                    return@post
                }

                // TID 저장
                transaction {
                    NicePaymentsTable.update({ NicePaymentsTable.orderId eq orderId }) {
                        it[NicePaymentsTable.tid] = tid
                        it[NicePaymentsTable.authToken] = authToken
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }

                // NICE 승인 API 호출
                val approvalResult = nicePayService.approve(tid, parsedAmount)

                if (approvalResult.success) {
                    val userId = payment[NicePaymentsTable.userId]
                    val paymentType = payment[NicePaymentsTable.paymentType]

                    transaction {
                        // 결제 상태 업데이트
                        NicePaymentsTable.update({ NicePaymentsTable.orderId eq orderId }) {
                            it[status] = "paid"
                            it[NicePaymentsTable.tid] = approvalResult.tid ?: tid
                            it[approveNo] = approvalResult.approveNo
                            it[balanceAmt] = approvalResult.balanceAmt
                            it[channel] = approvalResult.channel
                            it[cardInfo] = approvalResult.cardInfo
                            it[paidAt] = System.currentTimeMillis()
                            it[resultCode] = approvalResult.resultCode
                            it[resultMsg] = approvalResult.resultMsg
                            it[updatedAt] = System.currentTimeMillis()
                        }

                        // 크레딧 충전인 경우 잔액 업데이트
                        if (userId != null && paymentType == "credit_charge") {
                            val existing = CreditBalancesTable.selectAll()
                                .where { CreditBalancesTable.userId eq userId }
                                .firstOrNull()

                            if (existing != null) {
                                val currentBalance = existing[CreditBalancesTable.balance]
                                CreditBalancesTable.update({ CreditBalancesTable.userId eq userId }) {
                                    it[balance] = currentBalance + parsedAmount
                                    it[updatedAt] = System.currentTimeMillis()
                                }
                            } else {
                                CreditBalancesTable.insert {
                                    it[id] = UUID.randomUUID().toString()
                                    it[CreditBalancesTable.userId] = userId
                                    it[balance] = parsedAmount.toDouble()
                                    it[updatedAt] = System.currentTimeMillis()
                                }
                            }
                        }

                        // 구독인 경우 구독 활성화
                        if (userId != null && paymentType == "subscription") {
                            val tier = when {
                                parsedAmount >= 9900 -> "premium"
                                parsedAmount >= 4900 -> "paid"
                                else -> "free"
                            }

                            // 기존 구독 비활성화
                            SubscriptionsTable.update({
                                (SubscriptionsTable.userId eq userId) and (SubscriptionsTable.isActive eq true)
                            }) {
                                it[isActive] = false
                                it[endDate] = System.currentTimeMillis()
                            }

                            // 새 구독 생성
                            SubscriptionsTable.insert {
                                it[id] = UUID.randomUUID().toString()
                                it[SubscriptionsTable.userId] = userId
                                it[SubscriptionsTable.tier] = tier
                                it[startDate] = System.currentTimeMillis()
                                it[isActive] = true
                                it[monthlyPrice] = parsedAmount
                                it[createdAt] = System.currentTimeMillis()
                            }
                        }

                        // PaymentsTable에도 기록
                        if (userId != null) {
                            PaymentsTable.insert {
                                it[id] = UUID.randomUUID().toString()
                                it[PaymentsTable.userId] = userId
                                it[PaymentsTable.amount] = parsedAmount
                                it[type] = paymentType ?: "credit_charge"
                                it[status] = "completed"
                                it[description] = payment[NicePaymentsTable.goodsName]
                                it[transactionId] = approvalResult.tid ?: tid
                                it[createdAt] = System.currentTimeMillis()
                            }
                        }
                    }

                    call.respondRedirect("bizconnect://payment/success?orderId=$orderId&tid=${approvalResult.tid ?: tid}&amount=$parsedAmount")
                } else {
                    transaction {
                        NicePaymentsTable.update({ NicePaymentsTable.orderId eq orderId }) {
                            it[status] = "failed"
                            it[resultCode] = approvalResult.resultCode
                            it[resultMsg] = approvalResult.resultMsg
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }
                    call.respondRedirect("bizconnect://payment/fail?orderId=$orderId&code=${approvalResult.resultCode}")
                }
            } catch (e: Exception) {
                call.respondRedirect("bizconnect://payment/error?msg=${java.net.URLEncoder.encode(e.message ?: "Error", "UTF-8")}")
            }
        }

        // POST /{tid}/cancel - 결제 취소
        post("/{tid}/cancel") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val tid = call.parameters["tid"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing tid"))

                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val reason = json["reason"]?.jsonPrimitive?.contentOrNull ?: "사용자 요청 취소"
                val cancelAmt = json["cancelAmt"]?.jsonPrimitive?.intOrNull

                // 결제 조회
                val payment = transaction {
                    NicePaymentsTable.selectAll()
                        .where { NicePaymentsTable.tid eq tid }
                        .firstOrNull()
                }

                if (payment == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Payment not found"))
                    return@post
                }

                val orderId = payment[NicePaymentsTable.orderId]

                // NICE 취소 API 호출
                val result = nicePayService.cancel(tid, reason, orderId, cancelAmt)

                if (result.success) {
                    transaction {
                        val newStatus = if (cancelAmt != null && cancelAmt < payment[NicePaymentsTable.amount]) {
                            "partialCancelled"
                        } else "cancelled"

                        NicePaymentsTable.update({ NicePaymentsTable.tid eq tid }) {
                            it[status] = newStatus
                            it[balanceAmt] = result.balanceAmt
                            it[cancelledAt] = System.currentTimeMillis()
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }

                    call.respond(HttpStatusCode.OK, mapOf(
                        "message" to "Payment cancelled",
                        "cancelledTid" to (result.cancelledTid ?: ""),
                        "balanceAmt" to result.balanceAmt.toString()
                    ))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "Cancel failed: ${result.resultMsg}"
                    ))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Cancel failed")))
            }
        }

        // GET /{tid} - 결제 상태 조회
        get("/{tid}") {
            try {
                val tid = call.parameters["tid"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing tid"))

                val payment = transaction {
                    NicePaymentsTable.selectAll()
                        .where { NicePaymentsTable.tid eq tid }
                        .firstOrNull()
                }

                if (payment == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Payment not found"))
                } else {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "orderId" to payment[NicePaymentsTable.orderId],
                        "tid" to (payment[NicePaymentsTable.tid] ?: ""),
                        "status" to payment[NicePaymentsTable.status],
                        "amount" to payment[NicePaymentsTable.amount].toString(),
                        "goodsName" to payment[NicePaymentsTable.goodsName],
                        "paidAt" to (payment[NicePaymentsTable.paidAt]?.toString() ?: ""),
                        "resultCode" to (payment[NicePaymentsTable.resultCode] ?: ""),
                        "resultMsg" to (payment[NicePaymentsTable.resultMsg] ?: "")
                    ))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Query failed"))
            }
        }

        // POST /webhook - NICE 웹훅 수신
        post("/webhook") {
            try {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject

                val tid = json["tid"]?.jsonPrimitive?.contentOrNull ?: ""
                val orderId = json["orderId"]?.jsonPrimitive?.contentOrNull ?: ""
                val amount = json["amount"]?.jsonPrimitive?.intOrNull ?: 0
                val ediDate = json["ediDate"]?.jsonPrimitive?.contentOrNull ?: ""
                val signature = json["signature"]?.jsonPrimitive?.contentOrNull ?: ""
                val status = json["status"]?.jsonPrimitive?.contentOrNull ?: ""

                // 서명 검증
                if (!nicePayService.verifyResponseSignature(tid, amount, ediDate, signature)) {
                    call.respond(HttpStatusCode.BadRequest, "SIGNATURE_INVALID")
                    return@post
                }

                // 결제 상태 업데이트
                transaction {
                    NicePaymentsTable.update({ NicePaymentsTable.tid eq tid }) {
                        it[NicePaymentsTable.status] = status
                        it[updatedAt] = System.currentTimeMillis()
                        if (status == "paid") it[paidAt] = System.currentTimeMillis()
                        if (status == "cancelled") it[cancelledAt] = System.currentTimeMillis()
                    }
                }

                call.respondText("OK")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Webhook error")
            }
        }
    }
}
