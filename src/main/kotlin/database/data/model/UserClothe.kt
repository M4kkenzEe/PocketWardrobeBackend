package com.example.database.data.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

object UserClotheTable : IntIdTable("user_clothes") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val clotheId = reference("clothe_id", ClotheTable, onDelete = ReferenceOption.CASCADE)
    var isDeleted = bool("is_deleted").default(false)
}

class UserClotheDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserClotheDao>(UserClotheTable)

    var userId by UserClotheTable.userId
    var clotheId by UserClotheTable.clotheId
    var isDeleted by UserClotheTable.isDeleted
}
