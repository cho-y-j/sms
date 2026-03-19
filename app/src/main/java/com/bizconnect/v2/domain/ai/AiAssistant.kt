package com.bizconnect.v2.domain.ai

import com.bizconnect.v2.data.local.db.dao.AiUsageDao
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.dao.MessageDao
import com.bizconnect.v2.data.local.db.entity.AiUsageEntity
import com.bizconnect.v2.data.preferences.AppPreferences
import com.bizconnect.v2.data.remote.ai.DeepSeekApiService
import com.bizconnect.v2.data.remote.ai.DeepSeekApiService.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiAssistant @Inject constructor(
    private val deepSeekApi: DeepSeekApiService,
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val aiUsageDao: AiUsageDao,
    private val appPreferences: AppPreferences
) {
    companion object {
        private const val SYSTEM_PROMPT = "당신은 한국어 비즈니스 문자 메시지 도우미입니다. 간결하고 자연스러운 한국어로 응답하세요."
    }

    private suspend fun getConversationContext(
        threadId: Long,
        limit: Int = 30,
        sinceTimestamp: Long? = null
    ): List<ChatMessage> {
        val allMessages = messageDao.getMessagesByThreadDirect(threadId)
        val filtered = if (sinceTimestamp != null) {
            allMessages.filter { it.timestamp > sinceTimestamp }
        } else {
            allMessages.takeLast(limit)
        }
        return filtered.map { msg ->
            ChatMessage(
                role = if (msg.type == 1) "user" else "assistant",
                content = msg.body ?: ""
            )
        }
    }

    suspend fun summarizeConversation(
        threadId: Long,
        previousSummary: String? = null,
        sinceTimestamp: Long? = null
    ): String {
        val useIncremental = !previousSummary.isNullOrBlank() && sinceTimestamp != null
        val context = getConversationContext(
            threadId,
            sinceTimestamp = if (useIncremental) sinceTimestamp else null
        )

        if (useIncremental && context.isEmpty()) {
            // No new messages since last summary
            return previousSummary!!
        }

        val messages = mutableListOf(
            ChatMessage("system", SYSTEM_PROMPT),
        )

        if (useIncremental) {
            messages.add(
                ChatMessage("user", "이전 요약: $previousSummary")
            )
            messages.addAll(context)
            messages.add(
                ChatMessage(
                    "user",
                    "이전 요약과 새로운 메시지들을 합쳐서 3줄 이내로 통합 요약해 주세요."
                )
            )
        } else {
            messages.addAll(context)
            messages.add(
                ChatMessage(
                    "user",
                    "위 대화를 3줄 이내로 핵심만 요약해 주세요."
                )
            )
        }

        val response = deepSeekApi.chat(messages, maxTokens = 300)
        trackUsage(response.tokensUsed)
        return response.content
    }

    suspend fun searchInConversation(threadId: Long, query: String): String {
        val context = getConversationContext(threadId)
        val messages = mutableListOf(
            ChatMessage("system", SYSTEM_PROMPT),
        )
        messages.addAll(context)
        messages.add(
            ChatMessage(
                "user",
                "아래 대화에서 '$query' 관련 내용을 찾아 요약해 주세요. 관련 메시지가 없으면 '관련 내용을 찾을 수 없습니다'라고 응답하세요."
            )
        )

        val response = deepSeekApi.chat(messages, maxTokens = 400)
        trackUsage(response.tokensUsed)
        return response.content
    }

    suspend fun suggestReplies(threadId: Long): List<String> {
        val context = getConversationContext(threadId)
        val messages = mutableListOf(
            ChatMessage("system", SYSTEM_PROMPT),
        )
        messages.addAll(context)
        messages.add(
            ChatMessage(
                "user",
                "위 대화에 대한 답장을 3개 추천해 주세요. JSON 배열로만 응답하세요. 예: [\"답장1\",\"답장2\",\"답장3\"]"
            )
        )

        val response = deepSeekApi.chat(messages, maxTokens = 400)
        trackUsage(response.tokensUsed)

        return try {
            val jsonArray = JSONArray(response.content.trim())
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            listOf(response.content)
        }
    }

    suspend fun generateMessage(prompt: String, recipientInfo: String? = null): String {
        val systemMsg = if (recipientInfo != null) {
            "$SYSTEM_PROMPT 수신자 정보: $recipientInfo"
        } else {
            SYSTEM_PROMPT
        }

        val messages = listOf(
            ChatMessage("system", systemMsg),
            ChatMessage("user", prompt)
        )

        val response = deepSeekApi.chat(messages, maxTokens = 500)
        trackUsage(response.tokensUsed)
        return response.content
    }

    data class EmotionResult(val emotion: String, val emoji: String, val reason: String)

    suspend fun analyzeEmotion(threadId: Long): EmotionResult {
        val context = getConversationContext(threadId, limit = 10)
        val messages = mutableListOf(
            ChatMessage("system", SYSTEM_PROMPT),
        )
        messages.addAll(context)
        messages.add(
            ChatMessage(
                "user",
                "위 대화의 상대방 감정을 분석해 주세요. JSON으로만 응답하세요. 형식: {\"emotion\":\"긍정/보통/불만\",\"emoji\":\"😊\",\"reason\":\"이유\"}"
            )
        )

        val response = deepSeekApi.chat(messages, maxTokens = 200, temperature = 0f)
        trackUsage(response.tokensUsed)

        return try {
            val json = JSONObject(response.content.trim())
            EmotionResult(
                emotion = json.getString("emotion"),
                emoji = json.getString("emoji"),
                reason = json.getString("reason")
            )
        } catch (e: Exception) {
            EmotionResult("보통", "😐", response.content)
        }
    }

    suspend fun convertTone(message: String, tone: String): String {
        val messages = listOf(
            ChatMessage("system", SYSTEM_PROMPT),
            ChatMessage(
                "user",
                "다음 메시지를 '$tone' 톤으로 변환해 주세요. 변환된 메시지만 응답하세요.\n\n원본: $message"
            )
        )

        val response = deepSeekApi.chat(messages, maxTokens = 300)
        trackUsage(response.tokensUsed)
        return response.content
    }

    data class AppointmentInfo(
        val date: String,
        val time: String,
        val location: String?,
        val description: String
    )

    suspend fun extractAppointment(threadId: Long): AppointmentInfo? {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd EEEE", java.util.Locale.KOREA).format(java.util.Date())
        val context = getConversationContext(threadId)
        val messages = mutableListOf(
            ChatMessage("system", "오늘은 ${today}입니다. $SYSTEM_PROMPT"),
        )
        messages.addAll(context)
        messages.add(
            ChatMessage(
                "user",
                "위 대화에서 약속/일정/면접 등 시간이 정해진 정보를 추출해 주세요. 약속이 없으면 \"없음\"이라고만 응답하세요. 약속이 있으면 JSON으로만 응답하세요. \"다음주\", \"내일\", \"모레\" 같은 상대적 표현은 오늘 기준으로 실제 날짜로 변환하세요. 형식: {\"date\":\"2026-03-24\",\"time\":\"14:00\",\"location\":\"장소 또는 null\",\"description\":\"설명\"}"
            )
        )

        val response = deepSeekApi.chat(messages, maxTokens = 200, temperature = 0f)
        trackUsage(response.tokensUsed)

        val trimmed = response.content.trim()
        // JSON이 포함된 응답에서 추출
        val jsonStr = if (trimmed.contains("{")) {
            trimmed.substring(trimmed.indexOf("{"), trimmed.lastIndexOf("}") + 1)
        } else {
            return null
        }

        return try {
            val json = JSONObject(jsonStr)
            AppointmentInfo(
                date = json.getString("date"),
                time = json.optString("time", "00:00"),
                location = if (json.has("location") && !json.isNull("location")) json.getString("location") else null,
                description = json.optString("description", "약속")
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract appointment from a single message text (for incoming SMS auto-detect).
     */
    suspend fun extractAppointmentFromText(text: String): AppointmentInfo? {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd EEEE", java.util.Locale.KOREA).format(java.util.Date())
        val messages = listOf(
            ChatMessage(
                "system",
                "오늘은 ${today}입니다. 문자 메시지에서 약속/일정 정보를 추출하세요. 약속이 없으면 \"없음\"이라고만 응답하세요. 약속이 있으면 JSON으로만 응답하세요. \"다음주 화요일\" 같은 상대적 표현은 실제 날짜로 변환하세요. 형식: {\"date\":\"2026-03-24\",\"time\":\"14:00\",\"location\":\"장소 또는 null\",\"description\":\"약속 설명\"}"
            ),
            ChatMessage("user", text)
        )

        val response = deepSeekApi.chat(messages, maxTokens = 200, temperature = 0f)
        trackUsage(response.tokensUsed)

        val trimmed = response.content.trim()
        val jsonStr = if (trimmed.contains("{")) {
            trimmed.substring(trimmed.indexOf("{"), trimmed.lastIndexOf("}") + 1)
        } else { return null }

        return try {
            val json = org.json.JSONObject(jsonStr)
            AppointmentInfo(
                date = json.getString("date"),
                time = json.optString("time", "00:00"),
                location = if (json.has("location") && !json.isNull("location")) json.getString("location") else null,
                description = json.optString("description", "약속")
            )
        } catch (e: Exception) { null }
    }

    data class SpamAiResult(val isSpam: Boolean, val confidence: Float, val reason: String)

    suspend fun detectSpam(sender: String, body: String): SpamAiResult {
        val messages = listOf(
            ChatMessage(
                "system",
                "당신은 스팸 메시지 판별 전문가입니다. JSON으로만 응답하세요."
            ),
            ChatMessage(
                "user",
                "발신자: $sender\n내용: $body\n\n이 메시지가 스팸인지 판별해 주세요. 형식: {\"isSpam\":true/false,\"confidence\":0.0~1.0,\"reason\":\"이유\"}"
            )
        )

        val response = deepSeekApi.chat(messages, maxTokens = 200, temperature = 0.3f)
        trackUsage(response.tokensUsed)

        return try {
            val json = JSONObject(response.content.trim())
            SpamAiResult(
                isSpam = json.getBoolean("isSpam"),
                confidence = json.getDouble("confidence").toFloat(),
                reason = json.getString("reason")
            )
        } catch (e: Exception) {
            SpamAiResult(isSpam = false, confidence = 0f, reason = response.content)
        }
    }

    private suspend fun trackUsage(tokensUsed: Int) {
        val today = LocalDate.now().toString()
        val existing = aiUsageDao.getByDate(today)
        if (existing != null) {
            aiUsageDao.insert(
                existing.copy(
                    tokensUsed = existing.tokensUsed + tokensUsed,
                    requestCount = existing.requestCount + 1,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            aiUsageDao.insert(
                AiUsageEntity(
                    date = today,
                    tokensUsed = tokensUsed,
                    requestCount = 1
                )
            )
        }
    }
}
