package com.aisummarypodcast.podcast

import com.aisummarypodcast.llm.GenerationStats
import com.aisummarypodcast.llm.ProviderAttempt
import com.aisummarypodcast.store.LlmCallRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ComposeBreakdownTest {

    private fun row(stats: GenerationStats?, outcome: String = "ok", cacheHit: Boolean = false) = LlmCallRow(
        startedAt = "2026-09-24T10:00:00Z", stage = "compose", model = "m", durationMs = 102_000,
        outcome = outcome, cacheHit = cacheHit, generationStats = stats
    )

    private val stats = GenerationStats(
        firstContentMs = 80_000, generationTimeMs = 102_000, nativeCompletionTokens = 12_000,
        nativeReasoningTokens = 9_000, finishReason = "stop", servedProvider = "Novita",
        attempts = listOf(ProviderAttempt("Novita", 200, 2_000))
    )

    @Test
    fun `derives the breakdown of a run's compose request`() {
        val breakdown = ComposeBreakdown.of(listOf(row(stats)))!!

        assertEquals(2_000L, breakdown.startupMs)
        assertEquals(78_000L, breakdown.reasoningMs)
        assertEquals(22_000L, breakdown.writingMs)
        assertEquals(0.75, breakdown.reasoningShare)
        assertEquals(120.0, breakdown.tokensPerSecond)
        assertEquals(0, breakdown.fallbackAttempts)
    }

    @Test
    fun `counts attempts beyond the first as fallbacks`() {
        val fellBack = stats.copy(attempts = listOf(ProviderAttempt("DeepInfra", 503, 30_000), ProviderAttempt("Novita", 200, 2_000)))

        val breakdown = ComposeBreakdown.of(listOf(row(fellBack), row(stats)))!!

        assertEquals(1, breakdown.fallbackAttempts)
        assertEquals(34_000L, breakdown.startupMs)
    }

    @Test
    fun `is null while a request has no stats`() {
        assertNull(ComposeBreakdown.of(listOf(row(stats), row(null))))
    }

    @Test
    fun `leaves out failed and cached requests`() {
        val breakdown = ComposeBreakdown.of(listOf(row(stats), row(null, outcome = "error"), row(null, cacheHit = true)))

        assertEquals(2_000L, breakdown!!.startupMs)
        assertNull(ComposeBreakdown.of(listOf(row(null, outcome = "error"))))
    }

    @Test
    fun `a value a request did not report is null`() {
        val breakdown = ComposeBreakdown.of(listOf(row(stats.copy(nativeReasoningTokens = null))))!!

        assertNull(breakdown.reasoningShare)
        assertEquals(120.0, breakdown.tokensPerSecond)
    }
}
