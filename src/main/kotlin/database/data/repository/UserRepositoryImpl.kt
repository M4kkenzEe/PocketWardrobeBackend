package com.example.database.data.repository

import com.example.database.data.model.User
import com.example.database.data.model.UserDao
import com.example.database.data.model.UserClotheTable
import com.example.database.data.model.UserTable
import com.example.database.data.model.daoToModel
import com.example.database.domain.model.UserWithClothesCount
import com.example.database.domain.repository.UserRepository
import com.example.database.suspendTransaction
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.mindrot.jbcrypt.BCrypt

class UserRepositoryImpl : UserRepository {

    override suspend fun register(user: User, provisionedByAdmin: Boolean): User? = suspendTransaction {
        val existingUser = UserDao.find {
            (UserTable.username eq user.username) or (UserTable.email eq user.email)
        }.firstOrNull()

        if (existingUser != null) {
            return@suspendTransaction null
        }

        val now = Clock.System.now()
        UserDao.new {
            username = user.username
            email = user.email
            passwordHash = BCrypt.hashpw(user.passwordHash, BCrypt.gensalt())
            gender = user.gender
            isPro = !provisionedByAdmin
            proUntil = if (provisionedByAdmin) null else now.plus(30.days)
            emailVerified = provisionedByAdmin || user.emailVerified
            createdAt = now
        }.let { daoToModel(it) }
    }

    override suspend fun findAllWithClothesCount(): List<UserWithClothesCount> = suspendTransaction {
        val clothesCount = UserClotheTable.id.count()
        UserTable
            .join(
                UserClotheTable,
                JoinType.LEFT,
                onColumn = UserTable.id,
                otherColumn = UserClotheTable.userId,
                additionalConstraint = { UserClotheTable.isDeleted eq false }
            )
            .select(UserTable.columns + clothesCount)
            .groupBy(UserTable.id)
            .orderBy(UserTable.id)
            .map { row ->
                UserWithClothesCount(
                    userId = row[UserTable.id].value,
                    // Prod schema has legacy NULL emails, but UserTable declares email non-null.
                    email = row.getOrNull(UserTable.email),
                    createdAt = row[UserTable.createdAt],
                    isPro = row[UserTable.isPro],
                    proUntil = row[UserTable.proUntil],
                    clothesCount = row[clothesCount].toInt()
                )
            }
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

    override suspend fun findUserByEmailIgnoreCase(email: String): User? = suspendTransaction {
        UserDao.find { UserTable.email.lowerCase() eq email.lowercase() }
            .orderBy(UserTable.id to SortOrder.ASC)
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

    override suspend fun updatePassword(userId: Int, newPasswordHash: String): Unit = suspendTransaction {
        UserDao.findById(userId)?.passwordHash = newPasswordHash
    }

    override suspend fun incrementTokenVersion(userId: Int): Unit = suspendTransaction {
        UserDao.findById(userId)?.let { it.tokenVersion = it.tokenVersion + 1 }
    }

    override suspend fun setEmailVerified(userId: Int): Unit = suspendTransaction {
        UserDao.findById(userId)?.emailVerified = true
    }

    private fun verifyPassword(password: String, passwordHash: String): Boolean {
        return BCrypt.checkpw(password, passwordHash)
    }
}
