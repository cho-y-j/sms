package com.bizconnect.v2.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.CategoryDao
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.dao.ConversationDao
import com.bizconnect.v2.data.local.db.dao.MessageDao
import com.bizconnect.v2.data.local.db.entity.CategoryEntity
import com.bizconnect.v2.data.sync.SmsSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val categoryDao: CategoryDao,
    private val smsSyncManager: SmsSyncManager
) : ViewModel() {

    data class UiState(
        val conversations: List<ConversationUiModel> = emptyList(),
        val categories: List<CategoryEntity> = emptyList(),
        val selectedCategoryId: Long? = null, // null = 전체
        val isLoading: Boolean = true,
        val searchQuery: String = "",
        val error: String? = null
    )

    data class ConversationUiModel(
        val threadId: Long,
        val contactName: String,
        val contactPhoto: String? = null,
        val lastMessage: String,
        val timestamp: String,
        val unreadCount: Int = 0,
        val isPinned: Boolean = false,
        val isMuted: Boolean = false,
        val aiSummary: String? = null,
        val aiEmotion: String? = null
    )

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _error = MutableStateFlow<String?>(null)

    /** Call this after permissions are granted */
    fun triggerSync() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                android.util.Log.d("ConversationListVM", "triggerSync called")
                smsSyncManager.performInitialSyncIfNeeded()
            } catch (e: Exception) {
                android.util.Log.e("ConversationListVM", "Sync failed", e)
            }
        }
    }

    // 카테고리별 전화번호 캐시 (Flow로 자동 갱신)
    private val _categoryPhonesCache = MutableStateFlow<Set<String>>(emptySet())

    init {
        // 카테고리 선택 변경 시 해당 카테고리 전화번호 로드
        viewModelScope.launch {
            _selectedCategoryId.collect { categoryId ->
                if (categoryId != null) {
                    val phones = categoryDao.getContactPhonesForCategory(categoryId).toSet()
                    _categoryPhonesCache.value = phones
                } else {
                    _categoryPhonesCache.value = emptySet()
                }
            }
        }
    }

    val uiState: StateFlow<UiState> = combine(
        _searchQuery.flatMapLatest { query ->
            if (query.isBlank()) conversationDao.getAll()
            else conversationDao.search(query)
        },
        _selectedCategoryId,
        _categoryPhonesCache,
        categoryDao.getAllFlow(),
        _error
    ) { conversations, categoryId, categoryPhones, categories, error ->
        val filtered = if (categoryId == null) {
            conversations
        } else {
            conversations.filter { entity ->
                val addr = entity.recipientAddress
                val digitsOnly = addr.filter { it.isDigit() }
                if (digitsOnly.length < 7 || addr.startsWith("#") || addr.any { it.isLetter() }) {
                    false
                } else {
                    categoryPhones.any { phone ->
                        val normPhone = phone.filter { it.isDigit() }
                        if (normPhone.length < 7) false
                        else digitsOnly.takeLast(8) == normPhone.takeLast(8)
                    }
                }
            }
        }

        UiState(
            conversations = filtered.map { entity ->
                ConversationUiModel(
                    threadId = entity.threadId,
                    contactName = entity.recipientName ?: entity.recipientAddress,
                    contactPhoto = entity.photoUri,
                    lastMessage = entity.snippet,
                    timestamp = formatTimestamp(entity.lastMessageTimestamp),
                    unreadCount = entity.unreadCount,
                    isPinned = entity.isPinned,
                    isMuted = entity.isMuted,
                    aiSummary = entity.aiSummary,
                    aiEmotion = entity.aiEmotion
                )
            },
            categories = categories,
            selectedCategoryId = categoryId,
            isLoading = false,
            searchQuery = _searchQuery.value,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            categoryDao.insert(CategoryEntity(name = name))
        }
    }

    fun getRecipientAddress(threadId: Long, callback: (String) -> Unit) {
        viewModelScope.launch {
            val conv = conversationDao.getByIdSync(threadId)
            callback(conv?.recipientAddress ?: "")
        }
    }

    fun getCategoriesForContact(phone: String, callback: (List<Long>) -> Unit) {
        viewModelScope.launch {
            val normalized = phone.filter { it.isDigit() }
            val ids = categoryDao.getCategoryIdsForContact(normalized)
            callback(ids)
        }
    }

    fun toggleContactCategory(phone: String, categoryId: Long, isCurrentlyIn: Boolean) {
        viewModelScope.launch {
            val normalized = phone.filter { it.isDigit() }
            if (isCurrentlyIn) {
                categoryDao.removeContactFromCategory(normalized, categoryId)
            } else {
                categoryDao.addContactToCategory(
                    com.bizconnect.v2.data.local.db.entity.ContactCategoryEntity(normalized, categoryId)
                )
            }
        }
    }

    fun deleteConversation(threadId: Long) {
        viewModelScope.launch {
            try {
                messageDao.deleteByThread(threadId)
                conversationDao.deleteById(threadId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun pinConversation(threadId: Long) {
        viewModelScope.launch {
            try {
                conversationDao.pin(threadId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun unpinConversation(threadId: Long) {
        viewModelScope.launch {
            try {
                conversationDao.unpin(threadId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun markAsRead(threadId: Long) {
        viewModelScope.launch {
            try {
                conversationDao.markAsRead(threadId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun formatTimestamp(millis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - millis
        return when {
            diff < 60_000 -> "방금"
            diff < 3_600_000 -> "${diff / 60_000}분 전"
            diff < 86_400_000 -> SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(millis))
            diff < 604_800_000 -> {
                val days = arrayOf("일", "월", "화", "수", "목", "금", "토")
                val cal = Calendar.getInstance().apply { timeInMillis = millis }
                "${days[cal.get(Calendar.DAY_OF_WEEK) - 1]}요일"
            }
            else -> SimpleDateFormat("M/d", Locale.KOREA).format(Date(millis))
        }
    }
}
