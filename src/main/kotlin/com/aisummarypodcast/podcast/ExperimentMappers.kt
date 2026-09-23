package com.aisummarypodcast.podcast

import com.aisummarypodcast.llm.PipelineStage
import com.aisummarypodcast.llm.RunOverrides

const val BASELINE_VARIANT = "baseline"

private val PROVIDER_SORTS = setOf("price", "throughput", "latency")

/**
 * The plan an experiment request describes. Throws [InvalidExperimentException] for a request no
 * run could execute: no variant, a repeat count below one, a blank or duplicate name, an unknown
 * stage key or provider sort, or a non-positive number.
 */
fun ExperimentRequest.toPlan(): ExperimentPlan {
    if (repeats < 1) throw InvalidExperimentException("repeats must be at least 1")
    val requested = variants.map { it.toVariant() }
    val all = if (includeBaseline) listOf(ExperimentVariant(BASELINE_VARIANT, RunOverrides())) + requested else requested
    if (all.isEmpty()) throw InvalidExperimentException("An experiment needs at least one variant, or includeBaseline")
    val duplicates = all.groupBy { it.name }.filterValues { it.size > 1 }.keys
    if (duplicates.isNotEmpty()) throw InvalidExperimentException("Variant names must be unique: ${duplicates.joinToString()}")
    return ExperimentPlan(all, repeats)
}

private fun ExperimentVariantRequest.toVariant(): ExperimentVariant {
    if (name.isBlank()) throw InvalidExperimentException("Every variant needs a name")
    if (providerSort != null && providerSort !in PROVIDER_SORTS) {
        throw InvalidExperimentException("providerSort must be one of ${PROVIDER_SORTS.joinToString()}")
    }
    for ((field, value) in listOf(
        "preferredMinThroughput" to preferredMinThroughput, "targetWords" to targetWords, "researchQueryCap" to researchQueryCap
    )) {
        if (value != null && value < 1) throw InvalidExperimentException("$field must be positive")
    }
    return ExperimentVariant(
        name.trim(),
        RunOverrides(
            models = models.orEmpty().mapKeys { stageOf(it.key) },
            reasoningEffort = reasoningEffort.orEmpty().mapKeys { stageOf(it.key) },
            providerSort = providerSort,
            preferredMinThroughput = preferredMinThroughput,
            targetWords = targetWords,
            researchQueryCap = researchQueryCap,
            bypassLlmCache = bypassLlmCache
        )
    )
}

private fun stageOf(key: String): PipelineStage =
    PipelineStage.entries.firstOrNull { it.value == key }
        ?: throw InvalidExperimentException(
            "Unknown stage '$key'; valid stages: ${PipelineStage.entries.joinToString { it.value }}"
        )

fun ExperimentStarted.toResponse() = ExperimentStartedResponse(experimentId, sourceEpisodeId, episodeIds)
