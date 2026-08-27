package com.aisummarypodcast.source

import java.net.URI

data class SourceArticleCounts(
    val sourceId: String,
    val total: Int,
    val relevant: Int
)

/**
 * Optional, related polling/behavior settings for a source, bundled so [SourceService.create] and
 * [SourceService.update] take one parameter object instead of a long positional argument list.
 */
data class SourceConfig(
    val pollIntervalMinutes: Int = 30,
    val enabled: Boolean = true,
    val aggregate: Boolean? = null,
    val maxFailures: Int? = null,
    val maxBackoffHours: Int? = null,
    val pollDelaySeconds: Int? = null,
    val categoryFilter: String? = null,
    val label: String? = null
)

/**
 * Host of [url], or null when it cannot be parsed.
 *
 * The single definition used by everything that groups sources by host: per-host poll delays, the
 * host circuit breaker, and the scheduler's host grouping. These must agree — a breaker keyed on a
 * differently-derived host than the group it guards would silently guard nothing.
 */
fun extractSourceHost(url: String): String? =
    try {
        URI(url).host
    } catch (_: Exception) {
        null
    }

/**
 * Signals a poll failure to the circuit breaker. [SourcePoller] handles failures internally rather
 * than throwing, so this carries the classification back out as a thrown type.
 *
 * Permanent and transient failures are distinct classes so `application.yaml` can list one under
 * `record-exceptions` and the other under `ignore-exceptions`. That distinction matters: an ignored
 * exception counts as neither success nor failure, whereas a merely unrecorded one would count as a
 * *success* and could close a breaker on a host that never actually answered.
 */
sealed class PollFailureException(val failure: PollFailure) : RuntimeException(failure.message) {
    class Permanent(failure: PollFailure) : PollFailureException(failure)
    class Transient(failure: PollFailure) : PollFailureException(failure)

    companion object {
        fun of(failure: PollFailure): PollFailureException = when (failure) {
            is PollFailure.Permanent -> Permanent(failure)
            is PollFailure.Transient -> Transient(failure)
        }
    }
}

/** Outcome of attempting to poll one source through its host's breaker. */
sealed interface HostPollOutcome {
    /** The poll ran. [failure] is null when it succeeded. */
    data class Polled(val failure: PollFailure?) : HostPollOutcome

    /** The breaker was open, so no request was made. */
    data object Skipped : HostPollOutcome
}
