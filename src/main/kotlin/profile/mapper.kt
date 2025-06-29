package com.example.profile

import com.example.database.data.model.User

fun User.toProfileResponse() = UserProfileResponse(
    name = username,
    email = email,
    gender = gender ?: "OTHER"
)