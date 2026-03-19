package com.bizconnect.server.routes

import com.bizconnect.server.database.UsersTable
import com.bizconnect.server.database.CallbackSettingsTable
import com.bizconnect.server.security.InputValidator
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.request.receive
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Settings management routes
 */
fun Route.settingsRoutes() {
    route("/api/settings") {
        /**
         * GET /api/settings
         * Get user settings
         */
        get("/") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val settings = transaction {
                    UsersTable.selectAll().where { UsersTable.id eq userId }.firstOrNull()?.let { row ->
                        SettingsDTO(
                            userId = userId,
                            email = row[UsersTable.email],
                            name = row[UsersTable.name],
                            phone = row[UsersTable.phone],
                            fcmToken = row[UsersTable.fcmToken],
                            role = row[UsersTable.role],
                            isActive = row[UsersTable.isActive],
                            createdAt = row[UsersTable.createdAt],
                            updatedAt = row[UsersTable.updatedAt]
                        )
                    }
                }

                if (settings == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "User not found")
                    )
                } else {
                    call.respond(HttpStatusCode.OK, settings)
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized")
                )
            }
        }

        /**
         * PUT /api/settings
         * Update user settings
         */
        put("/") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val request = call.receive<UpdateSettingsRequest>()

                transaction {
                    UsersTable.update({ UsersTable.id eq userId }) {
                        if (request.name != null) {
                            it[name] = InputValidator.validateName(request.name)
                        }
                        if (request.phone != null) {
                            it[phone] = InputValidator.validatePhoneNumber(request.phone)
                        }
                        if (request.fcmToken != null) {
                            it[fcmToken] = InputValidator.sanitizeString(request.fcmToken, 500)
                        }
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Settings updated successfully")
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid input"))
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to update settings")
                )
            }
        }

        /**
         * PUT /api/settings/callback
         * Update webhook callback settings
         */
        put("/callback") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val request = call.receive<UpdateCallbackRequest>()

                // Validate webhook URL
                if (!isValidUrl(request.webhookUrl)) {
                    throw IllegalArgumentException("Invalid webhook URL")
                }

                val settingId = UUID.randomUUID().toString()

                transaction {
                    // Check if callback setting exists
                    val existing = CallbackSettingsTable.selectAll().where {
                        (CallbackSettingsTable.userId eq userId) and
                                (CallbackSettingsTable.groupId.isNull())
                    }.firstOrNull()

                    if (existing != null) {
                        CallbackSettingsTable.update({
                            (CallbackSettingsTable.userId eq userId) and
                                    (CallbackSettingsTable.groupId.isNull())
                        }) {
                            it[webhookUrl] = request.webhookUrl
                            it[webhookSecret] = request.webhookSecret
                            it[isActive] = request.isActive
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    } else {
                        CallbackSettingsTable.insert {
                            it[id] = settingId
                            it[CallbackSettingsTable.userId] = userId
                            it[webhookUrl] = request.webhookUrl
                            it[webhookSecret] = request.webhookSecret
                            it[events] = "[]" // JSON array
                            it[isActive] = request.isActive
                            it[createdAt] = System.currentTimeMillis()
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Callback settings updated")
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid input"))
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to update callback settings")
                )
            }
        }
    }
}

private fun isValidUrl(url: String): Boolean {
    return try {
        val urlObj = java.net.URL(url)
        url.startsWith("https://") || url.startsWith("http://")
    } catch (e: Exception) {
        false
    }
}

// DTOs
data class SettingsDTO(
    val userId: String,
    val email: String,
    val name: String,
    val phone: String,
    val fcmToken: String?,
    val role: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class UpdateSettingsRequest(
    val name: String? = null,
    val phone: String? = null,
    val fcmToken: String? = null
)

data class UpdateCallbackRequest(
    val webhookUrl: String,
    val webhookSecret: String,
    val isActive: Boolean = true
)
