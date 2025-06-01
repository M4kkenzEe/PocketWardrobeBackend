package com.example

import com.example.auth.AuthRequest
import com.example.auth.generateToken
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Application.configureRouting(
) {
    routing {

        get("/") {
            call.respondText("Hello World!")
        }

        post("/login") {
            val authRequest = call.receive<AuthRequest>()

            // Здесь должна быть ваша логика проверки пользователя
            // Например, проверка в базе данных
            if (authRequest.username == "test" && authRequest.password == "password") {
                val token = generateToken(authRequest.username)
                call.respond(mapOf("token" to token))
            } else {
                call.respond(mapOf("error" to "Invalid credentials"))
            }
        }

        authenticate {
            get("/protected") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.payload?.getClaim("username")?.asString()

                call.respond(
                    mapOf(
                        "message" to "Hello, $username!",
                    )
                )
            }
        }
    }
}

@Serializable
data class SimplifiedClaim(val type: String, val value: String)