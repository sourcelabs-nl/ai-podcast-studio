package com.aisummarypodcast.llm

import com.aisummarypodcast.research.PreComposeResearch
import com.aisummarypodcast.research.PreComposeResearchService

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.ComposeProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelType
import com.aisummarypodcast.config.SourceProperties
import com.aisummarypodcast.podcast.EpisodeWindow
import com.aisummarypodcast.podcast.EpisodeWindowResolver
import com.aisummarypodcast.source.SourceAggregator
import com.aisummarypodcast.testComposeRetryRegistry
import com.aisummarypodcast.testRetryRegistry
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.CandidateOutcome
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.Post
import com.aisummarypodcast.store.PostRepository
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceRepository
import com.aisummarypodcast.store.SourceType
import com.aisummarypodcast.store.TtsProviderType
import com.aisummarypodcast.tts.TtsProvider
import com.aisummarypodcast.tts.TtsProviderFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class LlmPipelineTest {

    private val articleScoreSummarizer = mockk<ArticleScoreSummarizer>()
    private val briefingComposer = mockk<BriefingComposer>()
    private val dialogueComposer = mockk<DialogueComposer>()
    private val interviewComposer = mockk<InterviewComposer>()
    private val modelResolver = mockk<ModelResolver>()
    private val articleRepository = mockk<ArticleRepository> {
        every { save(any()) } answers { firstArg() }
    }
    private val sourceRepository = mockk<SourceRepository>()
    private val postRepository = mockk<PostRepository>()
    private val sourceAggregator = mockk<SourceAggregator>()
    private val ttsProviderMock = mockk<TtsProvider> {
        every { scriptGuidelines(any(), any()) } returns ""
    }
    private val ttsProviderFactory = mockk<TtsProviderFactory> {
        every { resolve(any()) } returns ttsProviderMock
    }
    private val articleEligibilityService = mockk<ArticleEligibilityService>()
    private val topicDedupFilter = mockk<TopicDedupFilter>()
    private val preComposeResearchService = mockk<PreComposeResearchService> {
        coEvery { research(any()) } returns PreComposeResearch.NONE
    }

    private val window = EpisodeWindow(
        start = Instant.parse("2026-03-17T14:00:00Z"),
        end = Instant.parse("2026-03-18T14:00:00Z")
    )
    private val episodeWindowResolver = mockk<EpisodeWindowResolver> {
        every { resolveForNow(any()) } returns window
        every { episodeDateOf(any(), any()) } returns LocalDate.of(2026, 3, 18)
        every { nextEpisodeDateAfter(any(), any()) } returns LocalDate.of(2026, 3, 19)
    }

    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key"),
        source = SourceProperties(maxArticleAgeDays = 7)
    )

    private val filterModelDef = ResolvedModel(provider = "openrouter", model = "anthropic/claude-haiku-4.5", cost = null, stage = PipelineStage.FILTER)
    private val composeModelDef = ResolvedModel(provider = "openrouter", model = "anthropic/claude-sonnet-4", cost = null, stage = PipelineStage.COMPOSE)

    private val pipeline = LlmPipeline(
        articleScoreSummarizer, briefingComposer, dialogueComposer, interviewComposer, modelResolver, articleRepository,
        sourceRepository, postRepository, sourceAggregator, appProperties, ttsProviderFactory,
        articleEligibilityService, topicDedupFilter, episodeWindowResolver, testRetryRegistry(),
        preComposeResearchService
    )

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Tech Daily", topic = "tech", relevanceThreshold = 5)
    private val runConfig = RunConfig.resolve(appProperties, podcast)
    private val source = Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed")

    private val scoredArticle = Article(
        id = 1, sourceId = "s1", title = "AI News", body = "Body",
        url = "https://example.com/ai", contentHash = "hash1", relevanceScore = 8, summary = "Summary."
    )

    private fun scored(
        id: Long,
        input: Int = 0,
        output: Int = 0,
        reportedUsd: Double? = null,
        relevance: Int = 8
    ) = Article(
        id = id, sourceId = "s1", title = "Article $id", body = "Body",
        url = "https://example.com/$id", contentHash = "hash$id", relevanceScore = relevance,
        summary = "Summary $id", llmInputTokens = input, llmOutputTokens = output,
        llmReportedCostUsd = reportedUsd
    )

    /** A pipeline whose compose cap is [maxArticles], so the cut can be observed. */
    private fun pipelineComposingAtMost(maxArticles: Int) = LlmPipeline(
        articleScoreSummarizer, briefingComposer, dialogueComposer, interviewComposer, modelResolver, articleRepository,
        sourceRepository, postRepository, sourceAggregator,
        appProperties.copy(compose = ComposeProperties(maxArticles = maxArticles)), ttsProviderFactory,
        articleEligibilityService, topicDedupFilter, episodeWindowResolver, testRetryRegistry(),
        preComposeResearchService
    )

    private fun setupBasicPipeline(articles: List<Article> = listOf(scoredArticle), podcast: Podcast = this.podcast) {
        every { sourceRepository.findByPodcastId(podcast.id) } returns listOf(source)
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.DEDUP) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.COMPOSE) } returns composeModelDef
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns emptyList()
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns emptyList()
        every { articleEligibilityService.findEligibleArticles(listOf("s1"), podcast, any()) } returns articles
        every { articleEligibilityService.findHistory(podcast) } returns EpisodeHistory.EMPTY
        coEvery { topicDedupFilter.filter(articles, EpisodeHistory.EMPTY, podcast.userId, filterModelDef) } returns
            DedupFilterResult(articles.map { FilteredArticle(it) }, TokenUsage(100, 50))
    }

    /**
     * Drives the stages in the order `PodcastService.runGenerationPipeline` drives them: select,
     * dedup, compose. The pipeline deliberately exposes no single method that does all three, so a
     * test that wants the whole sequence walks the same calls production makes rather than a
     * convenience entry point only tests would keep alive.
     *
     * Returns null at the point production would abandon the episode: no eligible articles, or
     * everything filtered away as duplicates.
     */
    private suspend fun composeThroughStages(
        podcast: Podcast = this.podcast,
        pipeline: LlmPipeline = this.pipeline
    ): ComposeStageResult? {
        val eligible = pipeline.aggregateScoreAndFilter(podcast, runConfig, window) ?: return null
        val dedupResult = pipeline.dedup(eligible, podcast, runConfig) ?: return null
        return pipeline.compose(
            dedupResult.filteredArticles, podcast,
            ComposeContext(
                followUpAnnotations = dedupResult.followUpAnnotations,
                topicLabels = dedupResult.topicLabels,
                episodeDate = episodeWindowResolver.episodeDateOf(podcast, window)
            )
        )
    }

    @Test
    fun `returns null when podcast has no sources`() {
        every { sourceRepository.findByPodcastId("p1") } returns emptyList()

        runTest {
            assertNull(pipeline.aggregateScoreAndFilter(podcast, runConfig, window))
        }
    }

    @Test
    fun `returns null when no eligible articles exist`() {
        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.DEDUP) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.COMPOSE) } returns composeModelDef
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns emptyList()
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns emptyList()
        every { articleEligibilityService.findEligibleArticles(listOf("s1"), podcast, any()) } returns emptyList()

        runTest {
            assertNull(pipeline.aggregateScoreAndFilter(podcast, runConfig, window))
        }
    }

    @Test
    fun `returns null when all articles filtered as duplicates`() {
        setupBasicPipeline()
        coEvery { topicDedupFilter.filter(any(), any(), any(), any()) } returns
            DedupFilterResult(emptyList(), TokenUsage(100, 50))

        runTest {
            val eligible = pipeline.aggregateScoreAndFilter(podcast, runConfig, window)

            assertNotNull(eligible)
            assertNull(pipeline.dedup(eligible!!, podcast, runConfig))
        }
    }

    @Test
    fun `aggregates posts then scores and composes`() {
        val unlinkedPost = Post(
            id = 1, sourceId = "s1", title = "AI News", body = "Post body",
            url = "https://example.com/ai", contentHash = "hash1", createdAt = "2026-02-16T10:00:00Z"
        )
        val createdArticle = Article(
            id = 1, sourceId = "s1", title = "AI News", body = "Post body",
            url = "https://example.com/ai", contentHash = "arthash1"
        )
        val scored = createdArticle.copy(relevanceScore = 8, summary = "AI is advancing.")
        val compositionResult = CompositionResult("Today in tech...", TokenUsage(1000, 500))

        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.DEDUP) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.COMPOSE) } returns composeModelDef
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns listOf(unlinkedPost)
        every { sourceAggregator.aggregateAndPersist(listOf(unlinkedPost), source) } returns listOf(createdArticle)
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns listOf(createdArticle)
        coEvery { articleScoreSummarizer.scoreSummarize(listOf(createdArticle), podcast, filterModelDef, ScoringContext(mapOf("s1" to "example.com/feed")), any()) } returns listOf(scored)
        every { articleEligibilityService.findEligibleArticles(listOf("s1"), podcast, any()) } returns listOf(scored)
        every { articleEligibilityService.findHistory(podcast) } returns EpisodeHistory.EMPTY
        coEvery { topicDedupFilter.filter(listOf(scored), EpisodeHistory.EMPTY, "u1", filterModelDef) } returns
            DedupFilterResult(listOf(FilteredArticle(scored)), TokenUsage(100, 50))
        coEvery { briefingComposer.compose(listOf(scored), podcast, composeModelDef, any()) } returns compositionResult

        runTest {
            val result = composeThroughStages()

            assertNotNull(result)
            assertEquals("Today in tech...", result!!.script)

            verify { sourceAggregator.aggregateAndPersist(listOf(unlinkedPost), source) }
            coVerify { articleScoreSummarizer.scoreSummarize(listOf(createdArticle), podcast, filterModelDef, ScoringContext(mapOf("s1" to "example.com/feed")), any()) }
        }
    }

    @Test
    fun `composes distinct articles when the dedup filter returns duplicates`() {
        val second = scoredArticle.copy(id = 2, title = "More AI News", contentHash = "hash2")
        val eligible = listOf(scoredArticle, second)
        setupBasicPipeline(eligible)
        // A degenerating dedup response repeats the same two articles many times over. Without
        // de-duplication these repeats would consume the compose cap and starve the episode.
        val repeated = (1..30).flatMap { eligible.map { article -> FilteredArticle(article) } }
        coEvery { topicDedupFilter.filter(eligible, EpisodeHistory.EMPTY, "u1", filterModelDef) } returns
            DedupFilterResult(repeated, TokenUsage(100, 50))

        val composed = slot<List<Article>>()
        coEvery {
            briefingComposer.compose(capture(composed), any(), any(), any())
        } returns CompositionResult("Script", TokenUsage(500, 200))

        runTest {
            composeThroughStages()

            assertEquals(listOf(1L, 2L), composed.captured.map { it.id })
        }
    }

    @Test
    fun `delegates article selection to ArticleEligibilityService`() {
        setupBasicPipeline()
        coEvery { briefingComposer.compose(any(), any(), any(), any()) } returns CompositionResult("Script", TokenUsage(500, 200))

        runTest { composeThroughStages() }

        verify { articleEligibilityService.findEligibleArticles(listOf("s1"), podcast, any()) }
        verify { articleEligibilityService.findHistory(podcast) }
    }

    @Test
    fun `dedup caps compose articles to the highest-relevance ones`() {
        val cappedProperties = appProperties.copy(compose = ComposeProperties(maxArticles = 2))
        val cappedPipeline = LlmPipeline(
            articleScoreSummarizer, briefingComposer, dialogueComposer, interviewComposer, modelResolver, articleRepository,
            sourceRepository, postRepository, sourceAggregator, cappedProperties, ttsProviderFactory,
            articleEligibilityService, topicDedupFilter, episodeWindowResolver, testRetryRegistry(),
        preComposeResearchService
        )

        val low = scoredArticle.copy(id = 1, relevanceScore = 5)
        val high = scoredArticle.copy(id = 2, relevanceScore = 9)
        val mid = scoredArticle.copy(id = 3, relevanceScore = 7)
        val eligible = listOf(low, high, mid)

        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.DEDUP) } returns filterModelDef
        every { articleEligibilityService.findHistory(podcast) } returns EpisodeHistory.EMPTY
        coEvery { topicDedupFilter.filter(eligible, EpisodeHistory.EMPTY, "u1", filterModelDef) } returns
            DedupFilterResult(eligible.map { FilteredArticle(it) }, TokenUsage(100, 50))

        runTest {
            val result = cappedPipeline.dedup(eligible, podcast, runConfig)

            assertNotNull(result)
            assertEquals(listOf(2L, 3L), result!!.filteredArticles.map { it.article.id })
        }
    }

    @Test
    fun `compose caps its input to the highest-relevance articles`() {
        val cappedProperties = appProperties.copy(compose = ComposeProperties(maxArticles = 2))
        val cappedPipeline = LlmPipeline(
            articleScoreSummarizer, briefingComposer, dialogueComposer, interviewComposer, modelResolver, articleRepository,
            sourceRepository, postRepository, sourceAggregator, cappedProperties, ttsProviderFactory,
            articleEligibilityService, topicDedupFilter, episodeWindowResolver, testRetryRegistry(),
        preComposeResearchService
        )

        val low = scoredArticle.copy(id = 1, relevanceScore = 5)
        val high = scoredArticle.copy(id = 2, relevanceScore = 9)
        val mid = scoredArticle.copy(id = 3, relevanceScore = 7)
        val filtered = listOf(low, high, mid).map { FilteredArticle(it) }

        every { modelResolver.resolve(any(), PipelineStage.COMPOSE) } returns composeModelDef
        val composedArticles = slot<List<Article>>()
        coEvery {
            briefingComposer.compose(capture(composedArticles), podcast, composeModelDef, any())
        } returns CompositionResult("Script", TokenUsage(500, 200))

        runTest {
            cappedPipeline.compose(filtered, podcast)

            assertEquals(listOf(2L, 3L), composedArticles.captured.map { it.id })
        }
    }

    @Test
    fun `calls dedup filter with eligible and historical articles`() {
        val history = EpisodeHistory(
            articles = listOf(Article(id = 99, sourceId = "s1", title = "Old", body = "old", url = "http://old.com", contentHash = "h99")),
            coveredTopics = listOf("An already covered topic")
        )
        setupBasicPipeline()
        every { articleEligibilityService.findHistory(podcast) } returns history
        coEvery { topicDedupFilter.filter(listOf(scoredArticle), history, "u1", filterModelDef) } returns
            DedupFilterResult(listOf(FilteredArticle(scoredArticle)), TokenUsage(100, 50))
        coEvery { briefingComposer.compose(any(), any(), any(), any()) } returns CompositionResult("Script", TokenUsage(500, 200))

        runTest { composeThroughStages() }

        coVerify { topicDedupFilter.filter(listOf(scoredArticle), history, "u1", filterModelDef) }
    }

    @Test
    fun `passes follow-up annotations from dedup filter to composer`() {
        setupBasicPipeline()
        coEvery { topicDedupFilter.filter(any(), any(), any(), any()) } returns
            DedupFilterResult(listOf(FilteredArticle(scoredArticle, "Previously covered release")), TokenUsage(100, 50))
        coEvery { briefingComposer.compose(listOf(scoredArticle), podcast, composeModelDef, match { it.followUpAnnotations == mapOf(1L to "Previously covered release") }) } returns
            CompositionResult("Script with follow-up", TokenUsage(500, 200))

        runTest {
            val result = composeThroughStages()

            assertNotNull(result)
            coVerify { briefingComposer.compose(listOf(scoredArticle), podcast, composeModelDef, match { it.followUpAnnotations == mapOf(1L to "Previously covered release") }) }
        }
    }

    @Test
    fun `uses dialogueComposer for dialogue style podcast`() {
        val dialoguePodcast = podcast.copy(style = PodcastStyle.DIALOGUE, ttsProvider = TtsProviderType.ELEVENLABS, ttsVoices = mapOf("host" to "v1", "cohost" to "v2"))
        setupBasicPipeline(podcast = dialoguePodcast)
        coEvery { dialogueComposer.compose(any(), any(), any(), any()) } returns CompositionResult("<host>Hello!</host>", TokenUsage(500, 200))

        runTest {
            val result = composeThroughStages(dialoguePodcast)

            assertNotNull(result)
            coVerify { dialogueComposer.compose(any(), any(), any(), any()) }
            coVerify(exactly = 0) { briefingComposer.compose(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `uses interviewComposer for interview style podcast`() {
        val interviewPodcast = podcast.copy(style = PodcastStyle.INTERVIEW, ttsProvider = TtsProviderType.ELEVENLABS, ttsVoices = mapOf("interviewer" to "v1", "expert" to "v2"))
        setupBasicPipeline(podcast = interviewPodcast)
        coEvery { interviewComposer.compose(any(), any(), any(), any()) } returns CompositionResult("<interviewer>Q?</interviewer>", TokenUsage(500, 200))

        runTest {
            val result = composeThroughStages(interviewPodcast)

            assertNotNull(result)
            coVerify { interviewComposer.compose(any(), any(), any(), any()) }
            coVerify(exactly = 0) { briefingComposer.compose(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `each stage reports its own token usage`() {
        setupBasicPipeline()
        coEvery { topicDedupFilter.filter(any(), any(), any(), any()) } returns
            DedupFilterResult(listOf(FilteredArticle(scoredArticle)), TokenUsage(200, 100))
        coEvery { briefingComposer.compose(any(), any(), any(), any()) } returns
            CompositionResult("Script", TokenUsage(500, 300))

        runTest {
            val eligible = pipeline.aggregateScoreAndFilter(podcast, runConfig, window)
            val dedupResult = pipeline.dedup(eligible!!, podcast, runConfig)
            val composeResult = pipeline.compose(dedupResult!!.filteredArticles, podcast)

            assertEquals(200, dedupResult.usage.inputTokens)
            assertEquals(100, dedupResult.usage.outputTokens)
            assertEquals(500, composeResult.usage.inputTokens)
            assertEquals(300, composeResult.usage.outputTokens)
        }
    }

    @Test
    fun `does not mark articles as processed`() {
        setupBasicPipeline()
        coEvery { briefingComposer.compose(any(), any(), any(), any()) } returns CompositionResult("Script", TokenUsage(500, 200))

        runTest { composeThroughStages() }

        verify(exactly = 0) { articleRepository.save(match { it.isProcessed }) }
    }

    @Test
    fun `passes pronunciations to scriptGuidelines`() {
        val podcastWithPronunciations = podcast.copy(pronunciations = mapOf("Anthropic" to "/ænˈθɹɒpɪk/"))
        setupBasicPipeline(podcast = podcastWithPronunciations)
        coEvery { briefingComposer.compose(any(), any(), any(), any()) } returns CompositionResult("Script", TokenUsage(500, 200))

        runTest { composeThroughStages(podcastWithPronunciations) }

        verify { ttsProviderMock.scriptGuidelines(PodcastStyle.NEWS_BRIEFING, mapOf("Anthropic" to "/ænˈθɹɒpɪk/")) }
    }

    // --- Cost gate tests ---

    private val pricedFilterModel = ResolvedModel(
        provider = "openrouter", model = "gpt-4o-mini",
        cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 0.15, outputCostPerMtok = 0.60),
        stage = PipelineStage.FILTER
    )
    private val pricedComposeModel = ResolvedModel(
        provider = "openrouter", model = "claude-sonnet",
        cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 3.00, outputCostPerMtok = 15.00),
        stage = PipelineStage.COMPOSE
    )

    private fun articleWithBody(bodySize: Int) = Article(
        id = null, sourceId = "s1", title = "Test", body = "x".repeat(bodySize),
        url = "http://test.com", contentHash = "hash-$bodySize"
    )

    @Test
    fun `cost gate - above threshold skips pipeline`() {
        val lowThresholdProps = AppProperties(
            llm = LlmProperties(maxCostCents = 1),
            briefing = BriefingProperties(),
            episodes = EpisodesProperties(),
            feed = FeedProperties(),
            encryption = EncryptionProperties(masterKey = "test-key"),
            source = SourceProperties(maxArticleAgeDays = 7)
        )
        val pipelineWithLowThreshold = LlmPipeline(
            articleScoreSummarizer, briefingComposer, dialogueComposer, interviewComposer, modelResolver, articleRepository,
            sourceRepository, postRepository, sourceAggregator, lowThresholdProps, ttsProviderFactory,
            articleEligibilityService, topicDedupFilter, episodeWindowResolver, testRetryRegistry(),
        preComposeResearchService
        )

        val articles = (1..100).map { articleWithBody(10000) }

        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns pricedFilterModel
        every { modelResolver.resolve(any(), PipelineStage.COMPOSE) } returns pricedComposeModel
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns emptyList()
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns articles

        runTest {
            assertNull(pipelineWithLowThreshold.aggregateScoreAndFilter(podcast, runConfig, window))

            coVerify(exactly = 0) { articleScoreSummarizer.scoreSummarize(any(), any(), any(), any(), any()) }
        }
    }

    // --- Stage method tests ---

    @Test
    fun `aggregateScoreAndFilter returns eligible articles`() = runTest {
        setupBasicPipeline()
        coEvery { briefingComposer.compose(any(), any(), any(), any()) } returns CompositionResult("Script", TokenUsage(500, 200))

        val eligible = pipeline.aggregateScoreAndFilter(podcast, runConfig, window)

        assertNotNull(eligible)
        assertEquals(1, eligible!!.size)
        assertEquals(scoredArticle, eligible[0])
    }

    @Test
    fun `aggregateScoreAndFilter returns null when no eligible articles`() = runTest {
        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.DEDUP) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.COMPOSE) } returns composeModelDef
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns emptyList()
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns emptyList()
        every { articleEligibilityService.findEligibleArticles(listOf("s1"), podcast, any()) } returns emptyList()

        val eligible = pipeline.aggregateScoreAndFilter(podcast, runConfig, window)

        assertNull(eligible)
    }

    @Test
    fun `dedup returns filtered articles with topics`() {
        val filteredArticle = FilteredArticle(scoredArticle, topic = "AI Safety")
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.DEDUP) } returns filterModelDef
        every { articleEligibilityService.findHistory(podcast) } returns EpisodeHistory.EMPTY
        coEvery { topicDedupFilter.filter(listOf(scoredArticle), EpisodeHistory.EMPTY, "u1", filterModelDef) } returns
            DedupFilterResult(listOf(filteredArticle), TokenUsage(100, 50))

        runTest {
            val result = pipeline.dedup(listOf(scoredArticle), podcast, runConfig)

            assertNotNull(result)
            assertEquals(1, result!!.filteredArticles.size)
            assertEquals("AI Safety", result.filteredArticles[0].topic)
            assertEquals(listOf("AI Safety"), result.topicLabels)
            assertEquals(filterModelDef.model, result.filterModel)
        }
    }

    @Test
    fun `an episode is charged for every candidate it scored, not only the survivors`() = runTest {
        // Scoring runs against the full article body, and it runs because the article fell in this
        // episode's window. Costing the stage over the survivors attributes the rest to nothing.
        val kept = scored(1, input = 1000, output = 100, reportedUsd = 0.001)
        val dropped = scored(2, input = 4000, output = 400, reportedUsd = 0.004)
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.DEDUP) } returns filterModelDef
        every { articleEligibilityService.findHistory(podcast) } returns EpisodeHistory.EMPTY
        coEvery { topicDedupFilter.filter(any(), any(), any(), any(), any()) } returns DedupFilterResult(
            filteredArticles = listOf(FilteredArticle(kept)),
            usage = TokenUsage(100, 50),
            dropped = listOf(DroppedCandidate(2L, CandidateOutcome.DROPPED_AS_DUPLICATE))
        )

        val result = pipeline.dedup(listOf(kept, dropped), podcast, runConfig)!!

        assertEquals(5000, result.scoreInputTokens)
        assertEquals(500, result.scoreOutputTokens)
        // 0.005 USD is half a cent.
        assertEquals(0.5, result.scoreReportedCostCents)
    }

    @Test
    fun `every candidate is recorded with what became of it`() = runTest {
        val used = scored(1, relevance = 9)
        val cutByCap = scored(2, relevance = 1)
        val duplicate = scored(3)
        val gated = scored(4)
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.DEDUP) } returns filterModelDef
        every { articleEligibilityService.findHistory(podcast) } returns EpisodeHistory.EMPTY
        coEvery { topicDedupFilter.filter(any(), any(), any(), any(), any()) } returns DedupFilterResult(
            filteredArticles = listOf(FilteredArticle(used), FilteredArticle(cutByCap)),
            usage = TokenUsage(100, 50),
            dropped = listOf(
                DroppedCandidate(3L, CandidateOutcome.DROPPED_AS_DUPLICATE),
                DroppedCandidate(4L, CandidateOutcome.EXCLUDED_BY_GATE)
            )
        )
        val cappedPipeline = pipelineComposingAtMost(1)

        val result = cappedPipeline.dedup(listOf(used, cutByCap, duplicate, gated), podcast, runConfig)!!

        assertEquals(
            mapOf(
                1L to CandidateOutcome.USED,
                2L to CandidateOutcome.CUT_BY_COMPOSE_CAP,
                3L to CandidateOutcome.DROPPED_AS_DUPLICATE,
                4L to CandidateOutcome.EXCLUDED_BY_GATE
            ),
            result.candidates.associate { it.articleId to it.outcome }
        )
    }

    @Test
    fun `the gate is costed apart from the clustering call it relieves`() = runTest {
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.DEDUP) } returns filterModelDef
        every { articleEligibilityService.findHistory(podcast) } returns EpisodeHistory.EMPTY
        coEvery { topicDedupFilter.filter(any(), any(), any(), any(), any()) } returns DedupFilterResult(
            filteredArticles = listOf(FilteredArticle(scoredArticle)),
            usage = TokenUsage(29435, 3791, reportedCostUsd = 0.02),
            gate = DedupGateUsage(inputTokens = 900, requests = 2, reportedCostUsd = 0.0007)
        )

        val result = pipeline.dedup(listOf(scoredArticle), podcast, runConfig)!!

        // The clustering call alone, no longer carrying the gate's charge.
        assertEquals(2.0, result.dedupReportedCostCents)
        assertEquals(0.07, result.dedupGateReportedCostCents!!, 1e-9)
        assertEquals(900, result.dedupGateInputTokens)
        assertEquals(2, result.dedupGateCalls)
    }

    @Test
    fun `dedup returns null when all articles filtered`() {
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.DEDUP) } returns filterModelDef
        every { articleEligibilityService.findHistory(podcast) } returns EpisodeHistory.EMPTY
        coEvery { topicDedupFilter.filter(any(), any(), any(), any()) } returns
            DedupFilterResult(emptyList(), TokenUsage(100, 50))

        runTest {
            val result = pipeline.dedup(listOf(scoredArticle), podcast, runConfig)

            assertNull(result)
        }
    }

    @Test
    fun `dedup propagates exception when filter fails so the episode fails`() {
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(any(), PipelineStage.DEDUP) } returns filterModelDef
        every { articleEligibilityService.findHistory(podcast) } returns EpisodeHistory.EMPTY
        coEvery { topicDedupFilter.filter(any(), any(), any(), any()) } throws
            IllegalStateException("No content to map due to end-of-input")

        assertThrows(IllegalStateException::class.java) {
            runTest { pipeline.dedup(listOf(scoredArticle), podcast, runConfig) }
        }
    }

    @Test
    fun `compose returns script with topic order`() = runTest {
        val filteredArticle = FilteredArticle(scoredArticle, topic = "AI Safety")
        every { modelResolver.resolve(any(), PipelineStage.COMPOSE) } returns composeModelDef
        coEvery { briefingComposer.compose(listOf(scoredArticle), podcast, composeModelDef, match { it.topicLabels == listOf("AI Safety") }) } returns
            CompositionResult("Today in tech...", TokenUsage(500, 200), listOf("AI Safety"))

        val result = pipeline.compose(listOf(filteredArticle), podcast, ComposeContext(topicLabels = listOf("AI Safety")))

        assertEquals("Today in tech...", result.script)
        assertEquals(composeModelDef.model, result.composeModel)
        assertEquals(listOf("AI Safety"), result.topicOrder)
    }

    @Test
    fun `compose researches the focus and the clusters first and hands the result to the composer`() = runTest {
        val filteredArticle = FilteredArticle(scoredArticle, topic = "AI Safety")
        val research = PreComposeResearch(
            sources = listOf(com.aisummarypodcast.research.BackgroundSource("q", "t", "https://x.com", "s")),
            researchCalls = 2
        )
        val request = slot<com.aisummarypodcast.research.ResearchRequest>()
        coEvery { preComposeResearchService.research(capture(request)) } returns research
        every { modelResolver.resolve(any(), PipelineStage.COMPOSE) } returns composeModelDef
        coEvery { briefingComposer.compose(any(), podcast, composeModelDef, match { it.research == research }) } returns
            CompositionResult("Script", TokenUsage(500, 200))

        val result = pipeline.compose(
            listOf(filteredArticle), podcast,
            ComposeContext(topicLabels = listOf("AI Safety"), focus = "Opus", episodeId = 12)
        )

        assertEquals(listOf("Opus", "AI Safety"), request.captured.subjects)
        assertTrue(request.captured.focusEpisode)
        assertEquals(12L, request.captured.episodeId)
        assertEquals(2, result.researchCalls)
        assertEquals(2 * appProperties.research.tavily.costPerCallCents, result.researchCostCents)
    }

    @Test
    fun `compose falls back to the article titles when there are no clusters`() = runTest {
        val request = slot<com.aisummarypodcast.research.ResearchRequest>()
        coEvery { preComposeResearchService.research(capture(request)) } returns PreComposeResearch.NONE
        every { modelResolver.resolve(any(), PipelineStage.COMPOSE) } returns composeModelDef
        coEvery { briefingComposer.compose(any(), podcast, composeModelDef, any()) } returns
            CompositionResult("Script", TokenUsage(500, 200))

        val result = pipeline.compose(listOf(FilteredArticle(scoredArticle)), podcast)

        assertEquals(listOf("AI News"), request.captured.subjects)
        assertEquals(0, result.researchCalls)
        assertNull(result.researchCostCents)
    }

    // --- scoreReadySources (eager ranking) tests ---

    @Test
    fun `scoreReadySources aggregates and scores non-aggregate sources`() = runTest {
        val unlinkedPost = Post(
            id = 1, sourceId = "s1", title = "AI News", body = "Post body",
            url = "https://example.com/ai", contentHash = "hash1", createdAt = "2026-02-16T10:00:00Z"
        )
        val createdArticle = Article(
            id = 1, sourceId = "s1", title = "AI News", body = "Post body",
            url = "https://example.com/ai", contentHash = "arthash1"
        )
        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { sourceAggregator.shouldAggregate(source) } returns false
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns listOf(unlinkedPost)
        every { sourceAggregator.aggregateAndPersist(listOf(unlinkedPost), source) } returns listOf(createdArticle)
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns listOf(createdArticle)
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        coEvery { articleScoreSummarizer.scoreSummarize(listOf(createdArticle), podcast, filterModelDef, ScoringContext(mapOf("s1" to "example.com/feed"))) } returns
            listOf(createdArticle.copy(relevanceScore = 7))

        pipeline.scoreReadySources(podcast)

        verify { sourceAggregator.aggregateAndPersist(listOf(unlinkedPost), source) }
        coVerify { articleScoreSummarizer.scoreSummarize(listOf(createdArticle), podcast, filterModelDef, ScoringContext(mapOf("s1" to "example.com/feed"))) }
    }

    @Test
    fun `scoreReadySources skips aggregate sources`() = runTest {
        val twitterSource = Source(id = "s2", podcastId = "p1", type = SourceType.TWITTER, url = "https://nitter.net/foo")
        every { sourceRepository.findByPodcastId("p1") } returns listOf(twitterSource)
        every { sourceAggregator.shouldAggregate(twitterSource) } returns true

        pipeline.scoreReadySources(podcast)

        verify(exactly = 0) { postRepository.findUnlinkedBySourceIds(any(), any()) }
        coVerify(exactly = 0) { articleScoreSummarizer.scoreSummarize(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `scoreReadySources is a no-op when no unscored ready articles`() = runTest {
        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { sourceAggregator.shouldAggregate(source) } returns false
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns emptyList()
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns emptyList()

        pipeline.scoreReadySources(podcast)

        verify(exactly = 0) { modelResolver.resolve(any(), PipelineStage.FILTER) }
        coVerify(exactly = 0) { articleScoreSummarizer.scoreSummarize(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `scoreReadySources skips scoring when cost gate exceeded`() = runTest {
        val lowThresholdProps = AppProperties(
            llm = LlmProperties(maxCostCents = 1),
            briefing = BriefingProperties(),
            episodes = EpisodesProperties(),
            feed = FeedProperties(),
            encryption = EncryptionProperties(masterKey = "test-key"),
            source = SourceProperties(maxArticleAgeDays = 7)
        )
        val pipelineWithLowThreshold = LlmPipeline(
            articleScoreSummarizer, briefingComposer, dialogueComposer, interviewComposer, modelResolver, articleRepository,
            sourceRepository, postRepository, sourceAggregator, lowThresholdProps, ttsProviderFactory,
            articleEligibilityService, topicDedupFilter, episodeWindowResolver, testRetryRegistry(),
        preComposeResearchService
        )
        val articles = (1..100).map { articleWithBody(10000) }

        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { sourceAggregator.shouldAggregate(source) } returns false
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns emptyList()
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns articles
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns pricedFilterModel

        pipelineWithLowThreshold.scoreReadySources(podcast)

        coVerify(exactly = 0) { articleScoreSummarizer.scoreSummarize(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `recompose does not pass recaps to composer`() = runTest {
        val article = scoredArticle
        every { modelResolver.resolve(any(), PipelineStage.COMPOSE) } returns composeModelDef
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        coEvery { briefingComposer.compose(listOf(article), podcast, composeModelDef, any()) } returns
            CompositionResult("Recomposed script", TokenUsage(500, 200))

        val result = pipeline.recompose(listOf(article), podcast)

        assertNotNull(result)
        coVerify { briefingComposer.compose(listOf(article), podcast, composeModelDef, any()) }
    }

    // --- Compose retry -------------------------------------------------------------------------

    /** Same wiring as [pipeline], but with the application's real `compose` retry policy. */
    private val retryingPipeline = LlmPipeline(
        articleScoreSummarizer, briefingComposer, dialogueComposer, interviewComposer, modelResolver,
        articleRepository, sourceRepository, postRepository, sourceAggregator, appProperties,
        ttsProviderFactory, articleEligibilityService, topicDedupFilter, episodeWindowResolver,
        testComposeRetryRegistry(), preComposeResearchService
    )

    @Test
    fun `a transient provider fault is retried rather than failing the episode`() {
        setupBasicPipeline()
        var attempts = 0
        coEvery { briefingComposer.compose(any(), any(), any(), any()) } answers {
            attempts++
            // What episode 197 hit: the provider returned a completion with no finish_reason.
            if (attempts == 1) throw com.openai.errors.OpenAIInvalidDataException("`finish_reason` is null")
            CompositionResult("Script", TokenUsage(500, 200))
        }

        var result: ComposeStageResult? = null
        runTest { result = composeThroughStages(pipeline = retryingPipeline) }

        assertEquals(2, attempts)
        assertEquals("Script", result?.script)
    }

    @Test
    fun `a speaker-tag failure is not retried by the compose retry`() {
        setupBasicPipeline()
        var attempts = 0
        coEvery { briefingComposer.compose(any(), any(), any(), any()) } answers {
            attempts++
            // RoleTagValidationAdvisor has already exhausted its own attempts inside the call.
            throw IllegalStateException("Compose LLM produced a script with no speaker tags")
        }

        assertThrows(IllegalStateException::class.java) {
            runTest { composeThroughStages(pipeline = retryingPipeline) }
        }

        assertEquals(1, attempts)
    }

    private fun stubFocusCandidates(vararg focusScores: Int) {
        val candidates = focusScores.indices.map { scored(it + 1L, relevance = 2) }
        every { sourceRepository.findByPodcastId(podcast.id) } returns listOf(source)
        every { modelResolver.resolve(any(), PipelineStage.FILTER) } returns filterModelDef
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns emptyList()
        every { articleEligibilityService.findEligibleArticlesForFocus(listOf("s1"), podcast, window) } returns candidates
        coEvery { articleScoreSummarizer.scoreForFocus(candidates, "Claude Opus 5.5 release", podcast, filterModelDef, any(), any()) } returns
            candidates.mapIndexed { i, a -> FocusScoredArticle(a.copy(relevanceScore = focusScores[i]), TokenUsage(100, 20)) }
    }

    @Test
    fun `focus selection keeps only articles relevant to the focus`() = runTest {
        stubFocusCandidates(9, 2, 6)

        val selection = pipeline.selectForFocus(podcast, runConfig, window, "Claude Opus 5.5 release")

        assertEquals(setOf(1L, 3L), selection.articles.map { it.article.id }.toSet())
        assertEquals(300, selection.scoreInputTokens)
        coVerify(exactly = 0) { articleScoreSummarizer.scoreSummarize(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `focus selection with no relevant article fails naming the focus`() = runTest {
        stubFocusCandidates(1, 2)

        val error = assertThrows(NoFocusRelevantArticlesException::class.java) {
            kotlinx.coroutines.runBlocking { pipeline.selectForFocus(podcast, runConfig, window, "Claude Opus 5.5 release") }
        }

        assertTrue(error.message!!.contains("Claude Opus 5.5 release"))
        assertTrue(error.message!!.contains("No relevant articles"))
    }
}
