package com.example.auth.service

import org.mindrot.jbcrypt.BCrypt

//class UserService {
//    // В реальном приложении здесь будет работа с БД
//    fun findByEmail(email: String): User? {
//        return if (email == "user@example.com") {
//            User(
//                id = 1,
//                email = "user@example.com",
//                passwordHash = BCrypt.hashpw("password", BCrypt.gensalt())
//            )
//        } else null
//    }
//
//    fun verifyPassword(password: String, passwordHash: String): Boolean {
//        return BCrypt.checkpw(password, passwordHash)
//    }
//}
//
//private data class User(
//    val id: Int,
//    val email: String,
//    val passwordHash: String
//)