package com.example.routes

import com.example.auth.EnvironmentConfig
import com.example.database.data.model.Look
import com.example.database.data.model.ShareLinkResponse
import com.example.database.domain.repository.LookRepository
import com.example.database.domain.repository.SharedLookRepository
import com.example.database.domain.repository.UserLookRepository
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
import io.ktor.server.plugins.ratelimit.RateLimitName
import validation.FileUploadValidator
import java.io.File
import java.util.*

fun Application.looks() {
    val lookRepository: LookRepository by inject()
    val sharedLookRepository: SharedLookRepository by inject()
    val userLookRepository: UserLookRepository by inject()
    routing {
        staticFiles("/looks", File(EnvironmentConfig.looksDirectory))
        authenticate {
            route("/api/v1") {
                route("/looks") {
                    rateLimit(RateLimitName("default")) {
                        get {
                            val userId = call.principal<UserPrincipal>()?.userId
                                ?: throw IllegalStateException("User not authenticated")
                            val looks = lookRepository.getLookList(userId)
                            call.respond(HttpStatusCode.OK, looks)
                        }
                    }

                    rateLimit(RateLimitName("upload")) {
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
                                call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                            }
                        }
                    }

                    rateLimit(RateLimitName("upload")) {
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
                                        }
                                    }

                                    else -> {}
                                }
                                part.dispose()
                            }

                            if (imageBytes == null || contentType == null) {
                                return@post call.respond(HttpStatusCode.BadRequest, "Missing image file part")
                            }

                            // Validate file bytes
                            FileUploadValidator.validateFileBytes(imageBytes, contentType.toString())

                            // Создаем папку "looks", если не существует
                            val looksDir = File("looks").apply { if (!exists()) mkdirs() }

                            // Генерируем уникальное имя файла
                            val extension = if (contentType == ContentType.Image.JPEG) "jpg" else "png"
                            val fileName = "${UUID.randomUUID()}.$extension"

                            // Сохраняем файл
                            val imageFile = File(looksDir, fileName)
                            imageFile.writeBytes(imageBytes)

                            val imageUrl = "${EnvironmentConfig.getServerBaseUrl()}/looks/$fileName"

                            call.respond(HttpStatusCode.Created, mapOf("imageUrl" to imageUrl))
                        }
                    }

                    rateLimit(RateLimitName("upload")) {
                        // Create share link for a look
                        post("/{lookId}/share") {
                            val ownerUserId = call.principal<UserPrincipal>()?.userId
                                ?: throw IllegalStateException("User not authenticated")

                            val lookId = call.parameters["lookId"]?.toIntOrNull()
                                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid lookId")

                            val shareToken = sharedLookRepository.createShareToken(lookId, ownerUserId)
                            val shareUrl = "${EnvironmentConfig.getAppDeepLinkBase()}/$shareToken"

                            call.respond(
                                HttpStatusCode.Created,
                                ShareLinkResponse(shareToken, shareUrl)
                            )
                        }
                    }

                    rateLimit(RateLimitName("default")) {
                        get("/byId/{lookId}") {
                            val userId = call.principal<UserPrincipal>()?.userId
                                ?: throw IllegalStateException("User not authenticated")
                            val lookId = call.parameters["lookId"]?.toIntOrNull()
                                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid lookId")
                            val result = lookRepository.getLookById(lookId = lookId, userId = userId)
                            call.respond(HttpStatusCode.OK, result)
                        }

                        delete("/{id}") {
                            val userId = call.principal<UserPrincipal>()?.userId
                                ?: throw IllegalStateException("User not authenticated")
                            val id = call.parameters["id"]?.toIntOrNull()
                                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid id")

                            if (userLookRepository.removeLookFromUser(userId, id)) {
                                call.respond(HttpStatusCode.NoContent)
                            } else {
                                call.respond(HttpStatusCode.NotFound, "Look not found in user's wardrobe")
                            }
                        }

                        // Get all share tokens for a look
                        get("/{lookId}/shares") {
                            val ownerUserId = call.principal<UserPrincipal>()?.userId
                                ?: throw IllegalStateException("User not authenticated")

                            val lookId = call.parameters["lookId"]?.toIntOrNull()
                                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid lookId")

                            val shareTokens = sharedLookRepository.getShareTokensForLook(lookId, ownerUserId)
                            val responses = shareTokens.map {
                                ShareLinkResponse(
                                    shareToken = it.shareToken,
                                    shareUrl = "${EnvironmentConfig.getAppDeepLinkBase()}/${it.shareToken}"
                                )
                            }
                            call.respond(HttpStatusCode.OK, responses)
                        }
                    }
                }
            }
        }
    }
    log.info("✓ Looks routes configured at /api/v1/looks")
}