package com.example.database.domain.repository

import com.example.database.data.model.AvailableFiltersResponse
import com.example.database.data.model.Clothe
import com.example.database.data.model.ClotheFilter

interface ClotheRepository {
    suspend fun getAllClothes(userId: Int): List<Clothe>
    suspend fun getClothesPaginated(userId: Int, limit: Int, afterId: Int?): List<Clothe>
    suspend fun getClothesPaginatedFiltered(userId: Int, limit: Int, afterId: Int?, filter: ClotheFilter): List<Clothe>
    suspend fun getAvailableFilters(userId: Int): AvailableFiltersResponse
    suspend fun getClotheByName(name: String, userId: Int): Clothe?
    suspend fun getClotheById(clotheId: Int): Clothe?
    suspend fun getClotheByIdForUser(clotheId: Int, userId: Int): Clothe?

    /**
     * Finds the user's active clothe that descends from [rootId], or [rootId] itself.
     * Used to keep saving the same shared item idempotent.
     */
    suspend fun findUserClotheByRoot(userId: Int, rootId: Int): Clothe?

    suspend fun addClothe(
        clothe: Clothe,
        season: String? = null,
        fit: String? = null,
        material: String? = null,
        category: String? = null,
        styleTags: String? = null,
        colors: String? = null,
        occasion: String? = null,
        rootClotheId: Int? = null
    ): Clothe

    suspend fun updateClothe(
        clotheId: Int,
        name: String? = null,
        storeUrl: String? = null,
        season: String? = null,
        fit: String? = null,
        material: String? = null,
        brand: String? = null,
        occasion: String? = null,
        styleTags: String? = null
    ): Clothe?
}