package com.example.routes

import com.example.database.data.model.Look
import com.example.database.domain.repository.LookRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.looks() {
    val repository: LookRepository by inject()

    routing {
        authenticate {

            route("/looks") {
                get {
                    val userId = call.principal<UserIdPrincipal>()?.name?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, "Invalid user ID")

                    try {
                        val looks = repository.getLooks(userId)
                        call.respond(HttpStatusCode.OK, looks)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to retrieve looks: ${e.message}")
                    }
                }

                post {
                    val userId = call.principal<UserIdPrincipal>()?.name?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid user ID")

                    try {
                        val look = call.receive<Look>()
                        val success = repository.addLook(look, userId)
                        if (success) {
                            call.respond(HttpStatusCode.Created, "Look created successfully")
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Failed to create look")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid look data: ${e.message}")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Error creating look: ${e.message}")
                    }
                }
            }
        }
    }
}