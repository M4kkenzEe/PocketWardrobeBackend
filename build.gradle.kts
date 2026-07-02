plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.example"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)

    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.openapi)

    // Security and Validation Plugins
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.double.receive)

    implementation(libs.koin.ktor)
    implementation(libs.koin.logger)

    implementation(libs.dotenv)
    implementation(libs.jbcrypt)

    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.kotlin.datetime)
    implementation("com.h2database:h2:2.2.224")
    implementation("org.postgresql:postgresql:42.7.7")

    // Database Connection Pooling
    implementation(libs.hikari.cp)

    // Email (SMTP)
    implementation("com.sun.mail:jakarta.mail:2.0.1")

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
