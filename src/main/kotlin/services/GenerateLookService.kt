package com.example.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GeneratedLook(
    @SerialName("look_id") val lookId: Int,
    val score: Float
)

@Serializable
data class GenerateLookResponse(
    val looks: List<GeneratedLook>
)

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
                val detail = runCatching {
                    json.parseToJsonElement(body).let {
                        it.toString().substringAfter("\"detail\":\"").substringBefore("\"")
                    }
                }.getOrDefault(body)
                Result.failure(IllegalArgumentException(detail))
            } else {
                Result.failure(IllegalStateException("Look generation service error ${response.status.value}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
