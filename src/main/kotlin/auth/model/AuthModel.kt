package com.example.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Legacy - kept for backward compatibility
@Serializable
data class AuthResponse(
    val token: String,
    val expiresAt: Long
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val gender: String?
)

// New auth models for refresh token flow

@Serializable
data class RegisterResponse(
    val message: String,
    val userId: Int
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("token_type")
    val tokenType: String = "Bearer"
)

@Serializable
data class RefreshResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("token_type")
    val tokenType: String = "Bearer"
)

@Serializable
data class AuthErrorResponse(
    val error: String,
    val message: String
)