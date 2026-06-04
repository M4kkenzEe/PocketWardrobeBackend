package com.example.usecases

import com.example.auth.EnvironmentConfig
import com.example.database.data.model.Look
import com.example.database.domain.repository.LookRepository
import com.example.services.GenerateLookService
import java.io.File
import java.util.UUID

class GenerateLookUseCase(
    private val generateLookService: GenerateLookService,
    private val lookRepository: LookRepository
) {
    suspend fun generateAndSaveLooks(userId: Int): List<Look> {
        val response = generateLookService.generateLooks(userId)
            .getOrElse { e -> throw e }
        val lookIds = response.looks.map { it.lookId }
        lookIds.forEach { lookId -> renderAndSave(lookId) }
        return lookIds.mapNotNull { lookId ->
            runCatching { lookRepository.getLookById(lookId, userId) }.getOrNull()
        }
    }

    private suspend fun renderAndSave(lookId: Int) {
        generateLookService.renderLook(lookId)
            .onSuccess { bytes ->
                val fileName = "${UUID.randomUUID()}.png"
                val looksDir = File(EnvironmentConfig.looksDirectory).apply { if (!exists()) mkdirs() }
                File(looksDir, fileName).writeBytes(bytes)
                val url = "${EnvironmentConfig.getServerBaseUrl()}/looks/$fileName"
                lookRepository.updateLookUrl(lookId, url)
            }
            .onFailure { e ->
                println("Failed to render look $lookId: ${e.message}")
            }
    }
}
