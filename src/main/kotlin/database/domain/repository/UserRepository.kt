package com.example.database.domain.repository

import com.example.database.data.model.User

interface UserRepository {
    suspend fun register(user: User): User?
    suspend fun findUserByUsername(userName: String): User?
    suspend fun findUserByEmail(email: String): User?
    suspend fun findUserById(userId: Int): User?
    suspend fun authenticate(username: String, password: String): User?
}