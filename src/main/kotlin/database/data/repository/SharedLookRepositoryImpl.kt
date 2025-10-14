package com.example.database.data.repository

import com.example.database.data.model.*
import com.example.database.domain.repository.SharedLookRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.util.*

class SharedLookRepositoryImpl : SharedLookRepository {

    override suspend fun createShareToken(lookId: Int, ownerId: Int): String = suspendTransaction {
        // Check if the user owns this look
        val userLook = UserLookDao.find {
            (UserLookTable.userId eq ownerId) and
            (UserLookTable.lookId eq lookId) and
            (UserLookTable.isDeleted eq false)
        }.firstOrNull() ?: throw IllegalArgumentException("Look not found or not accessible")

        val shareToken = UUID.randomUUID().toString()

        SharedLookDao.new {
            this.lookId = EntityID(lookId, LookTable)
            this.shareToken = shareToken
            this.createdAt = System.currentTimeMillis()
        }
        shareToken
    }

    override suspend fun getLookByShareToken(shareToken: String): SharedLookDetails? = suspendTransaction {
        val sharedLook = SharedLookDao.find {
            SharedLookTable.shareToken eq shareToken
        }.firstOrNull() ?: return@suspendTransaction null

        val lookDao = sharedLook.look
        val look = daoToModel(lookDao)

        SharedLookDetails(
            look = look,
            shareToken = shareToken
        )
    }

    override suspend fun revokeShareToken(shareToken: String, ownerId: Int): Boolean = suspendTransaction {
        val sharedLook = SharedLookDao.find {
            SharedLookTable.shareToken eq shareToken
        }.firstOrNull() ?: return@suspendTransaction false

        val lookId = sharedLook.lookId.value

        // Check if the user owns this look
        val userLook = UserLookDao.find {
            (UserLookTable.userId eq ownerId) and
            (UserLookTable.lookId eq lookId) and
            (UserLookTable.isDeleted eq false)
        }.firstOrNull() ?: return@suspendTransaction false

        sharedLook.delete()
        true
    }

    override suspend fun getShareTokensForLook(lookId: Int, ownerId: Int): List<SharedLook> = suspendTransaction {
        // Check if the user owns this look
        val userLook = UserLookDao.find {
            (UserLookTable.userId eq ownerId) and
            (UserLookTable.lookId eq lookId) and
            (UserLookTable.isDeleted eq false)
        }.firstOrNull() ?: throw IllegalArgumentException("Look not found or not accessible")

        SharedLookDao.find {
            SharedLookTable.lookId eq lookId
        }.map(::daoToModel)
    }
}
