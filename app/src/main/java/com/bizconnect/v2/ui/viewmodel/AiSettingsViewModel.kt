package com.bizconnect.v2.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.AiUsageDao
import com.bizconnect.v2.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val aiUsageDao: AiUsageDao
) : ViewModel() {

    data class UiState(
        val aiEnabled: Boolean = true,
        val deepSeekApiKey: String = "",
        val apickApiKey: String = "",
        val ipqsApiKey: String = "",
        val systemPrompt: String = "",
        val spamApiEnabled: Boolean = false,
        val todayTokens: Int = 0,
        val monthTokens: Int = 0,
        val aiUsageToday: Int = 0,
        val aiDailyLimit: Int = 10,
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    init {
        loadSettings()
        loadUsageData()
    }

    private fun loadSettings() {
        _uiState.update {
            it.copy(
                aiEnabled = appPreferences.getAiEnabled(),
                deepSeekApiKey = appPreferences.getDeepSeekApiKey(),
                apickApiKey = appPreferences.getApickApiKey(),
                ipqsApiKey = appPreferences.getIpqsApiKey(),
                systemPrompt = appPreferences.getAiSystemPrompt(),
                spamApiEnabled = appPreferences.getSpamApiEnabled(),
                aiUsageToday = appPreferences.getAiUsageToday(),
                aiDailyLimit = appPreferences.getAiDailyLimit(),
                isLoading = false
            )
        }
    }

    private fun loadUsageData() {
        viewModelScope.launch {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val monthStart = LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

            val todayTokens = aiUsageDao.getTodayTokens(today) ?: 0
            val monthTokens = aiUsageDao.getTotalTokensSince(monthStart) ?: 0

            _uiState.update {
                it.copy(
                    todayTokens = todayTokens,
                    monthTokens = monthTokens
                )
            }
        }
    }

    fun toggleAiEnabled(enabled: Boolean) {
        _uiState.update { it.copy(aiEnabled = enabled) }
        appPreferences.setAiEnabled(enabled)
    }

    fun toggleSpamApiEnabled(enabled: Boolean) {
        _uiState.update { it.copy(spamApiEnabled = enabled) }
        appPreferences.setSpamApiEnabled(enabled)
    }

    fun updateDeepSeekApiKey(key: String) {
        _uiState.update { it.copy(deepSeekApiKey = key) }
        debounceSave { appPreferences.setDeepSeekApiKey(key) }
    }

    fun updateApickApiKey(key: String) {
        _uiState.update { it.copy(apickApiKey = key) }
        debounceSave { appPreferences.setApickApiKey(key) }
    }

    fun updateIpqsApiKey(key: String) {
        _uiState.update { it.copy(ipqsApiKey = key) }
        debounceSave { appPreferences.setIpqsApiKey(key) }
    }

    fun updateSystemPrompt(prompt: String) {
        _uiState.update { it.copy(systemPrompt = prompt) }
        debounceSave { appPreferences.setAiSystemPrompt(prompt) }
    }

    private fun debounceSave(action: () -> Unit) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            action()
        }
    }
}
