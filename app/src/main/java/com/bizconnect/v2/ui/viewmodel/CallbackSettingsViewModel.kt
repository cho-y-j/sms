package com.bizconnect.v2.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.CallbackSettingDao
import com.bizconnect.v2.data.local.db.dao.CategoryDao
import com.bizconnect.v2.data.local.db.entity.CallbackSettingEntity
import com.bizconnect.v2.data.local.db.entity.CategoryEntity
import com.bizconnect.v2.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CallbackSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callbackSettingDao: CallbackSettingDao,
    private val appPreferences: AppPreferences,
    private val categoryDao: CategoryDao
) : ViewModel() {

    data class UiState(
        val autoCallbackEnabled: Boolean = false,
        val onEndEnabled: Boolean = false,
        val onEndMessage: String = "",
        val onEndImageUrl: String? = null,
        val onEndTemplateId: Long? = null,
        val onMissedEnabled: Boolean = false,
        val onMissedMessage: String = "",
        val onMissedImageUrl: String? = null,
        val onMissedTemplateId: Long? = null,
        val onBusyEnabled: Boolean = false,
        val onBusyMessage: String = "",
        val onBusyImageUrl: String? = null,
        val onBusyTemplateId: Long? = null,
        val onOutgoingEnabled: Boolean = false,
        val onOutgoingMessage: String = "",
        val onOutgoingTemplateId: Long? = null,
        val businessCardEnabled: Boolean = false,
        val businessCardImageUrl: String? = null,
        val throttleInterval: Int = 5,
        // 발송 방식: false=자동, true=수동(확인 후 발송)
        val manualMode: Boolean = false,
        // 자동발송 금지 번호 목록
        val blockedNumbers: List<String> = emptyList(),
        val excludedCategoryIds: Set<Long> = emptySet(),
        val categories: List<CategoryEntity> = emptyList(),
        val isLoading: Boolean = true,
        val isSaved: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val userId = "default"

    init {
        loadSettings()
        loadCategories()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val setting = callbackSettingDao.getSync(userId)
            if (setting != null) {
                val excludedIds = setting.excludedCategoryIds
                    .split(",")
                    .mapNotNull { it.trim().toLongOrNull() }
                    .toSet()
                _uiState.update { state ->
                    state.copy(
                        autoCallbackEnabled = setting.autoCallbackEnabled,
                        onEndEnabled = setting.onEndEnabled,
                        onEndMessage = setting.onEndMessage,
                        onEndImageUrl = setting.onEndImageUrl,
                        onEndTemplateId = setting.onEndTemplateId,
                        onMissedEnabled = setting.onMissedEnabled,
                        onMissedMessage = setting.onMissedMessage,
                        onMissedImageUrl = setting.onMissedImageUrl,
                        onMissedTemplateId = setting.onMissedTemplateId,
                        onBusyEnabled = setting.onBusyEnabled,
                        onBusyMessage = setting.onBusyMessage,
                        onBusyImageUrl = setting.onBusyImageUrl,
                        onBusyTemplateId = setting.onBusyTemplateId,
                        onOutgoingEnabled = setting.onOutgoingEnabled,
                        onOutgoingMessage = setting.onOutgoingMessage,
                        onOutgoingTemplateId = setting.onOutgoingTemplateId,
                        businessCardEnabled = setting.businessCardEnabled,
                        businessCardImageUrl = setting.businessCardImageUrl,
                        throttleInterval = setting.throttleInterval / 60000, // ms to minutes
                        manualMode = setting.manualMode,
                        blockedNumbers = setting.blockedNumbers
                            .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        excludedCategoryIds = excludedIds,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val categories = categoryDao.getAllSync()
            _uiState.update { it.copy(categories = categories) }
        }
    }

    fun toggleAutoCallback(enabled: Boolean) {
        _uiState.update { it.copy(autoCallbackEnabled = enabled) }
        autoSave()
    }

    fun toggleOnEnd(enabled: Boolean) {
        _uiState.update { it.copy(onEndEnabled = enabled) }
        autoSave()
    }

    fun updateOnEndMessage(msg: String) {
        _uiState.update { it.copy(onEndMessage = msg) }
        autoSave()
    }

    fun setOnEndTemplateId(id: Long?) {
        _uiState.update { it.copy(onEndTemplateId = id) }
        autoSave()
    }

    fun toggleOnMissed(enabled: Boolean) {
        _uiState.update { it.copy(onMissedEnabled = enabled) }
        autoSave()
    }

    fun updateOnMissedMessage(msg: String) {
        _uiState.update { it.copy(onMissedMessage = msg) }
        autoSave()
    }

    fun setOnMissedTemplateId(id: Long?) {
        _uiState.update { it.copy(onMissedTemplateId = id) }
        autoSave()
    }

    fun toggleOnBusy(enabled: Boolean) {
        _uiState.update { it.copy(onBusyEnabled = enabled) }
        autoSave()
    }

    fun updateOnBusyMessage(msg: String) {
        _uiState.update { it.copy(onBusyMessage = msg) }
        autoSave()
    }

    fun setOnBusyTemplateId(id: Long?) {
        _uiState.update { it.copy(onBusyTemplateId = id) }
        autoSave()
    }

    fun toggleOutgoing(enabled: Boolean) {
        _uiState.update { it.copy(onOutgoingEnabled = enabled) }
        autoSave()
    }

    fun updateOutgoingMessage(msg: String) {
        _uiState.update { it.copy(onOutgoingMessage = msg) }
        autoSave()
    }

    fun setOutgoingTemplateId(id: Long?) {
        _uiState.update { it.copy(onOutgoingTemplateId = id) }
        autoSave()
    }

    fun toggleBusinessCard(enabled: Boolean) {
        _uiState.update { it.copy(businessCardEnabled = enabled) }
        autoSave()
    }

    fun setBusinessCardImage(uri: String?) {
        if (uri == null) {
            _uiState.update { it.copy(businessCardImageUrl = null) }
            autoSave()
            return
        }
        // Picker (GetContent) URIs are transient — readable only by this Activity
        // instance. The callback fires later in a different process, so copy the
        // image into app-internal storage now and persist that stable file path.
        viewModelScope.launch {
            val stablePath = withContext(Dispatchers.IO) { copyBusinessCardToInternal(uri) }
            _uiState.update { it.copy(businessCardImageUrl = stablePath ?: uri) }
            saveSettings()
        }
    }

    private fun copyBusinessCardToInternal(uriString: String): String? {
        return try {
            val dir = File(context.filesDir, "business_card").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // keep only the latest card
            val file = File(dir, "card_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun updateThrottleInterval(minutes: Int) {
        val clamped = minutes.coerceIn(1, 60)
        _uiState.update { it.copy(throttleInterval = clamped) }
        autoSave()
    }

    /** 발송 방식 전환: true=수동(확인 후 발송), false=자동 */
    fun setManualMode(manual: Boolean) {
        _uiState.update { it.copy(manualMode = manual) }
        autoSave()
    }

    /** 자동발송 금지 번호 제거 */
    fun removeBlockedNumber(number: String) {
        _uiState.update { it.copy(blockedNumbers = it.blockedNumbers.filterNot { n -> n == number }) }
        autoSave()
    }

    fun toggleCategoryExclusion(categoryId: Long) {
        _uiState.update { state ->
            val newSet = state.excludedCategoryIds.toMutableSet()
            if (categoryId in newSet) newSet.remove(categoryId) else newSet.add(categoryId)
            state.copy(excludedCategoryIds = newSet)
        }
        autoSave()
    }

    private var saveJob: kotlinx.coroutines.Job? = null

    /**
     * Auto-save with 500ms debounce (for text input fields)
     */
    private fun autoSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            saveSettings()
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val state = _uiState.value
            val excludedIdsStr = state.excludedCategoryIds.joinToString(",")
            val entity = CallbackSettingEntity(
                userId = userId,
                autoCallbackEnabled = state.autoCallbackEnabled,
                onEndEnabled = state.onEndEnabled,
                onEndMessage = state.onEndMessage,
                onEndImageUrl = state.onEndImageUrl,
                onEndTemplateId = state.onEndTemplateId,
                onMissedEnabled = state.onMissedEnabled,
                onMissedMessage = state.onMissedMessage,
                onMissedImageUrl = state.onMissedImageUrl,
                onMissedTemplateId = state.onMissedTemplateId,
                onBusyEnabled = state.onBusyEnabled,
                onBusyMessage = state.onBusyMessage,
                onBusyImageUrl = state.onBusyImageUrl,
                onBusyTemplateId = state.onBusyTemplateId,
                onOutgoingEnabled = state.onOutgoingEnabled,
                onOutgoingMessage = state.onOutgoingMessage,
                onOutgoingTemplateId = state.onOutgoingTemplateId,
                businessCardEnabled = state.businessCardEnabled,
                businessCardImageUrl = state.businessCardImageUrl,
                throttleInterval = state.throttleInterval * 60000, // minutes to ms
                manualMode = state.manualMode,
                blockedNumbers = state.blockedNumbers.joinToString(","),
                excludedCategoryIds = excludedIdsStr,
                updatedAt = System.currentTimeMillis()
            )
            callbackSettingDao.insert(entity)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
