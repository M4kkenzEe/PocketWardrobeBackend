package com.example.services

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.File

class RemoveBgService(
    private val client: HttpClient = HttpClient(CIO),
    private val removeBgServiceUrl: String = "http://0.0.0.0:8000/remove-background",
    private val outputPath: String = "/Users/yuriichernigovtsev/Desktop/output_test2"
) {
    suspend fun processImage(fileBytes: ByteArray, fileName: String, mimeType: String): Result<File> {
        return try {
            // Отправляем файл на внешний сервис
            val response: HttpResponse = client.submitFormWithBinaryData(
                url = removeBgServiceUrl,
                formData = formData {
                    append("file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    })
                }
            )

            if (response.status != HttpStatusCode.OK) {
                return Result.failure(Exception("Error from service: ${response.status}"))
            }

            // Сохраняем результат
            val responseBytes = response.readRawBytes()
            val outputFileName = "output_${System.currentTimeMillis()}.png"
            val outputFile = File(outputPath, outputFileName)
            outputFile.writeBytes(responseBytes)

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}