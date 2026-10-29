package com.example.database.data.repository

import com.example.database.data.model.LookDao
import com.example.database.data.model.LookDraftDao
import com.example.database.data.model.LookDraftTable
import com.example.database.data.model.LookItemDao
import com.example.database.data.model.LookItemTable
import com.example.database.data.model.LookTable
import com.example.database.data.model.UserTable
import com.example.database.domain.model.LookDraft
import com.example.database.domain.repository.LookDraftRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class LookDraftRepositoryImpl : LookDraftRepository {

    override suspend fun findUnsavedContainingClothe(userId: Int, clotheId: Int): List<LookDraft> = suspendTransaction {
        val lookIdsWithClothe = LookItemDao.find { LookItemTable.clotheId eq clotheId }.map { it.lookId.value }
        if (lookIdsWithClothe.isEmpty()) return@suspendTransaction emptyList()

        LookDraftDao.find {
            (LookDraftTable.userId eq userId) and
                LookDraftTable.savedAt.isNull() and
                (LookDraftTable.lookId inList lookIdsWithClothe)
        }.toList().toDrafts()
    }

    override suspend fun findDraft(userId: Int, lookId: Int): LookDraft? = suspendTransaction {
        LookDraftDao.find {
            (LookDraftTable.userId eq userId) and (LookDraftTable.lookId eq lookId)
        }.toList().toDrafts().firstOrNull()
    }

    override suspend fun createDraft(userId: Int, lookId: Int, anchorClotheId: Int) {
        suspendTransaction {
            LookDraftDao.new {
                this.userId = EntityID(userId, UserTable)
                this.lookId = EntityID(lookId, LookTable)
                this.anchorClotheId = anchorClotheId
                this.createdAt = System.currentTimeMillis()
            }
        }
    }

    override suspend fun markSaved(lookId: Int) {
        suspendTransaction {
            LookDraftDao.find { (LookDraftTable.lookId eq lookId) and LookDraftTable.savedAt.isNull() }
                .forEach { it.savedAt = System.currentTimeMillis() }
        }
    }

    private fun List<LookDraftDao>.toDrafts(): List<LookDraft> {
        if (isEmpty()) return emptyList()
        val lookIds = map { it.lookId.value }
        val clotheIdsByLook = LookItemDao.find { LookItemTable.lookId inList lookIds }
            .groupBy({ it.lookId.value }, { it.clotheId.value })
        val urlByLook = LookDao.find { LookTable.id inList lookIds }.associate { it.id.value to it.url }
        return map { draft ->
            val lookId = draft.lookId.value
            LookDraft(
                lookId = lookId,
                url = urlByLook[lookId].orEmpty(),
                clotheIds = clotheIdsByLook[lookId].orEmpty().toSet(),
                isSaved = draft.savedAt != null
            )
        }
    }
}
