package com.example.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.statement.readRawBytes
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.File


const val removeBgServiceUrl = "http://0.0.0.0:8000/remove-background"
const val output_path = "/Users/yuriichernigovtsev/Desktop/output_test2"


fun getMimeType(file: File): String = when (file.extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "bmp" -> "image/bmp"
    "webp" -> "image/webp"
    else -> "application/octet-stream" // fallback
}

val client = HttpClient(CIO)

fun Application.removeBackground() {
    routing {
        authenticate {
            post("/remove-background") {
                val multipart = call.receiveMultipart()
                var fileBytes: ByteArray? = null
                var fileName: String? = null
                var mimeType: String? = null

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        fileName = part.originalFileName ?: "upload_file"
                        mimeType = part.contentType?.toString() ?: "application/octet-stream"
                        fileBytes = part.provider().toInputStream().readBytes()
                    }
                    part.dispose()
                }

                if (fileBytes == null) {
                    call.respond(HttpStatusCode.BadRequest, "No file uploaded")
                    return@post
                }

                // Отправляем файл дальше на внешний сервис
                val response: HttpResponse = client.submitFormWithBinaryData(
                    url = removeBgServiceUrl,
                    formData = formData {
                        append("file", fileBytes, Headers.build {
                            append(HttpHeaders.ContentType, mimeType!!)
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        })
                    }
                )

                if (response.status != HttpStatusCode.OK) {
                    call.respondText("Error from service: ${response.status}", status = response.status)
                    return@post
                }

                val responseBytes = response.readRawBytes()
                val outputFileName = "output_${System.currentTimeMillis()}.png"
                val outputFile = File(output_path, outputFileName)
                outputFile.writeBytes(responseBytes)

                call.respondText("File saved as ${outputFile.absolutePath}", status = HttpStatusCode.OK)
            }
        }
    }
}