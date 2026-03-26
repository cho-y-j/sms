package com.bizconnect.v2.data.remote.auth

import com.bizconnect.v2.data.preferences.AppPreferences
import com.bizconnect.v2.data.remote.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthApiService @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val appPreferences: AppPreferences,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val BASE_URL = "https://sm.on1.kr"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Fetch app config (API keys, settings) from server.
     * Called on app startup to get DeepSeek key etc.
     */
    suspend fun fetchConfig(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            if (appPreferences.getAccessToken() == null) return@withContext emptyMap()
            val requestBuilder = Request.Builder()
                .url("$BASE_URL/api/admin/config")
                .get()
            val response = tokenManager.executeAuthenticated(requestBuilder)
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                response.close()
                val configs = mutableMapOf<String, String>()
                val arr = org.json.JSONArray(body)
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    configs[item.getString("key")] = item.getString("value")
                }
                configs
            } else {
                response.close()
                emptyMap()
            }
        } catch (_: Exception) { emptyMap() }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class AuthResult(
        val success: Boolean,
        val token: String? = null,
        val refreshToken: String? = null,
        val userId: String? = null,
        val error: String? = null,
        val offline: Boolean = false
    )

    suspend fun login(phone: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            // Android ID를 deviceToken으로 사용 (기기 인증 자동 통과)
            val androidId = android.provider.Settings.Secure.getString(
                appContext.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "app_${System.currentTimeMillis()}"

            val requestBody = JSONObject().apply {
                put("phone", phone)
                put("password", password)
                put("deviceToken", "app_$androidId")
                put("deviceName", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                put("platform", "app")
            }

            val request = Request.Builder()
                .url("$BASE_URL/api/auth/login")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val token = json.optString("token", json.optString("accessToken", ""))
                val refreshToken = json.optString("refreshToken", "")
                val userId = json.optString("userId", json.optString("id", ""))
                AuthResult(
                    success = true,
                    token = token,
                    refreshToken = refreshToken.ifEmpty { null },
                    userId = userId
                )
            } else {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optString("error", errJson.optString("message", "로그인에 실패했습니다"))
                } catch (_: Exception) {
                    "로그인에 실패했습니다 (${response.code})"
                }
                AuthResult(success = false, error = errorMsg)
            }
        } catch (e: Exception) {
            // Server unreachable - offline mode
            val offlineToken = "offline_${UUID.randomUUID()}"
            val offlineUserId = "offline_${phone.takeLast(4)}"
            AuthResult(
                success = true,
                token = offlineToken,
                userId = offlineUserId,
                offline = true
            )
        }
    }

    suspend fun signup(
        name: String,
        phone: String,
        email: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("name", name)
                put("phone", phone)
                put("email", email)
                put("password", password)
            }

            val request = Request.Builder()
                .url("$BASE_URL/api/auth/register")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val token = json.optString("token", json.optString("accessToken", ""))
                val refreshToken = json.optString("refreshToken", "")
                val userId = json.optString("userId", json.optString("id", ""))
                AuthResult(
                    success = true,
                    token = token,
                    refreshToken = refreshToken.ifEmpty { null },
                    userId = userId
                )
            } else {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optString("error", errJson.optString("message", "회원가입에 실패했습니다"))
                } catch (_: Exception) {
                    "회원가입에 실패했습니다 (${response.code})"
                }
                AuthResult(success = false, error = errorMsg)
            }
        } catch (e: Exception) {
            // Server unreachable - offline mode
            val offlineToken = "offline_${UUID.randomUUID()}"
            val offlineUserId = "offline_${phone.takeLast(4)}"
            AuthResult(
                success = true,
                token = offlineToken,
                userId = offlineUserId,
                offline = true
            )
        }
    }
}
