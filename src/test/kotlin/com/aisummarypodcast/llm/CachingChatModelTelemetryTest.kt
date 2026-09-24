package com.aisummarypodcast.llm

import com.aisummarypodcast.store.LlmCache
import com.aisummarypodcast.store.LlmCacheRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException

/**
 * Covers the telemetry [CachingChatModel] writes: one record per HTTP request, with cache hits and
 * failures marked so that neither can pass for a fast successful call when percentiles are read.
 */
class CachingChatModelTelemetryTest {

    private val delegate = mockk<ChatModel>()
    private val llmCacheRepository = mockk<LlmCacheRepository>(relaxed = true) {
        every { save(any<LlmCache>()) } answers { firstArg() }
    }
    private val llmCallLogService = mockk<LlmCallLogService>(relaxed = true)
    private val resolvedModel = ResolvedModel(
        provider = "openrouter",
        model = "test-model",
        cost = null,
        stage = PipelineStage.COMPOSE
    )
    private val model =
        CachingChatModel(
            delegate, llmCacheRepository, resolvedModel, llmCallLogService,
            attribution = LlmCallAttribution(episodeId = 42)
        )

    private val prompt = Prompt("Write the script", OpenAiChatOptions.builder().model("test-model").build())

    private fun response(text: String = "A script") = ChatResponse(
        listOf(Generation(AssistantMessage(text))),
        ChatResponseMetadata.builder().usage(DefaultUsage(200, 50)).build()
    )

