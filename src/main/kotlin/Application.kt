package com.example

import com.example.auth.ConfigValidator
import com.example.di.remBgModule
import com.example.di.authModule
import com.example.di.databaseModule
import com.example.routes.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.SLF4JLogger
import plugins.*

fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    // 1. Validate configuration before starting anything else
    ConfigValidator.validateConfiguration(log)

    // 2. Install security and monitoring plugins
    configureErrorHandling()      // Centralized error handling (prevents error leaks)
    configureCors()                // CORS configuration
    configureRateLimiting()        // Rate limiting (brute force protection)
    configureLogging()             // Request logging
    setupRequestTiming()           // Request timing for logging

    // 3. Install Koin dependency injection
    install(Koin) {
        modules(authModule, databaseModule, remBgModule(environment))
        logger(SLF4JLogger())
    }

    // 4. Configure application features
    configureSerialization()
    configureSecurity()
    configureDatabase()

    // 5. Register routes
    configureHealth()              // Health check endpoint
    configureRouting()
    clothes()
    looks()
    sharedLooks()
    profile()
    configureSwagger()

    log.info("=" + "=".repeat(69))
    log.info("✓ PocketWardrobe Backend started successfully!")
    log.info("=" + "=".repeat(69))
}