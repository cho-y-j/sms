package com.bizconnect.v2.data.remote.ai

import com.bizconnect.v2.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepSeekApiService @Inject constructor(
    private val appPreferences: AppPreferences
) {
    companion object {
        private const val BASE_URL = "https://api.deepseek.com/v1/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class ChatMessage(val role: String, val content: String)
    data class ChatResponse(val content: String, val tokensUsed: Int)

    suspend fun chat(
        messages: List<ChatMessage>,
        maxTokens: Int = 500,
        temperature: Float = 0.7f
    ): ChatResponse = withContext(Dispatchers.IO) {
        val apiKey = appPreferences.getDeepSeekApiKey()

        val messagesArray = JSONArray()
        for (msg in messages) {
            messagesArray.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        val requestBody = JSONObject().apply {
            put("model", "deepseek-chat")
            put("messages", messagesArray)
            put("max_tokens", maxTokens)
            put("temperature", temperature.toDouble())
        }

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from DeepSeek API")

        if (!response.isSuccessful) {
            throw Exception("DeepSeek API error ${response.code}: $responseBody")
        }

        val json = JSONObject(responseBody)
        val content = json
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")

        val tokensUsed = json
            .getJSONObject("usage")
            .getInt("total_tokens")

        ChatResponse(content = content, tokensUsed = tokensUsed)
    }
}
