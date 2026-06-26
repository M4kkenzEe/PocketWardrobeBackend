package com.example.auth.model

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String? = null,
    val gender: String? = null
)

@Serializable
data class LoginRequest(
    val login: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: Int
)

data class UserPrincipal(val userId: Int, val username: String)

@Serializable
data class ForgotPasswordRequest(
    val email: String
)
