package com.aisummarypodcast.podcast

import com.aisummarypodcast.llm.LlmCallOutcome
import com.aisummarypodcast.llm.RequestPhases
import com.aisummarypodcast.store.LlmCallRow

/**
 * Where a run's compose time went, summed over its answered compose requests from OpenRouter's
 * account of each (see [RequestPhases]): [startupMs] until the provider started responding,
 * [reasoningMs] reasoning before the first answer token, [writingMs] producing the answer,
 * [reasoningShare] the part of the native output that was reasoning, [tokensPerSecond] native output
 * over reasoning and writing time, and [fallbackAttempts] the upstream attempts beyond the first per
 * request. A value is null when a request did not report what it is derived from.
 */
data class ComposeBreakdown(
    val startupMs: Long?,
    val reasoningMs: Long?,
    val writingMs: Long?,
    val reasoningShare: Double?,
    val tokensPerSecond: Double?,
    val fallbackAttempts: Int
) {
    companion object {
        /**
         * The breakdown of [composeCalls], or null when there is none to give: no answered request,
         * or one whose stats have not (yet) been fetched. Cached and failed requests are left out,
         * as neither reached a provider's generation.
         */
        fun of(composeCalls: List<LlmCallRow>): ComposeBreakdown? {
            val answered = composeCalls.filter { it.outcome == LlmCallOutcome.OK.value && !it.cacheHit }
            if (answered.isEmpty()) return null
            val stats = answered.map { it.generationStats ?: return null }
            val phases = stats.map { it.phases() }
            val startupMs = phases.sumOrNull { it.startupMs }
            val reasoningMs = phases.sumOrNull { it.reasoningMs }
            val writingMs = phases.sumOrNull { it.writingMs }
            val completionTokens = stats.sumOrNull { it.nativeCompletionTokens?.toLong() }
            val reasoningTokens = stats.sumOrNull { it.nativeReasoningTokens?.toLong() }
            val producingSeconds = if (reasoningMs != null && writingMs != null) (reasoningMs + writingMs) / 1000.0 else null
            return ComposeBreakdown(
                startupMs = startupMs,
                reasoningMs = reasoningMs,
                writingMs = writingMs,
                reasoningShare = ratio(reasoningTokens, completionTokens),
                tokensPerSecond = ratio(completionTokens, producingSeconds),
                fallbackAttempts = stats.sumOf { (it.attempts.size - 1).coerceAtLeast(0) }
            )
        }

        private fun <T> List<T>.sumOrNull(value: (T) -> Long?): Long? =
            map { value(it) ?: return null }.sum()

        private fun ratio(numerator: Number?, denominator: Number?): Double? {
            val divisor = denominator?.toDouble()?.takeIf { it > 0 } ?: return null
            return numerator?.toDouble()?.div(divisor)
        }
    }
}

/** Means of [ComposeBreakdown] over a variant's completed runs; each is null when no run reported it. */
data class ComposeBreakdownMeans(
    val startupMs: Double?,
    val reasoningMs: Double?,
    val writingMs: Double?,
    val reasoningShare: Double?,
    val tokensPerSecond: Double?,
    val fallbackAttempts: Double?
)
