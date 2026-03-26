package com.bizconnect.v2.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.preferences.AppPreferences
import com.bizconnect.v2.data.remote.TokenManager
import com.bizconnect.v2.data.remote.auth.AuthApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authApiService: AuthApiService,
    private val appPreferences: AppPreferences,
    private val tokenManager: TokenManager
) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var isLoggedIn by mutableStateOf(appPreferences.isLoggedIn())
        private set

    var isOfflineMode by mutableStateOf(false)
        private set

    fun clearError() {
        error = null
    }

    fun login(phone: String, password: String, onSuccess: () -> Unit) {
        if (phone.isBlank()) {
            error = "전화번호를 입력해주세요"
            return
        }
        if (password.isBlank()) {
            error = "비밀번호를 입력해주세요"
            return
        }

        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val result = authApiService.login(phone, password)
                if (result.success) {
                    result.token?.let { appPreferences.setAccessToken(it) }
                    result.refreshToken?.let { appPreferences.setRefreshToken(it) }
                    result.userId?.let { appPreferences.setUserId(it) }
                    isLoggedIn = true
                    isOfflineMode = result.offline
                    uploadFcmToken()
                    fetchServerConfig()
                    onSuccess()
                } else {
                    error = result.error ?: "로그인에 실패했습니다"
                }
            } catch (e: Exception) {
                error = "오류가 발생했습니다: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun signup(
        name: String,
        phone: String,
        email: String,
        password: String,
        passwordConfirm: String,
        onSuccess: () -> Unit
    ) {
        if (name.isBlank()) {
            error = "이름을 입력해주세요"
            return
        }
        if (phone.isBlank()) {
            error = "전화번호를 입력해주세요"
            return
        }
        if (email.isBlank()) {
            error = "이메일을 입력해주세요"
            return
        }
        if (password.isBlank()) {
            error = "비밀번호를 입력해주세요"
            return
        }
        if (password.length < 4) {
            error = "비밀번호는 4자 이상이어야 합니다"
            return
        }
        if (password != passwordConfirm) {
            error = "비밀번호가 일치하지 않습니다"
            return
        }

        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val result = authApiService.signup(name, phone, email, password)
                if (result.success) {
                    result.token?.let { appPreferences.setAccessToken(it) }
                    result.refreshToken?.let { appPreferences.setRefreshToken(it) }
                    result.userId?.let { appPreferences.setUserId(it) }
                    isLoggedIn = true
                    isOfflineMode = result.offline
                    // 가입 후 자동으로 FCM 토큰 등록 + 설정 동기화
                    uploadFcmToken()
                    fetchServerConfig()
                    onSuccess()
                } else {
                    error = result.error ?: "회원가입에 실패했습니다"
                }
            } catch (e: Exception) {
                error = "오류가 발생했습니다: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun logout() {
        appPreferences.logout()
        isLoggedIn = false
        isOfflineMode = false
    }

    private fun uploadFcmToken() {
        viewModelScope.launch {
            try {
                val fcmToken = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                appPreferences.saveFcmToken(fcmToken)
                val body = org.json.JSONObject().put("token", fcmToken).toString()
                val request = okhttp3.Request.Builder()
                    .url("https://sm.on1.kr/api/fcm/token")
                    .put(okhttp3.RequestBody.create("application/json".toMediaType(), body))
                tokenManager.executeAuthenticated(request).close()
            } catch (_: Exception) { }
        }
    }

    /**
     * 서버에서 AI API 키 등 설정을 받아와 로컬에 저장
     */
    private fun fetchServerConfig() {
        viewModelScope.launch {
            try {
                val config = authApiService.fetchConfig()
                config["deepseek_api_key"]?.let { key ->
                    if (key.isNotBlank()) appPreferences.setDeepSeekApiKey(key)
                }
                // role도 저장
                val request = okhttp3.Request.Builder()
                    .url("https://sm.on1.kr/api/user/me")
                    .get()
                val resp = tokenManager.executeAuthenticated(request)
                val body = resp.body?.string() ?: ""
                resp.close()
                val json = org.json.JSONObject(body)
                json.optString("role", "user").let { appPreferences.setUserRole(it) }
                json.optString("tier", "free").let { appPreferences.setSubscriptionTier(it) }
            } catch (_: Exception) { }
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it, null) }
            addOnFailureListener { cont.resume(null as T, null) }
        }
    }
}
