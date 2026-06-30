package com.aisummarypodcast.llm

import com.aisummarypodcast.store.LlmCache
import com.aisummarypodcast.store.LlmCacheRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions

class CachingChatModelTest {

    private val delegate = mockk<ChatModel>()
    private val llmCacheRepository = mockk<LlmCacheRepository>(relaxed = true) {
        every { save(any<LlmCache>()) } answers { firstArg() }
    }
    private val cachingChatModel = CachingChatModel(delegate, llmCacheRepository)

    @Test
    fun `cache miss delegates to wrapped model and stores result with tokens`() {
        val prompt = Prompt("Summarize this article", OpenAiChatOptions.builder().model("test-model").build())
        val metadata = ChatResponseMetadata.builder()
            .usage(DefaultUsage(200, 50))
            .build()
        val expectedResponse = ChatResponse(listOf(Generation(AssistantMessage("A summary"))), metadata)

        every { llmCacheRepository.findByPromptHashAndModel(any(), "test-model") } returns null
        every { delegate.call(prompt) } returns expectedResponse

        val result = cachingChatModel.call(prompt)

        assertEquals("A summary", result.result!!.output.text)
        verify(exactly = 1) { delegate.call(prompt) }

        val savedSlot = slot<LlmCache>()
        verify { llmCacheRepository.save(capture(savedSlot)) }
        assertEquals("A summary", savedSlot.captured.response)
        assertEquals("test-model", savedSlot.captured.model)
        assertEquals(200, savedSlot.captured.inputTokens)
        assertEquals(50, savedSlot.captured.outputTokens)
    }

    @Test
    fun `cache hit returns cached response with cached token counts`() {
        val prompt = Prompt("Summarize this article", OpenAiChatOptions.builder().model("test-model").build())
        val cachedEntry = LlmCache(
            id = 1,
            promptHash = "somehash",
            model = "test-model",
            response = "Cached summary",
            createdAt = "2026-01-01T00:00:00Z",
            inputTokens = 300,
            outputTokens = 75
        )

        every { llmCacheRepository.findByPromptHashAndModel(any(), "test-model") } returns cachedEntry

        val result = cachingChatModel.call(prompt)

        assertEquals("Cached summary", result.result!!.output.text)
        verify(exactly = 0) { delegate.call(any<Prompt>()) }

        val usage = TokenUsage.fromChatResponse(result)
        assertEquals(300, usage.inputTokens)
        assertEquals(75, usage.outputTokens)
    }

    @Test
    fun `different model produces cache miss even for same prompt`() {
        val promptA = Prompt("Summarize this article", OpenAiChatOptions.builder().model("model-a").build())
        val promptB = Prompt("Summarize this article", OpenAiChatOptions.builder().model("model-b").build())
        val responseA = ChatResponse(listOf(Generation(AssistantMessage("Summary A"))))
        val responseB = ChatResponse(listOf(Generation(AssistantMessage("Summary B"))))

        every { llmCacheRepository.findByPromptHashAndModel(any(), "model-a") } returns null
        every { llmCacheRepository.findByPromptHashAndModel(any(), "model-b") } returns null
        every { delegate.call(promptA) } returns responseA
        every { delegate.call(promptB) } returns responseB

        val resultA = cachingChatModel.call(promptA)
        val resultB = cachingChatModel.call(promptB)

        assertEquals("Summary A", resultA.result!!.output.text)
        assertEquals("Summary B", resultB.result!!.output.text)
        verify(exactly = 1) { delegate.call(promptA) }
        verify(exactly = 1) { delegate.call(promptB) }

        val savedSlots = mutableListOf<LlmCache>()
        verify(exactly = 2) { llmCacheRepository.save(capture(savedSlots)) }
        assertEquals("model-a", savedSlots[0].model)
        assertEquals("model-b", savedSlots[1].model)
    }

    @Test
    fun `prompt without model options uses default key`() {
        val prompt = Prompt("Summarize this article")
        val expectedResponse = ChatResponse(listOf(Generation(AssistantMessage("A summary"))))

        every { llmCacheRepository.findByPromptHashAndModel(any(), "default") } returns null
        every { delegate.call(prompt) } returns expectedResponse

        val result = cachingChatModel.call(prompt)

        assertEquals("A summary", result.result!!.output.text)

        val savedSlot = slot<LlmCache>()
        verify { llmCacheRepository.save(capture(savedSlot)) }
        assertEquals("default", savedSlot.captured.model)
    }

    @Test
    fun `cache miss preserves usage metadata from delegate response`() {
        val prompt = Prompt("Summarize", OpenAiChatOptions.builder().model("test-model").build())
        val metadata = ChatResponseMetadata.builder()
            .usage(DefaultUsage(500, 100))
            .build()
        val delegateResponse = ChatResponse(listOf(Generation(AssistantMessage("Summary"))), metadata)

        every { llmCacheRepository.findByPromptHashAndModel(any(), "test-model") } returns null
        every { delegate.call(prompt) } returns delegateResponse

        val result = cachingChatModel.call(prompt)

        val usage = TokenUsage.fromChatResponse(result)
        assertEquals(500, usage.inputTokens)
        assertEquals(100, usage.outputTokens)
    }

