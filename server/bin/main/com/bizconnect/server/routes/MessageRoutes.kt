package com.bizconnect.server.routes

import com.bizconnect.server.models.MessageRequest
import com.bizconnect.server.models.MessageResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("MessageRoutes")

fun Route.messageRoutes() {
    post("/api/v1/messages/send") {
        try {
            val request = call.receive<MessageRequest>()
            logger.info("Received message send request: recipients=${request.recipients.size}, type=${request.messageType}")

            // Validate request
            if (request.recipients.isEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Recipients list cannot be empty")
                )
                return@post
            }

            if (request.messageBody.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Message body cannot be empty")
                )
                return@post
            }

            // Process message (in production, this would save to DB and queue for sending)
            val messageId = System.currentTimeMillis()

            call.respond(
                HttpStatusCode.Accepted,
                MessageResponse(
                    id = messageId,
                    status = "PENDING",
                    message = "Message queued for sending",
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            logger.error("Error processing message send", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to process message")
            )
        }
    }

    get("/api/v1/messages/{id}") {
        try {
            val messageId = call.parameters["id"]
            logger.info("Fetching message: $messageId")

            call.respond(
                HttpStatusCode.OK,
                MessageResponse(
                    id = messageId?.toLongOrNull() ?: 0,
                    status = "DELIVERED",
                    message = "Message delivered successfully",
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            logger.error("Error fetching message", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to fetch message")
            )
        }
    }

    get("/api/v1/messages") {
        try {
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 50

            logger.info("Fetching messages: page=$page, pageSize=$pageSize")

            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "data" to emptyList<MessageResponse>(),
                    "total" to 0,
                    "page" to page,
                    "pageSize" to pageSize
                )
            )
        } catch (e: Exception) {
            logger.error("Error fetching messages", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to fetch messages")
            )
        }
    }
}
