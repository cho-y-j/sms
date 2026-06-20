package com.bizconnect.v2.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.dao.ConversationDao
import com.bizconnect.v2.data.local.db.dao.MessageDao
import com.bizconnect.v2.data.local.db.entity.MessageEntity
import com.bizconnect.v2.domain.ai.AiAssistant
import com.bizconnect.v2.util.FileUploader
import com.bizconnect.v2.util.NotificationUtil
import com.bizconnect.v2.util.SmsSender
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
    savedStateHandle: SavedStateHandle,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val contactDao: ContactDao,
    private val notificationUtil: NotificationUtil,
    private val smsSender: SmsSender,
    private val fileUploader: FileUploader,
    private val aiAssistant: AiAssistant,
    private val appPreferences: com.bizconnect.v2.data.preferences.AppPreferences
) : ViewModel() {

    private val threadId: Long = savedStateHandle.get<Long>("threadId") ?: 0L

    /** 문자 읽어주기(TTS) 활성 여부 — 설정에서 토글. */
    fun isTtsEnabled(): Boolean = appPreferences.isTtsEnabled()

    data class UiState(
        val messages: List<MessageUiModel> = emptyList(),
        val contactName: String = "",
        val phoneNumber: String = "",
        val isLoading: Boolean = true
    )

    data class MessageUiModel(
        val id: Long,
        val text: String,
        val isSent: Boolean,
        val timestamp: String,
        val date: String? = null,
        val status: Int = 0,
        val isRead: Boolean = false,
        val isMms: Boolean = false,
        val attachmentPath: String? = null
    )

    private val _contactName = MutableStateFlow("")
    private val _phoneNumber = MutableStateFlow("")
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    // AI state
    private val _aiSummary = MutableStateFlow<String?>(null)
    val aiSummary: StateFlow<String?> = _aiSummary
    private val _aiEmotion = MutableStateFlow<AiAssistant.EmotionResult?>(null)
    val aiEmotion: StateFlow<AiAssistant.EmotionResult?> = _aiEmotion
    private val _aiAppointment = MutableStateFlow<AiAssistant.AppointmentInfo?>(null)
    val aiAppointment: StateFlow<AiAssistant.AppointmentInfo?> = _aiAppointment
    private val _aiSuggestions = MutableStateFlow<List<String>>(emptyList())
    val aiSuggestions: StateFlow<List<String>> = _aiSuggestions
    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading
    private val _isAiSuggestLoading = MutableStateFlow(false)
    val isAiSuggestLoading: StateFlow<Boolean> = _isAiSuggestLoading
    private val _toneConvertedText = MutableStateFlow<String?>(null)
    val toneConvertedText: StateFlow<String?> = _toneConvertedText

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun clearSearch() { _searchQuery.value = "" }

    // AI search state
    private val _aiSearchResult = MutableStateFlow<String?>(null)
    val aiSearchResult: StateFlow<String?> = _aiSearchResult.asStateFlow()
    private val _isAiSearchLoading = MutableStateFlow(false)
    val isAiSearchLoading: StateFlow<Boolean> = _isAiSearchLoading.asStateFlow()

    fun aiSearchInConversation(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAiSearchLoading.value = true
            try {
                val result = aiAssistant.searchInConversation(threadId, query)
                _aiSearchResult.value = result
            } catch (e: Exception) {
                Log.e("MessageDetailVM", "AI search error", e)
                _aiSearchResult.value = "AI 검색에 실패했습니다."
            } finally {
                _isAiSearchLoading.value = false
            }
        }
    }

    fun clearAiSearchResult() { _aiSearchResult.value = null }

    // Use messages Flow only - don't combine with conversation Flow
    val uiState: StateFlow<UiState> = messageDao.getByThreadFlow(threadId)
        .map { messages ->
            Log.d("MessageDetailVM", "Flow emitted ${messages.size} messages for thread $threadId")
            val sortedMessages = messages.sortedBy { it.timestamp }
            val uiModels = mapMessagesWithDateHeaders(sortedMessages)

            UiState(
                messages = uiModels,
                contactName = _contactName.value,
                phoneNumber = _phoneNumber.value,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState()
        )

    init {
        Log.d("MessageDetailVM", "init threadId=$threadId")

        // Load contact info separately
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get conversation info for contact name
                val conversations = conversationDao.getByIdSync(threadId)
                if (conversations != null) {
                    _contactName.value = conversations.recipientName ?: conversations.recipientAddress
                    _phoneNumber.value = conversations.recipientAddress
                } else {
                    // Fallback: get from first message
                    val firstMsg = messageDao.getFirstMessageInThread(threadId)
                    if (firstMsg != null) {
                        _phoneNumber.value = firstMsg.address
                        _contactName.value = firstMsg.address
                    }
                }
                Log.d("MessageDetailVM", "Contact: ${_contactName.value}, Phone: ${_phoneNumber.value}")

                // Debug: direct suspend query
                val directMessages = messageDao.getMessagesByThreadDirect(threadId)
                Log.d("MessageDetailVM", "Direct query returned ${directMessages.size} messages for thread $threadId")
                if (directMessages.isNotEmpty()) {
                    Log.d("MessageDetailVM", "First msg: id=${directMessages[0].id}, body=${directMessages[0].body?.take(20)}")
                }

                // Mark as read
                conversationDao.markAsRead(threadId)
                messageDao.markAsRead(threadId)
            } catch (e: Exception) {
                Log.e("MessageDetailVM", "init error", e)
            }
        }
        notificationUtil.cancelNotification(threadId)
    }

    fun sendMessage(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val phoneNumber = _phoneNumber.value
            if (phoneNumber.isNotEmpty() && text.isNotEmpty()) {
                Log.d("MessageDetailVM", "Sending to $phoneNumber: $text")
                smsSender.sendSms(phoneNumber, text)
            }
        }
    }

    fun sendMmsWithImage(text: String, imageUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val phoneNumber = _phoneNumber.value
            if (phoneNumber.isNotEmpty()) {
                _isSending.value = true
                try { smsSender.sendMmsWithImage(phoneNumber, text, imageUri) }
                finally { _isSending.value = false }
            }
        }
    }

    fun sendMmsWithImages(text: String, imageUris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val phoneNumber = _phoneNumber.value
            if (phoneNumber.isNotEmpty()) {
                _isSending.value = true
                try { smsSender.sendMmsWithImages(phoneNumber, text, imageUris) }
                finally { _isSending.value = false }
            }
        }
    }

    fun sendFileMessage(text: String, fileUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val phoneNumber = _phoneNumber.value
            if (phoneNumber.isEmpty()) return@launch

            _isSending.value = true
            Log.d("MessageDetailVM", "Uploading file and sending link to $phoneNumber")

            try {
                val result = fileUploader.uploadFile(fileUri)
                if (result.success) {
                    val sizeStr = fileUploader.formatFileSize(result.fileSize)
                    val messageBody = buildString {
                        if (text.isNotBlank()) appendLine(text)
                        append("📎 ${result.fileName} ($sizeStr)\n${result.downloadUrl}")
                    }
                    smsSender.sendSms(phoneNumber, messageBody)
                    Log.d("MessageDetailVM", "File message sent: ${result.fileName}")
                } else {
                    Log.e("MessageDetailVM", "File upload failed: ${result.errorMessage}")
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    fun createTempImageUri(): Uri? {
        return try {
            val imageFile = File(appContext.cacheDir, "camera_${System.nanoTime()}.jpg")
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", imageFile)
        } catch (e: Exception) {
            Log.e("MessageDetailVM", "Failed to create temp image URI", e)
            null
        }
    }

    fun loadAiSummary() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAiLoading.value = true
            _aiSummary.value = null
            _aiEmotion.value = null
            _aiAppointment.value = null

            // 각각 독립 실행 (하나 실패해도 나머지 동작)
            try {
                // Incremental summary: read existing summary from DB
                val conversation = conversationDao.getByIdSync(threadId)
                val previousSummary = conversation?.aiSummary
                val sinceTimestamp = conversation?.aiSummaryDate

                val summary = aiAssistant.summarizeConversation(
                    threadId = threadId,
                    previousSummary = previousSummary,
                    sinceTimestamp = sinceTimestamp
                )
                _aiSummary.value = summary
                Log.d("MessageDetailVM", "AI summary done (incremental=${!previousSummary.isNullOrBlank()})")
            } catch (e: Exception) {
                Log.e("MessageDetailVM", "AI summary error: ${e.message}")
                _aiSummary.value = "요약을 가져올 수 없습니다."
            }

            try {
                val emotion = aiAssistant.analyzeEmotion(threadId)
                _aiEmotion.value = emotion
                Log.d("MessageDetailVM", "AI emotion: ${emotion.emoji}")
            } catch (e: Exception) {
                Log.e("MessageDetailVM", "AI emotion error: ${e.message}")
            }

            try {
                val appointment = aiAssistant.extractAppointment(threadId)
                _aiAppointment.value = appointment
                Log.d("MessageDetailVM", "AI appointment: ${appointment?.date ?: "없음"}")
            } catch (e: Exception) {
                Log.e("MessageDetailVM", "AI appointment error: ${e.message}")
            }

            // 요약 결과를 DB에 저장 (대화 목록에서 표시용)
            try {
                conversationDao.updateAiSummary(
                    threadId = threadId,
                    summary = _aiSummary.value,
                    date = System.currentTimeMillis(),
                    emotion = _aiEmotion.value?.let { "${it.emoji} ${it.emotion}" }
                )
                Log.d("MessageDetailVM", "AI summary saved to DB")
            } catch (e: Exception) {
                Log.e("MessageDetailVM", "Save summary failed: ${e.message}")
            }

            _isAiLoading.value = false
        }
    }

    fun loadAiSuggestions() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAiSuggestLoading.value = true
            try {
                val suggestions = aiAssistant.suggestReplies(threadId)
                _aiSuggestions.value = suggestions
            } catch (e: Exception) {
                Log.e("MessageDetailVM", "AI suggestions error", e)
                _aiSuggestions.value = emptyList()
            } finally {
                _isAiSuggestLoading.value = false
            }
        }
    }

    fun clearAiSuggestions() {
        _aiSuggestions.value = emptyList()
        _isAiSuggestLoading.value = false
    }

    fun convertTone(text: String, tone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAiSuggestLoading.value = true
            try {
                val converted = aiAssistant.convertTone(text, tone)
                _toneConvertedText.value = converted
            } catch (e: Exception) {
                Log.e("MessageDetailVM", "Tone conversion error", e)
            } finally {
                _isAiSuggestLoading.value = false
            }
        }
    }

    fun clearToneConvertedText() {
        _toneConvertedText.value = null
    }

    private fun mapMessagesWithDateHeaders(messages: List<MessageEntity>): List<MessageUiModel> {
        val result = mutableListOf<MessageUiModel>()
        val dateFormat = SimpleDateFormat("yyyy년 M월 d일 EEEE", Locale.KOREA)
        val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREA)
        var lastDateString: String? = null

        for (message in messages) {
            val currentDateString = dateFormat.format(Date(message.timestamp))

            val dateHeader = if (currentDateString != lastDateString) {
                lastDateString = currentDateString
                currentDateString
            } else {
                null
            }

            result.add(
                MessageUiModel(
                    id = message.id,
                    text = message.body ?: "",
                    isSent = message.type == 2, // TYPE_SENT
                    timestamp = timeFormat.format(Date(message.timestamp)),
                    date = dateHeader,
                    status = message.status,
                    isRead = message.read,
                    isMms = message.isMms,
                    attachmentPath = message.attachmentPath
                )
            )
        }
        return result
    }
}
