package com.example.usecases

import com.example.auth.EnvironmentConfig
import com.example.database.domain.repository.LookRepository
import com.example.services.GenerateLookService
import java.io.File
import java.util.UUID

class LookRenderer(
    private val generateLookService: GenerateLookService,
    private val lookRepository: LookRepository,
    private val looksDirectory: File = File(EnvironmentConfig.looksDirectory)
) {
    /** Renders the look, stores the PNG and saves its url. Returns the url, or null when rendering failed. */
    suspend fun renderAndSave(lookId: Int): String? =
        generateLookService.renderLook(lookId)
            .onFailure { e -> println("Failed to render look $lookId: ${e.message}") }
            .getOrNull()
            ?.let { bytes ->
                val fileName = "${UUID.randomUUID()}.png"
                looksDirectory.mkdirs()
                File(looksDirectory, fileName).writeBytes(bytes)
                val url = "${EnvironmentConfig.getServerBaseUrl()}/looks/$fileName"
                lookRepository.updateLookUrl(lookId, url)
                url
            }
}
