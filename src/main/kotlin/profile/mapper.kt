package com.example.profile

import com.example.database.data.model.User
import com.example.database.data.model.isProActive
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

fun User.toProfileResponse(clothesCount: Int, looksCount: Int, quotaToday: Int): UserProfileResponse {
    val moscowTz = TimeZone.of("Europe/Moscow")
    val today = Clock.System.now().toLocalDateTime(moscowTz).date
    val resetAt = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(moscowTz).toString()
    val isActive = isProActive()

    return UserProfileResponse(
        name = username,
        email = email,
        gender = gender ?: "OTHER",
        clothes_count = clothesCount,
        looks_count = looksCount,
        is_pro = isPro,
        pro_trial_ends_at = proUntil
            ?.takeIf { it > Clock.System.now() }
            ?.toString(),
        quota = QuotaInfo(
            recommendations_today = if (isActive) 0 else quotaToday,
            recommendations_limit = if (isActive) -1 else 1,
            reset_at = resetAt
        )
    )
}