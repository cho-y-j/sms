package com.bizconnect.v2.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.CategoryDao
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.dao.MessageTemplateDao
import com.bizconnect.v2.data.preferences.AppPreferences
import com.bizconnect.v2.domain.ai.AiAssistant
import com.bizconnect.v2.ui.business.MessageType
import com.bizconnect.v2.ui.business.Recipient
import com.bizconnect.v2.util.SmsSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class BulkSendUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val recipients: List<Recipient> = emptyList(),
    val messageText: String = "",
    val selectedImageUri: Uri? = null,
    val isSending: Boolean = false,
    val sendProgress: Float = 0f,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val sendComplete: Boolean = false,
    val dailySentCount: Int = 0
)

@HiltViewModel
class BulkSendViewModel @Inject constructor(
    private val smsSender: SmsSender,
    private val contactDao: ContactDao,
    private val categoryDao: CategoryDao,
    private val appPreferences: AppPreferences,
    private val messageTemplateDao: MessageTemplateDao,
    private val aiAssistant: AiAssistant
) : ViewModel() {
    private val _uiState = MutableStateFlow(BulkSendUiState())
    val uiState: StateFlow<BulkSendUiState> = _uiState

    fun setRecipients(recipients: List<Recipient>) {
        _uiState.update { it.copy(recipients = recipients) }
    }

    fun setMessageText(text: String) {
        _uiState.update { it.copy(messageText = text) }
    }

    fun setImageUri(uri: Uri?) {
        _uiState.update { it.copy(selectedImageUri = uri) }
    }

    fun loadTemplate(templateId: Long) {
        viewModelScope.launch {
            val template = messageTemplateDao.getById(templateId)
            if (template != null) loadTemplateFromEntity(template)
        }
    }

    fun loadTemplateFromEntity(template: com.bizconnect.v2.data.local.db.entity.MessageTemplateEntity) {
        _uiState.update {
            it.copy(
                messageText = template.content,
                selectedImageUri = if (template.isMms && template.imageUri != null) {
                    val path = template.imageUri
                    if (path.startsWith("/")) android.net.Uri.fromFile(java.io.File(path))
                    else android.net.Uri.parse(path)
                } else it.selectedImageUri
            )
        }
    }

    fun setTemplateContent(content: String) {
        _uiState.update { it.copy(messageText = content) }
    }

    fun insertVariable(variable: String) {
        _uiState.update { state ->
            state.copy(messageText = state.messageText + "%${variable}%")
        }
    }

    fun getMessageType(): MessageType {
        val state = _uiState.value
        return when {
            state.selectedImageUri != null -> MessageType.MMS
            state.messageText.toByteArray(Charsets.UTF_8).size > 90 -> MessageType.LMS
            else -> MessageType.SMS
        }
    }

    fun estimateCost(): Double {
        val state = _uiState.value
        val count = state.recipients.size
        val unitCost = when (getMessageType()) {
            MessageType.SMS -> appPreferences.getSmsCost()
            MessageType.LMS -> appPreferences.getLmsCost()
            MessageType.MMS -> appPreferences.getMmsCost()
        }
        return count * unitCost
    }

    fun getDailyLimit(): Int {
        return if (appPreferences.getSubscriptionTier() == "free") {
            appPreferences.getDailyLimitCount()
        } else {
            appPreferences.getPaidTierDailyLimit()
        }
    }

    fun getCreditBalance(): Double {
        return appPreferences.getCreditBalance()
    }

    suspend fun generateAiMessage(prompt: String): String {
        return aiAssistant.generateMessage(prompt)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetSendState() {
        _uiState.update {
            it.copy(
                sendComplete = false,
                sendProgress = 0f,
                successCount = 0,
                failureCount = 0
            )
        }
    }

    fun sendBulkMessages() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.recipients.isEmpty() || state.messageText.isEmpty()) {
                _uiState.update { it.copy(error = "수신자와 메시지를 입력해주세요") }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isSending = true,
                    sendProgress = 0f,
                    successCount = 0,
                    failureCount = 0,
                    sendComplete = false,
                    error = null
                )
            }

            try {
                val recipientCount = state.recipients.size
                var success = 0
                var failure = 0
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)
                val now = Date()
                val currentDate = dateFormat.format(now)
                val currentTime = timeFormat.format(now)

                state.recipients.forEachIndexed { index, recipient ->
                    try {
                        // Variable substitution per recipient
                        var msg = state.messageText
                        msg = msg.replace("%이름%", recipient.name)
                        msg = msg.replace("%고객명%", recipient.name)
                        msg = msg.replace("%전화번호%", recipient.phone)
                        msg = msg.replace("%날짜%", currentDate)
                        msg = msg.replace("%시간%", currentTime)
                        // Also handle {variable} format
                        msg = msg.replace("{이름}", recipient.name)
                        msg = msg.replace("{고객명}", recipient.name)
                        msg = msg.replace("{전화번호}", recipient.phone)
                        msg = msg.replace("{날짜}", currentDate)
                        msg = msg.replace("{시간}", currentTime)

                        val result = if (state.selectedImageUri != null) {
                            smsSender.sendMmsWithImage(recipient.phone, msg, state.selectedImageUri)
                        } else {
                            smsSender.sendSms(recipient.phone, msg)
                        }

                        if (result >= 0) success++ else failure++
                    } catch (e: Exception) {
                        failure++
                    }

                    _uiState.update {
                        it.copy(
                            sendProgress = (index + 1) / recipientCount.toFloat(),
                            successCount = success,
                            failureCount = failure
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        isSending = false,
                        sendComplete = true,
                        dailySentCount = it.dailySentCount + success
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        error = "발송 중 오류가 발생했습니다: ${e.message}"
                    )
                }
            }
        }
    }
}
