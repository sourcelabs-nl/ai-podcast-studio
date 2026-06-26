package com.aisummarypodcast.source

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
