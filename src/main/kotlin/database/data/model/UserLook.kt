package com.example.database.data.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

object UserLookTable : IntIdTable("user_looks") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val lookId = reference("look_id", LookTable, onDelete = ReferenceOption.CASCADE)
    val isDeleted = bool("is_deleted").default(false)
}

class UserLookDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserLookDao>(UserLookTable)

    var userId by UserLookTable.userId
    var lookId by UserLookTable.lookId
    var isDeleted by UserLookTable.isDeleted
}
