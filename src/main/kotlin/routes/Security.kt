package com.example.routes

import com.example.auth.service.TokenService
import com.example.database.domain.repository.UserRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.ktor.ext.inject

fun Application.configureSecurity() {
    val userRepository: UserRepository by inject()
    val tokenService: TokenService by inject()

    authentication {
        jwt {
            realm = "PocketWardrobe API"
            verifier(tokenService.verifier())

            validate { credential ->
                val userId = credential.payload.getClaim("userId")?.asInt()
                val username = credential.payload.getClaim("username")?.asString()

                if (userId != null && username != null) {
                    UserPrincipal(userId, username)
                } else {
                    application.log.warn("Invalid JWT: missing userId or username")
                    null
                }
            }
        }
    }
}
