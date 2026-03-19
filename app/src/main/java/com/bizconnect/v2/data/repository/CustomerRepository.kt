package com.bizconnect.v2.data.repository

import com.bizconnect.v2.data.local.db.BizConnectDatabase
import com.bizconnect.v2.data.local.db.entity.CustomerEntity
import com.bizconnect.v2.domain.model.Customer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CustomerRepository(private val database: BizConnectDatabase) {
    private val customerDao = database.customerDao()

    fun getAllCustomers(userId: String): Flow<List<Customer>> =
        customerDao.getAll(userId).map { customers ->
            customers.map { it.toDomain() }
        }

    suspend fun getCustomerById(id: String): Customer? =
        customerDao.getById(id)?.toDomain()

    suspend fun getCustomerByPhone(phone: String): Customer? =
        customerDao.getByPhone(phone)?.toDomain()

    fun getCustomersByGroup(groupId: String): Flow<List<Customer>> =
        customerDao.getByGroup(groupId).map { customers ->
            customers.map { it.toDomain() }
        }

    fun getBirthdayCustomers(userId: String, monthDay: String): Flow<List<Customer>> =
        customerDao.getBirthdaysToday(userId, monthDay).map { customers ->
            customers.map { it.toDomain() }
        }

    fun getAnniversaryCustomers(userId: String, monthDay: String): Flow<List<Customer>> =
        customerDao.getAnniversariesToday(userId, monthDay).map { customers ->
            customers.map { it.toDomain() }
        }

    fun searchCustomers(userId: String, query: String): Flow<List<Customer>> =
        customerDao.search(userId, query).map { customers ->
            customers.map { it.toDomain() }
        }

    suspend fun addCustomer(customer: Customer) =
        customerDao.insert(customer.toEntity())

    suspend fun addCustomers(customers: List<Customer>) =
        customerDao.insertAll(customers.map { it.toEntity() })

    suspend fun updateCustomer(customer: Customer) =
        customerDao.update(customer.toEntity())

    suspend fun deleteCustomer(customer: Customer) =
        customerDao.delete(customer.toEntity())

    suspend fun deleteCustomerById(id: String) =
        customerDao.deleteById(id)

    suspend fun softDeleteCustomer(id: String) =
        customerDao.softDelete(id, System.currentTimeMillis())

    suspend fun getUnsyncedCustomers(userId: String): List<Customer> =
        customerDao.getUnsyncedCustomers(userId).map { it.toDomain() }

    suspend fun markCustomerAsSynced(id: String) =
        customerDao.markAsSynced(id, System.currentTimeMillis())

    fun getTotalCount(userId: String): Flow<Int> =
        customerDao.getTotalCount(userId)

    fun getCallbackEnabledCustomers(userId: String): Flow<List<Customer>> =
        customerDao.getCallbackEnabledCustomers(userId).map { customers ->
            customers.map { it.toDomain() }
        }

    fun getGroupMemberCount(userId: String, groupId: String): Flow<Int> =
        customerDao.getGroupMemberCount(userId, groupId)

    private fun CustomerEntity.toDomain() = Customer(
        id = id,
        userId = userId,
        name = name,
        phone = phone,
        normalizedPhone = normalizedPhone,
        groupId = groupId,
        groupName = groupName,
        birthday = birthday,
        anniversary = anniversary,
        memo = memo,
        industryType = industryType,
        callbackEnabled = callbackEnabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncedAt = syncedAt,
        isDeleted = isDeleted
    )

    private fun Customer.toEntity() = CustomerEntity(
        id = id,
        userId = userId,
        name = name,
        phone = phone,
        normalizedPhone = normalizedPhone,
        groupId = groupId,
        groupName = groupName,
        birthday = birthday,
        anniversary = anniversary,
        memo = memo,
        industryType = industryType,
        callbackEnabled = callbackEnabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncedAt = syncedAt,
        isDeleted = isDeleted
    )
}
