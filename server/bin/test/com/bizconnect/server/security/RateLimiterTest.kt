package com.bizconnect.server.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RateLimiterTest {

    private lateinit var rateLimiter: RateLimiter

    @Before
    fun setup() {
        rateLimiter = RateLimiter(
            requestsPerWindow = 10,
            windowDurationMillis = 60000 // 1 minute
        )
    }

    @Test
    fun allowRequestsUnderLimit() {
        repeat(10) {
            assertTrue(rateLimiter.allowRequest("user-1"))
        }
    }

    @Test
    fun blockRequestsOverLimit() {
        repeat(10) {
            assertTrue(rateLimiter.allowRequest("user-1"))
        }

        assertFalse(rateLimiter.allowRequest("user-1"))
    }

    @Test
    fun resetAfterTimeWindow() {
        repeat(10) {
            assertTrue(rateLimiter.allowRequest("user-1"))
        }

        // Should be rate limited
        assertFalse(rateLimiter.allowRequest("user-1"))

        // Simulate time passing (in real scenario would use actual time)
        rateLimiter.resetWindow("user-1")

        // Should allow requests again
        assertTrue(rateLimiter.allowRequest("user-1"))
    }

    @Test
    fun perUserTracking() {
        repeat(10) {
            assertTrue(rateLimiter.allowRequest("user-1"))
        }

        repeat(5) {
            assertTrue(rateLimiter.allowRequest("user-2"))
        }

        // user-1 should be rate limited
        assertFalse(rateLimiter.allowRequest("user-1"))

        // user-2 should still be able to make requests
        assertTrue(rateLimiter.allowRequest("user-2"))
    }

    @Test
    fun perIpTracking() {
        repeat(10) {
            assertTrue(rateLimiter.allowRequest("192.168.1.1"))
        }

        repeat(5) {
            assertTrue(rateLimiter.allowRequest("192.168.1.2"))
        }

        // IP 1 should be rate limited
        assertFalse(rateLimiter.allowRequest("192.168.1.1"))

        // IP 2 should still be able to make requests
        assertTrue(rateLimiter.allowRequest("192.168.1.2"))
    }

    @Test
    fun getRequestCountForIdentifier() {
        repeat(5) {
            rateLimiter.allowRequest("user-1")
        }

        val count = rateLimiter.getRequestCount("user-1")
        assertTrue(count >= 5)
    }

    @Test
    fun checkIfRateLimited() {
        repeat(10) {
            rateLimiter.allowRequest("user-1")
        }

        assertTrue(rateLimiter.isRateLimited("user-1"))
        assertFalse(rateLimiter.isRateLimited("user-2"))
    }

    @Test
    fun customRequestsPerWindow() {
        val strictLimiter = RateLimiter(
            requestsPerWindow = 3,
            windowDurationMillis = 60000
        )

        assertTrue(strictLimiter.allowRequest("user"))
        assertTrue(strictLimiter.allowRequest("user"))
        assertTrue(strictLimiter.allowRequest("user"))
        assertFalse(strictLimiter.allowRequest("user"))
    }

    @Test
    fun identifierCaseSensitivity() {
        repeat(10) {
            rateLimiter.allowRequest("User-1")
        }

        // Different case should be treated as different identifier
        assertTrue(rateLimiter.allowRequest("user-1"))
    }

    @Test
    fun getRateLimitInfo() {
        repeat(7) {
            rateLimiter.allowRequest("user-1")
        }

        val info = rateLimiter.getRateLimitInfo("user-1")

        assertTrue(info.requestsRemaining >= 3)
        assertTrue(info.requestsMade >= 7)
    }
}

data class RateLimitInfo(
    val requestsMade: Int,
    val requestsRemaining: Int,
    val resetTime: Long
)

class RateLimiter(
    private val requestsPerWindow: Int,
    private val windowDurationMillis: Long
) {
    private val requestCounts = mutableMapOf<String, MutableList<Long>>()

    fun allowRequest(identifier: String): Boolean {
        val now = System.currentTimeMillis()
        val requests = requestCounts.getOrPut(identifier) { mutableListOf() }

        // Remove requests outside the current window
        requests.removeIf { it < now - windowDurationMillis }

        return if (requests.size < requestsPerWindow) {
            requests.add(now)
            true
        } else {
            false
        }
    }

    fun isRateLimited(identifier: String): Boolean {
        val requests = requestCounts[identifier] ?: return false
        val now = System.currentTimeMillis()

        // Clean old requests
        requests.removeIf { it < now - windowDurationMillis }

        return requests.size >= requestsPerWindow
    }

    fun getRequestCount(identifier: String): Int {
        val requests = requestCounts[identifier] ?: return 0
        val now = System.currentTimeMillis()

        return requests.count { it >= now - windowDurationMillis }
    }

    fun resetWindow(identifier: String) {
        requestCounts[identifier]?.clear()
    }

    fun getRateLimitInfo(identifier: String): RateLimitInfo {
        val now = System.currentTimeMillis()
        val requests = requestCounts[identifier] ?: emptyList()

        val validRequests = requests.count { it >= now - windowDurationMillis }
        val remaining = (requestsPerWindow - validRequests).coerceAtLeast(0)

        val oldestValidRequest = requests
            .filter { it >= now - windowDurationMillis }
            .minOrNull() ?: now

        val resetTime = oldestValidRequest + windowDurationMillis

        return RateLimitInfo(
            requestsMade = validRequests,
            requestsRemaining = remaining,
            resetTime = resetTime
        )
    }
}
