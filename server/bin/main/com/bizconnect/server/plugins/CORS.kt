package com.bizconnect.server.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

fun Application.configureCors() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-API-Key")
        allowCredentials = true
        maxAgeInSeconds = 3600

        // Allow all origins in development
        if (System.getenv("ENVIRONMENT") == "development") {
            anyHost()
        } else {
            // Restrict to specific domains in production
            allowHost("bizconnect.com")
            allowHost("app.bizconnect.com")
            allowHost("localhost:3000")
            allowHost("localhost:8080")
        }
    }
}
