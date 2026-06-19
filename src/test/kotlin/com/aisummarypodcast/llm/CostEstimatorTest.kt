package com.aisummarypodcast.llm

import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelType
import com.aisummarypodcast.store.Article
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

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
}
