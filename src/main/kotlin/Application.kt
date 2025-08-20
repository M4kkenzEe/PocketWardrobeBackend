package com.example

import com.example.di.remBgModule
import com.example.di.authModule
import com.example.di.databaseModule
import com.example.routes.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.SLF4JLogger

fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(Koin) {
        modules(authModule, databaseModule, remBgModule(environment))
        logger(SLF4JLogger())
    }
    configureSerialization()
    configureSecurity()
    configureRouting()
    clothes()
    looks()
    configureDatabase()
    profile()
}