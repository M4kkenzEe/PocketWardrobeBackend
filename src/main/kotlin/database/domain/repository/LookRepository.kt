package com.example.database.domain.repository

import com.example.database.data.model.Look

interface LookRepository {
    suspend fun getLooks(userId: Int): List<Look>
    suspend fun addLook(look: Look, userId: Int): Boolean
}