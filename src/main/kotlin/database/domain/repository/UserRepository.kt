package com.example.database.domain.repository

import com.example.database.data.model.User
import com.example.database.domain.model.UserWithClothesCount

interface UserRepository {
    /**
     * [provisionedByAdmin] skips the registration trial and creates the account already verified,
     * so accounts made through the admin API need no verification email.
     * A [user] passed with emailVerified = true is also created verified, but keeps the registration trial.
     */
    suspend fun register(user: User, provisionedByAdmin: Boolean = false): User?
    suspend fun findAllWithClothesCount(): List<UserWithClothesCount>
    suspend fun findUserByUsername(userName: String): User?
    suspend fun findUserByEmail(email: String): User?
    suspend fun findUserById(userId: Int): User?
    suspend fun authenticate(username: String, password: String): User?
    suspend fun updatePassword(userId: Int, newPasswordHash: String)
    suspend fun incrementTokenVersion(userId: Int)
    suspend fun setEmailVerified(userId: Int)
}
