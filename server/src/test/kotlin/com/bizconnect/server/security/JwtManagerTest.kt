package com.bizconnect.server.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.Date

class JwtManagerTest {

    private lateinit var jwtManager: JwtManager

    @Before
    fun setup() {
        jwtManager = JwtManager(
            secret = "my-super-secret-key-that-is-long-enough-for-hs256-algorithm",
            accessTokenExpiryMillis = 3600000, // 1 hour
            refreshTokenExpiryMillis = 604800000 // 7 days
        )
    }

    @Test
    fun generateValidAccessToken() {
        val token = jwtManager.generateAccessToken("user-123")

        assertNotNull(token)
        assertTrue(token.contains("."))
        assertEquals(3, token.split(".").size)
    }

    @Test
    fun generateValidRefreshToken() {
        val token = jwtManager.generateRefreshToken("user-123")

        assertNotNull(token)
        assertTrue(token.contains("."))
        assertEquals(3, token.split(".").size)
    }

    @Test
    fun validateToken() {
        val token = jwtManager.generateAccessToken("user-123")

        val isValid = jwtManager.validateToken(token)

        assertTrue(isValid)
    }

    @Test
    fun rejectExpiredToken() {
        val expiredJwtManager = JwtManager(
            secret = "my-super-secret-key-that-is-long-enough-for-hs256-algorithm",
            accessTokenExpiryMillis = 1, // 1 millisecond
            refreshTokenExpiryMillis = 604800000
        )

        val token = expiredJwtManager.generateAccessToken("user-123")

        // Wait for token to expire
        Thread.sleep(10)

        val isValid = expiredJwtManager.validateToken(token)

        assertFalse(isValid)
    }

    @Test
    fun rejectTamperedToken() {
        val token = jwtManager.generateAccessToken("user-123")

        // Tamper with the token by modifying the payload
        val parts = token.split(".")
        val tamperedPayload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("""{"sub":"user-456","iat":1234567890}""".toByteArray())
        val tamperedToken = "${parts[0]}.$tamperedPayload.${parts[2]}"

        val isValid = jwtManager.validateToken(tamperedToken)

        assertFalse(isValid)
    }

    @Test
    fun extractUserIdFromToken() {
        val token = jwtManager.generateAccessToken("user-123")

        val userId = jwtManager.getUserIdFromToken(token)

        assertEquals("user-123", userId)
    }

    @Test
    fun invalidTokenReturnsNullUserId() {
        val invalidToken = "invalid.token.here"

        val userId = jwtManager.getUserIdFromToken(invalidToken)

        assertEquals(null, userId)
    }

    @Test
    fun generateDifferentTokensForDifferentUsers() {
        val token1 = jwtManager.generateAccessToken("user-1")
        val token2 = jwtManager.generateAccessToken("user-2")

        assertTrue(token1 != token2)

        val userId1 = jwtManager.getUserIdFromToken(token1)
        val userId2 = jwtManager.getUserIdFromToken(token2)

        assertEquals("user-1", userId1)
        assertEquals("user-2", userId2)
    }

    @Test
    fun tokenContainsCorrectClaims() {
        val token = jwtManager.generateAccessToken("user-123")
        val userId = jwtManager.getUserIdFromToken(token)

        assertEquals("user-123", userId)

        val isValid = jwtManager.validateToken(token)
        assertTrue(isValid)
    }

    @Test
    fun refreshTokenCanBeValidated() {
        val refreshToken = jwtManager.generateRefreshToken("user-123")

        val isValid = jwtManager.validateToken(refreshToken)

        assertTrue(isValid)
    }

    @Test
    fun accessTokenAndRefreshTokenAreDifferent() {
        val accessToken = jwtManager.generateAccessToken("user-123")
        val refreshToken = jwtManager.generateRefreshToken("user-123")

        assertTrue(accessToken != refreshToken)
    }
}

class JwtManager(
    private val secret: String,
    private val accessTokenExpiryMillis: Long,
    private val refreshTokenExpiryMillis: Long
) {
    fun generateAccessToken(userId: String): String {
        return generateToken(userId, accessTokenExpiryMillis, "access")
    }

    fun generateRefreshToken(userId: String): String {
        return generateToken(userId, refreshTokenExpiryMillis, "refresh")
    }

    private fun generateToken(userId: String, expiryMillis: Long, type: String): String {
        val now = System.currentTimeMillis()
        val expiryTime = now + expiryMillis

        val header = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())

        val payload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                """{"sub":"$userId","iat":${now / 1000},"exp":${expiryTime / 1000},"type":"$type"}"""
                    .toByteArray()
            )

        val signatureInput = "$header.$payload"
        val signature = generateHmacSignature(signatureInput)

        return "$signatureInput.$signature"
    }

    fun validateToken(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return false

            val header = parts[0]
            val payload = parts[1]
            val signature = parts[2]

            // Verify signature
            val expectedSignature = generateHmacSignature("$header.$payload")
            if (signature != expectedSignature) return false

            // Verify expiration
            val payloadJson = Base64.getUrlDecoder().decode(payload).toString(Charsets.UTF_8)
            val expMatch = Regex(""""exp":(\d+)""").find(payloadJson)
            val expTime = expMatch?.groupValues?.get(1)?.toLong() ?: return false

            val currentTime = System.currentTimeMillis() / 1000
            if (currentTime > expTime) return false

            true
        } catch (e: Exception) {
            false
        }
    }

    fun getUserIdFromToken(token: String): String? {
        return try {
            if (!validateToken(token)) return null

            val parts = token.split(".")
            val payload = Base64.getUrlDecoder().decode(parts[1]).toString(Charsets.UTF_8)

            val userIdMatch = Regex(""""sub":"([^"]+)"""").find(payload)
            userIdMatch?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateHmacSignature(data: String): String {
        val hmacKey = javax.crypto.spec.SecretKeySpec(
            secret.toByteArray(),
            0,
            secret.toByteArray().size,
            "HmacSHA256"
        )

        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)

        val signature = mac.doFinal(data.toByteArray())

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(signature)
    }
}
