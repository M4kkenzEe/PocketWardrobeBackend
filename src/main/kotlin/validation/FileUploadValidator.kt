package validation

import com.example.auth.EnvironmentConfig
import io.ktor.http.content.*

/**
 * Centralized file upload validation
 * Validates file size, content type, and filename safety
 */
object FileUploadValidator {

    private val maxFileSizeBytes = EnvironmentConfig.maxFileSizeMB * 1024 * 1024 // Convert MB to bytes

    private val allowedContentTypes = EnvironmentConfig.allowedImageTypes
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

    /**
     * Validates an uploaded file part
     * Throws IllegalArgumentException if validation fails
     */
    fun validateFile(filePart: PartData.FileItem) {
        // 1. Validate content type
        val contentType = filePart.contentType?.toString()
        if (contentType == null || contentType !in allowedContentTypes) {
            throw IllegalArgumentException(
                "Invalid file type. Allowed types: ${allowedContentTypes.joinToString(", ")}"
            )
        }

        // 2. Read file bytes and validate size
        val fileBytes = filePart.streamProvider().readBytes()
        if (fileBytes.size > maxFileSizeBytes) {
            throw IllegalArgumentException(
                "File size exceeds maximum allowed size of ${EnvironmentConfig.maxFileSizeMB}MB"
            )
        }

        // 3. Validate filename
        val originalFileName = filePart.originalFileName
        if (originalFileName != null) {
            validateFileName(originalFileName)
        }
    }

    /**
     * Validates file bytes directly
     * Throws IllegalArgumentException if validation fails
     */
    fun validateFileBytes(bytes: ByteArray, contentType: String?) {
        // 1. Validate content type
        if (contentType == null || contentType !in allowedContentTypes) {
            throw IllegalArgumentException(
                "Invalid file type. Allowed types: ${allowedContentTypes.joinToString(", ")}"
            )
        }

        // 2. Validate size
        if (bytes.size > maxFileSizeBytes) {
            throw IllegalArgumentException(
                "File size exceeds maximum allowed size of ${EnvironmentConfig.maxFileSizeMB}MB"
            )
        }
    }

    /**
     * Validates filename for security issues
     * Prevents path traversal attacks and invalid characters
     */
    private fun validateFileName(fileName: String) {
        // Check for path traversal attempts
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw IllegalArgumentException("Invalid filename: path traversal detected")
        }

        // Check for null bytes
        if (fileName.contains("\u0000")) {
            throw IllegalArgumentException("Invalid filename: null byte detected")
        }

        // Check filename length
        if (fileName.length > 255) {
            throw IllegalArgumentException("Filename too long (max 255 characters)")
        }

        // Check for empty filename
        if (fileName.isBlank()) {
            throw IllegalArgumentException("Filename cannot be empty")
        }
    }

    /**
     * Sanitizes a filename by removing potentially dangerous characters
     * Returns a safe filename suitable for storage
     */
    fun sanitizeFileName(fileName: String): String {
        return fileName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_") // Replace unsafe chars with underscore
            .take(255) // Limit length
            .trim('_', '.', '-') // Remove leading/trailing special chars
            .ifBlank { "file" } // Fallback for empty names
    }

    /**
     * Gets file extension from filename
     */
    fun getFileExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex)
        } else {
            ""
        }
    }

    /**
     * Validates if content type is an allowed image type
     */
    fun isAllowedContentType(contentType: String?): Boolean {
        return contentType != null && contentType in allowedContentTypes
    }
}
