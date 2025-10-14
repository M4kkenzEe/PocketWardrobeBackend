package com.example.database.domain.repository

import com.example.database.data.model.Look
import com.example.database.data.model.LookDto
import com.example.database.domain.model.LookPreview

interface LookRepository {
    suspend fun getAllLooks(userId: Int): List<Look>
    suspend fun addLook(look: Look, userId: Int, imageUrl: String): Int
    suspend fun getLookById(lookId: Int, userId: Int): Look
    suspend fun getLookList(userId: Int): List<LookPreview>
}