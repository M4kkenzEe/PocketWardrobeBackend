package com.example.routes

import com.example.database.data.model.Clothe
import com.example.database.domain.repository.ClotheRepository
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
    val removeBgService: RemoveBgService by inject()

    routing {
        staticFiles("/images", uploadsDirPath.toFile())
        authenticate {
            route("/clothes") {
                get {
                    val userId = getUserIdOrThrow(call)
                    call.respond(clotheRepository.getAllClothes(userId))
                }

                get("/byName/{clotheName}") {
                    val userId = getUserIdOrThrow(call)
                    val name = call.parameters["clotheName"]?.takeIf { it.isNotBlank() }
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing clotheName")
                    call.respond(clotheRepository.getClotheByName(name, userId))
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
                    val saved = clotheRepository.addClothe(clothe, userId)
                    val result = clotheRepository.getClotheById(saved.id!!, userId)
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
                    val saved = clotheRepository.addClothe(clothe, userId)
                    val result = clotheRepository.getClotheById(saved.id!!, userId)
                    call.respond(HttpStatusCode.Created, result)
                }

                delete("/{clotheName}") {
                    val name = call.parameters["clotheName"]?.takeIf { it.isNotBlank() }
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing clotheName")
                    if (clotheRepository.removeClothe(name)) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
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
