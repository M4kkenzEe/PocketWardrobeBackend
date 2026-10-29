package com.example.usecases

import com.example.database.domain.repository.LookDraftRepository
import com.example.database.domain.repository.LookRepository
import com.example.database.domain.repository.UserLookRepository

class SaveLookDraftUseCase(
    private val lookDraftRepository: LookDraftRepository,
    private val lookRepository: LookRepository,
    private val userLookRepository: UserLookRepository
) {
    /** Returns the id of the look now in the user's "my looks", or null when the user has no such look or draft. */
    suspend fun save(userId: Int, lookId: Int): Int? {
        val draft = lookDraftRepository.findDraft(userId, lookId)
        val isInWardrobe = userLookRepository.isLookInUserWardrobe(userId, lookId)
        if (draft == null) return lookId.takeIf { isInWardrobe }
        if (isInWardrobe) return lookId

        val savedLookId = findSavedLookWithSameClothes(userId, draft.clotheIds)
            ?: lookId.also { userLookRepository.addLookToUser(userId, it) }
        lookDraftRepository.markSaved(lookId)
        return savedLookId
    }

    private suspend fun findSavedLookWithSameClothes(userId: Int, clotheIds: Set<Int>): Int? =
        lookRepository.getSavedClotheIdsByLook(userId).entries
            .firstOrNull { (_, savedClotheIds) -> savedClotheIds == clotheIds }
            ?.key
}
