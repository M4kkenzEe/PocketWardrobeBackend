package com.example.database.data.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

@Serializable
data class Clothe(
    val id: Int? = null,
    val imageUrl: String? = null,
    val name: String,
    val storeUrl: String? = null,
)

object ClotheTable : IntIdTable("clothes") {
    val name = varchar("name", 100)
    val imageUrl = varchar("image_url", 100)
    val storeUrl = varchar("store_url", 100)
}

class ClotheDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClotheDao>(ClotheTable)

    var name by ClotheTable.name
    var imageUrl by ClotheTable.imageUrl
    var storeUrl by ClotheTable.storeUrl
}

fun daoToModel(dao: ClotheDao) = Clothe(
    id = dao.id.value,
    name = dao.name,
    imageUrl = dao.imageUrl,
    storeUrl = dao.storeUrl
)