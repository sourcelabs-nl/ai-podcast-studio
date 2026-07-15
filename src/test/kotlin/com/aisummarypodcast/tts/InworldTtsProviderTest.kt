package com.aisummarypodcast.tts

import com.aisummarypodcast.store.PodcastStyle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

class InworldTtsProviderTest {

    private val apiClient = mockk<InworldApiClient>()
    private val provider = InworldTtsProvider(apiClient)

    private val sampleAudio = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))

    @Test
    fun `maxChunkSize is 2000`() {
        assertEquals(2000, provider.maxChunkSize)
    }

    @Test
    fun `generates single-speaker audio with default voice`() = runTest {
        val request = TtsRequest(
            script = "Hello world",
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } returns InworldSpeechResponse(sampleAudio, 11)

        val result = provider.generate(request)

        assertEquals(1, result.audioChunks.size)
        assertEquals(11, result.totalCharacters)
        assertFalse(result.requiresConcatenation)
        assertEquals("inworld-tts-1.5-max", result.model)
    }

    @Test
    fun `uses model from ttsSettings when specified`() = runTest {
        val request = TtsRequest(
            script = "Test",
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = mapOf("model" to "inworld-tts-1.5-mini"),
            language = "en",
            userId = "u1"
        )
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Test", "inworld-tts-1.5-mini", InworldSynthesisOptions(temperature = 0.8))
        } returns InworldSpeechResponse(sampleAudio, 4)

        val result = provider.generate(request)

        assertEquals("inworld-tts-1.5-mini", result.model)
    }

    @Test
    fun `throws when default voice is missing for monologue`() {
        val request = TtsRequest(
            script = "Test",
            ttsVoices = emptyMap(),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )
        assertThrows<IllegalStateException> { runBlocking { provider.generate(request) } }
    }

    @Test
    fun `generates dialogue with per-turn voice`() = runTest {
        val request = TtsRequest(
            script = "<host>Hello there!</host><cohost>Hey, how are you?</cohost>",
            ttsVoices = mapOf("host" to "voice-1", "cohost" to "voice-2"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello there!", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } returns InworldSpeechResponse(sampleAudio, 12)
        every {
            apiClient.synthesizeSpeech("u1", "voice-2", "Hey, how are you?", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } returns InworldSpeechResponse(sampleAudio, 17)

        val result = provider.generate(request)

        assertEquals(2, result.audioChunks.size)
        assertEquals(29, result.totalCharacters)
        assertTrue(result.requiresConcatenation)
    }

    @Test
    fun `throws when dialogue role has no configured voice`() {
        val request = TtsRequest(
            script = "<host>Hello!</host><guest>Hi!</guest>",
            ttsVoices = mapOf("host" to "voice-1"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        assertThrows<IllegalStateException> { runBlocking { provider.generate(request) } }
    }

    @Test
    fun `generates interview style with interviewer and expert roles`() = runTest {
        val request = TtsRequest(
            script = "<interviewer>What happened?</interviewer><expert>A lot of things.</expert>",
            ttsVoices = mapOf("interviewer" to "voice-1", "expert" to "voice-2"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "What happened?", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } returns InworldSpeechResponse(sampleAudio, 14)
        every {
            apiClient.synthesizeSpeech("u1", "voice-2", "A lot of things.", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } returns InworldSpeechResponse(sampleAudio, 16)

        val result = provider.generate(request)

        assertEquals(2, result.audioChunks.size)
        assertEquals(30, result.totalCharacters)
    }

    @Test
    fun `passes speed and temperature from ttsSettings`() = runTest {
        val request = TtsRequest(
            script = "Hello world",
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = mapOf("speed" to "1.2", "temperature" to "0.8"),
            language = "en",
            userId = "u1"
        )
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(speed = 1.2, temperature = 0.8))
        } returns InworldSpeechResponse(sampleAudio, 11)

        val result = provider.generate(request)

        assertEquals(1, result.audioChunks.size)
        assertEquals(11, result.totalCharacters)
    }

    @Test
    fun `uses default temperature of 0_8 when not configured`() = runTest {
        val request = TtsRequest(
            script = "Hello world",
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } returns InworldSpeechResponse(sampleAudio, 11)

        val result = provider.generate(request)

        assertEquals(1, result.audioChunks.size)
        verify {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        }
    }

    @Test
    fun `uses explicit temperature when configured`() = runTest {
        val request = TtsRequest(
            script = "Hello world",
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = mapOf("temperature" to "1.1"),
            language = "en",
            userId = "u1"
        )
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 1.1))
        } returns InworldSpeechResponse(sampleAudio, 11)

        val result = provider.generate(request)

        assertEquals(1, result.audioChunks.size)
        verify {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 1.1))
        }
    }

    @Test
    fun `passes deliveryMode and suppresses temperature default when set`() = runTest {
        val request = TtsRequest(
            script = "Hello world",
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = mapOf("model" to "inworld-tts-2", "deliveryMode" to "expressive"),
            language = "en",
            userId = "u1"
        )
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", InworldSynthesisOptions(deliveryMode = "EXPRESSIVE"))
        } returns InworldSpeechResponse(sampleAudio, 11)

        val result = provider.generate(request)

        assertEquals(1, result.audioChunks.size)
        verify {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", InworldSynthesisOptions(deliveryMode = "EXPRESSIVE"))
        }
    }

    @Test
    fun `blank deliveryMode is treated as unset`() = runTest {
        val request = TtsRequest(
            script = "Hello world",
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = mapOf("deliveryMode" to ""),
            language = "en",
            userId = "u1"
        )
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } returns InworldSpeechResponse(sampleAudio, 11)

        val result = provider.generate(request)

        assertEquals(1, result.audioChunks.size)
    }

    // --- Parallel generation tests ---

    @Test
    fun `monologue generates multiple chunks in parallel and preserves order`() = runTest {
        // Create a script that will be split into multiple chunks (each > 2000 chars total)
        val chunk1Text = "A".repeat(1500) + ". "
        val chunk2Text = "B".repeat(1500) + ". "
        val chunk3Text = "C".repeat(1000)
        val script = chunk1Text + chunk2Text + chunk3Text

        val audio1 = Base64.getEncoder().encodeToString(byteArrayOf(1))
        val audio2 = Base64.getEncoder().encodeToString(byteArrayOf(2))
        val audio3 = Base64.getEncoder().encodeToString(byteArrayOf(3))

        every { apiClient.synthesizeSpeech("u1", "voice-1", any(), "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8)) } answers {
            val text = arg<String>(2)
            when {
                text.startsWith("A") -> InworldSpeechResponse(audio1, text.length)
                text.startsWith("B") -> InworldSpeechResponse(audio2, text.length)
                else -> InworldSpeechResponse(audio3, text.length)
            }
        }

        val request = TtsRequest(
            script = script,
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        val result = provider.generate(request)

        assertTrue(result.audioChunks.size >= 3)
        assertTrue(result.requiresConcatenation)
        // First chunk should decode to byte 1 (from chunk starting with 'A')
        assertArrayEquals(byteArrayOf(1), result.audioChunks[0])
        assertArrayEquals(byteArrayOf(2), result.audioChunks[1])
        assertArrayEquals(byteArrayOf(3), result.audioChunks[2])
    }

    @Test
    fun `dialogue generates all turn chunks in parallel and preserves order`() = runTest {
        val audio1 = Base64.getEncoder().encodeToString(byteArrayOf(10))
        val audio2 = Base64.getEncoder().encodeToString(byteArrayOf(20))
        val audio3 = Base64.getEncoder().encodeToString(byteArrayOf(30))

        val script = "<host>First turn.</host><cohost>Second turn.</cohost><host>Third turn.</host>"

        every { apiClient.synthesizeSpeech("u1", "voice-1", "First turn.", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8)) } returns InworldSpeechResponse(audio1, 11)
        every { apiClient.synthesizeSpeech("u1", "voice-2", "Second turn.", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8)) } returns InworldSpeechResponse(audio2, 12)
        every { apiClient.synthesizeSpeech("u1", "voice-1", "Third turn.", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8)) } returns InworldSpeechResponse(audio3, 11)

        val request = TtsRequest(
            script = script,
            ttsVoices = mapOf("host" to "voice-1", "cohost" to "voice-2"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        val result = provider.generate(request)

        assertEquals(3, result.audioChunks.size)
        assertArrayEquals(byteArrayOf(10), result.audioChunks[0])
        assertArrayEquals(byteArrayOf(20), result.audioChunks[1])
        assertArrayEquals(byteArrayOf(30), result.audioChunks[2])
        assertEquals(34, result.totalCharacters)
    }

    // --- Retry on 429 tests ---

    @Test
    fun `retries on 429 and succeeds on second attempt`() = runTest {
        val request = TtsRequest(
            script = "Hello world",
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        var callCount = 0
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } answers {
            callCount++
            if (callCount == 1) throw InworldRateLimitException("Rate limited")
            InworldSpeechResponse(sampleAudio, 11)
        }

        val result = provider.generate(request)

        assertEquals(1, result.audioChunks.size)
        assertEquals(11, result.totalCharacters)
        assertEquals(2, callCount)
    }

    @Test
    fun `throws InworldRateLimitException after exhausting retries`() {
        val request = TtsRequest(
            script = "Hello world",
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } throws InworldRateLimitException("Rate limited")

        val exception = assertThrows<InworldRateLimitException> { runBlocking { provider.generate(request) } }
        assertEquals("Rate limited", exception.message)

        verify(exactly = 3) {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        }
    }

    // --- Retry on transient 5xx tests ---

    @Test
    fun `retries on transient 5xx and succeeds on second attempt`() = runTest {
        val request = TtsRequest(
            script = "Hello world",
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        var callCount = 0
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } answers {
            callCount++
            if (callCount == 1) throw InworldTransientException("Inworld API error (HTTP 503): upstream connect error")
            InworldSpeechResponse(sampleAudio, 11)
        }

        val result = provider.generate(request)

        assertEquals(1, result.audioChunks.size)
        assertEquals(11, result.totalCharacters)
        assertEquals(2, callCount)
    }

    @Test
    fun `throws InworldTransientException after exhausting retries`() {
        val request = TtsRequest(
            script = "Hello world",
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )

        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } throws InworldTransientException("Inworld API error (HTTP 503): upstream connect error")

        val exception = assertThrows<InworldTransientException> { runBlocking { provider.generate(request) } }
        assertEquals("Inworld API error (HTTP 503): upstream connect error", exception.message)

        verify(exactly = 3) {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        }
    }

    // --- Post-processing integration tests ---

    @Test
    fun `monologue post-processes script before sending to API`() = runTest {
        val request = TtsRequest(
            script = "**Breaking** news! [excitedly] Check [this](https://example.com).",
            ttsVoices = mapOf("default" to "voice-1"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "*Breaking* news! Check this.", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } returns InworldSpeechResponse(sampleAudio, 28)

        val result = provider.generate(request)

        assertEquals(1, result.audioChunks.size)
        verify {
            apiClient.synthesizeSpeech("u1", "voice-1", "*Breaking* news! Check this.", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        }
    }

    @Test
    fun `dialogue post-processes each turn before sending to API`() = runTest {
        val request = TtsRequest(
            script = "<host>**Welcome** to the show! [cheerfully] Hello.</host><cohost>[sigh] Thanks for having me.</cohost>",
            ttsVoices = mapOf("host" to "voice-1", "cohost" to "voice-2"),
            ttsSettings = emptyMap(),
            language = "en",
            userId = "u1"
        )
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "*Welcome* to the show! Hello.", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } returns InworldSpeechResponse(sampleAudio, 30)
        every {
            apiClient.synthesizeSpeech("u1", "voice-2", "[sigh] Thanks for having me.", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        } returns InworldSpeechResponse(sampleAudio, 28)

        val result = provider.generate(request)

        assertEquals(2, result.audioChunks.size)
        verify {
            apiClient.synthesizeSpeech("u1", "voice-1", "*Welcome* to the show! Hello.", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
            apiClient.synthesizeSpeech("u1", "voice-2", "[sigh] Thanks for having me.", "inworld-tts-1.5-max", InworldSynthesisOptions(temperature = 0.8))
        }
    }

    // --- Script guidelines tests ---

    @Test
    fun `casual style guidelines include filler words`() {
        val guidelines = provider.scriptGuidelines(PodcastStyle.CASUAL)
        assertTrue(guidelines.contains("filler words"))
        assertTrue(guidelines.contains("[sigh]"))
        assertTrue(guidelines.contains("*word*"))
    }

    @Test
    fun `dialogue style guidelines include filler words`() {
        val guidelines = provider.scriptGuidelines(PodcastStyle.DIALOGUE)
        assertTrue(guidelines.contains("filler words"))
    }

    @Test
    fun `executive summary guidelines suppress filler words`() {
        val guidelines = provider.scriptGuidelines(PodcastStyle.EXECUTIVE_SUMMARY)
        assertTrue(guidelines.contains("Avoid filler words"))
        assertTrue(guidelines.contains("minimize non-verbal tags"))
    }

    @Test
    fun `news briefing guidelines suppress filler words`() {
        val guidelines = provider.scriptGuidelines(PodcastStyle.NEWS_BRIEFING)
        assertTrue(guidelines.contains("Avoid filler words"))
    }

    @Test
    fun `all styles include core markup instructions`() {
        for (style in PodcastStyle.entries) {
            val guidelines = provider.scriptGuidelines(style)
            assertTrue(guidelines.contains("[sigh]"), "Missing non-verbal tags for $style")
            assertTrue(guidelines.contains("*word*"), "Missing emphasis for $style")
            assertTrue(guidelines.contains("..."), "Missing pacing for $style")
        }
    }

    @Test
    fun `all styles include text normalization rules`() {
        for (style in PodcastStyle.entries) {
            val guidelines = provider.scriptGuidelines(style)
            assertTrue(guidelines.contains("spoken form"), "Missing text normalization for $style")
        }
    }

    @Test
    fun `all styles warn against double asterisks`() {
        for (style in PodcastStyle.entries) {
            val guidelines = provider.scriptGuidelines(style)
            assertTrue(guidelines.contains("double asterisks"), "Missing double asterisk warning for $style")
        }
    }

    @Test
    fun `all styles include anti-markdown rules`() {
        for (style in PodcastStyle.entries) {
            val guidelines = provider.scriptGuidelines(style)
            assertTrue(guidelines.contains("NEVER use markdown"), "Missing anti-markdown rule for $style")
        }
    }

    @Test
    fun `all styles include contractions guidance`() {
        for (style in PodcastStyle.entries) {
            val guidelines = provider.scriptGuidelines(style)
            assertTrue(guidelines.contains("contractions"), "Missing contractions guidance for $style")
        }
    }

    @Test
    fun `all styles include punctuation rule`() {
        for (style in PodcastStyle.entries) {
            val guidelines = provider.scriptGuidelines(style)
            assertTrue(guidelines.contains("end sentences with proper punctuation"), "Missing punctuation rule for $style")
        }
    }

    // --- Pronunciation dictionary tests ---

    @Test
    fun `guidelines include pronunciation guide when pronunciations provided`() {
        val pronunciations = mapOf("Anthropic" to "/ænˈθɹɒpɪk/", "Jarno" to "/jɑrnoː/")
        val guidelines = provider.scriptGuidelines(PodcastStyle.CASUAL, pronunciations)
        assertTrue(guidelines.contains("Pronunciation Guide"))
        assertTrue(guidelines.contains("- Anthropic → /ænˈθɹɒpɪk/"))
        assertTrue(guidelines.contains("- Jarno → /jɑrnoː/"))
        assertTrue(guidelines.contains("EVERY occurrence"))
        assertTrue(guidelines.contains("REPLACE the word"))
        assertTrue(guidelines.contains("ONLY use IPA notation for the exact terms listed below"))
    }

    @Test
    fun `guidelines omit pronunciation section when empty map`() {
        val guidelines = provider.scriptGuidelines(PodcastStyle.CASUAL, emptyMap())
        assertFalse(guidelines.contains("Pronunciation Guide"))
    }

    @Test
    fun `guidelines omit pronunciation section when no pronunciations parameter`() {
        val guidelines = provider.scriptGuidelines(PodcastStyle.CASUAL)
        assertFalse(guidelines.contains("Pronunciation Guide"))
    }

    @Test
    fun `pronunciation guide preserves core guidelines and style additions`() {
        val pronunciations = mapOf("LLaMA" to "/ˈlɑːmə/")
        val guidelines = provider.scriptGuidelines(PodcastStyle.CASUAL, pronunciations)
        assertTrue(guidelines.contains("[sigh]"), "Missing core guidelines")
        assertTrue(guidelines.contains("filler words"), "Missing casual style addition")
        assertTrue(guidelines.contains("- LLaMA → /ˈlɑːmə/"), "Missing pronunciation entry")
    }
}
