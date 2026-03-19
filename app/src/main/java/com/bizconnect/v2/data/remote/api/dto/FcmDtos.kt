package com.bizconnect.v2.data.remote.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateFcmTokenRequest(
    val token: String,
    val deviceId: String,
    val deviceModel: String,
    val osVersion: String
)
