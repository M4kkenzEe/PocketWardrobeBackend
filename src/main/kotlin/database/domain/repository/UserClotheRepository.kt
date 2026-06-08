package com.example.database.domain.repository

sealed class AddClotheResult {
    data class Success(val clotheId: Int) : AddClotheResult()
    data class FreemiumLimitReached(val current: Int, val limit: Int) : AddClotheResult()
}

interface UserClotheRepository {
    suspend fun addClotheToUser(userId: Int, clotheId: Int): Boolean
    suspend fun removeClotheFromUser(userId: Int, clotheId: Int): Boolean
    suspend fun restoreClotheForUser(userId: Int, clotheId: Int): Boolean
    suspend fun isClotheInUserWardrobe(userId: Int, clotheId: Int): Boolean
    suspend fun addClotheById(userId: Int, clotheId: Int): Boolean
    suspend fun countByUserId(userId: Int): Int
    suspend fun addClotheToUserAtomic(userId: Int, clotheId: Int, isPro: Boolean, limit: Int = 100): AddClotheResult
}
