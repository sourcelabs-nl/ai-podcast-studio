package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.ComposeProperties
import com.aisummarypodcast.config.DedupGateProperties
import com.aisummarypodcast.config.DedupProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.LlmCallLatency
import com.aisummarypodcast.store.LlmCallRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Covers which stages the report names. A caller that records requests without being a pipeline
 * stage has to appear, or its requests are written and never read back.
 */
class LlmCallLatencyServiceTest {

    private val llmCallRepository = mockk<LlmCallRepository>()
    private val episodeRepository = mockk<EpisodeRepository>()

    private val appProperties = AppProperties(
        llm = LlmProperties(
            dedup = DedupProperties(gate = DedupGateProperties(timeout = Duration.ofSeconds(30)))
        ),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test"),
        compose = ComposeProperties(maxArticles = 40)
    )

    private val service = LlmCallLatencyService(llmCallRepository, episodeRepository, appProperties)

    @Test
    fun `the dedup gate and the research plan are reported alongside the pipeline stages`() {
        every { llmCallRepository.latencyPercentiles(any()) } returns emptyList()

        val stages = service.latencySince(Duration.ofDays(1)).stages.map { it.stage }

        assertEquals(PipelineStage.entries.map { it.value } + DEDUP_GATE_STAGE + RESEARCH_PLAN_STAGE, stages)
    }

    @Test
    fun `a stage that issued nothing is reported with a zero sample count`() {
        every { llmCallRepository.latencyPercentiles(any()) } returns emptyList()

        val gate = service.latencySince(Duration.ofDays(1)).stages.single { it.stage == DEDUP_GATE_STAGE }

        assertEquals(0, gate.samples)
        assertNull(gate.p95Ms)
    }

    @Test
    fun `the gate is reported against its own configured timeout, not a stage timeout`() {
        every { llmCallRepository.latencyPercentiles(any()) } returns emptyList()

        val stages = service.latencySince(Duration.ofDays(1)).stages.associateBy { it.stage }

        assertEquals(30_000L, stages.getValue(DEDUP_GATE_STAGE).timeoutMs)
        assertEquals(
            PipelineStage.COMPOSE.timeout(appProperties.llm.timeouts).toMillis(),
            stages.getValue(PipelineStage.COMPOSE.value).timeoutMs
        )
    }

    @Test
    fun `a measured gate reports its percentiles`() {
        every { llmCallRepository.latencyPercentiles(any()) } returns listOf(
            LlmCallLatency(DEDUP_GATE_STAGE, samples = 4, p50Ms = 610, p90Ms = 860, p95Ms = 860, p99Ms = 860)
        )

        val gate = service.latencySince(Duration.ofDays(1)).stages.single { it.stage == DEDUP_GATE_STAGE }

        assertEquals(4, gate.samples)
        assertEquals(610L, gate.p50Ms)
    }
}
