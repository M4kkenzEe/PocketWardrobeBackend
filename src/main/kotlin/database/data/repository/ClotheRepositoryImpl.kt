package com.example.database.data.repository

import com.example.database.data.model.Clothe
import com.example.database.data.model.ClotheDao
import com.example.database.data.model.ClotheTable
import com.example.database.data.model.UserTable
import com.example.database.data.model.daoToModel
import com.example.database.domain.repository.ClotheRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.deleteWhere

class ClotheRepositoryImpl : ClotheRepository {
    override suspend fun getAllClothes(userId: Int): List<Clothe> = suspendTransaction {
        ClotheDao.find { ClotheTable.userId eq userId }.map(::daoToModel)
    }

    override suspend fun getClotheByName(name: String, idUser: Int): Clothe = suspendTransaction {
        ClotheDao
            .find { (ClotheTable.name eq name) and (ClotheTable.userId eq idUser) }
            .map(::daoToModel)
            .first()
    }

    override suspend fun getClotheById(clotheId: Int, idUser: Int): Clothe = suspendTransaction {
        ClotheDao
            .find { (ClotheTable.id eq clotheId) and (ClotheTable.userId eq idUser) }
            .map(::daoToModel)
            .first()
    }

    override suspend fun addClothe(clothe: Clothe, idUser: Int): Int = suspendTransaction {
        ClotheDao.new {
            userId = EntityID(idUser, UserTable)
            name = clothe.name
            imageUrl = clothe.imageUrl
            storeUrl = clothe.storeUrl
        }.id.value
    }

    override suspend fun removeClothe(name: String): Boolean = suspendTransaction {
        val rowsDeleted = ClotheTable.deleteWhere {
            ClotheTable.name eq name
        }
        rowsDeleted == 1
    }
}