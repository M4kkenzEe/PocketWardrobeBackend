package com.example.database.domain.model

data class LookDraft(
    val lookId: Int,
    val url: String,
    val clotheIds: Set<Int>,
    val isSaved: Boolean
)
