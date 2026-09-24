package com.aisummarypodcast.store

import com.aisummarypodcast.llm.GenerationStats
import com.aisummarypodcast.llm.ProviderAttempt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class LlmCallGenerationStatsTest {

    @Autowired lateinit var llmCallRepository: LlmCallRepository

    @BeforeEach
    fun setUp() {
        llmCallRepository.deleteAll()
    }

    private fun call(servedProvider: String? = null) = llmCallRepository.save(
        LlmCall(
            startedAt = "2026-09-24T10:00:00Z", stage = "compose", provider = "openrouter", model = "m",
            durationMs = 526_000, inputTokens = 10, outputTokens = 20, cacheHit = false, outcome = "ok",
            episodeId = 225, servedProvider = servedProvider, generationId = "gen-1"
        )
    )

    private val stats = GenerationStats(
        firstContentMs = 480_000, generationTimeMs = 524_000, nativeCompletionTokens = 12_000,
        nativeReasoningTokens = 9_000, finishReason = "stop", servedProvider = "Novita",
        attempts = listOf(ProviderAttempt("DeepInfra", 503, 400_000), ProviderAttempt("Novita", 200, 2_000))
    )

    @Test
    fun `stats written after the request are read back with the episode's requests`() {
        val id = call().id!!

        llmCallRepository.updateGenerationStats(id, stats)

        val row = llmCallRepository.requestsForEpisode(225).single()
        assertEquals(stats, row.generationStats)
        assertEquals("Novita", row.servedProvider)
    }

    @Test
    fun `a request without stats yet reads as none`() {
        call()

        assertNull(llmCallRepository.requestsForEpisode(225).single().generationStats)
    }

    @Test
    fun `the provider the response reported is kept`() {
        val id = call(servedProvider = "Novita (fp8)").id!!

        llmCallRepository.updateGenerationStats(id, stats)

        assertEquals("Novita (fp8)", llmCallRepository.requestsForEpisode(225).single().servedProvider)
    }
}
