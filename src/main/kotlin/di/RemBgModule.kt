package com.example.di

import com.example.services.RemoveBgService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.server.application.*
import org.koin.dsl.module

fun remBgModule(environment: ApplicationEnvironment) = module {
    single { HttpClient(CIO) }

    single<RemoveBgService> {
        val config = environment.config
        val url = "http://localhost:8000/"
//            config.config("ktor").property("url").getString()
        val directory = "uploads"
//            config.config("ktor").property("outputDir").getString()

        RemoveBgService(
            client = get(),
            removeBgServiceUrl = url,
            outputPath = directory
        )
    }
}