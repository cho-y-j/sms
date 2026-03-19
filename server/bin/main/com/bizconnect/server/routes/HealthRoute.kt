package com.bizconnect.server.routes

import com.bizconnect.server.models.HealthResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoute() {
    get("/health") {
        call.respond(
            HttpStatusCode.OK,
            HealthResponse(
                status = "UP",
                version = "2.0.0",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    get("/status") {
        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "service" to "BizConnect API",
                "status" to "running",
                "version" to "2.0.0",
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
}
