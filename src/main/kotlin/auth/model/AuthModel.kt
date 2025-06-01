package com.example.auth.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val expiresAt: Long
)