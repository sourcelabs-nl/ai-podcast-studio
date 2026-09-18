package com.aisummarypodcast.llm

import java.time.Instant
import kotlin.time.Duration

/** Outcome of one LLM request, as recorded in `llm_calls.outcome`. */
enum class LlmCallOutcome(val value: String) {
    OK("ok"),
    ERROR("error")
}

/**
 * One LLM request as observed at the point it was issued.
 *
 * [duration] covers the request alone. A stage that uses tools issues several requests, and the
 * local tool execution between them falls outside every one of these records, which is what makes
 * them comparable to the per-request stage timeouts.
 *
 * [episodeId] names the episode the request was issued for, and is null for the paths that have no
 * episode: preview runs and ad-hoc source scoring. It is carried explicitly from the call site
 * rather than read from ambient state, because the composers issue their request on another
 * dispatcher than the one the stage started on.
 */
data class LlmCallRecord(
    val startedAt: Instant,
    val stage: String,
    val provider: String,
    val model: String,
    val duration: Duration,
    val inputTokens: Int,
    val outputTokens: Int,
    val reportedCostUsd: Double? = null,
    val cacheHit: Boolean = false,
    val outcome: LlmCallOutcome = LlmCallOutcome.OK,
    val errorType: String? = null,
    val episodeId: Long? = null
)
