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
    val season: String? = null,
    val fit: String? = null,
    val material: String? = null,
    val category: String? = null,
    val styleTags: String? = null
)

object ClotheTable : IntIdTable("clothes") {
    val name = varchar("name", 100)
    val imageUrl = varchar("image_url", 100)
    val storeUrl = varchar("store_url", 100)
    val season = varchar("season", 100).nullable()
    val fit = varchar("fit", 100).nullable()
    val material = varchar("material", 100).nullable()
    val category = varchar("category", 100).nullable()
    val styleTags = varchar("style_tags", 500).nullable()
}

class ClotheDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClotheDao>(ClotheTable)

    var name by ClotheTable.name
    var imageUrl by ClotheTable.imageUrl
    var storeUrl by ClotheTable.storeUrl
    var season by ClotheTable.season
    var fit by ClotheTable.fit
    var material by ClotheTable.material
    var category by ClotheTable.category
    var styleTags by ClotheTable.styleTags
}

fun daoToModel(dao: ClotheDao) = Clothe(
    id = dao.id.value,
    name = dao.name,
    imageUrl = dao.imageUrl,
    storeUrl = dao.storeUrl,
    season = dao.season,
    fit = dao.fit,
    material = dao.material,
    category = dao.category,
    styleTags = dao.styleTags
)

@Serializable
data class PaginatedClothesResponse(
    val data: List<Clothe>,
    val nextCursor: Int?,
    val hasMore: Boolean
)