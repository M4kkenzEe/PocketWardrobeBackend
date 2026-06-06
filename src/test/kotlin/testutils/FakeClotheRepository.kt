package com.example.testutils

import com.example.database.data.model.AvailableFiltersResponse
import com.example.database.data.model.Clothe
import com.example.database.data.model.ClotheFilter
import com.example.database.data.model.Look
import com.example.database.domain.model.LookPreview
import com.example.database.domain.repository.ClotheRepository
import com.example.database.domain.repository.LookRepository
import com.example.database.domain.repository.UserClotheRepository

const val TEST_USER_ID = 1

val CLOTHES_SEED = listOf(
    Clothe(
        id = 100,
        name = "white shirt",
        imageUrl = "",
        storeUrl = "",
        category = "tops",
        material = "cotton",
        fit = "slim",
        season = "summer",
        styleTags = "casual,minimalist",
        brand = "Nike",
        colors = listOf("#FFFFFF")
    ),
    Clothe(
        id = 101,
        name = "black tee",
        imageUrl = "",
        storeUrl = "",
        category = "tops",
        material = "cotton + polyester",
        fit = "slim",
        season = "all-season",
        styleTags = "casual,streetwear",
        brand = "Adidas",
        colors = listOf("#000000")
    ),
    Clothe(
        id = 102,
        name = "blue jeans",
        imageUrl = "",
        storeUrl = "",
        category = "pants",
        material = "denim",
        fit = "regular",
        season = "all-season",
        styleTags = "casual",
        brand = null,
        colors = listOf("#0000C8")
    ),
    Clothe(
        id = 103,
        name = "wool coat",
        imageUrl = "",
        storeUrl = "",
        category = "outerwear",
        material = "wool",
        fit = "loose",
        season = "winter",
        styleTags = "formal,classic",
        brand = "Zara",
        colors = listOf("#644628")
    ),
    Clothe(
        id = 104,
        name = "summer blouse",
        imageUrl = "",
        storeUrl = "",
        category = "tops",
        material = "linen",
        fit = "relaxed",
        season = "summer",
        styleTags = "boho,casual",
        brand = "HM",
        colors = listOf("#FDFCF8")
    ),
    Clothe(
        id = 128,
        name = "item128",
        imageUrl = "",
        storeUrl = "",
        category = "tops",
        material = "cotton",
        fit = "slim",
        season = "summer",
        styleTags = "casual",
        brand = "Nike",
        colors = null
    ),
    Clothe(
        id = 129,
        name = "item129",
        imageUrl = "",
        storeUrl = "",
        category = "pants",
        material = "denim",
        fit = "regular",
        season = "all-season",
        styleTags = "casual",
        brand = null,
        colors = null
    ),
    Clothe(
        id = 130,
        name = "item130",
        imageUrl = "",
        storeUrl = "",
        category = "tops",
        material = "linen",
        fit = "relaxed",
        season = "summer",
        styleTags = "boho",
        brand = "HM",
        colors = null
    ),
    Clothe(
        id = 131,
        name = "item131",
        imageUrl = "",
        storeUrl = "",
        category = "outerwear",
        material = "wool",
        fit = "loose",
        season = "winter",
        styleTags = "formal",
        brand = "Zara",
        colors = null
    ),
)

class FakeClotheRepository : ClotheRepository {

    private val store: MutableMap<Int, Clothe> = LinkedHashMap()
    private val userClothes: MutableMap<Int, MutableSet<Int>> = HashMap()

    fun seed(userId: Int, vararg clothes: Clothe) {
        clothes.forEach { c ->
            val id = c.id ?: error("Seed clothe must have an id")
            store[id] = c
            userClothes.getOrPut(userId) { LinkedHashSet() }.add(id)
        }
    }

    fun clear() {
        store.clear()
        userClothes.clear()
    }

    override suspend fun getAllClothes(userId: Int): List<Clothe> =
        userClothes[userId].orEmpty()
            .mapNotNull { store[it] }
            .sortedBy { it.id }

    override suspend fun getClothesPaginated(userId: Int, limit: Int, afterId: Int?): List<Clothe> =
        userClothes[userId].orEmpty()
            .mapNotNull { store[it] }
            .sortedBy { it.id }
            .let { list -> if (afterId != null) list.filter { it.id!! > afterId } else list }
            .take(limit)