    @Test
    fun `a successful call is recorded with its stage, model and tokens`() {
        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns null
        every { delegate.call(prompt) } returns response()
        val recorded = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(recorded)) } returns 1L

        model.call(prompt)

        assertEquals("compose", recorded.captured.stage)
        assertEquals("openrouter", recorded.captured.provider)
        assertEquals("test-model", recorded.captured.model)
        assertEquals(200, recorded.captured.inputTokens)
        assertEquals(50, recorded.captured.outputTokens)
        assertEquals(LlmCallOutcome.OK, recorded.captured.outcome)
        assertFalse(recorded.captured.cacheHit)
    }

    @Test
    fun `an OpenRouter request records its generation id and has its stats looked up`() {
        val statsService = mockk<GenerationStatsService>(relaxed = true)
        val lookup = GenerationStatsLookup("https://openrouter.ai/api", "sk-test")
        val tracked = CachingChatModel(
            delegate, llmCacheRepository, resolvedModel, llmCallLogService,
            generationStats = GenerationStatsTracker(statsService, lookup)
        )
        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns null
        every { delegate.call(prompt) } returns ChatResponse(
            listOf(Generation(AssistantMessage("A script"))),
            ChatResponseMetadata.builder().id("gen-1").usage(DefaultUsage(200, 50)).build()
        )
        val recorded = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(recorded)) } returns 9L

        tracked.call(prompt)

        assertEquals("gen-1", recorded.captured.generationId)
        verify { statsService.schedule(9L, "gen-1", lookup) }
    }

    @Test
    fun `a cache hit has no generation stats looked up`() {
        val statsService = mockk<GenerationStatsService>(relaxed = true)
        val tracked = CachingChatModel(
            delegate, llmCacheRepository, resolvedModel, llmCallLogService,
            generationStats = GenerationStatsTracker(statsService, GenerationStatsLookup("https://openrouter.ai/api", "sk-test"))
        )
        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns
            LlmCache(promptHash = "h", model = "test-model", response = "cached", createdAt = "2026-09-24T10:00:00Z")
        every { llmCallLogService.record(any()) } returns 9L

        tracked.call(prompt)

        verify(exactly = 0) { statsService.schedule(any(), any(), any()) }
    }

    @Test
    fun `a cache hit is recorded as a hit and never reaches the provider`() {
        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns LlmCache(
            promptHash = "hash",
            model = "test-model",
            response = "A cached script",
            createdAt = "2026-09-15T10:00:00Z",
            inputTokens = 200,
            outputTokens = 50
        )
        val recorded = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(recorded)) } returns 1L

        model.call(prompt)

        assertTrue(recorded.captured.cacheHit)
        verify(exactly = 0) { delegate.call(any<Prompt>()) }
    }

    @Test
    fun `a call that ran out of time is recorded as a timeout and the failure still propagates`() {
        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns null
        every { delegate.call(prompt) } throws SocketTimeoutException("timeout")
        val recorded = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(recorded)) } returns 1L

        assertThrows(SocketTimeoutException::class.java) { model.call(prompt) }

        assertEquals(LlmCallOutcome.ERROR, recorded.captured.outcome)
        // A fixed value rather than the class name: the latency read counts a timeout and skips
        // every other failure, and matches on this in SQL.
        assertEquals(TIMEOUT_ERROR_TYPE, recorded.captured.errorType)
    }

    @Test
    fun `a failure that is not a timeout keeps its exception type`() {
        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns null
        every { delegate.call(prompt) } throws IllegalStateException("provider refused")
        val recorded = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(recorded)) } returns 1L

        assertThrows(IllegalStateException::class.java) { model.call(prompt) }

        assertEquals("IllegalStateException", recorded.captured.errorType)
    }

    @Test
    fun `a tool loop records one call per round-trip`() {
        // The timeout this telemetry exists to size is per HTTP request, so a compose run that uses
        // tools must produce a record per round-trip rather than one spanning the whole loop.
        val toolCall = AssistantMessage.builder()
            .content("")
            .toolCalls(listOf(AssistantMessage.ToolCall("id-1", "function", "searchPastEpisodes", "{}")))
            .build()
        val pending = ChatResponse(
            listOf(Generation(toolCall)),
            ChatResponseMetadata.builder().usage(DefaultUsage(200, 10)).build()
        )
        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns null
        every { delegate.call(any<Prompt>()) } returnsMany listOf(pending, response())
        val recorded = mutableListOf<LlmCallRecord>()
        every { llmCallLogService.record(capture(recorded)) } returns 1L

        model.call(prompt)
        model.call(prompt)

        assertEquals(2, recorded.size)
        assertTrue(recorded.all { it.outcome == LlmCallOutcome.OK && !it.cacheHit })
    }

    @Test
    fun `every kind of record names the episode the request was issued for`() {
        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns null
        every { delegate.call(prompt) } returns response()
        val success = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(success)) } returns 1L
        model.call(prompt)
        assertEquals(42L, success.captured.attribution.episodeId)

        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns LlmCache(
            promptHash = "hash",
            model = "test-model",
            response = "A cached script",
            createdAt = "2026-09-15T10:00:00Z",
            inputTokens = 200,
            outputTokens = 50
        )
        val hit = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(hit)) } returns 1L
        model.call(prompt)
        assertEquals(42L, hit.captured.attribution.episodeId)

        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns null
        every { delegate.call(prompt) } throws SocketTimeoutException("timeout")
        val failure = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(failure)) } returns 1L
        assertThrows(SocketTimeoutException::class.java) { model.call(prompt) }
        assertEquals(42L, failure.captured.attribution.episodeId)
    }

    @Test
    fun `a caller with no episode records none`() {
        val unattributed =
            CachingChatModel(delegate, llmCacheRepository, resolvedModel, llmCallLogService)
        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns null
        every { delegate.call(prompt) } returns response()
        val recorded = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(recorded)) } returns 1L

        unattributed.call(prompt)

        assertNull(recorded.captured.attribution.episodeId)
    }

    /**
     * The composers issue their request inside `withContext(Dispatchers.IO)`, so the request runs on
     * a different thread from the one the stage started on. This is why the episode travels as a
     * constructor parameter rather than in thread-bound ambient state, which would arrive empty here
     * and silently record nothing for exactly the stage this tab is most often opened for.
     */
    @Test
    fun `the episode survives the request being issued on another thread`() = runTest {
        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns null
        every { delegate.call(prompt) } returns response()
        val recorded = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(recorded)) } returns 1L

        withContext(Dispatchers.IO) { model.call(prompt) }

        assertEquals(42L, recorded.captured.attribution.episodeId)
    }

    @Test
    fun `a call records the served provider and the reasoning tokens`() {
        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns null
        val nativeUsage = com.openai.models.completions.CompletionUsage.builder()
            .promptTokens(200).completionTokens(50).totalTokens(250)
            .completionTokensDetails(
                com.openai.models.completions.CompletionUsage.CompletionTokensDetails.builder().reasoningTokens(40).build()
            )
            .build()
        every { delegate.call(prompt) } returns ChatResponse(
            listOf(Generation(AssistantMessage("A script"))),
            ChatResponseMetadata.builder().usage(DefaultUsage(200, 50, 250, nativeUsage)).keyValue("provider", "Anthropic").build()
        )
        val recorded = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(recorded)) } returns 1L

        model.call(prompt)

        assertEquals("Anthropic", recorded.captured.servedProvider)
        assertEquals(40, recorded.captured.reasoningTokens)
    }

    @Test
    fun `a borrowed model records under its telemetry stage`() {
        every { llmCacheRepository.findByPromptHashAndModel(any(), any()) } returns null
        every { delegate.call(prompt) } returns response()
        val recorded = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(recorded)) } returns 1L
        val planModel = CachingChatModel(
            delegate, llmCacheRepository,
            resolvedModel.copy(stage = PipelineStage.FILTER, telemetryStage = RESEARCH_PLAN_STAGE),
            llmCallLogService
        )

        planModel.call(prompt)

        assertEquals("research-plan", recorded.captured.stage)
        assertNull(recorded.captured.servedProvider)
    }
}
