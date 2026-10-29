package com.example.routes

import com.example.auth.model.UserPrincipal
import com.example.auth.EnvironmentConfig
import com.example.database.data.model.Look
import com.example.database.data.model.ShareLinkResponse
import com.example.database.domain.repository.LookRepository
import com.example.database.domain.repository.SharedLookRepository
import com.example.database.domain.repository.UserLookRepository
import com.example.services.AnchorRejection
import com.example.usecases.DraftLooksResult
import com.example.usecases.GenerateDraftLooksUseCase
import com.example.usecases.GenerateLookUseCase
import com.example.usecases.LookServiceUnavailableException
import com.example.usecases.QuotaExceededException
import com.example.usecases.QuotaUseCase
import com.example.usecases.SaveLookDraftUseCase
import plugins.KeepResponseBody
import kotlinx.serialization.Serializable
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.BadRequestException
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

@Serializable
data class GenerateLookRequest(val occasion: String? = null)

@Serializable
data class DraftLooksRequest(val anchorClotheId: Int)

@Serializable
data class DraftLookResponse(val lookId: Int, val url: String)

@Serializable
data class DraftLooksResponse(val looks: List<DraftLookResponse>, val allSaved: Boolean)

@Serializable
data class DraftLooksErrorResponse(val error: String)

@Serializable
data class NotEnoughForAnchorResponse(val error: String, val missing: List<String>)

@Serializable
data class SavedLookResponse(val lookId: Int)

// Ktor wraps a body that fails deserialization in BadRequestException; a missing body is ContentTransformationException.
private suspend fun ApplicationCall.receiveDraftLooksRequest(): DraftLooksRequest? =
    try {
        receive<DraftLooksRequest>()
    } catch (_: BadRequestException) {
        null
    } catch (_: ContentTransformationException) {
        null
    }

private suspend fun ApplicationCall.respondNotFound(error: String) {
    attributes.put(KeepResponseBody, Unit)
    respond(HttpStatusCode.NotFound, DraftLooksErrorResponse(error))
}

private suspend fun ApplicationCall.respondRejection(rejection: AnchorRejection) = when (rejection) {
    AnchorRejection.ClotheNotFound -> respondNotFound("clothe_not_found")
    is AnchorRejection.NotEnoughForAnchor ->
        respond(HttpStatusCode.UnprocessableEntity, NotEnoughForAnchorResponse("not_enough_for_anchor", rejection.missing))
    AnchorRejection.UnsupportedCategory ->
        respond(HttpStatusCode.UnprocessableEntity, DraftLooksErrorResponse("unsupported_category"))
}

fun Application.looks() {
    val lookRepository: LookRepository by inject()
    val sharedLookRepository: SharedLookRepository by inject()
    val userLookRepository: UserLookRepository by inject()
    val generateLookUseCase: GenerateLookUseCase by inject()
    val quotaUseCase: QuotaUseCase by inject()
    val generateDraftLooksUseCase: GenerateDraftLooksUseCase by inject()
    val saveLookDraftUseCase: SaveLookDraftUseCase by inject()
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
                            } catch (_: ContentTransformationException) {
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

                    rateLimit(RateLimitName("generate")) {
                        get("/generate") {
                            val userId = call.principal<UserPrincipal>()?.userId
                                ?: throw IllegalStateException("User not authenticated")
                            try {
                                val lookIds = generateLookUseCase.generateAndSaveLooks(userId)
                                call.respond(HttpStatusCode.OK, mapOf("look_ids" to lookIds))
                            } catch (e: IllegalArgumentException) {
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to "not_enough_clothes")
                                )
                            } catch (e: Exception) {
                                call.respond(
                                    HttpStatusCode.ServiceUnavailable,
                                    mapOf("error" to "AI service unavailable")
                                )
                            }
                        }
                    }

                    rateLimit(RateLimitName("generate")) {
                        post("/generate") {
                            val userId = call.principal<UserPrincipal>()?.userId
                                ?: throw IllegalStateException("User not authenticated")

                            try {
                                val request = try {
                                    call.receive<GenerateLookRequest>()
                                } catch (_: ContentTransformationException) {
                                    GenerateLookRequest()
                                }
                                quotaUseCase.checkAndConsume(userId)
                                val lookIds = generateLookUseCase.generateAndSaveLooks(userId)
                                call.respond(HttpStatusCode.OK, mapOf("look_ids" to lookIds))
                            } catch (e: QuotaExceededException) {
                                call.respond(
                                    HttpStatusCode(402, "Payment Required"),
                                    mapOf("error" to "DAILY_LIMIT_REACHED", "limit" to e.limit, "used" to e.used, "reset_at" to e.resetAt)
                                )
                            } catch (e: IllegalArgumentException) {
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to "not_enough_clothes")
                                )
                            } catch (e: Exception) {
                                call.respond(
                                    HttpStatusCode.ServiceUnavailable,
                                    mapOf("error" to "AI service unavailable")
                                )
                            }
                        }
                    }

                    rateLimit(RateLimitName("drafts")) {
                        post("/drafts") {
                            val userId = call.principal<UserPrincipal>()?.userId
                                ?: throw IllegalStateException("User not authenticated")
                            val request = call.receiveDraftLooksRequest()
                                ?: return@post call.respond(HttpStatusCode.BadRequest, DraftLooksErrorResponse("invalid_request"))

                            try {
                                when (val result = generateDraftLooksUseCase.generate(userId, request.anchorClotheId)) {
                                    is DraftLooksResult.Rejected -> call.respondRejection(result.rejection)
                                    is DraftLooksResult.Generated -> call.respond(
                                        HttpStatusCode.OK,
                                        DraftLooksResponse(
                                            looks = result.looks.map { DraftLookResponse(it.lookId, it.url) },
                                            allSaved = result.allSaved
                                        )
                                    )
                                }
                            } catch (e: LookServiceUnavailableException) {
                                call.application.log.warn("Draft looks generation failed: ${e.message}")
                                call.respond(
                                    HttpStatusCode.ServiceUnavailable,
                                    DraftLooksErrorResponse("AI service unavailable")
                                )
                            }
                        }
                    }

                    rateLimit(RateLimitName("default")) {
                        post("/{lookId}/save") {
                            val userId = call.principal<UserPrincipal>()?.userId
                                ?: throw IllegalStateException("User not authenticated")
                            val lookId = call.parameters["lookId"]?.toIntOrNull()
                                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid lookId")

                            val savedLookId = saveLookDraftUseCase.save(userId, lookId)
                                ?: return@post call.respondNotFound("look_not_found")
                            call.respond(HttpStatusCode.OK, SavedLookResponse(savedLookId))
                        }

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
