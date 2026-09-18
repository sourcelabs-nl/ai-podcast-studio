package com.aisummarypodcast.llm

/**
 * What the scoring stage needs beyond the articles, the podcast and the model.
 *
 * Both fields describe the run rather than the articles, and both are absent on the paths that
 * score outside a generation, so they travel together rather than as a tail of optional positional
 * arguments. This mirrors [ComposeContext], which plays the same role for composition.
 *
 * [sourceLabels] maps a source id to the domain and path its log lines name it by.
 *
 * [episodeId] is the episode these articles are being scored for, recorded on the request
 * telemetry. It is null for ad-hoc scoring and preview runs, which have no episode to name.
 */
data class ScoringContext(
    val sourceLabels: Map<String, String> = emptyMap(),
    val episodeId: Long? = null
)
