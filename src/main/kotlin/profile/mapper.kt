package com.example.profile

import com.example.database.data.model.User

fun User.toProfileResponse(clothesCount: Int, looksCount: Int) = UserProfileResponse(
    name = username,
    email = email,
    gender = gender ?: "OTHER",
    clothes_count = clothesCount,
    looks_count = looksCount
)