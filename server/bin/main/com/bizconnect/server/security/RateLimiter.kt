package com.bizconnect.server.security

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Token bucket rate limiter with per-IP and per-user limits.
 * Configurable limits for different endpoints:
 * - Login: 5 attempts per 15 minutes
 * - SMS send: 500 per day per user
 * - API calls: 100 per minute per key
 * - Registration: 3 per hour per IP
 *
 * In production, this should be backed by Redis for distributed deployments.
 */
class RateLimiter(
    private val ipManager: IpManager
) {
    // In-memory bucket storage (demo; use Redis in production)
    private val buckets = ConcurrentHashMap<String, TokenBucket>()

    // Configuration per endpoint
    private val limitConfigs = mapOf(
        "login" to RateLimitConfig(
            tokensPerWindow = 5,
            windowDurationMs = 15 * 60 * 1000L // 15 minutes
        ),
        "register" to RateLimitConfig(
            tokensPerWindow = 3,
            windowDurationMs = 60 * 60 * 1000L // 1 hour
        ),
        "sms_send" to RateLimitConfig(
            tokensPerWindow = 500,
            windowDurationMs = 24 * 60 * 60 * 1000L // 1 day
        ),
        "api_call" to RateLimitConfig(
            tokensPerWindow = 100,
            windowDurationMs = 60 * 1000L // 1 minute
        ),
        "password_reset" to RateLimitConfig(
            tokensPerWindow = 3,
            windowDurationMs = 60 * 60 * 1000L // 1 hour
        )
    )

    /**
     * Check rate limit for IP address
     * @param endpoint The endpoint being accessed
     * @param ipAddress Client IP address
     * @return RateLimitResult with allowed, remaining tokens, and reset time
     */
    fun checkIPLimit(endpoint: String, ipAddress: String): RateLimitResult {
        // Check if IP is blacklisted
        if (ipManager.isIpBlacklisted(ipAddress)) {
            throw ForbiddenException("IP address is blocked due to suspicious activity")
        }

        val config = limitConfigs[endpoint]
            ?: throw IllegalArgumentException("Unknown endpoint: $endpoint")

        val bucketKey = "ip:$endpoint:$ipAddress"
        val bucket = getOrCreateBucket(bucketKey, config)

        return bucket.tryConsume(1)
    }

    /**
     * Check rate limit for user
     * @param endpoint The endpoint being accessed
     * @param userId User ID
     * @param tokensToConsume Number of tokens to consume (default 1)
     * @return RateLimitResult
     */
    fun checkUserLimit(endpoint: String, userId: String, tokensToConsume: Int = 1): RateLimitResult {
        val config = limitConfigs[endpoint]
            ?: throw IllegalArgumentException("Unknown endpoint: $endpoint")

        val bucketKey = "user:$endpoint:$userId"
        val bucket = getOrCreateBucket(bucketKey, config)

        return bucket.tryConsume(tokensToConsume)
    }

    /**
     * Check rate limit for API key
     * @param apiKeyId API key ID
     * @param tokensToConsume Number of tokens to consume (default 1)
     * @param limitPerMinute Custom rate limit for this key
     */
    fun checkApiKeyLimit(
        apiKeyId: String,
        tokensToConsume: Int = 1,
        limitPerMinute: Int = 100
    ): RateLimitResult {
        val config = RateLimitConfig(
            tokensPerWindow = limitPerMinute,
            windowDurationMs = 60 * 1000L
        )

        val bucketKey = "api_key:$apiKeyId"
        val bucket = getOrCreateBucket(bucketKey, config)

        return bucket.tryConsume(tokensToConsume)
    }

    /**
     * Record failed login attempt and check for brute force
     * @param ipAddress Client IP
     * @param userId User ID (if known)
     * @return true if limit exceeded (should block further attempts)
     */
    fun recordFailedLoginAttempt(ipAddress: String, userId: String?): Boolean {
        val ipResult = checkIPLimit("login", ipAddress)

        if (!ipResult.allowed) {
            // Too many failed attempts from this IP, block it
            ipManager.blacklistIp(
                ipAddress = ipAddress,
                reason = "Brute force login attempts",
                durationMs = 60 * 60 * 1000L // 1 hour
            )
            throw RateLimitExceededException(ipResult.resetInSeconds)
        }

        // Also track per-user if available
        if (userId != null) {
            val userResult = checkUserLimit("login", userId)
            if (!userResult.allowed) {
                throw RateLimitExceededException(userResult.resetInSeconds)
            }
        }

        return false
    }

    /**
     * Reset rate limit for endpoint/key combination
     */
    fun resetLimit(key: String) {
        buckets.remove(key)
    }

    /**
     * Clear all limits (mainly for testing)
     */
    fun clearAll() {
        buckets.clear()
    }

    private fun getOrCreateBucket(key: String, config: RateLimitConfig): TokenBucket {
        return buckets.computeIfAbsent(key) {
            TokenBucket(
                capacity = config.tokensPerWindow.toDouble(),
                refillRatePerMs = config.tokensPerWindow.toDouble() / config.windowDurationMs
            )
        }
    }
}

/**
 * Token bucket implementation for rate limiting
 */
class TokenBucket(
    private val capacity: Double,
    private val refillRatePerMs: Double
) {
    private var tokens = capacity
    private var lastRefillTime = System.currentTimeMillis()

    @Synchronized
    fun tryConsume(tokens: Int = 1): RateLimitResult {
        val now = System.currentTimeMillis()
        refill(now)

        val allowed = this.tokens >= tokens
        if (allowed) {
            this.tokens -= tokens
        }

        val resetTime = if (allowed) {
            ((capacity - this.tokens) / refillRatePerMs).toLong()
        } else {
            ((tokens - this.tokens) / refillRatePerMs).toLong()
        }

        return RateLimitResult(
            allowed = allowed,
            remainingTokens = this.tokens.toLong(),
            resetInSeconds = max(1, resetTime / 1000L)
        )
    }

    private fun refill(now: Long) {
        val timePassed = now - lastRefillTime
        val tokensToAdd = timePassed * refillRatePerMs
        tokens = min(capacity, tokens + tokensToAdd)
        lastRefillTime = now
    }
}

data class RateLimitConfig(
    val tokensPerWindow: Int,
    val windowDurationMs: Long
)

data class RateLimitResult(
    val allowed: Boolean,
    val remainingTokens: Long,
    val resetInSeconds: Long
)
