package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Covers the percentile read: which rows qualify as latency, what the percentiles are, and how a
 * read scoped to one episode differs from a read over a window.
 */
@SpringBootTest
class LlmCallLatencyTest {

    @Autowired lateinit var llmCallRepository: LlmCallRepository

    private val window = LlmCallScope(cutoff = "2026-09-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        llmCallRepository.deleteAll()
    }

    private fun call(
        durationMs: Long,
        stage: String = "compose",
        cacheHit: Boolean = false,
        outcome: String = "ok",
        startedAt: String = "2026-09-15T10:00:00Z",
        episodeId: Long? = null
    ) = llmCallRepository.save(
        LlmCall(
            startedAt = startedAt,
            stage = stage,
            provider = "openrouter",
            model = "test-model",
            durationMs = durationMs,
            inputTokens = 10,
            outputTokens = 20,
            cacheHit = cacheHit,
            outcome = outcome,
            episodeId = episodeId
        )
    )

    @Test
    fun `percentiles are the nearest-rank durations`() {
        (1..100).forEach { call(durationMs = it * 100L) }

        val latency = llmCallRepository.latencyPercentiles(window).single()

        assertEquals(100, latency.samples)
        assertEquals(5000L, latency.p50Ms)
        assertEquals(9000L, latency.p90Ms)
        assertEquals(9500L, latency.p95Ms)
        assertEquals(9900L, latency.p99Ms)
    }

    @Test
    fun `cache hits and failures do not count as latency`() {
        call(durationMs = 1000)
        call(durationMs = 0, cacheHit = true)
        call(durationMs = 1_200_000, outcome = "error")

        val latency = llmCallRepository.latencyPercentiles(window).single()

        assertEquals(1, latency.samples)
        assertEquals(1000L, latency.p50Ms)
        assertEquals(1000L, latency.p99Ms)
    }

    @Test
    fun `rows outside the window are excluded`() {
        call(durationMs = 1000, startedAt = "2026-08-01T00:00:00Z")
        call(durationMs = 2000, startedAt = "2026-09-15T10:00:00Z")

        val latency = llmCallRepository.latencyPercentiles(window).single()

        assertEquals(1, latency.samples)
        assertEquals(2000L, latency.p50Ms)
    }

    @Test
    fun `each stage is reported separately`() {
        call(durationMs = 1000, stage = "filter")
        call(durationMs = 60_000, stage = "compose")

        val byStage = llmCallRepository.latencyPercentiles(window).associateBy { it.stage }

        assertEquals(1000L, byStage.getValue("filter").p95Ms)
        assertEquals(60_000L, byStage.getValue("compose").p95Ms)
    }

    @Test
    fun `an episode's percentiles cover only its own requests`() {
        call(durationMs = 1000, episodeId = 1)
        call(durationMs = 3000, episodeId = 1)
        call(durationMs = 99_000, episodeId = 2)
        call(durationMs = 99_000)

        val latency = llmCallRepository.latencyPercentiles(LlmCallScope(episodeId = 1)).single()

        assertEquals(2, latency.samples)
        assertEquals(1000L, latency.p50Ms)
        assertEquals(3000L, latency.p99Ms)
    }

    @Test
    fun `an episode's requests are not bounded by a time window`() {
        call(durationMs = 1000, startedAt = "2020-01-01T00:00:00Z", episodeId = 1)

        val latency = llmCallRepository.latencyPercentiles(LlmCallScope(episodeId = 1)).single()

        assertEquals(1, latency.samples)
    }

    @Test
    fun `an episode's request list includes its cached and failed requests`() {
        call(durationMs = 1000, episodeId = 1)
        call(durationMs = 0, cacheHit = true, episodeId = 1)
        call(durationMs = 1_200_000, outcome = "error", episodeId = 1)
        call(durationMs = 500, episodeId = 2)

        val requests = llmCallRepository.requestsForEpisode(1)

        assertEquals(3, requests.size)
        assertEquals(1, requests.count { it.cacheHit })
        assertEquals(1, requests.count { it.outcome == "error" })
    }

    @Test
    fun `the attribution floor is the earliest request naming an episode`() {
        assertNull(llmCallRepository.earliestAttributedStart())

        call(durationMs = 1000, startedAt = "2026-09-10T00:00:00Z")
        assertNull(llmCallRepository.earliestAttributedStart())

        call(durationMs = 1000, startedAt = "2026-09-14T00:00:00Z", episodeId = 1)
        call(durationMs = 1000, startedAt = "2026-09-16T00:00:00Z", episodeId = 2)

        assertEquals("2026-09-14T00:00:00Z", llmCallRepository.earliestAttributedStart())
    }
}
