package com.bizconnect.v2.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.preferences.AppPreferences
import com.bizconnect.v2.data.remote.auth.AuthApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authApiService: AuthApiService,
    private val appPreferences: AppPreferences
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
                    result.userId?.let { appPreferences.setUserId(it) }
                    isLoggedIn = true
                    isOfflineMode = result.offline
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
        if (password.length < 6) {
            error = "비밀번호는 6자 이상이어야 합니다"
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
                    result.userId?.let { appPreferences.setUserId(it) }
                    isLoggedIn = true
                    isOfflineMode = result.offline
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
}
