package com.bizconnect.v2.data.repository

import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.entity.ContactEntity
import com.bizconnect.v2.domain.model.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ContactRepository @Inject constructor(
    private val contactDao: ContactDao
) {
    fun getAllContacts(): Flow<List<Contact>> =
        contactDao.getAll().map { contacts ->
            contacts.map { it.toDomain() }
        }

    fun getContactsWithPhoto(): Flow<List<Contact>> =
        contactDao.getContactsWithPhoto().map { contacts ->
            contacts.map { it.toDomain() }
        }

    suspend fun getContactByPhone(phone: String): Contact? =
        contactDao.getByPhone(phone)?.toDomain()

    fun searchContacts(query: String): Flow<List<Contact>> =
        contactDao.search(query).map { contacts ->
            contacts.map { it.toDomain() }
        }

    fun getContactsByName(name: String): Flow<List<Contact>> =
        contactDao.getByName(name).map { contacts ->
            contacts.map { it.toDomain() }
        }

    suspend fun insertContact(contact: Contact) =
        contactDao.insert(contact.toEntity())

    suspend fun insertContacts(contacts: List<Contact>) =
        contactDao.insertAll(contacts.map { it.toEntity() })

    suspend fun updateContact(contact: Contact) =
        contactDao.update(contact.toEntity())

    suspend fun deleteContact(contact: Contact) =
        contactDao.delete(contact.toEntity())

    suspend fun deleteContactById(id: Long) =
        contactDao.deleteById(id)

    suspend fun deleteAllContacts() =
        contactDao.deleteAll()

    fun getTotalCount(): Flow<Int> =
        contactDao.getTotalCount()

    suspend fun updateLastUpdated(id: Long, timestamp: Long) =
        contactDao.updateLastUpdated(id, timestamp)

    suspend fun getOldContacts(olderThan: Long): List<Contact> =
        contactDao.getOldContacts(olderThan).map { it.toDomain() }

    private fun ContactEntity.toDomain() = Contact(
        id = id,
        name = name,
        phoneNumber = phoneNumber,
        normalizedNumber = normalizedNumber,
        photoUri = photoUri,
        thumbnailUri = thumbnailUri,
        lastUpdated = lastUpdated
    )

    private fun Contact.toEntity() = ContactEntity(
        id = id,
        name = name,
        phoneNumber = phoneNumber,
        normalizedNumber = normalizedNumber,
        photoUri = photoUri,
        thumbnailUri = thumbnailUri,
        lastUpdated = lastUpdated
    )
}
