package com.bizconnect.server.routes

import com.bizconnect.server.ai.AiService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

fun Route.aiRoutes(aiService: AiService) {
    route("/api/ai") {
        post("/summarize") {
            try {
                val request = call.receive<SummarizeTextRequest>()

                if (request.text.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AiErrorResponse("Text cannot be empty")
                    )
                    return@post
                }

                val summary = aiService.summarize(request.text)
                call.respond(
                    HttpStatusCode.OK,
                    SummarizeTextResponse(summary)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AiErrorResponse("Summarization failed: ${e.message}")
                )
            }
        }

        post("/suggest-replies") {
            try {
                val request = call.receive<SuggestRepliesApiRequest>()

                if (request.message.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AiErrorResponse("Message cannot be empty")
                    )
                    return@post
                }

                val suggestions = aiService.suggestReplies(
                    message = request.message,
                    history = request.conversationHistory,
                    customerName = request.customerName
                )

                call.respond(
                    HttpStatusCode.OK,
                    SuggestRepliesApiResponse(suggestions)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AiErrorResponse("Reply suggestion failed: ${e.message}")
                )
            }
        }

        post("/generate-message") {
            try {
                val request = call.receive<GenerateMessageApiRequest>()

                if (request.purpose.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AiErrorResponse("Purpose cannot be empty")
                    )
                    return@post
                }

                val message = aiService.generateMessage(
                    purpose = request.purpose,
                    customerName = request.customerName,
                    context = request.context
                )

                call.respond(
                    HttpStatusCode.OK,
                    GenerateMessageApiResponse(message)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AiErrorResponse("Message generation failed: ${e.message}")
                )
            }
        }

        post("/classify-spam") {
            try {
                val request = call.receive<ClassifySpamApiRequest>()

                if (request.senderAddress.isBlank() || request.messageBody.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AiErrorResponse("Sender address and message body cannot be empty")
                    )
                    return@post
                }

                val classification = aiService.classifySpam(
                    address = request.senderAddress,
                    body = request.messageBody
                )

                call.respond(
                    HttpStatusCode.OK,
                    ClassifySpamApiResponse(
                        isSpam = classification.isSpam,
                        confidence = classification.confidence,
                        reason = classification.reason
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AiErrorResponse("Spam classification failed: ${e.message}")
                )
            }
        }

        post("/categorize-customer") {
            try {
                val request = call.receive<CategorizeCustomerApiRequest>()

                if (request.conversationHistory.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AiErrorResponse("Conversation history cannot be empty")
                    )
                    return@post
                }

                val categorization = aiService.categorizeCustomer(
                    messages = request.conversationHistory
                )

                call.respond(
                    HttpStatusCode.OK,
                    CategorizeCustomerApiResponse(
                        suggestedGroup = categorization.suggestedGroup,
                        confidence = categorization.confidence,
                        reason = categorization.reason
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AiErrorResponse("Customer categorization failed: ${e.message}")
                )
            }
        }
    }
}

// Request DTOs
@Serializable
data class SummarizeTextRequest(val text: String)

@Serializable
data class SuggestRepliesApiRequest(
    val message: String,
    val conversationHistory: String? = null,
    val customerName: String? = null
)

@Serializable
data class GenerateMessageApiRequest(
    val purpose: String,
    val customerName: String? = null,
    val context: String? = null
)

@Serializable
data class ClassifySpamApiRequest(
    val senderAddress: String,
    val messageBody: String
)

@Serializable
data class CategorizeCustomerApiRequest(
    val conversationHistory: String
)

// Response DTOs
@Serializable
data class SummarizeTextResponse(val summary: String)

@Serializable
data class SuggestRepliesApiResponse(val suggestions: List<String>)

@Serializable
data class GenerateMessageApiResponse(val message: String)

@Serializable
data class ClassifySpamApiResponse(
    val isSpam: Boolean,
    val confidence: Float,
    val reason: String?
)

@Serializable
data class CategorizeCustomerApiResponse(
    val suggestedGroup: String,
    val confidence: Float,
    val reason: String
)

@Serializable
data class AiErrorResponse(val error: String)
