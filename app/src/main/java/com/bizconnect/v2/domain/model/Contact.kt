package com.bizconnect.v2.domain.model

data class Contact(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val photoUri: String?,
    val thumbnailUri: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)
