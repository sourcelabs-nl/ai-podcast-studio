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

data class LlmCallLatencyResponse(
    val since: String,
    val stages: List<StageLatencyResponse>
)
