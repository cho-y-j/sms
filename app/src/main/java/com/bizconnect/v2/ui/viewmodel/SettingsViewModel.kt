package com.bizconnect.v2.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.bizconnect.v2.data.remote.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.MediaType.Companion.toMediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val dataStore: DataStore<Preferences>,
    private val appPreferences: com.bizconnect.v2.data.preferences.AppPreferences,
    private val tokenManager: TokenManager
) : ViewModel() {

    data class UiState(
        val isDarkMode: Boolean = false,
        val fontScale: Float = 1.0f,
        val notificationsEnabled: Boolean = true,
        val dailyLimit: Int = 499,
        val isAutoApproval: Boolean = false,
        val isDefaultSmsApp: Boolean = false
    )

    private object PreferencesKeys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DAILY_LIMIT = intPreferencesKey("daily_limit")
        val AUTO_APPROVAL = booleanPreferencesKey("auto_approval")
        val IS_DEFAULT_SMS_APP = booleanPreferencesKey("is_default_sms_app")
    }

    val uiState: StateFlow<UiState> = dataStore.data
        .map { preferences ->
            UiState(
                isDarkMode = preferences[PreferencesKeys.DARK_MODE] ?: false,
                fontScale = preferences[PreferencesKeys.FONT_SCALE] ?: 1.0f,
                notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true,
                dailyLimit = preferences[PreferencesKeys.DAILY_LIMIT] ?: 499,
                isAutoApproval = preferences[PreferencesKeys.AUTO_APPROVAL] ?: false,
                isDefaultSmsApp = preferences[PreferencesKeys.IS_DEFAULT_SMS_APP] ?: false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState()
        )

    fun updateDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.DARK_MODE] = enabled
            }
        }
    }

    fun updateFontScale(scale: Float) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.FONT_SCALE] = scale
            }
        }
    }

    fun updateNotifications(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
            }
        }
    }

    fun updateDailyLimit(limit: Int) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.DAILY_LIMIT] = limit
            }
        }
    }

    fun updateAutoApproval(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.AUTO_APPROVAL] = enabled
            }
        }
    }

    fun updateDefaultSmsApp(isDefault: Boolean) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.IS_DEFAULT_SMS_APP] = isDefault
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            appPreferences.clearAuth()
        }
    }

    fun isLoggedIn(): Boolean = appPreferences.isLoggedIn()

    fun getUserName(): String = appPreferences.getUserId()?.let {
        if (it.startsWith("offline_")) "오프라인" else it.take(8) + "..."
    } ?: "알 수 없음"

    fun getUserTier(): String = appPreferences.getSubscriptionTier()

    fun getUserRole(): String = appPreferences.getUserRole()

    fun getAccessToken(): String? = appPreferences.getAccessToken()

    /**
     * Sync phone contacts to server using TokenManager for auto-refresh.
     * Returns a result message string.
     */
    fun syncContactsToServer(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (appPreferences.getAccessToken().isNullOrBlank()) {
                    launch(Dispatchers.Main) { onResult("로그인이 필요합니다") }
                    return@launch
                }

                val cursor = appContext.contentResolver.query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    ), null, null, null
                )

                val contacts = mutableListOf<org.json.JSONObject>()
                val seenPhones = mutableSetOf<String>()
                cursor?.use {
                    while (it.moveToNext()) {
                        val n = it.getString(0) ?: continue
                        val p = it.getString(1)?.replace(Regex("[^0-9+]"), "") ?: continue
                        if (p.length < 10 || seenPhones.contains(p)) continue
                        seenPhones.add(p)
                        contacts.add(org.json.JSONObject().apply {
                            put("name", n); put("phone", p)
                        })
                    }
                }

                val payload = org.json.JSONObject().put("contacts", org.json.JSONArray(contacts))
                val requestBuilder = okhttp3.Request.Builder()
                    .url("https://sm.on1.kr/api/user/contacts/import")
                    .post(okhttp3.RequestBody.create(
                        "application/json".toMediaType(), payload.toString()))
                val resp = tokenManager.executeAuthenticated(requestBuilder)
                val body = resp.body?.string() ?: ""
                resp.close()

                val imported = try { org.json.JSONObject(body).optString("imported", "0") } catch (_: Exception) { "0" }
                val existing = contacts.size - (imported.toIntOrNull() ?: 0)
                val msg = if (imported == "0") "모든 연락처가 이미 동기화되어 있습니다 (${contacts.size}건)"
                    else "신규 ${imported}건 추가 완료 (기존 ${existing}건은 이미 동기화됨)"

                launch(Dispatchers.Main) { onResult(msg) }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onResult("동기화 실패: ${e.message}") }
            }
        }
    }
}
