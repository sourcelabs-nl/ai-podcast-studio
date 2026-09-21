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
 * [articleId] is the article the request was issued for, which only the scoring stage has: every
 * other stage covers a whole set. A scoring request is issued when the article arrives, before the
 * episode that uses it exists, so it names an article and no episode, and an episode gathers those
 * requests through its candidates.
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
    val cacheHit: Boolean,
    val outcome: String,
    val errorType: String? = null,
    val episodeId: Long? = null,
    val articleId: Long? = null,
    @Version val version: Long? = null
)
