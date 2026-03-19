package com.bizconnect.v2.data.remote.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SettingsDto(
    val userId: String,
    val autoApprove: Boolean = false,
    val autoApproveThreshold: Int = 100, // SMS threshold for auto-approve
    val dailyLimit: Int = 500,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val timezone: String = "UTC",
    val theme: String = "LIGHT",
    val language: String = "en",
    val callbackUrl: String? = null,
    val callbackEnabled: Boolean = false,
    val updatedAt: Long
)

@Serializable
data class UpdateSettingsRequest(
    val autoApprove: Boolean? = null,
    val autoApproveThreshold: Int? = null,
    val dailyLimit: Int? = null,
    val notificationsEnabled: Boolean? = null,
    val soundEnabled: Boolean? = null,
    val vibrateEnabled: Boolean? = null,
    val timezone: String? = null,
    val theme: String? = null,
    val language: String? = null
)

@Serializable
data class UpdateCallbackRequest(
    val callbackUrl: String,
    val callbackEnabled: Boolean
)
