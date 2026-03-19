package com.bizconnect.server.plugins

import com.bizconnect.server.routes.healthRoute
import com.bizconnect.server.routes.messageRoutes
import com.bizconnect.server.routes.adminRoutes
import com.bizconnect.server.routes.subscriptionRoutes
import com.bizconnect.server.security.JwtManager
import com.bizconnect.server.security.PasswordManager
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.http.ContentType
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.routing.route

fun Application.configureRouting(jwtManager: JwtManager, passwordManager: PasswordManager) {
    routing {
        healthRoute()
        messageRoutes()
        adminRoutes(jwtManager, passwordManager)
        authenticate("auth-jwt") {
            subscriptionRoutes()
        }
        // Admin web panel - serve static files from resources
        get("/admin") {
            val html = this::class.java.classLoader.getResource("admin/index.html")?.readText() ?: "Not found"
            call.respondText(html, ContentType.Text.Html)
        }
        get("/admin/{file}") {
            val fileName = call.parameters["file"] ?: ""
            val contentType = when {
                fileName.endsWith(".css") -> ContentType.Text.CSS
                fileName.endsWith(".js") -> ContentType.Text.JavaScript
                else -> ContentType.Text.Plain
            }
            val content = this::class.java.classLoader.getResource("admin/$fileName")?.readText() ?: "Not found"
            call.respondText(content, contentType)
        }
    }
}
