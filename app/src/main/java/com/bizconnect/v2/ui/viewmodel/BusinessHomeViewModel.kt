package com.bizconnect.v2.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.DailyLimitDao
import com.bizconnect.v2.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class BusinessHomeViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val dailyLimitDao: DailyLimitDao
) : ViewModel() {

    var sentToday by mutableIntStateOf(0)
        private set

    var dailyLimit by mutableIntStateOf(50)
        private set

    var creditBalance by mutableDoubleStateOf(0.0)
        private set

    var subscriptionTier by mutableStateOf("free")
        private set

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        // Invalidate cache to pick up server-synced values
        appPreferences.invalidateCache()

        // Load preferences synchronously (they use runBlocking internally)
        subscriptionTier = appPreferences.getSubscriptionTier()
        creditBalance = appPreferences.getCreditBalance()
        dailyLimit = if (subscriptionTier == "paid" || subscriptionTier == "premium") {
            appPreferences.getPaidTierDailyLimit()
        } else {
            appPreferences.getDailyLimitCount()
        }

        // Load sent count from DB
        viewModelScope.launch {
            val userId = appPreferences.getUserId() ?: "default"
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            sentToday = dailyLimitDao.getSentCount(userId, today) ?: 0
        }
    }
}
