package com.bizconnect.server

import com.bizconnect.server.database.DatabaseFactory
import com.bizconnect.server.routes.authRoutes
import com.bizconnect.server.routes.taskRoutes
import com.bizconnect.server.routes.smsRoutes
import com.bizconnect.server.routes.adminRoutes
import com.bizconnect.server.routes.subscriptionRoutes
import com.bizconnect.server.routes.paymentRoutes
import com.bizconnect.server.routes.smsApiRoutes
import com.bizconnect.server.routes.userPortalRoutes
import com.bizconnect.server.services.NicePayService
import com.bizconnect.server.services.PhoneVerificationService
import com.bizconnect.server.services.WideshotSmsService
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
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondRedirect
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
    val nicePayService = NicePayService()
    val wideshotSmsService = WideshotSmsService()
    val phoneVerificationService = PhoneVerificationService(wideshotSmsService)
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
        // Landing page
        get("/") {
            val html = Thread.currentThread().contextClassLoader.getResource("landing/index.html")?.readText()
            if (html != null) call.respondText(html, ContentType.Text.Html)
            else call.respondText("BizConnect - Business SMS Platform", ContentType.Text.Plain)
        }
        get("/landing/{file}") {
            val fileName = call.parameters["file"] ?: ""
            val ct = when {
                fileName.endsWith(".css") -> ContentType.Text.CSS
                fileName.endsWith(".js") -> ContentType.Text.JavaScript
                fileName.endsWith(".png") -> ContentType.Image.PNG
                fileName.endsWith(".svg") -> ContentType.Image.SVG
                else -> ContentType.Text.Plain
            }
            val content = Thread.currentThread().contextClassLoader.getResource("landing/$fileName")?.readText()
            if (content != null) call.respondText(content, ct)
            else call.respond(HttpStatusCode.NotFound)
        }

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
        authRoutes(jwtManager, passwordManager, rateLimiter, auditLogger, apiKeyManager, phoneVerificationService)

        // Admin routes (public login, then protected endpoints)
        adminRoutes(jwtManager, passwordManager)

        // Payment routes (public approve callback, protected prepare/cancel)
        paymentRoutes(nicePayService)

        // 이미지 서빙 (로컬 → S3 리다이렉트)
        get("/uploads/{file}") {
            val fileName = call.parameters["file"] ?: ""
            val file = java.io.File("/app/uploads/$fileName")
            if (file.exists()) {
                call.respondFile(file)
            } else {
                // S3로 리다이렉트
                val s3Bucket = System.getenv("S3_BUCKET") ?: "bizconnect-uploads"
                call.respondRedirect("https://$s3Bucket.s3.ap-northeast-2.amazonaws.com/images/$fileName")
            }
        }

        // 파일 직접 다운로드
        get("/f/{file}") {
            val fileName = call.parameters["file"] ?: ""
            val s3Bucket = System.getenv("S3_BUCKET") ?: "bizconnect-uploads"
            // S3로 리다이렉트 (직접 다운로드)
            call.respondRedirect("https://$s3Bucket.s3.ap-northeast-2.amazonaws.com/files/$fileName")
        }

        // 이미지 미리보기 (OG 태그 포함)
        get("/i/{file}") {
            val fileName = call.parameters["file"] ?: ""
            val s3Bucket = System.getenv("S3_BUCKET") ?: "bizconnect-uploads"
            // S3에 있으면 S3 URL, 없으면 로컬
            val imageUrl = "https://$s3Bucket.s3.ap-northeast-2.amazonaws.com/images/$fileName"
            val html = """<!DOCTYPE html><html><head>
                <meta charset="UTF-8"><meta property="og:title" content="BizConnect">
                <meta property="og:image" content="$imageUrl"><meta property="og:type" content="website">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <style>body{margin:0;background:#000;display:flex;align-items:center;justify-content:center;min-height:100vh;}img{max-width:100%;max-height:100vh;}</style>
                </head><body><img src="$imageUrl"></body></html>""".trimIndent()
            call.respondText(html, ContentType.Text.Html)
        }

        // 개인정보처리방침
        get("/privacy") {
            val html = Thread.currentThread().contextClassLoader.getResource("privacy/index.html")?.readText()
            if (html != null) call.respondText(html, ContentType.Text.Html)
            else call.respond(HttpStatusCode.NotFound, "Not found")
        }

        // User web portal (static files)
        get("/portal") {
            val html = Thread.currentThread().contextClassLoader.getResource("portal/index.html")?.readText()
            if (html != null) call.respondText(html, ContentType.Text.Html)
            else call.respond(HttpStatusCode.NotFound, "Portal not found")
        }
        get("/portal/{file}") {
            val fileName = call.parameters["file"] ?: ""
            val ct = when {
                fileName.endsWith(".css") -> ContentType.Text.CSS
                fileName.endsWith(".js") -> ContentType.Text.JavaScript
                else -> ContentType.Text.Plain
            }
            val content = Thread.currentThread().contextClassLoader.getResource("portal/$fileName")?.readText()
            if (content != null) call.respondText(content, ct)
            else call.respond(HttpStatusCode.NotFound)
        }

        // Admin web panel (추측 불가 경로)
        get("/mgmt-dainon") {
            val html = Thread.currentThread().contextClassLoader.getResource("admin/index.html")?.readText()
            if (html != null) call.respondText(html, ContentType.Text.Html)
            else call.respond(HttpStatusCode.NotFound, "Not found")
        }
        // 기존 /admin 경로는 404
        get("/admin") {
            call.respond(HttpStatusCode.NotFound, "Not found")
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
            smsApiRoutes(wideshotSmsService)
            userPortalRoutes(wideshotSmsService)
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
