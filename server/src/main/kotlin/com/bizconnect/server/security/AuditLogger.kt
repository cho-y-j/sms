package com.bizconnect.server.security

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Audit logging for security events:
 * - Login success/failure
 * - API key usage
 * - SMS sends
 * - Permission changes
 * - Account modifications
 *
 * In production, logs should be written to a secure append-only storage
 * (e.g., database table, syslog, external audit service)
 */
class AuditLogger(private val persistent: AuditLogPersistence = InMemoryAuditLog()) {
    private val queue = ConcurrentLinkedQueue<AuditEntry>()

    /**
     * Log an audit event
     */
    fun log(
        userId: String?,
        action: String,
        details: String,
        ipAddress: String,
        userAgent: String,
        severity: String = "INFO"
    ) {
        val entry = AuditEntry(
            timestamp = System.currentTimeMillis(),
            userId = userId,
            action = action,
            details = details,
            ipAddress = ipAddress,
            userAgent = userAgent,
            severity = severity
        )

        queue.offer(entry)
        persistent.store(entry)
    }

    /**
     * Get recent audit logs (for admin dashboard)
     */
    fun getRecentLogs(limit: Int = 100): List<AuditEntry> {
        return queue.toList().takeLast(limit)
    }

    /**
     * Get logs for specific user
     */
    fun getLogsForUser(userId: String, limit: Int = 100): List<AuditEntry> {
        return queue.filter { it.userId == userId }.toList().takeLast(limit)
    }

    /**
     * Get logs for specific action
     */
    fun getLogsByAction(action: String, limit: Int = 100): List<AuditEntry> {
        return queue.filter { it.action == action }.takeLast(limit)
    }

    /**
     * Get security incidents (failed logins, rate limit violations, etc.)
     */
    fun getSecurityIncidents(limit: Int = 100): List<AuditEntry> {
        return queue.filter { it.severity == "WARNING" || it.severity == "CRITICAL" }
            .toList().takeLast(limit)
    }

    /**
     * Log login attempt
     */
    fun logLoginAttempt(
        userId: String?,
        success: Boolean,
        ipAddress: String,
        userAgent: String,
        reason: String? = null
    ) {
        log(
            userId = userId,
            action = if (success) "LOGIN_SUCCESS" else "LOGIN_FAILURE",
            details = reason ?: "",
            ipAddress = ipAddress,
            userAgent = userAgent,
            severity = if (success) "INFO" else "WARNING"
        )
    }

    /**
     * Log API key usage
     */
    fun logApiKeyUsage(
        userId: String,
        apiKeyId: String,
        endpoint: String,
        ipAddress: String
    ) {
        log(
            userId = userId,
            action = "API_KEY_USED",
            details = "API Key: $apiKeyId, Endpoint: $endpoint",
            ipAddress = ipAddress,
            userAgent = "API_CLIENT"
        )
    }

    /**
     * Log SMS send
     */
    fun logSmsSend(
        userId: String,
        phoneNumber: String,
        messageId: String,
        characterCount: Int,
        ipAddress: String,
        userAgent: String
    ) {
        log(
            userId = userId,
            action = "SMS_SENT",
            details = "Phone: ${maskPhone(phoneNumber)}, ID: $messageId, Length: $characterCount",
            ipAddress = ipAddress,
            userAgent = userAgent
        )
    }

    /**
     * Log rate limit violation
     */
    fun logRateLimitViolation(
        userId: String?,
        endpoint: String,
        ipAddress: String,
        userAgent: String
    ) {
        log(
            userId = userId,
            action = "RATE_LIMIT_EXCEEDED",
            details = "Endpoint: $endpoint",
            ipAddress = ipAddress,
            userAgent = userAgent,
            severity = "WARNING"
        )
    }

    /**
     * Log failed authentication attempt
     */
    fun logAuthenticationFailure(
        email: String,
        ipAddress: String,
        userAgent: String,
        reason: String
    ) {
        log(
            userId = null,
            action = "AUTH_FAILURE",
            details = "Email: ${maskEmail(email)}, Reason: $reason",
            ipAddress = ipAddress,
            userAgent = userAgent,
            severity = "WARNING"
        )
    }

    /**
     * Log suspicious activity
     */
    fun logSuspiciousActivity(
        userId: String?,
        activity: String,
        ipAddress: String,
        userAgent: String
    ) {
        log(
            userId = userId,
            action = "SUSPICIOUS_ACTIVITY",
            details = activity,
            ipAddress = ipAddress,
            userAgent = userAgent,
            severity = "CRITICAL"
        )
    }

    /**
     * Mask sensitive information in logs
     */
    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return "****@****"
        val local = parts[0]
        val masked = if (local.length > 2) {
            local[0] + "*".repeat(local.length - 2) + local.last()
        } else {
            "*".repeat(local.length)
        }
        return "$masked@${parts[1]}"
    }

    private fun maskPhone(phone: String): String {
        return if (phone.length >= 8) {
            phone.take(3) + "****" + phone.takeLast(3)
        } else {
            "****"
        }
    }
}

data class AuditEntry(
    val timestamp: Long,
    val userId: String?,
    val action: String,
    val details: String,
    val ipAddress: String,
    val userAgent: String,
    val severity: String = "INFO"
) {
    fun toLogString(): String {
        val ts = Instant.ofEpochMilli(timestamp)
        return "[$ts] [$severity] User=$userId | Action=$action | IP=$ipAddress | Details=$details"
    }
}

/**
 * Interface for persistent audit log storage
 */
interface AuditLogPersistence {
    fun store(entry: AuditEntry)
    fun retrieve(limit: Int): List<AuditEntry>
}

/**
 * In-memory audit log (demo; use database in production)
 */
class InMemoryAuditLog : AuditLogPersistence {
    private val logs = ConcurrentLinkedQueue<AuditEntry>()
    private val maxSize = 10000

    override fun store(entry: AuditEntry) {
        logs.offer(entry)
        // Keep size bounded
        while (logs.size > maxSize) {
            logs.poll()
        }
    }

    override fun retrieve(limit: Int): List<AuditEntry> {
        return logs.toList().takeLast(limit)
    }
}
