package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodeProperties
import com.aisummarypodcast.config.DedupProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.SourceProperties
import com.aisummarypodcast.podcast.EpisodeWindow
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeArticle
import com.aisummarypodcast.store.EpisodeArticleRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.TtsProviderType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class ArticleEligibilityServiceTest {

    private val articleRepository = mockk<ArticleRepository>()
    private val episodeRepository = mockk<EpisodeRepository>()
    private val episodeArticleRepository = mockk<EpisodeArticleRepository>()

    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key"),
        source = SourceProperties(),
        episode = EpisodeProperties(recapLookbackEpisodes = 7)
    )

    private val service = ArticleEligibilityService(
        articleRepository, episodeRepository, episodeArticleRepository, appProperties
    )

    private val podcast = Podcast(
        id = "pod-1",
        userId = "user-1",
        name = "Test Podcast",
        topic = "AI",
        language = "en",
        style = PodcastStyle.NEWS_BRIEFING,
        ttsProvider = TtsProviderType.OPENAI,
        relevanceThreshold = 5
    )

    private fun article(id: Long, publishedAt: String? = "2026-03-18T10:00:00Z") = Article(
        id = id,
        sourceId = "src-1",
        title = "Article $id",
        body = "Body $id",
        url = "https://example.com/article-$id",
        contentHash = "hash-$id",
        relevanceScore = 8,
        isProcessed = false,
        publishedAt = publishedAt
    )

    private val window = EpisodeWindow(
        start = Instant.parse("2026-03-17T14:00:00Z"),
        end = Instant.parse("2026-03-18T14:00:00Z")
    )

    @Test
    fun `findEligibleArticles keeps every candidate inside the window`() {
        val candidates = listOf(article(1), article(2))
        every { articleRepository.findRelevantUnprocessedBySourceIds(any(), any()) } returns candidates

        val result = service.findEligibleArticles(listOf("src-1"), podcast, window)

        assertEquals(2, result.size)
    }

    @Test
    fun `findEligibleArticles drops articles published before the window`() {
        val before = article(1, publishedAt = "2026-03-16T10:00:00Z")
        val inside = article(2, publishedAt = "2026-03-18T10:00:00Z")
        every { articleRepository.findRelevantUnprocessedBySourceIds(any(), any()) } returns listOf(before, inside)

        val result = service.findEligibleArticles(listOf("src-1"), podcast, window)

        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `findEligibleArticles drops articles published after the window`() {
        val inside = article(1, publishedAt = "2026-03-18T10:00:00Z")
        val after = article(2, publishedAt = "2026-03-18T15:00:00Z")
        every { articleRepository.findRelevantUnprocessedBySourceIds(any(), any()) } returns listOf(inside, after)

        val result = service.findEligibleArticles(listOf("src-1"), podcast, window)

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `findEligibleArticles treats the window as half-open`() {
        val atStart = article(1, publishedAt = "2026-03-17T14:00:00Z")
        val atEnd = article(2, publishedAt = "2026-03-18T14:00:00Z")
        every { articleRepository.findRelevantUnprocessedBySourceIds(any(), any()) } returns listOf(atStart, atEnd)

        val result = service.findEligibleArticles(listOf("src-1"), podcast, window)

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `findEligibleArticles compares instants rather than strings`() {
        // Same instant as the window start, written with an offset instead of Z. A lexicographic
        // comparison would place it before the window and drop it.
        val offsetForm = article(1, publishedAt = "2026-03-17T15:00:00+01:00")
        every { articleRepository.findRelevantUnprocessedBySourceIds(any(), any()) } returns listOf(offsetForm)

        val result = service.findEligibleArticles(listOf("src-1"), podcast, window)

        assertEquals(1, result.size)
    }

    @Test
    fun `findEligibleArticles keeps articles without publishedAt`() {
        val articleNoDate = article(1, publishedAt = null)
        every { articleRepository.findRelevantUnprocessedBySourceIds(any(), any()) } returns listOf(articleNoDate)

        val result = service.findEligibleArticles(listOf("src-1"), podcast, window)

        assertEquals(1, result.size)
    }

    @Test
    fun `findEligibleArticles keeps an article whose publishedAt cannot be parsed`() {
        val unparseable = article(1, publishedAt = "not-a-date")
        every { articleRepository.findRelevantUnprocessedBySourceIds(any(), any()) } returns listOf(unparseable)

        val result = service.findEligibleArticles(listOf("src-1"), podcast, window)

        assertEquals(1, result.size)
    }

    @Test
    fun `canResetArticle returns true when article not linked to published episode`() {
        every { episodeArticleRepository.isArticleLinkedToPublishedEpisode(1L) } returns false

        assertTrue(service.canResetArticle(1L))
    }

    @Test
    fun `canResetArticle returns false when article linked to published episode`() {
        every { episodeArticleRepository.isArticleLinkedToPublishedEpisode(1L) } returns true

        assertFalse(service.canResetArticle(1L))
    }

    @Test
    fun `findHistoricalArticles returns articles from recent generated episodes`() {
        val episode1 = Episode(id = 10, podcastId = "pod-1", generatedAt = "2026-03-18T10:00:00Z", scriptText = "test", status = EpisodeStatus.GENERATED)
        val episode2 = Episode(id = 9, podcastId = "pod-1", generatedAt = "2026-03-17T10:00:00Z", scriptText = "test", status = EpisodeStatus.GENERATED)
        every { episodeRepository.findRecentGeneratedByPodcastId("pod-1", 7) } returns listOf(episode1, episode2)
        every { episodeArticleRepository.findByEpisodeId(10) } returns listOf(EpisodeArticle(episodeId = 10, articleId = 1))
        every { episodeArticleRepository.findByEpisodeId(9) } returns listOf(EpisodeArticle(episodeId = 9, articleId = 2), EpisodeArticle(episodeId = 9, articleId = 1))
        every { articleRepository.findById(1L) } returns Optional.of(article(1))
        every { articleRepository.findById(2L) } returns Optional.of(article(2))

        val result = service.findHistoricalArticles(podcast)

        assertEquals(2, result.size)
    }

    @Test
    fun `findHistoricalArticles deduplicates articles shared across episodes`() {
        val episode1 = Episode(id = 10, podcastId = "pod-1", generatedAt = "2026-03-18T10:00:00Z", scriptText = "test", status = EpisodeStatus.GENERATED)
        val episode2 = Episode(id = 9, podcastId = "pod-1", generatedAt = "2026-03-17T10:00:00Z", scriptText = "test", status = EpisodeStatus.GENERATED)
        every { episodeRepository.findRecentGeneratedByPodcastId("pod-1", 7) } returns listOf(episode1, episode2)
        every { episodeArticleRepository.findByEpisodeId(10) } returns listOf(EpisodeArticle(episodeId = 10, articleId = 1))
        every { episodeArticleRepository.findByEpisodeId(9) } returns listOf(EpisodeArticle(episodeId = 9, articleId = 1))
        every { articleRepository.findById(1L) } returns Optional.of(article(1))

        val result = service.findHistoricalArticles(podcast)

        assertEquals(1, result.size)
    }

    @Test
    fun `findHistoricalArticles caps historical articles to the configured maximum`() {
        val cappedProperties = appProperties.copy(llm = LlmProperties(dedup = DedupProperties(maxHistoricalArticles = 2)))
        val cappedService = ArticleEligibilityService(
            articleRepository, episodeRepository, episodeArticleRepository, cappedProperties
        )
        val episode = Episode(id = 10, podcastId = "pod-1", generatedAt = "2026-03-18T10:00:00Z", scriptText = "test", status = EpisodeStatus.GENERATED)
        every { episodeRepository.findRecentGeneratedByPodcastId("pod-1", 7) } returns listOf(episode)
        every { episodeArticleRepository.findByEpisodeId(10) } returns listOf(
            EpisodeArticle(episodeId = 10, articleId = 1),
            EpisodeArticle(episodeId = 10, articleId = 2),
            EpisodeArticle(episodeId = 10, articleId = 3)
        )
        every { articleRepository.findById(1L) } returns Optional.of(article(1))
        every { articleRepository.findById(2L) } returns Optional.of(article(2))
        every { articleRepository.findById(3L) } returns Optional.of(article(3))

        val result = cappedService.findHistoricalArticles(podcast)

        assertEquals(2, result.size)
        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun `findHistoricalArticles returns empty when no generated episodes`() {
        every { episodeRepository.findRecentGeneratedByPodcastId("pod-1", 7) } returns emptyList()

        val result = service.findHistoricalArticles(podcast)

        assertTrue(result.isEmpty())
    }
}
