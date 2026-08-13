package com.aisummarypodcast.podcast

import com.aisummarypodcast.llm.ArticleEligibilityService
import com.aisummarypodcast.llm.ComposeStageResult
import com.aisummarypodcast.llm.DedupStageResult
import com.aisummarypodcast.llm.EpisodeRecapGenerator
import com.aisummarypodcast.llm.FilteredArticle
import com.aisummarypodcast.llm.LlmCostSource
import com.aisummarypodcast.llm.ModelResolver
import com.aisummarypodcast.llm.PipelineResult
import com.aisummarypodcast.llm.PipelineStage
import com.aisummarypodcast.llm.RecapResult
import com.aisummarypodcast.llm.TokenUsage
import com.aisummarypodcast.llm.ResolvedModel
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeArticle
import com.aisummarypodcast.store.EpisodeArticleRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastRepository
import com.aisummarypodcast.store.PostArticleRepository
import com.aisummarypodcast.tts.TtsPipeline
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.springframework.context.ApplicationEventPublisher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class EpisodeServiceTest {

    private val episodeRepository = mockk<EpisodeRepository>()
    private val podcastRepository = mockk<PodcastRepository> {
        every { findById(any<String>()) } answers { Optional.of(Podcast(id = firstArg(), userId = "u1", name = "Test", topic = "tech")) }
    }
    private val ttsPipeline = mockk<TtsPipeline>()
    private val episodeArticleRepository = mockk<EpisodeArticleRepository> {
        every { save(any()) } answers { firstArg() }
        every { insertIgnore(any(), any(), any(), any()) } returns Unit
    }
    private val articleRepository = mockk<ArticleRepository> {
        every { findById(any<Long>()) } returns Optional.empty()
        every { save(any()) } answers { firstArg() }
    }
    private val episodeRecapGenerator = mockk<EpisodeRecapGenerator>()
    private val modelResolver = mockk<ModelResolver>()
    private val postArticleRepository = mockk<PostArticleRepository>()
    private val episodeSourcesGenerator = mockk<EpisodeSourcesGenerator>(relaxed = true)
    private val articleEligibilityService = mockk<ArticleEligibilityService> {
        every { canResetArticle(any()) } returns true
    }
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val filterModelDef = ResolvedModel(provider = "openrouter", model = "anthropic/claude-haiku-4.5", cost = null)

    private val audioGenerationService = mockk<AudioGenerationService>(relaxed = true)

    private val episodeService = EpisodeService(
        episodeRepository, podcastRepository, ttsPipeline,
        episodeArticleRepository, articleRepository, episodeRecapGenerator, modelResolver,
        postArticleRepository, episodeSourcesGenerator, articleEligibilityService, eventPublisher,
        audioGenerationService
    )

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")

    private fun setupRecapMocks(podcast: Podcast) {
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns filterModelDef
        coEvery { episodeRecapGenerator.generate(any(), podcast, filterModelDef, any()) } returns RecapResult(
            recap = "Recap text.", usage = TokenUsage(800, 60), costCents = 0,
            costSource = LlmCostSource.TABLE
        )
    }

    // --- createEpisodeFromPipelineResult tests ---

    @Test
    fun `creates PENDING_REVIEW episode when requireReview is true`() = runTest {
        val reviewPodcast = podcast.copy(requireReview = true)
        val result = PipelineResult(
            script = "Script", filterModel = "filter", composeModel = "compose",
            processedArticleIds = listOf(10L, 20L)
        )
        every { episodeRepository.save(any()) } answers { firstArg<Episode>().copy(id = 5) }
        every { podcastRepository.save(any()) } answers { firstArg() }
        setupRecapMocks(reviewPodcast)

        val episode = episodeService.createEpisodeFromPipelineResult(reviewPodcast, result)

        assertEquals(EpisodeStatus.PENDING_REVIEW, episode.status)
        verify { episodeArticleRepository.insertIgnore(5L, 10L, null, null) }
        verify { episodeArticleRepository.insertIgnore(5L, 20L, null, null) }
    }

    @Test
    fun `creates un-approved episode when requirePublishApproval is true`() = runTest {
        val approvalPodcast = podcast.copy(requirePublishApproval = true)
        val result = PipelineResult(
            script = "Script", filterModel = "filter", composeModel = "compose",
            processedArticleIds = listOf(10L)
        )
        val saved = mutableListOf<Episode>()
        every { episodeRepository.save(capture(saved)) } answers { firstArg<Episode>().copy(id = 5) }
        every { podcastRepository.save(any()) } answers { firstArg() }
        coEvery { ttsPipeline.generateForExistingEpisode(any(), approvalPodcast) } answers { firstArg<Episode>().copy(status = EpisodeStatus.GENERATED) }
        setupRecapMocks(approvalPodcast)

        episodeService.createEpisodeFromPipelineResult(approvalPodcast, result)

        assertTrue(saved.any { !it.publishApproved }, "expected the episode to be saved un-approved for publication")
    }

    @Test
    fun `approveForPublication sets publishApproved true`() {
        val episode = Episode(id = 9L, podcastId = "p1", generatedAt = "2025-01-01T00:00:00Z", scriptText = "s", status = EpisodeStatus.GENERATED, publishApproved = false)
        every { episodeRepository.findById(9L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }

        val result = episodeService.approveForPublication(episode, podcast)

        assertTrue(result.publishApproved)
        verify { episodeRepository.save(match { it.publishApproved }) }
    }

    @Test
    fun `creates GENERATED episode with TTS when requireReview is false`() = runTest {
        val result = PipelineResult(
            script = "Script", filterModel = "filter", composeModel = "compose",
            processedArticleIds = listOf(10L)
        )
        coEvery { ttsPipeline.generateForExistingEpisode(any(), podcast) } answers {
            firstArg<Episode>().copy(audioFilePath = "/audio.mp3", durationSeconds = 60, status = EpisodeStatus.GENERATED)
        }
        every { episodeRepository.save(any()) } answers { firstArg<Episode>().copy(id = 5) }
        every { podcastRepository.save(any()) } answers { firstArg() }
        setupRecapMocks(podcast)

        val episode = episodeService.createEpisodeFromPipelineResult(podcast, result)

        coVerify { ttsPipeline.generateForExistingEpisode(any(), podcast) }
        verify { episodeArticleRepository.insertIgnore(5L, 10L, null, null) }
    }

    @Test
    fun `saves episode-article links for all processed articles`() = runTest {
        val reviewPodcast = podcast.copy(requireReview = true)
        val result = PipelineResult(
            script = "Script", filterModel = "filter", composeModel = "compose",
            processedArticleIds = listOf(10L, 20L, 30L)
        )
        every { episodeRepository.save(any()) } answers { firstArg<Episode>().copy(id = 7) }
        every { podcastRepository.save(any()) } answers { firstArg() }
        setupRecapMocks(reviewPodcast)

        episodeService.createEpisodeFromPipelineResult(reviewPodcast, result)

        verify(exactly = 3) { episodeArticleRepository.insertIgnore(any(), any(), any(), any()) }
        verify { episodeArticleRepository.insertIgnore(7L, 10L, null, null) }
        verify { episodeArticleRepository.insertIgnore(7L, 20L, null, null) }
        verify { episodeArticleRepository.insertIgnore(7L, 30L, null, null) }
    }

    @Test
    fun `generates and stores recap`() = runTest {
        val reviewPodcast = podcast.copy(requireReview = true)
        val result = PipelineResult(
            script = "Script", filterModel = "filter", composeModel = "compose",
            llmInputTokens = 1000, llmOutputTokens = 500
        )
        every { episodeRepository.save(any()) } answers { firstArg<Episode>().copy(id = 5) }
        every { podcastRepository.save(any()) } answers { firstArg() }
        setupRecapMocks(reviewPodcast)

        episodeService.createEpisodeFromPipelineResult(reviewPodcast, result)

        verify { episodeRepository.save(match { it.recap == "Recap text." }) }
    }

    @Test
    fun `recap failure does not block episode creation`() = runTest {
        val reviewPodcast = podcast.copy(requireReview = true)
        val result = PipelineResult(script = "Script", filterModel = "filter", composeModel = "compose")
        every { episodeRepository.save(any()) } answers { firstArg<Episode>().copy(id = 5) }
        every { podcastRepository.save(any()) } answers { firstArg() }
        every { modelResolver.resolve(reviewPodcast, PipelineStage.FILTER) } returns filterModelDef
        coEvery { episodeRecapGenerator.generate(any(), reviewPodcast, filterModelDef) } throws RuntimeException("LLM error")

        episodeService.createEpisodeFromPipelineResult(reviewPodcast, result)

        verify { podcastRepository.save(match { it.lastGeneratedAt != null }) }
    }

    @Test
    fun `updates lastGeneratedAt on podcast`() = runTest {
        val reviewPodcast = podcast.copy(requireReview = true)
        val result = PipelineResult(script = "Script", filterModel = "filter", composeModel = "compose")
        every { episodeRepository.save(any()) } answers { firstArg<Episode>().copy(id = 5) }
        every { podcastRepository.save(any()) } answers { firstArg() }
        setupRecapMocks(reviewPodcast)

        episodeService.createEpisodeFromPipelineResult(reviewPodcast, result)

        verify { podcastRepository.save(match { it.lastGeneratedAt != null }) }
    }

    @Test
    fun `marks articles as processed after linking`() = runTest {
        val reviewPodcast = podcast.copy(requireReview = true)
        val result = PipelineResult(
            script = "Script", filterModel = "filter", composeModel = "compose",
            processedArticleIds = listOf(10L, 20L)
        )
        val article1 = Article(id = 10L, sourceId = "src-1", title = "A1", body = "body", url = "https://example.com/1", contentHash = "h1")
        val article2 = Article(id = 20L, sourceId = "src-1", title = "A2", body = "body", url = "https://example.com/2", contentHash = "h2")
        every { episodeRepository.save(any()) } answers { firstArg<Episode>().copy(id = 5) }
        every { podcastRepository.save(any()) } answers { firstArg() }
        every { articleRepository.findById(10L) } returns Optional.of(article1)
        every { articleRepository.findById(20L) } returns Optional.of(article2)
        every { articleRepository.save(any()) } answers { firstArg() }
        setupRecapMocks(reviewPodcast)

        episodeService.createEpisodeFromPipelineResult(reviewPodcast, result)

        verify { articleRepository.save(match { it.id == 10L && it.isProcessed }) }
        verify { articleRepository.save(match { it.id == 20L && it.isProcessed }) }
    }

    @Test
    fun `saves topic order when topicOrder is present in pipeline result`() = runTest {
        val reviewPodcast = podcast.copy(requireReview = true)
        val result = PipelineResult(
            script = "Script", filterModel = "filter", composeModel = "compose",
            processedArticleIds = listOf(10L, 20L, 30L),
            articleTopics = mapOf(10L to "AI Safety", 20L to "New Releases", 30L to "AI Safety"),
            topicOrder = listOf("New Releases", "AI Safety")
        )
        every { episodeRepository.save(any()) } answers { firstArg<Episode>().copy(id = 5) }
        every { podcastRepository.save(any()) } answers { firstArg() }
        setupRecapMocks(reviewPodcast)

        episodeService.createEpisodeFromPipelineResult(reviewPodcast, result)

        verify { episodeArticleRepository.insertIgnore(5L, 10L, "AI Safety", 1) }
        verify { episodeArticleRepository.insertIgnore(5L, 20L, "New Releases", 0) }
        verify { episodeArticleRepository.insertIgnore(5L, 30L, "AI Safety", 1) }
    }

    // --- discardAndResetArticles tests ---

    @Test
    fun `discardAndResetArticles resets non-aggregated articles`() {
        val episode = Episode(id = 1L, podcastId = "p1", generatedAt = "now", scriptText = "Script", status = EpisodeStatus.PENDING_REVIEW)
        val article1 = Article(id = 10L, sourceId = "src-1", title = "A1", body = "body", url = "https://example.com/1", contentHash = "h1", isProcessed = true)
        val article2 = Article(id = 20L, sourceId = "src-1", title = "A2", body = "body", url = "https://example.com/2", contentHash = "h2", isProcessed = true)
        val links = listOf(
            EpisodeArticle(id = 1L, episodeId = 1L, articleId = 10L),
            EpisodeArticle(id = 2L, episodeId = 1L, articleId = 20L)
        )

        every { episodeRepository.findById(1L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }
        every { episodeArticleRepository.findByEpisodeId(1L) } returns links
        every { articleRepository.findById(10L) } returns Optional.of(article1)
        every { articleRepository.findById(20L) } returns Optional.of(article2)
        every { articleRepository.save(any()) } answers { firstArg() }
        every { postArticleRepository.countByArticleId(10L) } returns 1L
        every { postArticleRepository.countByArticleId(20L) } returns 0L
        every { podcastRepository.findById("p1") } returns Optional.of(podcast)
        every { episodeRepository.findLatestPublishedByPodcastId("p1") } returns null
        every { podcastRepository.save(any()) } answers { firstArg() }

        episodeService.discardAndResetArticles(episode, "p1")

        verify { episodeRepository.save(match { it.status == EpisodeStatus.DISCARDED }) }
        verify { articleRepository.save(match { it.id == 10L && !it.isProcessed }) }
        verify { articleRepository.save(match { it.id == 20L && !it.isProcessed }) }
        verify(exactly = 0) { postArticleRepository.deleteByArticleId(any()) }
        verify(exactly = 0) { articleRepository.deleteById(any<Long>()) }
    }

    @Test
    fun `discardAndResetArticles deletes aggregated articles and unlinks posts`() {
        val episode = Episode(id = 1L, podcastId = "p1", generatedAt = "now", scriptText = "Script", status = EpisodeStatus.PENDING_REVIEW)
        val aggregatedArticle = Article(id = 30L, sourceId = "src-1", title = "Posts from @user", body = "body", url = "https://nitter.net/user", contentHash = "h3", isProcessed = true)
        val links = listOf(EpisodeArticle(id = 1L, episodeId = 1L, articleId = 30L))

        every { episodeRepository.findById(1L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }
        every { episodeArticleRepository.findByEpisodeId(1L) } returns links
        every { articleRepository.findById(30L) } returns Optional.of(aggregatedArticle)
        every { postArticleRepository.countByArticleId(30L) } returns 5L
        justRun { postArticleRepository.deleteByArticleId(30L) }
        justRun { articleRepository.deleteById(30L) }

        episodeService.discardAndResetArticles(episode, "p1")

        verify { episodeRepository.save(match { it.status == EpisodeStatus.DISCARDED }) }
        verify { postArticleRepository.deleteByArticleId(30L) }
        verify { articleRepository.deleteById(30L) }
        verify(exactly = 0) { articleRepository.save(any()) }
    }

    @Test
    fun `discardAndResetArticles handles mixed article types`() {
        val episode = Episode(id = 1L, podcastId = "p1", generatedAt = "now", scriptText = "Script", status = EpisodeStatus.PENDING_REVIEW)
        val regularArticle = Article(id = 10L, sourceId = "src-1", title = "A1", body = "body", url = "https://example.com/1", contentHash = "h1", isProcessed = true)
        val aggregatedArticle = Article(id = 30L, sourceId = "src-2", title = "Posts from @user", body = "body", url = "https://nitter.net/user", contentHash = "h3", isProcessed = true)
        val links = listOf(
            EpisodeArticle(id = 1L, episodeId = 1L, articleId = 10L),
            EpisodeArticle(id = 2L, episodeId = 1L, articleId = 30L)
        )

        every { episodeRepository.findById(1L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }
        every { episodeArticleRepository.findByEpisodeId(1L) } returns links
        every { articleRepository.findById(10L) } returns Optional.of(regularArticle)
        every { articleRepository.findById(30L) } returns Optional.of(aggregatedArticle)
        every { articleRepository.save(any()) } answers { firstArg() }
        every { postArticleRepository.countByArticleId(10L) } returns 1L
        every { postArticleRepository.countByArticleId(30L) } returns 4L
        justRun { postArticleRepository.deleteByArticleId(30L) }
        justRun { articleRepository.deleteById(30L) }
        every { podcastRepository.findById("p1") } returns Optional.of(podcast)
        every { episodeRepository.findLatestPublishedByPodcastId("p1") } returns null
        every { podcastRepository.save(any()) } answers { firstArg() }

        episodeService.discardAndResetArticles(episode, "p1")

        verify { episodeRepository.save(match { it.status == EpisodeStatus.DISCARDED }) }
        verify { articleRepository.save(match { it.id == 10L && !it.isProcessed }) }
        verify { postArticleRepository.deleteByArticleId(30L) }
        verify { articleRepository.deleteById(30L) }
    }

    @Test
    fun `discardAndResetArticles handles no linked articles`() {
        val episode = Episode(id = 1L, podcastId = "p1", generatedAt = "now", scriptText = "Script", status = EpisodeStatus.PENDING_REVIEW)

        every { episodeRepository.findById(1L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }
        every { episodeArticleRepository.findByEpisodeId(1L) } returns emptyList()

        episodeService.discardAndResetArticles(episode, "p1")

        verify { episodeRepository.save(match { it.status == EpisodeStatus.DISCARDED }) }
        verify(exactly = 0) { articleRepository.save(any()) }
    }

    @Test
    fun `discardAndResetArticles skips articles linked to published episodes`() {
        val episode = Episode(id = 1L, podcastId = "p1", generatedAt = "now", scriptText = "Script", status = EpisodeStatus.PENDING_REVIEW)
        val publishedArticle = Article(id = 10L, sourceId = "src-1", title = "A1", body = "body", url = "https://example.com/1", contentHash = "h1", isProcessed = true)
        val resettableArticle = Article(id = 20L, sourceId = "src-1", title = "A2", body = "body", url = "https://example.com/2", contentHash = "h2", isProcessed = true)
        val links = listOf(
            EpisodeArticle(id = 1L, episodeId = 1L, articleId = 10L),
            EpisodeArticle(id = 2L, episodeId = 1L, articleId = 20L)
        )

        every { episodeRepository.findById(1L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }
        every { episodeArticleRepository.findByEpisodeId(1L) } returns links
        every { articleRepository.findById(10L) } returns Optional.of(publishedArticle)
        every { articleRepository.findById(20L) } returns Optional.of(resettableArticle)
        every { articleRepository.save(any()) } answers { firstArg() }
        every { articleEligibilityService.canResetArticle(10L) } returns false
        every { articleEligibilityService.canResetArticle(20L) } returns true
        every { postArticleRepository.countByArticleId(20L) } returns 0L
        every { podcastRepository.findById("p1") } returns Optional.of(podcast)
        every { episodeRepository.findLatestPublishedByPodcastId("p1") } returns null
        every { podcastRepository.save(any()) } answers { firstArg() }

        episodeService.discardAndResetArticles(episode, "p1")

        verify { episodeRepository.save(match { it.status == EpisodeStatus.DISCARDED }) }
        verify(exactly = 0) { postArticleRepository.countByArticleId(10L) }
        verify { articleRepository.save(match { it.id == 20L && !it.isProcessed }) }
        verify(exactly = 1) { articleRepository.save(any()) }
    }

    @Test
    fun `discardAndResetArticles skips aggregated article deletion when linked to published episode`() {
        val episode = Episode(id = 1L, podcastId = "p1", generatedAt = "now", scriptText = "Script", status = EpisodeStatus.PENDING_REVIEW)
        val aggregatedArticle = Article(id = 30L, sourceId = "src-1", title = "Posts from @user", body = "body", url = "https://nitter.net/user", contentHash = "h3", isProcessed = true)
        val links = listOf(EpisodeArticle(id = 1L, episodeId = 1L, articleId = 30L))

        every { episodeRepository.findById(1L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }
        every { episodeArticleRepository.findByEpisodeId(1L) } returns links
        every { articleRepository.findById(30L) } returns Optional.of(aggregatedArticle)
        every { articleEligibilityService.canResetArticle(30L) } returns false

        episodeService.discardAndResetArticles(episode, "p1")

        verify { episodeRepository.save(match { it.status == EpisodeStatus.DISCARDED }) }
        verify(exactly = 0) { postArticleRepository.deleteByArticleId(any()) }
        verify(exactly = 0) { articleRepository.deleteById(any<Long>()) }
        verify(exactly = 0) { articleRepository.save(any()) }
    }

    @Test
    fun `discardAndResetArticles rolls back lastGeneratedAt to latest published episode`() {
        val episode = Episode(id = 3L, podcastId = "p1", generatedAt = "2026-03-18T15:00:00Z", scriptText = "Script", status = EpisodeStatus.PENDING_REVIEW)
        val article = Article(id = 10L, sourceId = "src-1", title = "A1", body = "body", url = "https://example.com/1", contentHash = "h1", isProcessed = true, publishedAt = "2026-03-18T12:00:00Z")
        val links = listOf(EpisodeArticle(id = 1L, episodeId = 3L, articleId = 10L))
        val publishedEpisode = Episode(id = 2L, podcastId = "p1", generatedAt = "2026-03-17T15:00:00Z", scriptText = "Old", status = EpisodeStatus.GENERATED)

        every { episodeRepository.findById(3L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }
        every { episodeArticleRepository.findByEpisodeId(3L) } returns links
        every { articleRepository.findById(10L) } returns Optional.of(article)
        every { articleRepository.save(any()) } answers { firstArg() }
        every { postArticleRepository.countByArticleId(10L) } returns 0L
        every { podcastRepository.findById("p1") } returns Optional.of(podcast)
        every { episodeRepository.findLatestPublishedByPodcastId("p1") } returns publishedEpisode
        every { podcastRepository.save(any()) } answers { firstArg() }

        episodeService.discardAndResetArticles(episode, "p1")

        verify { podcastRepository.save(match { it.lastGeneratedAt == "2026-03-17T15:00:00Z" }) }
    }

    @Test
    fun `discardAndResetArticles clears lastGeneratedAt when no published episodes exist`() {
        val episode = Episode(id = 1L, podcastId = "p1", generatedAt = "2026-03-18T15:00:00Z", scriptText = "Script", status = EpisodeStatus.PENDING_REVIEW)
        val article = Article(id = 10L, sourceId = "src-1", title = "A1", body = "body", url = "https://example.com/1", contentHash = "h1", isProcessed = true)
        val links = listOf(EpisodeArticle(id = 1L, episodeId = 1L, articleId = 10L))

        every { episodeRepository.findById(1L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }
        every { episodeArticleRepository.findByEpisodeId(1L) } returns links
        every { articleRepository.findById(10L) } returns Optional.of(article)
        every { articleRepository.save(any()) } answers { firstArg() }
        every { postArticleRepository.countByArticleId(10L) } returns 0L
        every { podcastRepository.findById("p1") } returns Optional.of(podcast)
        every { episodeRepository.findLatestPublishedByPodcastId("p1") } returns null
        every { podcastRepository.save(any()) } answers { firstArg() }

        episodeService.discardAndResetArticles(episode, "p1")

        verify { podcastRepository.save(match { it.lastGeneratedAt == null }) }
    }

    // --- saveDedupResults tests ---

    @Test
    fun `saveDedupResults saves episode-article links with topics`() {
        val episode = Episode(id = 5L, podcastId = "p1", generatedAt = "now", scriptText = "", status = EpisodeStatus.GENERATING)
        val article1 = Article(id = 10L, sourceId = "s1", title = "A1", body = "body", url = "https://example.com/1", contentHash = "h1", relevanceScore = 8)
        val article2 = Article(id = 20L, sourceId = "s1", title = "A2", body = "body", url = "https://example.com/2", contentHash = "h2", relevanceScore = 7)
        val dedupResult = DedupStageResult(
            filteredArticles = listOf(
                FilteredArticle(article1, topic = "AI Safety"),
                FilteredArticle(article2, topic = "New Releases")
            ),
            filterModel = "anthropic/claude-haiku-4.5",
            dedupModel = "anthropic/claude-sonnet-4.6",
            usage = TokenUsage(200, 100),
            followUpAnnotations = emptyMap(),
            topicLabels = listOf("AI Safety", "New Releases"),
            dedupCostCents = 5,
            dedupCostSource = LlmCostSource.TABLE
        )
        every { episodeRepository.findById(5L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }

        episodeService.saveDedupResults(episode, dedupResult)

        verify { episodeArticleRepository.insertIgnore(5L, 10L, "AI Safety", 0) }
        verify { episodeArticleRepository.insertIgnore(5L, 20L, "New Releases", 1) }
        verify { episodeRepository.save(match { it.filterModel == "anthropic/claude-haiku-4.5" && it.llmInputTokens == 200 && it.llmOutputTokens == 100 }) }
    }

    // --- saveComposeResult tests ---

    @Test
    fun `saveComposeResult saves script and aggregate sums per-stage tokens`() {
        val episode = Episode(
            id = 5L, podcastId = "p1", generatedAt = "now", scriptText = "",
            status = EpisodeStatus.GENERATING,
            dedupInputTokens = 200, dedupOutputTokens = 100, dedupCostCents = 5
        )
        val composeResult = ComposeStageResult(
            script = "Today in tech...",
            composeModel = "anthropic/claude-sonnet-4",
            usage = TokenUsage(500, 300),
            topicOrder = listOf("AI Safety"),
            composeCostCents = 10,
            composeCostSource = LlmCostSource.TABLE
        )
        every { episodeRepository.findById(5L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }

        episodeService.saveComposeResult(episode, composeResult)

        verify { episodeRepository.save(match {
            it.scriptText == "Today in tech..." &&
            it.composeModel == "anthropic/claude-sonnet-4" &&
            it.composeInputTokens == 500 &&
            it.composeOutputTokens == 300 &&
            it.composeCostCents == 10 &&
            it.llmInputTokens == 700 &&
            it.llmOutputTokens == 400 &&
            it.llmCostCents == 15
        }) }
    }

    @Test
    fun `saveDedupResults persists per-stage score and dedup triples`() {
        val episode = Episode(id = 5L, podcastId = "p1", generatedAt = "now", scriptText = "", status = EpisodeStatus.GENERATING)
        val article1 = Article(id = 10L, sourceId = "s1", title = "A1", body = "b", url = "https://x/1", contentHash = "h1", llmInputTokens = 400, llmOutputTokens = 80)
        val article2 = Article(id = 20L, sourceId = "s1", title = "A2", body = "b", url = "https://x/2", contentHash = "h2", llmInputTokens = 500, llmOutputTokens = 100)
        val dedupResult = DedupStageResult(
            filteredArticles = listOf(FilteredArticle(article1), FilteredArticle(article2)),
            filterModel = "anthropic/claude-haiku-4.5",
            dedupModel = "anthropic/claude-sonnet-4.6",
            usage = TokenUsage(200, 100),
            followUpAnnotations = emptyMap(),
            topicLabels = emptyList(),
            dedupCostCents = 5,
            dedupCostSource = LlmCostSource.TABLE,
            scoreInputTokens = 900, scoreOutputTokens = 180, scoreCostCents = 3,
            scoreCostSource = LlmCostSource.TABLE
        )
        every { episodeRepository.findById(5L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }

        episodeService.saveDedupResults(episode, dedupResult)

        verify { episodeRepository.save(match {
            it.scoreInputTokens == 900 &&
            it.scoreOutputTokens == 180 &&
            it.scoreCostCents == 3 &&
            it.dedupInputTokens == 200 &&
            it.dedupOutputTokens == 100 &&
            it.dedupCostCents == 5 &&
            it.llmInputTokens == 1100 &&
            it.llmOutputTokens == 280 &&
            it.llmCostCents == 8
        }) }
    }

    @Test
    fun `saveDedupResults persists the per-stage reported cost cents`() {
        val episode = Episode(id = 5L, podcastId = "p1", generatedAt = "now", scriptText = "", status = EpisodeStatus.GENERATING)
        val article = Article(id = 10L, sourceId = "s1", title = "A1", body = "b", url = "https://x/1", contentHash = "h1")
        val dedupResult = DedupStageResult(
            filteredArticles = listOf(FilteredArticle(article)),
            filterModel = "anthropic/claude-haiku-4.5",
            dedupModel = "anthropic/claude-sonnet-4.6",
            usage = TokenUsage(200, 100),
            followUpAnnotations = emptyMap(),
            topicLabels = emptyList(),
            dedupCostCents = 5,
            dedupCostSource = LlmCostSource.API,
            dedupReportedCostCents = 4.62,
            scoreInputTokens = 900, scoreOutputTokens = 180, scoreCostCents = 3,
            scoreCostSource = LlmCostSource.MIXED,
            scoreReportedCostCents = 3.14
        )
        every { episodeRepository.findById(5L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }

        episodeService.saveDedupResults(episode, dedupResult)

        verify { episodeRepository.save(match {
            it.scoreReportedCostCents == 3.14 &&
            it.dedupReportedCostCents == 4.62 &&
            it.composeReportedCostCents == null &&
            it.recapReportedCostCents == null
        }) }
    }

    @Test
    fun `saveDedupResults leaves the reported cost null when nothing was reported`() {
        val episode = Episode(id = 5L, podcastId = "p1", generatedAt = "now", scriptText = "", status = EpisodeStatus.GENERATING)
        val article = Article(id = 10L, sourceId = "s1", title = "A1", body = "b", url = "https://x/1", contentHash = "h1")
        val dedupResult = DedupStageResult(
            filteredArticles = listOf(FilteredArticle(article)),
            filterModel = "anthropic/claude-haiku-4.5",
            dedupModel = "anthropic/claude-sonnet-4.6",
            usage = TokenUsage(200, 100),
            followUpAnnotations = emptyMap(),
            topicLabels = emptyList(),
            dedupCostCents = 5,
            dedupCostSource = LlmCostSource.TABLE,
            scoreCostSource = LlmCostSource.TABLE
        )
        every { episodeRepository.findById(5L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }

        episodeService.saveDedupResults(episode, dedupResult)

        verify { episodeRepository.save(match {
            it.scoreReportedCostCents == null && it.dedupReportedCostCents == null
        }) }
    }

    @Test
    fun `saveComposeResult persists the compose reported cost cents`() {
        val episode = Episode(
            id = 5L, podcastId = "p1", generatedAt = "now", scriptText = "",
            status = EpisodeStatus.GENERATING,
            scoreReportedCostCents = 3.14
        )
        val composeResult = ComposeStageResult(
            script = "Today in tech...",
            composeModel = "anthropic/claude-sonnet-4",
            usage = TokenUsage(500, 300),
            topicOrder = listOf("AI Safety"),
            composeCostCents = 10,
            composeCostSource = LlmCostSource.API,
            composeReportedCostCents = 9.81
        )
        every { episodeRepository.findById(5L) } returns Optional.of(episode)
        every { episodeRepository.save(any()) } answers { firstArg() }

        episodeService.saveComposeResult(episode, composeResult)

        verify { episodeRepository.save(match {
            it.composeReportedCostCents == 9.81 && it.scoreReportedCostCents == 3.14
        }) }
    }

    // --- resetForRetry tests ---

    @Test
    fun `resetForRetry sets GENERATING status and clears errorMessage`() {
        val failedEpisode = Episode(
            id = 5L, podcastId = "p1", generatedAt = "now", scriptText = "Script",
            status = EpisodeStatus.FAILED, errorMessage = "LLM error", pipelineStage = "composing"
        )
        every { episodeRepository.findById(5L) } returns Optional.of(failedEpisode)
        every { episodeRepository.save(any()) } answers { firstArg() }

        val reset = episodeService.resetForRetry(failedEpisode)

        assertEquals(EpisodeStatus.GENERATING, reset.status)
        assertNull(reset.errorMessage)
        assertEquals("Script", reset.scriptText)
        assertEquals("composing", reset.pipelineStage)
    }

    // --- failEpisode tests ---

    @Test
    fun `failEpisode preserves pipelineStage`() {
        val generatingEpisode = Episode(
            id = 5L, podcastId = "p1", generatedAt = "now", scriptText = "",
            status = EpisodeStatus.GENERATING, pipelineStage = "composing"
        )
        every { episodeRepository.findById(5L) } returns Optional.of(generatingEpisode)
        every { episodeRepository.save(any()) } answers { firstArg() }
        every { podcastRepository.save(any()) } answers { firstArg() }

        episodeService.failEpisode(podcast, "LLM error", generatingEpisode)

        verify { episodeRepository.save(match { it.status == EpisodeStatus.FAILED && it.pipelineStage == "composing" && it.errorMessage == "LLM error" }) }
    }

    // --- hasActiveEpisode tests ---

    @Test
    fun `hasActiveEpisode returns true when pending episodes exist`() {
        every { episodeRepository.findByPodcastIdAndStatusIn("p1", listOf(EpisodeStatus.GENERATING, EpisodeStatus.PENDING_REVIEW, EpisodeStatus.APPROVED, EpisodeStatus.GENERATING_AUDIO)) } returns listOf(
            Episode(id = 1L, podcastId = "p1", generatedAt = "now", scriptText = "Script", status = EpisodeStatus.PENDING_REVIEW)
        )

        assertEquals(true, episodeService.hasActiveEpisode("p1"))
    }

    @Test
    fun `hasActiveEpisode returns false when no pending episodes`() {
        every { episodeRepository.findByPodcastIdAndStatusIn("p1", listOf(EpisodeStatus.GENERATING, EpisodeStatus.PENDING_REVIEW, EpisodeStatus.APPROVED, EpisodeStatus.GENERATING_AUDIO)) } returns emptyList()

        assertEquals(false, episodeService.hasActiveEpisode("p1"))
    }

    @Test
    fun `hasActiveEpisode returns true when GENERATING_AUDIO episode exists`() {
        every { episodeRepository.findByPodcastIdAndStatusIn("p1", listOf(EpisodeStatus.GENERATING, EpisodeStatus.PENDING_REVIEW, EpisodeStatus.APPROVED, EpisodeStatus.GENERATING_AUDIO)) } returns listOf(
            Episode(id = 1L, podcastId = "p1", generatedAt = "now", scriptText = "Script", status = EpisodeStatus.GENERATING_AUDIO)
        )

        assertEquals(true, episodeService.hasActiveEpisode("p1"))
    }
}
