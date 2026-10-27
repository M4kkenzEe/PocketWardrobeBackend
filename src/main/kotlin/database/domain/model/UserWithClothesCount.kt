package com.example.database.domain.model

import com.example.database.data.model.User

data class UserWithClothesCount(
    val user: User,
    val clothesCount: Int
)
