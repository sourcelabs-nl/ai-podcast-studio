package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelType
import com.aisummarypodcast.config.SourceProperties
import com.aisummarypodcast.source.SourceAggregator
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
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
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

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

    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key"),
        source = SourceProperties(maxArticleAgeDays = 7)
    )

    private val filterModelDef = ResolvedModel(provider = "openrouter", model = "anthropic/claude-haiku-4.5", cost = null)
    private val composeModelDef = ResolvedModel(provider = "openrouter", model = "anthropic/claude-sonnet-4", cost = null)

    private val pipeline = LlmPipeline(
        articleScoreSummarizer, briefingComposer, dialogueComposer, interviewComposer, modelResolver, articleRepository,
        sourceRepository, postRepository, sourceAggregator, appProperties, ttsProviderFactory,
        articleEligibilityService, topicDedupFilter
    )

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Tech Daily", topic = "tech", relevanceThreshold = 5)
    private val source = Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed")

    private val scoredArticle = Article(
        id = 1, sourceId = "s1", title = "AI News", body = "Body",
        url = "https://example.com/ai", contentHash = "hash1", relevanceScore = 8, summary = "Summary."
    )

    private fun setupBasicPipeline(articles: List<Article> = listOf(scoredArticle), podcast: Podcast = this.podcast) {
        every { sourceRepository.findByPodcastId(podcast.id) } returns listOf(source)
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(podcast, PipelineStage.DEDUP) } returns filterModelDef
        every { modelResolver.resolve(podcast, PipelineStage.COMPOSE) } returns composeModelDef
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns emptyList()
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns emptyList()
        every { articleEligibilityService.findEligibleArticles(listOf("s1"), podcast) } returns articles
        every { articleEligibilityService.findHistoricalArticles(podcast) } returns emptyList()
        coEvery { topicDedupFilter.filter(articles, emptyList(), podcast.userId, filterModelDef) } returns
            DedupFilterResult(articles.map { FilteredArticle(it) }, TokenUsage(100, 50))
    }

    @Test
    fun `returns null when podcast has no sources`() {
        every { sourceRepository.findByPodcastId("p1") } returns emptyList()

        runTest {
            val result = pipeline.run(podcast)

            assertNull(result)
        }
    }

    @Test
    fun `returns null when no eligible articles exist`() {
        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(podcast, PipelineStage.DEDUP) } returns filterModelDef
        every { modelResolver.resolve(podcast, PipelineStage.COMPOSE) } returns composeModelDef
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns emptyList()
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns emptyList()
        every { articleEligibilityService.findEligibleArticles(listOf("s1"), podcast) } returns emptyList()

        runTest {
            val result = pipeline.run(podcast)

            assertNull(result)
        }
    }

    @Test
    fun `returns null when all articles filtered as duplicates`() {
        setupBasicPipeline()
        coEvery { topicDedupFilter.filter(any(), any(), any(), any()) } returns
            DedupFilterResult(emptyList(), TokenUsage(100, 50))

        runTest {
            val result = pipeline.run(podcast)

            assertNull(result)
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
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(podcast, PipelineStage.DEDUP) } returns filterModelDef
        every { modelResolver.resolve(podcast, PipelineStage.COMPOSE) } returns composeModelDef
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns listOf(unlinkedPost)
        every { sourceAggregator.aggregateAndPersist(listOf(unlinkedPost), source) } returns listOf(createdArticle)
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns listOf(createdArticle)
        coEvery { articleScoreSummarizer.scoreSummarize(listOf(createdArticle), podcast, filterModelDef, mapOf("s1" to "example.com/feed"), any()) } returns listOf(scored)
        every { articleEligibilityService.findEligibleArticles(listOf("s1"), podcast) } returns listOf(scored)
        every { articleEligibilityService.findHistoricalArticles(podcast) } returns emptyList()
        coEvery { topicDedupFilter.filter(listOf(scored), emptyList(), "u1", filterModelDef) } returns
            DedupFilterResult(listOf(FilteredArticle(scored)), TokenUsage(100, 50))
        coEvery { briefingComposer.compose(listOf(scored), podcast, composeModelDef, "", emptyMap()) } returns compositionResult

        runTest {
            val result = pipeline.run(podcast)

            assertNotNull(result)
            assertEquals("Today in tech...", result!!.script)

            verify { sourceAggregator.aggregateAndPersist(listOf(unlinkedPost), source) }
            coVerify { articleScoreSummarizer.scoreSummarize(listOf(createdArticle), podcast, filterModelDef, mapOf("s1" to "example.com/feed"), any()) }
        }
    }

    @Test
    fun `delegates article selection to ArticleEligibilityService`() {
        setupBasicPipeline()
        coEvery { briefingComposer.compose(any(), any(), any(), any(), any<Map<Long, String>>()) } returns CompositionResult("Script", TokenUsage(500, 200))

        runTest { pipeline.run(podcast) }

        verify { articleEligibilityService.findEligibleArticles(listOf("s1"), podcast) }
        verify { articleEligibilityService.findHistoricalArticles(podcast) }
    }

    @Test
    fun `calls dedup filter with eligible and historical articles`() {
        val historical = listOf(Article(id = 99, sourceId = "s1", title = "Old", body = "old", url = "http://old.com", contentHash = "h99"))
        setupBasicPipeline()
        every { articleEligibilityService.findHistoricalArticles(podcast) } returns historical
        coEvery { topicDedupFilter.filter(listOf(scoredArticle), historical, "u1", filterModelDef) } returns
            DedupFilterResult(listOf(FilteredArticle(scoredArticle)), TokenUsage(100, 50))
        coEvery { briefingComposer.compose(any(), any(), any(), any(), any<Map<Long, String>>()) } returns CompositionResult("Script", TokenUsage(500, 200))

        runTest { pipeline.run(podcast) }

        coVerify { topicDedupFilter.filter(listOf(scoredArticle), historical, "u1", filterModelDef) }
    }

    @Test
    fun `passes follow-up annotations from dedup filter to composer`() {
        setupBasicPipeline()
        coEvery { topicDedupFilter.filter(any(), any(), any(), any()) } returns
            DedupFilterResult(listOf(FilteredArticle(scoredArticle, "Previously covered release")), TokenUsage(100, 50))
        coEvery { briefingComposer.compose(listOf(scoredArticle), podcast, composeModelDef, "", mapOf(1L to "Previously covered release")) } returns
            CompositionResult("Script with follow-up", TokenUsage(500, 200))

        runTest {
            val result = pipeline.run(podcast)

            assertNotNull(result)
            coVerify { briefingComposer.compose(listOf(scoredArticle), podcast, composeModelDef, "", mapOf(1L to "Previously covered release")) }
        }
    }

    @Test
    fun `uses dialogueComposer for dialogue style podcast`() {
        val dialoguePodcast = podcast.copy(style = PodcastStyle.DIALOGUE, ttsProvider = TtsProviderType.ELEVENLABS, ttsVoices = mapOf("host" to "v1", "cohost" to "v2"))
        setupBasicPipeline(podcast = dialoguePodcast)
        coEvery { dialogueComposer.compose(any(), any(), any(), any(), any<Map<Long, String>>()) } returns CompositionResult("<host>Hello!</host>", TokenUsage(500, 200))

        runTest {
            val result = pipeline.run(dialoguePodcast)

            assertNotNull(result)
            coVerify { dialogueComposer.compose(any(), any(), any(), any(), any<Map<Long, String>>()) }
            coVerify(exactly = 0) { briefingComposer.compose(any(), any(), any(), any(), any<Map<Long, String>>()) }
        }
    }

    @Test
    fun `uses interviewComposer for interview style podcast`() {
        val interviewPodcast = podcast.copy(style = PodcastStyle.INTERVIEW, ttsProvider = TtsProviderType.ELEVENLABS, ttsVoices = mapOf("interviewer" to "v1", "expert" to "v2"))
        setupBasicPipeline(podcast = interviewPodcast)
        coEvery { interviewComposer.compose(any(), any(), any(), any(), any<Map<Long, String>>()) } returns CompositionResult("<interviewer>Q?</interviewer>", TokenUsage(500, 200))

        runTest {
            val result = pipeline.run(interviewPodcast)

            assertNotNull(result)
            coVerify { interviewComposer.compose(any(), any(), any(), any(), any<Map<Long, String>>()) }
            coVerify(exactly = 0) { briefingComposer.compose(any(), any(), any(), any(), any<Map<Long, String>>()) }
        }
    }

    @Test
    fun `includes dedup filter token usage in pipeline result`() {
        setupBasicPipeline()
        coEvery { topicDedupFilter.filter(any(), any(), any(), any()) } returns
            DedupFilterResult(listOf(FilteredArticle(scoredArticle)), TokenUsage(200, 100))
        coEvery { briefingComposer.compose(any(), any(), any(), any(), any<Map<Long, String>>()) } returns
            CompositionResult("Script", TokenUsage(500, 300))

        runTest {
            val result = pipeline.run(podcast)

            assertNotNull(result)
            assertEquals(700, result!!.llmInputTokens)
            assertEquals(400, result.llmOutputTokens)
        }
    }

    @Test
    fun `does not mark articles as processed`() {
        setupBasicPipeline()
        coEvery { briefingComposer.compose(any(), any(), any(), any(), any<Map<Long, String>>()) } returns CompositionResult("Script", TokenUsage(500, 200))

        runTest { pipeline.run(podcast) }

        verify(exactly = 0) { articleRepository.save(match { it.isProcessed }) }
    }

    @Test
    fun `passes pronunciations to scriptGuidelines`() {
        val podcastWithPronunciations = podcast.copy(pronunciations = mapOf("Anthropic" to "/ænˈθɹɒpɪk/"))
        setupBasicPipeline(podcast = podcastWithPronunciations)
        coEvery { briefingComposer.compose(any(), any(), any(), any(), any<Map<Long, String>>()) } returns CompositionResult("Script", TokenUsage(500, 200))

        runTest { pipeline.run(podcastWithPronunciations) }

        verify { ttsProviderMock.scriptGuidelines(PodcastStyle.NEWS_BRIEFING, mapOf("Anthropic" to "/ænˈθɹɒpɪk/")) }
    }

    // --- Cost gate tests ---

    private val pricedFilterModel = ResolvedModel(
        provider = "openrouter", model = "gpt-4o-mini",
        cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 0.15, outputCostPerMtok = 0.60)
    )
    private val pricedComposeModel = ResolvedModel(
        provider = "openrouter", model = "claude-sonnet",
        cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 3.00, outputCostPerMtok = 15.00)
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
            articleEligibilityService, topicDedupFilter
        )

        val articles = (1..100).map { articleWithBody(10000) }

        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns pricedFilterModel
        every { modelResolver.resolve(podcast, PipelineStage.COMPOSE) } returns pricedComposeModel
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns emptyList()
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns articles

        runTest {
            val result = pipelineWithLowThreshold.run(podcast)

            assertNull(result)
            coVerify(exactly = 0) { articleScoreSummarizer.scoreSummarize(any(), any(), any(), any(), any()) }
        }
    }

    // --- Stage method tests ---

    @Test
    fun `aggregateScoreAndFilter returns eligible articles`() = runTest {
        setupBasicPipeline()
        coEvery { briefingComposer.compose(any(), any(), any(), any(), any<Map<Long, String>>()) } returns CompositionResult("Script", TokenUsage(500, 200))

        val eligible = pipeline.aggregateScoreAndFilter(podcast)

        assertNotNull(eligible)
        assertEquals(1, eligible!!.size)
        assertEquals(scoredArticle, eligible[0])
    }

    @Test
    fun `aggregateScoreAndFilter returns null when no eligible articles`() = runTest {
        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(podcast, PipelineStage.DEDUP) } returns filterModelDef
        every { modelResolver.resolve(podcast, PipelineStage.COMPOSE) } returns composeModelDef
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns emptyList()
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns emptyList()
        every { articleEligibilityService.findEligibleArticles(listOf("s1"), podcast) } returns emptyList()

        val eligible = pipeline.aggregateScoreAndFilter(podcast)

        assertNull(eligible)
    }

    @Test
    fun `dedup returns filtered articles with topics`() {
        val filteredArticle = FilteredArticle(scoredArticle, topic = "AI Safety")
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(podcast, PipelineStage.DEDUP) } returns filterModelDef
        every { articleEligibilityService.findHistoricalArticles(podcast) } returns emptyList()
        coEvery { topicDedupFilter.filter(listOf(scoredArticle), emptyList(), "u1", filterModelDef) } returns
            DedupFilterResult(listOf(filteredArticle), TokenUsage(100, 50))

        runTest {
            val result = pipeline.dedup(listOf(scoredArticle), podcast)

            assertNotNull(result)
            assertEquals(1, result!!.filteredArticles.size)
            assertEquals("AI Safety", result.filteredArticles[0].topic)
            assertEquals(listOf("AI Safety"), result.topicLabels)
            assertEquals(filterModelDef.model, result.filterModel)
        }
    }

    @Test
    fun `dedup returns null when all articles filtered`() {
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(podcast, PipelineStage.DEDUP) } returns filterModelDef
        every { articleEligibilityService.findHistoricalArticles(podcast) } returns emptyList()
        coEvery { topicDedupFilter.filter(any(), any(), any(), any()) } returns
            DedupFilterResult(emptyList(), TokenUsage(100, 50))

        runTest {
            val result = pipeline.dedup(listOf(scoredArticle), podcast)

            assertNull(result)
        }
    }

    @Test
    fun `dedup propagates exception when filter fails so the episode fails`() {
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns filterModelDef
        every { modelResolver.resolve(podcast, PipelineStage.DEDUP) } returns filterModelDef
        every { articleEligibilityService.findHistoricalArticles(podcast) } returns emptyList()
        coEvery { topicDedupFilter.filter(any(), any(), any(), any()) } throws
            IllegalStateException("No content to map due to end-of-input")

        assertThrows(IllegalStateException::class.java) {
            runTest { pipeline.dedup(listOf(scoredArticle), podcast) }
        }
    }

    @Test
    fun `compose returns script with topic order`() = runTest {
        val filteredArticle = FilteredArticle(scoredArticle, topic = "AI Safety")
        every { modelResolver.resolve(podcast, PipelineStage.COMPOSE) } returns composeModelDef
        coEvery { briefingComposer.compose(listOf(scoredArticle), podcast, composeModelDef, "", emptyMap(), listOf("AI Safety")) } returns
            CompositionResult("Today in tech...", TokenUsage(500, 200), listOf("AI Safety"))

        val result = pipeline.compose(listOf(filteredArticle), podcast, topicLabels = listOf("AI Safety"))

        assertEquals("Today in tech...", result.script)
        assertEquals(composeModelDef.model, result.composeModel)
        assertEquals(listOf("AI Safety"), result.topicOrder)
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
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns filterModelDef
        coEvery { articleScoreSummarizer.scoreSummarize(listOf(createdArticle), podcast, filterModelDef, mapOf("s1" to "example.com/feed"), any()) } returns
            listOf(createdArticle.copy(relevanceScore = 7))

        pipeline.scoreReadySources(podcast)

        verify { sourceAggregator.aggregateAndPersist(listOf(unlinkedPost), source) }
        coVerify { articleScoreSummarizer.scoreSummarize(listOf(createdArticle), podcast, filterModelDef, mapOf("s1" to "example.com/feed"), any()) }
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

        verify(exactly = 0) { modelResolver.resolve(podcast, PipelineStage.FILTER) }
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
            articleEligibilityService, topicDedupFilter
        )
        val articles = (1..100).map { articleWithBody(10000) }

        every { sourceRepository.findByPodcastId("p1") } returns listOf(source)
        every { sourceAggregator.shouldAggregate(source) } returns false
        every { postRepository.findUnlinkedBySourceIds(listOf("s1"), any()) } returns emptyList()
        every { articleRepository.findUnscoredBySourceIds(listOf("s1")) } returns articles
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns pricedFilterModel

        pipelineWithLowThreshold.scoreReadySources(podcast)

        coVerify(exactly = 0) { articleScoreSummarizer.scoreSummarize(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `recompose does not pass recaps to composer`() = runTest {
        val article = scoredArticle
        every { modelResolver.resolve(podcast, PipelineStage.COMPOSE) } returns composeModelDef
        every { modelResolver.resolve(podcast, PipelineStage.FILTER) } returns filterModelDef
        coEvery { briefingComposer.compose(listOf(article), podcast, composeModelDef, "", emptyMap()) } returns
            CompositionResult("Recomposed script", TokenUsage(500, 200))

        val result = pipeline.recompose(listOf(article), podcast)

        assertNotNull(result)
        coVerify { briefingComposer.compose(listOf(article), podcast, composeModelDef, "", emptyMap()) }
    }
}
