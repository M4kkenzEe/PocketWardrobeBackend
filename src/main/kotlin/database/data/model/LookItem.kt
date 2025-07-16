package com.example.database.data.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass


@Serializable
data class LookItem(
    val clothe: Clothe,
    val size: Int,
    val x: Float,
    val y: Float,
    val rotation: Float
)

object LookItemTable : IntIdTable("look_items") {
    val lookId = reference("look_id", LookTable, onDelete = ReferenceOption.CASCADE)
    val clotheId = reference("clothe_id", ClotheTable, onDelete = ReferenceOption.RESTRICT)
    val size = integer("size")
    val x = float("x")
    val y = float("y")
    val rotation = float("rotation")
}

class LookItemDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LookItemDao>(LookItemTable)

    var lookId by LookItemTable.lookId
    var clotheId by LookItemTable.clotheId
    var size by LookItemTable.size
    var rotation by LookItemTable.rotation
    var x by LookItemTable.x
    var y by LookItemTable.y
    val clothe by ClotheDao referencedOn LookItemTable.clotheId
}

fun daoToModel(dao: LookItemDao): LookItem = LookItem(
    clothe = daoToModel(dao.clothe),
    size = dao.size,
    x = dao.x,
    y = dao.y,
    rotation = dao.rotation
)