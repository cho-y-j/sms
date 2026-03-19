package com.bizconnect.v2.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.SpamFilterDao
import com.bizconnect.v2.data.local.db.entity.SpamFilterEntity
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.bizconnect.v2.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import com.bizconnect.v2.data.remote.spam.SpamApiService
import com.bizconnect.v2.domain.engine.SpamStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SpamManagementViewModel @Inject constructor(
    private val spamFilterDao: SpamFilterDao,
    private val appPreferences: AppPreferences,
    private val spamApiService: SpamApiService
) : ViewModel() {

    val blockedNumbers: StateFlow<List<SpamFilterEntity>> = spamFilterDao.getByType("number")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val keywordFilters: StateFlow<List<SpamFilterEntity>> = spamFilterDao.getByType("keyword")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val overseasExceptions: StateFlow<List<SpamFilterEntity>> = spamFilterDao.getByType("overseas_exception")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _aggressiveMode = MutableStateFlow(false)
    val aggressiveMode: StateFlow<Boolean> = _aggressiveMode.asStateFlow()

    private val _overseasBlockEnabled = MutableStateFlow(false)
    val overseasBlockEnabled: StateFlow<Boolean> = _overseasBlockEnabled.asStateFlow()

    private val _blockUnknownEnabled = MutableStateFlow(false)
    val blockUnknownEnabled: StateFlow<Boolean> = _blockUnknownEnabled.asStateFlow()

    private val _contactsOnlyEnabled = MutableStateFlow(false)
    val contactsOnlyEnabled: StateFlow<Boolean> = _contactsOnlyEnabled.asStateFlow()

    private val _block070Enabled = MutableStateFlow(false)
    val block070Enabled: StateFlow<Boolean> = _block070Enabled.asStateFlow()

    private val _spamStats = MutableStateFlow(SpamStats(0, 0, 0, 0))
    val spamStats: StateFlow<SpamStats> = _spamStats.asStateFlow()

    private val _lookupResult = MutableStateFlow<SpamApiService.SpamCheckResult?>(null)
    val lookupResult: StateFlow<SpamApiService.SpamCheckResult?> = _lookupResult.asStateFlow()

    private val _isLookingUp = MutableStateFlow(false)
    val isLookingUp: StateFlow<Boolean> = _isLookingUp.asStateFlow()

    private val _lookupError = MutableStateFlow<String?>(null)
    val lookupError: StateFlow<String?> = _lookupError.asStateFlow()

    init {
        _overseasBlockEnabled.value = appPreferences.getOverseasBlockEnabled()
        viewModelScope.launch {
            val prefs = appPreferences.dataStoreRef.data.first()
            _blockUnknownEnabled.value = prefs[booleanPreferencesKey("block_unknown")] ?: false
            _contactsOnlyEnabled.value = prefs[booleanPreferencesKey("contacts_only")] ?: false
            _block070Enabled.value = prefs[booleanPreferencesKey("block_070")] ?: false
        }
        loadSpamStats()
    }

    fun lookupNumber(phone: String) {
        viewModelScope.launch {
            _isLookingUp.value = true
            _lookupError.value = null
            _lookupResult.value = null
            try {
                val result = spamApiService.checkNumber(phone)
                _lookupResult.value = result
                if (result.source == "none") {
                    _lookupError.value = "API 키가 설정되지 않았거나 조회를 사용할 수 없습니다"
                }
            } catch (e: Exception) {
                _lookupError.value = "조회 중 오류가 발생했습니다: ${e.message}"
            } finally {
                _isLookingUp.value = false
            }
        }
    }

    fun clearLookupResult() {
        _lookupResult.value = null
        _lookupError.value = null
    }

    fun setAggressiveMode(enabled: Boolean) {
        _aggressiveMode.value = enabled
    }

    fun toggleBlockUnknown(enabled: Boolean) {
        _blockUnknownEnabled.value = enabled
        viewModelScope.launch {
            appPreferences.dataStoreRef.edit { it[booleanPreferencesKey("block_unknown")] = enabled }
        }
    }

    fun toggleContactsOnly(enabled: Boolean) {
        _contactsOnlyEnabled.value = enabled
        viewModelScope.launch {
            appPreferences.dataStoreRef.edit { it[booleanPreferencesKey("contacts_only")] = enabled }
        }
    }

    fun toggleBlock070(enabled: Boolean) {
        _block070Enabled.value = enabled
        viewModelScope.launch {
            appPreferences.dataStoreRef.edit { it[booleanPreferencesKey("block_070")] = enabled }
        }
    }

    fun toggleOverseasBlock(enabled: Boolean) {
        _overseasBlockEnabled.value = enabled
        appPreferences.setOverseasBlockEnabled(enabled)
    }

    fun addOverseasException(phone: String) {
        viewModelScope.launch {
            val entity = SpamFilterEntity(
                id = UUID.randomUUID().toString(),
                type = "overseas_exception",
                pattern = phone,
                value = phone,
                reason = "해외번호 예외",
                isActive = true
            )
            spamFilterDao.insert(entity)
        }
    }

    fun removeOverseasException(phone: SpamFilterEntity) {
        viewModelScope.launch {
            spamFilterDao.delete(phone)
        }
    }

    fun addBlockedNumber(number: String, reason: String? = null) {
        viewModelScope.launch {
            val entity = SpamFilterEntity(
                id = UUID.randomUUID().toString(),
                type = "number",
                pattern = number,
                value = number,
                reason = reason,
                isActive = true
            )
            spamFilterDao.insert(entity)
            loadSpamStats()
        }
    }

    fun addKeywordFilter(keyword: String) {
        viewModelScope.launch {
            val entity = SpamFilterEntity(
                id = UUID.randomUUID().toString(),
                type = "keyword",
                pattern = keyword,
                value = keyword,
                isActive = true
            )
            spamFilterDao.insert(entity)
            loadSpamStats()
        }
    }

    fun deleteFilter(filter: SpamFilterEntity) {
        viewModelScope.launch {
            spamFilterDao.delete(filter)
            loadSpamStats()
        }
    }

    fun deleteFilterById(id: String) {
        viewModelScope.launch {
            spamFilterDao.deleteById(id)
            loadSpamStats()
        }
    }

    private fun loadSpamStats() {
        viewModelScope.launch {
            try {
                val blockedNums = spamFilterDao.getBlockedNumbers()
                val keywords = spamFilterDao.getKeywordFilters()
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                val last30DaysBlocked = spamFilterDao.getBlockedCountSince(thirtyDaysAgo)

                _spamStats.value = SpamStats(
                    totalBlocked = blockedNums.count { it.isActive } + last30DaysBlocked,
                    blockedNumbers = blockedNums.count { it.isActive },
                    keywordFilters = keywords.count { it.isActive },
                    last30DaysBlocked = last30DaysBlocked
                )
            } catch (_: Exception) {
                // Keep current stats on error
            }
        }
    }
}
