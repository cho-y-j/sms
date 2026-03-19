package com.bizconnect.v2.data.remote.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val success: Boolean = false,
    val message: String,
    val error: String? = null,
    val statusCode: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val details: Map<String, String>? = null
)
