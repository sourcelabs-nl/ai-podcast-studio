package com.aisummarypodcast.tts

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.PreviewAudioProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class PreviewAudioStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private val audioConcatenator = mockk<AudioConcatenator>()
    private val appProperties = mockk<AppProperties>()
    private lateinit var store: PreviewAudioStore

    @BeforeEach
    fun setUp() {
        every { appProperties.previewAudio } returns PreviewAudioProperties(
            directory = tempDir.toString(),
            retentionMinutes = 60
        )
        store = PreviewAudioStore(appProperties, audioConcatenator)
    }

    private fun age(file: Path, minutes: Long) {
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(minutes, ChronoUnit.MINUTES)))
    }

    @Test
    fun `a single chunk is written straight through and found again`() {
        val audioId = store.write("podcast-1", listOf(byteArrayOf(1, 2, 3)), requiresConcatenation = false)

        val found = store.find("podcast-1", audioId)

        assertNotNull(found)
        assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(found!!))
    }

    @Test
    fun `the audio id is an opaque uuid`() {
        val audioId = store.write("podcast-1", listOf(byteArrayOf(1)), requiresConcatenation = false)

        assertEquals(audioId, UUID.fromString(audioId).toString())
    }

    @Test
    fun `another podcast cannot read this podcast's preview audio`() {
        val audioId = store.write("podcast-1", listOf(byteArrayOf(1)), requiresConcatenation = false)

        assertNull(store.find("podcast-2", audioId))
    }

    @Test
    fun `an unknown audio id is not found`() {
        store.write("podcast-1", listOf(byteArrayOf(1)), requiresConcatenation = false)

        assertNull(store.find("podcast-1", UUID.randomUUID().toString()))
    }

    @Test
    fun `a malformed audio id is refused before it reaches the filesystem`() {
        store.write("podcast-1", listOf(byteArrayOf(1)), requiresConcatenation = false)

        assertNull(store.find("podcast-1", "../../etc/passwd"))
        assertNull(store.find("podcast-1", "not-a-uuid"))
    }

    @Test
    fun `the sweep deletes audio older than the retention window`() {
        val expired = store.write("podcast-1", listOf(byteArrayOf(1)), requiresConcatenation = false)
        age(store.find("podcast-1", expired)!!, 90)

        val deleted = store.sweepExpired()

        assertEquals(1, deleted)
        assertNull(store.find("podcast-1", expired))
    }

    @Test
    fun `the sweep keeps audio inside the retention window`() {
        val fresh = store.write("podcast-1", listOf(byteArrayOf(1)), requiresConcatenation = false)
        age(store.find("podcast-1", fresh)!!, 10)

        val deleted = store.sweepExpired()

        assertEquals(0, deleted)
        assertNotNull(store.find("podcast-1", fresh))
    }

    @Test
    fun `the sweep spans every podcast and removes emptied directories`() {
        val first = store.write("podcast-1", listOf(byteArrayOf(1)), requiresConcatenation = false)
        val second = store.write("podcast-2", listOf(byteArrayOf(2)), requiresConcatenation = false)
        age(store.find("podcast-1", first)!!, 90)
        age(store.find("podcast-2", second)!!, 90)

        assertEquals(2, store.sweepExpired())
        assertFalse(Files.exists(tempDir.resolve("podcast-1")))
        assertFalse(Files.exists(tempDir.resolve("podcast-2")))
    }

    @Test
    fun `the sweep tolerates a store that was never written to`() {
        assertEquals(0, PreviewAudioStore(appProperties, audioConcatenator).sweepExpired())
    }
}
