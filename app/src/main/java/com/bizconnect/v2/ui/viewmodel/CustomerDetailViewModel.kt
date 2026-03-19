package com.bizconnect.v2.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.CategoryDao
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.dao.CustomerDao
import com.bizconnect.v2.data.local.db.entity.CategoryEntity
import com.bizconnect.v2.data.local.db.entity.ContactCategoryEntity
import com.bizconnect.v2.data.local.db.entity.CustomerEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val customerDao: CustomerDao,
    private val contactDao: ContactDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    data class UiState(
        val phone: String = "",
        val contactName: String = "",
        val birthday: String = "",
        val anniversary: String = "",
        val memo: String = "",
        val callbackEnabled: Boolean = true,
        val allCategories: List<CategoryEntity> = emptyList(),
        val selectedCategoryIds: Set<Long> = emptySet(),
        val existingCustomerId: String? = null
    )

    private val phone: String = savedStateHandle.get<String>("phone") ?: ""

    private val _uiState = MutableStateFlow(UiState(phone = phone))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _saveResult = MutableSharedFlow<String>()
    val saveResult: SharedFlow<String> = _saveResult.asSharedFlow()

    init {
        viewModelScope.launch {
            loadData()
        }
    }

    private suspend fun loadData() {
        // Load contact name from device contacts
        val contact = contactDao.getByPhone(phone)
        val contactName = contact?.name ?: phone

        // Load existing customer entity
        val customer = customerDao.getByPhone(phone)

        // Load all categories
        val allCategories = categoryDao.getAllSync()

        // Load category assignments for this phone
        val categoryIds = categoryDao.getCategoryIdsForContact(phone).toSet()

        _uiState.update { state ->
            state.copy(
                contactName = contactName,
                birthday = customer?.birthday ?: "",
                anniversary = customer?.anniversary ?: "",
                memo = customer?.memo ?: "",
                callbackEnabled = customer?.callbackEnabled ?: true,
                allCategories = allCategories,
                selectedCategoryIds = categoryIds,
                existingCustomerId = customer?.id
            )
        }
    }

    fun updateBirthday(value: String) {
        _uiState.update { it.copy(birthday = value) }
    }

    fun updateAnniversary(value: String) {
        _uiState.update { it.copy(anniversary = value) }
    }

    fun updateMemo(value: String) {
        _uiState.update { it.copy(memo = value) }
    }

    fun updateCallbackEnabled(value: Boolean) {
        _uiState.update { it.copy(callbackEnabled = value) }
    }

    fun toggleCategory(categoryId: Long) {
        _uiState.update { state ->
            val newIds = if (categoryId in state.selectedCategoryIds) {
                state.selectedCategoryIds - categoryId
            } else {
                state.selectedCategoryIds + categoryId
            }
            state.copy(selectedCategoryIds = newIds)
        }
    }

    fun save() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val now = System.currentTimeMillis()

                val customer = if (state.existingCustomerId != null) {
                    // Update existing
                    val existing = customerDao.getByPhone(phone)!!
                    existing.copy(
                        birthday = state.birthday.ifBlank { null },
                        anniversary = state.anniversary.ifBlank { null },
                        memo = state.memo.ifBlank { null },
                        callbackEnabled = state.callbackEnabled,
                        updatedAt = now
                    )
                } else {
                    // Create new
                    CustomerEntity(
                        id = UUID.randomUUID().toString(),
                        userId = "",
                        name = state.contactName,
                        phone = phone,
                        normalizedPhone = phone.replace(Regex("[^0-9+]"), ""),
                        birthday = state.birthday.ifBlank { null },
                        anniversary = state.anniversary.ifBlank { null },
                        memo = state.memo.ifBlank { null },
                        callbackEnabled = state.callbackEnabled,
                        createdAt = now,
                        updatedAt = now
                    )
                }

                customerDao.insert(customer)

                // Update category assignments
                categoryDao.removeContactFromAllCategories(phone)
                for (categoryId in state.selectedCategoryIds) {
                    categoryDao.addContactToCategory(
                        ContactCategoryEntity(
                            contactPhone = phone,
                            categoryId = categoryId
                        )
                    )
                }

                _uiState.update { it.copy(existingCustomerId = customer.id) }
                _saveResult.emit("저장되었습니다")
            } catch (e: Exception) {
                _saveResult.emit("저장에 실패했습니다")
            }
        }
    }
}
