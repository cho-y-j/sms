package com.bizconnect.v2.data.remote.spam

import android.util.Log
import com.bizconnect.v2.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpamApiService @Inject constructor(
    private val appPreferences: AppPreferences
) {
    companion object {
        private const val TAG = "SpamApiService"
        private const val APICK_URL = "https://apick.app/rest/check_spam_number"
        private const val IPQS_BASE_URL = "https://ipqualityscore.com/api/json/phone"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    data class SpamCheckResult(
        val isSpam: Boolean,
        val spamScore: Int, // 0-100
        val spamType: String?, // "보이스피싱", "스팸문자", etc
        val reportCount: Int,
        val source: String // "apick", "ipqs", "none"
    )

    /**
     * Check Korean number via APick API
     */
    suspend fun checkApick(phoneNumber: String): SpamCheckResult? {
        val apiKey = appPreferences.getApickApiKey()
        if (apiKey.isBlank()) {
            Log.d(TAG, "APick API key not configured")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("phoneNumber", phoneNumber)
                }

                val request = Request.Builder()
                    .url(APICK_URL)
                    .addHeader("CL_AUTH_KEY", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    Log.w(TAG, "APick API error: ${response.code}")
                    return@withContext null
                }

                val json = JSONObject(responseBody)
                val result = json.optString("result", "")

                if (result != "success") {
                    Log.w(TAG, "APick API returned non-success: $result")
                    return@withContext null
                }

                val data = json.optJSONObject("data")
                if (data == null) {
                    // No spam data found - number is clean
                    return@withContext SpamCheckResult(
                        isSpam = false,
                        spamScore = 0,
                        spamType = null,
                        reportCount = 0,
                        source = "apick"
                    )
                }

                val spamType: String? = if (data.has("spamType")) data.getString("spamType") else null
                val reportCount = data.optInt("reportCount", 0)

                // Calculate spam score based on report count
                val spamScore = when {
                    reportCount >= 20 -> 100
                    reportCount >= 10 -> 80
                    reportCount >= 5 -> 60
                    reportCount >= 2 -> 40
                    reportCount >= 1 -> 20
                    else -> 0
                }

                SpamCheckResult(
                    isSpam = reportCount > 0,
                    spamScore = spamScore,
                    spamType = spamType,
                    reportCount = reportCount,
                    source = "apick"
                )
            } catch (e: Exception) {
                Log.e(TAG, "APick API call failed", e)
                null
            }
        }
    }

    /**
     * Check international number via IPQS API
     */
    suspend fun checkIpqs(phoneNumber: String): SpamCheckResult? {
        val apiKey = appPreferences.getIpqsApiKey()
        if (apiKey.isBlank()) {
            Log.d(TAG, "IPQS API key not configured")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = "$IPQS_BASE_URL/$apiKey/$phoneNumber"

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    Log.w(TAG, "IPQS API error: ${response.code}")
                    return@withContext null
                }

                val json = JSONObject(responseBody)
                val success = json.optBoolean("success", false)

                if (!success) {
                    Log.w(TAG, "IPQS API returned failure")
                    return@withContext null
                }

                val fraudScore = json.optInt("fraud_score", 0)
                val spamScore = json.optInt("spam_score", 0)
                val valid = json.optBoolean("valid", true)
                val lineType = json.optString("line_type", "unknown")

                // Use the higher of fraud_score and spam_score
                val combinedScore = maxOf(fraudScore, spamScore)
                val isSpam = combinedScore >= 75 || !valid

                val spamType = when {
                    fraudScore >= 85 -> "사기 의심"
                    spamScore >= 85 -> "스팸"
                    fraudScore >= 75 -> "사기 주의"
                    spamScore >= 75 -> "스팸 주의"
                    !valid -> "유효하지 않은 번호"
                    else -> null
                }

                SpamCheckResult(
                    isSpam = isSpam,
                    spamScore = combinedScore,
                    spamType = spamType,
                    reportCount = 0, // IPQS doesn't provide report count
                    source = "ipqs"
                )
            } catch (e: Exception) {
                Log.e(TAG, "IPQS API call failed", e)
                null
            }
        }
    }

    /**
     * Combined check: APick for Korean numbers, IPQS for international.
     * Gracefully handles missing API keys.
     */
    suspend fun checkNumber(phoneNumber: String): SpamCheckResult {
        if (!appPreferences.getSpamApiEnabled()) {
            return SpamCheckResult(
                isSpam = false,
                spamScore = 0,
                spamType = null,
                reportCount = 0,
                source = "none"
            )
        }

        val cleaned = phoneNumber.trim().replace("[\\s\\-()]".toRegex(), "")

        // Determine if Korean number
        val isKorean = cleaned.startsWith("+82") ||
                cleaned.startsWith("0082") ||
                (cleaned.startsWith("0") && cleaned.length >= 9 && !cleaned.startsWith("00"))

        // Try APick for Korean numbers
        if (isKorean) {
            val apickResult = checkApick(cleaned)
            if (apickResult != null) return apickResult
        }

        // Try IPQS for all numbers (fallback for Korean, primary for international)
        val ipqsResult = checkIpqs(cleaned)
        if (ipqsResult != null) return ipqsResult

        // No API available
        return SpamCheckResult(
            isSpam = false,
            spamScore = 0,
            spamType = null,
            reportCount = 0,
            source = "none"
        )
    }
}
