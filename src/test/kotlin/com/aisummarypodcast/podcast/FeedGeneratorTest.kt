package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeArticleRepository
import com.aisummarypodcast.store.EpisodePublicationRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.FeedArticle
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.User
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class FeedGeneratorTest {

    private val episodeRepository = mockk<EpisodeRepository>()
    private val podcastImageService = mockk<PodcastImageService>()
    private val episodeSourcesGenerator = mockk<EpisodeSourcesGenerator>()
    private val publicationRepository = mockk<EpisodePublicationRepository>()
    private val episodeArticleRepository = mockk<EpisodeArticleRepository>().also {
        every { it.findArticlesByEpisodeIds(any()) } returns emptyMap()
    }
    private val appProperties = mockk<AppProperties>().also {
        every { it.feed } returns FeedProperties(
            baseUrl = "http://localhost:8085",
            title = "AI Summary Podcast",
            description = "Test feed",
            ownerName = "TestOwner",
            ownerEmail = "test@example.com",
            author = "TestAuthor"
        )
    }
    private val feedGenerator = FeedGenerator(
        episodeRepository, appProperties, podcastImageService,
        episodeSourcesGenerator, publicationRepository, episodeArticleRepository
    )

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Tech Daily", topic = "tech")
    private val user = User(id = "u1", name = "Test User")

    @Test
    fun `feed title is podcast name only`() {
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns emptyList()
        every { podcastImageService.get("p1") } returns null

        val xml = feedGenerator.generate(podcast, user)
        assertTrue(xml.contains("<title>Tech Daily</title>"), "Expected podcast name as feed title")
        assertFalse(xml.contains("AI Summary Podcast"), "Expected no app title in feed")
    }

    @Test
    fun `feed includes language element matching podcast language`() {
        val nlPodcast = podcast.copy(language = "nl")
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns emptyList()
        every { podcastImageService.get("p1") } returns null

        val xml = feedGenerator.generate(nlPodcast, user)
        assertTrue(xml.contains("<language>nl</language>"))
    }

    @Test
    fun `feed includes image element when publicUrl and image are present`() {
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns emptyList()
        every { podcastImageService.get("p1") } returns Path.of("/data/p1/podcast-image.jpg")

        val xml = feedGenerator.generate(podcast, user, publicUrl = "https://podcast.example.com/shows/tech/")
        assertTrue(xml.contains("https://podcast.example.com/shows/tech/podcast-image.jpg"), "Expected image URL in feed")
    }

    @Test
    fun `feed includes image with local url when no publicUrl`() {
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns emptyList()
        every { podcastImageService.get("p1") } returns Path.of("/data/p1/podcast-image.jpg")

        val xml = feedGenerator.generate(podcast, user)
        assertTrue(xml.contains("http://localhost:8085/data/p1/podcast-image.jpg"), "Expected local image URL in feed")
    }

    @Test
    fun `feed omits image element when no image exists`() {
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns emptyList()
        every { podcastImageService.get("p1") } returns null

        val xml = feedGenerator.generate(podcast, user, publicUrl = "https://podcast.example.com/shows/tech/")
        assertFalse(xml.contains("<image>"), "Expected no image element when podcast has no image")
    }

    @Test
    fun `episode plain description has show notes without sources link`() {
        val episode = Episode(
            id = 1L, podcastId = "p1", generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Full script", status = EpisodeStatus.GENERATED,
            audioFilePath = "/data/p1/episodes/briefing-20250101-000000.mp3", durationSeconds = 120,
            showNotes = "Today's recap summary."
        )
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns listOf(episode)
        every { podcastImageService.get("p1") } returns null
        every { episodeSourcesGenerator.deriveSlug(episode) } returns "briefing-20250101-000000"
        every { episodeArticleRepository.findArticlesByEpisodeIds(listOf(1L)) } returns emptyMap()

        val xml = feedGenerator.generate(podcast, user, publicUrl = "https://podcast.example.com/shows/tech/")
        assertTrue(xml.contains("Today's recap summary."), "Expected recap in description")
        assertFalse(xml.contains("Sources: https://"), "Sources link should not be in plain description")
    }

    @Test
    fun `feed uses show notes when available`() {
        val episode = Episode(
            id = 1L, podcastId = "p1", generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Full script text here", status = EpisodeStatus.GENERATED,
            audioFilePath = "/data/p1/episodes/briefing-20250101-000000.mp3", durationSeconds = 120,
            showNotes = "Recap summary."
        )
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns listOf(episode)
        every { podcastImageService.get("p1") } returns null
        every { episodeSourcesGenerator.deriveSlug(episode) } returns "briefing-20250101-000000"
        every { episodeArticleRepository.findArticlesByEpisodeIds(listOf(1L)) } returns emptyMap()

        val xml = feedGenerator.generate(podcast, user)
        assertTrue(xml.contains("Recap summary."), "Expected show notes in feed description")
        assertFalse(xml.contains("Full script text here"), "Expected script text NOT in feed when show notes present")
    }

    @Test
    fun `feed excludes pending review episodes`() {
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns emptyList()
        every { podcastImageService.get("p1") } returns null

        val xml = feedGenerator.generate(podcast, user)
        assertFalse(xml.contains("<item>"), "Expected no items in feed")
    }

    @Test
    fun `feed includes itunes type and explicit at channel level`() {
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns emptyList()
        every { podcastImageService.get("p1") } returns null

        val xml = feedGenerator.generate(podcast, user)
        assertTrue(xml.contains("<itunes:type>episodic</itunes:type>"), "Expected itunes:type")
        assertTrue(xml.contains("<itunes:explicit>no</itunes:explicit>"), "Expected itunes:explicit")
    }

    @Test
    fun `feed includes itunes category`() {
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns emptyList()
        every { podcastImageService.get("p1") } returns null

        val xml = feedGenerator.generate(podcast, user)
        assertTrue(xml.contains("<itunes:category"), "Expected itunes:category")
        assertTrue(xml.contains("Technology"), "Expected Technology category")
    }

    @Test
    fun `feed includes itunes image when podcast image exists`() {
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns emptyList()
        every { podcastImageService.get("p1") } returns Path.of("/data/p1/podcast-image.jpg")

        val xml = feedGenerator.generate(podcast, user)
        assertTrue(xml.contains("itunes:image"), "Expected itunes:image element")
        assertTrue(xml.contains("podcast-image.jpg"), "Expected image filename in itunes:image")
    }

    @Test
    fun `feed includes atom self link`() {
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns emptyList()
        every { podcastImageService.get("p1") } returns null

        val xml = feedGenerator.generate(podcast, user)
        assertTrue(xml.contains("atom:link"), "Expected atom:link element")
        assertTrue(xml.contains("rel=\"self\""), "Expected rel=self")
        assertTrue(xml.contains("feed.xml"), "Expected feed.xml in self link")
    }

    @Test
    fun `feed includes lastBuildDate`() {
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns emptyList()
        every { podcastImageService.get("p1") } returns null

        val xml = feedGenerator.generate(podcast, user)
        assertTrue(xml.contains("<lastBuildDate>"), "Expected lastBuildDate element")
    }

    @Test
    fun `episode includes itunes duration and episodeType`() {
        val episode = Episode(
            id = 1L, podcastId = "p1", generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Script", status = EpisodeStatus.GENERATED,
            audioFilePath = "/data/p1/episodes/briefing-20250101-000000.mp3", durationSeconds = 3661
        )
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns listOf(episode)
        every { podcastImageService.get("p1") } returns null
        every { episodeSourcesGenerator.deriveSlug(episode) } returns "briefing-20250101-000000"
        every { episodeArticleRepository.findArticlesByEpisodeIds(listOf(1L)) } returns emptyMap()

        val xml = feedGenerator.generate(podcast, user)
        assertTrue(xml.contains("<itunes:episodeType>full</itunes:episodeType>"), "Expected itunes:episodeType")
        assertTrue(xml.contains("<itunes:duration>"), "Expected itunes:duration")
    }

    @Test
    fun `episode link points to sources html not audio`() {
        val episode = Episode(
            id = 1L, podcastId = "p1", generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Script", status = EpisodeStatus.GENERATED,
            audioFilePath = "/data/p1/episodes/briefing-20250101-000000.mp3", durationSeconds = 120
        )
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns listOf(episode)
        every { podcastImageService.get("p1") } returns null
        every { episodeSourcesGenerator.deriveSlug(episode) } returns "briefing-20250101-000000"
        every { episodeArticleRepository.findArticlesByEpisodeIds(listOf(1L)) } returns emptyMap()

        val xml = feedGenerator.generate(podcast, user)
        // The <link> should point to the sources HTML page
        assertTrue(xml.contains("briefing-20250101-000000-sources.html"), "Expected sources HTML link")
    }

    @Test
    fun `episode includes content encoded with html show notes and sources link`() {
        val episode = Episode(
            id = 1L, podcastId = "p1", generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Script", status = EpisodeStatus.GENERATED,
            audioFilePath = "/data/p1/episodes/briefing-20250101-000000.mp3", durationSeconds = 120,
            showNotes = "Great episode about AI."
        )
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns listOf(episode)
        every { podcastImageService.get("p1") } returns null
        every { episodeSourcesGenerator.deriveSlug(episode) } returns "briefing-20250101-000000"
        every { episodeArticleRepository.findArticlesByEpisodeIds(listOf(1L)) } returns mapOf(
            1L to listOf(
                FeedArticle("OpenAI launches GPT-5", "https://example.com/gpt5"),
                FeedArticle("Anthropic updates Claude", "https://example.com/claude")
            )
        )

        val xml = feedGenerator.generate(podcast, user)
        assertTrue(xml.contains("content:encoded"), "Expected content:encoded element")
        assertTrue(xml.contains("Great episode about AI."), "Expected show notes in content:encoded")
        assertFalse(xml.contains("href=\"https://example.com/gpt5\""), "Expected no article links in content:encoded")
        assertFalse(xml.contains("OpenAI launches GPT-5"), "Expected no article titles in content:encoded")
        assertTrue(xml.contains("view all sources and show notes"), "Expected sources link text")
    }

    @Test
    fun `content encoded lists topic names without article titles when topics present`() {
        val episode = Episode(
            id = 1L, podcastId = "p1", generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Script", status = EpisodeStatus.GENERATED,
            audioFilePath = "/data/p1/episodes/briefing-20250101-000000.mp3", durationSeconds = 120,
            showNotes = "AI news roundup."
        )
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns listOf(episode)
        every { podcastImageService.get("p1") } returns null
        every { episodeSourcesGenerator.deriveSlug(episode) } returns "briefing-20250101-000000"
        every { episodeArticleRepository.findArticlesByEpisodeIds(listOf(1L)) } returns mapOf(
            1L to listOf(
                FeedArticle("GPT-5 launched", "https://example.com/gpt5", topic = "GPT-5 release", topicOrder = 1),
                FeedArticle("GPT-5 benchmarks", "https://example.com/gpt5-bench", topic = "GPT-5 release", topicOrder = 1),
                FeedArticle("Claude update", "https://example.com/claude", topic = "Anthropic Claude", topicOrder = 2),
                FeedArticle("Claude pricing", "https://example.com/claude-price", topic = "Anthropic Claude", topicOrder = 2),
                FeedArticle("New MCP tools", "https://example.com/mcp", topic = "MCP ecosystem", topicOrder = 3)
            )
        )

        val xml = feedGenerator.generate(podcast, user)
        // Only topic names should appear, no article titles or links
        assertTrue(xml.contains("Topics Covered"), "Expected topics header")
        assertTrue(xml.contains("GPT-5 release"), "Expected GPT-5 topic name")
        assertTrue(xml.contains("Anthropic Claude"), "Expected Claude topic name")
        assertTrue(xml.contains("MCP ecosystem"), "Expected MCP topic name")
        assertFalse(xml.contains("GPT-5 launched"), "Expected no article titles")
        assertFalse(xml.contains("Claude update"), "Expected no article titles")
        assertFalse(xml.contains("href=\"https://example.com/gpt5\""), "Expected no article links")
        assertTrue(xml.contains("view all sources and show notes"), "Expected sources link text")
        assertTrue(xml.contains("mailto:test@example.com"), "Expected mailto link in footer")
    }

    @Test
    fun `content encoded omits article list when no topics present`() {
        val episode = Episode(
            id = 1L, podcastId = "p1", generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Script", status = EpisodeStatus.GENERATED,
            audioFilePath = "/data/p1/episodes/briefing-20250101-000000.mp3", durationSeconds = 120,
            showNotes = "Legacy episode."
        )
        every { episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc("p1", EpisodeStatus.GENERATED) } returns listOf(episode)
        every { podcastImageService.get("p1") } returns null
        every { episodeSourcesGenerator.deriveSlug(episode) } returns "briefing-20250101-000000"
        every { episodeArticleRepository.findArticlesByEpisodeIds(listOf(1L)) } returns mapOf(
            1L to listOf(
                FeedArticle("Article one", "https://example.com/1"),
                FeedArticle("Article two", "https://example.com/2"),
                FeedArticle("Article three", "https://example.com/3")
            )
        )

        val xml = feedGenerator.generate(podcast, user)
        // Legacy episodes without topic data rely on the sources page link instead
        assertFalse(xml.contains("Article one"), "Expected no article titles in legacy mode")
        assertFalse(xml.contains("Topics Covered"), "Expected no topics header without topic data")
        assertTrue(xml.contains("view all sources and show notes"), "Expected sources link text")
    }
}
