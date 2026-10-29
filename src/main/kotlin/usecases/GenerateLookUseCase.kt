package com.example.usecases

import com.example.services.GenerateLookService

class GenerateLookUseCase(
    private val generateLookService: GenerateLookService,
    private val lookRenderer: LookRenderer
) {
    suspend fun generateAndSaveLooks(userId: Int): List<Int> {
        val response = generateLookService.generateLooks(userId)
            .getOrElse { e -> throw e }
        val lookIds = response.looks.map { it.lookId }
        lookIds.forEach { lookId -> lookRenderer.renderAndSave(lookId) }
        return lookIds
    }
}
