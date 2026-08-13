package com.aisummarypodcast.llm

import com.openai.core.JsonValue
import com.openai.models.completions.CompletionUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation

class TokenUsageTest {

    /** Mirrors what OpenRouter returns: a CompletionUsage carrying extra properties. */
    private fun openRouterUsage(cost: JsonValue?): CompletionUsage {
        val builder = CompletionUsage.builder()
            .promptTokens(500)
            .completionTokens(100)
            .totalTokens(600)
        if (cost != null) builder.putAdditionalProperty("cost", cost)
        return builder.build()
    }

    private fun responseWith(nativeUsage: CompletionUsage?, metadataCost: Double? = null): ChatResponse {
        val builder = ChatResponseMetadata.builder()
            .usage(DefaultUsage(500, 100, 600, nativeUsage))
        metadataCost?.let { builder.keyValue(TokenUsage.REPORTED_COST_METADATA_KEY, it) }
        return ChatResponse(listOf(Generation(AssistantMessage("text"))), builder.build())
    }

    @Test
    fun `extracts usage from ChatResponse with metadata`() {
        val metadata = ChatResponseMetadata.builder()
            .usage(DefaultUsage(500, 100))
            .build()
        val response = ChatResponse(listOf(Generation(AssistantMessage("text"))), metadata)

        val usage = TokenUsage.fromChatResponse(response)

        assertEquals(500, usage.inputTokens)
        assertEquals(100, usage.outputTokens)
        assertNull(usage.reportedCostUsd)
    }

    @Test
    fun `returns zero when ChatResponse has no metadata`() {
        val response = ChatResponse(listOf(Generation(AssistantMessage("text"))))

        val usage = TokenUsage.fromChatResponse(response)

        assertEquals(0, usage.inputTokens)
        assertEquals(0, usage.outputTokens)
    }

    @Test
    fun `returns zero when response is null`() {
        val usage = TokenUsage.fromChatResponse(null)

        assertEquals(0, usage.inputTokens)
        assertEquals(0, usage.outputTokens)
    }

    @Test
    fun `captures the reported cost from OpenRouter native usage`() {
        val usage = TokenUsage.fromChatResponse(responseWith(openRouterUsage(JsonValue.from(7.6E-5))))

        assertEquals(7.6E-5, usage.reportedCostUsd)
        assertFalse(usage.reportedCostFromCache)
        assertEquals(500, usage.inputTokens)
        assertEquals(100, usage.outputTokens)
    }

    @Test
    fun `no reported cost when the provider does not supply one`() {
        val usage = TokenUsage.fromChatResponse(responseWith(openRouterUsage(null)))

        assertNull(usage.reportedCostUsd)
        assertEquals(500, usage.inputTokens)
        assertEquals(100, usage.outputTokens)
    }

    @Test
    fun `non-numeric reported cost is ignored`() {
        val usage = TokenUsage.fromChatResponse(responseWith(openRouterUsage(JsonValue.from("not-a-number"))))

        assertNull(usage.reportedCostUsd)
        assertEquals(500, usage.inputTokens)
    }

    @Test
    fun `negative reported cost is ignored`() {
        val usage = TokenUsage.fromChatResponse(responseWith(openRouterUsage(JsonValue.from(-0.01))))

        assertNull(usage.reportedCostUsd)
    }

    @Test
    fun `metadata-carried cost takes precedence and is marked as replayed`() {
        val usage = TokenUsage.fromChatResponse(
            responseWith(openRouterUsage(JsonValue.from(7.6E-5)), metadataCost = 0.00042)
        )

        assertEquals(0.00042, usage.reportedCostUsd)
        assertTrue(usage.reportedCostFromCache)
    }

    @Test
    fun `metadata-carried cost works without any native usage`() {
        val usage = TokenUsage.fromChatResponse(responseWith(nativeUsage = null, metadataCost = 0.00042))

        assertEquals(0.00042, usage.reportedCostUsd)
        assertTrue(usage.reportedCostFromCache)
    }
}
