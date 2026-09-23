package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.ModelReference

/**
 * One variant of an experiment request. Every override is optional and keeps the podcast's own
 * value when left out, so a variant naming nothing is the baseline. [models] and [reasoningEffort]
 * are keyed by pipeline stage name (`filter`, `dedup`, `compose`, `eval`). [bypassLlmCache]
 * defaults to true for an experiment, so repeats sample fresh scripts instead of replaying the
 * first one from the LLM cache.
 */
data class ExperimentVariantRequest(
    val name: String,
    val models: Map<String, ModelReference>? = null,
    val reasoningEffort: Map<String, String>? = null,
    val providerSort: String? = null,
    val preferredMinThroughput: Int? = null,
    val targetWords: Int? = null,
    val researchQueryCap: Int? = null,
    val bypassLlmCache: Boolean? = null
)

/**
 * An experiment request: every variant composed [repeats] times against the source episode's
 * article set. [includeBaseline] adds a variant named `baseline` with no overrides.
 */
data class ExperimentRequest(
    val variants: List<ExperimentVariantRequest> = emptyList(),
    val repeats: Int = 1,
    val includeBaseline: Boolean = false
)

data class ExperimentStartedResponse(
    val experimentId: String,
    val sourceEpisodeId: Long,
    val episodeIds: List<Long>
)
