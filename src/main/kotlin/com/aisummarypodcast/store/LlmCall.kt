package com.aisummarypodcast.store

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table

/**
 * One LLM request and how long it took.
 *
 * Written per HTTP request rather than per pipeline stage: a stage that uses tools issues several
 * requests, and only the per-request figure is comparable to the stage timeouts, which are okhttp
 * callTimeouts. [durationMs] therefore never includes the local tool execution between two
 * requests.
 *
 * [cacheHit] rows performed no network request and [outcome] `error` rows report the time until the
 * failure (a timeout reports the timeout), so both are excluded when latency is read back.
 *
 * [episodeId] is the episode the request was issued for. It is null for requests issued outside a
 * generation, such as a preview or ad-hoc source scoring, and for every row written before the
 * column existed.
 *
 * [resolvedCostUsd] is what the request is counted as having cost, and [costSource] says where
 * that figure came from. It is written with the row rather than derived when the stage that issued
 * the request ends, because a request that was answered was paid for whether or not anything after
 * it succeeded. [reportedCostUsd] stays what the provider stated; the two are in the same unit so
 * one call's values can be compared without converting either.
 *
 * [articleId] is the article the request was issued for, which only the scoring stage has: every
 * other stage covers a whole set. A scoring request is issued when the article arrives, before the
 * episode that uses it exists, so it names an article and no episode, and an episode gathers those
 * requests through its candidates.
 *
 * [servedProvider] is the upstream provider OpenRouter routed the request to, and [reasoningTokens]
 * how many of [outputTokens] were reasoning. Both are null where the response did not say, which
 * includes cache hits and failed requests.
 *
 * [generationId] is OpenRouter's id for the request, and the columns after it are OpenRouter's own
 * account of it, fetched shortly after the request returned (see `GenerationStatsService`). They are
 * written by a separate update rather than through this entity, so they stay null here on insert.
 */
@Table("llm_calls")
data class LlmCall(
    @Id val id: Long? = null,
    val startedAt: String,
    val stage: String,
    val provider: String,
    val model: String,
    val durationMs: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val reportedCostUsd: Double? = null,
    val resolvedCostUsd: Double? = null,
    val costSource: String? = null,
    val cacheHit: Boolean,
    val outcome: String,
    val errorType: String? = null,
    val episodeId: Long? = null,
    val articleId: Long? = null,
    val servedProvider: String? = null,
    val reasoningTokens: Int? = null,
    val generationId: String? = null,
    val firstContentMs: Long? = null,
    val generationTimeMs: Long? = null,
    val nativeCompletionTokens: Int? = null,
    val nativeReasoningTokens: Int? = null,
    val finishReason: String? = null,
    val providerAttemptsJson: String? = null,
    @Version val version: Long? = null
)
