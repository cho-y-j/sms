package com.bizconnect.server.security

import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.SecureRandom
import java.util.Base64

/**
 * API key management with:
 * - Secure random key generation (256-bit)
 * - BCrypt hashing before storage
 * - Key rotation with 24-hour grace period
 * - Per-key rate limits and permissions
 * - Key revocation
 */
class ApiKeyManager {
    private val keyLength = 32 // 256 bits
    private val secureRandom = SecureRandom()
    private val bcryptCost = 12
    private val keyRotationGracePeriodMs = 24 * 60 * 60 * 1000L // 24 hours

    /**
     * Generate a new API key
     * Returns: Base64-encoded random 256-bit key
     */
    fun generateApiKey(): String {
        val randomBytes = ByteArray(keyLength)
        secureRandom.nextBytes(randomBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }

    /**
     * Hash API key for storage using BCrypt
     * Never store plain API keys
     */
    fun hashApiKey(apiKey: String): String {
        return BCrypt.withDefaults()
            .hashToString(bcryptCost, apiKey.toCharArray())
    }

    /**
     * Verify API key against stored hash
     */
    fun verifyApiKey(plainApiKey: String, hash: String): Boolean {
        return try {
            BCrypt.verifyer().verify(plainApiKey.toCharArray(), hash.toByteArray()).verified
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if rotated key is still valid (within grace period)
     */
    fun isRotatedKeyStillValid(rotatedAt: Long): Boolean {
        return System.currentTimeMillis() - rotatedAt <= keyRotationGracePeriodMs
    }

    /**
     * Validate API key format
     */
    fun validateApiKeyFormat(apiKey: String): Boolean {
        // Should be valid Base64
        return try {
            Base64.getUrlDecoder().decode(apiKey).size == keyLength
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * API key metadata stored in database
 */
data class StoredApiKey(
    val id: String,
    val userId: String,
    val name: String,
    val keyHash: String,
    val permissions: List<String>,
    val rateLimitPerMinute: Int,
    val isActive: Boolean,
    val lastUsedAt: Long?,
    val createdAt: Long,
    val expiresAt: Long?,
    val rotatedAt: Long? = null
)

enum class ApiKeyPermission(val value: String) {
    SMS_SEND("sms:send"),
    SMS_READ("sms:read"),
    CUSTOMER_READ("customer:read"),
    CUSTOMER_WRITE("customer:write"),
    TASK_READ("task:read"),
    TASK_WRITE("task:write"),
    SETTINGS_READ("settings:read"),
    SETTINGS_WRITE("settings:write")
}
