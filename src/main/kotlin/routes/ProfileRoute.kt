package com.example.routes

import com.example.database.domain.repository.UserRepository
import com.example.profile.toProfileResponse
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject
import io.ktor.server.plugins.ratelimit.RateLimitName

fun Application.profile() {
    val userRepository: UserRepository by inject()
    routing {
        authenticate {
            route("/api/v1") {
                rateLimit(RateLimitName("default")) {
                    route("/profile") {
                        get {
                            val userId = call.principal<UserPrincipal>()?.userId
                                ?: throw IllegalStateException("User not authenticated")
                            val user = userRepository.findUserById(userId)?.toProfileResponse()
                            call.respond(user!!)
                        }
                    }
                }
            }
        }
    }
}