package com.aisummarypodcast.llm

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class GenerationStatsServiceTest {

    private val client = mockk<OpenRouterGenerationClient>()
    private val llmCallLogService = mockk<LlmCallLogService>(relaxed = true)
    private val service = GenerationStatsService(client, llmCallLogService, GenerationStatsRetry())
    private val lookup = GenerationStatsLookup("https://openrouter.ai/api", "sk-test")
    private val stats = GenerationStats(80_000, 100_000, 12_000, 9_000, "stop", "Novita", emptyList())

    @Test
    fun `retries until the stats are available and records them`() = runTest {
        every { client.fetch("gen-1", lookup) } returns null andThen null andThen stats

        service.fetchAndRecord(7, "gen-1", lookup)

        verify(exactly = 3) { client.fetch("gen-1", lookup) }
        verify { llmCallLogService.recordGenerationStats(7, stats) }
    }

    @Test
    fun `a failing lookup is retried rather than propagated`() = runTest {
        every { client.fetch("gen-1", lookup) } throws RuntimeException("502") andThen stats

        service.fetchAndRecord(7, "gen-1", lookup)

        verify { llmCallLogService.recordGenerationStats(7, stats) }
    }

    @Test
    fun `gives up after the retry period without recording anything`() = runTest {
        every { client.fetch("gen-1", lookup) } returns null

        service.fetchAndRecord(7, "gen-1", lookup)

        // 5 s first try, then every 10 s up to 3 minutes: 18 lookups.
        verify(exactly = 18) { client.fetch("gen-1", lookup) }
        verify(exactly = 0) { llmCallLogService.recordGenerationStats(any(), any()) }
    }
}
