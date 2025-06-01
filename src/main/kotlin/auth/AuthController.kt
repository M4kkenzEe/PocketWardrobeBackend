package com.example.auth

import com.example.auth.model.AuthRequest
import com.example.auth.service.JwtService
import com.example.auth.service.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.koin.java.KoinJavaComponent.inject

class AuthController {
    private val jwtService: JwtService by inject(JwtService::class.java)
    private val userService: UserService by inject(UserService::class.java)

    suspend fun authenticate(call: ApplicationCall) {
        val request = call.receive<AuthRequest>()

        val user = userService.findByEmail(request.email)
        if (user == null || !userService.verifyPassword(request.password, user.passwordHash)) {
            call.respond(UnauthorizedResponse())
            return
        }

        val tokenResponse = jwtService.generateToken(user.id.toString())
        call.respond(tokenResponse)
    }
}