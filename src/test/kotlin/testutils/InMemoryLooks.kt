package com.example.testutils

import com.example.database.data.model.Clothe
import com.example.database.data.model.Look
import com.example.database.data.model.LookItem
import com.example.database.data.model.SharedLook
import com.example.database.data.model.SharedLookDetails
import com.example.database.domain.model.LookDraft
import com.example.database.domain.model.LookPreview
import com.example.database.domain.repository.LookDraftRepository
import com.example.database.domain.repository.LookRepository
import com.example.database.domain.repository.SharedLookRepository
import com.example.database.domain.repository.UserLookRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stateful stand-in for looks, user_looks and look_drafts, with the same access rules as the Exposed
 * implementations: user_looks rows (not deleted) or the owner's unsaved draft grant access.
 * Concurrent collections: drafts are materialized from parallel coroutines.
 */
class InMemoryLooks : LookRepository, LookDraftRepository, UserLookRepository {

    private class LookRow(var url: String, val clotheIds: Set<Int>)
    private class UserLookRow(val userId: Int, val lookId: Int, var isDeleted: Boolean = false)
    private class DraftRow(val userId: Int, val lookId: Int, var isSaved: Boolean = false)

    private val nextLookId = AtomicInteger(1)
    private val looks = ConcurrentHashMap<Int, LookRow>()
    private val userLooks = CopyOnWriteArrayList<UserLookRow>()
    private val drafts = CopyOnWriteArrayList<DraftRow>()

    /** What RecommendationService does on /materialize-look: inserts a look and its items, no user_looks row. */
    fun createLook(clotheIds: Set<Int>, url: String = ""): Int {
        val lookId = nextLookId.getAndIncrement()
        looks[lookId] = LookRow(url, clotheIds)
        return lookId
    }

    fun createSavedLook(userId: Int, clotheIds: Set<Int>): Int {
        val lookId = createLook(clotheIds, url = "https://test/looks/saved-${nextLookId.get()}.png")
        userLooks += UserLookRow(userId, lookId)
        return lookId
    }

    fun draftCount(): Int = drafts.size

    fun lookCount(): Int = looks.size

    fun userLookCount(userId: Int): Int = userLooks.count { it.userId == userId && !it.isDeleted }

    fun urlOf(lookId: Int): String = looks.getValue(lookId).url

    fun clearUrl(lookId: Int) {
        looks.getValue(lookId).url = ""
    }

    private fun isSaved(userId: Int, lookId: Int) =
        userLooks.any { it.userId == userId && it.lookId == lookId && !it.isDeleted }

    private fun toDraft(row: DraftRow) = looks.getValue(row.lookId).let {
        LookDraft(row.lookId, it.url, it.clotheIds, row.isSaved)
    }

    // LookRepository

    override suspend fun getAllLooks(userId: Int): List<Look> = emptyList()

    override suspend fun addLook(look: Look, userId: Int, imageUrl: String, generatedByAi: Boolean): Int =
        createSavedLook(userId, look.lookItems.mapNotNull { it.clothe.id }.toSet())

    override suspend fun getLookById(lookId: Int, userId: Int): Look {
        val saved = isSaved(userId, lookId)
        val unsavedDraft = drafts.any { it.userId == userId && it.lookId == lookId && !it.isSaved }
        require(saved || unsavedDraft) { "Look not found or not accessible" }
        val row = looks.getValue(lookId)
        return Look(
            id = lookId,
            name = "look-$lookId",
            lookItems = row.clotheIds.map { LookItem(Clothe(id = it, name = "clothe-$it"), 0, 0f, 0f, 0f, 0f) },
            url = row.url,
            isDraft = !saved
        )
    }

    override suspend fun getLookList(userId: Int): List<LookPreview> =
        userLooks.filter { it.userId == userId && !it.isDeleted }
            .map { LookPreview(id = it.lookId, url = looks.getValue(it.lookId).url, name = "look-${it.lookId}") }

    override suspend fun updateLookUrl(lookId: Int, url: String) {
        looks.getValue(lookId).url = url
    }

    override suspend fun getSavedClotheIdsByLook(userId: Int): Map<Int, Set<Int>> =
        userLooks.filter { it.userId == userId && !it.isDeleted }
            .associate { it.lookId to looks.getValue(it.lookId).clotheIds }

    // LookDraftRepository

    override suspend fun findUnsavedContainingClothe(userId: Int, clotheId: Int): List<LookDraft> =
        drafts.filter { it.userId == userId && !it.isSaved }
            .map(::toDraft)
            .filter { clotheId in it.clotheIds }

    override suspend fun findDraft(userId: Int, lookId: Int): LookDraft? =
        drafts.firstOrNull { it.userId == userId && it.lookId == lookId }?.let(::toDraft)

    override suspend fun createDraft(userId: Int, lookId: Int, anchorClotheId: Int) {
        drafts += DraftRow(userId, lookId)
    }

    override suspend fun markSaved(lookId: Int) {
        drafts.filter { it.lookId == lookId }.forEach { it.isSaved = true }
    }

    // UserLookRepository

    override suspend fun addLookToUser(userId: Int, lookId: Int): Boolean {
        val existing = userLooks.firstOrNull { it.userId == userId && it.lookId == lookId }
        if (existing == null) {
            userLooks += UserLookRow(userId, lookId)
            return true
        }
        if (!existing.isDeleted) return false
        existing.isDeleted = false
        return true
    }

    override suspend fun removeLookFromUser(userId: Int, lookId: Int): Boolean {
        val row = userLooks.firstOrNull { it.userId == userId && it.lookId == lookId && !it.isDeleted } ?: return false
        row.isDeleted = true
        return true
    }

    override suspend fun restoreLookForUser(userId: Int, lookId: Int): Boolean = false

    override suspend fun isLookInUserWardrobe(userId: Int, lookId: Int): Boolean = isSaved(userId, lookId)

    override suspend fun countByUserId(userId: Int): Int = userLookCount(userId)
}

/** Share tokens follow the production rule: only a look in the owner's user_looks can be shared. */
class OwnerCheckingSharedLookRepository(private val userLooks: UserLookRepository) : SharedLookRepository {
    val createdTokens = mutableListOf<String>()

    override suspend fun createShareToken(lookId: Int, ownerId: Int): String {
        require(userLooks.isLookInUserWardrobe(ownerId, lookId)) { "Look not found or not accessible" }
        return "token-$lookId".also { createdTokens += it }
    }

    override suspend fun getLookByShareToken(shareToken: String): SharedLookDetails? = null
    override suspend fun revokeShareToken(shareToken: String, ownerId: Int): Boolean = false
    override suspend fun getShareTokensForLook(lookId: Int, ownerId: Int): List<SharedLook> = emptyList()
}
