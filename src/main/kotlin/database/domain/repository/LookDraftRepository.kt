package com.example.database.domain.repository

import com.example.database.domain.model.LookDraft

interface LookDraftRepository {
    suspend fun findUnsavedContainingClothe(userId: Int, clotheId: Int): List<LookDraft>
    suspend fun findDraft(userId: Int, lookId: Int): LookDraft?
    suspend fun createDraft(userId: Int, lookId: Int, anchorClotheId: Int)
    suspend fun markSaved(lookId: Int)
}
