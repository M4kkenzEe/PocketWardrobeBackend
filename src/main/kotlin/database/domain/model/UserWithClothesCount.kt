package com.example.database.domain.model

import com.example.database.data.model.User
import com.example.database.data.model.isProActive
import kotlinx.datetime.Instant

// Own model instead of wrapping User: legacy rows can have a NULL email, which User cannot represent.
data class UserWithClothesCount(
    val userId: Int,
    val email: String?,
    val createdAt: Instant?,
    val isPro: Boolean,
    val proUntil: Instant?,
    val clothesCount: Int
)

fun UserWithClothesCount.isProActive(): Boolean = isProActive(isPro, proUntil)

fun User.withClothesCount(clothesCount: Int) = UserWithClothesCount(
    userId = userId,
    email = email,
    createdAt = createdAt,
    isPro = isPro,
    proUntil = proUntil,
    clothesCount = clothesCount
)
