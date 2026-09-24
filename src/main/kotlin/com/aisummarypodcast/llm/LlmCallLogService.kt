package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.LlmCall
import com.aisummarypodcast.store.LlmCallRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** [CostEstimator] resolves in fractional cents; `llm_calls` holds USD, as the provider reports it. */
private const val USD_TO_CENTS = 100.0

/**
 * Records one row per LLM request.
 *
 * This is observability and must never change the outcome of the call it observes, so every failure
 * to write is logged and swallowed: a busy database or a schema problem costs a measurement, not an
 * episode. The write runs in its own transaction because the caller's transaction may still roll
 * back, and a request that was really issued (and really cost money) should stay recorded when it
 * does.
 *
 * What the request cost is resolved here, in the same insert, rather than by the stage that issued
 * it: the stage resolves its total at its end, inside the transaction that rolls back, so a run
 * that paid for its calls and then failed left no cost behind at all. Resolving it here needs
 * nothing but the values already in hand and the configured rates, so it adds no I/O to the call
 * path it observes.
 */
@Service
class LlmCallLogService(
    private val llmCallRepository: LlmCallRepository,
    private val appProperties: AppProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Returns the recorded row's id, or null when the write failed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(record: LlmCallRecord): Long? {
        return try {
            val cost = resolveCost(record)
            llmCallRepository.save(
                LlmCall(
                    startedAt = record.startedAt.toString(),
                    stage = record.stage,
                    provider = record.provider,
                    model = record.model,
                    durationMs = record.duration.inWholeMilliseconds,
                    inputTokens = record.inputTokens,
                    outputTokens = record.outputTokens,
                    reportedCostUsd = record.reportedCostUsd,
                    resolvedCostUsd = cost.costCents?.let { it / USD_TO_CENTS },
                    costSource = cost.source.name,
                    cacheHit = record.cacheHit,
                    outcome = record.outcome.value,
                    errorType = record.errorType,
                    episodeId = record.attribution.episodeId,
                    articleId = record.attribution.articleId,
                    servedProvider = record.servedProvider,
                    reasoningTokens = record.reasoningTokens,
                    generationId = record.generationId
                )
            ).id
        } catch (e: RuntimeException) {
            log.warn(
                "Failed to record LLM call telemetry for stage={} model={}: {}",
                record.stage, record.model, e.message
            )
            null
        }
    }

    /** Writes OpenRouter's account of a recorded request. Like [record], a failure is logged and swallowed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordGenerationStats(callId: Long, stats: GenerationStats) {
        try {
            llmCallRepository.updateGenerationStats(callId, stats)
        } catch (e: RuntimeException) {
            log.warn("Failed to record generation stats for LLM call {}: {}", callId, e.message)
        }
    }

    /**
     * What this one request is counted as having cost: the provider's figure where it gave one,
     * the configured per-Mtok rates otherwise, and [LlmCostSource.UNKNOWN] with no value where
     * neither is available.
     *
     * A request that failed carries no cost whatever it was called with. It was not charged, and
     * the rates would happily turn whatever tokens a caller passed into a figure that nothing
     * backs. Refusing it here rather than in the query that reads the rows back keeps the row
     * itself truthful, so a second reader cannot arrive at a different total.
     *
     * [CostEstimator] works in fractional cents; the row holds USD, matching the reported value.
     */
    private fun resolveCost(record: LlmCallRecord): ResolvedLlmCost {
        if (record.outcome != LlmCallOutcome.OK) return ResolvedLlmCost(null, LlmCostSource.UNKNOWN)
        return CostEstimator.resolveLlmCost(
            TokenUsage(
                inputTokens = record.inputTokens,
                outputTokens = record.outputTokens,
                reportedCostUsd = record.reportedCostUsd,
                reportedCostFromCache = record.cacheHit
            ),
            appProperties.models[record.provider]?.get(record.model)
        )
    }

    /** Deletes records that have aged past the retention window, so the log stays bounded. */
    fun deleteOlderThan(cutoff: Instant) {
        llmCallRepository.deleteByStartedAtLessThan(cutoff.toString())
    }
}
