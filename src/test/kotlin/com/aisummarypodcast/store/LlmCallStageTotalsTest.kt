package com.aisummarypodcast.store

import com.aisummarypodcast.llm.LlmCostSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Covers the per-stage projection an episode's cost is read from: what it sums, what it refuses to
 * sum, and how it tells the two stages that record themselves as `filter` apart.
 */
@SpringBootTest
class LlmCallStageTotalsTest {

    @Autowired lateinit var llmCallRepository: LlmCallRepository
    @Autowired lateinit var candidateRepository: EpisodeCandidateArticleRepository

    private val episodeId = 226L

    @BeforeEach
    fun setUp() {
        llmCallRepository.deleteAll()
        candidateRepository.deleteAll()
    }

    private fun call(
        stage: String,
        episodeId: Long? = null,
        articleId: Long? = null,
        inputTokens: Int = 100,
        outputTokens: Int = 20,
        resolvedCostUsd: Double? = 0.001,
        costSource: LlmCostSource? = LlmCostSource.API,
        outcome: String = "ok"
    ) = llmCallRepository.save(
        LlmCall(
            startedAt = "2026-09-15T10:00:00Z",
            stage = stage,
            provider = "openrouter",
            model = "test-model",
            durationMs = 100,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            resolvedCostUsd = resolvedCostUsd,
            costSource = costSource?.name,
            cacheHit = false,
            outcome = outcome,
            episodeId = episodeId,
            articleId = articleId
        )
    )

    private fun totals(stage: String) =
        llmCallRepository.stageTotalsForEpisode(episodeId).singleOrNull { it.stage == stage }

    @Test
    fun `a stage that ran in a failed attempt and again in the retry reports both`() {
        // Episode 226: the first run's dedup call was paid for and then rolled out of the episode
        // columns. It is still a request the episode made.
        call(CostStage.DEDUP, episodeId = episodeId, resolvedCostUsd = 0.011451)
        call(CostStage.DEDUP, episodeId = episodeId, resolvedCostUsd = 0.002)

        val dedup = totals(CostStage.DEDUP)!!
        assertEquals(2, dedup.calls)
        assertEquals(0.013451, dedup.costUsd!!, 1e-9)
        assertEquals(200, dedup.inputTokens)
    }

    @Test
    fun `scoring and recap are told apart although both record themselves as filter`() {
        candidateRepository.insertIgnore(episodeId, 10L, CandidateOutcome.USED.name)
        call("filter", articleId = 10L, resolvedCostUsd = 0.004)
        call("filter", episodeId = episodeId, resolvedCostUsd = 0.007)

        assertEquals(0.004, totals(CostStage.SCORE)!!.costUsd!!, 1e-9)
        assertEquals(0.007, totals(CostStage.RECAP)!!.costUsd!!, 1e-9)
    }

    @Test
    fun `a stage holding a request whose cost was never resolved says so`() {
        call(CostStage.COMPOSE, episodeId = episodeId, resolvedCostUsd = 0.02)
        call(CostStage.COMPOSE, episodeId = episodeId, resolvedCostUsd = null, costSource = null)

        // The caller reads this and leaves the stage on its persisted column: summing what is here
        // would report less than the episode was charged.
        assertEquals(1, totals(CostStage.COMPOSE)!!.unresolvedCalls)
    }

    @Test
    fun `a failed request is counted as a request but never as an unresolved cost`() {
        call(CostStage.DEDUP, episodeId = episodeId, resolvedCostUsd = 0.02)
        call(
            CostStage.DEDUP, episodeId = episodeId, inputTokens = 0, outputTokens = 0,
            resolvedCostUsd = null, costSource = LlmCostSource.UNKNOWN, outcome = "error"
        )

        val dedup = totals(CostStage.DEDUP)!!
        // It really happened, so the request list shows it and this count must agree.
        assertEquals(2, dedup.calls)
        // It was not charged, so it neither adds cost nor blocks the stage from being projected.
        assertEquals(0, dedup.unresolvedCalls)
        assertEquals(0.02, dedup.costUsd!!, 1e-9)
    }

    @Test
    fun `the judge is not billed to the episode`() {
        call("eval", episodeId = episodeId)

        assertNull(totals("eval"))
    }

    @Test
    fun `a stage reports the sources its requests carried`() {
        call(CostStage.DEDUP, episodeId = episodeId, costSource = LlmCostSource.API)
        call(CostStage.DEDUP, episodeId = episodeId, costSource = LlmCostSource.TABLE)

        assertEquals(
            setOf(LlmCostSource.API, LlmCostSource.TABLE),
            totals(CostStage.DEDUP)!!.sources.toSet()
        )
    }

    @Test
    fun `scoring is complete only when every candidate has a request behind it`() {
        candidateRepository.insertIgnore(episodeId, 10L, CandidateOutcome.USED.name)
        candidateRepository.insertIgnore(episodeId, 11L, CandidateOutcome.USED.name)
        call("filter", articleId = 10L)

        val partial = llmCallRepository.scoreAttribution(episodeId)
        assertEquals(2, partial.candidates)
        assertEquals(1, partial.attributed)
        assertFalse(partial.isComplete)

        call("filter", articleId = 11L)
        assertTrue(llmCallRepository.scoreAttribution(episodeId).isComplete)
    }

    @Test
    fun `an episode that recorded no candidates is never complete`() {
        // Nothing to be complete about: it predates candidates being recorded at all, and reading
        // zero of zero as complete would project a score stage of nothing.
        assertFalse(llmCallRepository.scoreAttribution(episodeId).isComplete)
    }

    @Test
    fun `the request list and the stage totals count the same requests`() {
        candidateRepository.insertIgnore(episodeId, 10L, CandidateOutcome.USED.name)
        call("filter", articleId = 10L)
        call("filter", episodeId = episodeId)
        call(CostStage.DEDUP, episodeId = episodeId)
        call(CostStage.DEDUP, episodeId = episodeId)
        call(CostStage.GATE, episodeId = episodeId)

        // The two views are read from the same rows, so a reader cannot see one count of a stage's
        // requests on the latency page and a different one in the cost breakdown.
        val listed = llmCallRepository.requestsForEpisode(episodeId).size
        val totalled = llmCallRepository.stageTotalsForEpisode(episodeId).sumOf { it.calls }
        assertEquals(listed, totalled)
        assertEquals(2, totals(CostStage.DEDUP)!!.calls)
    }
}
