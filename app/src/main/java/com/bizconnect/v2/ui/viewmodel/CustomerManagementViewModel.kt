package com.bizconnect.v2.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.CategoryDao
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.entity.CategoryEntity
import com.bizconnect.v2.data.local.db.entity.ContactEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class CustomerManagementViewModel @Inject constructor(
    private val categoryDao: CategoryDao,
    private val contactDao: ContactDao
) : ViewModel() {

    data class CategoryWithCount(
        val category: CategoryEntity,
        val contactCount: Int
    )

    data class ContactWithCategories(
        val phone: String,
        val name: String,
        val categoryNames: List<String>
    )

    data class UiState(
        val categories: List<CategoryWithCount> = emptyList(),
        val contactsWithCategories: List<ContactWithCategories> = emptyList()
    )

    val uiState: StateFlow<UiState> = categoryDao.getAllFlow()
        .map { categories ->
            val catWithCounts = categories.map { cat ->
                CategoryWithCount(cat, categoryDao.getContactCountForCategory(cat.id))
            }

            // Build phone -> category names mapping
            val phoneToCategoryNames = mutableMapOf<String, MutableList<String>>()
            for (cat in categories) {
                val phones = categoryDao.getContactPhonesForCategory(cat.id)
                for (phone in phones) {
                    phoneToCategoryNames.getOrPut(phone) { mutableListOf() }.add(cat.name)
                }
            }

            // Load ALL contacts from device address book
            val allContacts = contactDao.getAllSync()
            val contactsWithCats = allContacts.map { contact ->
                val catNames = phoneToCategoryNames[contact.phoneNumber]
                    ?: phoneToCategoryNames[contact.normalizedNumber]
                    ?: emptyList()
                ContactWithCategories(
                    phone = contact.phoneNumber,
                    name = contact.name,
                    categoryNames = catNames
                )
            }.sortedBy { it.name }

            UiState(categories = catWithCounts, contactsWithCategories = contactsWithCats)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun addCategory(name: String) {
        viewModelScope.launch { categoryDao.insert(CategoryEntity(name = name)) }
    }

    fun updateCategory(id: Long, name: String) {
        viewModelScope.launch {
            categoryDao.getById(id)?.let { cat ->
                categoryDao.update(cat.copy(name = name))
            }
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch { categoryDao.deleteById(id) }
    }

    fun addContact(name: String, phone: String) {
        viewModelScope.launch {
            val normalized = phone.filter { it.isDigit() }
            val existing = contactDao.getByPhone(normalized)
            if (existing == null) {
                contactDao.insert(ContactEntity(
                    id = phone.hashCode().toLong() and 0x7FFFFFFFL,
                    name = name,
                    phoneNumber = phone,
                    normalizedNumber = normalized,
                    photoUri = null,
                    thumbnailUri = null
                ))
            }
        }
    }

    fun getContactsForCategory(categoryId: Long, callback: (List<ContactWithCategories>) -> Unit) {
        viewModelScope.launch {
            val phones = categoryDao.getContactPhonesForCategory(categoryId)
            val contacts = phones.mapNotNull { phone ->
                val contact = contactDao.getByPhone(phone)
                if (contact != null) ContactWithCategories(contact.phoneNumber, contact.name, listOf()) else null
            }
            callback(contacts)
        }
    }
}
