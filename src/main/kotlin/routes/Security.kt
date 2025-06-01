package com.example.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.auth.EnvironmentConfig.jwtAudience
import com.example.auth.EnvironmentConfig.jwtIssuer
import com.example.auth.EnvironmentConfig.jwtSecret
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureSecurity() {
    authentication {
        jwt {
            realm = "Secure API"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )

            validate { credential ->
                val issuer = credential.payload.issuer
                val audience = credential.payload.audience

                if (issuer == jwtIssuer && audience.contains(jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    application.log.error("Invalid JWT: issuer=$issuer, audience=$audience")
                    null
                }
            }
        }
    }
}
