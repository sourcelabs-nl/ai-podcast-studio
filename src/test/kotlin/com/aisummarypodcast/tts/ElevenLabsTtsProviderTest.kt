package com.aisummarypodcast.tts

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ElevenLabsTtsProviderTest {

    private val apiClient = mockk<ElevenLabsApiClient>()
    private val provider = ElevenLabsTtsProvider(apiClient)

    @Test
    fun `generates audio with default voice`() = runTest {
        val request = TtsRequest(
            script = "Hello world",
            ttsVoices = mapOf("default" to "voice123"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )
        every { apiClient.textToSpeech("u1", "voice123", "Hello world", null) } returns byteArrayOf(1, 2, 3)

        val result = provider.generate(request)

        assertEquals(1, result.audioChunks.size)
        assertEquals(11, result.totalCharacters)
        assertTrue(!result.requiresConcatenation)
    }

    @Test
    fun `passes voice settings to API client`() = runTest {
        val request = TtsRequest(
            script = "Test text",
            ttsVoices = mapOf("default" to "voice123"),
            ttsSettings = mapOf("stability" to "0.5"),
            language = "en",
            userId = "u1"
        )
        every { apiClient.textToSpeech("u1", "voice123", "Test text", mapOf("stability" to "0.5")) } returns byteArrayOf(1)

        provider.generate(request)

        verify { apiClient.textToSpeech("u1", "voice123", "Test text", mapOf("stability" to "0.5")) }
    }

    @Test
    fun `throws when default voice is missing`() {
        val request = TtsRequest(
            script = "Test",
            ttsVoices = mapOf("host" to "v1"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        assertThrows<IllegalStateException> { runBlocking { provider.generate(request) } }
    }
}
