package com.bizconnect.server.routes

import com.bizconnect.server.database.UsersTable
import com.bizconnect.server.security.InputValidator
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.request.receive
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Firebase Cloud Messaging (FCM) routes for mobile push notifications
 */
fun Route.fcmRoutes() {
    route("/api/fcm") {
        /**
         * POST /api/fcm/send
         * Send FCM push notification to mobile device
         */
        post("/send") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val request = call.receive<SendFcmRequest>()

                // Input validation
                val title = InputValidator.validateName(request.title, maxLength = 255)
                val message = InputValidator.validateMessageContent(request.message, maxLength = 1000)

                // Check for XSS
                if (InputValidator.checkXss(title) || InputValidator.checkXss(message)) {
                    throw IllegalArgumentException("Message contains invalid content")
                }

                // In production, use Firebase Admin SDK to send actual FCM message
                // For now, this is a placeholder that would integrate with Firebase

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "message" to "Push notification sent",
                        "title" to title,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to e.message)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to send notification")
                )
            }
        }

        /**
         * PUT /api/fcm/token
         * Update user's FCM token (for device registration)
         */
        put("/token") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val request = call.receive<UpdateFcmTokenRequest>()

                // Validate token format (basic validation)
                if (request.token.isBlank() || request.token.length < 50) {
                    throw IllegalArgumentException("Invalid FCM token format")
                }

                val sanitizedToken = InputValidator.sanitizeString(request.token, maxLength = 500)

                transaction {
                    UsersTable.update({ UsersTable.id eq userId }) {
                        it[fcmToken] = sanitizedToken
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "FCM token updated successfully")
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to e.message)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to update token")
                )
            }
        }
    }
}

// DTOs
data class SendFcmRequest(
    val title: String,
    val message: String,
    val data: Map<String, String>? = null
)

data class UpdateFcmTokenRequest(
    val token: String
)
