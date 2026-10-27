package com.example.admin

import com.example.database.domain.model.UserWithClothesCount
import com.example.database.domain.model.isProActive
import kotlinx.serialization.Serializable

@Serializable
data class AdminCreateUserRequest(
    val email: String,
    val password: String
)

@Serializable
data class AdminUserResponse(
    val id: Int,
    val email: String?,
    val createdAt: String?,
    val isPro: Boolean,
    val clothesCount: Int
)

fun UserWithClothesCount.toAdminResponse() = AdminUserResponse(
    id = userId,
    email = email,
    createdAt = createdAt?.toString(),
    isPro = isProActive(),
    clothesCount = clothesCount
)
