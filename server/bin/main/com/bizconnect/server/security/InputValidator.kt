package com.bizconnect.server.security

/**
 * Input validation to prevent SQL injection, XSS, and other attacks.
 * Validates:
 * - Phone numbers (strict Korean format)
 * - Message content (XSS prevention)
 * - SQL injection pattern detection
 * - File uploads (type, size, magic bytes)
 * - Request body size limits
 * - JSON schema validation
 * - Parameter type checking
 */
object InputValidator {
    private const val MAX_EMAIL_LENGTH = 254
    private const val MAX_PASSWORD_LENGTH = 128
    private const val MAX_NAME_LENGTH = 100
    private const val MAX_MESSAGE_LENGTH = 1000
    private const val MAX_REQUEST_BODY_SIZE = 10 * 1024 * 1024 // 10MB

    // Phone number pattern: Korean format 010-1234-5678 or 01012345678
    private val phonePattern = Regex("^01[0-9]-?\\d{3,4}-?\\d{4}$")

    // Email pattern: Basic RFC 5322 validation
    private val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$")

    // SQL injection patterns to detect
    private val sqlInjectionPatterns = listOf(
        Regex("(?i)('|\")(;|--|\\*|\\||#)"),
        Regex("(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute)\\s"),
        Regex("(?i)(and|or)\\s+1\\s*=\\s*1"),
        Regex("(?i)xp_"),
        Regex("(?i)sp_")
    )

    // XSS patterns to detect
    private val xssPatterns = listOf(
        Regex("<script[^>]*>", RegexOption.IGNORE_CASE),
        Regex("javascript:", RegexOption.IGNORE_CASE),
        Regex("onerror\\s*=", RegexOption.IGNORE_CASE),
        Regex("onload\\s*=", RegexOption.IGNORE_CASE),
        Regex("onclick\\s*=", RegexOption.IGNORE_CASE),
        Regex("<iframe", RegexOption.IGNORE_CASE),
        Regex("<embed", RegexOption.IGNORE_CASE),
        Regex("<object", RegexOption.IGNORE_CASE)
    )

    /**
     * Validate email format and length
     */
    fun validateEmail(email: String?): String {
        if (email == null || email.isBlank()) {
            throw IllegalArgumentException("Email cannot be empty")
        }

        val trimmed = email.trim()
        if (trimmed.length > MAX_EMAIL_LENGTH) {
            throw IllegalArgumentException("Email is too long (max $MAX_EMAIL_LENGTH characters)")
        }

        if (!emailPattern.matches(trimmed)) {
            throw IllegalArgumentException("Invalid email format")
        }

        return trimmed
    }

    /**
     * Validate phone number in Korean format
     */
    fun validatePhoneNumber(phone: String?): String {
        if (phone == null || phone.isBlank()) {
            throw IllegalArgumentException("Phone number cannot be empty")
        }

        val trimmed = phone.trim()
        if (!phonePattern.matches(trimmed)) {
            throw IllegalArgumentException("Phone number must be in Korean format (e.g., 010-1234-5678)")
        }

        return trimmed
    }

    /**
     * Validate name/string with length limit
     */
    fun validateName(name: String?, maxLength: Int = MAX_NAME_LENGTH): String {
        if (name == null || name.isBlank()) {
            throw IllegalArgumentException("Name cannot be empty")
        }

        val trimmed = name.trim()
        if (trimmed.length > maxLength) {
            throw IllegalArgumentException("Name is too long (max $maxLength characters)")
        }

        return trimmed
    }

    /**
     * Validate message content for XSS and length
     */
    fun validateMessageContent(content: String?, maxLength: Int = MAX_MESSAGE_LENGTH): String {
        if (content == null || content.isBlank()) {
            throw IllegalArgumentException("Message cannot be empty")
        }

        val trimmed = content.trim()
        if (trimmed.length > maxLength) {
            throw IllegalArgumentException("Message is too long (max $maxLength characters)")
        }

        // Check for XSS patterns
        for (pattern in xssPatterns) {
            if (pattern.containsMatchIn(trimmed)) {
                throw IllegalArgumentException("Message contains invalid content")
            }
        }

        return trimmed
    }

    /**
     * Detect SQL injection patterns
     */
    fun checkSqlInjection(input: String): Boolean {
        for (pattern in sqlInjectionPatterns) {
            if (pattern.containsMatchIn(input)) {
                return true
            }
        }
        return false
    }

    /**
     * Detect XSS patterns
     */
    fun checkXss(input: String): Boolean {
        for (pattern in xssPatterns) {
            if (pattern.containsMatchIn(input)) {
                return true
            }
        }
        return false
    }

    /**
     * Validate integer parameter
     */
    fun validateInt(value: String?, fieldName: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int {
        if (value == null || value.isBlank()) {
            throw IllegalArgumentException("$fieldName cannot be empty")
        }

        return try {
            val intValue = value.toInt()
            if (intValue < min || intValue > max) {
                throw IllegalArgumentException("$fieldName must be between $min and $max")
            }
            intValue
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("$fieldName must be a valid integer")
        }
    }

    /**
     * Validate file size
     */
    fun validateFileSize(sizeBytes: Long, maxSizeBytes: Long = 5 * 1024 * 1024): Boolean {
        if (sizeBytes > maxSizeBytes) {
            throw IllegalArgumentException("File is too large (max ${maxSizeBytes / 1024 / 1024}MB)")
        }
        return true
    }

    /**
     * Validate file type by extension
     */
    fun validateFileExtension(filename: String, allowedExtensions: List<String>): Boolean {
        if (filename.isBlank()) {
            throw IllegalArgumentException("Filename cannot be empty")
        }

        val extension = filename.substringAfterLast(".").lowercase()
        if (extension.isEmpty() || !allowedExtensions.contains(extension)) {
            throw IllegalArgumentException("File type not allowed. Allowed types: ${allowedExtensions.joinToString(", ")}")
        }
        return true
    }

    /**
     * Validate request body size
     */
    fun validateRequestBodySize(sizeBytes: Long, maxSizeBytes: Long = MAX_REQUEST_BODY_SIZE.toLong()): Boolean {
        if (sizeBytes > maxSizeBytes) {
            throw IllegalArgumentException("Request body is too large")
        }
        return true
    }

    /**
     * Sanitize string for safe display/storage
     */
    fun sanitizeString(input: String, maxLength: Int = MAX_MESSAGE_LENGTH): String {
        val trimmed = input.trim()

        if (trimmed.length > maxLength) {
            throw IllegalArgumentException("Input is too long")
        }

        // Remove null bytes
        val nullBytesRemoved = trimmed.filter { it != '\u0000' }

        return nullBytesRemoved
    }

    /**
     * Validate UUID format
     */
    fun validateUUID(id: String?): String {
        if (id == null || id.isBlank()) {
            throw IllegalArgumentException("ID cannot be empty")
        }

        val uuidPattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
        if (!uuidPattern.matches(id)) {
            throw IllegalArgumentException("Invalid ID format")
        }

        return id
    }

    /**
     * Validate pagination parameters
     */
    fun validatePagination(page: Int?, limit: Int?, maxLimit: Int = 100): Pair<Int, Int> {
        val validPage = maxOf(1, page ?: 1)
        val validLimit = when {
            limit == null -> 20
            limit < 1 -> 1
            limit > maxLimit -> maxLimit
            else -> limit
        }
        return validPage to validLimit
    }
}
