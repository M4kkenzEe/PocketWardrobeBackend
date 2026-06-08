package com.example.profile

import kotlinx.serialization.Serializable

@Serializable
data class QuotaInfo(
    val recommendations_today: Int,
    val recommendations_limit: Int,
    val reset_at: String
)

@Serializable
data class UserProfileResponse(
    val name: String,
    val email: String,
    val gender: String,
    val clothes_count: Int,
    val looks_count: Int,
    val is_pro: Boolean,
    val pro_trial_ends_at: String?,
    val quota: QuotaInfo
)

