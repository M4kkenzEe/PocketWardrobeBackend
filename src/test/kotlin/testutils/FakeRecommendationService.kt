package com.example.testutils

import com.example.services.GenerateLookService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** RecommendationService over a MockEngine, so the real [GenerateLookService] parsing is under test. */
class FakeRecommendationService(private val looks: InMemoryLooks) {
    private val requestedPaths = CopyOnWriteArrayList<String>()

    val materializeDraftFlags = CopyOnWriteArrayList<Boolean>()

    var anchorClotheSets: List<List<Int>> = emptyList()
    var allSaved = false
    var generateFromItemFailure: Pair<HttpStatusCode, String>? = null
    var isUnreachable = false
    var rendersFail = false

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath
        requestedPaths += path
        if (isUnreachable) throw IOException("RecommendationService is down")
        when {
            path == "/generate-from-item" -> respondGenerateFromItem()
            path == "/materialize-look" -> respondMaterialize(request.body)
            path.endsWith("/render") -> respondRender()
            else -> respond("unexpected $path", HttpStatusCode.NotFound)
        }
    }

    val service = GenerateLookService(HttpClient(engine), "http://recommendation")

    fun callCount(path: String): Int = requestedPaths.count { it == path }

    fun renderCallCount(): Int = requestedPaths.count { it.endsWith("/render") }

    val totalCallCount: Int get() = requestedPaths.size

    private fun MockRequestHandleScope.respondGenerateFromItem() =
        generateFromItemFailure
            ?.let { (status, body) -> respond(body, status, jsonHeaders) }
            ?: respond(
                """{"looks": [${anchorClotheSets.joinToString { """{"items": $it, "score": 0.9}""" }}], "all_saved": $allSaved}""",
                HttpStatusCode.OK,
                jsonHeaders
            )

    private fun MockRequestHandleScope.respondMaterialize(body: OutgoingContent): io.ktor.client.request.HttpResponseData {
        val payload = Json.parseToJsonElement((body as TextContent).text).jsonObject
        materializeDraftFlags += payload.getValue("draft").jsonPrimitive.boolean
        val itemIds = payload.getValue("item_ids").jsonArray.map { it.jsonPrimitive.int }
        val lookId = looks.createLook(itemIds.toSet())
        return respond("""{"look_id": $lookId}""", HttpStatusCode.OK, jsonHeaders)
    }

    private fun MockRequestHandleScope.respondRender() =
        if (rendersFail) respond("render failed", HttpStatusCode.InternalServerError)
        else respond(ByteArray(8) { it.toByte() }, HttpStatusCode.OK)

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
