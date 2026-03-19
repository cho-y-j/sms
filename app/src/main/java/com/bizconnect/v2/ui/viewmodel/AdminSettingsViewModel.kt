package com.bizconnect.v2.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminSettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    data class UiState(
        val smsCost: String = "9.8",
        val lmsCost: String = "29.0",
        val mmsCost: String = "63.0",
        val freeDailyLimit: String = "50",
        val paidDailyLimit: String = "150",
        val subscriptionTier: String = "free",
        val creditBalance: Double = 0.0,
        val saveSuccess: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    smsCost = appPreferences.getSmsCost().toString(),
                    lmsCost = appPreferences.getLmsCost().toString(),
                    mmsCost = appPreferences.getMmsCost().toString(),
                    freeDailyLimit = appPreferences.getDailyLimitCount().toString(),
                    paidDailyLimit = appPreferences.getPaidTierDailyLimit().toString(),
                    subscriptionTier = appPreferences.getSubscriptionTier(),
                    creditBalance = appPreferences.getCreditBalance()
                )
            }
        }
    }

    fun updateSmsCost(value: String) {
        _uiState.update { it.copy(smsCost = value, saveSuccess = false) }
    }

    fun updateLmsCost(value: String) {
        _uiState.update { it.copy(lmsCost = value, saveSuccess = false) }
    }

    fun updateMmsCost(value: String) {
        _uiState.update { it.copy(mmsCost = value, saveSuccess = false) }
    }

    fun updateFreeDailyLimit(value: String) {
        _uiState.update { it.copy(freeDailyLimit = value, saveSuccess = false) }
    }

    fun updatePaidDailyLimit(value: String) {
        _uiState.update { it.copy(paidDailyLimit = value, saveSuccess = false) }
    }

    fun resetBalance() {
        viewModelScope.launch(Dispatchers.IO) {
            appPreferences.setCreditBalance(0.0)
            _uiState.update { it.copy(creditBalance = 0.0) }
        }
    }

    fun addTestBalance() {
        viewModelScope.launch(Dispatchers.IO) {
            val newBalance = _uiState.value.creditBalance + 10000.0
            appPreferences.setCreditBalance(newBalance)
            _uiState.update { it.copy(creditBalance = newBalance) }
        }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            appPreferences.setSmsCost(state.smsCost.toDoubleOrNull() ?: 9.8)
            appPreferences.setLmsCost(state.lmsCost.toDoubleOrNull() ?: 29.0)
            appPreferences.setMmsCost(state.mmsCost.toDoubleOrNull() ?: 63.0)
            appPreferences.setDailyLimitCount(state.freeDailyLimit.toIntOrNull() ?: 50)
            appPreferences.setPaidTierDailyLimit(state.paidDailyLimit.toIntOrNull() ?: 150)
            _uiState.update { it.copy(saveSuccess = true) }
        }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
