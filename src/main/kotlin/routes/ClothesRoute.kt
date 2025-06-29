package com.example.routes

import com.example.database.data.model.Clothe
import com.example.database.domain.repository.ClotheRepository
import com.example.services.RemoveBgService
import com.example.services.getMimeType
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import org.koin.ktor.ext.inject
import java.io.File
import java.util.*

const val UPLOADS_DIRECTORY = "/Users/yuriichernigovtsev/IdeaProjects/pocketwodrobe/uploads"

fun Application.clothes() {
    val clotheRepository: ClotheRepository by inject()
    val removeBgService: RemoveBgService by inject()
    routing {
        authenticate {
            staticFiles("/images", File(UPLOADS_DIRECTORY))
            route("/clothes") {
                get {
                    val userId = call.principal<UserPrincipal>()?.userId
                        ?: throw IllegalStateException("User not authenticated")
                    val clothes = clotheRepository.getAllClothes(userId)
                    call.respond(clothes)
                }

                get("/byName/{clotheName}") {
                    val userId = call.principal<UserPrincipal>()?.userId
                        ?: throw IllegalStateException("User not authenticated")
                    val name = call.parameters["clotheName"]
                    if (name == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }
                    val clothe = clotheRepository.getClotheByName(name, userId)
                    call.respond(clothe)
                }

                post {
                    val userId = call.principal<UserPrincipal>()?.userId
                        ?: throw IllegalStateException("User not authenticated")
                    try {
                        val multipart = call.receiveMultipart()
                        var name: String? = null
                        var storeUrl: String? = null
                        var imageBytes: ByteArray? = null
                        var originalFileName: String? = null

                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FormItem -> {
                                    when (part.name) {
                                        "name" -> name = part.value
                                        "storeUrl" -> storeUrl = part.value
                                    }
                                }

                                is PartData.FileItem -> {
                                    if (part.name == "image") {
                                        originalFileName = part.originalFileName ?: "image.png"
                                        imageBytes = part.provider().toInputStream().readBytes()
                                    }
                                }

                                else -> {}
                            }
                            part.dispose()
                        }

                        if (name == null || storeUrl == null || imageBytes == null || originalFileName == null) {
                            call.respond(HttpStatusCode.BadRequest, "Missing required fields: name, storeUrl, or image")
                            return@post
                        }

                        // Получаем MIME-тип для файла
                        val mimeType = getMimeType(File(originalFileName))

                        // Обрабатываем изображение с помощью RemoveBgService
                        val processedImageResult: Result<File> =
                            removeBgService.processImage(imageBytes, originalFileName, mimeType)
                        val processedImageFile = processedImageResult.getOrNull()

                        // Сохраняем обработанное изображение
                        val fileName = "${UUID.randomUUID()}.png"
                        val finalFile = File("$UPLOADS_DIRECTORY/$fileName")
                        processedImageFile?.copyTo(finalFile)

                        val imageUrl = "http://localhost:8080/images/$fileName"
                        val addingClothe = Clothe(
                            name = name,
                            imageUrl = imageUrl,
                            storeUrl = storeUrl
                        )
                        val clotheId = clotheRepository.addClothe(addingClothe, userId)
                        val clothe = clotheRepository.getClotheById(clotheId = clotheId, idUser = userId)
                        call.respond(HttpStatusCode.Created, clothe)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid request: ${e.message}")
                    }
                }

                delete("/{clotheName}") {
                    val name = call.parameters["clotheName"]
                    if (name == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@delete
                    }
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