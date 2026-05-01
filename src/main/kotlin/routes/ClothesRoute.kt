package com.example.routes

import com.example.auth.EnvironmentConfig
import com.example.auth.model.UserPrincipal
import com.example.database.data.model.Clothe
import com.example.database.data.model.ClotheFilter
import com.example.database.data.model.PaginatedClothesResponse
import com.example.database.data.model.RgbColor
import com.example.database.domain.repository.ClotheRepository
import com.example.database.domain.repository.UserClotheRepository
import com.example.services.RemoveBgService
import com.example.usecases.ClotheUseCase
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import validation.FileUploadValidator
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.math.sqrt

private val uploadsDirPath: Path = Path.of(EnvironmentConfig.uploadsDirectory).toAbsolutePath()


fun Application.clothes() {
    val clotheRepository: ClotheRepository by inject()
    val userClotheRepository: UserClotheRepository by inject()
    val removeBgService: RemoveBgService by inject()
    val clotheAnalysisService: com.example.services.ClotheAnalysisService by inject()
    val usecase: ClotheUseCase by inject()

    routing {
        staticFiles("/images", uploadsDirPath.toFile())

        // API v1 routes with rate limiting
        route("/api/v1") {
            rateLimit(RateLimitName("default")) {
                authenticate {
                    route("/clothes") {
                        // Get all clothes for user with optional filtering
                        // Without ?limit and no filters → returns full list (backward compat)
                        // With ?limit or filters → returns paginated PaginatedClothesResponse
                        get {
                            val userId = getUserIdOrThrow(call)
                            val limitParam = call.request.queryParameters["limit"]
                            val params = call.request.queryParameters

                            val filter = ClotheFilter(
                                categories = params.getAll("category")?.takeIf { it.isNotEmpty() },
                                materials = params.getAll("material")?.takeIf { it.isNotEmpty() },
                                fits = params.getAll("fit")?.takeIf { it.isNotEmpty() },
                                seasons = params.getAll("season")?.takeIf { it.isNotEmpty() },
                                styles = params.getAll("style")?.takeIf { it.isNotEmpty() },
                                brands = params.getAll("brand")?.takeIf { it.isNotEmpty() },
                                color = parseColorParam(params),
                                colorTolerance = params["color_tolerance"]?.toDoubleOrNull() ?: 50.0,
                                searchQuery = params["q"]
                            )

                            if (limitParam == null && filter.isEmpty) {
                                call.respond(clotheRepository.getAllClothes(userId))
                            } else {
                                val limit = limitParam?.toIntOrNull()?.coerceIn(1, 50) ?: 20
                                val cursor = params["cursor"]?.toIntOrNull()

                                // Over-fetch when color filter is active (applied in-memory)
                                val sqlLimit = if (filter.color != null) limit * 3 else limit + 1
                                val items = clotheRepository.getClothesPaginatedFiltered(userId, sqlLimit, cursor, filter)

                                // Apply color filter in-memory (Euclidean distance)
                                val filtered = if (filter.color != null) {
                                    items.filter { clothe ->
                                        clothe.colors?.any { c ->
                                            euclideanDistance(c, filter.color) <= filter.colorTolerance
                                        } == true
                                    }
                                } else items

                                val hasMore = filtered.size > limit
                                val page = if (hasMore) filtered.take(limit) else filtered
                                val nextCursor = if (hasMore) page.lastOrNull()?.id else null

                                call.respond(PaginatedClothesResponse(data = page, nextCursor = nextCursor, hasMore = hasMore))
                            }
                        }

                        // Get available filter values for user's collection
                        get("/filters") {
                            val userId = getUserIdOrThrow(call)
                            call.respond(clotheRepository.getAvailableFilters(userId))
                        }

                        // Get clothe by name
                        get("/byName/{clotheName}") {
                            val userId = getUserIdOrThrow(call)
                            val name = call.parameters["clotheName"]?.takeIf { it.isNotBlank() }
                                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing clotheName")
                            val clothe = clotheRepository.getClotheByName(name, userId)
                                ?: return@get call.respond(HttpStatusCode.NotFound, "Clothe not found")
                            call.respond(clothe)
                        }

                        // Get clothe by ID
                        get("/{id}") {
                            val id = call.parameters["id"]?.toIntOrNull()
                                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid id")
                            val clothe = clotheRepository.getClotheById(id)
                                ?: return@get call.respond(HttpStatusCode.NotFound, "Clothe not found")
                            call.respond(clothe)
                        }

                        // Get clothes by look ID
                        get("/byLookId") {
                            val userId = getUserIdOrThrow(call)
                            val lookId = call.parameters["lookId"]?.toIntOrNull()
                                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid lookId")

                            val result = usecase.getClothesByLookId(lookId = lookId, userId = userId)
                            call.respond(HttpStatusCode.OK, result)
                        }

                        // Delete clothe from user's wardrobe
                        delete("/{id}") {
                            val userId = getUserIdOrThrow(call)
                            val id = call.parameters["id"]?.toIntOrNull()
                                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")

                            if (userClotheRepository.removeClotheFromUser(userId, id)) {
                                call.respond(HttpStatusCode.NoContent)
                            } else {
                                call.respond(HttpStatusCode.NotFound, "Clothe not found in user's wardrobe")
                            }
                        }
                    }
                }
            }

            // Upload endpoints with stricter rate limiting
            rateLimit(RateLimitName("upload")) {
                authenticate {
                    route("/clothes") {
                        // Upload new clothe with image
                        post {
                            val userId = getUserIdOrThrow(call)
                            val form = parseMultipartForm(call)
                                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing required fields")

                            // Validate file
                            FileUploadValidator.validateFileBytes(form.imageBytes, form.contentType)

                            val mimeType = getMimeType(File(form.originalFileName))

                            // 1. Background removal
                            val processedImage =
                                removeBgService.processImage(form.imageBytes, form.originalFileName, mimeType)
                                    .getOrNull()
                                    ?: return@post call.respond(
                                        HttpStatusCode.InternalServerError,
                                        "Image processing failed"
                                    )

                            // 2. Clothe analysis
                            val analysisResult = clotheAnalysisService.analyzeImage(
                                form.imageBytes,
                                form.originalFileName,
                                mimeType
                            ).getOrNull()

                            val imageUrl = saveImage(processedImage)
                            val clothe = Clothe(name = form.name, imageUrl = imageUrl, storeUrl = form.storeUrl)

                            // Serialize colors to JSON string
                            val colorsJson = analysisResult?.colors?.let { colorList ->
                                Json.encodeToString(colorList.map { RgbColor(it.r, it.g, it.b) })
                            }

                            // 3. Save with analysis results
                            val saved = clotheRepository.addClothe(
                                clothe = clothe,
                                season = analysisResult?.season,
                                fit = analysisResult?.fit,
                                material = analysisResult?.material,
                                category = analysisResult?.category,
                                styleTags = analysisResult?.styleTags,
                                colors = colorsJson
                            )

                            // Add clothe to user's wardrobe
                            userClotheRepository.addClotheToUser(userId, saved.id!!)

                            val result = clotheRepository.getClotheById(saved.id)
                                ?: throw NoSuchElementException("Failed to retrieve created clothe")

                            call.respond(HttpStatusCode.Created, result)
                        }

                        // Add clothe from URL
                        get("/from_url") {
                            val userId = getUserIdOrThrow(call)
                            val url = call.parameters["url"]?.takeIf { it.isNotBlank() }
                                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing url")

                            val imageFile = removeBgService.getImageFromUrl(url).getOrNull()
                                ?: return@get call.respond(HttpStatusCode.InternalServerError, "Image download failed")

                            val imageUrl = saveImage(imageFile.readBytes())
                            val clothe = Clothe(name = "", imageUrl = imageUrl, storeUrl = url)
                            val saved = clotheRepository.addClothe(clothe)

                            // Add clothe to user's wardrobe
                            userClotheRepository.addClotheToUser(userId, saved.id!!)

                            val result = clotheRepository.getClotheById(saved.id)
                                ?: throw NoSuchElementException("Failed to retrieve created clothe")

                            call.respond(HttpStatusCode.Created, result)
                        }
                    }
                }
            }
        }
    }

    log.info("✓ Clothes routes configured at /api/v1/clothes")
}


