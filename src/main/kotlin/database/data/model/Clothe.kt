package com.example.database.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
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
    val styleTags: String? = null,
    val brand: String? = null,
    val colors: List<String>? = null,
    val occasion: String? = null,
    val rootClotheId: Int? = null
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
    val brand = varchar("brand", 100).nullable()
    val colors = text("colors").nullable()
    val occasion = varchar("occasion", 100).nullable()
    // Points at the clothe this row was cloned from. No FK: a future storage GC must be free
    // to delete the ancestor. Null for rows created by upload.
    val rootClotheId = integer("root_clothe_id").nullable()
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
    var brand by ClotheTable.brand
    var colors by ClotheTable.colors
    var occasion by ClotheTable.occasion
    var rootClotheId by ClotheTable.rootClotheId
}

private val json = Json { ignoreUnknownKeys = true }

fun daoToModel(dao: ClotheDao) = Clothe(
    id = dao.id.value,
    name = dao.name,
    imageUrl = dao.imageUrl,
    storeUrl = dao.storeUrl,
    season = dao.season,
    fit = dao.fit,
    material = dao.material,
    category = dao.category,
    styleTags = dao.styleTags,
    brand = dao.brand,
    colors = dao.colors?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() },
    occasion = dao.occasion,
    rootClotheId = dao.rootClotheId
)

fun rowToClothe(row: ResultRow) = Clothe(
    id = row[ClotheTable.id].value,
    name = row[ClotheTable.name],
    imageUrl = row[ClotheTable.imageUrl],
    storeUrl = row[ClotheTable.storeUrl],
    season = row[ClotheTable.season],
    fit = row[ClotheTable.fit],
    material = row[ClotheTable.material],
    category = row[ClotheTable.category],
    styleTags = row[ClotheTable.styleTags],
    brand = row[ClotheTable.brand],
    colors = row[ClotheTable.colors]?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() },
    occasion = row[ClotheTable.occasion],
    rootClotheId = row[ClotheTable.rootClotheId]
)

@Serializable
data class PaginatedClothesResponse(
    val data: List<Clothe>,
    val nextCursor: Int?,
    val hasMore: Boolean
)

data class ClotheFilter(
    val categories: List<String>? = null,
    val materials: List<String>? = null,
    val fits: List<String>? = null,
    val seasons: List<String>? = null,
    val styles: List<String>? = null,
    val brands: List<String>? = null,
    val color: String? = null,
    val colorTolerance: Double = 50.0,
    val searchQuery: String? = null,
    val occasion: String? = null
) {
    val isEmpty: Boolean get() = categories == null && materials == null &&
        fits == null && seasons == null && styles == null &&
        brands == null && color == null && searchQuery == null && occasion == null
}

@Serializable
data class AvailableFiltersResponse(
    val categories: List<String>,
    val materials: List<String>,
    val fits: List<String>,
    val seasons: List<String>,
    val styles: List<String>,
    val brands: List<String>,
    val colors: List<String>
)