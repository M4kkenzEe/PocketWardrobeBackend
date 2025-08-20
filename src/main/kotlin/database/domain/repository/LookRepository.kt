package com.example.database.domain.repository

import com.example.database.data.model.Look
import com.example.database.data.model.LookDto

interface LookRepository {
    suspend fun getAllLooks(userId: Int): List<Look>
    suspend fun addLook(look: Look, userId: Int, imageUrl: String): Int
}