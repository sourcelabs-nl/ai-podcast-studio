package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.ComposeProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelType
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.CandidateOutcome
import com.aisummarypodcast.testRetryRegistry
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import tools.jackson.databind.json.JsonMapper

/**
 * The wiring between the gate and the clustering call: that an excluded candidate really does not
 * reach the prompt, and that a gate answering nothing leaves the stage as it was.
 */
class TopicDedupFilterGateTest {

    private val chatClientFactory = mockk<ChatClientFactory>()
    private val chatClient = mockk<ChatClient>()
    private val gate = mockk<CoveredTopicGate>()

    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test"),
        compose = ComposeProperties(maxArticles = 40)
    )

    private val modelDef = ResolvedModel(
        provider = "openrouter", model = "test-model",
        cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 0.15, outputCostPerMtok = 0.60),
        stage = PipelineStage.DEDUP
    )

    private val filter = TopicDedupFilter(
        chatClientFactory, JsonMapper.builder().build(), testRetryRegistry(), appProperties, gate
    )

    private val history = EpisodeHistory(articles = emptyList(), coveredTopics = listOf("Opus 5 release"))

    private fun article(id: Long, title: String) = Article(
        id = id, sourceId = "src-1", title = title, body = "Body",
        url = "https://example.com/$id", contentHash = "hash-$id", relevanceScore = 8,
        summary = "Summary of $title"
    )

    /** Captures the prompt the clustering call receives and answers with [clustersJson]. */
    private fun capturePrompt(clustersJson: String): CapturingSlot<String> {
        val prompt = slot<String>()
        val metadata = ChatResponseMetadata.builder().usage(DefaultUsage(100, 20)).build()
        val chatResponse = ChatResponse(listOf(Generation(AssistantMessage(clustersJson))), metadata)

        val callSpec = mockk<ChatClient.CallResponseSpec> {
            every { chatResponse() } returns chatResponse
        }
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        every { requestSpec.user(capture(prompt)) } returns requestSpec
        every { requestSpec.options(any()) } returns requestSpec
        every { requestSpec.call() } returns callSpec
        every { chatClient.prompt() } returns requestSpec
        every { chatClientFactory.createForModel("u1", modelDef, any(), any()) } returns chatClient
        return prompt
    }

    @Test
    fun `an excluded candidate never reaches the clustering prompt`() = runTest {
        every { gate.evaluate(any(), any(), "u1") } returns
            CoveredTopicGateResult(setOf(2L), 0.0007, answered = true)
        val prompt = capturePrompt("""{"clusters":[{"topic":"Fresh","status":"NEW","selectedArticleIds":[1]}]}""")

        val result = filter.filter(listOf(article(1, "Fresh story"), article(2, "Old story")), history, "u1", modelDef)

        assertTrue(prompt.captured.contains("Fresh story"))
        assertFalse(prompt.captured.contains("Old story"))
        assertEquals(listOf(1L), result.filteredArticles.map { it.article.id })
        assertEquals(0.0007, result.gate.reportedCostUsd)
    }

    @Test
    fun `a gate that answers nothing clusters every candidate`() = runTest {
        every { gate.evaluate(any(), any(), "u1") } returns CoveredTopicGateResult.NOT_RUN
        val prompt = capturePrompt(
            """{"clusters":[{"topic":"Both","status":"NEW","selectedArticleIds":[1,2]}]}"""
        )

        val result = filter.filter(listOf(article(1, "Fresh story"), article(2, "Old story")), history, "u1", modelDef)

        assertTrue(prompt.captured.contains("Fresh story"))
        assertTrue(prompt.captured.contains("Old story"))
        assertEquals(2, result.filteredArticles.size)
        assertNull(result.gate.reportedCostUsd)
    }

    @Test
    fun `a gate excluding every candidate is overruled rather than skipping the episode`() = runTest {
        // Ending the run is the clustering call's decision to make. A gate excluding everything
        // would otherwise be indistinguishable from a genuinely quiet day and silently cost an
        // episode.
        every { gate.evaluate(any(), any(), "u1") } returns
            CoveredTopicGateResult(setOf(1L, 2L), 0.0007, answered = true)
        val prompt = capturePrompt(
            """{"clusters":[{"topic":"Both","status":"CONTINUATION","previousContext":"Covered","selectedArticleIds":[]}]}"""
        )

        val result = filter.filter(listOf(article(1, "Old one"), article(2, "Old two")), history, "u1", modelDef)

        assertTrue(prompt.captured.contains("Old one"))
        assertTrue(prompt.captured.contains("Old two"))
        // The clustering call reached the same conclusion here, but on its own evidence.
        assertTrue(result.filteredArticles.isEmpty())
        assertEquals(0.0007, result.gate.reportedCostUsd)
    }

    @Test
    fun `each dropped candidate carries the decision that dropped it`() = runTest {
        every { gate.evaluate(any(), any(), "u1") } returns
            CoveredTopicGateResult(setOf(3L), 0.0007, answered = true, inputTokens = 900, requests = 1)
        capturePrompt("""{"clusters":[{"topic":"Fresh","status":"NEW","selectedArticleIds":[1]}]}""")

        val result = filter.filter(
            listOf(article(1, "Kept"), article(2, "Clustered away"), article(3, "Already covered")),
            history, "u1", modelDef
        )

        val byArticle = result.dropped.associate { it.articleId to it.outcome }
        assertEquals(2, byArticle.size)
        assertEquals(CandidateOutcome.EXCLUDED_BY_GATE, byArticle[3L])
        assertEquals(CandidateOutcome.DROPPED_AS_DUPLICATE, byArticle[2L])
    }

    @Test
    fun `an overruled gate drops nothing of its own`() = runTest {
        // It excluded both, so it removed neither: the overrule means those candidates were
        // clustered, and what happened to them there is the clustering call's decision.
        every { gate.evaluate(any(), any(), "u1") } returns
            CoveredTopicGateResult(setOf(1L, 2L), 0.0007, answered = true)
        capturePrompt(
            """{"clusters":[{"topic":"Both","status":"NEW","selectedArticleIds":[1]}]}"""
        )

        val result = filter.filter(listOf(article(1, "One"), article(2, "Two")), history, "u1", modelDef)

        assertEquals(listOf(CandidateOutcome.DROPPED_AS_DUPLICATE), result.dropped.map { it.outcome })
    }

    @Test
    fun `the gate's own spend is reported apart from the clustering call`() = runTest {
        every { gate.evaluate(any(), any(), "u1") } returns
            CoveredTopicGateResult(setOf(2L), 0.0007, answered = true, inputTokens = 900, requests = 2)
        capturePrompt("""{"clusters":[{"topic":"Fresh","status":"NEW","selectedArticleIds":[1]}]}""")

        val result = filter.filter(listOf(article(1, "Fresh"), article(2, "Old")), history, "u1", modelDef)

        assertEquals(DedupGateUsage(inputTokens = 900, requests = 2, reportedCostUsd = 0.0007), result.gate)
        // The clustering call's own usage is untouched by the gate's.
        assertEquals(100, result.usage.inputTokens)
    }

    @Test
    fun `the summary line says what the gate did`() {
        val all = listOf(article(1, "One"), article(2, "Two"))

        assertEquals(
            "ungated",
            filter.describeGate(all, all, CoveredTopicGateResult.NOT_RUN)
        )
        assertEquals(
            "1 gated out",
            filter.describeGate(all, listOf(all[0]), CoveredTopicGateResult(setOf(2L), 0.0, answered = true))
        )
        // The run where the gate misbehaved most must not read as "0 gated out".
        assertEquals(
            "gate wanted all 2 excluded, overruled",
            filter.describeGate(all, all, CoveredTopicGateResult(setOf(1L, 2L), 0.0, answered = true))
        )
        assertEquals(
            "0 gated out",
            filter.describeGate(all, all, CoveredTopicGateResult(emptySet(), 0.0, answered = true))
        )
    }
}
