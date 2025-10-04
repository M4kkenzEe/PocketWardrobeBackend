package com.example.routes

import com.example.database.data.model.ClotheTable
import com.example.database.data.model.LookItemTable
import com.example.database.data.model.LookTable
import com.example.database.data.model.UserTable
import io.ktor.server.application.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabase() {
    Database.connect(
        "jdbc:postgresql://localhost:5432/wardrobe",
        driver = "org.postgresql.Driver",
        user = "postgres",
        password = ""
    )

    transaction {
        SchemaUtils.create(
            ClotheTable,
            UserTable,
            LookTable,
            LookItemTable
        )
    }
}