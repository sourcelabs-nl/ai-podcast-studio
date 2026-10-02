package com.aisummarypodcast.podcast

import org.junit.jupiter.api.Assertions.assertEquals
import com.aisummarypodcast.api.InvalidRequestException
import com.aisummarypodcast.config.AppProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path

class PodcastImageServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var service: PodcastImageService

    @BeforeEach
    fun setup() {
        val appProperties = mockk<AppProperties> {
            every { episodes } returns mockk {
                every { directory } returns tempDir.toString()
            }
        }
        service = PodcastImageService(appProperties)
    }

    @Test
    fun `upload stores JPEG image`() {
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(100)
        val file = mockMultipartFile("image/jpeg", jpegBytes)

        val path = service.upload("pod-1", file)

        assertTrue(path.endsWith("podcast-image.jpg"))
        assertTrue(Files.exists(Path.of(path)))
    }

    @Test
    fun `upload stores PNG image`() {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) + ByteArray(100)
        val file = mockMultipartFile("image/png", pngBytes)

        val path = service.upload("pod-1", file)

        assertTrue(path.endsWith("podcast-image.png"))
    }

    @Test
    fun `upload stores WebP image`() {
        val webpBytes = "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray() + ByteArray(100)
        val file = mockMultipartFile("image/webp", webpBytes)

        val path = service.upload("pod-1", file)

        assertTrue(path.endsWith("podcast-image.webp"))
    }

    @Test
    fun `upload rejects unsupported content type`() {
        val file = mockMultipartFile("image/gif", ByteArray(100))

        val ex = assertThrows<InvalidRequestException> {
            service.upload("pod-1", file)
        }
        assertEquals("Only JPEG, PNG, and WebP images are accepted", ex.message)
    }

    @Test
    fun `upload rejects missing content type`() {
        val file = mockk<MultipartFile> {
            every { contentType } returns null
        }

        val ex = assertThrows<InvalidRequestException> {
            service.upload("pod-1", file)
        }
        assertEquals("Content type is required", ex.message)
    }

    @Test
    fun `upload rejects file exceeding 1MB`() {
        val largeBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(1_048_577)
        val file = mockMultipartFile("image/jpeg", largeBytes)

        val ex = assertThrows<InvalidRequestException> {
            service.upload("pod-1", file)
        }
        assertEquals("Maximum file size is 1MB", ex.message)
    }

    @Test
    fun `upload rejects JPEG with wrong magic bytes`() {
        val fakeJpeg = byteArrayOf(0x00, 0x00, 0x00, 0x00) + ByteArray(100)
        val file = mockMultipartFile("image/jpeg", fakeJpeg)

        val ex = assertThrows<InvalidRequestException> {
            service.upload("pod-1", file)
        }
        assertEquals("File content does not match declared content type", ex.message)
    }

    @Test
    fun `upload rejects WebP with wrong magic bytes`() {
        val fakeWebp = "NOT_".toByteArray() + ByteArray(8) + ByteArray(100)
        val file = mockMultipartFile("image/webp", fakeWebp)

        val ex = assertThrows<InvalidRequestException> {
            service.upload("pod-1", file)
        }
        assertEquals("File does not appear to be a valid WebP image", ex.message)
    }

    @Test
    fun `upload replaces existing image of different type`() {
        val podcastDir = tempDir.resolve("pod-1")
        Files.createDirectories(podcastDir)
        Files.write(podcastDir.resolve("podcast-image.jpg"), ByteArray(10))

        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) + ByteArray(100)
        val file = mockMultipartFile("image/png", pngBytes)

        service.upload("pod-1", file)

        assertFalse(Files.exists(podcastDir.resolve("podcast-image.jpg")))
        assertTrue(Files.exists(podcastDir.resolve("podcast-image.png")))
    }

    @Test
    fun `get returns path when image exists`() {
        val podcastDir = tempDir.resolve("pod-1")
        Files.createDirectories(podcastDir)
        Files.write(podcastDir.resolve("podcast-image.png"), ByteArray(10))

        val result = service.get("pod-1")

        assertNotNull(result)
        assertTrue(result!!.toString().endsWith("podcast-image.png"))
    }

    @Test
    fun `get returns null when no image exists`() {
        assertNull(service.get("nonexistent"))
    }

    @Test
    fun `delete removes existing image`() {
        val podcastDir = tempDir.resolve("pod-1")
        Files.createDirectories(podcastDir)
        Files.write(podcastDir.resolve("podcast-image.jpg"), ByteArray(10))

        assertTrue(service.delete("pod-1"))
        assertFalse(Files.exists(podcastDir.resolve("podcast-image.jpg")))
    }

    @Test
    fun `delete returns false when no image exists`() {
        assertFalse(service.delete("nonexistent"))
    }

    private fun mockMultipartFile(contentType: String, bytes: ByteArray): MultipartFile = mockk {
        every { this@mockk.contentType } returns contentType
        every { size } returns bytes.size.toLong()
        every { this@mockk.bytes } returns bytes
    }
}
