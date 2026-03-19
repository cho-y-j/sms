package com.bizconnect.v2.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
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
}
