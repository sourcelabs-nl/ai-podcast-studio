package com.aisummarypodcast.store

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table

/**
 * The conditions one evaluation run was composed under, and the episode it produced.
 *
 * Written only for a run that asked to bypass the LLM cache, which is what an ablation does, so an
 * ordinary generation costs nothing here.
 */
@Table("evaluation_runs")
data class EvaluationRun(
    @Id val id: Long? = null,
    val episodeId: Long,
    val podcastId: String,
    val ranAt: String,
    val promptHash: String,
    val varietySelection: String,
    val composeModel: String,
    val temperature: Double,
    val cacheBypassed: Boolean,
    val cacheHit: Boolean,
    val toolsFiredJson: String,
    @Version val version: Long? = null
)
