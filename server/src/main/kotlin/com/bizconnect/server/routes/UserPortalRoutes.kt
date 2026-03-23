package com.bizconnect.server.routes

import com.bizconnect.server.database.*
import com.bizconnect.server.services.AdMessageDetector
import com.bizconnect.server.services.FcmService
import com.bizconnect.server.services.WideshotSmsService
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.server.request.receiveText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
// AWS S3 - 향후 전환 예정 (현재 로컬 볼륨 저장)
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.ceil

private val fcmService = FcmService()
private val adDetector = AdMessageDetector()

// S3 향후 전환 예정
private val S3_BUCKET = System.getenv("S3_BUCKET") ?: "bizconnect-uploads"

fun Route.userPortalRoutes(smsService: WideshotSmsService) {
    // ===== FCM 토큰 업데이트 (앱에서 호출) =====
    route("/api/fcm") {
        put("/token") {
            val userId = getUserId(call) ?: return@put
            try {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val fcmToken = json["token"]?.jsonPrimitive?.contentOrNull ?: ""
                if (fcmToken.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "token required"))
                    return@put
                }
                transaction {
                    UsersTable.update({ UsersTable.id eq userId }) {
                        it[UsersTable.fcmToken] = fcmToken
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("message" to "FCM token updated"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed")))
            }
        }
    }

    route("/api/user") {

        // ===== 내 정보 =====
        get("/me") {
            val userId = getUserId(call) ?: return@get
            try {
                val result = transaction {
                    val user = UsersTable.selectAll().where { UsersTable.id eq userId }.firstOrNull()
                        ?: return@transaction null
                    val tier = SubscriptionsTable.selectAll()
                        .where { (SubscriptionsTable.userId eq userId) and (SubscriptionsTable.isActive eq true) }
                        .firstOrNull()?.get(SubscriptionsTable.tier) ?: "free"
                    val balance = CreditBalancesTable.selectAll()
                        .where { CreditBalancesTable.userId eq userId }
                        .firstOrNull()?.get(CreditBalancesTable.balance) ?: 0.0
                    val contactCount = UserContactsTable.selectAll()
                        .where { UserContactsTable.userId eq userId }.count()
                    mapOf(
                        "id" to user[UsersTable.id], "name" to user[UsersTable.name],
                        "phone" to user[UsersTable.phone], "email" to user[UsersTable.email],
                        "role" to user[UsersTable.role],
                        "tier" to tier, "balance" to balance.toString(),
                        "todayUsage" to "0", "contactCount" to contactCount.toString()
                    )
                }
                if (result != null) call.respond(HttpStatusCode.OK, result)
                else call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Error")))
            }
        }

        // ===== FCM 토큰 업데이트 =====
        post("/fcm-token") {
            val userId = getUserId(call) ?: return@post
            try {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val fcmToken = json["token"]?.jsonPrimitive?.contentOrNull ?: ""
                if (fcmToken.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "token required"))
                    return@post
                }
                transaction {
                    UsersTable.update({ UsersTable.id eq userId }) {
                        it[UsersTable.fcmToken] = fcmToken
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("message" to "FCM token updated"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed")))
            }
        }

        // ===== 폰 발송 (무료, 순차) =====
        post("/sms/phone-send") {
            val userId = getUserId(call) ?: return@post
            try {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val recipients = json["recipients"]?.jsonArray?.map {
                    it.jsonObject.let { r ->
                        Pair(r["phone"]?.jsonPrimitive?.content ?: "", r["name"]?.jsonPrimitive?.contentOrNull)
                    }
                } ?: emptyList()
                val message = json["message"]?.jsonPrimitive?.contentOrNull ?: ""
                val isAdExplicit = json["isAd"]?.jsonPrimitive?.booleanOrNull ?: false

                if (recipients.isEmpty() || message.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "recipients and message required"))
                    return@post
                }

                // 광고 감지
                val adResult = adDetector.detect(message)
                if (adResult.isAd || isAdExplicit) {
                    call.respond(HttpStatusCode.Forbidden, mapOf(
                        "error" to "광고성 문자는 폰 발송이 불가합니다. 유료 발송을 이용해주세요.",
                        "isAd" to "true",
                        "reasons" to adResult.reasons.joinToString(", ")
                    ))
                    return@post
                }

                // 야간 체크
                if (adDetector.isNightTime() && recipients.size > 10) {
                    call.respond(HttpStatusCode.Forbidden, mapOf(
                        "error" to "야간(21시~08시)에는 10건 이상 발송이 제한됩니다."
                    ))
                    return@post
                }

                // FCM 토큰 확인
                val fcmToken = transaction {
                    UsersTable.selectAll().where { UsersTable.id eq userId }
                        .firstOrNull()?.get(UsersTable.fcmToken)
                }
                if (fcmToken.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to "앱이 연결되지 않았습니다. BizConnect 앱을 실행해 주세요."
                    ))
                    return@post
                }

                // ETA 계산 (20건/분)
                val etaMinutes = if (recipients.size <= 10) 0 else ceil(recipients.size / 20.0).toInt()
                val jobId = UUID.randomUUID().toString()

                // 변수 치환 + 대기 저장
                transaction {
                    SmsSendJobsTable.insert {
                        it[id] = jobId
                        it[SmsSendJobsTable.userId] = userId
                        it[totalCount] = recipients.size
                        it[status] = "queued"
                        it[sendMethod] = "phone"
                        it[isAdMessage] = false
                        it[messageTemplate] = message
                        it[SmsSendJobsTable.etaMinutes] = etaMinutes
                        it[createdAt] = System.currentTimeMillis()
                        it[updatedAt] = System.currentTimeMillis()
                    }

                    for ((phone, name) in recipients) {
                        val resolvedMsg = substituteVariables(message, mapOf(
                            "이름" to (name ?: ""), "전화번호" to phone
                        ), userId, phone)

                        PendingSmsTable.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[PendingSmsTable.userId] = userId
                            it[PendingSmsTable.jobId] = jobId
                            it[recipientPhone] = phone.replace("-", "")
                            it[recipientName] = name
                            it[messageContent] = resolvedMsg
                            it[sendMethod] = "phone"
                            it[status] = "pending"
                            it[createdAt] = System.currentTimeMillis()
                        }
                    }
                }

                // FCM 푸시
                fcmService.notifySmsBatch(fcmToken, jobId, recipients.size)

                val etaText = if (etaMinutes == 0) "즉시 발송" else "약 ${etaMinutes}분 소요"
                call.respond(HttpStatusCode.OK, mapOf(
                    "jobId" to jobId,
                    "message" to "${recipients.size}건 발송 요청 ($etaText)",
                    "totalCount" to recipients.size.toString(),
                    "etaMinutes" to etaMinutes.toString()
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Send failed")))
            }
        }

        // ===== 유료 웹 발송 (즉시) =====
        post("/sms/paid-send") {
            val userId = getUserId(call) ?: return@post
            try {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val phones = json["phones"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val message = json["message"]?.jsonPrimitive?.contentOrNull ?: ""
                val isAd = json["isAd"]?.jsonPrimitive?.booleanOrNull ?: false

                if (phones.isEmpty() || message.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "phones and message required"))
                    return@post
                }

                // 광고 문자 자동 포맷
                val finalMessage = if (isAd) adDetector.formatAdMessage(message) else message

                // 잔액 확인
                val costInfo = smsService.calculateCost(finalMessage)
                val totalCost = costInfo.cost * phones.size
                val balance = transaction {
                    CreditBalancesTable.selectAll()
                        .where { CreditBalancesTable.userId eq userId }
                        .firstOrNull()?.get(CreditBalancesTable.balance) ?: 0.0
                }

                if (balance < totalCost) {
                    call.respond(HttpStatusCode.PaymentRequired, mapOf(
                        "error" to "잔액 부족 (필요: ${totalCost.toInt()}원, 잔액: ${balance.toInt()}원)"
                    ))
                    return@post
                }

                val result = smsService.sendBatch(phones, finalMessage, null)

                transaction {
                    if (result.totalCost > 0) {
                        CreditBalancesTable.update({ CreditBalancesTable.userId eq userId }) {
                            it[CreditBalancesTable.balance] = balance - result.totalCost
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }

                    // Save detailed SMS logs with error tracking
                    val now = System.currentTimeMillis()
                    for ((i, sr) in result.results.withIndex()) {
                        SmsLogsTable.insert {
                            it[SmsLogsTable.id] = UUID.randomUUID().toString()
                            it[SmsLogsTable.userId] = userId
                            it[recipientPhone] = phones[i]
                            it[messageContent] = finalMessage
                            it[characterCount] = finalMessage.toByteArray(Charsets.UTF_8).size
                            it[status] = if (sr.success) "sent" else "failed"
                            it[errorCode] = sr.errorCode
                            it[errorMessage] = sr.errorMessage
                            it[wideshotSendCode] = sr.sendCode
                            it[sentAt] = now
                            it[requestedAt] = now
                        }
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf(
                    "total" to result.total.toString(),
                    "success" to result.success.toString(),
                    "failed" to result.failed.toString(),
                    "totalCost" to result.totalCost.toString(),
                    "messageType" to costInfo.msgType
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Send failed")))
            }
        }

        // ===== 작업 진행률 =====
        get("/sms/job/{jobId}/progress") {
            val userId = getUserId(call) ?: return@get
            val jobId = call.parameters["jobId"] ?: return@get
            try {
                val result = transaction {
                    val job = SmsSendJobsTable.selectAll()
                        .where { (SmsSendJobsTable.id eq jobId) and (SmsSendJobsTable.userId eq userId) }
                        .firstOrNull() ?: return@transaction null

                    val counts = PendingSmsTable.selectAll()
                        .where { PendingSmsTable.jobId eq jobId }
                        .groupBy { it[PendingSmsTable.status] }
                        .mapValues { it.value.size }

                    val sent = counts["sent"] ?: 0
                    val failed = counts["failed"] ?: 0
                    val pending = counts["pending"] ?: 0
                    val total = job[SmsSendJobsTable.totalCount]
                    val status = if (pending == 0 && total > 0) "completed" else job[SmsSendJobsTable.status]

                    mapOf(
                        "jobId" to jobId, "total" to total.toString(),
                        "sent" to sent.toString(), "failed" to failed.toString(),
                        "pending" to pending.toString(), "status" to status,
                        "etaMinutes" to job[SmsSendJobsTable.etaMinutes].toString(),
                        "progress" to if (total > 0) ((sent + failed) * 100 / total).toString() else "0"
                    )
                }

                if (result != null) call.respond(HttpStatusCode.OK, result)
                else call.respond(HttpStatusCode.NotFound, mapOf("error" to "Job not found"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Query failed"))
            }
        }

        // ===== 대기 문자 조회 (앱 폴링용) =====
        get("/sms/pending") {
            val userId = getUserId(call) ?: return@get
            val jobId = call.request.queryParameters["jobId"]
            try {
                val pending = transaction {
                    val query = if (!jobId.isNullOrBlank()) {
                        PendingSmsTable.selectAll()
                            .where { (PendingSmsTable.jobId eq jobId) and (PendingSmsTable.status eq "pending") }
                    } else {
                        PendingSmsTable.selectAll()
                            .where { (PendingSmsTable.userId eq userId) and (PendingSmsTable.status eq "pending") }
                    }
                    query.orderBy(PendingSmsTable.createdAt, SortOrder.ASC)
                        .limit(50)
                        .map { row ->
                            mapOf(
                                "id" to row[PendingSmsTable.id],
                                "phone" to row[PendingSmsTable.recipientPhone],
                                "name" to (row[PendingSmsTable.recipientName] ?: ""),
                                "message" to row[PendingSmsTable.messageContent],
                                "jobId" to (row[PendingSmsTable.jobId] ?: "")
                            )
                        }
                }
                // JSON 직접 생성 (mixed type 직렬화 문제 방지)
                val jsonResponse = buildJsonObject {
                    putJsonArray("data") {
                        for (p in pending) {
                            addJsonObject {
                                for ((k, v) in p) put(k, v)
                            }
                        }
                    }
                    put("count", pending.size)
                }
                call.respond(HttpStatusCode.OK, jsonResponse)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Query failed")))
            }
        }

        // ===== 발송 결과 업데이트 (앱→서버) =====
        put("/sms/{id}/status") {
            val userId = getUserId(call) ?: return@put
            val smsId = call.parameters["id"] ?: return@put
            try {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val status = json["status"]?.jsonPrimitive?.contentOrNull ?: "sent"
                val errorMsg = json["error"]?.jsonPrimitive?.contentOrNull

                transaction {
                    PendingSmsTable.update({
                        (PendingSmsTable.id eq smsId) and (PendingSmsTable.userId eq userId)
                    }) {
                        it[PendingSmsTable.status] = status
                        it[PendingSmsTable.errorMessage] = errorMsg
                        it[processedAt] = System.currentTimeMillis()
                    }

                    // Job 카운트 업데이트
                    val msg = PendingSmsTable.selectAll().where { PendingSmsTable.id eq smsId }.firstOrNull()
                    val jId = msg?.get(PendingSmsTable.jobId)
                    if (jId != null) {
                        val job = SmsSendJobsTable.selectAll()
                            .where { SmsSendJobsTable.id eq jId }.firstOrNull()
                        if (job != null) {
                            val curSent = job[SmsSendJobsTable.sentCount]
                            val curFailed = job[SmsSendJobsTable.failedCount]
                            SmsSendJobsTable.update({ SmsSendJobsTable.id eq jId }) {
                                if (status == "sent") it[sentCount] = curSent + 1
                                else if (status == "failed") it[failedCount] = curFailed + 1
                                it[updatedAt] = System.currentTimeMillis()
                            }
                        }

                        // 모든 메시지 처리 완료 시 Job 상태 변경
                        val remaining = PendingSmsTable.selectAll().where {
                            (PendingSmsTable.jobId eq jId) and (PendingSmsTable.status eq "pending")
                        }.count()
                        if (remaining == 0L) {
                            SmsSendJobsTable.update({ SmsSendJobsTable.id eq jId }) {
                                it[SmsSendJobsTable.status] = "completed"
                                it[updatedAt] = System.currentTimeMillis()
                            }
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("message" to "Updated"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Update failed")))
            }
        }

        // ===== 광고 감지 체크 =====
        post("/sms/check-ad") {
            try {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val message = json["message"]?.jsonPrimitive?.contentOrNull ?: ""
                val result = adDetector.detect(message)
                call.respond(HttpStatusCode.OK, mapOf(
                    "isAd" to result.isAd.toString(),
                    "reasons" to result.reasons.joinToString(", "),
                    "formattedMessage" to (result.formattedMessage ?: message)
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Check failed"))
            }
        }

        // ===== 발송 이력 =====
        get("/sms/history") {
            val userId = getUserId(call) ?: return@get
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 30
            val offset = ((page - 1) * limit).toLong()
            try {
                val result = transaction {
                    // PendingSms (phone-based sends)
                    val pendingItems = PendingSmsTable.selectAll()
                        .where { PendingSmsTable.userId eq userId }
                        .orderBy(PendingSmsTable.createdAt, SortOrder.DESC)
                        .limit(limit, offset)
                        .map { row ->
                            mapOf(
                                "id" to row[PendingSmsTable.id],
                                "phone" to row[PendingSmsTable.recipientPhone],
                                "name" to (row[PendingSmsTable.recipientName] ?: ""),
                                "message" to row[PendingSmsTable.messageContent].take(50),
                                "method" to row[PendingSmsTable.sendMethod],
                                "status" to row[PendingSmsTable.status],
                                "jobId" to (row[PendingSmsTable.jobId] ?: ""),
                                "errorCode" to (row[PendingSmsTable.errorMessage] ?: ""),
                                "errorMessage" to (row[PendingSmsTable.errorMessage] ?: ""),
                                "createdAt" to row[PendingSmsTable.createdAt].toString()
                            )
                        }

                    // SmsLogs (paid API sends) - also include with error details
                    val smsLogItems = SmsLogsTable.selectAll()
                        .where { SmsLogsTable.userId eq userId }
                        .orderBy(SmsLogsTable.sentAt, SortOrder.DESC)
                        .limit(limit, offset)
                        .map { row ->
                            mapOf(
                                "id" to row[SmsLogsTable.id],
                                "phone" to row[SmsLogsTable.recipientPhone],
                                "name" to "",
                                "message" to row[SmsLogsTable.messageContent].take(50),
                                "method" to "paid",
                                "status" to row[SmsLogsTable.status],
                                "jobId" to "",
                                "errorCode" to (row[SmsLogsTable.errorCode] ?: ""),
                                "errorMessage" to (row[SmsLogsTable.errorMessage] ?: ""),
                                "createdAt" to row[SmsLogsTable.sentAt].toString()
                            )
                        }

                    // Combine and sort by createdAt descending, then paginate
                    val allItems = (pendingItems + smsLogItems)
                        .sortedByDescending { it["createdAt"]?.toLongOrNull() ?: 0L }
                        .take(limit)

                    val totalPending = PendingSmsTable.selectAll()
                        .where { PendingSmsTable.userId eq userId }.count().toInt()
                    val totalLogs = SmsLogsTable.selectAll()
                        .where { SmsLogsTable.userId eq userId }.count().toInt()
                    val total = totalPending + totalLogs

                    buildJsonObject {
                        putJsonArray("data") { for (i in allItems) addJsonObject { for ((k, v) in i) put(k, v) } }
                        put("total", total); put("page", page)
                    }
                }
                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Query failed")))
            }
        }

        // ===== 수신거부 번호 확인 =====
        post("/sms/check-blocked") {
            val userId = getUserId(call) ?: return@post
            try {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val phones = json["phones"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

                // For now, return empty blocked list (future: check against blocked_numbers table)
                val blocked = emptyList<String>()
                val reasons = emptyMap<String, String>()

                call.respond(HttpStatusCode.OK, mapOf(
                    "blocked" to blocked.joinToString(","),
                    "reasons" to reasons.entries.joinToString(",") { "${it.key}:${it.value}" },
                    "total" to phones.size.toString(),
                    "blockedCount" to blocked.size.toString()
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Check failed")))
            }
        }

        // ===== AI 메시지 생성 =====
        post("/ai/generate") {
            val userId = getUserId(call) ?: return@post
            try {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val prompt = json["prompt"]?.jsonPrimitive?.contentOrNull ?: ""
                val tone = json["tone"]?.jsonPrimitive?.contentOrNull ?: "정중"

                if (prompt.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "prompt required"))
                    return@post
                }

                val apiKey = System.getenv("DEEPSEEK_API_KEY") ?: ""
                if (apiKey.isBlank()) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "AI API key not configured"))
                    return@post
                }

                val systemPrompt = "당신은 한국어 비즈니스 문자 메시지 작성 도우미입니다. $tone 한 톤으로 간결하게 작성하세요. SMS는 90바이트(한글 45자) 이내, LMS는 2000바이트 이내입니다."

                val requestBody = buildJsonObject {
                    put("model", "deepseek-chat")
                    putJsonArray("messages") {
                        addJsonObject { put("role", "system"); put("content", systemPrompt) }
                        addJsonObject { put("role", "user"); put("content", prompt) }
                    }
                    put("max_tokens", 500)
                    put("temperature", 0.7)
                }

                val conn = java.net.URL("https://api.deepseek.com/chat/completions").openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.doOutput = true
                conn.outputStream.write(requestBody.toString().toByteArray())

                if (conn.responseCode == 200) {
                    val resp = Json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
                    val content = resp["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
                    conn.disconnect()
                    call.respond(HttpStatusCode.OK, mapOf("content" to content))
                } else {
                    conn.disconnect()
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "AI generation failed"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "AI error")))
            }
        }

        // ===== 이미지 업로드 (Base64) =====
        post("/upload/image") {
            val userId = getUserId(call) ?: return@post
            try {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val base64Data = json["data"]?.jsonPrimitive?.contentOrNull ?: ""
                val originalName = json["fileName"]?.jsonPrimitive?.contentOrNull ?: "image.jpg"

                if (base64Data.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "data required"))
                    return@post
                }

                val ext = originalName.substringAfterLast(".", "jpg")
                val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
                val shortCode = (1..6).map { chars.random() }.joinToString("")
                val fileName = "$shortCode.$ext"

                // Base64 디코딩
                val cleanBase64 = base64Data.substringAfter(",") // data:image/png;base64, 제거
                val fileBytes = java.util.Base64.getDecoder().decode(cleanBase64)

                // 이미지 저장 (Docker 볼륨 - 영구 저장)
                val uploadDir = java.io.File("/app/uploads")
                uploadDir.mkdirs()
                java.io.File(uploadDir, fileName).writeBytes(fileBytes)

                val publicUrl = "https://sm.on1.kr/uploads/$fileName"
                val previewUrl = "https://sm.on1.kr/i/$fileName"

                call.respond(HttpStatusCode.OK, mapOf(
                    "fileName" to fileName,
                    "publicUrl" to publicUrl,
                    "previewUrl" to previewUrl,
                    "originalName" to originalName
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Upload failed")))
            }
        }

        // ===== 관리자 제공 템플릿 (읽기 전용) =====
        get("/admin-templates") {
            try {
                val result = transaction {
                    AdminTemplateCategoriesTable.selectAll()
                        .orderBy(AdminTemplateCategoriesTable.sortOrder, SortOrder.ASC)
                        .map { cat ->
                            val catId = cat[AdminTemplateCategoriesTable.id]
                            val templates = AdminTemplatesTable.selectAll()
                                .where { AdminTemplatesTable.categoryId eq catId }
                                .orderBy(AdminTemplatesTable.sortOrder, SortOrder.ASC)
                                .map { tpl ->
                                    buildJsonObject {
                                        put("id", tpl[AdminTemplatesTable.id])
                                        put("title", tpl[AdminTemplatesTable.title])
                                        put("content", tpl[AdminTemplatesTable.content])
                                    }
                                }
                            buildJsonObject {
                                put("id", catId)
                                put("name", cat[AdminTemplateCategoriesTable.name])
                                put("icon", cat[AdminTemplateCategoriesTable.icon])
                                putJsonArray("templates") { for (t in templates) add(t) }
                            }
                        }
                }
                val response = buildJsonObject {
                    putJsonArray("data") { for (r in result) add(r) }
                }
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed")))
            }
        }

        // ===== 카테고리 CRUD =====
        get("/categories") {
            val userId = getUserId(call) ?: return@get
            val cats = transaction {
                UserCategoriesTable.selectAll()
                    .where { UserCategoriesTable.userId eq userId }
                    .orderBy(UserCategoriesTable.sortOrder, SortOrder.ASC)
                    .map { mapOf("id" to it[UserCategoriesTable.id], "name" to it[UserCategoriesTable.name], "color" to it[UserCategoriesTable.color]) }
            }
            call.respond(HttpStatusCode.OK, mapOf("data" to cats))
        }

        post("/categories") {
            val userId = getUserId(call) ?: return@post
            val body = call.receiveText()
            val json = Json.parseToJsonElement(body).jsonObject
            val name = json["name"]?.jsonPrimitive?.contentOrNull ?: ""
            val color = json["color"]?.jsonPrimitive?.contentOrNull ?: "#6366f1"
            if (name.isBlank()) { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "name required")); return@post }
            val id = UUID.randomUUID().toString()
            transaction {
                UserCategoriesTable.insert {
                    it[UserCategoriesTable.id] = id; it[UserCategoriesTable.userId] = userId
                    it[UserCategoriesTable.name] = name; it[UserCategoriesTable.color] = color
                    it[createdAt] = System.currentTimeMillis()
                }
            }
            call.respond(HttpStatusCode.Created, mapOf("id" to id))
        }

        delete("/categories/{id}") {
            val userId = getUserId(call) ?: return@delete
            val catId = call.parameters["id"] ?: return@delete
            transaction {
                UserCategoriesTable.deleteWhere {
                    (UserCategoriesTable.id eq catId) and (UserCategoriesTable.userId eq userId)
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("message" to "삭제됨"))
        }

        // ===== 템플릿 CRUD =====
        get("/templates") {
            val userId = getUserId(call) ?: return@get
            val templates = transaction {
                MessageTemplatesTable.selectAll()
                    .where { MessageTemplatesTable.userId eq userId }
                    .orderBy(MessageTemplatesTable.updatedAt, SortOrder.DESC)
                    .map { mapOf("id" to it[MessageTemplatesTable.id], "title" to it[MessageTemplatesTable.title], "content" to it[MessageTemplatesTable.content], "category" to (it[MessageTemplatesTable.category] ?: ""), "isFromPhone" to it[MessageTemplatesTable.isFromPhone].toString()) }
            }
            call.respond(HttpStatusCode.OK, mapOf("data" to templates))
        }

        post("/templates") {
            val userId = getUserId(call) ?: return@post
            val body = call.receiveText()
            val json = Json.parseToJsonElement(body).jsonObject
            val title = json["title"]?.jsonPrimitive?.contentOrNull ?: ""
            val content = json["content"]?.jsonPrimitive?.contentOrNull ?: ""
            if (title.isBlank() || content.isBlank()) { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "title and content required")); return@post }
            val id = UUID.randomUUID().toString()
            transaction {
                MessageTemplatesTable.insert {
                    it[MessageTemplatesTable.id] = id; it[MessageTemplatesTable.userId] = userId
                    it[MessageTemplatesTable.title] = title; it[MessageTemplatesTable.content] = content
                    it[category] = json["category"]?.jsonPrimitive?.contentOrNull
                    it[isFromPhone] = false
                    it[createdAt] = System.currentTimeMillis(); it[updatedAt] = System.currentTimeMillis()
                }
            }
            call.respond(HttpStatusCode.Created, mapOf("id" to id))
        }

        delete("/templates/{id}") {
            val userId = getUserId(call) ?: return@delete
            val tId = call.parameters["id"] ?: return@delete
            transaction {
                MessageTemplatesTable.deleteWhere {
                    (MessageTemplatesTable.id eq tId) and (MessageTemplatesTable.userId eq userId)
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("message" to "삭제됨"))
        }

        // ===== SMS 이력 동기화 (앱→서버) =====
        post("/sms-history/sync") {
            val userId = getUserId(call) ?: return@post
            try {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val messages = json["messages"]?.jsonArray ?: JsonArray(emptyList())

                if (messages.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "messages required"))
                    return@post
                }

                var synced = 0
                transaction {
                    for (msg in messages) {
                        val obj = msg.jsonObject
                        val recipientPhone = obj["recipientPhone"]?.jsonPrimitive?.contentOrNull ?: continue
                        val timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: continue
                        val threadId = obj["threadId"]?.jsonPrimitive?.longOrNull ?: 0L

                        // 중복 체크 (userId + recipientPhone + timestamp)
                        val exists = UserSmsHistoryTable.selectAll().where {
                            (UserSmsHistoryTable.userId eq userId) and
                            (UserSmsHistoryTable.recipientPhone eq recipientPhone.replace("-", "")) and
                            (UserSmsHistoryTable.timestamp eq timestamp)
                        }.count() > 0

                        if (!exists) {
                            UserSmsHistoryTable.insert {
                                it[id] = UUID.randomUUID().toString()
                                it[UserSmsHistoryTable.userId] = userId
                                it[UserSmsHistoryTable.threadId] = threadId
                                it[UserSmsHistoryTable.recipientPhone] = recipientPhone.replace("-", "")
                                it[recipientName] = obj["recipientName"]?.jsonPrimitive?.contentOrNull
                                it[UserSmsHistoryTable.body] = obj["body"]?.jsonPrimitive?.contentOrNull
                                it[UserSmsHistoryTable.timestamp] = timestamp
                                it[type] = obj["type"]?.jsonPrimitive?.contentOrNull ?: "sent"
                                it[isMms] = obj["isMms"]?.jsonPrimitive?.booleanOrNull ?: false
                                it[createdAt] = System.currentTimeMillis()
                            }
                            synced++
                        }
                    }

                    // 연락처별 통계 업데이트
                    val phoneGroups = messages.groupBy {
                        it.jsonObject["recipientPhone"]?.jsonPrimitive?.contentOrNull?.replace("-", "") ?: ""
                    }.filterKeys { it.isNotBlank() }

                    for ((phone, msgs) in phoneGroups) {
                        val latestTimestamp = msgs.mapNotNull {
                            it.jsonObject["timestamp"]?.jsonPrimitive?.longOrNull
                        }.maxOrNull() ?: continue

                        val totalCount = UserSmsHistoryTable.selectAll().where {
                            (UserSmsHistoryTable.userId eq userId) and
                            (UserSmsHistoryTable.recipientPhone eq phone)
                        }.count().toInt()

                        UserContactsTable.update({
                            (UserContactsTable.userId eq userId) and (UserContactsTable.phone eq phone)
                        }) {
                            it[lastContactDate] = latestTimestamp
                            it[totalMessageCount] = totalCount
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("synced" to synced.toString()))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Sync failed")))
            }
        }

        // ===== 연락처 상세 (메시지 이력 포함) =====
        get("/contacts/{id}/detail") {
            val userId = getUserId(call) ?: return@get
            val contactId = call.parameters["id"] ?: return@get
            try {
                val result = transaction {
                    val contact = UserContactsTable.selectAll().where {
                        (UserContactsTable.id eq contactId) and (UserContactsTable.userId eq userId)
                    }.firstOrNull() ?: return@transaction null

                    val phone = contact[UserContactsTable.phone]

                    val recentMessages = UserSmsHistoryTable.selectAll().where {
                        (UserSmsHistoryTable.userId eq userId) and
                        (UserSmsHistoryTable.recipientPhone eq phone)
                    }.orderBy(UserSmsHistoryTable.timestamp, SortOrder.DESC)
                        .limit(20)
                        .map { row ->
                            buildJsonObject {
                                put("id", row[UserSmsHistoryTable.id])
                                put("threadId", row[UserSmsHistoryTable.threadId])
                                put("body", row[UserSmsHistoryTable.body] ?: "")
                                put("timestamp", row[UserSmsHistoryTable.timestamp])
                                put("type", row[UserSmsHistoryTable.type])
                                put("isMms", row[UserSmsHistoryTable.isMms])
                            }
                        }

                    val messageCount = UserSmsHistoryTable.selectAll().where {
                        (UserSmsHistoryTable.userId eq userId) and
                        (UserSmsHistoryTable.recipientPhone eq phone)
                    }.count().toInt()

                    val sentCount = UserSmsHistoryTable.selectAll().where {
                        (UserSmsHistoryTable.userId eq userId) and
                        (UserSmsHistoryTable.recipientPhone eq phone) and
                        (UserSmsHistoryTable.type eq "sent")
                    }.count().toInt()

                    val receivedCount = messageCount - sentCount

                    buildJsonObject {
                        put("id", contact[UserContactsTable.id])
                        put("name", contact[UserContactsTable.name])
                        put("phone", contact[UserContactsTable.phone])
                        put("email", contact[UserContactsTable.email] ?: "")
                        put("company", contact[UserContactsTable.company] ?: "")
                        put("memo", contact[UserContactsTable.memo] ?: "")
                        put("category", contact[UserContactsTable.category] ?: "")
                        put("birthday", contact[UserContactsTable.birthday] ?: "")
                        put("anniversary", contact[UserContactsTable.anniversary] ?: "")
                        put("tags", contact[UserContactsTable.tags] ?: "")
                        put("notes", contact[UserContactsTable.notes] ?: "")
                        put("lastContactDate", contact[UserContactsTable.lastContactDate] ?: 0L)
                        put("totalMessageCount", contact[UserContactsTable.totalMessageCount])
                        putJsonObject("stats") {
                            put("totalMessages", messageCount)
                            put("sentMessages", sentCount)
                            put("receivedMessages", receivedCount)
                        }
                        putJsonArray("recentMessages") {
                            for (msg in recentMessages) add(msg)
                        }
                    }
                }

                if (result != null) call.respond(HttpStatusCode.OK, result)
                else call.respond(HttpStatusCode.NotFound, mapOf("error" to "Contact not found"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Query failed")))
            }
        }

        // ===== AI 컨텍스트 기반 메시지 생성 =====
        post("/ai/generate-contextual") {
            val userId = getUserId(call) ?: return@post
            try {
                val body = call.receiveText()
                val json = Json.parseToJsonElement(body).jsonObject
                val recipientPhone = json["recipientPhone"]?.jsonPrimitive?.contentOrNull ?: ""
                val prompt = json["prompt"]?.jsonPrimitive?.contentOrNull ?: ""
                val tone = json["tone"]?.jsonPrimitive?.contentOrNull ?: "정중"

                if (recipientPhone.isBlank() || prompt.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "recipientPhone and prompt required"))
                    return@post
                }

                val apiKey = System.getenv("DEEPSEEK_API_KEY") ?: ""
                if (apiKey.isBlank()) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "AI API key not configured"))
                    return@post
                }

                val normalizedPhone = recipientPhone.replace("-", "")

                // 연락처 정보 조회
                val contactInfo = transaction {
                    UserContactsTable.selectAll().where {
                        (UserContactsTable.userId eq userId) and (UserContactsTable.phone eq normalizedPhone)
                    }.firstOrNull()
                }

                val contactName = contactInfo?.get(UserContactsTable.name) ?: ""
                val contactCompany = contactInfo?.get(UserContactsTable.company) ?: ""
                val contactMemo = contactInfo?.get(UserContactsTable.memo) ?: ""
                val contactNotes = contactInfo?.get(UserContactsTable.notes) ?: ""

                // 최근 20개 메시지 조회
                val recentMessages = transaction {
                    UserSmsHistoryTable.selectAll().where {
                        (UserSmsHistoryTable.userId eq userId) and
                        (UserSmsHistoryTable.recipientPhone eq normalizedPhone)
                    }.orderBy(UserSmsHistoryTable.timestamp, SortOrder.DESC)
                        .limit(20)
                        .map { row ->
                            val msgType = if (row[UserSmsHistoryTable.type] == "sent") "나" else contactName.ifBlank { "상대방" }
                            "$msgType: ${row[UserSmsHistoryTable.body] ?: ""}"
                        }
                        .reversed() // 시간순 정렬
                }

                val messageCount = transaction {
                    UserSmsHistoryTable.selectAll().where {
                        (UserSmsHistoryTable.userId eq userId) and
                        (UserSmsHistoryTable.recipientPhone eq normalizedPhone)
                    }.count().toInt()
                }

                // 풍부한 컨텍스트 프롬프트 구성
                val contextParts = mutableListOf<String>()
                if (contactName.isNotBlank()) contextParts.add("상대방 이름: $contactName")
                if (contactCompany.isNotBlank()) contextParts.add("회사: $contactCompany")
                if (contactMemo.isNotBlank()) contextParts.add("메모: $contactMemo")
                if (contactNotes.isNotBlank()) contextParts.add("노트: $contactNotes")
                if (recentMessages.isNotEmpty()) {
                    contextParts.add("최근 대화 이력:\n${recentMessages.joinToString("\n")}")
                }

                val contextStr = if (contextParts.isNotEmpty()) {
                    "\n\n[컨텍스트 정보]\n${contextParts.joinToString("\n")}"
                } else ""

                val systemPrompt = "당신은 한국어 비즈니스 문자 메시지 작성 도우미입니다. " +
                    "$tone 한 톤으로 간결하게 작성하세요. " +
                    "SMS는 90바이트(한글 45자) 이내, LMS는 2000바이트 이내입니다. " +
                    "이전 대화 맥락을 고려하여 자연스럽고 적절한 메시지를 생성하세요.$contextStr"

                val requestBody = buildJsonObject {
                    put("model", "deepseek-chat")
                    putJsonArray("messages") {
                        addJsonObject { put("role", "system"); put("content", systemPrompt) }
                        addJsonObject { put("role", "user"); put("content", prompt) }
                    }
                    put("max_tokens", 500)
                    put("temperature", 0.7)
                }

                val conn = java.net.URL("https://api.deepseek.com/chat/completions").openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.doOutput = true
                conn.outputStream.write(requestBody.toString().toByteArray())

                if (conn.responseCode == 200) {
                    val resp = Json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
                    val content = resp["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
                    conn.disconnect()

                    val response = buildJsonObject {
                        put("content", content)
                        putJsonObject("context") {
                            put("contactName", contactName)
                            put("messageCount", messageCount)
                        }
                    }
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    val errorBody = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                    conn.disconnect()
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "AI generation failed: $errorBody"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "AI error")))
            }
        }

        // ===== 연락처 (기존 유지 + 개선) =====
        get("/contacts") {
            val userId = getUserId(call) ?: return@get
            val search = call.request.queryParameters["search"] ?: ""
            val category = call.request.queryParameters["category"]
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val offset = ((page - 1) * limit).toLong()
            try {
                val result = transaction {
                    val baseCondition = UserContactsTable.userId eq userId
                    val searchCondition = if (search.isNotBlank()) {
                        baseCondition and ((UserContactsTable.name like "%$search%") or (UserContactsTable.phone like "%$search%"))
                    } else baseCondition
                    val finalCondition = if (category != null) {
                        searchCondition and (UserContactsTable.category eq category)
                    } else searchCondition

                    val total = UserContactsTable.selectAll().where { finalCondition }.count().toInt()
                    val contacts = UserContactsTable.selectAll().where { finalCondition }
                        .orderBy(UserContactsTable.name, SortOrder.ASC)
                        .limit(limit, offset)
                        .map { row ->
                            mapOf(
                                "id" to row[UserContactsTable.id], "name" to row[UserContactsTable.name],
                                "phone" to row[UserContactsTable.phone], "email" to (row[UserContactsTable.email] ?: ""),
                                "company" to (row[UserContactsTable.company] ?: ""),
                                "memo" to (row[UserContactsTable.memo] ?: ""),
                                "category" to (row[UserContactsTable.category] ?: ""),
                                "birthday" to (row[UserContactsTable.birthday] ?: ""),
                                "anniversary" to (row[UserContactsTable.anniversary] ?: "")
                            )
                        }
                    val response = buildJsonObject {
                        putJsonArray("data") {
                            for (c in contacts) addJsonObject { for ((k, v) in c) put(k, v) }
                        }
                        put("total", total)
                        put("page", page)
                    }
                    response
                }
                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Query failed")))
            }
        }

        post("/contacts") {
            val userId = getUserId(call) ?: return@post
            val body = call.receiveText()
            val json = Json.parseToJsonElement(body).jsonObject
            val name = json["name"]?.jsonPrimitive?.contentOrNull ?: ""
            val phone = json["phone"]?.jsonPrimitive?.contentOrNull ?: ""
            if (name.isBlank() || phone.isBlank()) { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "name and phone required")); return@post }
            val id = UUID.randomUUID().toString()
            transaction {
                UserContactsTable.insert {
                    it[UserContactsTable.id] = id; it[UserContactsTable.userId] = userId
                    it[UserContactsTable.name] = name; it[UserContactsTable.phone] = phone.replace("-", "")
                    it[email] = json["email"]?.jsonPrimitive?.contentOrNull
                    it[company] = json["company"]?.jsonPrimitive?.contentOrNull
                    it[memo] = json["memo"]?.jsonPrimitive?.contentOrNull
                    it[category] = json["category"]?.jsonPrimitive?.contentOrNull
                    it[birthday] = json["birthday"]?.jsonPrimitive?.contentOrNull
                    it[anniversary] = json["anniversary"]?.jsonPrimitive?.contentOrNull
                    it[createdAt] = System.currentTimeMillis(); it[updatedAt] = System.currentTimeMillis()
                }
            }
            call.respond(HttpStatusCode.Created, mapOf("id" to id))
        }

        post("/contacts/import") {
            val userId = getUserId(call) ?: return@post
            val body = call.receiveText()
            val json = Json.parseToJsonElement(body).jsonObject
            val contacts = json["contacts"]?.jsonArray ?: JsonArray(emptyList())
            var imported = 0
            transaction {
                for (c in contacts) {
                    val obj = c.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                    val phone = obj["phone"]?.jsonPrimitive?.contentOrNull ?: continue
                    val normalizedPhone = phone.replace("-", "")
                    val exists = UserContactsTable.selectAll().where {
                        (UserContactsTable.userId eq userId) and (UserContactsTable.phone eq normalizedPhone)
                    }.count() > 0
                    if (!exists) {
                        UserContactsTable.insert {
                            it[UserContactsTable.id] = UUID.randomUUID().toString(); it[UserContactsTable.userId] = userId
                            it[UserContactsTable.name] = name; it[UserContactsTable.phone] = normalizedPhone
                            it[email] = obj["email"]?.jsonPrimitive?.contentOrNull
                            it[company] = obj["company"]?.jsonPrimitive?.contentOrNull
                            it[memo] = obj["memo"]?.jsonPrimitive?.contentOrNull
                            it[category] = obj["category"]?.jsonPrimitive?.contentOrNull
                            it[birthday] = obj["birthday"]?.jsonPrimitive?.contentOrNull
                            it[anniversary] = obj["anniversary"]?.jsonPrimitive?.contentOrNull
                            it[createdAt] = System.currentTimeMillis(); it[updatedAt] = System.currentTimeMillis()
                        }
                        imported++
                    }
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("imported" to imported.toString(), "total" to contacts.size.toString()))
        }

        put("/contacts/{id}") {
            val userId = getUserId(call) ?: return@put
            val contactId = call.parameters["id"] ?: return@put
            val body = call.receiveText()
            val json = Json.parseToJsonElement(body).jsonObject
            transaction {
                UserContactsTable.update({
                    (UserContactsTable.id eq contactId) and (UserContactsTable.userId eq userId)
                }) {
                    json["name"]?.jsonPrimitive?.contentOrNull?.let { v -> it[name] = v }
                    json["phone"]?.jsonPrimitive?.contentOrNull?.let { v -> it[phone] = v.replace("-", "") }
                    json["email"]?.jsonPrimitive?.contentOrNull?.let { v -> it[email] = v }
                    json["company"]?.jsonPrimitive?.contentOrNull?.let { v -> it[company] = v }
                    json["memo"]?.jsonPrimitive?.contentOrNull?.let { v -> it[memo] = v }
                    json["category"]?.jsonPrimitive?.contentOrNull?.let { v -> it[category] = v }
                    json["birthday"]?.jsonPrimitive?.contentOrNull?.let { v -> it[birthday] = v }
                    json["anniversary"]?.jsonPrimitive?.contentOrNull?.let { v -> it[anniversary] = v }
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("message" to "수정됨"))
        }

        delete("/contacts/{id}") {
            val userId = getUserId(call) ?: return@delete
            val contactId = call.parameters["id"] ?: return@delete
            transaction {
                UserContactsTable.deleteWhere {
                    (UserContactsTable.id eq contactId) and (UserContactsTable.userId eq userId)
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("message" to "삭제됨"))
        }

        // ===== 신뢰 기기 관리 =====
        get("/devices") {
            val userId = getUserId(call) ?: return@get
            try {
                val devices = transaction {
                    TrustedDevicesTable.selectAll()
                        .where { TrustedDevicesTable.userId eq userId }
                        .orderBy(TrustedDevicesTable.lastUsedAt, SortOrder.DESC)
                        .map { row ->
                            mapOf(
                                "id" to row[TrustedDevicesTable.id],
                                "deviceName" to row[TrustedDevicesTable.deviceName],
                                "platform" to row[TrustedDevicesTable.platform],
                                "lastUsedAt" to row[TrustedDevicesTable.lastUsedAt].toString(),
                                "createdAt" to row[TrustedDevicesTable.createdAt].toString()
                            )
                        }
                }
                val response = buildJsonObject {
                    putJsonArray("data") {
                        for (d in devices) addJsonObject { for ((k, v) in d) put(k, v) }
                    }
                    put("count", devices.size)
                }
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Query failed")))
            }
        }

        delete("/devices/{id}") {
            val userId = getUserId(call) ?: return@delete
            val deviceId = call.parameters["id"] ?: return@delete
            transaction {
                TrustedDevicesTable.deleteWhere {
                    (TrustedDevicesTable.id eq deviceId) and (TrustedDevicesTable.userId eq userId)
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("message" to "기기가 제거되었습니다"))
        }

        // ===== 예약 문자 관리 =====
        get("/sms/scheduled") {
            val userId = getUserId(call) ?: return@get
            try {
                val items = transaction {
                    ScheduledSmsTable.selectAll()
                        .where { (ScheduledSmsTable.userId eq userId) and (ScheduledSmsTable.status eq "scheduled") }
                        .orderBy(ScheduledSmsTable.scheduledAt, SortOrder.ASC)
                        .map { row ->
                            mapOf(
                                "id" to row[ScheduledSmsTable.id],
                                "recipientPhone" to row[ScheduledSmsTable.recipientPhone],
                                "recipientName" to (row[ScheduledSmsTable.recipientName] ?: ""),
                                "messageContent" to row[ScheduledSmsTable.messageContent].take(100),
                                "scheduledAt" to row[ScheduledSmsTable.scheduledAt].toString(),
                                "sendMethod" to row[ScheduledSmsTable.sendMethod],
                                "status" to row[ScheduledSmsTable.status],
                                "createdAt" to row[ScheduledSmsTable.createdAt].toString()
                            )
                        }
                }
                val response = buildJsonObject {
                    putJsonArray("data") {
                        for (i in items) addJsonObject { for ((k, v) in i) put(k, v) }
                    }
                    put("count", items.size)
                }
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Query failed")))
            }
        }

        delete("/sms/scheduled/{id}") {
            val userId = getUserId(call) ?: return@delete
            val schedId = call.parameters["id"] ?: return@delete
            try {
                val updated = transaction {
                    ScheduledSmsTable.update({
                        (ScheduledSmsTable.id eq schedId) and
                        (ScheduledSmsTable.userId eq userId) and
                        (ScheduledSmsTable.status eq "scheduled")
                    }) {
                        it[status] = "cancelled"
                    }
                }
                if (updated > 0) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "예약이 취소되었습니다"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "예약을 찾을 수 없습니다"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Cancel failed")))
            }
        }
    }
}

// ===== Helpers =====

private suspend fun getUserId(call: io.ktor.server.application.ApplicationCall): String? {
    val principal = call.principal<JWTPrincipal>()
    val userId = principal?.payload?.getClaim("userId")?.asString()
    if (userId == null) {
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
    }
    return userId
}

/**
 * 변수 치환: %이름%, %회사%, %전화번호%, %이메일%, %메모%
 */
private fun substituteVariables(
    template: String,
    basicVars: Map<String, String>,
    userId: String,
    phone: String
): String {
    var result = template

    // 기본 변수
    for ((key, value) in basicVars) {
        result = result.replace("%${key}%", value)
    }

    // 연락처에서 추가 변수 조회
    if (result.contains("%")) {
        val contact = transaction {
            UserContactsTable.selectAll().where {
                (UserContactsTable.userId eq userId) and (UserContactsTable.phone eq phone.replace("-", ""))
            }.firstOrNull()
        }

        if (contact != null) {
            result = result
                .replace("%이름%", contact[UserContactsTable.name])
                .replace("%회사%", contact[UserContactsTable.company] ?: "")
                .replace("%이메일%", contact[UserContactsTable.email] ?: "")
                .replace("%메모%", contact[UserContactsTable.memo] ?: "")
                .replace("%전화번호%", contact[UserContactsTable.phone])
        }
    }

    return result
}
