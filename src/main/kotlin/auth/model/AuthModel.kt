package com.example.auth.model

import kotlinx.serialization.Serializable

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