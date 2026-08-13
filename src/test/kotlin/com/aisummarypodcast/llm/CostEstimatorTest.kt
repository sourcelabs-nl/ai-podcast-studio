package com.aisummarypodcast.llm

import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelType
import com.aisummarypodcast.store.Article
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

class CostEstimatorTest {

    @Test
    fun `estimates LLM cost with configured pricing`() {
        val cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 3.00, outputCostPerMtok = 15.00)
        // (10000 * 3.00 + 2000 * 15.00) / 1_000_000 = 0.06 USD = 6 cents
        assertEquals(6, CostEstimator.estimateLlmCostCents(10000, 2000, cost))
    }

    @Test
    fun `returns null when pricing not configured`() {
        assertNull(CostEstimator.estimateLlmCostCents(1000, 200, null))
    }

    @Test
    fun `returns null when only input pricing configured`() {
        val cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 3.00, outputCostPerMtok = null)
        assertNull(CostEstimator.estimateLlmCostCents(1000, 200, cost))
    }

    @Test
    fun `rounds small costs to nearest cent`() {
        val cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 0.15, outputCostPerMtok = 0.60)
        // (1000 * 0.15 + 200 * 0.60) / 1_000_000 = 0.00027 USD = 0.027 cents -> 0
        assertEquals(0, CostEstimator.estimateLlmCostCents(1000, 200, cost))
    }

    @Test
    fun `exact LLM cost keeps sub-cent precision for cheap models`() {
        // deepseek-v4-flash pricing; 4785 in + 1899 out rounds to 0 cents but is ~0.084 cents exact.
        val cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 0.0983, outputCostPerMtok = 0.1966)
        assertEquals(0, CostEstimator.estimateLlmCostCents(4785, 1899, cost))
        assertEquals(0.0843, CostEstimator.estimateLlmCostCentsExact(4785, 1899, cost)!!, 0.0001)
    }

    @Test
    fun `exact LLM cost returns null when pricing not configured`() {
        assertNull(CostEstimator.estimateLlmCostCentsExact(1000, 200, null))
    }

    @Test
    fun `estimates TTS cost`() {
        val models = mapOf(
            "openai" to mapOf("tts-1-hd" to ModelCost(type = ModelType.TTS, costPerMillionChars = 15.00))
        )
        // 50000 * 15.00 / 1_000_000 = 0.75 USD = 75 cents
        assertEquals(75, CostEstimator.estimateTtsCostCents(50000, models, "openai", "tts-1-hd"))
    }

    @Test
    fun `returns null when TTS pricing not configured for provider`() {
        val models = mapOf(
            "openai" to mapOf("tts-1-hd" to ModelCost(type = ModelType.TTS, costPerMillionChars = 15.00))
        )
        assertNull(CostEstimator.estimateTtsCostCents(8000, models, "elevenlabs"))
    }

    @Test
    fun `returns null when TTS pricing map is empty`() {
        assertNull(CostEstimator.estimateTtsCostCents(8000, emptyMap(), "openai"))
    }

    @Test
    fun `estimates ElevenLabs TTS cost`() {
        val models = mapOf(
            "elevenlabs" to mapOf("default" to ModelCost(type = ModelType.TTS, costPerMillionChars = 30.00))
        )
        assertEquals(24, CostEstimator.estimateTtsCostCents(8000, models, "elevenlabs", "default"))
    }

    @Test
    fun `estimates Inworld TTS Max cost by model name`() {
        val models = mapOf(
            "inworld" to mapOf(
                "inworld-tts-1.5-max" to ModelCost(type = ModelType.TTS, costPerMillionChars = 10.00),
                "inworld-tts-1.5-mini" to ModelCost(type = ModelType.TTS, costPerMillionChars = 5.00)
            )
        )
        assertEquals(8, CostEstimator.estimateTtsCostCents(8000, models, "inworld", "inworld-tts-1.5-max"))
    }

    @Test
    fun `estimates Inworld TTS Mini cost by model name`() {
        val models = mapOf(
            "inworld" to mapOf(
                "inworld-tts-1.5-max" to ModelCost(type = ModelType.TTS, costPerMillionChars = 10.00),
                "inworld-tts-1.5-mini" to ModelCost(type = ModelType.TTS, costPerMillionChars = 5.00)
            )
        )
        assertEquals(4, CostEstimator.estimateTtsCostCents(8000, models, "inworld", "inworld-tts-1.5-mini"))
    }

    @Test
    fun `returns null when Inworld model pricing not configured`() {
        val models = mapOf(
            "openai" to mapOf("tts-1-hd" to ModelCost(type = ModelType.TTS, costPerMillionChars = 15.00))
        )
        assertNull(CostEstimator.estimateTtsCostCents(8000, models, "inworld", "inworld-tts-1.5-max"))
    }

    @Test
    fun `falls back to first provider model when specific model not found`() {
        val models = mapOf(
            "openai" to mapOf("tts-1-hd" to ModelCost(type = ModelType.TTS, costPerMillionChars = 15.00))
        )
        // model "tts-1" not in map, falls back to first entry under "openai"
        assertEquals(75, CostEstimator.estimateTtsCostCents(50000, models, "openai", "tts-1"))
    }

    @Test
    fun `handles zero tokens`() {
        val cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 3.00, outputCostPerMtok = 15.00)
        assertEquals(0, CostEstimator.estimateLlmCostCents(0, 0, cost))
    }

    // --- estimatePipelineCostCents tests ---

    private val cheapModel = ResolvedModel(
        provider = "openrouter", model = "gpt-4o-mini",
        cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 0.15, outputCostPerMtok = 0.60)
    )

    private val capableModel = ResolvedModel(
        provider = "openrouter", model = "claude-sonnet",
        cost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 3.00, outputCostPerMtok = 15.00)
    )

    private fun article(body: String) = Article(
        sourceId = "src1", title = "Test", body = body, url = "http://test.com", contentHash = "hash"
    )

    @Test
    fun `estimates pipeline cost for articles with default models`() {
        val articles = (1..10).map { article("x".repeat(2000)) }
        val cost = CostEstimator.estimatePipelineCostCents(articles, cheapModel, capableModel, 1500)
        assertEquals(4, cost)
    }

    @Test
    fun `estimates pipeline cost with varying article sizes`() {
        val articles = listOf(
            article("x".repeat(500)),
            article("x".repeat(3000)),
            article("x".repeat(8000))
        )
        val cost = CostEstimator.estimatePipelineCostCents(articles, cheapModel, capableModel, 1500)
        assertEquals(3, cost)
    }

    @Test
    fun `returns null when pricing not configured for filter model`() {
        val noPricingModel = ResolvedModel(provider = "openrouter", model = "test", cost = null)
        val articles = listOf(article("x".repeat(1000)))
        assertNull(CostEstimator.estimatePipelineCostCents(articles, noPricingModel, capableModel, 1500))
    }

    @Test
    fun `returns null when pricing not configured for compose model`() {
        val noPricingModel = ResolvedModel(provider = "openrouter", model = "test", cost = null)
        val articles = listOf(article("x".repeat(1000)))
        assertNull(CostEstimator.estimatePipelineCostCents(articles, cheapModel, noPricingModel, 1500))
    }

    @Test
    fun `returns null when pricing not configured for both models`() {
        val noPricingModel = ResolvedModel(provider = "openrouter", model = "test", cost = null)
        val articles = listOf(article("x".repeat(1000)))
        assertNull(CostEstimator.estimatePipelineCostCents(articles, noPricingModel, noPricingModel, 1500))
    }

    // --- estimateScoringCostCents tests ---

    @Test
    fun `estimateScoringCostCents covers only scoring cost`() {
        val articles = (1..10).map { article("x".repeat(2000)) }
        // input 5000 tok, output 2000 tok at cheap pricing -> 0.195 cents -> 0
        assertEquals(0, CostEstimator.estimateScoringCostCents(articles, cheapModel))
        // capable pricing: (5000*3 + 2000*15) / 1_000_000 * 100 = 4.5 cents -> 5
        assertEquals(5, CostEstimator.estimateScoringCostCents(articles, capableModel))
    }

    @Test
    fun `estimateScoringCostCents returns null without pricing`() {
        val noPricingModel = ResolvedModel(provider = "openrouter", model = "test", cost = null)
        assertNull(CostEstimator.estimateScoringCostCents(listOf(article("x".repeat(1000))), noPricingModel))
    }

    // --- resolveLlmCost tests ---

    private val filterCost = ModelCost(type = ModelType.LLM, inputCostPerMtok = 3.00, outputCostPerMtok = 15.00)

    @Test
    fun `reported cost is preferred over the configured rates`() {
        val usage = TokenUsage(10000, 2000, reportedCostUsd = 0.00042)

        val resolved = CostEstimator.resolveLlmCost(usage, filterCost)

        assertEquals(0.042, resolved.costCents!!, 1e-9)
        assertEquals(LlmCostSource.API, resolved.source)
    }

    @Test
    fun `replayed reported cost resolves as API_CACHED`() {
        val usage = TokenUsage(10000, 2000, reportedCostUsd = 0.00042, reportedCostFromCache = true)

        assertEquals(LlmCostSource.API_CACHED, CostEstimator.resolveLlmCost(usage, filterCost).source)
    }

    @Test
    fun `falls back to the configured rates when nothing is reported`() {
        val resolved = CostEstimator.resolveLlmCost(TokenUsage(10000, 2000), filterCost)

        assertEquals(6.0, resolved.costCents!!, 1e-9)
        assertEquals(LlmCostSource.TABLE, resolved.source)
    }

    @Test
    fun `resolves to unknown without a reported cost or configured rates`() {
        val resolved = CostEstimator.resolveLlmCost(TokenUsage(10000, 2000), null)

        assertNull(resolved.costCents)
        assertEquals(LlmCostSource.UNKNOWN, resolved.source)
    }

    @Test
    fun `sub-cent reported cost survives at full precision`() {
        val resolved = CostEstimator.resolveLlmCost(TokenUsage(500, 100, reportedCostUsd = 7.6E-5), filterCost)

        assertEquals(0.0076, resolved.costCents!!, 1e-9)
        assertEquals(0, resolved.costCents!!.roundToInt())
    }

    // --- aggregateStageCost tests ---

    private fun reportingCall(cost: Double) = LlmCallCost(100, 20, cost)
    private fun silentCall(input: Int, output: Int) = LlmCallCost(input, output, null)

    @Test
    fun `stage where every call reported sums the reported costs`() {
        val calls = (1..40).map { reportingCall(0.0004) }

        val resolved = CostEstimator.aggregateStageCost(calls, filterCost)

        assertEquals(1.6, resolved.costCents!!, 1e-9)
        assertEquals(LlmCostSource.API, resolved.source)
    }

    @Test
    fun `stage with partial reporting adds a rate estimate for the gap and is MIXED`() {
        // 38 reporting calls totalling $0.0152, plus 2 that reported nothing but used 900/300 tokens.
        val calls = (1..38).map { reportingCall(0.0004) } + listOf(silentCall(900, 300))

        val resolved = CostEstimator.aggregateStageCost(calls, filterCost)

        val gapCents = (900 * 3.00 + 300 * 15.00) / 1_000_000.0 * 100
        assertEquals(1.52 + gapCents, resolved.costCents!!, 1e-9)
        assertEquals(LlmCostSource.MIXED, resolved.source)
    }

    @Test
    fun `partial stage total is never the bare sum of the reported costs`() {
        val calls = listOf(reportingCall(0.0004), silentCall(900, 300))

        val resolved = CostEstimator.aggregateStageCost(calls, filterCost)

        assertNotEquals(0.04, resolved.costCents!!)
        assertNotEquals(LlmCostSource.API, resolved.source)
    }

    @Test
    fun `stage where no call reported is estimated from the summed tokens`() {
        val calls = listOf(silentCall(600, 200), silentCall(300, 100))

        val resolved = CostEstimator.aggregateStageCost(calls, filterCost)

        assertEquals((900 * 3.00 + 300 * 15.00) / 1_000_000.0 * 100, resolved.costCents!!, 1e-9)
        assertEquals(LlmCostSource.TABLE, resolved.source)
    }

    @Test
    fun `non-reporting calls contribute nothing without rates but still force MIXED`() {
        val calls = listOf(reportingCall(0.0004), silentCall(900, 300))

        val resolved = CostEstimator.aggregateStageCost(calls, null)

        assertEquals(0.04, resolved.costCents!!, 1e-9)
        assertEquals(LlmCostSource.MIXED, resolved.source)
    }

    @Test
    fun `stage with no reported cost and no rates is unknown`() {
        val resolved = CostEstimator.aggregateStageCost(listOf(silentCall(900, 300)), null)

        assertNull(resolved.costCents)
        assertEquals(LlmCostSource.UNKNOWN, resolved.source)
    }

    @Test
    fun `stage without any calls is unknown`() {
        val resolved = CostEstimator.aggregateStageCost(emptyList(), filterCost)

        assertNull(resolved.costCents)
        assertEquals(LlmCostSource.UNKNOWN, resolved.source)
    }

    // --- ResolvedLlmCost.reportedCostCents tests ---

    @Test
    fun `reported cost cents is carried for API, API_CACHED and MIXED sources`() {
        assertEquals(0.42, ResolvedLlmCost(0.42, LlmCostSource.API).reportedCostCents)
        assertEquals(0.42, ResolvedLlmCost(0.42, LlmCostSource.API_CACHED).reportedCostCents)
        assertEquals(0.42, ResolvedLlmCost(0.42, LlmCostSource.MIXED).reportedCostCents)
    }

    @Test
    fun `reported cost cents is null for TABLE and UNKNOWN sources`() {
        assertNull(ResolvedLlmCost(0.42, LlmCostSource.TABLE).reportedCostCents)
        assertNull(ResolvedLlmCost(null, LlmCostSource.UNKNOWN).reportedCostCents)
    }

    // --- addNullableReportedCosts tests ---

    @Test
    fun `nullable reported costs accumulate and stay null when both are absent`() {
        assertNull(CostEstimator.addNullableReportedCosts(null, null))
        assertEquals(0.0004, CostEstimator.addNullableReportedCosts(null, 0.0004))
        assertEquals(0.0006, CostEstimator.addNullableReportedCosts(0.0002, 0.0004)!!, 1e-9)
    }
}
