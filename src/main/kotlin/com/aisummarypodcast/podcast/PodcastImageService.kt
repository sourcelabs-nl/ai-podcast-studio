package com.aisummarypodcast.podcast

import com.aisummarypodcast.api.InvalidRequestException
import com.aisummarypodcast.config.AppProperties
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path

@Service
class PodcastImageService(private val appProperties: AppProperties) {

    companion object {
        private const val MAX_SIZE = 1_048_576L // 1MB
        private val ALLOWED_TYPES = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp"
        )
        private val MAGIC_BYTES = mapOf(
            "image/jpeg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
            "image/png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            "image/webp" to "RIFF".toByteArray() // WebP starts with RIFF....WEBP
        )
    }

    fun upload(podcastId: String, file: MultipartFile): String {
        val contentType = file.contentType
            ?: throw InvalidRequestException("Content type is required")

        val ext = ALLOWED_TYPES[contentType]
            ?: throw InvalidRequestException("Only JPEG, PNG, and WebP images are accepted")

        if (file.size > MAX_SIZE) {
            throw InvalidRequestException("Maximum file size is 1MB")
        }

        val bytes = file.bytes
        validateMagicBytes(contentType, bytes)

        val podcastDir = podcastDir(podcastId)
        Files.createDirectories(podcastDir)

        // Remove any existing podcast image
        deleteExistingImages(podcastDir)

        val imagePath = podcastDir.resolve("podcast-image.$ext")
        Files.write(imagePath, bytes)
        return imagePath.toString()
    }

    fun get(podcastId: String): Path? {
        val podcastDir = podcastDir(podcastId)
        if (!Files.exists(podcastDir)) return null
        return ALLOWED_TYPES.values
            .map { podcastDir.resolve("podcast-image.$it") }
            .firstOrNull { Files.exists(it) }
    }

    fun delete(podcastId: String): Boolean {
        val existing = get(podcastId) ?: return false
        Files.deleteIfExists(existing)
        return true
    }

    private fun podcastDir(podcastId: String): Path =
        Path.of(appProperties.episodes.directory, podcastId)

    private fun deleteExistingImages(podcastDir: Path) {
        ALLOWED_TYPES.values.forEach { ext ->
            Files.deleteIfExists(podcastDir.resolve("podcast-image.$ext"))
        }
    }

    private fun validateMagicBytes(contentType: String, bytes: ByteArray) {
        val expected = MAGIC_BYTES[contentType] ?: return
        if (bytes.size < expected.size) {
            throw InvalidRequestException("File is too small to be a valid image")
        }
        // For WebP, check RIFF header and WEBP at offset 8
        if (contentType == "image/webp") {
            if (!bytes.slice(0..3).toByteArray().contentEquals("RIFF".toByteArray()) ||
                bytes.size < 12 ||
                !bytes.slice(8..11).toByteArray().contentEquals("WEBP".toByteArray())
            ) {
                throw InvalidRequestException("File does not appear to be a valid WebP image")
            }
            return
        }
        if (!bytes.slice(0 until expected.size).toByteArray().contentEquals(expected)) {
            throw InvalidRequestException("File content does not match declared content type")
        }
    }
}
