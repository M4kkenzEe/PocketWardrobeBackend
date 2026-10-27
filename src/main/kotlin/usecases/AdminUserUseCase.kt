package com.example.usecases

import com.example.database.data.model.User
import com.example.database.domain.model.UserWithClothesCount
import com.example.database.domain.repository.UserRepository
import validation.AuthValidator

class AdminUserUseCase(private val userRepository: UserRepository) {

    suspend fun listUsers(): List<UserWithClothesCount> = userRepository.findAllWithClothesCount()

    suspend fun createUser(username: String, email: String, password: String): UserWithClothesCount {
        AuthValidator.validateRegisterRequest(email = email, password = password, username = null, gender = null)

        val createdUser = userRepository.register(
            User(userId = 0, username = username, email = email, passwordHash = password, gender = null),
            provisionedByAdmin = true
        ) ?: throw IllegalStateException("User with this username or email already exists")

        return UserWithClothesCount(user = createdUser, clothesCount = 0)
    }
}
