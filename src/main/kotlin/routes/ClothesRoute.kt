package com.example.routes

import com.example.usecases.ClotheUseCase
import com.example.database.data.model.Clothe
import com.example.database.domain.repository.ClotheRepository
import com.example.database.domain.repository.UserClotheRepository
import com.example.services.RemoveBgService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import org.koin.ktor.ext.inject
import java.io.File
import java.nio.file.Path
import java.util.*

private val uploadsDirPath: Path = Path.of(System.getenv("UPLOADS_DIRECTORY") ?: "uploads").toAbsolutePath()


fun Application.clothes() {
    val clotheRepository: ClotheRepository by inject()
    val userClotheRepository: UserClotheRepository by inject()
    val removeBgService: RemoveBgService by inject()
    val usecase: ClotheUseCase by inject()

    routing {
        staticFiles("/images", uploadsDirPath.toFile())
        authenticate {
            route("/clothes") {
                get {
                    val userId = getUserIdOrThrow(call)
                    try {
                        call.respond(clotheRepository.getAllClothes(userId))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "${e.localizedMessage}")
                    }

                }

                get("/byName/{clotheName}") {
                    val userId = getUserIdOrThrow(call)
                    val name = call.parameters["clotheName"]?.takeIf { it.isNotBlank() }
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing clotheName")
                    val clothe = clotheRepository.getClotheByName(name, userId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, "Clothe not found")
                    call.respond(clothe)
                }

                get("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid id")
                    val clothe = clotheRepository.getClotheById(id)
                        ?: return@get call.respond(HttpStatusCode.NotFound, "Clothe not found")
                    call.respond(clothe)
                }

                post {
                    val userId = getUserIdOrThrow(call)
                    val form = parseMultipartForm(call)
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing required fields")

                    val mimeType = getMimeType(File(form.originalFileName))
                    val processedImage =
                        removeBgService.processImage(form.imageBytes, form.originalFileName, mimeType).getOrNull()
                            ?: return@post call.respond(HttpStatusCode.InternalServerError, "Image processing failed")

                    val imageUrl = saveImage(processedImage)
                    val clothe = Clothe(name = form.name, imageUrl = imageUrl, storeUrl = form.storeUrl)
                    val saved = clotheRepository.addClothe(clothe)

                    // Add clothe to user's wardrobe
                    userClotheRepository.addClotheToUser(userId, saved.id!!)

                    val result = clotheRepository.getClotheById(saved.id)
                        ?: return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            "Failed to retrieve created clothe"
                        )
                    call.respond(HttpStatusCode.Created, result)
                }

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
                        ?: return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            "Failed to retrieve created clothe"
                        )
                    call.respond(HttpStatusCode.Created, result)
                }

                get("/byLookId") {
                    val userId = getUserIdOrThrow(call)
                    val lookId = call.parameters["lookId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid lookId")

                    try {
                        val result = usecase.getClothesByLookId(lookId = lookId, userId)
                        call.respond(HttpStatusCode.OK, result)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Err: ${e.localizedMessage}")
                    }
                }

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
    return "http://localhost:8080/images/$fileName"
}

private data class MultipartForm(
    val name: String,
    val storeUrl: String,
    val imageBytes: ByteArray,
    val originalFileName: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MultipartForm

        if (name != other.name) return false
        if (storeUrl != other.storeUrl) return false
        if (!imageBytes.contentEquals(other.imageBytes)) return false
        if (originalFileName != other.originalFileName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + storeUrl.hashCode()
        result = 31 * result + imageBytes.contentHashCode()
        result = 31 * result + originalFileName.hashCode()
        return result
    }
}

private suspend fun parseMultipartForm(call: ApplicationCall): MultipartForm? {
    var name: String? = null
    var storeUrl: String? = null
    var imageBytes: ByteArray? = null
    var originalFileName: String? = null

    call.receiveMultipart().forEachPart { part ->
        when (part) {
            is PartData.FormItem -> when (part.name) {
                "name" -> name = part.value
                "storeUrl" -> storeUrl = part.value
            }

            is PartData.FileItem -> if (part.name == "image") {
                originalFileName = part.originalFileName ?: "image.png"
                imageBytes = part.provider().toInputStream().readBytes()
            }

            else -> {}
        }
        part.dispose()
    }

    return if (name != null && storeUrl != null && imageBytes != null && originalFileName != null) {
        MultipartForm(name, storeUrl, imageBytes, originalFileName)
    } else null
}
