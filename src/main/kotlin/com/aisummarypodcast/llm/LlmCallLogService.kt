package com.aisummarypodcast.llm

import com.aisummarypodcast.store.LlmCall
import com.aisummarypodcast.store.LlmCallRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Records one row per LLM request.
 *
 * This is observability and must never change the outcome of the call it observes, so every failure
 * to write is logged and swallowed: a busy database or a schema problem costs a measurement, not an
 * episode. The write runs in its own transaction because the caller's transaction may still roll
 * back, and a request that was really issued (and really cost money) should stay recorded when it
 * does.
 */
@Service
class LlmCallLogService(private val llmCallRepository: LlmCallRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(record: LlmCallRecord) {
        try {
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
                    cacheHit = record.cacheHit,
                    outcome = record.outcome.value,
                    errorType = record.errorType,
                    episodeId = record.attribution.episodeId,
                    articleId = record.attribution.articleId
                )
            )
        } catch (e: RuntimeException) {
            log.warn(
                "Failed to record LLM call telemetry for stage={} model={}: {}",
                record.stage, record.model, e.message
            )
        }
    }

    /** Deletes records that have aged past the retention window, so the log stays bounded. */
    fun deleteOlderThan(cutoff: Instant) {
        llmCallRepository.deleteByStartedAtLessThan(cutoff.toString())
    }
}
