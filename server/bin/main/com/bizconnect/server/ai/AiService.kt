package com.bizconnect.server.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject

/**
 * AI service that interfaces with DeepSeek or other AI providers.
 * Provides intelligent message processing, summarization, suggestion, and classification.
 */
class AiService(private val apiKey: String) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val baseUrl = "https://api.deepseek.com/v1"
    private val model = "deepseek-chat"

    /**
     * Summarize text using AI.
     * Returns a concise summary of the input text.
     */
    suspend fun summarize(text: String): String {
        return try {
            val prompt = buildSummarizePrompt(text)
            val response = callAiApi(prompt)
            extractTextFromResponse(response)
        } catch (e: Exception) {
            throw AiServiceException("Summarization failed", e)
        }
    }

    /**
     * Suggest 3 reply options based on received message.
     * Considers conversation history and customer info.
     */
    suspend fun suggestReplies(
        message: String,
        history: String? = null,
        customerName: String? = null
    ): List<String> {
        return try {
            val prompt = buildSuggestRepliesPrompt(message, history, customerName)
            val response = callAiApi(prompt)
            extractReplySuggestions(response)
        } catch (e: Exception) {
            throw AiServiceException("Reply suggestion failed", e)
        }
    }

    /**
     * Generate a business message for specific purpose.
     * Respects customer name and context.
     */
    suspend fun generateMessage(
        purpose: String,
        customerName: String? = null,
        context: String? = null
    ): String {
        return try {
            val prompt = buildGenerateMessagePrompt(purpose, customerName, context)
            val response = callAiApi(prompt)
            extractTextFromResponse(response)
        } catch (e: Exception) {
            throw AiServiceException("Message generation failed", e)
        }
    }

    /**
     * Classify message as spam or legitimate.
     * Returns classification with confidence score.
     */
    suspend fun classifySpam(address: String, body: String): SpamResult {
        return try {
            val prompt = buildClassifySpamPrompt(address, body)
            val response = callAiApi(prompt)
            extractSpamClassification(response)
        } catch (e: Exception) {
            throw AiServiceException("Spam classification failed", e)
        }
    }

    /**
     * Categorize customer based on conversation history.
     * Returns suggested group and reasoning.
     */
    suspend fun categorizeCustomer(messages: String): CategoryResult {
        return try {
            val prompt = buildCategorizationPrompt(messages)
            val response = callAiApi(prompt)
            extractCategorization(response)
        } catch (e: Exception) {
            throw AiServiceException("Customer categorization failed", e)
        }
    }

    private suspend fun callAiApi(prompt: String): String {
        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
            put("temperature", 0.7)
            put("max_tokens", 1024)
            put("top_p", 0.95)
        }

        return try {
            val response = client.post("$baseUrl/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val text = response.bodyAsText()
            if (response.status.value != 200) {
                throw AiServiceException("API returned status ${response.status.value}: $text")
            }

            // Parse JSON response and extract message content
            val json = Json.parseToJsonElement(text)
            val content = json.jsonObject["choices"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: throw AiServiceException("Invalid API response format")

            content
        } catch (e: Exception) {
            if (e is AiServiceException) throw e
            throw AiServiceException("API call failed: ${e.message}", e)
        }
    }

    private fun buildSummarizePrompt(text: String): String {
        return """
        Please provide a concise summary of the following text in Korean.
        Keep it to 2-3 sentences maximum.

        Text:
        $text

        Summary:
        """.trimIndent()
    }

    private fun buildSuggestRepliesPrompt(
        message: String,
        history: String?,
        customerName: String?
    ): String {
        val customerContext = if (customerName != null) {
            "The customer's name is $customerName. "
        } else {
            ""
        }

        val historyContext = if (!history.isNullOrBlank()) {
            "Previous conversation:\n$history\n\n"
        } else {
            ""
        }

        return """
        $customerContext${historyContext}Received message:
        "$message"

        Generate exactly 3 appropriate reply suggestions in Korean for a business context.
        Each reply should be practical and professional.
        Format each reply on a new line starting with a number (1., 2., 3.).

        Replies:
        """.trimIndent()
    }

    private fun buildGenerateMessagePrompt(
        purpose: String,
        customerName: String?,
        context: String?
    ): String {
        val customerPart = if (customerName != null) {
            "addressed to $customerName"
        } else {
            "for a valued customer"
        }

        val contextPart = if (!context.isNullOrBlank()) {
            "Context: $context\n\n"
        } else {
            ""
        }

        return """
        Generate a professional business message $customerPart.
        Purpose: $purpose
        ${contextPart}Requirements:
        - Write in Korean
        - Keep it concise and polite
        - Make it appropriate for business communication
        - Use respectful tone

        Message:
        """.trimIndent()
    }

    private fun buildClassifySpamPrompt(address: String, body: String): String {
        return """
        Analyze whether the following message is spam.

        Sender: $address
        Message: "$body"

        Respond in this exact format:
        IS_SPAM: [yes/no]
        CONFIDENCE: [0-100]
        REASON: [brief explanation in Korean]
        """.trimIndent()
    }

    private fun buildCategorizationPrompt(messages: String): String {
        return """
        Based on the following conversation history, suggest a customer category/group.
        Consider communication patterns, interests, and customer type.

        Conversation:
        $messages

        Respond in this exact format:
        GROUP: [suggested group name in Korean]
        CONFIDENCE: [0-100]
        REASON: [brief explanation in Korean]
        """.trimIndent()
    }

    private fun extractTextFromResponse(response: String): String {
        return response.trim()
            .substringBefore("\n")
            .ifBlank { response.trim() }
    }

    private fun extractReplySuggestions(response: String): List<String> {
        val suggestions = mutableListOf<String>()
        val lines = response.split("\n")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^\\d+\\.\\s+.*"))) {
                val suggestion = trimmed
                    .replaceFirst(Regex("^\\d+\\.\\s+"), "")
                    .trim()
                if (suggestion.isNotBlank()) {
                    suggestions.add(suggestion)
                }
            }
        }

        // Ensure we have exactly 3 suggestions
        return suggestions.take(3).ifEmpty {
            listOf(
                "네, 감사합니다.",
                "확인되었습니다.",
                "자세히 설명해 주시겠어요?"
            )
        }
    }

    private fun extractSpamClassification(response: String): SpamResult {
        val isSpam = response.contains("IS_SPAM: yes", ignoreCase = true)

        val confidenceMatch = Regex("CONFIDENCE:\\s*(\\d+)").find(response)
        val confidence = confidenceMatch?.groupValues?.get(1)?.toFloatOrNull()?.div(100f) ?: 0.5f

        val reasonMatch = Regex("REASON:\\s*(.+?)(?=\\n|$)").find(response)
        val reason = reasonMatch?.groupValues?.get(1)?.trim() ?: "No reason provided"

        return SpamResult(
            isSpam = isSpam,
            confidence = confidence.coerceIn(0f, 1f),
            reason = reason
        )
    }

    private fun extractCategorization(response: String): CategoryResult {
        val groupMatch = Regex("GROUP:\\s*(.+?)(?=\\n|$)").find(response)
        val suggestedGroup = groupMatch?.groupValues?.get(1)?.trim() ?: "General"

        val confidenceMatch = Regex("CONFIDENCE:\\s*(\\d+)").find(response)
        val confidence = confidenceMatch?.groupValues?.get(1)?.toFloatOrNull()?.div(100f) ?: 0.5f

        val reasonMatch = Regex("REASON:\\s*(.+?)(?=\\n|$)").find(response)
        val reason = reasonMatch?.groupValues?.get(1)?.trim() ?: "Based on conversation patterns"

        return CategoryResult(
            suggestedGroup = suggestedGroup,
            confidence = confidence.coerceIn(0f, 1f),
            reason = reason
        )
    }

    companion object {
        private const val SYSTEM_PROMPT = """
            You are a helpful AI assistant for BizConnect, a Korean business messaging platform.
            Your role is to help with message summarization, reply suggestions, message generation, spam classification, and customer categorization.
            Always respond in Korean when appropriate, maintain professional business tone, and be concise and practical.
            For structured responses, follow the exact format requested.
        """
    }
}

data class SpamResult(
    val isSpam: Boolean,
    val confidence: Float,
    val reason: String
)

data class CategoryResult(
    val suggestedGroup: String,
    val confidence: Float,
    val reason: String
)

class AiServiceException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
