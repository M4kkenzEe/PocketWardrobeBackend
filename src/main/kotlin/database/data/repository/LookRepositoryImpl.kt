package database.data.repository

import com.example.database.data.model.*
import com.example.database.domain.model.LookPreview
import com.example.database.domain.repository.LookRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class LookRepositoryImpl : LookRepository {
    override suspend fun getAllLooks(userId: Int): List<Look> = suspendTransaction {
        // Find all user_looks relations for this user where isDeleted = false
        val userLooks = UserLookDao.find {
            (UserLookTable.userId eq userId) and (UserLookTable.isDeleted eq false)
        }

        // Get the look IDs
        val lookIds = userLooks.map { it.lookId.value }

        // Fetch the actual looks
        LookDao.find { LookTable.id inList lookIds }.map(::daoToModel)
    }

    override suspend fun addLook(look: Look, userId: Int, imageUrl: String, generatedByAi: Boolean): Int = suspendTransaction {
        val newLook = LookDao.new {
            this.name = look.name
            this.url = look.url
            this.generatedByAi = generatedByAi
        }

        look.lookItems.forEach { item ->
            LookItemDao.new {
                this.lookId = newLook.id
                this.clotheId =
                    EntityID(item.clothe.id!!, ClotheTable)
                this.size = item.size
                this.x = item.x
                this.y = item.y
                this.z = item.z
                this.rotation = item.rotation
            }
        }

        // Create UserLook relation
        UserLookDao.new {
            this.userId = EntityID(userId, UserTable)
            this.lookId = newLook.id
            this.isDeleted = false
        }

        newLook.id.value
    }

    override suspend fun updateLookUrl(lookId: Int, url: String) = suspendTransaction {
        LookDao.findById(lookId)?.url = url
    }

    override suspend fun getLookById(lookId: Int, userId: Int): Look = suspendTransaction {
        // Check if user has access to this look (and it's not deleted)
        val userLook = UserLookDao.find {
            (UserLookTable.userId eq userId) and
            (UserLookTable.lookId eq lookId) and
            (UserLookTable.isDeleted eq false)
        }.firstOrNull() ?: throw IllegalArgumentException("Look not found or not accessible")

        LookDao.findById(lookId)?.let(::daoToModel)
            ?: throw IllegalArgumentException("Look not found")
    }

    override suspend fun getLookList(userId: Int): List<LookPreview> = suspendTransaction {
        // Find all user_looks relations for this user where isDeleted = false
        val userLooks = UserLookDao.find {
            (UserLookTable.userId eq userId) and (UserLookTable.isDeleted eq false)
        }

        // Get the look IDs
        val lookIds = userLooks.map { it.lookId.value }

        // Fetch the actual looks and map directly from DAO to preserve generatedByAi
        LookDao.find { LookTable.id inList lookIds }.map { dao ->
            LookPreview(
                id = dao.id.value,
                url = dao.url,
                name = dao.name,
                generatedByAi = dao.generatedByAi
            )
        }
    }
}


