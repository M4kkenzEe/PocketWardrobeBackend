package database.data.repository

import com.example.database.data.model.ClotheTable
import com.example.database.data.model.Look
import com.example.database.data.model.LookDao
import com.example.database.data.model.LookDto
import com.example.database.data.model.LookItemDao
import com.example.database.data.model.LookTable
import com.example.database.data.model.UserTable
import com.example.database.data.model.daoToModel
import com.example.database.domain.repository.LookRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class LookRepositoryImpl : LookRepository {
    override suspend fun getAllLooks(userId: Int): List<Look> = suspendTransaction {
        LookDao.find { LookTable.userId eq userId }.map(::daoToModel)
    }

    override suspend fun addLook(look: Look, userId: Int, imageUrl: String): Int = suspendTransaction {
        val newLook = LookDao.new {
            this.userId = EntityID(userId, UserTable)
            this.name = look.name
            this.url = look.url
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

        newLook.id.value
    }
}