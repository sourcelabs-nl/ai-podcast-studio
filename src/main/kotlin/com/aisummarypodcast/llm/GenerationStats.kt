package com.aisummarypodcast.llm

private const val MS_PER_SECOND = 1000.0

/**
 * OpenRouter's server-side account of one request, from `GET /api/v1/generation`.
 *
 * Measured on a live compose request, the fields mean: [generationTimeMs] spans the whole request,
 * [firstContentMs] is the time until the first answer token, which comes after the model's
 * reasoning, and each [attempts] entry's latency is how long that endpoint took to start responding.
 * The native token counts are the provider's own rather than OpenRouter's normalised ones. Every
 * value is null where OpenRouter did not report it.
 */
data class GenerationStats(
    val firstContentMs: Long?,
    val generationTimeMs: Long?,
    val nativeCompletionTokens: Int?,
    val nativeReasoningTokens: Int?,
    val finishReason: String?,
    val servedProvider: String?,
    val attempts: List<ProviderAttempt>
) {
    /** Where the request's time went; see [RequestPhases]. */
    fun phases(): RequestPhases {
        val latencies = attempts.mapNotNull { it.latencyMs }
        val startupMs = latencies.takeIf { it.isNotEmpty() && it.size == attempts.size }?.sum()
        val reasoningMs = difference(firstContentMs, startupMs)
        val writingMs = difference(generationTimeMs, firstContentMs)
        val producingMs = difference(generationTimeMs, startupMs)?.takeIf { it > 0 }
        return RequestPhases(
            startupMs = startupMs,
            reasoningMs = reasoningMs,
            writingMs = writingMs,
            tokensPerSecond = if (nativeCompletionTokens != null && producingMs != null) {
                nativeCompletionTokens / (producingMs / MS_PER_SECOND)
            } else null
        )
    }

    private fun difference(end: Long?, start: Long?): Long? =
        if (end != null && start != null) (end - start).coerceAtLeast(0) else null
}

/**
 * One request's time split into phases: [startupMs] until the serving endpoint started responding
 * (queueing and prompt processing, including every failed attempt before it), [reasoningMs] from
 * there until the first answer token, and [writingMs] producing the answer. [tokensPerSecond] is the
 * native output over reasoning and writing together. A phase is null when a value it needs is missing.
 */
data class RequestPhases(
    val startupMs: Long?,
    val reasoningMs: Long?,
    val writingMs: Long?,
    val tokensPerSecond: Double?
)

/** One upstream endpoint OpenRouter tried for a request. */
data class ProviderAttempt(
    val provider: String?,
    val status: Int?,
    val latencyMs: Long?
)
