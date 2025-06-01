package com.example.routes

import com.example.auth.AuthController
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.getValue

fun Application.configureRouting(
) {
    val authController: AuthController by inject()
    routing {

        get("/") {
            call.respondText("Hello World!")
        }

        post("/login") {
            authController.authenticate(call)
        }

        authenticate {
            get("/protected") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                call.respond(mapOf("message" to "Hello, $userId!"))
            }
        }
    }
}