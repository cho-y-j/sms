package com.bizconnect.server.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Security")

fun Application.configureSecurity() {
    val jwtSecret = System.getenv("JWT_SECRET") ?: "bizconnect-secret-key-change-in-production"
    val jwtIssuer = System.getenv("JWT_ISSUER") ?: "https://bizconnect.com"
    val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "bizconnect-api"

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "BizConnect Server"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                logger.warn("JWT validation failed")
            }
        }
    }
}
