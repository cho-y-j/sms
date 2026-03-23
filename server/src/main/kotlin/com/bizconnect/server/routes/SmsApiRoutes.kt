package com.bizconnect.server.routes

import com.bizconnect.server.database.*
import com.bizconnect.server.services.WideshotSmsService
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.request.receiveText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Wideshot SMS API 라우트
 *
 * POST /api/sms/send      — 단건 발송
 * POST /api/sms/batch      — 대량 발송
 * POST /api/sms/alimtalk   — 알림톡 발송
 * GET  /api/sms/result/{sendCode} — 발송 결과 조회
 * GET  /api/sms/history     — 발송 이력
 * GET  /api/sms/balance     — 잔액 조회
 */
fun Route.smsApiRoutes(smsService: WideshotSmsService) {
    route("/api/sms") {

        // POST /send — 단건 SMS/LMS 발송
        post("/send") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val phone = json["phone"]?.jsonPrimitive?.contentOrNull
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "phone required"))
                val message = json["message"]?.jsonPrimitive?.contentOrNull
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "message required"))
                val callback = json["callback"]?.jsonPrimitive?.contentOrNull
                val subject = json["subject"]?.jsonPrimitive?.contentOrNull

                // 잔액 확인
                val costInfo = smsService.calculateCost(message)
                val balance = transaction {
                    CreditBalancesTable.selectAll()
                        .where { CreditBalancesTable.userId eq userId }
                        .firstOrNull()?.get(CreditBalancesTable.balance) ?: 0.0
                }

                if (balance < costInfo.cost) {
                    call.respond(HttpStatusCode.PaymentRequired, mapOf(
                        "error" to "잔액이 부족합니다",
                        "balance" to balance.toString(),
                        "required" to costInfo.cost.toString()
                    ))
                    return@post
                }

                // 발송
                val result = smsService.sendMessage(phone, message, callback, subject)

                if (result.success) {
                    // 잔액 차감 + 발송 로그
                    transaction {
                        CreditBalancesTable.update({ CreditBalancesTable.userId eq userId }) {
                            it[CreditBalancesTable.balance] = balance - result.cost
                            it[updatedAt] = System.currentTimeMillis()
                        }

                        SmsLogsTable.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[SmsLogsTable.userId] = userId
                            it[recipientPhone] = phone
                            it[messageContent] = message
                            it[characterCount] = message.length
                            it[status] = "sent"
                            it[SmsLogsTable.result] = result.sendCode
                            it[sentAt] = System.currentTimeMillis()
                            it[requestedAt] = System.currentTimeMillis()
                            it[externalId] = result.sendCode
                        }

                        // 일일 사용량 기록
                        recordUsage(userId, result.msgType, result.cost.toDouble())
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to result.success.toString(),
                    "sendCode" to (result.sendCode ?: ""),
                    "msgType" to result.msgType,
                    "cost" to result.cost.toString(),
                    "error" to (result.error ?: "")
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Send failed")))
            }
        }

        // POST /batch — 대량 발송
        post("/batch") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val phones = json["phones"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "phones required"))
                val message = json["message"]?.jsonPrimitive?.contentOrNull
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "message required"))
                val callback = json["callback"]?.jsonPrimitive?.contentOrNull
                val subject = json["subject"]?.jsonPrimitive?.contentOrNull

                // 잔액 확인
                val costInfo = smsService.calculateCost(message)
                val totalEstCost = costInfo.cost * phones.size
                val balance = transaction {
                    CreditBalancesTable.selectAll()
                        .where { CreditBalancesTable.userId eq userId }
                        .firstOrNull()?.get(CreditBalancesTable.balance) ?: 0.0
                }

                if (balance < totalEstCost) {
                    call.respond(HttpStatusCode.PaymentRequired, mapOf(
                        "error" to "잔액이 부족합니다 (필요: ${totalEstCost}원, 잔액: ${balance.toInt()}원)",
                        "balance" to balance.toString(),
                        "required" to totalEstCost.toString()
                    ))
                    return@post
                }

                // 대량 발송
                val result = smsService.sendBatch(phones, message, callback, subject)

                // 잔액 차감 + 로그
                transaction {
                    if (result.totalCost > 0) {
                        CreditBalancesTable.update({ CreditBalancesTable.userId eq userId }) {
                            it[CreditBalancesTable.balance] = balance - result.totalCost
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }

                    for ((index, sendResult) in result.results.withIndex()) {
                        if (sendResult.success) {
                            SmsLogsTable.insert {
                                it[id] = UUID.randomUUID().toString()
                                it[SmsLogsTable.userId] = userId
                                it[recipientPhone] = phones[index]
                                it[messageContent] = message
                                it[characterCount] = message.length
                                it[status] = "sent"
                                it[SmsLogsTable.result] = sendResult.sendCode
                                it[sentAt] = System.currentTimeMillis()
                                it[requestedAt] = System.currentTimeMillis()
                                it[externalId] = sendResult.sendCode
                            }
                        }
                    }

                    recordUsage(userId, result.results.firstOrNull()?.msgType ?: "sms", result.totalCost.toDouble(), result.success)
                }

                call.respond(HttpStatusCode.OK, mapOf(
                    "total" to result.total.toString(),
                    "success" to result.success.toString(),
                    "failed" to result.failed.toString(),
                    "totalCost" to result.totalCost.toString()
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Batch send failed")))
            }
        }

        // POST /alimtalk — 알림톡 발송
        post("/alimtalk") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val phone = json["phone"]?.jsonPrimitive?.contentOrNull
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "phone required"))
                val message = json["message"]?.jsonPrimitive?.contentOrNull
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "message required"))
                val templateCode = json["templateCode"]?.jsonPrimitive?.contentOrNull
                val senderKey = json["senderKey"]?.jsonPrimitive?.contentOrNull
                val fallbackType = json["fallbackType"]?.jsonPrimitive?.contentOrNull ?: "sms"

                val result = smsService.sendAlimtalk(phone, message, templateCode, senderKey, fallbackType)

                if (result.success) {
                    transaction {
                        val balance = CreditBalancesTable.selectAll()
                            .where { CreditBalancesTable.userId eq userId }
                            .firstOrNull()?.get(CreditBalancesTable.balance) ?: 0.0

                        CreditBalancesTable.update({ CreditBalancesTable.userId eq userId }) {
                            it[CreditBalancesTable.balance] = balance - result.cost
                            it[updatedAt] = System.currentTimeMillis()
                        }

                        SmsLogsTable.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[SmsLogsTable.userId] = userId
                            it[recipientPhone] = phone
                            it[messageContent] = "[알림톡] $message"
                            it[characterCount] = message.length
                            it[status] = "sent"
                            it[SmsLogsTable.result] = result.sendCode
                            it[sentAt] = System.currentTimeMillis()
                            it[requestedAt] = System.currentTimeMillis()
                            it[externalId] = result.sendCode
                        }
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to result.success.toString(),
                    "sendCode" to (result.sendCode ?: ""),
                    "cost" to result.cost.toString(),
                    "error" to (result.error ?: "")
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "AlimTalk failed")))
            }
        }

        // GET /result/{sendCode} — 발송 결과 조회
        get("/result/{sendCode}") {
            try {
                val sendCode = call.parameters["sendCode"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing sendCode"))

                val result = smsService.checkResult(sendCode)
                if (result != null) {
                    call.respond(HttpStatusCode.OK, result.toString())
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Result not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Query failed"))
            }
        }

        // GET /history — 발송 이력
        get("/history") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val offset = ((page - 1) * limit).toLong()

                val result = transaction {
                    val total = SmsLogsTable.selectAll()
                        .where { SmsLogsTable.userId eq userId }
                        .count().toInt()

                    val logs = SmsLogsTable.selectAll()
                        .where { SmsLogsTable.userId eq userId }
                        .orderBy(SmsLogsTable.sentAt, SortOrder.DESC)
                        .limit(limit, offset)
                        .map { row ->
                            mapOf(
                                "id" to row[SmsLogsTable.id],
                                "phone" to row[SmsLogsTable.recipientPhone],
                                "message" to row[SmsLogsTable.messageContent].take(100),
                                "status" to row[SmsLogsTable.status],
                                "sendCode" to (row[SmsLogsTable.externalId] ?: ""),
                                "sentAt" to row[SmsLogsTable.sentAt].toString()
                            )
                        }

                    mapOf(
                        "data" to logs,
                        "total" to total,
                        "page" to page,
                        "pageSize" to limit
                    )
                }

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load history"))
            }
        }

        // GET /balance — 잔액 조회
        get("/balance") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val balance = transaction {
                    CreditBalancesTable.selectAll()
                        .where { CreditBalancesTable.userId eq userId }
                        .firstOrNull()?.get(CreditBalancesTable.balance) ?: 0.0
                }

                call.respond(HttpStatusCode.OK, mapOf(
                    "balance" to balance.toString(),
                    "pricing" to mapOf(
                        "sms" to WideshotSmsService.COST_SMS.toString(),
                        "lms" to WideshotSmsService.COST_LMS.toString(),
                        "mms" to WideshotSmsService.COST_MMS.toString(),
                        "alimtalk" to WideshotSmsService.COST_ALIMTALK.toString()
                    )
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load balance"))
            }
        }
    }
}

// 일일 사용량 기록 헬퍼
private fun recordUsage(userId: String, msgType: String, cost: Double, count: Int = 1) {
    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val existing = DailyUsageTable.selectAll()
        .where { (DailyUsageTable.userId eq userId) and (DailyUsageTable.date eq today) }
        .firstOrNull()

    if (existing != null) {
        DailyUsageTable.update({
            (DailyUsageTable.userId eq userId) and (DailyUsageTable.date eq today)
        }) {
            when (msgType) {
                "sms" -> it[smsCount] = existing[smsCount] + count
                "lms" -> it[lmsCount] = existing[lmsCount] + count
                "mms" -> it[mmsCount] = existing[mmsCount] + count
            }
            it[totalCost] = existing[totalCost] + cost
        }
    } else {
        DailyUsageTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[DailyUsageTable.userId] = userId
            it[date] = today
            when (msgType) {
                "sms" -> it[smsCount] = count
                "lms" -> it[lmsCount] = count
                "mms" -> it[mmsCount] = count
            }
            it[totalCost] = cost
        }
    }
}
