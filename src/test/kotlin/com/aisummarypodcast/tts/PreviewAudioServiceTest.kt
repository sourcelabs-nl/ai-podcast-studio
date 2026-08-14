package com.aisummarypodcast.tts

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelType
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.TtsProviderType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Collections

class PreviewAudioServiceTest {

    private val provider = mockk<TtsProvider>()
    private val ttsProviderFactory = mockk<TtsProviderFactory>()
    private val previewAudioStore = mockk<PreviewAudioStore>()
    private val audioConcatenator = mockk<AudioConcatenator>()
    private val appProperties = mockk<AppProperties>()

    private val service = PreviewAudioService(ttsProviderFactory, previewAudioStore, audioConcatenator, appProperties)

    private val models = mapOf(
        "inworld" to mapOf(
            "inworld-tts-2" to ModelCost(type = ModelType.TTS, costPerMillionChars = 5.0)
        )
    )

    private fun podcast(
        style: PodcastStyle = PodcastStyle.NEWS_BRIEFING,
        ttsSettings: Map<String, String>? = mapOf("model" to "inworld-tts-2"),
        ttsVoices: Map<String, String>? = mapOf("default" to "voice-1")
    ) = Podcast(
        id = "p1",
        userId = "u1",
        name = "Test",
        topic = "tech",
        ttsProvider = TtsProviderType.INWORLD,
        style = style,
        ttsVoices = ttsVoices,
        ttsSettings = ttsSettings
    )

    private fun stubProvider(maxChunkSize: Int = 1900) {
        every { ttsProviderFactory.resolve(any()) } returns provider
        every { provider.maxChunkSize } returns maxChunkSize
    }

    @Test
    fun `estimate bills every character of a monologue script`() {
        every { appProperties.models } returns models
        val script = "a".repeat(2_000_000)

        val estimate = service.estimate(podcast(), script)

        assertEquals(2_000_000, estimate.characters)
        // 2M characters at $5.00 per million is $10.00, which is 1000 cents.
        assertEquals(1000, estimate.costCents)
    }

    @Test
    fun `estimate excludes speaker tags from a dialogue script`() {
        every { appProperties.models } returns models
        val script = "<host>Hello there.</host>\n<cohost>Hi back.</cohost>"

        val estimate = service.estimate(podcast(style = PodcastStyle.DIALOGUE), script)

        assertEquals("Hello there.".length + "Hi back.".length, estimate.characters)
    }

    @Test
    fun `estimate reports an unknown cost when the provider has no configured rate`() {
        every { appProperties.models } returns emptyMap()

        val estimate = service.estimate(podcast(), "Some script text.")

        assertEquals("Some script text.".length, estimate.characters)
        assertNull(estimate.costCents)
    }

    @Test
    fun `estimate falls back to the provider's first configured rate when no model is set`() {
        every { appProperties.models } returns models

        val estimate = service.estimate(podcast(ttsSettings = null), "a".repeat(1_000_000))

        assertEquals(500, estimate.costCents)
    }

    @Test
    fun `sample synthesises only the opening slice through the normal provider path`() = runTest {
        stubProvider(maxChunkSize = 100)
        val request = slot<TtsRequest>()
        coEvery { provider.generate(capture(request)) } returns TtsResult(
            audioChunks = listOf(byteArrayOf(9)),
            totalCharacters = 10,
            requiresConcatenation = false,
            model = "inworld-tts-2"
        )
        val script = (1..40).joinToString("\n\n") { "Paragraph $it of the full script." }

        val audio = service.synthesizeSample(podcast(), script)

        assertArrayEquals(byteArrayOf(9), audio)
        assertTrue(request.captured.script.length <= 100)
        assertTrue(script.startsWith(request.captured.script.take(20)))
    }

    @Test
    fun `sample rejects a script with no speakable text`() = runTest {
        stubProvider()

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { service.synthesizeSample(podcast(), "   ") }
        }
    }

    @Test
    fun `full audio reports progress per chunk and stores the result`() = runTest {
        stubProvider()
        val request = slot<TtsRequest>()
        coEvery { provider.generate(capture(request)) } answers {
            request.captured.progress?.onChunkCompleted(1, 2)
            request.captured.progress?.onChunkCompleted(2, 2)
            TtsResult(listOf(byteArrayOf(1), byteArrayOf(2)), 40, requiresConcatenation = true, model = "inworld-tts-2")
        }
        every { previewAudioStore.write("p1", any(), true) } returns "audio-id"
        val reported = Collections.synchronizedList(mutableListOf<Pair<Int, Int>>())

        val audioId = service.generateFullAudio(podcast(), "The full script.") { completed, total ->
            reported.add(completed to total)
        }

        assertEquals("audio-id", audioId)
        assertEquals(listOf(1 to 2, 2 to 2), reported)
    }

    @Test
    fun `full audio rejects a blank script`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { service.generateFullAudio(podcast(), " ") { _, _ -> } }
        }
    }

    @Test
    fun `the sweep is delegated to the store`() {
        every { previewAudioStore.sweepExpired() } returns 3

        assertEquals(3, service.sweepExpiredAudio())
    }
}
