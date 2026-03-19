package com.bizconnect.v2.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.CategoryDao
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.entity.CategoryEntity
import com.bizconnect.v2.data.local.db.entity.ContactCategoryEntity
import com.bizconnect.v2.ui.contacts.ContactItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsScreenViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    data class UiState(
        val contacts: List<ContactItem> = emptyList(),
        val categories: List<CategoryEntity> = emptyList(),
        val isLoading: Boolean = true,
        val searchQuery: String = ""
    )

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<UiState> = combine(
        _searchQuery.flatMapLatest { query ->
            if (query.isBlank()) contactDao.getAll() else contactDao.search(query)
        },
        categoryDao.getAllFlow()
    ) { entities, categories ->
        UiState(
            contacts = entities.map { entity ->
                ContactItem(
                    id = entity.id,
                    name = entity.name,
                    phone = entity.phoneNumber,
                    photoUrl = entity.photoUri
                )
            },
            categories = categories,
            isLoading = false,
            searchQuery = _searchQuery.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun getCategoriesForContact(phone: String, callback: (List<Long>) -> Unit) {
        viewModelScope.launch {
            val normalized = phone.filter { it.isDigit() }
            callback(categoryDao.getCategoryIdsForContact(normalized))
        }
    }

    fun toggleContactCategory(phone: String, categoryId: Long, isCurrentlyIn: Boolean) {
        viewModelScope.launch {
            val normalized = phone.filter { it.isDigit() }
            if (isCurrentlyIn) {
                categoryDao.removeContactFromCategory(normalized, categoryId)
            } else {
                categoryDao.addContactToCategory(ContactCategoryEntity(normalized, categoryId))
            }
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            categoryDao.insert(CategoryEntity(name = name))
        }
    }
}
