package com.example.routes

import com.example.database.data.model.ImportRequest
import com.example.database.data.model.ImportType
import com.example.database.data.model.ShareLinkResponse
import com.example.database.domain.repository.SharedLookRepository
import com.example.usecases.ImportSharedLookUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.sharedLooks() {
    val sharedLookRepository: SharedLookRepository by inject()
    val importUseCase: ImportSharedLookUseCase by inject()

    routing {
        // PUBLIC endpoint - anyone can view shared look
        get("/shared/{shareToken}") {
            val shareToken = call.parameters["shareToken"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing shareToken")

            try {
                val sharedLook = sharedLookRepository.getLookByShareToken(shareToken)

                if (sharedLook == null) {
                    call.respond(HttpStatusCode.NotFound, "Shared link not found or expired")
                } else {
                    call.respond(HttpStatusCode.OK, sharedLook)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error retrieving shared look: ${e.message}")
            }
        }

        // PROTECTED endpoints - require authentication
        authenticate {
            // Revoke a share token
            delete("/shares/{shareToken}") {
                val ownerUserId = call.principal<UserPrincipal>()?.userId
                    ?: throw IllegalStateException("User not authenticated")

                val shareToken = call.parameters["shareToken"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing shareToken")

                try {
                    val revoked = sharedLookRepository.revokeShareToken(shareToken, ownerUserId)

                    if (revoked) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Share token not found or you are not the owner")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error revoking share link: ${e.message}")
                }
            }

            // Import shared look
            post("/shares/{shareToken}/import") {
                val receiverUserId = call.principal<UserPrincipal>()?.userId
                    ?: throw IllegalStateException("User not authenticated")

                val shareToken = call.parameters["shareToken"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing shareToken")

                try {
                    val request = call.receive<ImportRequest>()

                    when (request.importType) {
                        ImportType.FULL_LOOK -> {
                            importUseCase.importFullLook(shareToken, receiverUserId)
                                .fold(
                                    onSuccess = { lookId ->
                                        call.respond(HttpStatusCode.Created, mapOf("lookId" to lookId))
                                    },
                                    onFailure = { e ->
                                        call.respond(
                                            HttpStatusCode.InternalServerError,
                                            "Error importing look: ${e.message}"
                                        )
                                    }
                                )
                        }

                        ImportType.SELECTED_ITEMS -> {
                            if (request.clotheIds.isNullOrEmpty()) {
                                return@post call.respond(
                                    HttpStatusCode.BadRequest,
                                    "clotheIds required for SELECTED_ITEMS import"
                                )
                            }

                            importUseCase.importSelectedClothes(shareToken, request.clotheIds, receiverUserId)
                                .fold(
                                    onSuccess = { clotheIds ->
                                        call.respond(HttpStatusCode.Created, mapOf("clotheIds" to clotheIds))
                                    },
                                    onFailure = { e ->
                                        call.respond(
                                            HttpStatusCode.InternalServerError,
                                            "Error importing clothes: ${e.message}"
                                        )
                                    }
                                )
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid request: ${e.message}")
                }
            }
        }
    }
}
