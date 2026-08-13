package com.aisummarypodcast.llm

import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.store.Article
import kotlin.math.roundToInt

/** A cost in fractional cents together with the way it was arrived at. */
data class ResolvedLlmCost(
    val costCents: Double?,
    val source: LlmCostSource
)

/** One call's contribution to a multi-call stage total (the score stage runs one call per article). */
data class LlmCallCost(
    val inputTokens: Int,
    val outputTokens: Int,
    val reportedCostUsd: Double?
)

object CostEstimator {

    private const val USD_TO_CENTS = 100.0

    /**
     * Resolves a single call's cost: the provider-reported value when present, otherwise the
     * configured per-Mtok rates, otherwise unknown. Defining the precedence here keeps it out of
     * the individual call sites (scoring, dedup, compose, recap).
     *
     * The result stays in fractional cents so a sub-cent reported cost (e.g. 7.6E-5 USD is 0.0076
     * cents) survives; persistence and the budget gate round it to integer cents as before.
     */
    fun resolveLlmCost(usage: TokenUsage, cost: ModelCost?): ResolvedLlmCost {
        val reported = usage.reportedCostUsd
        if (reported != null) {
            val source = if (usage.reportedCostFromCache) LlmCostSource.API_CACHED else LlmCostSource.API
            return ResolvedLlmCost(reported * USD_TO_CENTS, source)
        }
        val estimated = estimateLlmCostCentsExact(usage.inputTokens, usage.outputTokens, cost)
            ?: return ResolvedLlmCost(null, LlmCostSource.UNKNOWN)
        return ResolvedLlmCost(estimated, LlmCostSource.TABLE)
    }

    /**
     * Totals a stage that made several calls. Reported costs are summed (they are full-precision
     * USD values and lose nothing when added); calls that reported nothing are estimated together
     * from their own tokens and the configured rate, and the stage is marked
     * [LlmCostSource.MIXED]. Never returns the bare sum of the reported costs when some calls
     * reported nothing — a partial sum is indistinguishable from a complete one and would silently
     * understate the stage.
     */
    fun aggregateStageCost(calls: List<LlmCallCost>, cost: ModelCost?): ResolvedLlmCost {
        if (calls.isEmpty()) return ResolvedLlmCost(null, LlmCostSource.UNKNOWN)
        val (reporting, silent) = calls.partition { it.reportedCostUsd != null }
        val reportedCents = reporting.sumOf { it.reportedCostUsd!! } * USD_TO_CENTS

        if (silent.isEmpty()) return ResolvedLlmCost(reportedCents, LlmCostSource.API)

        val gapEstimate = estimateLlmCostCentsExact(
            silent.sumOf { it.inputTokens },
            silent.sumOf { it.outputTokens },
            cost
        )
        if (reporting.isEmpty()) {
            return gapEstimate?.let { ResolvedLlmCost(it, LlmCostSource.TABLE) }
                ?: ResolvedLlmCost(null, LlmCostSource.UNKNOWN)
        }
        // Without a configured rate the non-reporting calls contribute nothing; the stage stays
        // MIXED so the shortfall is visible rather than implied to be complete.
        return ResolvedLlmCost(reportedCents + (gapEstimate ?: 0.0), LlmCostSource.MIXED)
    }

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

    /** USD variant of [addNullableCosts], used to accumulate per-article reported costs. */
    fun addNullableReportedCosts(existing: Double?, additional: Double?): Double? {
        if (existing == null && additional == null) return null
        return (existing ?: 0.0) + (additional ?: 0.0)
    }
}
