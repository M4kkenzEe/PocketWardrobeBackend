package com.example.database.data.repository

import com.example.database.data.model.LookTable
import com.example.database.data.model.UserLookDao
import com.example.database.data.model.UserLookTable
import com.example.database.data.model.UserTable
import com.example.database.domain.repository.UserLookRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class UserLookRepositoryImpl : UserLookRepository {
    override suspend fun addLookToUser(userId: Int, lookId: Int): Boolean = suspendTransaction {
        // Check if relation already exists
        val existing = UserLookDao.find {
            (UserLookTable.userId eq userId) and (UserLookTable.lookId eq lookId)
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
            UserLookDao.new {
                this.userId = EntityID(userId, UserTable)
                this.lookId = EntityID(lookId, LookTable)
                this.isDeleted = false
            }
            true
        }
    }

    override suspend fun removeLookFromUser(userId: Int, lookId: Int): Boolean = suspendTransaction {
        val relation = UserLookDao.find {
            (UserLookTable.userId eq userId) and
            (UserLookTable.lookId eq lookId) and
            (UserLookTable.isDeleted eq false)
        }.firstOrNull()

        if (relation != null) {
            relation.isDeleted = true
            true
        } else {
            false
        }
    }

    override suspend fun restoreLookForUser(userId: Int, lookId: Int): Boolean = suspendTransaction {
        val relation = UserLookDao.find {
            (UserLookTable.userId eq userId) and
            (UserLookTable.lookId eq lookId) and
            (UserLookTable.isDeleted eq true)
        }.firstOrNull()

        if (relation != null) {
            relation.isDeleted = false
            true
        } else {
            false
        }
    }

    override suspend fun isLookInUserWardrobe(userId: Int, lookId: Int): Boolean = suspendTransaction {
        UserLookDao.find {
            (UserLookTable.userId eq userId) and
            (UserLookTable.lookId eq lookId) and
            (UserLookTable.isDeleted eq false)
        }.firstOrNull() != null
    }

    override suspend fun countByUserId(userId: Int): Int = suspendTransaction {
        UserLookDao.find {
            (UserLookTable.userId eq userId) and (UserLookTable.isDeleted eq false)
        }.count().toInt()
    }
}
