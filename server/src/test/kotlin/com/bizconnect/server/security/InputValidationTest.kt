package com.bizconnect.server.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InputValidationTest {

    private lateinit var validator: InputValidator

    @Before
    fun setup() {
        validator = InputValidator()
    }

    @Test
    fun rejectSqlInjectionInPhoneNumber() {
        val maliciousInput = "01012345678'; DROP TABLE users; --"

        val isValid = validator.validatePhoneNumber(maliciousInput)

        assertFalse(isValid)
    }

    @Test
    fun rejectXssInMessage() {
        val xssPayload = "<script>alert('xss')</script>"

        val isValid = validator.validateMessage(xssPayload)

        assertFalse(isValid)
    }

    @Test
    fun acceptValidKoreanPhoneNumber() {
        val validPhone = "01012345678"

        val isValid = validator.validatePhoneNumber(validPhone)

        assertTrue(isValid)
    }

    @Test
    fun rejectEmptyFields() {
        assertFalse(validator.validatePhoneNumber(""))
        assertFalse(validator.validateMessage(""))
        assertFalse(validator.validateCustomerName(""))
    }

    @Test
    fun rejectOversizedPayload() {
        val hugeString = "A".repeat(100001)

        val isValid = validator.validateMessage(hugeString)

        assertFalse(isValid)
    }

    @Test
    fun sanitizeHtmlInMessage() {
        val htmlMessage = "<div>Hello <b>World</b></div>"

        val sanitized = validator.sanitizeMessage(htmlMessage)

        assertFalse(sanitized.contains("<div>"))
        assertFalse(sanitized.contains("<b>"))
        assertTrue(sanitized.contains("Hello"))
        assertTrue(sanitized.contains("World"))
    }

    @Test
    fun acceptValidEmail() {
        assertTrue(validator.validateEmail("user@example.com"))
        assertTrue(validator.validateEmail("test.user+alias@company.co.kr"))
        assertFalse(validator.validateEmail("invalid.email@"))
        assertFalse(validator.validateEmail("@example.com"))
    }

    @Test
    fun rejectCommandInjection() {
        val commandInjection = "'; rm -rf /; echo '"

        val isValid = validator.validateMessage(commandInjection)

        assertFalse(isValid)
    }

    @Test
    fun validateCustomerName() {
        assertTrue(validator.validateCustomerName("김민준"))
        assertTrue(validator.validateCustomerName("John Doe"))
        assertFalse(validator.validateCustomerName("A".repeat(101)))
        assertFalse(validator.validateCustomerName("Name<script>"))
    }

    @Test
    fun validateMessageLength() {
        assertTrue(validator.validateMessage("Short message"))
        assertTrue(validator.validateMessage("A".repeat(100000)))
        assertFalse(validator.validateMessage("A".repeat(100001)))
    }
}

class InputValidator {

    fun validatePhoneNumber(phone: String): Boolean {
        if (phone.isBlank()) return false
        if (phone.length > 20) return false

        // Reject common SQL injection patterns
        val sqlPatterns = listOf("'", "\"", ";", "--", "/*", "*/", "xp_", "sp_")
        if (sqlPatterns.any { phone.contains(it, ignoreCase = true) }) {
            return false
        }

        // Only allow digits and common formatting characters
        return phone.matches(Regex("^[0-9\\-\\s()]+$"))
    }

    fun validateMessage(message: String): Boolean {
        if (message.isBlank()) return false
        if (message.length > 100000) return false

        // Reject XSS patterns
        val xssPatterns = listOf("<script", "</script>", "javascript:", "onerror=", "onclick=")
        if (xssPatterns.any { message.contains(it, ignoreCase = true) }) {
            return false
        }

        // Reject command injection patterns
        val cmdPatterns = listOf("'; ", "; rm", "$(", "`", "| rm", "&& rm")
        if (cmdPatterns.any { message.contains(it, ignoreCase = true) }) {
            return false
        }

        return true
    }

    fun validateEmail(email: String): Boolean {
        if (email.isBlank()) return false
        if (email.length > 254) return false

        val emailRegex = Regex(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        )

        return email.matches(emailRegex)
    }

    fun validateCustomerName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.length > 100) return false

        // Reject HTML/script tags
        val dangerousPatterns = listOf("<", ">", "script", "iframe")
        if (dangerousPatterns.any { name.contains(it, ignoreCase = true) }) {
            return false
        }

        return true
    }

    fun sanitizeMessage(message: String): String {
        var sanitized = message

        // Remove HTML tags
        sanitized = sanitized.replace(Regex("<[^>]*>"), "")

        // Decode HTML entities
        sanitized = sanitized
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")

        return sanitized.trim()
    }
}
