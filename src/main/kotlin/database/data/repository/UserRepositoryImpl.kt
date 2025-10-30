package com.example.database.data.repository

import com.example.database.data.model.User
import com.example.database.data.model.UserDao
import com.example.database.data.model.UserTable
import com.example.database.data.model.daoToModel
import com.example.database.domain.repository.UserRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.or
import org.mindrot.jbcrypt.BCrypt

class UserRepositoryImpl : UserRepository {

    override suspend fun register(user: User): User? = suspendTransaction {
        val existingUser = UserDao.find {
            (UserTable.username eq user.username) or (UserTable.email eq user.email)
        }.firstOrNull()

        if (existingUser != null) {
            return@suspendTransaction null
        }

        UserDao.new {
            username = user.username
            email = user.email
            passwordHash = BCrypt.hashpw(user.passwordHash, BCrypt.gensalt())
            gender = user.gender
        }.let { daoToModel(it) }
    }

    override suspend fun findUserByUsername(userName: String): User? = suspendTransaction {
        UserDao.find { UserTable.username eq userName }
            .firstOrNull()
            ?.let { daoToModel(it) }
    }

    override suspend fun findUserByEmail(email: String): User? = suspendTransaction {
        UserDao.find { UserTable.email eq email }
            .firstOrNull()
            ?.let { daoToModel(it) }
    }

    override suspend fun findUserById(userId: Int): User? = suspendTransaction {
        UserDao.find { UserTable.id eq userId }
            .firstOrNull()
            ?.let { daoToModel(it) }
    }

    override suspend fun authenticate(username: String, password: String): User? = suspendTransaction {
        val user = if (username.contains("@")) {
            // Это email
            UserDao.find { UserTable.email eq username }.firstOrNull()
        } else {
            // Это username
            UserDao.find { UserTable.username eq username }.firstOrNull()
        }
        user?.takeIf { verifyPassword(password, it.passwordHash) }?.let { daoToModel(it) }
    }

    private fun verifyPassword(password: String, passwordHash: String): Boolean {
        return BCrypt.checkpw(password, passwordHash)
    }


}