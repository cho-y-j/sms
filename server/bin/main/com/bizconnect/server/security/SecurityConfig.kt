package com.bizconnect.server.security

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

/**
 * Central security configuration.
 * Implements defense-in-depth against known attack vectors:
 * 1. API Key theft -> JWT + API Key dual auth, key rotation, IP whitelist
 * 2. SQL Injection -> Exposed ORM (parameterized queries), input validation
 * 3. Brute force -> Rate limiting, account lockout, IP blocking
 */
class SecurityConfig(
    private val jwtManager: JwtManager,
    private val ipManager: IpManager,
    private val rateLimiter: RateLimiter,
    private val auditLogger: AuditLogger
) {
    fun configureJWT(application: Application) {
        application.install(Authentication) {
            jwt("auth-jwt") {
                realm = "bizconnect"
                verifier(jwtManager.verifier)
                validate { credential ->
                    try {
                        val payload = credential.payload
                        val userId = payload.getClaim("userId").asString()
                        val email = payload.getClaim("email").asString()

                        if (!userId.isNullOrEmpty() && !email.isNullOrEmpty()) {
                            JWTPrincipal(payload)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
    }

    fun configureCORS(application: Application) {
        val allowedOrigins = System.getenv("ALLOWED_ORIGINS")?.split(",")
            ?: listOf("https://app.bizconnect.com")

        application.install(CORS) {
            allowedOrigins.forEach { origin ->
                val trimmed = origin.trim()
                when {
                    trimmed.startsWith("https://") -> allowHost(trimmed.removePrefix("https://"), schemes = listOf("https"))
                    trimmed.startsWith("http://") -> allowHost(trimmed.removePrefix("http://"), schemes = listOf("http"))
                    else -> allowHost(trimmed)
                }
            }
            allowCredentials = true
            allowHeader("Content-Type")
            allowHeader("Authorization")
            allowHeader("X-API-Key")
            allowHeader("X-Request-ID")
            exposedHeaders.add("X-RateLimit-Remaining")
            exposedHeaders.add("X-RateLimit-Reset")
            maxAgeInSeconds = 3600
        }
    }

    fun configureRateLimit(application: Application) {
        application.install(RateLimit) {
            // Rate limiting configured per-endpoint via custom RateLimiter
        }
    }

    fun configureStatusPages(application: Application) {
        application.install(StatusPages) {
            exception<IllegalArgumentException> { call, cause ->
                auditLogger.log(
                    userId = null,
                    action = "VALIDATION_ERROR",
                    details = "Invalid argument: ${cause.message}",
                    ipAddress = call.request.local.remoteHost,
                    userAgent = call.request.headers["User-Agent"] ?: "unknown"
                )
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid input: ${cause.message}")
                )
            }

            exception<UnauthorizedException> { call, cause ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to (cause.message ?: "Unauthorized"))
                )
            }

            exception<ForbiddenException> { call, cause ->
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to (cause.message ?: "Forbidden"))
                )
            }

            exception<RateLimitExceededException> { call, cause ->
                call.response.headers.append("Retry-After", cause.retryAfterSeconds.toString())
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    mapOf(
                        "error" to "Rate limit exceeded",
                        "retryAfter" to cause.retryAfterSeconds.toString()
                    )
                )
            }

            exception<Exception> { call, cause ->
                auditLogger.log(
                    userId = null,
                    action = "UNHANDLED_ERROR",
                    details = "Exception: ${cause::class.simpleName} - ${cause.message}",
                    ipAddress = call.request.local.remoteHost,
                    userAgent = call.request.headers["User-Agent"] ?: "unknown"
                )
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Internal server error")
                )
            }
        }
    }

    fun configureRequestSizeLimit(application: Application) {
        // Request size limits handled by Ktor's built-in configuration
    }
}

// Custom exceptions
class UnauthorizedException(message: String) : Exception(message)
class ForbiddenException(message: String) : Exception(message)
class RateLimitExceededException(val retryAfterSeconds: Long) : Exception("Rate limit exceeded")
