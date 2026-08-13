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
import java.util.Collections

class InworldTtsProviderTest {

    private val apiClient = mockk<InworldApiClient>()
    private val provider = InworldTtsProvider(apiClient)

    private val sampleAudio = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))

    /** What the provider sends for a podcast with no ttsSettings and language "en". */
    private val defaultOptions = InworldSynthesisOptions(temperature = 0.8, language = "en")

    private fun request(
        script: String,
        ttsVoices: Map<String, String> = mapOf("default" to "voice-1"),
        ttsSettings: Map<String, String> = emptyMap(),
        language: String = "en"
    ) = TtsRequest(script = script, ttsVoices = ttsVoices, ttsSettings = ttsSettings, language = language, userId = "u1")

    /** Records the options each chunk text was synthesized with, across the parallel calls. */
    private fun recordCalls(): MutableMap<String, InworldSynthesisOptions> {
        val calls = Collections.synchronizedMap(mutableMapOf<String, InworldSynthesisOptions>())
        every { apiClient.synthesizeSpeech(any(), any(), any(), any(), any()) } answers {
            calls[arg(2)] = arg(4)
            InworldSpeechResponse(sampleAudio, arg<String>(2).length)
        }
        return calls
    }

    @Test
    fun `maxChunkSize is 1900`() {
        assertEquals(1900, provider.maxChunkSize)
    }

    @Test
    fun `default model is inworld-tts-2`() {
        assertEquals("inworld-tts-2", InworldTtsProvider.DEFAULT_MODEL)
    }

    @Test
    fun `generates single-speaker audio with default voice`() = runTest {
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions)
        } returns InworldSpeechResponse(sampleAudio, 11)

        val result = provider.generate(request("Hello world"))

        assertEquals(1, result.audioChunks.size)
        assertEquals(11, result.totalCharacters)
        assertFalse(result.requiresConcatenation)
        assertEquals("inworld-tts-2", result.model)
    }

    @Test
    fun `uses model from ttsSettings when specified`() = runTest {
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Test", "inworld-tts-1.5-mini", defaultOptions)
        } returns InworldSpeechResponse(sampleAudio, 4)

        val result = provider.generate(request("Test", ttsSettings = mapOf("model" to "inworld-tts-1.5-mini")))

        assertEquals("inworld-tts-1.5-mini", result.model)
    }

    @Test
    fun `throws when default voice is missing for monologue`() {
        assertThrows<IllegalStateException> {
            runBlocking { provider.generate(request("Test", ttsVoices = emptyMap())) }
        }
    }

    @Test
    fun `generates dialogue with per-turn voice`() = runTest {
        val calls = recordCalls()

        val result = provider.generate(
            request(
                "<host>Hello there!</host><cohost>Hey, how are you?</cohost>",
                ttsVoices = mapOf("host" to "voice-1", "cohost" to "voice-2")
            )
        )

        assertEquals(2, result.audioChunks.size)
        assertEquals(29, result.totalCharacters)
        assertTrue(result.requiresConcatenation)
        assertEquals(setOf("Hello there!", "Hey, how are you?"), calls.keys)
    }

    @Test
    fun `throws when dialogue role has no configured voice`() {
        assertThrows<IllegalStateException> {
            runBlocking {
                provider.generate(
                    request("<host>Hello!</host><guest>Hi!</guest>", ttsVoices = mapOf("host" to "voice-1"))
                )
            }
        }
    }

    @Test
    fun `generates interview style with interviewer and expert roles`() = runTest {
        recordCalls()

        val result = provider.generate(
            request(
                "<interviewer>What happened?</interviewer><expert>A lot of things.</expert>",
                ttsVoices = mapOf("interviewer" to "voice-1", "expert" to "voice-2")
            )
        )

        assertEquals(2, result.audioChunks.size)
        assertEquals(30, result.totalCharacters)
    }

    @Test
    fun `passes speed and temperature from ttsSettings`() = runTest {
        val expected = InworldSynthesisOptions(speed = 1.2, temperature = 0.8, language = "en")
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", expected)
        } returns InworldSpeechResponse(sampleAudio, 11)

        provider.generate(request("Hello world", ttsSettings = mapOf("speed" to "1.2", "temperature" to "0.8")))

        verify { apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", expected) }
    }

    @Test
    fun `uses default temperature of 0_8 when not configured`() = runTest {
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions)
        } returns InworldSpeechResponse(sampleAudio, 11)

        provider.generate(request("Hello world"))

        verify { apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions) }
    }

    @Test
    fun `uses explicit temperature when configured`() = runTest {
        val expected = InworldSynthesisOptions(temperature = 1.1, language = "en")
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", expected)
        } returns InworldSpeechResponse(sampleAudio, 11)

        provider.generate(request("Hello world", ttsSettings = mapOf("temperature" to "1.1")))

        verify { apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", expected) }
    }

    @Test
    fun `passes deliveryMode and suppresses temperature default when set`() = runTest {
        val expected = InworldSynthesisOptions(deliveryMode = "CREATIVE", language = "en")
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", expected)
        } returns InworldSpeechResponse(sampleAudio, 11)

        provider.generate(request("Hello world", ttsSettings = mapOf("deliveryMode" to "creative")))

        verify { apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", expected) }
    }

    @Test
    fun `blank deliveryMode is treated as unset`() = runTest {
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions)
        } returns InworldSpeechResponse(sampleAudio, 11)

        val result = provider.generate(request("Hello world", ttsSettings = mapOf("deliveryMode" to "")))

        assertEquals(1, result.audioChunks.size)
    }

    @Test
    fun `passes enhanceGeneration from ttsSettings`() = runTest {
        val expected = defaultOptions.copy(enhanceGeneration = true)
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", expected)
        } returns InworldSpeechResponse(sampleAudio, 11)

        provider.generate(request("Hello world", ttsSettings = mapOf("enhanceGeneration" to "true")))

        verify { apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", expected) }
    }

    @Test
    fun `enhanceGeneration is left unset when not configured`() = runTest {
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions)
        } returns InworldSpeechResponse(sampleAudio, 11)

        provider.generate(request("Hello world"))

        verify { apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions) }
    }

    @Test
    fun `non-boolean enhanceGeneration is ignored`() = runTest {
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions)
        } returns InworldSpeechResponse(sampleAudio, 11)

        provider.generate(request("Hello world", ttsSettings = mapOf("enhanceGeneration" to "yes")))

        verify { apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions) }
    }

    // --- Language ---

    @Test
    fun `forwards the podcast language`() = runTest {
        val expected = InworldSynthesisOptions(temperature = 0.8, language = "nl")
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hallo wereld", "inworld-tts-2", expected)
        } returns InworldSpeechResponse(sampleAudio, 12)

        provider.generate(request("Hallo wereld", language = "nl"))

        verify { apiClient.synthesizeSpeech("u1", "voice-1", "Hallo wereld", "inworld-tts-2", expected) }
    }

    @Test
    fun `omits a blank language`() = runTest {
        val calls = recordCalls()

        provider.generate(request("Hello world", language = " "))

        assertNull(calls.getValue("Hello world").language)
    }

    // --- Synthesis context ---

    @Test
    fun `first chunk has no context and later chunks carry their predecessors`() = runTest {
        val calls = recordCalls()
        val sentences = ('A'..'E').map { it.toString().repeat(1000) + ". " }

        provider.generate(request(sentences.joinToString("")))

        val chunks = calls.keys.sortedBy { it.first() }
        assertEquals(5, chunks.size)
        assertEquals(emptyList<String>(), calls.getValue(chunks[0]).previousRequests)
        assertEquals(listOf(chunks[0]), calls.getValue(chunks[1]).previousRequests)
    }

    @Test
    fun `context window is capped by total characters`() = runTest {
        val calls = recordCalls()
        val sentences = ('A'..'E').map { it.toString().repeat(1000) + ". " }

        provider.generate(request(sentences.joinToString("")))

        // Each chunk is ~1001 chars, so only the single most recent predecessor fits in 2000
        val chunks = calls.keys.sortedBy { it.first() }
        assertEquals(listOf(chunks[2]), calls.getValue(chunks[3]).previousRequests)
    }

    @Test
    fun `context window is capped at three preceding texts`() = runTest {
        val calls = recordCalls()
        val script = (1..5).joinToString("") { "<host>Turn number $it.</host>" }

        provider.generate(request(script, ttsVoices = mapOf("host" to "voice-1", "cohost" to "voice-2")))

        assertEquals(
            listOf("Turn number 2.", "Turn number 3.", "Turn number 4."),
            calls.getValue("Turn number 5.").previousRequests
        )
    }

    @Test
    fun `dialogue turns carry the preceding turn as context`() = runTest {
        val calls = recordCalls()

        provider.generate(
            request(
                "<host>Hello there!</host><cohost>Hey, how are you?</cohost>",
                ttsVoices = mapOf("host" to "voice-1", "cohost" to "voice-2")
            )
        )

        assertEquals(emptyList<String>(), calls.getValue("Hello there!").previousRequests)
        assertEquals(listOf("Hello there!"), calls.getValue("Hey, how are you?").previousRequests)
    }

    // --- Steering ---

    @Test
    fun `re-emits the steering instruction on later chunks of a monologue`() = runTest {
        val calls = recordCalls()
        val sentences = ('A'..'C').map { it.toString().repeat(1000) + ". " }

        provider.generate(request("[warm and conversational] " + sentences.joinToString("")))

        assertEquals(3, calls.size)
        assertTrue(
            calls.keys.all { it.startsWith("[warm and conversational] ") },
            "Every chunk should carry the active instruction: ${calls.keys.map { it.take(30) }}"
        )
    }

    @Test
    fun `steering instructions are stripped on models without steering support`() = runTest {
        val calls = recordCalls()

        provider.generate(
            request("[warm and conversational] Hello.", ttsSettings = mapOf("model" to "inworld-tts-1.5-max"))
        )

        assertEquals(setOf("Hello."), calls.keys)
    }

    @Test
    fun `steering instructions do not leak across dialogue turns`() = runTest {
        val calls = recordCalls()

        provider.generate(
            request(
                "<host>[excited and fast] Big news!</host><cohost>Tell me more.</cohost>",
                ttsVoices = mapOf("host" to "voice-1", "cohost" to "voice-2")
            )
        )

        assertEquals(setOf("[excited and fast] Big news!", "Tell me more."), calls.keys)
    }

    // --- Parallel generation tests ---

    @Test
    fun `monologue generates multiple chunks in parallel and preserves order`() = runTest {
        val chunk1Text = "A".repeat(1500) + ". "
        val chunk2Text = "B".repeat(1500) + ". "
        val chunk3Text = "C".repeat(1000)
        val script = chunk1Text + chunk2Text + chunk3Text

        val audio1 = Base64.getEncoder().encodeToString(byteArrayOf(1))
        val audio2 = Base64.getEncoder().encodeToString(byteArrayOf(2))
        val audio3 = Base64.getEncoder().encodeToString(byteArrayOf(3))

        every { apiClient.synthesizeSpeech("u1", "voice-1", any(), "inworld-tts-2", any()) } answers {
            val text = arg<String>(2)
            when {
                text.startsWith("A") -> InworldSpeechResponse(audio1, text.length)
                text.startsWith("B") -> InworldSpeechResponse(audio2, text.length)
                else -> InworldSpeechResponse(audio3, text.length)
            }
        }

        val result = provider.generate(request(script))

        assertTrue(result.audioChunks.size >= 3)
        assertTrue(result.requiresConcatenation)
        assertArrayEquals(byteArrayOf(1), result.audioChunks[0])
        assertArrayEquals(byteArrayOf(2), result.audioChunks[1])
        assertArrayEquals(byteArrayOf(3), result.audioChunks[2])
    }

    @Test
    fun `dialogue generates all turn chunks in parallel and preserves order`() = runTest {
        val audio1 = Base64.getEncoder().encodeToString(byteArrayOf(10))
        val audio2 = Base64.getEncoder().encodeToString(byteArrayOf(20))
        val audio3 = Base64.getEncoder().encodeToString(byteArrayOf(30))

        every { apiClient.synthesizeSpeech("u1", any(), any(), "inworld-tts-2", any()) } answers {
            when (arg<String>(2)) {
                "First turn." -> InworldSpeechResponse(audio1, 11)
                "Second turn." -> InworldSpeechResponse(audio2, 12)
                else -> InworldSpeechResponse(audio3, 11)
            }
        }

        val result = provider.generate(
            request(
                "<host>First turn.</host><cohost>Second turn.</cohost><host>Third turn.</host>",
                ttsVoices = mapOf("host" to "voice-1", "cohost" to "voice-2")
            )
        )

        assertEquals(3, result.audioChunks.size)
        assertArrayEquals(byteArrayOf(10), result.audioChunks[0])
        assertArrayEquals(byteArrayOf(20), result.audioChunks[1])
        assertArrayEquals(byteArrayOf(30), result.audioChunks[2])
        assertEquals(34, result.totalCharacters)
    }

    // --- Retry on 429 tests ---

    @Test
    fun `retries on 429 and succeeds on second attempt`() = runTest {
        var callCount = 0
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions)
        } answers {
            callCount++
            if (callCount == 1) throw InworldRateLimitException("Rate limited")
            InworldSpeechResponse(sampleAudio, 11)
        }

        val result = provider.generate(request("Hello world"))

        assertEquals(1, result.audioChunks.size)
        assertEquals(11, result.totalCharacters)
        assertEquals(2, callCount)
    }

    @Test
    fun `throws InworldRateLimitException after exhausting retries`() {
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions)
        } throws InworldRateLimitException("Rate limited")

        val exception = assertThrows<InworldRateLimitException> { runBlocking { provider.generate(request("Hello world")) } }
        assertEquals("Rate limited", exception.message)

        verify(exactly = 3) {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions)
        }
    }

    // --- Retry on transient 5xx tests ---

    @Test
    fun `retries on transient 5xx and succeeds on second attempt`() = runTest {
        var callCount = 0
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions)
        } answers {
            callCount++
            if (callCount == 1) throw InworldTransientException("Inworld API error (HTTP 503): upstream connect error")
            InworldSpeechResponse(sampleAudio, 11)
        }

        val result = provider.generate(request("Hello world"))

        assertEquals(1, result.audioChunks.size)
        assertEquals(11, result.totalCharacters)
        assertEquals(2, callCount)
    }

    @Test
    fun `throws InworldTransientException after exhausting retries`() {
        every {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions)
        } throws InworldTransientException("Inworld API error (HTTP 503): upstream connect error")

        val exception = assertThrows<InworldTransientException> { runBlocking { provider.generate(request("Hello world")) } }
        assertEquals("Inworld API error (HTTP 503): upstream connect error", exception.message)

        verify(exactly = 3) {
            apiClient.synthesizeSpeech("u1", "voice-1", "Hello world", "inworld-tts-2", defaultOptions)
        }
    }

    // --- Post-processing integration tests ---

    @Test
    fun `monologue post-processes script before sending to API`() = runTest {
        val calls = recordCalls()

        provider.generate(
            request(
                "**Breaking** news! [excitedly] Check [this](https://example.com).",
                ttsSettings = mapOf("model" to "inworld-tts-1.5-max")
            )
        )

        assertEquals(setOf("*Breaking* news! Check this."), calls.keys)
    }

    @Test
    fun `dialogue post-processes each turn before sending to API`() = runTest {
        val calls = recordCalls()

        provider.generate(
            request(
                "<host>**Welcome** to the show! [cheerfully] Hello.</host><cohost>[sigh] Thanks for having me.</cohost>",
                ttsVoices = mapOf("host" to "voice-1", "cohost" to "voice-2"),
                ttsSettings = mapOf("model" to "inworld-tts-1.5-max")
            )
        )

        assertEquals(
            setOf("*Welcome* to the show! Hello.", "[sigh] Thanks for having me."),
            calls.keys
        )
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
    fun `all styles spell the sound name as clear throat`() {
        for (style in PodcastStyle.entries) {
            val guidelines = provider.scriptGuidelines(style)
            assertTrue(guidelines.contains("[clear throat]"), "Missing [clear throat] for $style")
            assertFalse(guidelines.contains("[clear_throat]"), "Underscore spelling still present for $style")
        }
    }

    @Test
    fun `all styles describe steering instructions`() {
        for (style in PodcastStyle.entries) {
            val guidelines = provider.scriptGuidelines(style)
            assertTrue(guidelines.contains("[warm and conversational with an easy pace]"), "Missing steering example for $style")
            assertTrue(guidelines.contains("[reset]"), "Missing reset instruction for $style")
        }
    }

    @Test
    fun `all styles describe acronym handling`() {
        for (style in PodcastStyle.entries) {
            val guidelines = provider.scriptGuidelines(style)
            assertTrue(guidelines.contains("Acronyms:"), "Missing acronym rule for $style")
        }
    }

    @Test
    fun `all styles allow SSML break tags with their limits`() {
        for (style in PodcastStyle.entries) {
            val guidelines = provider.scriptGuidelines(style)
            assertTrue(guidelines.contains("<break time=\"1s\" />"), "Missing break tag for $style")
            assertTrue(guidelines.contains("20 per request"), "Missing break tag count limit for $style")
            assertTrue(guidelines.contains("10 seconds"), "Missing break tag duration limit for $style")
        }
    }

    @Test
    fun `all styles describe CAPS emphasis`() {
        for (style in PodcastStyle.entries) {
            val guidelines = provider.scriptGuidelines(style)
            assertTrue(guidelines.contains("CAPS"), "Missing CAPS emphasis for $style")
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
