package com.example.database.domain.repository

import com.example.database.data.model.Clothe

interface ClotheRepository {
    suspend fun getAllClothes(userId: Int): List<Clothe>
    suspend fun getClothesPaginated(userId: Int, limit: Int, afterId: Int?): List<Clothe>
    suspend fun getClotheByName(name: String, userId: Int): Clothe?
    suspend fun getClotheById(clotheId: Int): Clothe?
    suspend fun getClotheByIdForUser(clotheId: Int, userId: Int): Clothe?
    suspend fun addClothe(
        clothe: Clothe,
        season: String? = null,
        fit: String? = null,
        material: String? = null,
        category: String? = null,
        styleTags: String? = null
    ): Clothe
}