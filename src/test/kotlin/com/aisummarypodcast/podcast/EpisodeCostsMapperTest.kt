package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelType
import com.aisummarypodcast.llm.LlmCostSource
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeCostsMapperTest {

    private fun episode(
        scoreIn: Int = 0, scoreOut: Int = 0, scoreCost: Int = 0,
        dedupIn: Int = 0, dedupOut: Int = 0, dedupCost: Int = 0,
        composeIn: Int = 0, composeOut: Int = 0, composeCost: Int = 0,
        recapIn: Int = 0, recapOut: Int = 0, recapCost: Int = 0,
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
        composeInputTokens = composeIn, composeOutputTokens = composeOut, composeCostCents = composeCost,
        recapInputTokens = recapIn, recapOutputTokens = recapOut, recapCostCents = recapCost,
        llmCostSource = llmCostSource
    )

    @Test
    fun `totalCostCents sums all stages`() {
        val resp = episode(
            scoreCost = 1, dedupCost = 2, composeCost = 10, recapCost = 1,
            ttsCost = 25, researchCost = 3
        ).toResponse(scoreCalls = 5)
        assertEquals(42.0, resp.costs.totalCostCents)
    }

    @Test
    fun `score row carries article count and filter model`() {
        val resp = episode(
            scoreIn = 1000, scoreOut = 200, scoreCost = 3
        ).toResponse(scoreCalls = 5)
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
        val resp = episode(scoreIn = 4785, scoreOut = 1899, scoreCost = 0).toResponse(
            scoreCalls = 40,
            scoreReportedCostCents = 0.0076,
            costFor = stageCostFnFromModels(models)
        )
        assertEquals(0.0076, resp.costs.score.costCents)
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
        ).toResponse(scoreCalls = 40, costFor = stageCostFnFromModels(models))
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
