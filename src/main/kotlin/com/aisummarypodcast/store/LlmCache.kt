package com.aisummarypodcast.store

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("llm_cache")
data class LlmCache(
    @Id val id: Long? = null,
    val promptHash: String,
    val model: String,
    val response: String,
    val createdAt: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    /** Provider-reported cost (USD) of the cached call; null for rows stored before it was captured. */
    val reportedCostUsd: Double? = null
)
