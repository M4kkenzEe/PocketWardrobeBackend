package com.example.routes

import com.example.database.data.model.Look
import com.example.database.data.model.ShareLinkResponse
import com.example.database.domain.repository.LookRepository
import com.example.database.domain.repository.SharedLookRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import java.io.File
import java.util.*

fun Application.looks() {
    val lookRepository: LookRepository by inject()
    val sharedLookRepository: SharedLookRepository by inject()
    routing {
        staticFiles("/looks", File("looks"))
        authenticate {
            route("/looks") {
                get {
                    val userId = call.principal<UserPrincipal>()?.userId
                        ?: throw IllegalStateException("User not authenticated")
                    try {
                        val looks = lookRepository.getAllLooks(userId)
                        call.respond(HttpStatusCode.OK, looks)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Error retrieving looks: ${e.message}")
                    }
                }

                post {
                    val userId = call.principal<UserPrincipal>()?.userId
                        ?: throw IllegalStateException("User not authenticated")

                    try {
                        val lookJson = call.receiveText()
                        val look = Json.decodeFromString<Look>(lookJson)

                        if (look.lookItems.any { it.clothe.id == null }) {
                            return@post call.respond(HttpStatusCode.BadRequest, "All clothes must have a valid ID")
                        }

                        // Сохраняем в БД
                        val lookId = lookRepository.addLook(look, userId, "")

                        call.respond(HttpStatusCode.Created, mapOf("id" to lookId))
                    } catch (e: ContentTransformationException) {
                        call.respond(HttpStatusCode.BadRequest, "${e.localizedMessage}")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Error creating look: ${e.message}")
                    }
                }

                post("/uploadImage") {
                    val userId = call.principal<UserPrincipal>()?.userId
                        ?: throw IllegalStateException("User not authenticated")

                    val multipart = call.receiveMultipart()
                    var imageBytes: ByteArray? = null
                    var contentType: ContentType? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                if (part.name == "image") {
                                    contentType = part.contentType
                                    if (contentType != ContentType.Image.JPEG && contentType != ContentType.Image.PNG) {
                                        throw IllegalArgumentException("Invalid image format. Only JPEG/PNG allowed.")
                                    }
                                    imageBytes = part.provider().toInputStream().readAllBytes()
                                    if (imageBytes!!.size > 5 * 1024 * 1024) { // Лимит 5MB
                                        throw IllegalArgumentException("Image too large. Max size: 5MB.")
                                    }
                                }
                            }

                            else -> {}
                        }
                        part.dispose()
                    }

                    if (imageBytes == null || contentType == null) {
                        return@post call.respond(HttpStatusCode.BadRequest, "Missing image file part")
                    }

                    // Создаем папку "looks", если не существует
                    val looksDir = File("looks").apply { if (!exists()) mkdirs() }

                    // Генерируем уникальное имя файла
                    val extension = if (contentType == ContentType.Image.JPEG) "jpg" else "png"
                    val fileName = "${UUID.randomUUID()}.$extension"

                    // Сохраняем файл
                    val imageFile = File(looksDir, fileName)
                    imageFile.writeBytes(imageBytes)

                    val imageUrl = "http://localhost:8080/looks/$fileName"

                    call.respond(HttpStatusCode.Created, mapOf("imageUrl" to imageUrl))
                }

                // Create share link for a look
                post("/{lookId}/share") {
                    val ownerUserId = call.principal<UserPrincipal>()?.userId
                        ?: throw IllegalStateException("User not authenticated")

                    val lookId = call.parameters["lookId"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid lookId")

                    try {
                        val shareToken = sharedLookRepository.createShareToken(lookId, ownerUserId)
                        val shareUrl = "pocketwardrobe://share/$shareToken"

                        call.respond(
                            HttpStatusCode.Created,
                            ShareLinkResponse(shareToken, shareUrl)
                        )
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.NotFound, e.message ?: "Look not found")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Error creating share link: ${e.message}")
                    }
                }

                // Get all share tokens for a look
                get("/{lookId}/shares") {
                    val ownerUserId = call.principal<UserPrincipal>()?.userId
                        ?: throw IllegalStateException("User not authenticated")

                    val lookId = call.parameters["lookId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid lookId")

                    try {
                        val shareTokens = sharedLookRepository.getShareTokensForLook(lookId, ownerUserId)
                        val responses = shareTokens.map {
                            ShareLinkResponse(
                                shareToken = it.shareToken,
                                shareUrl = "pocketwardrobe://share/${it.shareToken}"
                            )
                        }
                        call.respond(HttpStatusCode.OK, responses)
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.NotFound, e.message ?: "Look not found")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Error retrieving share links: ${e.message}")
                    }
                }
            }
        }
    }
}