package com.example.testutils

import com.example.database.data.model.User
import com.example.database.domain.model.UserWithClothesCount
import com.example.database.domain.model.withClothesCount
import com.example.database.domain.repository.UserRepository
import kotlinx.datetime.Clock
import org.mindrot.jbcrypt.BCrypt
import kotlin.time.Duration.Companion.days

/**
 * Stateful stand-in for UserRepositoryImpl: real bcrypt hashing and the same register() rules
 * (trial for normal signups, no trial + verified for admin-provisioned), so tests can register then log in.
 */
class InMemoryUserRepository : UserRepository {
    private val users = mutableListOf<User>()
    private val clothesCountByUserId = mutableMapOf<Int, Int>()
    private val legacyUsersWithoutEmail = mutableListOf<UserWithClothesCount>()

    fun seed(user: User, clothesCount: Int = 0) {
        users += user
        clothesCountByUserId[user.userId] = clothesCount
    }

    fun seedUserWithoutEmail(userWithClothesCount: UserWithClothesCount) {
        legacyUsersWithoutEmail += userWithClothesCount
    }

    override suspend fun register(user: User, provisionedByAdmin: Boolean): User? {
        if (users.any { it.username == user.username || it.email == user.email }) return null
        val now = Clock.System.now()
        val created = user.copy(
            userId = users.size + 1,
            passwordHash = BCrypt.hashpw(user.passwordHash, BCrypt.gensalt(4)),
            isPro = !provisionedByAdmin,
            proUntil = if (provisionedByAdmin) null else now.plus(30.days),
            emailVerified = provisionedByAdmin,
            createdAt = now
        )
        users += created
        return created
    }

    override suspend fun findAllWithClothesCount(): List<UserWithClothesCount> =
        users.map { it.withClothesCount(clothesCountByUserId[it.userId] ?: 0) } + legacyUsersWithoutEmail

    override suspend fun findUserByUsername(userName: String): User? = users.firstOrNull { it.username == userName }
    override suspend fun findUserByEmail(email: String): User? = users.firstOrNull { it.email == email }
    override suspend fun findUserById(userId: Int): User? = users.firstOrNull { it.userId == userId }

    override suspend fun authenticate(username: String, password: String): User? =
        users.firstOrNull { it.username == username || it.email == username }
            ?.takeIf { BCrypt.checkpw(password, it.passwordHash) }

    override suspend fun updatePassword(userId: Int, newPasswordHash: String) = Unit
    override suspend fun incrementTokenVersion(userId: Int) = Unit
    override suspend fun setEmailVerified(userId: Int) = Unit
}
