package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.LlmCallRepository
import com.aisummarypodcast.store.LlmCallScope
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Reads back the measured latency of LLM requests, per stage, against the timeout each stage is
 * configured with, either over a recent window across all episodes or for one episode.
 *
 * Every stage is reported even when it issued nothing, with a zero sample count: a stage that is
 * silently absent reads as "no problem here", while a percentile over three requests reads as
 * authoritative unless the count is shown next to it.
 *
 * Which stages those are is [ReportedStage]'s to say, not [PipelineStage]'s. Requests are issued by
 * callers that are not pipeline stages, and one reported against the enum alone would be recorded
 * and then never read back.
 */
@Service
class LlmCallLatencyService(
    private val llmCallRepository: LlmCallRepository,
    private val episodeRepository: EpisodeRepository,
    private val appProperties: AppProperties
) {

    fun latencySince(window: Duration): LlmCallLatencyResponse {
        val since = Instant.now().minus(window)
        return LlmCallLatencyResponse(
            since = since.toString(),
            stages = stagesFor(LlmCallScope(cutoff = since.toString()))
        )
    }

    /** No window applies: every request the episode issued counts, whenever it was issued. */
    fun latencyForEpisode(episodeId: Long): LlmCallLatencyResponse =
        LlmCallLatencyResponse(since = null, stages = stagesFor(LlmCallScope(episodeId = episodeId)))

    fun requestsForEpisode(episodeId: Long): EpisodeLlmCallsResponse {
        val requests = llmCallRepository.requestsForEpisode(episodeId).map {
            LlmCallResponse(
                startedAt = it.startedAt,
                stage = it.stage,
                model = it.model,
                durationMs = it.durationMs,
                outcome = it.outcome,
                cacheHit = it.cacheHit,
                servedProvider = it.servedProvider,
                reasoningTokens = it.reasoningTokens,
                generationStats = it.generationStats,
                phases = it.generationStats?.phases()
            )
        }
        return EpisodeLlmCallsResponse(
            episodeId = episodeId,
            predatesAttribution = requests.isEmpty() && predatesAttribution(episodeId),
            requests = requests
        )
    }

    /**
     * Whether this episode was generated before any request recorded an episode. Below the earliest
     * attributed request, no row could have named an episode regardless of what the episode did, so
     * its empty result says nothing about the episode itself.
     *
     * With no attributed request anywhere, every episode predates attribution, which is true.
     */
    private fun predatesAttribution(episodeId: Long): Boolean {
        val episode = episodeRepository.findByIdOrNull(episodeId) ?: return false
        val earliest = llmCallRepository.earliestAttributedStart() ?: return true
        return episode.generatedAt < earliest
    }

    private fun stagesFor(scope: LlmCallScope): List<StageLatencyResponse> {
        val measured = llmCallRepository.latencyPercentiles(scope).associateBy { it.stage }
        return ReportedStage.all(appProperties).map { reported ->
            val latency = measured[reported.stage]
            StageLatencyResponse(
                stage = reported.stage,
                samples = latency?.samples ?: 0,
                p50Ms = latency?.p50Ms,
                p90Ms = latency?.p90Ms,
                p95Ms = latency?.p95Ms,
                p99Ms = latency?.p99Ms,
                timeoutMs = reported.timeout.toMillis()
            )
        }
    }
}
