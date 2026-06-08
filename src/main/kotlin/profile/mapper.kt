package com.example.profile

import com.example.database.data.model.User
import kotlinx.datetime.Clock

fun User.toProfileResponse(clothesCount: Int, looksCount: Int) = UserProfileResponse(
    name = username,
    email = email,
    gender = gender ?: "OTHER",
    clothes_count = clothesCount,
    looks_count = looksCount,
    is_pro = isPro,
    pro_trial_ends_at = proUntil
        ?.takeIf { it > Clock.System.now() }
        ?.toString()
)