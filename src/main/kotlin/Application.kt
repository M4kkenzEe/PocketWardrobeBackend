package com.example

import com.example.di.authModule
import com.example.di.databaseModule
import com.example.routes.clothes
import com.example.routes.configureDatabase
import com.example.routes.configureRouting
import com.example.routes.configureSecurity
import com.example.routes.configureSerialization
import com.example.routes.looks
import com.example.routes.profile
import com.example.services.removeBackground
import io.ktor.server.application.*
import org.koin.core.context.startKoin
import org.koin.logger.SLF4JLogger

fun main(args: Array<String>) {
    initKoin()
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureSecurity()
    configureRouting()
    removeBackground()
    configureDatabase()
    clothes()
    profile()
    looks()
}


fun initKoin() {
    startKoin {
        logger(SLF4JLogger())
        modules(authModule, databaseModule)
    }
}