    @Test
    fun `blank response is not cached`() {
        // A degenerate model run (e.g. burning its whole maxTokens budget) returns an empty
        // string, not null. Caching it would poison every retry and regenerate with the same
        // parse failure, so blank completions must never be stored.
        val prompt = Prompt("Cluster these articles", OpenAiChatOptions.builder().model("test-model").build())
        val blankResponse = ChatResponse(listOf(Generation(AssistantMessage(""))))

        every { llmCacheRepository.findByPromptHashAndModel(any(), "test-model") } returns null
        every { delegate.call(prompt) } returns blankResponse

        val result = cachingChatModel.call(prompt)

        assertEquals("", result.result!!.output.text)
        verify(exactly = 0) { llmCacheRepository.save(any<LlmCache>()) }
    }

    @Test
    fun `tool-loop intermediate call with tool calls in response is not cached`() {
        val prompt = Prompt("user prompt", OpenAiChatOptions.builder().model("test-model").build())
        val toolCall = AssistantMessage.ToolCall("call-1", "function", "searchPastEpisodes", "{\"query\":\"speckit\"}")
        val toolCallResponse = ChatResponse(listOf(Generation(
            AssistantMessage.builder().content("").toolCalls(listOf(toolCall)).build()
        )))

        every { llmCacheRepository.findByPromptHashAndModel(any(), "test-model") } returns null
        every { delegate.call(prompt) } returns toolCallResponse

        cachingChatModel.call(prompt)

        verify(exactly = 0) { llmCacheRepository.save(any<LlmCache>()) }
    }

    @Test
    fun `tool-loop second call (with tool messages) skips cache lookup but does cache the final response`() {
        // Reproduces a Spring AI tool-loop iteration: the augmented prompt includes the prior
        // assistant tool-call turn and the tool response. The cache key must hash only the
        // user message so that a later identical user prompt finds this final response.
        val userMessage = org.springframework.ai.chat.messages.UserMessage("user prompt")
        val priorAssistant = AssistantMessage.builder()
            .content("")
            .toolCalls(listOf(AssistantMessage.ToolCall("call-1", "function", "searchPastEpisodes", "{\"query\":\"x\"}")))
            .build()
        val toolResponse = org.springframework.ai.chat.messages.ToolResponseMessage.builder()
            .responses(listOf(org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse("call-1", "searchPastEpisodes", "{}")))
            .build()
        val augmented = Prompt(listOf(userMessage, priorAssistant, toolResponse), OpenAiChatOptions.builder().model("test-model").build())
        val finalResponse = ChatResponse(listOf(Generation(AssistantMessage("final answer"))))

        every { delegate.call(augmented) } returns finalResponse

        cachingChatModel.call(augmented)

        // No cache lookup happens for non-initial calls.
        verify(exactly = 0) { llmCacheRepository.findByPromptHashAndModel(any(), any()) }
        // The final response IS cached, keyed on the user-only hash.
        verify(exactly = 1) { llmCacheRepository.save(any<LlmCache>()) }
    }

    @Test
    fun `cache key derives from user message only, ignoring tool-loop messages`() {
        val userMessage = org.springframework.ai.chat.messages.UserMessage("identical user prompt")
        val initialPrompt = Prompt(listOf(userMessage), OpenAiChatOptions.builder().model("test-model").build())

        val priorAssistant = AssistantMessage.builder()
            .content("")
            .toolCalls(listOf(AssistantMessage.ToolCall("c", "function", "searchPastEpisodes", "{}")))
            .build()
        val toolResponse = org.springframework.ai.chat.messages.ToolResponseMessage.builder()
            .responses(listOf(org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse("c", "searchPastEpisodes", "{}")))
            .build()
        val toolLoopPrompt = Prompt(listOf(userMessage, priorAssistant, toolResponse), OpenAiChatOptions.builder().model("test-model").build())

        every { llmCacheRepository.findByPromptHashAndModel(any(), "test-model") } returns null
        every { delegate.call(any<Prompt>()) } returns ChatResponse(listOf(Generation(AssistantMessage("final"))))

        cachingChatModel.call(toolLoopPrompt)

        // The save key for the tool-loop final call must equal the key the initial prompt
        // would have used. Capture and verify the persisted hash matches what the initial
        // prompt's lookup would query.
        val savedSlot = slot<LlmCache>()
        verify { llmCacheRepository.save(capture(savedSlot)) }
        val persistedHash = savedSlot.captured.promptHash

        // Now simulate a second compose run that starts with the same user prompt.
        every { llmCacheRepository.findByPromptHashAndModel(persistedHash, "test-model") } returns LlmCache(
            id = 1, promptHash = persistedHash, model = "test-model",
            response = "final", createdAt = "now", inputTokens = 0, outputTokens = 0
        )

        val secondRun = cachingChatModel.call(initialPrompt)

        assertEquals("final", secondRun.result!!.output.text)
        // Delegate was only called for the first (tool-loop) prompt; the second run hits the cache.
        verify(exactly = 1) { delegate.call(any<Prompt>()) }
    }

    @Test
    fun `cache hit with null tokens returns zero usage`() {
        val prompt = Prompt("Summarize", OpenAiChatOptions.builder().model("test-model").build())
        val cachedEntry = LlmCache(
            id = 1, promptHash = "hash", model = "test-model",
            response = "Cached", createdAt = "2026-01-01T00:00:00Z",
            inputTokens = null, outputTokens = null
        )

        every { llmCacheRepository.findByPromptHashAndModel(any(), "test-model") } returns cachedEntry

        val result = cachingChatModel.call(prompt)

        val usage = TokenUsage.fromChatResponse(result)
        assertEquals(0, usage.inputTokens)
        assertEquals(0, usage.outputTokens)
    }
}
