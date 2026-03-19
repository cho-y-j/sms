package com.bizconnect.v2.data.remote.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val birthDate: String? = null, // yyyy-MM-dd
    val city: String? = null,
    val customFields: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class CreateCustomerRequest(
    val name: String,
    val phone: String,
    val email: String? = null,
    val birthDate: String? = null, // yyyy-MM-dd
    val city: String? = null,
    val customFields: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList()
)

@Serializable
data class UpdateCustomerRequest(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val birthDate: String? = null,
    val city: String? = null,
    val customFields: Map<String, String>? = null,
    val tags: List<String>? = null
)

@Serializable
data class ImportResult(
    val success: Boolean,
    val totalImported: Int,
    val totalFailed: Int,
    val failedRows: List<ImportError> = emptyList()
)

@Serializable
data class ImportError(
    val rowNumber: Int,
    val error: String
)
