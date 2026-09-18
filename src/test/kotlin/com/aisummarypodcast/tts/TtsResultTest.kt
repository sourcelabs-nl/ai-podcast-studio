package com.aisummarypodcast.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TtsResultTest {

    @Test
    fun `totalCharacters matches sum of chunk lengths`() {
        val chunks = listOf("Hello world", "This is a test", "Final chunk")
        val expectedCharacters = chunks.sumOf { it.length }

        val result = TtsResult(
            audioChunks = chunks.map { AudioChunk("voice-1", it.toByteArray()) },
            totalCharacters = expectedCharacters,
            model = "tts-1"
        )

        assertEquals(36, result.totalCharacters)
        assertEquals(3, result.audioChunks.size)
    }

    @Test
    fun `totalCharacters is zero for empty chunks`() {
        val result = TtsResult(audioChunks = emptyList(), totalCharacters = 0, model = "tts-1")

        assertEquals(0, result.totalCharacters)
        assertEquals(0, result.audioChunks.size)
    }
}
