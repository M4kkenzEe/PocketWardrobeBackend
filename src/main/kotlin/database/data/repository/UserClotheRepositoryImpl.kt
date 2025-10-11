package com.example.database.data.repository

import com.example.database.data.model.ClotheTable
import com.example.database.data.model.UserClotheDao
import com.example.database.data.model.UserClotheTable
import com.example.database.data.model.UserTable
import com.example.database.domain.repository.UserClotheRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class UserClotheRepositoryImpl : UserClotheRepository {
    override suspend fun addClotheToUser(userId: Int, clotheId: Int): Boolean = suspendTransaction {
        // Check if relation already exists
        val existing = UserClotheDao.find {
            (UserClotheTable.userId eq userId) and (UserClotheTable.clotheId eq clotheId)
        }.firstOrNull()

        if (existing != null) {
            // If exists but deleted, restore it
            if (existing.isDeleted) {
                existing.isDeleted = false
                true
            } else {
                false // Already exists and not deleted
            }
        } else {
            // Create new relation
            UserClotheDao.new {
                this.userId = EntityID(userId, UserTable)
                this.clotheId = EntityID(clotheId, ClotheTable)
                this.isDeleted = false
            }
            true
        }
    }

    override suspend fun removeClotheFromUser(userId: Int, clotheId: Int): Boolean = suspendTransaction {
        val relation = UserClotheDao.find {
            (UserClotheTable.userId eq userId) and
            (UserClotheTable.clotheId eq clotheId) and
            (UserClotheTable.isDeleted eq false)
        }.firstOrNull()

        if (relation != null) {
            relation.isDeleted = true
            true
        } else {
            false
        }
    }

    override suspend fun restoreClotheForUser(userId: Int, clotheId: Int): Boolean = suspendTransaction {
        val relation = UserClotheDao.find {
            (UserClotheTable.userId eq userId) and
            (UserClotheTable.clotheId eq clotheId) and
            (UserClotheTable.isDeleted eq true)
        }.firstOrNull()

        if (relation != null) {
            relation.isDeleted = false
            true
        } else {
            false
        }
    }

    override suspend fun isClotheInUserWardrobe(userId: Int, clotheId: Int): Boolean = suspendTransaction {
        UserClotheDao.find {
            (UserClotheTable.userId eq userId) and
            (UserClotheTable.clotheId eq clotheId) and
            (UserClotheTable.isDeleted eq false)
        }.firstOrNull() != null
    }
}
