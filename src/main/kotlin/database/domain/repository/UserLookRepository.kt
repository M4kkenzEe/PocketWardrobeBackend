package com.example.database.domain.repository

interface UserLookRepository {
    suspend fun addLookToUser(userId: Int, lookId: Int): Boolean
    suspend fun removeLookFromUser(userId: Int, lookId: Int): Boolean
    suspend fun restoreLookForUser(userId: Int, lookId: Int): Boolean
    suspend fun isLookInUserWardrobe(userId: Int, lookId: Int): Boolean
}
