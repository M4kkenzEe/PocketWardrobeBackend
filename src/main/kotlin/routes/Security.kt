package com.example.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.auth.EnvironmentConfig.jwtAudience
import com.example.auth.EnvironmentConfig.jwtIssuer
import com.example.auth.EnvironmentConfig.jwtSecret
import com.example.auth.service.JWTConfig
import com.example.database.domain.repository.UserRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.ktor.ext.inject

fun Application.configureSecurity() {
    val userRepository: UserRepository by inject()
    authentication {
        jwt {
            realm = "Secure API"
//            verifier(
//                JWT
//                    .require(Algorithm.HMAC256(jwtSecret))
//                    .withAudience(jwtAudience)
//                    .withIssuer(jwtIssuer)
//                    .build()
//            )

            verifier(JWTConfig.verifier())

            validate { credential ->
                credential.payload.getClaim("userId")?.asInt()?.let { userId ->
                    credential.payload.getClaim("username")?.asString()?.let { username ->
                        UserPrincipal(userId, username)
                    }
                }
            }

//            validate { credential ->
//                val issuer = credential.payload.issuer
//                val audience = credential.payload.audience
//
//                if (issuer == jwtIssuer && audience.contains(jwtAudience)) {
//                    JWTPrincipal(credential.payload)
//                } else {
//                    application.log.error("Invalid JWT: issuer=$issuer, audience=$audience")
//                    null
//                }
//            }
        }
    }
}
