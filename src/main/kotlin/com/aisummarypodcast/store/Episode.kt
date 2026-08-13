package com.aisummarypodcast.store

import com.aisummarypodcast.llm.LlmCostSource
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table

/**
 * Episode entity. The aggregate LLM totals — [llmInputTokens], [llmOutputTokens],
 * [llmCostCents] — are derived sums of the four per-stage triples below
 * (score / dedup / compose / recap). They are written exclusively by
 * `EpisodeService.finalizeEpisode`; no other code path may update them
 * independently. Treat the four stage triples as the source of truth.
 */
@Table("episodes")
data class Episode(
    @Id val id: Long? = null,
    val podcastId: String,
    val generatedAt: String,
    val scriptText: String,
    val status: EpisodeStatus = EpisodeStatus.GENERATED,
    val publishApproved: Boolean = true,
    val audioFilePath: String? = null,
    val durationSeconds: Int? = null,
    val filterModel: String? = null,
    val dedupModel: String? = null,
    val composeModel: String? = null,
    val llmInputTokens: Int? = null,
    val llmOutputTokens: Int? = null,
    val llmCostCents: Int? = null,
    val ttsCharacters: Int? = null,
    val ttsCostCents: Int? = null,
    val ttsModel: String? = null,
    val ttsCalls: Int? = null,
    val recap: String? = null,
    val showNotes: String? = null,
    val errorMessage: String? = null,
    val pipelineStage: String? = null,
    val researchCalls: Int = 0,
    val researchCostCents: Int? = null,
    val scoreInputTokens: Int = 0,
    val scoreOutputTokens: Int = 0,
    val scoreCostCents: Int = 0,
    val dedupInputTokens: Int = 0,
    val dedupOutputTokens: Int = 0,
    val dedupCostCents: Int = 0,
    val composeInputTokens: Int = 0,
    val composeOutputTokens: Int = 0,
    val composeCostCents: Int = 0,
    val recapInputTokens: Int = 0,
    val recapOutputTokens: Int = 0,
    val recapCostCents: Int = 0,
    /**
     * Where the aggregate LLM cost came from, aggregated across the contributing stages.
     * Null for episodes generated before this column existed; those are presented as estimates.
     */
    val llmCostSource: LlmCostSource? = null,
    /**
     * Per-stage provider-reported cost in fractional cents, written only when a reported value
     * contributed to that stage (source `API`, `API_CACHED` or `MIXED`). Null means nothing was
     * reported for the stage, and the cost breakdown recomputes the row from its tokens and the
     * model's configured rate instead. These are display values; the `*CostCents` integers above
     * remain the persisted rounded costs that feed the aggregates and the budget gate.
     */
    val scoreReportedCostCents: Double? = null,
    val dedupReportedCostCents: Double? = null,
    val composeReportedCostCents: Double? = null,
    val recapReportedCostCents: Double? = null,
    @Version val version: Long? = null
) {
    /**
     * Aggregate LLM totals are derived sums of the four per-stage triples
     * (score / dedup / compose / recap). These are the single read path; callers must
     * NOT compute aggregates any other way. See the class KDoc for the invariant.
     */
    fun sumStageInputTokens(): Int =
        scoreInputTokens + dedupInputTokens + composeInputTokens + recapInputTokens

    fun sumStageOutputTokens(): Int =
        scoreOutputTokens + dedupOutputTokens + composeOutputTokens + recapOutputTokens

    fun sumStageCostCents(): Int =
        scoreCostCents + dedupCostCents + composeCostCents + recapCostCents
}
