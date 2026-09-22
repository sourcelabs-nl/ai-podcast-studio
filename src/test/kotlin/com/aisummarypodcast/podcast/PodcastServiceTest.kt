package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.SourceProperties
import com.aisummarypodcast.llm.ComposeContext
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
import io.mockk.coVerify
import io.mockk.every
import com.aisummarypodcast.eval.EpisodeScoringService
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.repository.findByIdOrNull
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelType
import com.aisummarypodcast.llm.PipelineStage
import com.aisummarypodcast.llm.ResolvedModel
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

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
    private val episodeWindowResolver = mockk<EpisodeWindowResolver> {
        every { windowOf(any()) } answers { window }
        every { episodeDateOf(any(), any()) } answers { episodeDate }
    }
    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key"),
        source = SourceProperties(maxArticleAgeDays = 7)
    )

    private val episodeScoringService = mockk<EpisodeScoringService>(relaxed = true)
    private val modelResolver = mockk<com.aisummarypodcast.llm.ModelResolver>(relaxed = true)

    private val podcastService = PodcastService(
        podcastRepository, sourceRepository, articleRepository, postRepository,
        postArticleRepository, episodeArticleRepository, episodeRepository, appProperties, llmPipeline, episodeService,
        eventPublisher, sourceAggregator, episodeWindowResolver, episodeScoringService, modelResolver
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
        every { postRepository.getPostCountsByArticleIds(listOf(1L)) } returns mapOf(1L to 4)

        val result = podcastService.getUpcomingContent(podcast)

        // Thread size travels with the content so the upcoming view can show it on the card.
        assertEquals(mapOf(1L to 4), result.postCounts)
        assertEquals(1, result.articles.size)
        assertEquals(1, result.unlinkedPosts.size)
        assertEquals("Article 1", result.articles[0].title)
        assertEquals("Post 1", result.unlinkedPosts[0].title)
        assertEquals(4L, result.totalPostCount) // 3 linked + 1 unlinked
    }

    @Test
    fun `getUpcomingContent reports what scoring the standing candidates cost`() {
        // Totalled the way an episode's score stage is, so the figure shown before generation is
        // comparable with the one shown after. Nothing is scored to answer the request.
        val filterModel = ResolvedModel(
            provider = "openrouter", model = "deepseek/deepseek-v4.1-flash",
            cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 0.15, outputCostPerMtok = 0.60),
            stage = PipelineStage.FILTER
        )
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns filterModel
        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { articleRepository.findUnprocessedSince(listOf("s1"), any()) } returns listOf(
            scoredArticle(1, input = 1000, output = 100, reportedUsd = 0.001),
            scoredArticle(2, input = 3000, output = 300, reportedUsd = 0.003)
        )
        every { postRepository.findUnlinkedSince(listOf("s1"), any()) } returns emptyList()
        every { postArticleRepository.countByArticleIds(any()) } returns 0L
        every { postRepository.getPostCountsByArticleIds(any()) } returns emptyMap()

        val spend = podcastService.getUpcomingContent(podcast).scoringSpend

        assertEquals("deepseek/deepseek-v4.1-flash", spend.model)
        assertEquals(2, spend.calls)
        assertEquals(4000, spend.inputTokens)
        assertEquals(400, spend.outputTokens)
        // 0.004 USD is 0.4 cents.
        assertEquals(0.4, spend.costCents, 1e-9)
        verify(exactly = 0) { articleRepository.save(any()) }
    }

    @Test
    fun `getUpcomingContent reports no scoring spend when nothing is standing`() {
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns ResolvedModel(
            provider = "openrouter", model = "deepseek/deepseek-v4.1-flash", cost = null,
            stage = PipelineStage.FILTER
        )
        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { articleRepository.findUnprocessedSince(listOf("s1"), any()) } returns emptyList()
        every { postRepository.findUnlinkedSince(listOf("s1"), any()) } returns emptyList()

        val spend = podcastService.getUpcomingContent(podcast).scoringSpend

        assertEquals(0, spend.calls)
        assertEquals(0.0, spend.costCents)
    }

    private fun scoredArticle(id: Long, input: Int, output: Int, reportedUsd: Double?) = Article(
        id = id, sourceId = "s1", title = "Article $id", body = "body",
        url = "https://example.com/$id", contentHash = "h$id",
        publishedAt = "2026-03-02T00:00:00Z", relevanceScore = 7,
        llmInputTokens = input, llmOutputTokens = output, llmReportedCostUsd = reportedUsd
    )

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
        // retryEpisode launches the retry itself on a background scope. This test is only about the
        // SSE event, but leaving the background path unstubbed made its own failure handler throw,
        // and an exception escaping Dispatchers.IO is collected by whichever runTest starts next,
        // failing an unrelated test in another class.
        every { episodeService.findLinkedArticlesAndTopics(5L) } returns mockk(relaxed = true)
        every { episodeService.failEpisode(any(), any(), any()) } returns episode

        val resumePoint = podcastService.retryEpisode(episode, podcast)

        assertEquals(ResumePoint.POST_COMPOSE, resumePoint)
        verify { eventPublisher.publishEvent(match<PodcastEvent> { it.event == "episode.retrying" && it.data["resumePoint"] == "POST_COMPOSE" }) }
    }

    // --- Regeneration guard --------------------------------------------------------------------

    private val window = EpisodeWindow(
        start = Instant.parse("2026-08-30T13:00:00Z"),
        end = Instant.parse("2026-08-31T13:00:00Z")
    )

    private val episodeDate = LocalDate.of(2026, 8, 31)

    private val sourceEpisode = Episode(
        id = 191, podcastId = "p1", scriptText = "script",
        status = EpisodeStatus.FAILED, generatedAt = "2026-08-31T13:00:00Z",
        windowStart = window.startIso, windowEnd = window.endIso
    )

    // --- Re-running a past window ---------------------------------------------------------------

    @Test
    fun `re-run creates a fresh episode covering the source episode's window`() {
        val discarded = sourceEpisode.copy(status = EpisodeStatus.DISCARDED)
        val rerun = Episode(
            id = 206, podcastId = "p1", scriptText = "",
            status = EpisodeStatus.GENERATING, generatedAt = "2026-09-01T09:00:00Z"
        )
        every { episodeWindowResolver.windowOf(discarded) } returns window
        // The re-run must not satisfy today's cron slot, hence updateLastGenerated = false.
        every { episodeService.createGeneratingEpisode(podcast, window, false) } returns rerun
        every { episodeService.updatePipelineStage(any(), any()) } returns Unit
        coEvery { llmPipeline.aggregateScoreAndFilter(podcast, window, 206L, any()) } returns null
        every { episodeService.deleteGeneratingEpisode(any()) } returns Unit

        val result = podcastService.rerunEpisodeAsync(discarded, podcast)

        assertEquals(206, result.id)
        verify { episodeService.createGeneratingEpisode(podcast, window, false) }
    }

    @Test
    fun `re-run is rejected for an episode that is neither failed nor discarded`() {
        val published = sourceEpisode.copy(status = EpisodeStatus.GENERATED)

        val error = assertThrows(EpisodeNotRerunnableException::class.java) {
            podcastService.rerunEpisodeAsync(published, podcast)
        }

        assertTrue(error.message!!.contains("discard this one first"))
        verify(exactly = 0) { episodeService.createGeneratingEpisode(any(), any(), any()) }
    }

    @Test
    fun `re-run is rejected for an episode that carries no window`() {
        val withoutWindow = sourceEpisode.copy(
            status = EpisodeStatus.DISCARDED, windowStart = null, windowEnd = null
        )
        every { episodeWindowResolver.windowOf(withoutWindow) } returns null

        val error = assertThrows(EpisodeNotRerunnableException::class.java) {
            podcastService.rerunEpisodeAsync(withoutWindow, podcast)
        }

        assertTrue(error.message!!.contains("no article window"))
        verify(exactly = 0) { episodeService.createGeneratingEpisode(any(), any(), any()) }
    }

    @Test
    fun `a retry reselects from the episode's own window`() {
        val failed = sourceEpisode.copy(scriptText = "", status = EpisodeStatus.FAILED)
        every { episodeWindowResolver.windowOf(failed) } returns window
        every { episodeArticleRepository.findByEpisodeId(191) } returns emptyList()
        every { episodeService.resetForRetry(failed) } returns failed
        every { episodeService.updatePipelineStage(any(), any()) } returns Unit
        every { episodeService.failEpisode(any(), any(), any()) } returns failed
        coEvery { llmPipeline.aggregateScoreAndFilter(podcast, window, 191L, any()) } returns null

        podcastService.retryEpisode(failed, podcast)

        // The retry runs in the background, so the call is awaited rather than asserted inline.
        coVerify(timeout = 2_000) { llmPipeline.aggregateScoreAndFilter(podcast, window, 191L, any()) }
    }

    @Test
    fun `regenerate rejects an episode with no linked articles and creates no episode`() {
        every { episodeService.findLinkedArticlesAndTopics(191) } returns
            LinkedArticlesResult(emptyList(), emptyList(), emptyMap(), emptyMap())

        val error = assertThrows(EpisodeNotRegenerableException::class.java) {
            podcastService.regenerateEpisodeAsync(sourceEpisode, podcast)
        }

        assertTrue(error.message!!.contains("no linked articles"))
        verify(exactly = 0) { episodeService.createGeneratingEpisode(any(), any(), any()) }
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
        every { episodeService.createGeneratingEpisode(podcast, window, false) } returns generating
        // Stubbed so the background recompose this launches completes quietly.
        coEvery { llmPipeline.recompose(any(), any(), any(), any()) } returns mockk(relaxed = true)
        coEvery {
            episodeService.createEpisodeFromPipelineResult(any(), any(), any(), any(), any())
        } returns generating

        val result = podcastService.regenerateEpisodeAsync(sourceEpisode, podcast)

        assertEquals(192, result.id)
        verify { episodeService.createGeneratingEpisode(podcast, window, false) }
    }

    @Test
    fun `an evaluation run recomposes with the cache bypassed`() {
        // Without the bypass the repetitions of one prompt variant replay the first script and the
        // ablation measures nothing.
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
        every { episodeService.createGeneratingEpisode(podcast, window, false) } returns generating
        coEvery { llmPipeline.recompose(any(), any(), any(), any()) } returns mockk(relaxed = true)
        coEvery {
            episodeService.createEpisodeFromPipelineResult(any(), any(), any(), any(), any())
        } returns generating

        podcastService.regenerateEpisodeAsync(sourceEpisode, podcast, bypassLlmCache = true)

        coVerify(timeout = 5000) {
            llmPipeline.recompose(
                listOf(article), podcast,
                ComposeContext(
                    topicLabels = listOf("Topic"), episodeDate = episodeDate, bypassLlmCache = true,
                    episodeId = 192L
                ),
                any()
            )
        }
    }

    @Test
    fun `regenerate recomposes with the source episode's follow-up annotations`() {
        val article = Article(
            id = 1, sourceId = "s1", title = "Article 1", body = "body",
            url = "https://example.com/1", contentHash = "h1", relevanceScore = 7
        )
        val generating = Episode(
            id = 192, podcastId = "p1", scriptText = "",
            status = EpisodeStatus.GENERATING, generatedAt = "2026-08-31T19:00:00Z"
        )
        val annotations = mapOf(1L to "Covered the launch in a recent episode")
        every { episodeService.findLinkedArticlesAndTopics(191) } returns
            LinkedArticlesResult(listOf(article), listOf("Topic"), mapOf(1L to "Topic"), annotations)
        every { episodeService.createGeneratingEpisode(podcast, window, false) } returns generating
        coEvery { llmPipeline.recompose(any(), any(), any(), any()) } returns mockk(relaxed = true)
        coEvery {
            episodeService.createEpisodeFromPipelineResult(any(), any(), any(), any(), any())
        } returns generating

        podcastService.regenerateEpisodeAsync(sourceEpisode, podcast)

        // Without this the composer has no continuity signal and falls back on the history tool,
        // which demoted a launch story on an unrelated keyword match.
        coVerify(timeout = 5000) {
            llmPipeline.recompose(
                listOf(article), podcast,
                ComposeContext(followUpAnnotations = annotations, topicLabels = listOf("Topic"), episodeDate = episodeDate, episodeId = 192L),
                any()
            )
        }
    }

    // --- Article posts (thread view) ------------------------------------------------------------

    private fun threadArticle(id: Long, sourceId: String) = Article(
        id = id, sourceId = sourceId, title = "Thread", body = "body",
        url = "https://x.com/a/status/1", contentHash = "h$id"
    )

    @Test
    fun `findArticlePosts returns the posts of an article on this podcast`() {
        val post = Post(
            id = 7, sourceId = "s1", title = "p", body = "post body",
            url = "https://x.com/a/status/7", publishedAt = "2026-08-31T10:00:00Z",
            contentHash = "ph", createdAt = "2026-08-31T10:00:00Z"
        )
        every { articleRepository.findByIdOrNull(1L) } returns threadArticle(1, "s1")
        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { postRepository.findPostsByArticleId(1L) } returns listOf(post)

        val result = podcastService.findArticlePosts(podcast, 1L)

        assertEquals(1, result!!.size)
        assertEquals("post body", result[0].body)
        assertEquals("2026-08-31T10:00:00Z", result[0].publishedAt)
    }

    @Test
    fun `findArticlePosts returns null for an article belonging to another podcast`() {
        // The article exists, but its source is not one of this podcast's — the controller 404s.
        every { articleRepository.findByIdOrNull(1L) } returns threadArticle(1, "other-source")
        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)

        assertNull(podcastService.findArticlePosts(podcast, 1L))
    }

    @Test
    fun `findArticlePosts returns null for an unknown article`() {
        every { articleRepository.findByIdOrNull(99L) } returns null

        assertNull(podcastService.findArticlePosts(podcast, 99L))
    }

    @Test
    fun `feedback recompose rewrites the same focus episode against its linked articles`() {
        val focusEpisode = Episode(
            id = 30L, podcastId = "p1", generatedAt = "2026-09-22T08:00:00Z", scriptText = "old",
            status = EpisodeStatus.PENDING_REVIEW, focus = "Claude Opus 5.5 release"
        )
        val linkedArticle = Article(
            id = 1L, sourceId = "s1", title = "Opus", body = "b", url = "https://example.com/o", contentHash = "h"
        )
        val selection = com.aisummarypodcast.llm.FocusSelection(
            articles = listOf(com.aisummarypodcast.llm.FilteredArticle(linkedArticle)), filterModel = "f", scoreInputTokens = 0, scoreOutputTokens = 0,
            scoreCostCents = 0, scoreCostSource = com.aisummarypodcast.llm.LlmCostSource.API_CACHED, scoreReportedCostCents = null
        )
        val composeResult = com.aisummarypodcast.llm.ComposeStageResult(
            script = "new", composeModel = "c", usage = com.aisummarypodcast.llm.TokenUsage(1, 1), topicOrder = emptyList(),
            composeCostCents = 1, composeCostSource = com.aisummarypodcast.llm.LlmCostSource.API
        )
        every { episodeService.markRecomposing(focusEpisode) } returns focusEpisode.copy(pipelineStage = "composing")
        every { episodeService.updatePipelineStage(any(), any()) } returns Unit
        every { episodeService.findLinkedArticlesAndTopics(30L) } returns LinkedArticlesResult(listOf(linkedArticle), emptyList(), emptyMap(), emptyMap())
        coEvery { llmPipeline.scoreForFocus(podcast, listOf(linkedArticle), "Claude Opus 5.5 release", 30L, any()) } returns selection
        coEvery { llmPipeline.compose(selection.articles, podcast, any(), any()) } returns composeResult
        every { episodeService.saveFeedbackRecompose(any(), composeResult, "shorter") } answers {
            firstArg<Episode>().copy(scriptText = "new", reviewFeedback = "shorter", pipelineStage = null)
        }
        coEvery { episodeService.regenerateRecap(any(), podcast) } answers { firstArg() }

        val returned = podcastService.recomposeFocusEpisodeAsync(focusEpisode, podcast, "shorter")

        org.junit.jupiter.api.Assertions.assertEquals(30L, returned.id)
        coVerify(timeout = 5000) { episodeService.saveFeedbackRecompose(match { it.id == 30L }, composeResult, "shorter") }
        coVerify {
            llmPipeline.compose(selection.articles, podcast, match {
                it.focus == "Claude Opus 5.5 release" && it.extraInstruction == "shorter" && it.episodeId == 30L
            }, any())
        }
        verify(exactly = 0) { episodeService.createGeneratingEpisode(any(), any(), any(), any()) }
    }

    @Test
    fun `feedback recompose is refused for a regular episode`() {
        val regular = Episode(id = 31L, podcastId = "p1", generatedAt = "x", scriptText = "s", status = EpisodeStatus.PENDING_REVIEW)

        org.junit.jupiter.api.Assertions.assertThrows(EpisodeNotRecomposableException::class.java) {
            podcastService.recomposeFocusEpisodeAsync(regular, podcast, "shorter")
        }
    }
}