    override suspend fun getClothesPaginatedFiltered(
        userId: Int, limit: Int, afterId: Int?, filter: ClotheFilter
    ): List<Clothe> =
        userClothes[userId].orEmpty()
            .mapNotNull { store[it] }
            .sortedBy { it.id }
            .filter { c ->
                (afterId == null || c.id!! > afterId) &&
                        (filter.categories.isNullOrEmpty() || c.category in filter.categories) &&
                        (filter.materials.isNullOrEmpty() || filter.materials.any { m ->
                            c.material?.contains(
                                m,
                                ignoreCase = true
                            ) == true
                        }) &&
                        (filter.fits.isNullOrEmpty() || filter.fits.any { f ->
                            c.fit?.contains(
                                f,
                                ignoreCase = true
                            ) == true
                        }) &&
                        (filter.seasons.isNullOrEmpty() || c.season in filter.seasons) &&
                        (filter.styles.isNullOrEmpty() || filter.styles.any { s ->
                            c.styleTags?.contains(
                                s,
                                ignoreCase = true
                            ) == true
                        }) &&
                        (filter.brands.isNullOrEmpty() || c.brand in filter.brands) &&
                        (filter.searchQuery.isNullOrBlank() || c.name.contains(
                            filter.searchQuery.trim(),
                            ignoreCase = true
                        )) &&
                        (filter.occasion.isNullOrBlank() || c.occasion?.equals(
                            filter.occasion.trim(),
                            ignoreCase = true
                        ) == true)
            }
            .take(limit)

    override suspend fun getAvailableFilters(userId: Int): AvailableFiltersResponse {
        val items = userClothes[userId].orEmpty().mapNotNull { store[it] }
        return AvailableFiltersResponse(
            categories = items.mapNotNull { it.category }.distinct().sorted(),
            materials = items.mapNotNull { it.material }
                .flatMap { it.split("+").map(String::trim) }
                .filter(String::isNotBlank).distinct().sorted(),
            fits = items.mapNotNull { it.fit }
                .flatMap { it.split("-").map(String::trim) }
                .filter(String::isNotBlank).distinct().sorted(),
            seasons = items.mapNotNull { it.season }.distinct().sorted(),
            styles = items.mapNotNull { it.styleTags }
                .flatMap { it.split(",").map(String::trim) }
                .filter(String::isNotBlank).distinct().sorted(),
            brands = items.mapNotNull { it.brand }.distinct().sorted(),
            colors = items.flatMap { it.colors.orEmpty() }.distinct()
        )
    }

    override suspend fun getClotheByName(name: String, userId: Int): Clothe? =
        userClothes[userId].orEmpty().mapNotNull { store[it] }.firstOrNull { it.name == name }

    override suspend fun getClotheById(clotheId: Int): Clothe? = store[clotheId]

    override suspend fun getClotheByIdForUser(clotheId: Int, userId: Int): Clothe? =
        if (clotheId in userClothes[userId].orEmpty()) store[clotheId] else null

    override suspend fun addClothe(
        clothe: Clothe, season: String?, fit: String?,
        material: String?, category: String?, styleTags: String?, colors: String?,
        occasion: String?
    ): Clothe {
        val id = (store.keys.maxOrNull() ?: 0) + 1
        val saved = clothe.copy(
            id = id, season = season, fit = fit,
            material = material, category = category, styleTags = styleTags,
            occasion = occasion
        )
        store[id] = saved
        return saved
    }
}

class FakeUserClotheRepository : UserClotheRepository {
    override suspend fun addClotheToUser(userId: Int, clotheId: Int): Boolean = true
    override suspend fun removeClotheFromUser(userId: Int, clotheId: Int): Boolean = true
    override suspend fun restoreClotheForUser(userId: Int, clotheId: Int): Boolean = true
    override suspend fun isClotheInUserWardrobe(userId: Int, clotheId: Int): Boolean = true
    override suspend fun addClotheById(userId: Int, clotheId: Int): Boolean = true
    override suspend fun countByUserId(userId: Int): Int = 0
}

class FakeLookRepository : LookRepository {
    override suspend fun getAllLooks(userId: Int): List<Look> = emptyList()
    override suspend fun addLook(look: Look, userId: Int, imageUrl: String, generatedByAi: Boolean): Int = 0
    override suspend fun getLookById(lookId: Int, userId: Int): Look = error("Not implemented in fake")
    override suspend fun getLookList(userId: Int): List<LookPreview> = emptyList()
    override suspend fun updateLookUrl(lookId: Int, url: String) {}
}
