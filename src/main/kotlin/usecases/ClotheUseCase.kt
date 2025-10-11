package com.example.usecases

import com.example.database.data.model.Clothe
import com.example.database.domain.repository.LookRepository

class ClotheUseCase(private val lookRepository: LookRepository) {
    suspend fun getClothesByLookId(lookId: Int, userId: Int): List<Clothe> {
        val look = lookRepository.getLookById(lookId, userId)
        val result = mutableListOf<Clothe>()
        look.lookItems.forEach { result.add(it.clothe) }
        return result.toList()
    }
}