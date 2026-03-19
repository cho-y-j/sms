package com.bizconnect.v2.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.util.SmsSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComposeMessageViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val smsSender: SmsSender
) : ViewModel() {

    data class SendResult(
        val threadId: Long = -1L,       // 1건일 때 이동용
        val totalCount: Int = 0,
        val successCount: Int = 0
    )

    data class UiState(
        val searchResults: List<ContactUiModel> = emptyList(),
        val selectedRecipients: List<ContactUiModel> = emptyList(),
        val messageText: String = "",
        val isSending: Boolean = false,
        val sendResult: SendResult? = null
    )

    data class ContactUiModel(
        val id: Long,
        val name: String,
        val phone: String,
        val photoUri: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun searchContacts(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            contactDao.search(query).collect { contacts ->
                val results = contacts.map { entity ->
                    ContactUiModel(
                        id = entity.id,
                        name = entity.name,
                        phone = entity.phoneNumber,
                        photoUri = entity.photoUri
                    )
                }
                _uiState.update { it.copy(searchResults = results) }
            }
        }
    }

    /**
     * One-shot search (for dialog, no Flow subscription).
     */
    fun searchContactsOnce(query: String, callback: (List<ContactUiModel>) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val contacts = contactDao.searchSync(query)
            val results = contacts.take(20).map { entity ->
                ContactUiModel(
                    id = entity.id,
                    name = entity.name,
                    phone = entity.phoneNumber,
                    photoUri = entity.photoUri
                )
            }
            callback(results)
        }
    }

    fun addRecipientByPhone(phone: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val contact = contactDao.getByPhone(phone)
            val uiModel = ContactUiModel(
                id = contact?.id ?: phone.hashCode().toLong(),
                name = contact?.name ?: phone,
                phone = phone,
                photoUri = contact?.photoUri
            )
            addRecipient(uiModel)
        }
    }

    fun addRecipient(contact: ContactUiModel) {
        _uiState.update { state ->
            if (state.selectedRecipients.any { it.id == contact.id }) {
                state
            } else {
                state.copy(selectedRecipients = state.selectedRecipients + contact)
            }
        }
    }

    fun removeRecipient(contact: ContactUiModel) {
        _uiState.update { state ->
            state.copy(selectedRecipients = state.selectedRecipients.filter { it.id != contact.id })
        }
    }

    fun updateMessage(text: String) {
        _uiState.update { it.copy(messageText = text) }
    }

    fun sendMessage() {
        val state = _uiState.value
        if (state.selectedRecipients.isEmpty() || state.messageText.isEmpty()) return

        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            try {
                var lastThreadId = -1L
                var success = 0
                for (recipient in state.selectedRecipients) {
                    val tid = smsSender.sendSms(recipient.phone, state.messageText)
                    if (tid > 0) { lastThreadId = tid; success++ }
                }
                _uiState.update {
                    it.copy(
                        messageText = "", selectedRecipients = emptyList(), isSending = false,
                        sendResult = SendResult(lastThreadId, state.selectedRecipients.size, success)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun sendMmsWithImage(text: String, imageUri: Uri) {
        val state = _uiState.value
        if (state.selectedRecipients.isEmpty()) return

        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            try {
                var lastThreadId = -1L
                var success = 0
                for (recipient in state.selectedRecipients) {
                    val tid = smsSender.sendMmsWithImage(recipient.phone, text, imageUri)
                    if (tid > 0) { lastThreadId = tid; success++ }
                }
                _uiState.update {
                    it.copy(
                        messageText = "", selectedRecipients = emptyList(), isSending = false,
                        sendResult = SendResult(lastThreadId, state.selectedRecipients.size, success)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun clearSendResult() {
        _uiState.update { it.copy(sendResult = null) }
    }
}
