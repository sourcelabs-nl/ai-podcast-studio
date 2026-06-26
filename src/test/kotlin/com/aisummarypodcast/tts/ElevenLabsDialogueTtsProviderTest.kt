package com.aisummarypodcast.tts

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ElevenLabsDialogueTtsProviderTest {

    private val apiClient = mockk<ElevenLabsApiClient>()
    private val provider = ElevenLabsDialogueTtsProvider(apiClient)

    @Test
    fun `generates dialogue audio from tagged script`() = runTest {
        val request = TtsRequest(
            script = "<host>Hello!</host><cohost>Hi there!</cohost>",
            ttsVoices = mapOf("host" to "v1", "cohost" to "v2"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        every { apiClient.textToDialogue("u1", any(), null) } returns byteArrayOf(1, 2, 3)

        val result = provider.generate(request)

        assertEquals(1, result.audioChunks.size)
        assertFalse(result.requiresConcatenation)
        assertEquals(15, result.totalCharacters)

        verify {
            apiClient.textToDialogue("u1", match { inputs ->
                inputs.size == 2 &&
                inputs[0] == DialogueInput("Hello!", "v1") &&
                inputs[1] == DialogueInput("Hi there!", "v2")
            }, null)
        }
    }

    @Test
    fun `throws when voice not configured for role`() {
        val request = TtsRequest(
            script = "<host>Hello!</host><narrator>Narration</narrator>",
            ttsVoices = mapOf("host" to "v1"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        assertThrows<IllegalStateException> { runBlocking { provider.generate(request) } }
    }

    @Test
    fun `throws for empty dialogue`() {
        val request = TtsRequest(
            script = "",
            ttsVoices = mapOf("host" to "v1"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        assertThrows<IllegalStateException> { runBlocking { provider.generate(request) } }
    }

    @Test
    fun `passes settings to API`() = runTest {
        val request = TtsRequest(
            script = "<host>Hello!</host>",
            ttsVoices = mapOf("host" to "v1"),
            ttsSettings = mapOf("stability" to "0.7"),
            language = "en",
            userId = "u1"
        )

        every { apiClient.textToDialogue("u1", any(), mapOf("stability" to "0.7")) } returns byteArrayOf(1)

        provider.generate(request)

        verify { apiClient.textToDialogue("u1", any(), mapOf("stability" to "0.7")) }
    }

    @Test
    fun `short dialogue produces single batch with requiresConcatenation false`() = runTest {
        val shortText = "a".repeat(2000)
        val request = TtsRequest(
            script = "<host>$shortText</host><cohost>$shortText</cohost>",
            ttsVoices = mapOf("host" to "v1", "cohost" to "v2"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        every { apiClient.textToDialogue("u1", any(), null) } returns byteArrayOf(1, 2, 3)

        val result = provider.generate(request)

        assertEquals(1, result.audioChunks.size)
        assertFalse(result.requiresConcatenation)
        verify(exactly = 1) { apiClient.textToDialogue("u1", any(), null) }
    }

    @Test
    fun `long dialogue is split into multiple batches with requiresConcatenation true`() = runTest {
        val longText = "a".repeat(3000)
        val request = TtsRequest(
            script = "<host>$longText</host><cohost>$longText</cohost><host>$longText</host>",
            ttsVoices = mapOf("host" to "v1", "cohost" to "v2"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        every { apiClient.textToDialogue("u1", any(), null) } returns byteArrayOf(1, 2, 3)

        val result = provider.generate(request)

        // 3 turns of 3000 chars each: each turn alone fits in a batch, but no two fit together (6000 > 5000)
        assertEquals(3, result.audioChunks.size)
        assertTrue(result.requiresConcatenation)
        assertEquals(9000, result.totalCharacters)
        verify(exactly = 3) { apiClient.textToDialogue("u1", any(), null) }
    }

    @Test
    fun `turns are never split across batches`() {
        val inputs = listOf(
            DialogueInput("a".repeat(2000), "v1"),
            DialogueInput("b".repeat(2000), "v2"),
            DialogueInput("c".repeat(2000), "v1"),
            DialogueInput("d".repeat(2000), "v2")
        )

        val batches = provider.batchInputs(inputs)

        assertEquals(2, batches.size)
        // First batch: turns 1+2 (4000 chars)
        assertEquals(2, batches[0].size)
        assertEquals("a".repeat(2000), batches[0][0].text)
        assertEquals("b".repeat(2000), batches[0][1].text)
        // Second batch: turns 3+4 (4000 chars)
        assertEquals(2, batches[1].size)
        assertEquals("c".repeat(2000), batches[1][0].text)
        assertEquals("d".repeat(2000), batches[1][1].text)
        // Each batch stays under MAX_CHARS
        for (batch in batches) {
            assertTrue(batch.sumOf { it.text.length } <= ElevenLabsDialogueTtsProvider.MAX_CHARS)
        }
    }
}
