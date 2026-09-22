package com.aisummarypodcast.llm

/** Latency percentiles for one pipeline stage, in milliseconds. */
data class StageLatencyResponse(
    val stage: String,
    val samples: Int,
    val p50Ms: Long?,
    val p90Ms: Long?,
    val p95Ms: Long?,
    val p99Ms: Long?,
    /** The stage's configured request timeout, so a percentile can be read against the ceiling it informs. */
    val timeoutMs: Long
)

/**
 * [since] is the start of the window the percentiles cover, and is null when they cover one
 * episode: an episode is a bounded set of requests rather than a period.
 */
data class LlmCallLatencyResponse(
    val since: String?,
    val stages: List<StageLatencyResponse>
)

/** One recorded request of an episode. */
data class LlmCallResponse(
    val startedAt: String,
    val stage: String,
    val model: String,
    val durationMs: Long,
    val outcome: String,
    val cacheHit: Boolean,
    /** The upstream provider OpenRouter served the request from; null when unreported. */
    val servedProvider: String? = null,
    /** How many of the request's output tokens were reasoning; null when unreported. */
    val reasoningTokens: Int? = null
)

/**
 * One episode's individual requests.
 *
 * [predatesAttribution] is true when the episode was generated before requests recorded which
 * episode they belonged to. Such an episode has no requests and never will, which is a fact about
 * the records rather than about the episode, and an empty list alone cannot say which of the two
 * it is.
 */
data class EpisodeLlmCallsResponse(
    val episodeId: Long,
    val predatesAttribution: Boolean,
    val requests: List<LlmCallResponse>
)
