package com.aisummarypodcast.llm

import java.time.Instant
import kotlin.time.Duration

/** Outcome of one LLM request, as recorded in `llm_calls.outcome`. */
enum class LlmCallOutcome(val value: String) {
    OK("ok"),
    ERROR("error")
}

/**
 * The `errorType` a request that ran out of time is recorded with.
 *
 * A timeout counts as latency and every other failure does not, so the distinction is read back in
 * SQL. Recording it as a fixed value rather than as the failing exception's class name keeps that
 * query from silently matching nothing the day a client wraps its timeouts in a different type.
 */
const val TIMEOUT_ERROR_TYPE = "timeout"

/**
 * The `errorType` for [error]: [TIMEOUT_ERROR_TYPE] when it is a request that ran out of time, and
 * the exception's simple class name otherwise.
 *
 * The cause chain is walked because the timeout that matters is raised deep in the HTTP client and
 * reaches here wrapped: Spring's `ResourceAccessException` carries a `SocketTimeoutException`, and
 * a coroutine-side timeout arrives as its own type entirely.
 */
fun errorTypeOf(error: Throwable): String {
    var current: Throwable? = error
    while (current != null) {
        if (current is java.net.SocketTimeoutException ||
            current is java.util.concurrent.TimeoutException ||
            current is java.net.http.HttpTimeoutException
        ) {
            return TIMEOUT_ERROR_TYPE
        }
        current = current.cause.takeIf { it !== current }
    }
    return error.javaClass.simpleName
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
