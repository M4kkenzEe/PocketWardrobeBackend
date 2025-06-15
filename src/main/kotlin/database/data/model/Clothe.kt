package com.example.database.data.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

@Serializable
data class Clothe(
    val imageUrl: String,
    val name: String,
    val storeUrl: String,
)

object ClotheTable : IntIdTable("clothes") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val imageUrl = varchar("image_url", 100)
    val storeUrl = varchar("store_url", 100)
}

class ClotheDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClotheDao>(ClotheTable)

    var userId by ClotheTable.userId
    var name by ClotheTable.name
    var imageUrl by ClotheTable.imageUrl
    var storeUrl by ClotheTable.storeUrl
}

fun daoToModel(dao: ClotheDao) = Clothe(
    name = dao.name,
    imageUrl = dao.imageUrl,
    storeUrl = dao.storeUrl
)