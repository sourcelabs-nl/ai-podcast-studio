package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.LlmCallRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Reads back the measured latency of LLM requests, per stage, against the timeout each stage is
 * configured with.
 *
 * Every stage is reported even when it issued nothing in the window, with a zero sample count: a
 * stage that is silently absent reads as "no problem here", while a percentile over three requests
 * reads as authoritative unless the count is shown next to it.
 */
@Service
class LlmCallLatencyService(
    private val llmCallRepository: LlmCallRepository,
    private val appProperties: AppProperties
) {

    fun latencySince(window: Duration): LlmCallLatencyResponse {
        val since = Instant.now().minus(window)
        val measured = llmCallRepository.latencyPercentilesSince(since.toString()).associateBy { it.stage }

        val stages = PipelineStage.entries.map { stage ->
            val latency = measured[stage.value]
            StageLatencyResponse(
                stage = stage.value,
                samples = latency?.samples ?: 0,
                p50Ms = latency?.p50Ms,
                p90Ms = latency?.p90Ms,
                p95Ms = latency?.p95Ms,
                p99Ms = latency?.p99Ms,
                timeoutMs = stage.timeout(appProperties.llm.timeouts).toMillis()
            )
        }
        return LlmCallLatencyResponse(since = since.toString(), stages = stages)
    }
}
