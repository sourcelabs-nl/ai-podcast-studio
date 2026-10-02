package com.aisummarypodcast.podcast

import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.TtsProviderType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PodcastMappersTest {

    private val create = CreatePodcastRequest(name = "My Podcast", topic = "tech")
    private val update = UpdatePodcastRequest(name = "My Podcast", topic = "tech")
    private val existing = Podcast(
        id = "p1", userId = "u1", name = "Old", topic = "old", language = "nl",
        ttsProvider = TtsProviderType.ELEVENLABS, style = PodcastStyle.CASUAL,
        cron = "0 0 8 * * *", timezone = "Europe/Amsterdam", relevanceThreshold = 7
    )

    @Test
    fun `create applies the defaults for absent fields`() {
        val podcast = create.toPodcast("u1")

        assertEquals("u1", podcast.userId)
        assertEquals("en", podcast.language)
        assertEquals(TtsProviderType.OPENAI, podcast.ttsProvider)
        assertEquals(PodcastStyle.NEWS_BRIEFING, podcast.style)
        assertEquals(Podcast.DEFAULT_CRON, podcast.cron)
        assertEquals(Podcast.DEFAULT_TIMEZONE, podcast.timezone)
        assertEquals(Podcast.DEFAULT_RELEVANCE_THRESHOLD, podcast.relevanceThreshold)
        assertEquals(Podcast.DEFAULT_RAPID_FIRE_WEIGHT_THRESHOLD, podcast.rapidFireWeightThreshold)
        assertEquals(false, podcast.requireReview)
        assertEquals(false, podcast.deepDiveEnabled)
        assertNull(podcast.subtopics)
    }

    @Test
    fun `create rejects an unsupported language`() {
        val error = assertThrows<InvalidPodcastException> { create.copy(language = "xx").toPodcast("u1") }
        assertEquals("Unsupported language: xx", error.message)
    }

    @Test
    fun `create rejects an unsupported TTS provider`() {
        val error = assertThrows<InvalidPodcastException> { create.copy(ttsProvider = "nope").toPodcast("u1") }
        assertEquals(
            "Unsupported TTS provider: nope. Supported: ${TtsProviderType.entries.joinToString { it.value }}",
            error.message
        )
    }

    @Test
    fun `create rejects an unsupported style`() {
        val error = assertThrows<InvalidPodcastException> { create.copy(style = "nope").toPodcast("u1") }
        assertEquals("Unsupported style: nope. Supported: ${PodcastStyle.entries.joinToString { it.value }}", error.message)
    }

    @Test
    fun `update keeps stored values for absent fields`() {
        val merged = update.applyTo(existing)

        assertEquals("My Podcast", merged.name)
        assertEquals("nl", merged.language)
        assertEquals(TtsProviderType.ELEVENLABS, merged.ttsProvider)
        assertEquals("0 0 8 * * *", merged.cron)
        assertEquals("Europe/Amsterdam", merged.timezone)
        assertEquals(7, merged.relevanceThreshold)
    }

    @Test
    fun `update applies parsed values`() {
        val merged = update.copy(language = "en", ttsProvider = "openai", style = "news-briefing").applyTo(existing)

        assertEquals("en", merged.language)
        assertEquals(TtsProviderType.OPENAI, merged.ttsProvider)
        assertEquals(PodcastStyle.NEWS_BRIEFING, merged.style)
    }

    @Test
    fun `update rejects an unsupported language, provider and style`() {
        assertEquals("Unsupported language: xx", assertThrows<InvalidPodcastException> { update.copy(language = "xx").applyTo(existing) }.message)
        assertEquals(
            "Unsupported TTS provider: nope. Supported: ${TtsProviderType.entries.joinToString { it.value }}",
            assertThrows<InvalidPodcastException> { update.copy(ttsProvider = "nope").applyTo(existing) }.message
        )
        assertEquals(
            "Unsupported style: nope. Supported: ${PodcastStyle.entries.joinToString { it.value }}",
            assertThrows<InvalidPodcastException> { update.copy(style = "nope").applyTo(existing) }.message
        )
    }
}
