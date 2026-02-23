package com.example.usecases

import com.example.database.data.model.Clothe
import com.example.database.data.model.Look
import com.example.database.data.model.LookItem
import com.example.database.domain.repository.ClotheRepository
import com.example.database.domain.repository.LookRepository
import com.example.database.domain.repository.SharedLookRepository
import com.example.database.domain.repository.UserClotheRepository
import com.example.auth.EnvironmentConfig
import java.io.File
import java.net.URI
import java.util.*

class ImportSharedLookUseCase(
    private val sharedLookRepository: SharedLookRepository,
    private val clotheRepository: ClotheRepository,
    private val lookRepository: LookRepository,
    private val userClotheRepository: UserClotheRepository
) {
    /**
     * Imports a full look (all clothes + look itself) to the target user's wardrobe
     * @param shareToken The share token
     * @param targetUserId The user who is importing (receiver)
     * @return Result with the new look ID
     */
    suspend fun importFullLook(shareToken: String, targetUserId: Int): Result<Int> {
        return try {
            // Get shared look details
            val sharedLookDetails = sharedLookRepository.getLookByShareToken(shareToken)
                ?: return Result.failure(Exception("Share link not found or expired"))

            val originalLook = sharedLookDetails.look

            // Import all clothes from the look
            val clotheIdMapping = mutableMapOf<Int, Int>() // originalId -> newId

            for (lookItem in originalLook.lookItems) {
                val originalClothe = lookItem.clothe

                // Copy clothe image
                val newImageUrl = copyImageFile(originalClothe.imageUrl, "uploads")

                // Create new clothe for target user
                val newClothe = Clothe(
                    name = originalClothe.name,
                    imageUrl = newImageUrl,
                    storeUrl = originalClothe.storeUrl
                )

                val savedClothe = clotheRepository.addClothe(newClothe)
                // Add clothe to user's wardrobe
                userClotheRepository.addClotheToUser(targetUserId, savedClothe.id!!)
                clotheIdMapping[originalClothe.id!!] = savedClothe.id
            }

            // Copy look image
            val newLookImageUrl = copyImageFile(originalLook.url, "looks")

            // Create new look items with new clothe IDs
            val newLookItems = originalLook.lookItems.map { originalItem ->
                LookItem(
                    clothe = Clothe(
                        id = clotheIdMapping[originalItem.clothe.id],
                        name = originalItem.clothe.name,
                        imageUrl = originalItem.clothe.imageUrl,
                        storeUrl = originalItem.clothe.storeUrl
                    ),
                    size = originalItem.size,
                    x = originalItem.x,
                    y = originalItem.y,
                    z = originalItem.z,
                    rotation = originalItem.rotation
                )
            }

            // Create new look for target user
            val newLook = Look(
                name = originalLook.name,
                lookItems = newLookItems,
                url = newLookImageUrl ?: ""
            )

            val newLookId = lookRepository.addLook(newLook, targetUserId, newLookImageUrl ?: "")

            Result.success(newLookId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports only selected clothes from a shared look
     * @param shareToken The share token
     * @param clotheIds IDs of clothes to import
     * @param targetUserId The user who is importing (receiver)
     * @return Result with list of new clothe IDs
     */
    suspend fun importSelectedClothes(
        shareToken: String,
        clotheIds: List<Int>,
        targetUserId: Int
    ): Result<List<Int>> {
        return try {
            // Get shared look details
            val sharedLookDetails = sharedLookRepository.getLookByShareToken(shareToken)
                ?: return Result.failure(Exception("Share link not found or expired"))

            val originalLook = sharedLookDetails.look
            val newClotheIds = mutableListOf<Int>()

            // Filter selected clothes
            val selectedClothes = originalLook.lookItems
                .map { it.clothe }
                .filter { clotheIds.contains(it.id) }

            for (originalClothe in selectedClothes) {
                // Copy clothe image
                val newImageUrl = copyImageFile(originalClothe.imageUrl, "uploads")

                // Create new clothe for target user
                val newClothe = Clothe(
                    name = originalClothe.name,
                    imageUrl = newImageUrl,
                    storeUrl = originalClothe.storeUrl
                )

                val savedClothe = clotheRepository.addClothe(newClothe)
                // Add clothe to user's wardrobe
                userClotheRepository.addClotheToUser(targetUserId, savedClothe.id!!)
                newClotheIds.add(savedClothe.id)
            }

            Result.success(newClotheIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Copies an image file from original location to target directory
     * @param sourceUrl Original image URL (e.g., "http://localhost:8080/images/uuid.png")
     * @param targetDir Target directory name (e.g., "uploads" or "looks")
     * @return New image URL
     */
    private fun copyImageFile(sourceUrl: String?, targetDir: String): String? {
        if (sourceUrl == null || sourceUrl.isBlank()) return null

        try {
            // Extract URL path from full URL regardless of host/port
            val urlPath = URI(sourceUrl).path.trimStart('/')

            // Map URL path to physical directory
            // "/images/*" is served from "uploads/" directory
            // "/looks/*" is served from "looks/" directory
            val physicalPath = when {
                urlPath.startsWith("images/") -> urlPath.replace("images/", "uploads/")
                urlPath.startsWith("looks/") -> urlPath // looks maps to looks
                else -> urlPath
            }

            val sourceFile = File(physicalPath)

            if (!sourceFile.exists()) {
                throw IllegalArgumentException("Source file not found: ${sourceFile.absolutePath} (from URL: $sourceUrl)")
            }

            // Generate new filename with UUID
            val extension = sourceFile.extension.ifEmpty { "png" }
            val newFileName = "${UUID.randomUUID()}.$extension"

            // Ensure target directory exists
            val targetDirFile = File(targetDir)
            if (!targetDirFile.exists()) {
                targetDirFile.mkdirs()
            }

            val targetFile = File(targetDir, newFileName)

            // Copy file
            sourceFile.copyTo(targetFile, overwrite = false)

            // Return new URL based on target directory
            // uploads -> /images, looks -> /looks
            val urlPrefix = when (targetDir) {
                "uploads" -> "images"
                "looks" -> "looks"
                else -> targetDir
            }

            return "${EnvironmentConfig.getServerBaseUrl()}/$urlPrefix/$newFileName"
        } catch (e: Exception) {
            throw Exception("Failed to copy image file from '$sourceUrl' to '$targetDir': ${e.message}", e)
        }
    }
}
