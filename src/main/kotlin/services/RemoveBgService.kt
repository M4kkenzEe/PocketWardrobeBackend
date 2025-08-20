package com.example.services

import io.ktor.client.*
import io.ktor.client.request.forms.*
import io.ktor.client.request.get
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.File

class RemoveBgService(
    private val client: HttpClient,
    private val removeBgServiceUrl: String,
    private val outputPath: String
) {
    suspend fun processImage(fileBytes: ByteArray, fileName: String, mimeType: String): Result<File> {
        return try {
            // Отправляем файл на внешний сервис
            val response: HttpResponse = client.submitFormWithBinaryData(
                url = removeBgServiceUrl + PHOTO_ENDPOINT,
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

    suspend fun getImageFromUrl(img: String): Result<File> {
        return try {
            val response = client.get(removeBgServiceUrl + URL_ENDPOINT) {
                url {
                    parameters.append("url", img)
                }
            }

            if (response.status != HttpStatusCode.OK) {
                return Result.failure(Exception("Error from service: ${response.status}"))
            }

            val responseBytes = response.readRawBytes()
            val outputFileName = "output_${System.currentTimeMillis()}.png"
            val outputFile = File(outputPath, outputFileName)
            outputFile.writeBytes(responseBytes)

            Result.success(outputFile)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val PHOTO_ENDPOINT = "remove-background"
        private const val URL_ENDPOINT = "get_wb_image/"
    }
}