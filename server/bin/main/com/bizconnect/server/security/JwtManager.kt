package com.bizconnect.server.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.util.Date
import java.util.UUID

/**
 * JWT token management with short expiry, refresh rotation, and blacklisting.
 * Implements:
 * - 15-minute access tokens
 * - 7-day refresh tokens (one-time use)
 * - Token blacklisting for logout
 * - HMAC256 signing with environment secret
 */
class JwtManager(
    private val tokenBlacklist: TokenBlacklist,
    private val refreshTokenStore: RefreshTokenStore
) {
    private val secret = System.getenv("JWT_SECRET")
        ?: throw IllegalStateException("JWT_SECRET environment variable not set")

    private val algorithm = Algorithm.HMAC256(secret)

    val verifier = JWT.require(algorithm)
        .withIssuer("bizconnect")
        .build()

    private val accessTokenExpiryMinutes =
        System.getenv("JWT_ACCESS_EXPIRY_MINUTES")?.toLongOrNull() ?: 15L

    private val refreshTokenExpiryDays =
        System.getenv("JWT_REFRESH_EXPIRY_DAYS")?.toLongOrNull() ?: 7L

    /**
     * Create access and refresh tokens for user
     */
    fun createTokenPair(userId: String, email: String): TokenPair {
        val accessToken = createAccessToken(userId, email)
        val refreshToken = createRefreshToken(userId, email)

        // Store refresh token with one-time use tracking
        refreshTokenStore.storeRefreshToken(
            token = refreshToken,
            userId = userId,
            expiresAt = Date(System.currentTimeMillis() + refreshTokenExpiryDays * 24 * 60 * 60 * 1000)
        )

        return TokenPair(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = accessTokenExpiryMinutes * 60,
            tokenType = "Bearer"
        )
    }

    /**
     * Create short-lived access token
     */
    private fun createAccessToken(userId: String, email: String): String {
        val now = Date()
        val expiresAt = Date(now.time + accessTokenExpiryMinutes * 60 * 1000)

        return JWT.create()
            .withIssuer("bizconnect")
            .withAudience("bizconnect-api")
            .withSubject(userId)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("type", "access")
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .withJWTId(UUID.randomUUID().toString())
            .sign(algorithm)
    }

    /**
     * Create refresh token (one-time use)
     */
    private fun createRefreshToken(userId: String, email: String): String {
        val now = Date()
        val expiresAt = Date(now.time + refreshTokenExpiryDays * 24 * 60 * 60 * 1000)

        return JWT.create()
            .withIssuer("bizconnect")
            .withAudience("bizconnect-api")
            .withSubject(userId)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("type", "refresh")
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .withJWTId(UUID.randomUUID().toString())
            .sign(algorithm)
    }

    /**
     * Verify token validity (not expired, not blacklisted, signature valid)
     */
    fun verifyToken(token: String): TokenClaims {
        try {
            // Check if token is blacklisted
            if (tokenBlacklist.isBlacklisted(token)) {
                throw UnauthorizedException("Token has been revoked")
            }

            val decodedJWT = verifier.verify(token)

            val userId = decodedJWT.getClaim("userId").asString()
            val email = decodedJWT.getClaim("email").asString()
            val type = decodedJWT.getClaim("type").asString()

            if (userId == null || email == null || type == null) {
                throw UnauthorizedException("Invalid token claims")
            }

            return TokenClaims(
                userId = userId,
                email = email,
                type = type,
                issuedAt = decodedJWT.issuedAt,
                expiresAt = decodedJWT.expiresAt
            )
        } catch (e: JWTVerificationException) {
            throw UnauthorizedException("Invalid token: ${e.message}")
        }
    }

    /**
     * Refresh an access token using a refresh token
     * Implements one-time use: invalidates the old refresh token, issues new pair
     */
    fun refreshAccessToken(refreshToken: String): TokenPair {
        try {
            // Verify token signature and expiry
            val claims = verifyToken(refreshToken)

            if (claims.type != "refresh") {
                throw UnauthorizedException("Invalid token type")
            }

            // Check if refresh token has been used (one-time use enforcement)
            if (!refreshTokenStore.markRefreshTokenAsUsed(refreshToken)) {
                // Token was already used - security violation, invalidate all tokens for user
                tokenBlacklist.blacklistAllUserTokens(claims.userId)
                throw UnauthorizedException("Refresh token has already been used - please login again")
            }

            // Create new token pair
            val newTokenPair = createTokenPair(claims.userId, claims.email)

            // Blacklist the old refresh token
            tokenBlacklist.blacklist(refreshToken, claims.expiresAt)

            return newTokenPair
        } catch (e: JWTVerificationException) {
            throw UnauthorizedException("Invalid refresh token")
        }
    }

    /**
     * Revoke a token immediately (logout)
     */
    fun revokeToken(token: String) {
        try {
            val claims = verifyToken(token)
            tokenBlacklist.blacklist(token, claims.expiresAt)
        } catch (e: Exception) {
            // Token already invalid, no action needed
        }
    }

    /**
     * Revoke all tokens for a user (security: after password change)
     */
    fun revokeAllUserTokens(userId: String) {
        tokenBlacklist.blacklistAllUserTokens(userId)
    }
}

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String
)

data class TokenClaims(
    val userId: String,
    val email: String,
    val type: String,
    val issuedAt: Date,
    val expiresAt: Date
)

/**
 * In-memory/Redis-backed token blacklist for logout
 */
interface TokenBlacklist {
    fun blacklist(token: String, expiresAt: Date)
    fun isBlacklisted(token: String): Boolean
    fun blacklistAllUserTokens(userId: String)
}

/**
 * Refresh token storage with one-time use tracking
 */
interface RefreshTokenStore {
    fun storeRefreshToken(token: String, userId: String, expiresAt: Date)
    fun markRefreshTokenAsUsed(token: String): Boolean
    fun removeRefreshToken(token: String)
}
