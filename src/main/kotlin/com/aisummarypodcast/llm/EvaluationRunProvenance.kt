package com.aisummarypodcast.llm

import com.aisummarypodcast.util.sha256

/**
 * What a compose call was actually run under, captured only for an evaluation run.
 *
 * An ablation compares k repetitions of one prompt variant against k of another, and the composer
 * is not deterministic, so a difference between two sets of scripts is only attributable if the
 * conditions of each run are known. Every field here is something that would silently invalidate
 * that comparison if it differed unnoticed: a substituted model, a different temperature, a
 * different variety rotation, a run that never reached the model, or a tool that fired in one arm
 * and not the other.
 *
 * [cacheHit] is false on every row this writes today, because the bypass disables the cache read
 * itself and a completion that was never looked up cannot be replayed. It is recorded rather than
 * assumed: a stored run states the condition instead of leaving a reader to infer it from the
 * absence of a column, and a run recorded under some later path that does not bypass stays
 * interpretable.
 */
data class EvaluationRunProvenance(
    val promptHash: String,
    val varietySelection: String,
    val composeModel: String,
    val temperature: Double,
    val cacheBypassed: Boolean,
    val cacheHit: Boolean,
    val toolsFired: Map<String, Int>
) {
    companion object {
        /**
         * Captures the conditions of a compose call. Returns null unless the run asked to bypass
         * the cache, so an ordinary generation records nothing.
         */
        fun of(
            context: ComposeContext,
            prompt: String,
            composeModel: String,
            temperature: Double,
            variety: PromptVarietySelection,
            usage: TokenUsage,
            toolBudget: ToolBudget
        ): EvaluationRunProvenance? {
            if (!context.bypassLlmCache) return null
            return EvaluationRunProvenance(
                promptHash = sha256(prompt),
                varietySelection = variety.toString(),
                composeModel = composeModel,
                temperature = temperature,
                cacheBypassed = true,
                cacheHit = usage.reportedCostFromCache,
                toolsFired = toolBudget.invocationCounts()
            )
        }
    }
}
