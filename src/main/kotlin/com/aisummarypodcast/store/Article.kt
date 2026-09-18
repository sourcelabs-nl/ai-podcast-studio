package com.aisummarypodcast.store

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("articles")
data class Article(
    @Id val id: Long? = null,
    val sourceId: String,
    val title: String,
    val body: String,
    val url: String,
    val publishedAt: String? = null,
    val author: String? = null,
    val contentHash: String,
    val relevanceScore: Int? = null,
    val isProcessed: Boolean = false,
    val summary: String? = null,
    val llmInputTokens: Int? = null,
    val llmOutputTokens: Int? = null,
    val llmCostCents: Int? = null,
    /** Provider-reported cost (USD) of this article's scoring call(s); null when none was reported. */
    val llmReportedCostUsd: Double? = null,
    val subtopic: String? = null,
    /**
     * How the scoring stage placed this article in time, as a [com.aisummarypodcast.llm.NewsType]
     * name; null when it was scored before the classification existed or the model did not answer
     * with a known value.
     */
    val newsType: String? = null
)
