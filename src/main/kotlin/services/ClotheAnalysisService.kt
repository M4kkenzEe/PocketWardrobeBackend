package com.example.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ClotheAnalysisService(
    private val client: HttpClient,
    private val analysisServiceUrl: String
) {
    suspend fun analyzeImage(fileBytes: ByteArray, fileName: String, mimeType: String): Result<ClotheAnalysisResult> {
        return try {
            val response: HttpResponse = client.submitFormWithBinaryData(
                url = analysisServiceUrl + ANALYZE_ENDPOINT,
                formData = formData {
                    append("file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    })
                }
            )

            if (response.status != HttpStatusCode.OK) {
                return Result.failure(Exception("Analysis service error: ${response.status}"))
            }

            val analysisResponse = response.body<AnalysisResponse>()
            val features = analysisResponse.features

            // Извлекаем данные из ответа
            val category = features.category?.category?.takeIf { it.isNotBlank() }
            val material = features.material?.material?.takeIf { it.isNotBlank() }
            val fit = features.fit?.fit?.takeIf { it.isNotBlank() }
            val season = features.season?.season?.takeIf { it.isNotBlank() }

            // Объединяем style_tags в строку через ", "
            val styleTags = features.style_tags
                ?.map { it.tag }
                ?.filter { it.isNotBlank() }
                ?.joinToString(", ")
                ?.takeIf { it.isNotBlank() }

            Result.success(
                ClotheAnalysisResult(
                    category = category,
                    material = material,
                    fit = fit,
                    season = season,
                    styleTags = styleTags
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val ANALYZE_ENDPOINT = "analyze"
    }
}

data class ClotheAnalysisResult(
    val category: String?,
    val material: String?,
    val fit: String?,
    val season: String?,
    val styleTags: String?
)

// Модели для десериализации JSON ответа от микросервиса
@Serializable
data class AnalysisResponse(
    val features: Features
)

@Serializable
data class Features(
    val category: CategoryInfo? = null,
    val material: MaterialInfo? = null,
    val fit: FitInfo? = null,
    val season: SeasonInfo? = null,
    @SerialName("style_tags")
    val style_tags: List<StyleTag>? = null,
    val colors: List<ColorInfo>? = null // игнорируем, но оставляем для совместимости
)

@Serializable
data class CategoryInfo(
    val category: String,
    val confidence: Double
)

@Serializable
data class MaterialInfo(
    val material: String,
    val confidence: Double
)

@Serializable
data class FitInfo(
    val fit: String,
    val confidence: Double
)

@Serializable
data class SeasonInfo(
    val season: String,
    val confidence: Double
)

@Serializable
data class StyleTag(
    val tag: String,
    val score: Double
)

@Serializable
data class ColorInfo(
    val r: Int,
    val g: Int,
    val b: Int
)
