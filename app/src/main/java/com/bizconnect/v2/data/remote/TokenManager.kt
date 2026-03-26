package com.bizconnect.v2.data.remote

import android.util.Log
import com.bizconnect.v2.data.preferences.AppPreferences
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val appPreferences: AppPreferences
) {
    private val TAG = "TokenManager"
    private val BASE_URL = "https://sm.on1.kr"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Get the OkHttpClient instance (for callers that need it for non-authenticated calls).
     */
    fun getClient(): OkHttpClient = client

    /**
     * Get a valid access token from preferences.
     */
    fun getValidToken(): String? {
        return appPreferences.getAccessToken()
    }

    /**
     * Execute an authenticated request. If 401, refresh token and retry once.
     * Accepts a Request.Builder so the caller doesn't set Authorization themselves.
     */
    @Synchronized
    fun executeAuthenticated(requestBuilder: Request.Builder): Response {
        // 항상 캐시 무효화 후 토큰 읽기 (프로세스간 동기화)
        appPreferences.invalidateCache()
        var token = getValidToken()
        if (token.isNullOrBlank()) {
            throw TokenExpiredException("No access token available. Please re-login.")
        }

        val request = requestBuilder
            .header("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()

        // If 401, try to refresh token and retry
        if (response.code == 401) {
            response.close()
            Log.d(TAG, "Access token expired (401), attempting refresh...")

            val newToken = refreshToken()
            if (newToken != null) {
                Log.d(TAG, "Token refreshed successfully, retrying request")
                val retryRequest = requestBuilder
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return client.newCall(retryRequest).execute()
            } else {
                Log.e(TAG, "Token refresh failed, user must re-login")
                throw TokenExpiredException("Token expired and refresh failed. Please re-login.")
            }
        }

        return response
    }

    /**
     * Refresh the access token using the stored refresh token.
     * Returns the new access token or null if refresh fails.
     */
    private fun refreshToken(): String? {
        val refreshToken = appPreferences.getRefreshToken()
        if (refreshToken.isNullOrBlank()) {
            Log.e(TAG, "No refresh token available")
            return null
        }

        return try {
            val body = JSONObject().put("refreshToken", refreshToken).toString()
            val request = Request.Builder()
                .url("$BASE_URL/api/auth/refresh")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.close()

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val newAccessToken = json.optString("accessToken", json.optString("token", ""))
                val newRefreshToken = json.optString("refreshToken", "")

                if (newAccessToken.isNotBlank()) {
                    appPreferences.setAccessToken(newAccessToken)
                    if (newRefreshToken.isNotBlank()) {
                        appPreferences.setRefreshToken(newRefreshToken)
                    }
                    appPreferences.invalidateCache()
                    Log.d(TAG, "Tokens saved successfully after refresh")
                    return newAccessToken
                }
            }

            Log.e(TAG, "Refresh failed with HTTP ${response.code}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Refresh error: ${e.message}")
            null
        }
    }

    /**
     * Custom exception for expired/invalid tokens.
     */
    class TokenExpiredException(message: String) : Exception(message)
}
