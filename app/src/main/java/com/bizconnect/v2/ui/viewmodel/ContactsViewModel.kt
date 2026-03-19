package com.bizconnect.v2.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ContactsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val contacts: List<Any> = emptyList(),
    val filteredContacts: List<Any> = emptyList()
)

class ContactsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState

    init {
        loadContacts()
    }

    private fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Load contacts from repository
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    contacts = emptyList(),
                    filteredContacts = emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun searchContacts(query: String) {
        viewModelScope.launch {
            val filtered = if (query.isEmpty()) {
                _uiState.value.contacts
            } else {
                _uiState.value.contacts.filter { contact ->
                    // Filter logic
                    true
                }
            }
            _uiState.value = _uiState.value.copy(filteredContacts = filtered)
        }
    }

    fun addContact(name: String, phone: String) {
        viewModelScope.launch {
            try {
                // Add to repository
                loadContacts()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            try {
                // Delete from repository
                loadContacts()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
