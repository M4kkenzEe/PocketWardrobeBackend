package com.example.admin

import com.example.database.data.model.isProActive
import com.example.database.domain.model.UserWithClothesCount
import kotlinx.serialization.Serializable

@Serializable
data class AdminCreateUserRequest(
    val email: String,
    val password: String
)

@Serializable
data class AdminUserResponse(
    val id: Int,
    val email: String,
    val createdAt: String?,
    val isPro: Boolean,
    val clothesCount: Int
)

fun UserWithClothesCount.toAdminResponse() = AdminUserResponse(
    id = user.userId,
    email = user.email,
    createdAt = user.createdAt?.toString(),
    isPro = user.isProActive(),
    clothesCount = clothesCount
)
