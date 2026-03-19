package com.bizconnect.v2.data.remote.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val success: Boolean,
    val message: String,
    val data: AuthData? = null,
    val error: String? = null
)

@Serializable
data class AuthData(
    val userId: String,
    val email: String,
    val name: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val businessName: String,
    val phone: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)
