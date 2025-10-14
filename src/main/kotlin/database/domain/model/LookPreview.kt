package com.example.database.domain.model

import com.example.database.data.model.Look
import kotlinx.serialization.Serializable

@Serializable
data class LookPreview(
    val id: Int,
    val url: String = "",
    val name: String
) {
    companion object {
        fun toPreview(look: Look): LookPreview {
            return LookPreview(
                id = look.id!!,
                url = look.url,
                name = look.name
            )
        }
    }
}