fun getMimeType(file: File): String = when (file.extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "bmp" -> "image/bmp"
    "webp" -> "image/webp"
    else -> "application/octet-stream" // fallback
}


private fun getUserIdOrThrow(call: ApplicationCall): Int =
    call.principal<UserPrincipal>()?.userId ?: throw IllegalStateException("User not authenticated")

private fun saveImage(bytes: ByteArray): String {
    val fileName = "${UUID.randomUUID()}.png"
    File("$uploadsDirPath/$fileName").writeBytes(bytes)
    return "${EnvironmentConfig.getServerBaseUrl()}/images/$fileName"
}

private data class MultipartForm(
    val name: String,
    val storeUrl: String,
    val imageBytes: ByteArray,
    val originalFileName: String,
    val contentType: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MultipartForm

        if (name != other.name) return false
        if (storeUrl != other.storeUrl) return false
        if (!imageBytes.contentEquals(other.imageBytes)) return false
        if (originalFileName != other.originalFileName) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + storeUrl.hashCode()
        result = 31 * result + imageBytes.contentHashCode()
        result = 31 * result + originalFileName.hashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        return result
    }
}

private fun parseColorParam(params: Parameters): RgbColor? {
    val r = params["color_r"]?.toIntOrNull() ?: return null
    val g = params["color_g"]?.toIntOrNull() ?: return null
    val b = params["color_b"]?.toIntOrNull() ?: return null
    return RgbColor(r, g, b)
}

private fun euclideanDistance(c1: RgbColor, c2: RgbColor): Double {
    val dr = (c1.r - c2.r).toDouble()
    val dg = (c1.g - c2.g).toDouble()
    val db = (c1.b - c2.b).toDouble()
    return sqrt(dr * dr + dg * dg + db * db)
}

private suspend fun parseMultipartForm(call: ApplicationCall): MultipartForm? {
    var name: String? = null
    var storeUrl: String? = null
    var imageBytes: ByteArray? = null
    var originalFileName: String? = null
    var contentType: String? = null

    call.receiveMultipart().forEachPart { part ->
        when (part) {
            is PartData.FormItem -> when (part.name) {
                "name" -> name = part.value
                "storeUrl" -> storeUrl = part.value
            }

            is PartData.FileItem -> if (part.name == "image") {
                originalFileName = part.originalFileName ?: "image.png"
                contentType = part.contentType?.toString()
                imageBytes = part.provider().toInputStream().readBytes()
            }

            else -> {}
        }
        part.dispose()
    }

    return if (name != null && storeUrl != null && imageBytes != null && originalFileName != null) {
        MultipartForm(name, storeUrl, imageBytes, originalFileName, contentType)
    } else null
}
