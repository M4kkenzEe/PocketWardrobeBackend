package com.example.database.data.repository

import com.example.database.data.model.*
import com.example.database.domain.repository.ClotheRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.and

class ClotheRepositoryImpl : ClotheRepository {
    override suspend fun getAllClothes(userId: Int): List<Clothe> = suspendTransaction {
        // Find all UserClothe relations for this user where isDeleted = false
        val userClothes = UserClotheDao.find {
            (UserClotheTable.userId eq userId) and (UserClotheTable.isDeleted eq false)
        }

        // Get the Clothe for each relation
        userClothes.mapNotNull { userClothe ->
            val clotheDao = ClotheDao.findById(userClothe.clotheId)
            clotheDao?.let { daoToModel(it) }
        }
    }

    override suspend fun getClotheByName(name: String, userId: Int): Clothe? = suspendTransaction {
        // Find all UserClothe relations for this user where isDeleted = false
        val userClothes = UserClotheDao.find {
            (UserClotheTable.userId eq userId) and (UserClotheTable.isDeleted eq false)
        }

        // Find the clothe with matching name
        userClothes.firstNotNullOfOrNull { userClothe ->
            val clotheDao = ClotheDao.findById(userClothe.clotheId)
            clotheDao?.takeIf { it.name == name }?.let { daoToModel(it) }
        }
    }

    override suspend fun getClotheById(clotheId: Int): Clothe? = suspendTransaction {
        ClotheDao.findById(clotheId)?.let { daoToModel(it) }
    }

    override suspend fun getClotheByIdForUser(clotheId: Int, userId: Int): Clothe? = suspendTransaction {
        // Check if user has this clothe and it's not deleted
        val userClothe = UserClotheDao.find {
            (UserClotheTable.userId eq userId) and
            (UserClotheTable.clotheId eq clotheId) and
            (UserClotheTable.isDeleted eq false)
        }.firstOrNull()

        userClothe?.let {
            ClotheDao.findById(clotheId)?.let { clotheDao -> daoToModel(clotheDao) }
        }
    }

    override suspend fun addClothe(clothe: Clothe): Clothe = suspendTransaction {
        ClotheDao.new {
            name = clothe.name
            imageUrl = clothe.imageUrl.toString()
            storeUrl = clothe.storeUrl.toString()
        }.let { dao ->
            Clothe(
                id = dao.id.value,
                name = dao.name,
                imageUrl = dao.imageUrl,
                storeUrl = dao.storeUrl
            )
        }
    }
}