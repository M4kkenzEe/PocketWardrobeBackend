package com.example.database.data.model

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

data class User(
    val userId: Int,
    val username: String,
    val email: String,
    val passwordHash: String,
    val gender: String?,
    val isPro: Boolean = false
)

object UserTable : IntIdTable("users") {
    val username = varchar("username", 50).uniqueIndex()
    val email = varchar("email", 100).uniqueIndex()
    val passwordHash = varchar("password_hash", 256)
    val gender = varchar("gender", 20).nullable()
    val isPro = bool("is_pro").default(false)
}

class UserDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserDao>(UserTable)

    var username by UserTable.username
    var email by UserTable.email
    var passwordHash by UserTable.passwordHash
    var gender by UserTable.gender
    var isPro by UserTable.isPro
}

fun daoToModel(dao: UserDao) = User(
    userId = dao.id.value,
    username = dao.username,
    email = dao.email,
    passwordHash = dao.passwordHash,
    gender = dao.gender,
    isPro = dao.isPro
)