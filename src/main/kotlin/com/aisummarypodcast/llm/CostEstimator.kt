package com.aisummarypodcast.llm

import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.store.Article
import kotlin.math.roundToInt

object CostEstimator {

    fun estimateLlmCostCents(inputTokens: Int, outputTokens: Int, cost: ModelCost?): Int? =
        estimateLlmCostCentsExact(inputTokens, outputTokens, cost)?.roundToInt()

    /**
     * Fractional-cent variant used for the per-stage cost breakdown display. Cheap models
     * (e.g. deepseek-v4-flash at ~$0.10/Mtok) produce stage costs well under a cent, which
     * [estimateLlmCostCents] rounds to 0 and the UI then hides. Keep full precision here so
     * sub-cent stage costs stay visible; persistence and budget enforcement still use the
     * rounded integer-cent value.
     */
    fun estimateLlmCostCentsExact(inputTokens: Int, outputTokens: Int, cost: ModelCost?): Double? {
        val inputCost = cost?.inputCostPerMtok ?: return null
        val outputCost = cost.outputCostPerMtok ?: return null
        val costUsd = (inputTokens * inputCost + outputTokens * outputCost) / 1_000_000.0
        return costUsd * 100
    }

    fun estimateTtsCostCents(characters: Int, models: Map<String, Map<String, ModelCost>>, provider: String, model: String? = null): Int? {
        val providerModels = models[provider] ?: return null
        val cost = (model?.let { providerModels[it] } ?: providerModels.values.firstOrNull())
            ?: return null
        val rate = cost.costPerMillionChars ?: return null
        val costUsd = characters * rate / 1_000_000.0
        return (costUsd * 100).roundToInt()
    }

    /**
     * Estimated cost of LLM relevance-scoring the given articles (no composition). Used by the
     * eager-ranking cost gate, which only scores and never composes.
     */
    fun estimateScoringCostCents(articles: List<Article>, filterModel: ResolvedModel): Int? {
        val scoringInputTokens = articles.sumOf { it.body.length / 4 }
        val scoringOutputTokens = 200 * articles.size
        return estimateLlmCostCents(scoringInputTokens, scoringOutputTokens, filterModel.cost)
    }

    fun estimatePipelineCostCents(
        articles: List<Article>,
        filterModel: ResolvedModel,
        composeModel: ResolvedModel,
        targetWords: Int
    ): Int? {
        val scoringCost = estimateScoringCostCents(articles, filterModel)

        val compositionInputTokens = articles.size * 200
        val compositionOutputTokens = (targetWords * 1.3).roundToInt()
        val compositionCost = estimateLlmCostCents(compositionInputTokens, compositionOutputTokens, composeModel.cost)

        if (scoringCost == null || compositionCost == null) return null
        return scoringCost + compositionCost
    }

    fun addNullableCosts(existing: Int?, additional: Int?): Int? {
        if (existing == null && additional == null) return null
        return (existing ?: 0) + (additional ?: 0)
    }
}
