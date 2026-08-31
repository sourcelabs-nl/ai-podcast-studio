package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.SourceProperties
import com.aisummarypodcast.llm.LlmPipeline
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeArticle
import com.aisummarypodcast.store.EpisodeArticleRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastRepository
import com.aisummarypodcast.store.Post
import com.aisummarypodcast.store.PostArticleRepository
import com.aisummarypodcast.store.PostRepository
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceRepository
import com.aisummarypodcast.store.SourceType
import com.aisummarypodcast.source.SourceAggregator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastServiceTest {

    private val podcastRepository = mockk<PodcastRepository>()
    private val sourceRepository = mockk<SourceRepository>()
    private val articleRepository = mockk<ArticleRepository>()
    private val postRepository = mockk<PostRepository>()
    private val postArticleRepository = mockk<PostArticleRepository>()
    private val episodeArticleRepository = mockk<EpisodeArticleRepository>()
    private val episodeRepository = mockk<EpisodeRepository>()
    private val llmPipeline = mockk<LlmPipeline>()
    private val episodeService = mockk<EpisodeService>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val sourceAggregator = mockk<SourceAggregator>(relaxed = true)
    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key"),
        source = SourceProperties(maxArticleAgeDays = 7)
    )

    private val podcastService = PodcastService(
        podcastRepository, sourceRepository, articleRepository, postRepository,
        postArticleRepository, episodeArticleRepository, episodeRepository, appProperties, llmPipeline, episodeService,
        eventPublisher, sourceAggregator
    )

    private val podcast = Podcast(
        id = "p1", userId = "u1", name = "Test", topic = "tech",
        lastGeneratedAt = "2026-03-01T00:00:00Z"
    )

    private val source = Source(
        id = "s1", podcastId = "p1", type = SourceType.RSS,
        url = "https://example.com/feed", pollIntervalMinutes = 60
    )

    @Test
    fun `getUpcomingContent returns articles and unlinked posts since lastGeneratedAt`() {
        val article = Article(
            id = 1, sourceId = "s1", title = "Article 1", body = "body",
            url = "https://example.com/1", contentHash = "h1",
            publishedAt = "2026-03-02T00:00:00Z", relevanceScore = 7
        )
        val post = Post(
            id = 2, sourceId = "s1", title = "Post 1", body = "body",
            url = "https://example.com/2", contentHash = "h2",
            createdAt = "2026-03-02T00:00:00Z"
        )

        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { articleRepository.findUnprocessedSince(listOf("s1"), any()) } returns listOf(article)
        every { postRepository.findUnlinkedSince(listOf("s1"), "2026-03-01T00:00:00Z") } returns listOf(post)
        every { postArticleRepository.countByArticleIds(listOf(1L)) } returns 3L

        val result = podcastService.getUpcomingContent(podcast)

        assertEquals(1, result.articles.size)
        assertEquals(1, result.unlinkedPosts.size)
        assertEquals("Article 1", result.articles[0].title)
        assertEquals("Post 1", result.unlinkedPosts[0].title)
        assertEquals(4L, result.totalPostCount) // 3 linked + 1 unlinked
    }

    @Test
    fun `getUpcomingContent falls back to maxArticleAgeDays when lastGeneratedAt is null`() {
        val podcastNoGenerated = podcast.copy(lastGeneratedAt = null)

        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { articleRepository.findUnprocessedSince(listOf("s1"), any()) } returns emptyList()
        every { postRepository.findUnlinkedSince(eq(listOf("s1")), any()) } returns emptyList()

        val result = podcastService.getUpcomingContent(podcastNoGenerated)

        assertTrue(result.articles.isEmpty())
        assertTrue(result.unlinkedPosts.isEmpty())
        assertEquals(0L, result.totalPostCount)
    }

    @Test
    fun `getUpcomingContent returns empty when no sources`() {
        every { sourceRepository.findByPodcastId("p1") } returns emptyList()

        val result = podcastService.getUpcomingContent(podcast)

        assertTrue(result.articles.isEmpty())
        assertTrue(result.unlinkedPosts.isEmpty())
        assertTrue(result.sources.isEmpty())
    }

    // --- Resume point detection tests ---

    @Test
    fun `detectResumePoint returns POST_COMPOSE when script exists`() {
        val episode = Episode(id = 5L, podcastId = "p1", generatedAt = "now", scriptText = "Script text", status = EpisodeStatus.FAILED)

        val result = podcastService.detectResumePoint(episode)

        assertEquals(ResumePoint.POST_COMPOSE, result)
    }

    @Test
    fun `detectResumePoint returns COMPOSE when episode_articles exist but no script`() {
        val episode = Episode(id = 5L, podcastId = "p1", generatedAt = "now", scriptText = "", status = EpisodeStatus.FAILED)
        every { episodeArticleRepository.findByEpisodeId(5L) } returns listOf(
            EpisodeArticle(id = 1L, episodeId = 5L, articleId = 10L, topic = "AI Safety", topicOrder = 0)
        )

        val result = podcastService.detectResumePoint(episode)

        assertEquals(ResumePoint.COMPOSE, result)
    }

    @Test
    fun `detectResumePoint returns FULL_PIPELINE when no intermediate state`() {
        val episode = Episode(id = 5L, podcastId = "p1", generatedAt = "now", scriptText = "", status = EpisodeStatus.FAILED)
        every { episodeArticleRepository.findByEpisodeId(5L) } returns emptyList()

        val result = podcastService.detectResumePoint(episode)

        assertEquals(ResumePoint.FULL_PIPELINE, result)
    }

    @Test
    fun `retryEpisode publishes episode retrying SSE event`() {
        val episode = Episode(id = 5L, podcastId = "p1", generatedAt = "now", scriptText = "Script", status = EpisodeStatus.FAILED)
        every { episodeService.resetForRetry(episode) } returns episode.copy(status = EpisodeStatus.GENERATING, errorMessage = null)

        val resumePoint = podcastService.retryEpisode(episode, podcast)

        assertEquals(ResumePoint.POST_COMPOSE, resumePoint)
        verify { eventPublisher.publishEvent(match<PodcastEvent> { it.event == "episode.retrying" && it.data["resumePoint"] == "POST_COMPOSE" }) }
    }

    // --- Regeneration guard --------------------------------------------------------------------

    private val sourceEpisode = Episode(
        id = 191, podcastId = "p1", scriptText = "script",
        status = EpisodeStatus.FAILED, generatedAt = "2026-08-31T13:00:00Z"
    )

    @Test
    fun `regenerate rejects an episode with no linked articles and creates no episode`() {
        every { episodeService.findLinkedArticlesAndTopics(191) } returns
            LinkedArticlesResult(emptyList(), emptyList(), emptyMap())

        val error = assertThrows(EpisodeNotRegenerableException::class.java) {
            podcastService.regenerateEpisodeAsync(sourceEpisode, podcast)
        }

        assertTrue(error.message!!.contains("no linked articles"))
        verify(exactly = 0) { episodeService.createGeneratingEpisode(any(), any()) }
    }

    @Test
    fun `regenerate proceeds for an episode that has linked articles`() {
        val article = Article(
            id = 1, sourceId = "s1", title = "Article 1", body = "body",
            url = "https://example.com/1", contentHash = "h1", relevanceScore = 7
        )
        val generating = Episode(
            id = 192, podcastId = "p1", scriptText = "",
            status = EpisodeStatus.GENERATING, generatedAt = "2026-08-31T19:00:00Z"
        )
        every { episodeService.findLinkedArticlesAndTopics(191) } returns
            LinkedArticlesResult(listOf(article), listOf("Topic"), mapOf(1L to "Topic"))
        every { episodeService.createGeneratingEpisode(podcast, false) } returns generating
        // Stubbed so the background recompose this launches completes quietly.
        coEvery { llmPipeline.recompose(any(), any(), any(), any()) } returns mockk(relaxed = true)
        coEvery {
            episodeService.createEpisodeFromPipelineResult(any(), any(), any(), any(), any())
        } returns generating

        val result = podcastService.regenerateEpisodeAsync(sourceEpisode, podcast)

        assertEquals(192, result.id)
        verify { episodeService.createGeneratingEpisode(podcast, false) }
    }
}
