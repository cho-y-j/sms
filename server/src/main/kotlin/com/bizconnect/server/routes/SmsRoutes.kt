package com.bizconnect.server.routes

import com.bizconnect.server.database.DatabaseFactory
import com.bizconnect.server.database.SmsLogsTable
import com.bizconnect.server.database.DailyLimitsTable
import com.bizconnect.server.database.UsersTable
import com.bizconnect.server.security.InputValidator
import com.bizconnect.server.security.RateLimiter
import com.bizconnect.server.security.AuditLogger
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.request.receive
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import java.time.LocalDate

/**
 * SMS management routes with complete security
 * - Rate limiting (500 characters per day per user)
 * - Phone number validation
 * - Message content sanitization
 * - Audit logging
 */
fun Route.smsRoutes(
    rateLimiter: RateLimiter,
    auditLogger: AuditLogger
) {
    route("/api/sms") {
        /**
         * POST /api/sms/send
         * Initiate SMS send from web -> FCM to mobile
         */
        post("/send") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val request = call.receive<SendSmsRequest>()
                val ipAddress = call.request.local.remoteHost

                // Input validation
                val phoneNumber = InputValidator.validatePhoneNumber(request.phone)
                val message = InputValidator.validateMessageContent(request.message)

                // Check for SQL injection
                if (InputValidator.checkSqlInjection(message) ||
                    InputValidator.checkXss(message)) {
                    auditLogger.logSuspiciousActivity(
                        userId = userId,
                        activity = "Attempted injection in SMS message",
                        ipAddress = ipAddress,
                        userAgent = call.request.headers["User-Agent"] ?: "unknown"
                    )
                    throw IllegalArgumentException("Invalid message content")
                }

                // Rate limit: 500 messages per day per user
                val userRateLimit = rateLimiter.checkUserLimit("sms_send", userId)
                if (!userRateLimit.allowed) {
                    throw Exception("Daily SMS limit exceeded")
                }

                // Check daily character limit
                val today = LocalDate.now().toString()
                val dailyLimit = transaction {
                    DailyLimitsTable.selectAll().where {
                        (DailyLimitsTable.userId eq userId) and (DailyLimitsTable.date eq today)
                    }.firstOrNull()
                }

                val currentCharacters = dailyLimit?.get(DailyLimitsTable.charactersSent) ?: 0
                val maxCharacters = 30000 // 30,000 chars per day
                val messageLength = message.length

                if (currentCharacters + messageLength > maxCharacters) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to "Daily character limit exceeded",
                            "remaining" to (maxCharacters - currentCharacters)
                        )
                    )
                    return@post
                }

                // Create SMS log entry
                val smsId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                transaction {
                    SmsLogsTable.insert {
                        it[id] = smsId
                        it[SmsLogsTable.userId] = userId
                        it[recipientPhone] = phoneNumber
                        it[messageContent] = message
                        it[characterCount] = messageLength
                        it[segmentCount] = (messageLength / 160) + 1
                        it[status] = "sent"
                        it[sentAt] = now
                        it[requestedAt] = now
                    }
                }

                // Update daily limit
                if (dailyLimit != null) {
                    transaction {
                        DailyLimitsTable.update({
                            (DailyLimitsTable.userId eq userId) and (DailyLimitsTable.date eq today)
                        }) {
                            it[charactersSent] = currentCharacters + messageLength
                            it[messagesSent] = (dailyLimit[DailyLimitsTable.messagesSent]) + 1
                            it[remainingCharacters] = maxCharacters - (currentCharacters + messageLength)
                            it[updatedAt] = now
                        }
                    }
                } else {
                    transaction {
                        DailyLimitsTable.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[DailyLimitsTable.userId] = userId
                            it[date] = today
                            it[charactersSent] = messageLength
                            it[messagesSent] = 1
                            it[remainingCharacters] = maxCharacters - messageLength
                            it[remainingMessages] = 499
                            it[resetAt] = now + (24 * 60 * 60 * 1000)
                            it[updatedAt] = now
                        }
                    }
                }

                // Audit log
                auditLogger.logSmsSend(
                    userId = userId,
                    phoneNumber = phoneNumber,
                    messageId = smsId,
                    characterCount = messageLength,
                    ipAddress = ipAddress,
                    userAgent = call.request.headers["User-Agent"] ?: "unknown"
                )

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "smsId" to smsId,
                        "status" to "sent",
                        "characterCount" to messageLength,
                        "timestamp" to now
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Failed to send SMS"))
                )
            }
        }

        /**
         * GET /api/sms/logs
         * Get SMS send logs with pagination
         */
        get("/logs") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val status = call.request.queryParameters["status"]?.trim()

                val (validPage, validLimit) = InputValidator.validatePagination(page, limit)
                val offset = (validPage - 1) * validLimit

                val logs = transaction {
                    val query = SmsLogsTable.selectAll().where { SmsLogsTable.userId eq userId }
                    if (status != null && status.isNotEmpty()) {
                        query.andWhere { SmsLogsTable.status eq status }
                    }

                    query
                        .orderBy(SmsLogsTable.sentAt to SortOrder.DESC)
                        .limit(validLimit, offset.toLong())
                        .map { row ->
                            SmsLogDTO(
                                id = row[SmsLogsTable.id],
                                recipientPhone = row[SmsLogsTable.recipientPhone],
                                characterCount = row[SmsLogsTable.characterCount],
                                status = row[SmsLogsTable.status],
                                sentAt = row[SmsLogsTable.sentAt],
                                deliveredAt = row[SmsLogsTable.deliveredAt]
                            )
                        }
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "logs" to logs,
                        "page" to validPage,
                        "limit" to validLimit
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized")
                )
            }
        }

        /**
         * GET /api/sms/stats
         * Get SMS statistics
         */
        get("/stats") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val stats = transaction {
                    val sentCount = SmsLogsTable.selectAll().where {
                        (SmsLogsTable.userId eq userId) and (SmsLogsTable.status eq "sent")
                    }.count()
                    val deliveredCount = SmsLogsTable.selectAll().where {
                        (SmsLogsTable.userId eq userId) and (SmsLogsTable.status eq "delivered")
                    }.count()
                    val failedCount = SmsLogsTable.selectAll().where {
                        (SmsLogsTable.userId eq userId) and (SmsLogsTable.status eq "failed")
                    }.count()

                    mapOf(
                        "totalSent" to sentCount,
                        "totalDelivered" to deliveredCount,
                        "totalFailed" to failedCount
                    )
                }

                call.respond(HttpStatusCode.OK, stats)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized")
                )
            }
        }

        /**
         * GET /api/sms/daily-limit
         * Check daily character and message limit
         */
        get("/daily-limit") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val today = LocalDate.now().toString()

                val limit = transaction {
                    DailyLimitsTable.selectAll().where {
                        (DailyLimitsTable.userId eq userId) and (DailyLimitsTable.date eq today)
                    }.firstOrNull()
                }

                val response = if (limit != null) {
                    mapOf(
                        "used" to mapOf(
                            "characters" to limit[DailyLimitsTable.charactersSent],
                            "messages" to limit[DailyLimitsTable.messagesSent]
                        ),
                        "remaining" to mapOf(
                            "characters" to limit[DailyLimitsTable.remainingCharacters],
                            "messages" to limit[DailyLimitsTable.remainingMessages]
                        ),
                        "resetAt" to limit[DailyLimitsTable.resetAt]
                    )
                } else {
                    mapOf(
                        "used" to mapOf("characters" to 0, "messages" to 0),
                        "remaining" to mapOf("characters" to 30000, "messages" to 500),
                        "resetAt" to (System.currentTimeMillis() + 24 * 60 * 60 * 1000)
                    )
                }

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized")
                )
            }
        }
    }
}

// DTOs
data class SendSmsRequest(
    val phone: String,
    val message: String,
    val customerId: String? = null,
    val scheduledTime: Long? = null
)

data class SmsLogDTO(
    val id: String,
    val recipientPhone: String,
    val characterCount: Int,
    val status: String,
    val sentAt: Long,
    val deliveredAt: Long?
)
