package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelType
import com.aisummarypodcast.llm.LlmCostSource
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.podcast.ScoreStageSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeCostsMapperTest {

    private fun episode(
        scoreIn: Int = 0, scoreOut: Int = 0, scoreCost: Int = 0,
        dedupIn: Int = 0, dedupOut: Int = 0, dedupCost: Int = 0,
        gateIn: Int = 0, gateCost: Int = 0, gateCalls: Int = 0, gateReported: Double? = null,
        composeIn: Int = 0, composeOut: Int = 0, composeCost: Int = 0,
        recapIn: Int = 0, recapOut: Int = 0, recapCost: Int = 0,
        scoreReported: Double? = null, dedupReported: Double? = null,
        composeReported: Double? = null, recapReported: Double? = null,
        ttsChars: Int? = null, ttsCost: Int? = null, ttsCalls: Int? = null,
        researchCalls: Int = 0, researchCost: Int? = null,
        filterModel: String? = "anthropic/claude-haiku-4.5",
        composeModel: String? = "anthropic/claude-sonnet-4",
        ttsModel: String? = "inworld-tts-2",
        llmCostSource: LlmCostSource? = null
    ) = Episode(
        id = 1L, podcastId = "p1", generatedAt = "now", scriptText = "",
        status = EpisodeStatus.GENERATED,
        filterModel = filterModel, composeModel = composeModel, ttsModel = ttsModel,
        ttsCharacters = ttsChars, ttsCostCents = ttsCost, ttsCalls = ttsCalls,
        researchCalls = researchCalls, researchCostCents = researchCost,
        scoreInputTokens = scoreIn, scoreOutputTokens = scoreOut, scoreCostCents = scoreCost,
        dedupInputTokens = dedupIn, dedupOutputTokens = dedupOut, dedupCostCents = dedupCost,
        dedupGateInputTokens = gateIn, dedupGateCostCents = gateCost, dedupGateCalls = gateCalls,
        dedupGateReportedCostCents = gateReported,
        composeInputTokens = composeIn, composeOutputTokens = composeOut, composeCostCents = composeCost,
        recapInputTokens = recapIn, recapOutputTokens = recapOut, recapCostCents = recapCost,
        scoreReportedCostCents = scoreReported, dedupReportedCostCents = dedupReported,
        composeReportedCostCents = composeReported, recapReportedCostCents = recapReported,
        llmCostSource = llmCostSource
    )

    @Test
    fun `totalCostCents sums all stages`() {
        val resp = episode(
            scoreCost = 1, dedupCost = 2, composeCost = 10, recapCost = 1,
            ttsCost = 25, researchCost = 3
        ).toResponse(scoreStage = ScoreStageSummary(calls = 5))
        assertEquals(42.0, resp.costs.totalCostCents)
    }

    @Test
    fun `the gate names its configured model only when it issued requests`() {
        val ran = episode(gateIn = 900, gateCalls = 2, gateReported = 0.07)
            .toResponse(dedupGateModel = "typesafe/jev-1.13")
        assertEquals("typesafe/jev-1.13", ran.costs.dedupGate.model)

        val never = episode().toResponse(dedupGateModel = "typesafe/jev-1.13")
        assertNull(never.costs.dedupGate.model)
    }

    @Test
    fun `the gate is a row of its own beside the stage it relieves`() {
        val resp = episode(
            dedupIn = 5000, dedupCost = 2, dedupReported = 2.0,
            gateIn = 900, gateCalls = 2, gateReported = 0.07
        ).toResponse(scoreStage = ScoreStageSummary(calls = 5))

        assertEquals(2.0, resp.costs.dedup.costCents)
        assertEquals(1, resp.costs.dedup.calls)
        assertEquals(0.07, resp.costs.dedupGate.costCents)
        // Stored rather than derived: the gate chunks and retries, so its count does not follow
        // from its tokens.
        assertEquals(2, resp.costs.dedupGate.calls)
        assertEquals(900, resp.costs.dedupGate.inputTokens)
        assertEquals(2.07, resp.costs.totalCostCents)
    }

    @Test
    fun `an episode that ran without a gate reports it at zero`() {
        val resp = episode(dedupIn = 5000, dedupCost = 2, dedupReported = 2.0)
            .toResponse(scoreStage = ScoreStageSummary(calls = 5))

        assertEquals(0, resp.costs.dedupGate.calls)
        assertEquals(0.0, resp.costs.dedupGate.costCents)
        assertEquals(2.0, resp.costs.totalCostCents)
    }

    @Test
    fun `the dropped candidates break the score row down without adding to the total`() {
        val resp = episode(scoreCost = 4, scoreReported = 4.0).toResponse(
            scoreStage = ScoreStageSummary(calls = 188, droppedCalls = 67, droppedCostCents = 1.5)
        )

        assertEquals(188, resp.costs.score.calls)
        assertEquals(67, resp.costs.score.droppedCalls)
        assertEquals(1.5, resp.costs.score.droppedCostCents)
        // The dropped cost is already inside the score row; adding it would charge it twice.
        assertEquals(4.0, resp.costs.totalCostCents)
    }

    @Test
    fun `an episode with no recorded candidates reports nothing dropped`() {
        val resp = episode(scoreCost = 4, scoreReported = 4.0)
            .toResponse(scoreStage = ScoreStageSummary(calls = 12))

        assertEquals(12, resp.costs.score.calls)
        assertEquals(0, resp.costs.score.droppedCalls)
        assertEquals(0.0, resp.costs.score.droppedCostCents)
    }

    @Test
    fun `score row carries article count and filter model`() {
        val resp = episode(
            scoreIn = 1000, scoreOut = 200, scoreCost = 3
        ).toResponse(scoreStage = ScoreStageSummary(calls = 5))
        assertEquals("anthropic/claude-haiku-4.5", resp.costs.score.model)
        assertEquals(5, resp.costs.score.calls)
        assertEquals(1000, resp.costs.score.inputTokens)
        assertEquals(200, resp.costs.score.outputTokens)
        assertEquals(3.0, resp.costs.score.costCents)
    }

    @Test
    fun `compose row uses compose model and shows 1 call when tokens present`() {
        val resp = episode(composeIn = 500, composeOut = 300, composeCost = 10).toResponse()
        assertEquals("anthropic/claude-sonnet-4", resp.costs.compose.model)
        assertEquals(1, resp.costs.compose.calls)
    }

    @Test
    fun `stage row shows 0 calls when stage did not run`() {
        val resp = episode().toResponse()
        assertEquals(0, resp.costs.dedup.calls)
        assertEquals(0, resp.costs.compose.calls)
        assertEquals(0, resp.costs.recap.calls)
    }

    @Test
    fun `tts row reflects characters, cost and call count`() {
        val resp = episode(ttsChars = 12000, ttsCost = 25, ttsCalls = 7).toResponse()
        assertEquals(7, resp.costs.tts.calls)
        assertEquals(12000, resp.costs.tts.characters)
        assertEquals(25.0, resp.costs.tts.costCents)
    }

    @Test
    fun `research row reflects call count and cost`() {
        val resp = episode(researchCalls = 3, researchCost = 3).toResponse()
        assertEquals(3, resp.costs.research.calls)
        assertEquals(3.0, resp.costs.research.costCents)
    }

    @Test
    fun `nullable tts and research collapse to zero in response`() {
        val resp = episode().toResponse()
        assertEquals(0, resp.costs.tts.calls)
        assertEquals(0, resp.costs.tts.characters)
        assertEquals(0.0, resp.costs.tts.costCents)
        assertEquals(0, resp.costs.research.calls)
        assertEquals(0.0, resp.costs.research.costCents)
    }

    @Test
    fun `reported score cost is preferred over recomputation from tokens`() {
        val models = mapOf(
            "openrouter" to mapOf(
                "anthropic/claude-haiku-4.5" to ModelCost(type = ModelType.LLM, inputCostPerMtok = 1.00, outputCostPerMtok = 5.00)
            )
        )
        val resp = episode(
            scoreIn = 4785, scoreOut = 1899, scoreCost = 0, scoreReported = 0.0076
        ).toResponse(scoreStage = ScoreStageSummary(calls = 40), costFor = stageCostFnFromModels(models))
        assertEquals(0.0076, resp.costs.score.costCents)
    }

    @Test
    fun `each LLM stage row prefers its persisted reported cost over recomputation`() {
        val models = mapOf(
            "openrouter" to mapOf(
                "anthropic/claude-haiku-4.5" to ModelCost(type = ModelType.LLM, inputCostPerMtok = 1.00, outputCostPerMtok = 5.00),
                "anthropic/claude-sonnet-4" to ModelCost(type = ModelType.LLM, inputCostPerMtok = 3.00, outputCostPerMtok = 15.00)
            )
        )
        val resp = episode(
            scoreIn = 4785, scoreOut = 1899, scoreCost = 0, scoreReported = 0.0076,
            dedupIn = 2000, dedupOut = 400, dedupCost = 1, dedupReported = 4.62,
            composeIn = 5000, composeOut = 3000, composeCost = 10, composeReported = 9.81,
            recapIn = 1200, recapOut = 300, recapCost = 1, recapReported = 0.5
        ).toResponse(scoreStage = ScoreStageSummary(calls = 40), costFor = stageCostFnFromModels(models))

        assertEquals(0.0076, resp.costs.score.costCents)
        assertEquals(4.62, resp.costs.dedup.costCents)
        assertEquals(9.81, resp.costs.compose.costCents)
        assertEquals(0.5, resp.costs.recap.costCents)
        assertEquals(0.0076 + 4.62 + 9.81 + 0.5, resp.costs.totalCostCents, 0.0001)
    }

    @Test
    fun `stage without a reported cost falls back to recomputation from tokens`() {
        val models = mapOf(
            "openrouter" to mapOf(
                "anthropic/claude-sonnet-4" to ModelCost(type = ModelType.LLM, inputCostPerMtok = 3.00, outputCostPerMtok = 15.00)
            )
        )
        // 5000 * 3.00 + 3000 * 15.00 per Mtok = $0.06 = 6.0 cents, not the persisted 10.
        val resp = episode(
            composeIn = 5000, composeOut = 3000, composeCost = 10, composeReported = null
        ).toResponse(costFor = stageCostFnFromModels(models))
        assertEquals(6.0, resp.costs.compose.costCents, 0.0001)
    }

    @Test
    fun `stage without a reported cost or model rate falls back to persisted cents`() {
        val resp = episode(
            dedupIn = 2000, dedupOut = 400, dedupCost = 3, dedupReported = null
        ).toResponse()
        assertEquals(3.0, resp.costs.dedup.costCents)
    }

    @Test
    fun `sub-cent score cost is recomputed from tokens when nothing was reported`() {
        val models = mapOf(
            "openrouter" to mapOf(
                "deepseek/deepseek-v4-flash" to ModelCost(type = ModelType.LLM, inputCostPerMtok = 0.0983, outputCostPerMtok = 0.1966)
            )
        )
        val resp = episode(
            scoreIn = 4785, scoreOut = 1899, scoreCost = 0, filterModel = "deepseek/deepseek-v4-flash"
        ).toResponse(scoreStage = ScoreStageSummary(calls = 40), costFor = stageCostFnFromModels(models))
        assertEquals(0.0843, resp.costs.score.costCents, 0.0001)
    }

    @Test
    fun `cost source is exposed for a reported-cost episode`() {
        val resp = episode(llmCostSource = LlmCostSource.API).toResponse()
        assertEquals("API", resp.costs.costSource)
    }

    @Test
    fun `legacy episode has a null cost source and unchanged numbers`() {
        val resp = episode(scoreCost = 1, dedupCost = 2, composeCost = 10, recapCost = 1).toResponse()
        assertNull(resp.costs.costSource)
        assertEquals(14.0, resp.costs.totalCostCents)
    }
}
