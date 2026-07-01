package com.example.database.data.repository

import com.example.database.data.model.*
import com.example.database.domain.repository.ClotheRepository
import com.example.database.suspendTransaction
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll

class ClotheRepositoryImpl : ClotheRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getAllClothes(userId: Int): List<Clothe> = suspendTransaction {
        val userClothes = UserClotheDao.find {
            (UserClotheTable.userId eq userId) and (UserClotheTable.isDeleted eq false)
        }

        userClothes.mapNotNull { userClothe ->
            val clotheDao = ClotheDao.findById(userClothe.clotheId)
            clotheDao?.let { daoToModel(it) }
        }
    }

    override suspend fun getClothesPaginated(userId: Int, limit: Int, afterId: Int?): List<Clothe> = suspendTransaction {
        val allClotheIds = UserClotheDao.find {
            (UserClotheTable.userId eq userId) and (UserClotheTable.isDeleted eq false)
        }.map { it.clotheId.value }.sorted()

        val filteredIds = if (afterId != null) allClotheIds.filter { it > afterId } else allClotheIds
        val pageIds = filteredIds.take(limit)

        if (pageIds.isEmpty()) return@suspendTransaction emptyList()
        ClotheDao.find { ClotheTable.id inList pageIds }
            .orderBy(ClotheTable.id to SortOrder.ASC)
            .map { daoToModel(it) }
    }

    override suspend fun getClothesPaginatedFiltered(
        userId: Int, limit: Int, afterId: Int?, filter: ClotheFilter
    ): List<Clothe> = suspendTransaction {
        (UserClotheTable innerJoin ClotheTable)
            .selectAll()
            .where {
                var cond: Op<Boolean> =
                    (UserClotheTable.userId eq userId) and (UserClotheTable.isDeleted eq false)

                if (afterId != null) {
                    cond = cond and (ClotheTable.id greater afterId)
                }

                // Category filter (OR within group)
                filter.categories?.takeIf { it.isNotEmpty() }?.let { cats ->
                    cond = cond and (ClotheTable.category inList cats)
                }

                // Material filter — LIKE for combined values (e.g. "cotton + polyester")
                filter.materials?.takeIf { it.isNotEmpty() }?.let { mats ->
                    val matCond = mats
                        .map<String, Op<Boolean>> { m -> ClotheTable.material like "%$m%" }
                        .reduce { acc, op -> acc or op }
                    cond = cond and matCond
                }

                // Fit filter — LIKE for hybrid values (e.g. "slim-regular")
                filter.fits?.takeIf { it.isNotEmpty() }?.let { fits ->
                    val fitCond = fits
                        .map<String, Op<Boolean>> { f -> ClotheTable.fit like "%$f%" }
                        .reduce { acc, op -> acc or op }
                    cond = cond and fitCond
                }

                // Season filter (exact match, OR)
                filter.seasons?.takeIf { it.isNotEmpty() }?.let { seasons ->
                    cond = cond and (ClotheTable.season inList seasons)
                }

                // Style filter — searches within comma-separated style_tags
                filter.styles?.takeIf { it.isNotEmpty() }?.let { styles ->
                    val styleCond = styles
                        .map<String, Op<Boolean>> { s -> ClotheTable.styleTags like "%$s%" }
                        .reduce { acc, op -> acc or op }
                    cond = cond and styleCond
                }

                // Brand filter (exact match, OR)
                filter.brands?.takeIf { it.isNotEmpty() }?.let { brands ->
                    cond = cond and (ClotheTable.brand inList brands)
                }

                // Text search by name (case-insensitive via lowerCase)
                filter.searchQuery?.takeIf { it.isNotBlank() }?.let { q ->
                    val pattern = "%${q.trim().lowercase()}%"
                    cond = cond and (ClotheTable.name.lowerCase() like pattern)
                }

                // Occasion filter (case-insensitive exact match)
                filter.occasion?.takeIf { it.isNotBlank() }?.let { occ ->
                    cond = cond and (ClotheTable.occasion.lowerCase() eq occ.trim().lowercase())
                }

                cond
            }
            .orderBy(ClotheTable.id to SortOrder.ASC)
            .limit(limit)
            .map { row -> rowToClothe(row) }
    }

    override suspend fun getAvailableFilters(userId: Int): AvailableFiltersResponse = suspendTransaction {
        val rows = (UserClotheTable innerJoin ClotheTable)
            .selectAll()
            .where {
                (UserClotheTable.userId eq userId) and (UserClotheTable.isDeleted eq false)
            }
            .toList()

        val categories = rows.mapNotNull { it[ClotheTable.category] }.distinct().sorted()
        val materials = rows.mapNotNull { it[ClotheTable.material] }
            .flatMap { mat -> mat.split("+").map { it.trim() } }
            .filter { it.isNotBlank() }
            .distinct().sorted()
        val fits = rows.mapNotNull { it[ClotheTable.fit] }
            .flatMap { fit -> fit.split("-").map { it.trim() } }
            .filter { it.isNotBlank() }
            .distinct().sorted()
        val seasons = rows.mapNotNull { it[ClotheTable.season] }.distinct().sorted()
        val styles = rows.mapNotNull { it[ClotheTable.styleTags] }
            .flatMap { tags -> tags.split(",").map { it.trim() } }
            .filter { it.isNotBlank() }
            .distinct().sorted()
        val brands = rows.mapNotNull { it[ClotheTable.brand] }.distinct().sorted()
        val colors = rows.mapNotNull { it[ClotheTable.colors] }
            .flatMap { c -> runCatching { json.decodeFromString<List<String>>(c) }.getOrDefault(emptyList()) }
            .distinct()

        AvailableFiltersResponse(categories, materials, fits, seasons, styles, brands, colors)
    }

    override suspend fun getClotheByName(name: String, userId: Int): Clothe? = suspendTransaction {
        val userClothes = UserClotheDao.find {
            (UserClotheTable.userId eq userId) and (UserClotheTable.isDeleted eq false)
        }

        userClothes.firstNotNullOfOrNull { userClothe ->
            val clotheDao = ClotheDao.findById(userClothe.clotheId)
            clotheDao?.takeIf { it.name == name }?.let { daoToModel(it) }
        }
    }

    override suspend fun getClotheById(clotheId: Int): Clothe? = suspendTransaction {
        ClotheDao.findById(clotheId)?.let { daoToModel(it) }
    }

    override suspend fun getClotheByIdForUser(clotheId: Int, userId: Int): Clothe? = suspendTransaction {
        val userClothe = UserClotheDao.find {
            (UserClotheTable.userId eq userId) and
            (UserClotheTable.clotheId eq clotheId) and
            (UserClotheTable.isDeleted eq false)
        }.firstOrNull()

        userClothe?.let {
            ClotheDao.findById(clotheId)?.let { clotheDao -> daoToModel(clotheDao) }
        }
    }

    override suspend fun addClothe(
        clothe: Clothe,
        season: String?,
        fit: String?,
        material: String?,
        category: String?,
        styleTags: String?,
        colors: String?,
        occasion: String?
    ): Clothe = suspendTransaction {
        ClotheDao.new {
            name = clothe.name
            imageUrl = clothe.imageUrl.toString()
            storeUrl = clothe.storeUrl.toString()
            this.season = season
            this.fit = fit
            this.material = material
            this.category = category
            this.styleTags = styleTags
            this.brand = clothe.brand
            this.colors = colors
            this.occasion = occasion
        }.let { dao ->
            daoToModel(dao)
        }
    }

    override suspend fun updateClothe(
        clotheId: Int,
        name: String?,
        storeUrl: String?,
        season: String?,
        fit: String?,
        material: String?,
        brand: String?,
        occasion: String?,
        styleTags: String?
    ): Clothe? = suspendTransaction {
        val dao = ClotheDao.findById(clotheId) ?: return@suspendTransaction null
        name?.let { dao.name = it }
        storeUrl?.let { dao.storeUrl = it }
        season?.let { dao.season = it }
        fit?.let { dao.fit = it }
        material?.let { dao.material = it }
        brand?.let { dao.brand = it }
        occasion?.let { dao.occasion = it }
        styleTags?.let { dao.styleTags = it }
        daoToModel(dao)
    }
}
