package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.testRetryRegistry
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelType
import com.aisummarypodcast.config.ScoringProperties
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.Subtopics
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.ResponseEntity
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.converter.StructuredOutputConverter
import tools.jackson.databind.json.JsonMapper

class ArticleScoreSummarizerTest {

    private val articleRepository = mockk<ArticleRepository> {
        every { save(any()) } answers { firstArg() }
    }
    private val chatClientFactory = mockk<ChatClientFactory>()
    private val chatClient = mockk<ChatClient>()
    private val jsonMapper: JsonMapper = JsonMapper.builder().build()

    private val filterModelDef = ResolvedModel(
        provider = "openrouter", model = "test-model",
        cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 0.15, outputCostPerMtok = 0.60)
    )

    private fun appProperties(scoring: ScoringProperties = ScoringProperties(concurrency = 10)): AppProperties =
        AppProperties(
            llm = LlmProperties(scoring = scoring),
            briefing = BriefingProperties(),
            episodes = EpisodesProperties(),
            feed = FeedProperties(),
            encryption = EncryptionProperties(masterKey = "test-key")
        )

    private val scoreSummarizer = ArticleScoreSummarizer(
        articleRepository, chatClientFactory, jsonMapper, testRetryRegistry(), appProperties()
    )

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Tech Daily", topic = "AI engineering")

    private fun mockLlmResponse(result: ScoreSummarizeResult, inputTokens: Int = 500, outputTokens: Int = 80) {
        val metadata = ChatResponseMetadata.builder()
            .usage(DefaultUsage(inputTokens, outputTokens))
            .build()
        val chatResponse = ChatResponse(listOf(Generation(AssistantMessage("{}"))), metadata)
        val responseEntity = ResponseEntity(chatResponse, result)

        val callResponseSpec = mockk<ChatClient.CallResponseSpec>()
        every { callResponseSpec.responseEntity(any<StructuredOutputConverter<ScoreSummarizeResult>>()) } returns responseEntity

        val chatClientRequestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        every { chatClientRequestSpec.user(any<String>()) } returns chatClientRequestSpec
        every { chatClientRequestSpec.options(any()) } returns chatClientRequestSpec
        every { chatClientRequestSpec.call() } returns callResponseSpec

        every { chatClient.prompt() } returns chatClientRequestSpec
        every { chatClientFactory.createForModel(podcast.userId, filterModelDef) } returns chatClient
    }

    @Test
    fun `article receives relevance score and summary`() = runTest {
        val article = Article(
            id = 1, sourceId = "s1", title = "GPT-5 Released", body = "OpenAI released GPT-5 today.",
            url = "https://example.com/1", contentHash = "hash1"
        )
        mockLlmResponse(ScoreSummarizeResult(relevanceScore = 8, summary = "OpenAI launched GPT-5, a major AI milestone."))

        val result = scoreSummarizer.scoreSummarize(listOf(article), podcast, filterModelDef)

        assertEquals(1, result.size)
        assertEquals(8, result[0].relevanceScore)
        assertEquals("OpenAI launched GPT-5, a major AI milestone.", result[0].summary)

        val saved = slot<Article>()
        verify { articleRepository.save(capture(saved)) }
        assertEquals(8, saved.captured.relevanceScore)
        assertNotNull(saved.captured.summary)
    }

    @Test
    fun `irrelevant article receives low score and null summary`() = runTest {
        val article = Article(
            id = 2, sourceId = "s1", title = "Best Pizza Recipes", body = "Here are the best pizza recipes.",
            url = "https://example.com/2", contentHash = "hash2"
        )
        mockLlmResponse(ScoreSummarizeResult(relevanceScore = 1, summary = ""))

        val result = scoreSummarizer.scoreSummarize(listOf(article), podcast, filterModelDef)

        assertEquals(1, result.size)
        assertEquals(1, result[0].relevanceScore)
        assertEquals(null, result[0].summary) // blank summary is stored as null
    }

    @Test
    fun `LLM error returns empty list and does not save`() = runTest {
        val article = Article(
            id = 3, sourceId = "s1", title = "Some Article", body = "body",
            url = "https://example.com/3", contentHash = "hash3"
        )

        val chatClientRequestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        every { chatClientRequestSpec.user(any<String>()) } returns chatClientRequestSpec
        every { chatClientRequestSpec.options(any()) } returns chatClientRequestSpec
        every { chatClientRequestSpec.call() } throws RuntimeException("LLM unavailable")
        every { chatClient.prompt() } returns chatClientRequestSpec
        every { chatClientFactory.createForModel(podcast.userId, filterModelDef) } returns chatClient

        val result = scoreSummarizer.scoreSummarize(listOf(article), podcast, filterModelDef)

        assertTrue(result.isEmpty())
        verify(exactly = 0) { articleRepository.save(any()) }
    }

    @Test
    fun `token counts and cost are persisted on article`() = runTest {
        val article = Article(
            id = 5, sourceId = "s1", title = "AI News", body = "AI content",
            url = "https://example.com/5", contentHash = "hash5"
        )
        mockLlmResponse(ScoreSummarizeResult(relevanceScore = 7, summary = "Summary of AI news."), inputTokens = 800, outputTokens = 120)

        scoreSummarizer.scoreSummarize(listOf(article), podcast, filterModelDef)

        val saved = slot<Article>()
        verify { articleRepository.save(capture(saved)) }
        assertEquals(800, saved.captured.llmInputTokens)
        assertEquals(120, saved.captured.llmOutputTokens)
    }

    @Test
    fun `attribution preserved in summary prompt`() = runTest {
        val article = Article(
            id = 6, sourceId = "s1", title = "MIT Study", body = "Researchers at MIT published a study showing AI advances.",
            url = "https://example.com/6", contentHash = "hash6"
        )
        mockLlmResponse(ScoreSummarizeResult(relevanceScore = 9, summary = "MIT researchers found significant AI advances."))

        val result = scoreSummarizer.scoreSummarize(listOf(article), podcast, filterModelDef)

        assertEquals("MIT researchers found significant AI advances.", result[0].summary)
    }

    @Test
    fun `aggregated article prompt includes social media post context and author name`() {
        val article = Article(
            id = 7, sourceId = "s1", title = "Posts from @rauchg — Feb 15, 2026",
            body = "Post content here", url = "https://nitter.net/rauchg/rss",
            contentHash = "hash7", author = "@rauchg"
        )

        val prompt = scoreSummarizer.buildPrompt(article, podcast)

        assertTrue(prompt.contains("multiple social media posts by @rauchg"))
        assertTrue(prompt.contains("Post content here"))
        assertTrue(!prompt.contains("Content title:"))
    }

    @Test
    fun `non-aggregated article uses neutral content framing`() {
        val article = Article(
            id = 8, sourceId = "s1", title = "New AI Breakthrough",
            body = "Details about the breakthrough.", url = "https://example.com/8",
            contentHash = "hash8", author = "John Smith"
        )

        val prompt = scoreSummarizer.buildPrompt(article, podcast)

        assertTrue(prompt.contains("Content title: New AI Breakthrough"))
        assertTrue(prompt.contains("Content author: John Smith"))
        assertTrue(prompt.contains("Content: Details about the breakthrough."))
        assertTrue(!prompt.contains("social media posts"))
    }

    @Test
    fun `prompt works correctly when author is null`() {
        val article = Article(
            id = 9, sourceId = "s1", title = "Some News",
            body = "News content.", url = "https://example.com/9",
            contentHash = "hash9", author = null
        )

        val prompt = scoreSummarizer.buildPrompt(article, podcast)

        assertTrue(prompt.contains("Content title: Some News"))
        assertTrue(!prompt.contains("Content author:"))
        assertTrue(prompt.contains("Content: News content."))
    }

    @Test
    fun `prompt includes direct summarization instruction with negative example`() {
        val article = Article(
            id = 10, sourceId = "s1", title = "Test Article",
            body = "Test body.", url = "https://example.com/10",
            contentHash = "hash10"
        )

        val prompt = scoreSummarizer.buildPrompt(article, podcast)

        assertTrue(prompt.contains("say \"Anthropic launched X\" not \"The article discusses Anthropic launching X\""))
    }

    @Test
    fun `short article prompt requests 2-3 sentence summary`() {
        val article = Article(
            id = 11, sourceId = "s1", title = "Short Post",
            body = "A brief update on AI.", url = "https://example.com/11",
            contentHash = "hash11"
        )

        val prompt = scoreSummarizer.buildPrompt(article, podcast)

        assertTrue(prompt.contains("2-3 sentences"))
        assertFalse(prompt.contains("4-6 sentences"))
        assertFalse(prompt.contains("full paragraph"))
    }

    @Test
    fun `medium article prompt requests 4-6 sentence summary`() {
        val body = (1..100).joinToString(" ") { "word$it" } // ~100 words repeated to get 500+
        val mediumBody = (1..6).joinToString(" ") { body } // ~600 words

        val article = Article(
            id = 12, sourceId = "s1", title = "Medium Article",
            body = mediumBody, url = "https://example.com/12",
            contentHash = "hash12"
        )

        val prompt = scoreSummarizer.buildPrompt(article, podcast)

        assertTrue(prompt.contains("4-6 sentences"))
        assertFalse(prompt.contains("2-3 sentences"))
        assertFalse(prompt.contains("full paragraph"))
    }

    @Test
    fun `long article prompt requests full paragraph summary`() {
        val body = (1..1600).joinToString(" ") { "word$it" } // 1600 words

        val article = Article(
            id = 13, sourceId = "s1", title = "Long Article",
            body = body, url = "https://example.com/13",
            contentHash = "hash13"
        )

        val prompt = scoreSummarizer.buildPrompt(article, podcast)

        assertTrue(prompt.contains("full paragraph"))
        assertFalse(prompt.contains("2-3 sentences"))
        assertFalse(prompt.contains("4-6 sentences"))
    }

    @Test
    fun `one article failure does not cancel others`() = runTest {
        val article1 = Article(id = 1, sourceId = "s1", title = "Article 1", body = "body1", url = "https://example.com/1", contentHash = "h1")
        val article2 = Article(id = 2, sourceId = "s1", title = "Article 2", body = "body2", url = "https://example.com/2", contentHash = "h2")
        val article3 = Article(id = 3, sourceId = "s1", title = "Article 3", body = "body3", url = "https://example.com/3", contentHash = "h3")

        val successResult = ScoreSummarizeResult(relevanceScore = 7, summary = "Good summary.")
        val metadata = ChatResponseMetadata.builder()
            .usage(DefaultUsage(500, 80))
            .build()
        val chatResponse = ChatResponse(listOf(Generation(AssistantMessage("{}"))), metadata)
        val responseEntity = ResponseEntity(chatResponse, successResult)

        val successCallSpec = mockk<ChatClient.CallResponseSpec>()
        every { successCallSpec.responseEntity(any<StructuredOutputConverter<ScoreSummarizeResult>>()) } returns responseEntity

        val failCallSpec = mockk<ChatClient.CallResponseSpec>()
        every { failCallSpec.responseEntity(any<StructuredOutputConverter<ScoreSummarizeResult>>()) } throws RuntimeException("LLM unavailable")

        val callCount = AtomicInteger(0)
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        every { requestSpec.user(any<String>()) } returns requestSpec
        every { requestSpec.options(any()) } returns requestSpec
        every { requestSpec.call() } answers {
            val current = callCount.getAndIncrement()
            if (current == 1) failCallSpec else successCallSpec
        }

        every { chatClient.prompt() } returns requestSpec
        every { chatClientFactory.createForModel(podcast.userId, filterModelDef) } returns chatClient

        val result = scoreSummarizer.scoreSummarize(listOf(article1, article2, article3), podcast, filterModelDef)

        assertEquals(2, result.size)
        verify(exactly = 2) { articleRepository.save(any()) }
    }

    @Test
    fun `all articles fail returns empty list`() = runTest {
        val article1 = Article(id = 1, sourceId = "s1", title = "Article 1", body = "body1", url = "https://example.com/1", contentHash = "h1")
        val article2 = Article(id = 2, sourceId = "s1", title = "Article 2", body = "body2", url = "https://example.com/2", contentHash = "h2")
        val article3 = Article(id = 3, sourceId = "s1", title = "Article 3", body = "body3", url = "https://example.com/3", contentHash = "h3")

        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        every { requestSpec.user(any<String>()) } returns requestSpec
        every { requestSpec.options(any()) } returns requestSpec
        every { requestSpec.call() } throws RuntimeException("LLM unavailable")

        every { chatClient.prompt() } returns requestSpec
        every { chatClientFactory.createForModel(podcast.userId, filterModelDef) } returns chatClient

        val result = scoreSummarizer.scoreSummarize(listOf(article1, article2, article3), podcast, filterModelDef)

        assertTrue(result.isEmpty())
        verify(exactly = 0) { articleRepository.save(any()) }
    }

    @Test
    fun `concurrency is limited to configured window size`() = runTest {
        val articles = (1..4).map {
            Article(id = it.toLong(), sourceId = "s1", title = "Article $it", body = "body$it", url = "https://example.com/$it", contentHash = "h$it")
        }

        val maxConcurrent = AtomicInteger(0)
        val currentConcurrent = AtomicInteger(0)

        val successResult = ScoreSummarizeResult(relevanceScore = 7, summary = "Good summary.")
        val metadata = ChatResponseMetadata.builder()
            .usage(DefaultUsage(500, 80))
            .build()
        val chatResponse = ChatResponse(listOf(Generation(AssistantMessage("{}"))), metadata)
        val responseEntity = ResponseEntity(chatResponse, successResult)

        val callResponseSpec = mockk<ChatClient.CallResponseSpec>()
        every { callResponseSpec.responseEntity(any<StructuredOutputConverter<ScoreSummarizeResult>>()) } returns responseEntity

        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        every { requestSpec.user(any<String>()) } returns requestSpec
        every { requestSpec.options(any()) } returns requestSpec
        every { requestSpec.call() } answers {
            val current = currentConcurrent.incrementAndGet()
            maxConcurrent.updateAndGet { max -> maxOf(max, current) }
            Thread.sleep(50) // simulate LLM latency
            currentConcurrent.decrementAndGet()
            callResponseSpec
        }

        every { chatClient.prompt() } returns requestSpec
        every { chatClientFactory.createForModel(podcast.userId, filterModelDef) } returns chatClient

        val summarizer = ArticleScoreSummarizer(
            articleRepository, chatClientFactory, jsonMapper, testRetryRegistry(),
            appProperties(ScoringProperties(concurrency = 2))
        )
        val result = summarizer.scoreSummarize(articles, podcast, filterModelDef)

        assertEquals(4, result.size)
        assertTrue(maxConcurrent.get() <= 2, "Expected max concurrency <= 2 but was ${maxConcurrent.get()}")
    }

    @Test
    fun `retry succeeds on second attempt`() = runTest {
        val article = Article(
            id = 1, sourceId = "s1", title = "Flaky Article", body = "body",
            url = "https://example.com/1", contentHash = "h1"
        )

        val successResult = ScoreSummarizeResult(relevanceScore = 7, summary = "Good summary.")
        val metadata = ChatResponseMetadata.builder()
            .usage(DefaultUsage(500, 80))
            .build()
        val chatResponse = ChatResponse(listOf(Generation(AssistantMessage("{}"))), metadata)
        val responseEntity = ResponseEntity(chatResponse, successResult)

        val successCallSpec = mockk<ChatClient.CallResponseSpec>()
        every { successCallSpec.responseEntity(any<StructuredOutputConverter<ScoreSummarizeResult>>()) } returns responseEntity

        val failCallSpec = mockk<ChatClient.CallResponseSpec>()
        every { failCallSpec.responseEntity(any<StructuredOutputConverter<ScoreSummarizeResult>>()) } throws RuntimeException("Transient error")

        val callCount = AtomicInteger(0)
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        every { requestSpec.user(any<String>()) } returns requestSpec
        every { requestSpec.options(any()) } returns requestSpec
        every { requestSpec.call() } answers {
            if (callCount.getAndIncrement() == 0) failCallSpec else successCallSpec
        }

        every { chatClient.prompt() } returns requestSpec
        every { chatClientFactory.createForModel(podcast.userId, filterModelDef) } returns chatClient

        val summarizer = ArticleScoreSummarizer(
            articleRepository, chatClientFactory, jsonMapper, testRetryRegistry(maxAttempts = 3),
            appProperties(ScoringProperties(concurrency = 10))
        )
        val result = summarizer.scoreSummarize(listOf(article), podcast, filterModelDef)

        assertEquals(1, result.size)
        assertEquals(7, result[0].relevanceScore)
        verify(exactly = 1) { articleRepository.save(any()) }
    }

    @Test
    fun `prompt without subtopics does not mention subtopic`() {
        val article = Article(
            id = 100, sourceId = "s1", title = "AI Story", body = "body.",
            url = "https://example.com/100", contentHash = "h100"
        )
        val prompt = scoreSummarizer.buildPrompt(article, podcast)
        assertFalse(prompt.contains("subtopic"))
        assertFalse(prompt.contains("subtopics"))
    }

    @Test
    fun `prompt with subtopics includes the configured list and subtopic field`() {
        val article = Article(
            id = 101, sourceId = "s1", title = "AI Story", body = "body.",
            url = "https://example.com/101", contentHash = "h101"
        )
        val withSubs = podcast.copy(subtopics = Subtopics(mapOf("LLM releases" to 10, "Dev tools" to 5)))
        val prompt = scoreSummarizer.buildPrompt(article, withSubs)
        assertTrue(prompt.contains("LLM releases"))
        assertTrue(prompt.contains("Dev tools"))
        assertTrue(prompt.contains("\"subtopic\""))
        // weights MUST NOT leak into the prompt
        assertFalse(prompt.contains(": 10"))
        assertFalse(prompt.contains(": 5"))
    }

    @Test
    fun `normalizeSubtopic returns null when subtopics disabled`() {
        assertEquals(null, scoreSummarizer.normalizeSubtopic("anything", podcast))
    }

    @Test
    fun `normalizeSubtopic returns null for unknown name`() {
        val withSubs = podcast.copy(subtopics = Subtopics(mapOf("LLMs" to 10)))
        assertEquals(null, scoreSummarizer.normalizeSubtopic("Quantum", withSubs))
    }

    @Test
    fun `normalizeSubtopic matches case-insensitively and returns configured casing`() {
        val withSubs = podcast.copy(subtopics = Subtopics(mapOf("LLM releases" to 10)))
        assertEquals("LLM releases", scoreSummarizer.normalizeSubtopic("llm releases", withSubs))
        assertEquals("LLM releases", scoreSummarizer.normalizeSubtopic("LLM Releases", withSubs))
    }

    @Test
    fun `normalizeSubtopic treats blank and the string null as null`() {
        val withSubs = podcast.copy(subtopics = Subtopics(mapOf("LLMs" to 10)))
        assertEquals(null, scoreSummarizer.normalizeSubtopic("", withSubs))
        assertEquals(null, scoreSummarizer.normalizeSubtopic("   ", withSubs))
        assertEquals(null, scoreSummarizer.normalizeSubtopic("null", withSubs))
        assertEquals(null, scoreSummarizer.normalizeSubtopic(null, withSubs))
    }

    @Test
    fun `article with subtopics persists normalized subtopic`() = runTest {
        val article = Article(
            id = 102, sourceId = "s1", title = "GPT-5", body = "OpenAI launched a new model.",
            url = "https://example.com/102", contentHash = "h102"
        )
        val withSubs = podcast.copy(subtopics = Subtopics(mapOf("LLM releases" to 10, "Dev tools" to 5)))
        mockLlmResponse(ScoreSummarizeResult(relevanceScore = 8, summary = "OpenAI launched GPT-5.", subtopic = "LLM Releases"))
        every { chatClientFactory.createForModel(withSubs.userId, filterModelDef) } returns chatClient

        val result = scoreSummarizer.scoreSummarize(listOf(article), withSubs, filterModelDef)

        assertEquals(1, result.size)
        assertEquals("LLM releases", result[0].subtopic)

        val saved = slot<Article>()
        verify { articleRepository.save(capture(saved)) }
        assertEquals("LLM releases", saved.captured.subtopic)
    }

    @Test
    fun `article with subtopics disabled persists null subtopic`() = runTest {
        val article = Article(
            id = 103, sourceId = "s1", title = "X", body = "body.",
            url = "https://example.com/103", contentHash = "h103"
        )
        mockLlmResponse(ScoreSummarizeResult(relevanceScore = 7, summary = "x", subtopic = "whatever"))

        val result = scoreSummarizer.scoreSummarize(listOf(article), podcast, filterModelDef)

        assertEquals(1, result.size)
        assertEquals(null, result[0].subtopic)
    }

    @Test
    fun `all retries exhausted excludes article from result`() = runTest {
        val article = Article(
            id = 1, sourceId = "s1", title = "Persistent Failure", body = "body",
            url = "https://example.com/1", contentHash = "h1"
        )

        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        every { requestSpec.user(any<String>()) } returns requestSpec
        every { requestSpec.options(any()) } returns requestSpec
        every { requestSpec.call() } throws RuntimeException("LLM unavailable")

        every { chatClient.prompt() } returns requestSpec
        every { chatClientFactory.createForModel(podcast.userId, filterModelDef) } returns chatClient

        val summarizer = ArticleScoreSummarizer(
            articleRepository, chatClientFactory, jsonMapper, testRetryRegistry(maxAttempts = 3),
            appProperties(ScoringProperties(concurrency = 10))
        )
        val result = summarizer.scoreSummarize(listOf(article), podcast, filterModelDef)

        assertTrue(result.isEmpty())
        verify(exactly = 0) { articleRepository.save(any()) }
    }

    @Test
    fun `promptForAttempt leaves the first attempt untouched`() {
        assertEquals("Score this", scoreSummarizer.promptForAttempt("Score this", 1))
    }

    @Test
    fun `promptForAttempt appends a JSON-only correction from the second attempt on`() {
        val retried = scoreSummarizer.promptForAttempt("Score this", 2)

        assertTrue(retried.startsWith("Score this"))
        assertTrue(retried.contains("could not be parsed as JSON"))
    }

    @Test
    fun `promptForAttempt gives every attempt a distinct prompt so no retry replays a cached response`() {
        val prompts = (1..3).map { scoreSummarizer.promptForAttempt("Score this", it) }

        assertEquals(prompts.size, prompts.distinct().size)
    }

    @Test
    fun `retries send a different prompt than the failed attempt`() = runTest {
        val article = Article(
            id = 1L, sourceId = "s1", title = "Test", body = "Body",
            url = "https://example.com", contentHash = "h1"
        )

        val sentPrompts = mutableListOf<String>()
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        every { requestSpec.user(capture(sentPrompts)) } returns requestSpec
        every { requestSpec.options(any()) } returns requestSpec
        // Stands in for a cached, unparseable completion: the same prompt always fails the same way.
        every { requestSpec.call() } throws RuntimeException("Unrecognized token 'We'")

        every { chatClient.prompt() } returns requestSpec
        every { chatClientFactory.createForModel(podcast.userId, filterModelDef) } returns chatClient

        val summarizer = ArticleScoreSummarizer(
            articleRepository, chatClientFactory, jsonMapper, testRetryRegistry(maxAttempts = 3),
            appProperties(ScoringProperties(concurrency = 10))
        )
        summarizer.scoreSummarize(listOf(article), podcast, filterModelDef)

        assertEquals(3, sentPrompts.size)
        assertEquals(3, sentPrompts.distinct().size)
    }
}
