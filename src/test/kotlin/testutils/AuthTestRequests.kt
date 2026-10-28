package com.example.testutils

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlin.test.assertEquals
import kotlin.test.assertTrue

suspend fun ApplicationTestBuilder.postJson(path: String, body: String): HttpResponse =
    client.post("/api/v1$path") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

suspend fun ApplicationTestBuilder.postRefresh(refreshToken: String): HttpResponse =
    postJson("/refresh", """{"refreshToken":"$refreshToken"}""")

suspend fun ApplicationTestBuilder.postLogout(refreshToken: String): HttpResponse =
    postJson("/logout", """{"refreshToken":"$refreshToken"}""")

suspend fun HttpResponse.bodyJson(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

/**
 * The session contract the released mobile app parses after /login, /register and /refresh.
 * Returns the body so callers can check the tokens themselves.
 */
suspend fun HttpResponse.assertSessionResponse(expectedStatus: HttpStatusCode, expectedUserId: Int): JsonObject {
    assertEquals(expectedStatus, status)
    val body = bodyJson()
    assertTrue(body.getValue("accessToken").jsonPrimitive.content.isNotBlank(), "accessToken")
    assertTrue(body.getValue("refreshToken").jsonPrimitive.content.isNotBlank(), "refreshToken")
    assertTrue(body.getValue("expiresAt").jsonPrimitive.long > System.currentTimeMillis(), "expiresAt is an epoch millis in the future")
    assertEquals(expectedUserId, body.getValue("userId").jsonPrimitive.int)
    return body
}
