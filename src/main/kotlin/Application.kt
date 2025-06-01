package com.example

import com.example.auth.di.authModule
import com.example.routes.configureRouting
import com.example.routes.configureSecurity
import com.example.routes.configureSerialization
import io.ktor.server.application.*
import org.koin.core.context.startKoin
import org.koin.logger.SLF4JLogger

fun main(args: Array<String>) {
    initKoin()
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
//    install(Koin) {
//        slf4jLogger()
//        modules(authModule)
//    }

    configureSerialization()
    configureSecurity()
    configureRouting()
}


fun initKoin() {
    startKoin {
        logger(SLF4JLogger()) // Включаем логгер
        modules(authModule)
    }
}