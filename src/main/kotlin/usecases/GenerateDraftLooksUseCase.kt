package com.example.usecases

import com.example.database.domain.model.LookDraft
import com.example.database.domain.repository.LookDraftRepository
import com.example.database.domain.repository.UserClotheRepository
import com.example.services.AnchorRejectedException
import com.example.services.AnchorRejection
import com.example.services.GenerateLookService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class DraftLook(val lookId: Int, val url: String)

sealed interface DraftLooksResult {
    data class Generated(val looks: List<DraftLook>, val allSaved: Boolean) : DraftLooksResult
    data class Rejected(val rejection: AnchorRejection) : DraftLooksResult
}

/** RecommendationService is unreachable or answered with something other than the documented contract. */
class LookServiceUnavailableException(cause: Throwable) : RuntimeException(cause.message, cause)

class GenerateDraftLooksUseCase(
    private val generateLookService: GenerateLookService,
    private val userClotheRepository: UserClotheRepository,
    private val lookDraftRepository: LookDraftRepository,
    private val lookRenderer: LookRenderer
) {
    suspend fun generate(userId: Int, anchorClotheId: Int): DraftLooksResult {
        // The backend is the authorization boundary: ownership is checked here, not delegated to RecommendationService (IDOR).
        if (!userClotheRepository.isClotheInUserWardrobe(userId, anchorClotheId)) {
            return DraftLooksResult.Rejected(AnchorRejection.ClotheNotFound)
        }

        val anchorLooks = generateLookService.generateFromItem(userId, anchorClotheId, DRAFT_LOOKS_LIMIT)
            .getOrElse { error ->
                if (error is AnchorRejectedException) return DraftLooksResult.Rejected(error.rejection)
                throw LookServiceUnavailableException(error)
            }

        val unsavedDrafts = lookDraftRepository.findUnsavedContainingClothe(userId, anchorClotheId)
        val looks = coroutineScope {
            anchorLooks.clotheSets
                .map { clotheIds -> async { draftFor(userId, anchorClotheId, clotheIds, unsavedDrafts) } }
                .awaitAll()
        }
        return DraftLooksResult.Generated(looks, anchorLooks.allSaved)
    }

    // Look rows and files are never deleted, so an unsaved draft with the same clothes is reused instead of re-created.
    private suspend fun draftFor(
        userId: Int,
        anchorClotheId: Int,
        clotheIds: List<Int>,
        unsavedDrafts: List<LookDraft>
    ): DraftLook {
        val reusable = unsavedDrafts.firstOrNull { it.clotheIds == clotheIds.toSet() }
        if (reusable != null) {
            val url = reusable.url.ifEmpty { lookRenderer.renderAndSave(reusable.lookId).orEmpty() }
            return DraftLook(reusable.lookId, url)
        }

        val lookId = generateLookService.materializeDraftLook(userId, clotheIds)
            .getOrElse { error -> throw LookServiceUnavailableException(error) }
        lookDraftRepository.createDraft(userId, lookId, anchorClotheId)
        return DraftLook(lookId, lookRenderer.renderAndSave(lookId).orEmpty())
    }

    private companion object {
        const val DRAFT_LOOKS_LIMIT = 3
    }
}
