package com.example.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class GeneratedLook(
    @SerialName("look_id") val lookId: Int,
    val score: Float
)

@Serializable
data class GenerateLookResponse(
    val looks: List<GeneratedLook>
)

@Serializable
private data class GenerateFromItemRequest(
    @SerialName("user_id") val userId: Int,
    @SerialName("anchor_clothe_id") val anchorClotheId: Int,
    val limit: Int
)

@Serializable
private data class AnchorLookItems(val items: List<Int>)

@Serializable
private data class GenerateFromItemResponse(
    val looks: List<AnchorLookItems>,
    @SerialName("all_saved") val allSaved: Boolean
)

@Serializable
private data class MaterializeLookRequest(
    @SerialName("user_id") val userId: Int,
    @SerialName("item_ids") val itemIds: List<Int>,
    val draft: Boolean
)

@Serializable
private data class MaterializeLookResponse(@SerialName("look_id") val lookId: Int)

/** Sets of clothe ids composed around an anchor clothe; [allSaved] means every possible look is already in "my looks". */
data class AnchorLooks(val clotheSets: List<List<Int>>, val allSaved: Boolean)

/** Why RecommendationService refused to compose looks around an anchor clothe. */
sealed interface AnchorRejection {
    data object ClotheNotFound : AnchorRejection
    data class NotEnoughForAnchor(val missing: List<String>) : AnchorRejection
    data object UnsupportedCategory : AnchorRejection
}

class AnchorRejectedException(val rejection: AnchorRejection) : RuntimeException(rejection.toString())

class GenerateLookService(
    private val client: HttpClient,
    private val generateLookServiceUrl: String
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun generateLooks(userId: Int): Result<GenerateLookResponse> {
        return try {
            val response = client.get("$generateLookServiceUrl/generate/$userId")
            val body = response.bodyAsText()
            if (response.status.isSuccess()) {
                Result.success(json.decodeFromString<GenerateLookResponse>(body))
            } else if (response.status.value in 400..499) {
                val errorKey = runCatching {
                    json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
                }.getOrNull() ?: body
                Result.failure(IllegalArgumentException(errorKey))
            } else {
                Result.failure(IllegalStateException("Look generation service error ${response.status.value}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateFromItem(userId: Int, anchorClotheId: Int, limit: Int): Result<AnchorLooks> {
        return try {
            val response = client.post("$generateLookServiceUrl/generate-from-item") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(GenerateFromItemRequest(userId, anchorClotheId, limit)))
            }
            val body = response.bodyAsText()
            if (response.status.isSuccess()) {
                val parsed = json.decodeFromString<GenerateFromItemResponse>(body)
                Result.success(AnchorLooks(parsed.looks.map { it.items }, parsed.allSaved))
            } else {
                Result.failure(parseAnchorRejection(response.status.value, body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun materializeDraftLook(userId: Int, clotheIds: List<Int>): Result<Int> {
        return try {
            val response = client.post("$generateLookServiceUrl/materialize-look") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(MaterializeLookRequest(userId, clotheIds, draft = true)))
            }
            val body = response.bodyAsText()
            if (response.status.isSuccess()) {
                Result.success(json.decodeFromString<MaterializeLookResponse>(body).lookId)
            } else {
                Result.failure(IllegalStateException("Materialize look failed ${response.status.value}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseAnchorRejection(status: Int, body: String): Exception {
        val error = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val rejection = when (error?.get("error")?.jsonPrimitive?.content) {
            "clothe_not_found" -> AnchorRejection.ClotheNotFound
            "unsupported_category" -> AnchorRejection.UnsupportedCategory
            "not_enough_for_anchor" -> AnchorRejection.NotEnoughForAnchor(error.missingCategories())
            else -> null
        }
        return rejection?.let(::AnchorRejectedException)
            ?: IllegalStateException("Generate from item failed $status: $body")
    }

    private fun JsonObject.missingCategories(): List<String> =
        this["missing"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

    suspend fun renderLook(lookId: Int): Result<ByteArray> {
        return try {
            val response = client.get("$generateLookServiceUrl/look/$lookId/render")
            if (response.status.isSuccess()) {
                Result.success(response.body<ByteArray>())
            } else {
                Result.failure(IllegalStateException("Render failed for look $lookId: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
