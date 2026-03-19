package com.bizconnect.v2.domain.model

data class Customer(
    val id: String,
    val userId: String,
    val name: String,
    val phone: String,
    val normalizedPhone: String,
    val groupId: String? = null,
    val groupName: String? = null,
    val birthday: String? = null,
    val anniversary: String? = null,
    val memo: String? = null,
    val industryType: String? = null,
    val callbackEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val isDeleted: Boolean = false
)
