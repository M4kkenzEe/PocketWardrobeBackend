package com.example.profile

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileResponse(
    val name: String,
    val email: String,
    val gender: String,
    val clothes_count: Int,
    val looks_count: Int
)

