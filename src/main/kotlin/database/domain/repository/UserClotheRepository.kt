package com.example.database.domain.repository

interface UserClotheRepository {
    suspend fun addClotheToUser(userId: Int, clotheId: Int): Boolean
    suspend fun removeClotheFromUser(userId: Int, clotheId: Int): Boolean
    suspend fun restoreClotheForUser(userId: Int, clotheId: Int): Boolean
    suspend fun isClotheInUserWardrobe(userId: Int, clotheId: Int): Boolean
    suspend fun addClotheById(userId: Int, clotheId: Int): Boolean
    suspend fun countByUserId(userId: Int): Int
}
