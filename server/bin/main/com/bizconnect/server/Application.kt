package com.bizconnect.server

import com.bizconnect.server.database.DatabaseFactory
import com.bizconnect.server.routes.authRoutes
import com.bizconnect.server.routes.taskRoutes
import com.bizconnect.server.routes.smsRoutes
import com.bizconnect.server.routes.adminRoutes
import com.bizconnect.server.routes.subscriptionRoutes
import com.bizconnect.server.security.*
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.json
import java.time.LocalDateTime
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Application")

fun main() {
    val port = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080
    val environment = System.getenv("ENVIRONMENT") ?: "development"

    logger.info("Starting BizConnect Server V2 on port $port (environment: $environment)")

    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::bizConnectModule)
        .start(wait = true)
}

fun Application.bizConnectModule() {
    // Initialize database
    DatabaseFactory.init()

    // Create security managers
    val tokenBlacklist = InMemoryTokenBlacklist()
    val refreshTokenStore = InMemoryRefreshTokenStore()
    val jwtManager = JwtManager(tokenBlacklist, refreshTokenStore)
    val passwordManager = PasswordManager()
    val apiKeyManager = ApiKeyManager()
    val ipManager = IpManager()
    val rateLimiter = RateLimiter(ipManager)
    val auditLogger = AuditLogger()
    val securityConfig = SecurityConfig(jwtManager, ipManager, rateLimiter, auditLogger)

    // Install plugins
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }

    // Configure security
    securityConfig.configureCORS(this)
    securityConfig.configureJWT(this)
    securityConfig.configureStatusPages(this)

    // Routes
    routing {
        // Health check (no auth required)
        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "status" to "ok",
                    "timestamp" to LocalDateTime.now().toString(),
                    "version" to "2.0.0"
                )
            )
        }

        // Public auth routes
        authRoutes(jwtManager, passwordManager, rateLimiter, auditLogger, apiKeyManager)

        // Admin routes (public login, then protected endpoints)
        adminRoutes(jwtManager, passwordManager)

        // Admin web panel (static files)
        get("/admin") {
            val html = Thread.currentThread().contextClassLoader.getResource("admin/index.html")?.readText()
            if (html != null) call.respondText(html, ContentType.Text.Html)
            else call.respond(HttpStatusCode.NotFound, "Admin panel not found")
        }
        get("/admin/{file}") {
            val fileName = call.parameters["file"] ?: ""
            val ct = when {
                fileName.endsWith(".css") -> ContentType.Text.CSS
                fileName.endsWith(".js") -> ContentType.Text.JavaScript
                else -> ContentType.Text.Plain
            }
            val content = Thread.currentThread().contextClassLoader.getResource("admin/$fileName")?.readText()
            if (content != null) call.respondText(content, ct)
            else call.respond(HttpStatusCode.NotFound)
        }

        // Protected routes (require JWT authentication)
        authenticate("auth-jwt") {
            taskRoutes()
            smsRoutes(rateLimiter, auditLogger)
            subscriptionRoutes()
        }
    }

    logger.info("BizConnect Server V2 initialized successfully")
    logger.info("Database: ${System.getenv("DB_HOST") ?: "localhost"}:${System.getenv("DB_PORT") ?: "5432"}")
}

/**
 * In-memory token blacklist (use Redis in production)
 */
class InMemoryTokenBlacklist : TokenBlacklist {
    private val blacklist = mutableMapOf<String, Long>()

    override fun blacklist(token: String, expiresAt: java.util.Date) {
        blacklist[token] = expiresAt.time
    }

    override fun isBlacklisted(token: String): Boolean {
        val expiresAt = blacklist[token] ?: return false
        if (System.currentTimeMillis() > expiresAt) {
            blacklist.remove(token)
            return false
        }
        return true
    }

    override fun blacklistAllUserTokens(userId: String) {
        // In production, query all user tokens from database and blacklist them
        // For now, this is a no-op
    }
}

/**
 * In-memory refresh token store (use Redis in production)
 */
class InMemoryRefreshTokenStore : RefreshTokenStore {
    private data class TokenMetadata(
        val token: String,
        val userId: String,
        val isUsed: Boolean,
        val expiresAt: java.util.Date
    )

    private val tokens = mutableMapOf<String, TokenMetadata>()

    override fun storeRefreshToken(token: String, userId: String, expiresAt: java.util.Date) {
        tokens[token] = TokenMetadata(token, userId, false, expiresAt)
    }

    override fun markRefreshTokenAsUsed(token: String): Boolean {
        val metadata = tokens[token] ?: return false

        if (metadata.isUsed) {
            return false // Already used
        }

        if (System.currentTimeMillis() > metadata.expiresAt.time) {
            return false // Expired
        }

        // Mark as used
        tokens[token] = metadata.copy(isUsed = true)
        return true
    }

    override fun removeRefreshToken(token: String) {
        tokens.remove(token)
    }
}
