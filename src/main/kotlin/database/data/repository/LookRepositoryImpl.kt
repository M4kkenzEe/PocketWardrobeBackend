package com.example.database.data.repository

import com.example.database.data.model.ClotheDao
import com.example.database.data.model.ClotheTable
import com.example.database.data.model.Look
import com.example.database.data.model.LookDao
import com.example.database.data.model.LookItemDao
import com.example.database.data.model.UserTable
import com.example.database.data.model.daoToModel
import com.example.database.domain.repository.LookRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class LookRepositoryImpl : LookRepository {
    override suspend fun getLooks(userId: Int): List<Look> = suspendTransaction {
        LookDao.find { ClotheTable.userId eq userId }.map(::daoToModel)
    }

    override suspend fun addLook(look: Look, userId: Int): Boolean = suspendTransaction {
        try {
            val newLook = LookDao.new {
                this.userId = EntityID(userId, UserTable)
                this.name = look.name
            }

            look.lookItems.forEach { lookItem ->
                LookItemDao.new {
                    this.lookId = newLook.id
                    this.clotheId = ClotheDao.find {
                        (ClotheTable.imageUrl eq lookItem.clothe.imageUrl) and (ClotheTable.userId eq userId)
                    }.firstOrNull()?.id ?: throw IllegalArgumentException("Clothe not found for user")
                    this.size = lookItem.size
                    this.x = lookItem.x
                    this.y = lookItem.y
                    this.rotation = lookItem.rotation
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

}