package com.bizconnect.server.security

import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * Password security management with BCrypt hashing, strength validation,
 * breach detection, and account lockout after failed attempts.
 */
class PasswordManager {
    private val bcryptCost = 12
    private val maxLoginAttempts = 5
    private val lockoutDurationMinutes = 30

    private val commonPasswords = setOf(
        "password", "123456", "12345678", "qwerty", "abc123",
        "monkey", "1234567", "letmein", "trustno1", "dragon",
        "baseball", "111111", "iloveyou", "master", "sunshine",
        "ashley", "bailey", "passw0rd", "shadow", "123123",
        "654321", "superman", "qazwsx", "michael", "football"
    )

    /**
     * Hash password using BCrypt with configurable cost
     */
    fun hashPassword(password: String): String {
        validatePasswordStrength(password)
        return BCrypt.withDefaults().hashToString(bcryptCost, password.toCharArray())
    }

    /**
     * Verify plaintext password against BCrypt hash
     */
    fun verifyPassword(plainPassword: String, hash: String): Boolean {
        return try {
            BCrypt.verifyer()
                .verify(plainPassword.toCharArray(), hash.toByteArray())
                .verified
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate password strength
     * Requirements:
     * - Minimum 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one number
     * - At least one special character
     * - Not in common passwords list
     */
    fun validatePasswordStrength(password: String) {
        if (password.length < 8) {
            throw IllegalArgumentException("Password must be at least 8 characters long")
        }

        if (!password.any { it.isUpperCase() }) {
            throw IllegalArgumentException("Password must contain at least one uppercase letter")
        }

        if (!password.any { it.isLowerCase() }) {
            throw IllegalArgumentException("Password must contain at least one lowercase letter")
        }

        if (!password.any { it.isDigit() }) {
            throw IllegalArgumentException("Password must contain at least one number")
        }

        val specialCharPattern = Regex("[!@#$%^&*()_+\\-=\\[\\]{};':\",./<>?]")
        if (!specialCharPattern.containsMatchIn(password)) {
            throw IllegalArgumentException("Password must contain at least one special character")
        }

        // Check against common passwords (case-insensitive)
        if (commonPasswords.contains(password.lowercase())) {
            throw IllegalArgumentException("Password is too common. Please choose a stronger password")
        }
    }

    /**
     * Check if account should be locked due to failed attempts
     */
    fun shouldLockAccount(failedAttempts: Int): Boolean {
        return failedAttempts >= maxLoginAttempts
    }

    /**
     * Get lockout duration in minutes
     */
    fun getLockoutDurationMinutes(): Long = lockoutDurationMinutes.toLong()

    /**
     * Calculate lockout expiry time
     */
    fun calculateLockoutExpiry(): Long {
        return System.currentTimeMillis() + (lockoutDurationMinutes * 60 * 1000)
    }

    /**
     * Check if account is currently locked
     */
    fun isAccountLocked(lockedUntil: Long?): Boolean {
        if (lockedUntil == null) return false
        return System.currentTimeMillis() < lockedUntil
    }
}
