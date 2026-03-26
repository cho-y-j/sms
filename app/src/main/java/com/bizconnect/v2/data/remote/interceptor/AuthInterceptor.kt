package com.bizconnect.v2.data.remote.interceptor

import android.util.Log
import com.bizconnect.v2.data.preferences.AppPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject

/**
 * Interceptor that adds JWT Bearer token to all requests.
 * Handles 401 responses and manages token refresh.
 */
class AuthInterceptor @Inject constructor(
    private val appPreferences: AppPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for login/register endpoints
        if (isAuthEndpoint(originalRequest)) {
            return chain.proceed(originalRequest)
        }

        // Add token to request
        val tokenizedRequest = addTokenToRequest(originalRequest)
        var response = chain.proceed(tokenizedRequest)

        // Handle 401 Unauthorized - attempt token refresh
        if (response.code == 401) {
            response.close()
            val refreshedRequest = refreshTokenAndRetry(originalRequest, chain)
            return refreshedRequest
        }

        return response
    }

    private fun isAuthEndpoint(request: Request): Boolean {
        val path = request.url.encodedPath
        return path.contains("/auth/login") ||
                path.contains("/auth/register") ||
                path.contains("/auth/refresh")
    }

    private fun addTokenToRequest(originalRequest: Request): Request {
        return runBlocking {
            val token = appPreferences.getAccessToken()
            if (token != null) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }
        }
    }

    private fun refreshTokenAndRetry(
        originalRequest: Request,
        chain: Interceptor.Chain
    ): Response = runBlocking {
        try {
            val refreshToken = appPreferences.getRefreshToken()
            if (refreshToken == null) {
                // No refresh token, clear auth and return 401
                appPreferences.clearAuth()
                return@runBlocking chain.proceed(originalRequest)
            }

            // Attempt refresh token call
            val refreshUrl = originalRequest.url.newBuilder()
                .encodedPath("/api/auth/refresh")
                .build()

            val refreshBody = """{"refreshToken":"$refreshToken"}""".toByteArray()
            val refreshRequest = Request.Builder()
                .url(refreshUrl)
                .post(okhttp3.RequestBody.create(null, refreshBody))
                .build()

            val refreshResponse = chain.proceed(refreshRequest)

            if (refreshResponse.isSuccessful) {
                // Token refresh successful, parse new tokens
                val responseBody = refreshResponse.body?.string()
                if (responseBody != null) {
                    // Extract access token and refresh token from response and save them
                    val newAccessToken = parseAccessToken(responseBody)
                    val newRefreshToken = parseRefreshToken(responseBody)
                    if (newAccessToken != null) {
                        appPreferences.saveAccessToken(newAccessToken)
                        if (newRefreshToken != null) {
                            appPreferences.setRefreshToken(newRefreshToken)
                        }
                        appPreferences.invalidateCache()

                        // Retry original request with new token
                        val retryRequest = originalRequest.newBuilder()
                            .header("Authorization", "Bearer $newAccessToken")
                            .build()

                        return@runBlocking chain.proceed(retryRequest)
                    }
                }
            }

            // Token refresh failed, clear auth
            appPreferences.clearAuth()
            refreshResponse.close()
            return@runBlocking chain.proceed(originalRequest)
        } catch (e: Exception) {
            Log.e("AuthInterceptor", "Token refresh failed", e)
            appPreferences.clearAuth()
            return@runBlocking chain.proceed(originalRequest)
        }
    }

    private fun parseAccessToken(responseBody: String): String? {
        return try {
            val pattern = """"accessToken"\s*:\s*"([^"]+)"""".toRegex()
            pattern.find(responseBody)?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e("AuthInterceptor", "Failed to parse access token", e)
            null
        }
    }

    private fun parseRefreshToken(responseBody: String): String? {
        return try {
            val pattern = """"refreshToken"\s*:\s*"([^"]+)"""".toRegex()
            pattern.find(responseBody)?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e("AuthInterceptor", "Failed to parse refresh token", e)
            null
        }
    }
}
