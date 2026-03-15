package com.example.database.domain.repository

import com.example.database.data.model.Look
import com.example.database.data.model.LookDto
import com.example.database.domain.model.LookPreview

interface LookRepository {
    suspend fun getAllLooks(userId: Int): List<Look>
    suspend fun addLook(look: Look, userId: Int, imageUrl: String, generatedByAi: Boolean = false): Int
    suspend fun getLookById(lookId: Int, userId: Int): Look
    suspend fun getLookList(userId: Int): List<LookPreview>
    suspend fun updateLookUrl(lookId: Int, url: String)
}