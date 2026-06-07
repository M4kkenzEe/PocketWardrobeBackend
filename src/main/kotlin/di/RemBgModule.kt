package com.example.di

import com.example.auth.EnvironmentConfig
import com.example.services.AffiliateService
import com.example.services.ClotheAnalysisService
import com.example.services.GenerateLookService
import com.example.services.RemoveBgService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

fun remBgModule(environment: ApplicationEnvironment) = module {
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    single<RemoveBgService> {
        RemoveBgService(
            client = get(),
            removeBgServiceUrl = EnvironmentConfig.removeBgServiceUrl,
            outputPath = EnvironmentConfig.uploadsDirectory
        )
    }

    single<ClotheAnalysisService> {
        ClotheAnalysisService(
            client = get(),
            analysisServiceUrl = EnvironmentConfig.analysisServiceUrl
        )
    }

    single<GenerateLookService> {
        GenerateLookService(
            client = get(),
            generateLookServiceUrl = EnvironmentConfig.lookGenerationServiceUrl
        )
    }

    single<AffiliateService> {
        AffiliateService(
            admitadWbId = EnvironmentConfig.admitadWbId,
            admitadOzonId = EnvironmentConfig.admitadOzonId,
            admitadLamodaId = EnvironmentConfig.admitadLamodaId
        )
    }
}