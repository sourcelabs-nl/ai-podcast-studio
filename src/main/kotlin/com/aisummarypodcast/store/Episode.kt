package com.aisummarypodcast.store

import com.aisummarypodcast.llm.LlmCostSource
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table

/**
 * Episode entity. The aggregate LLM totals — [llmInputTokens], [llmOutputTokens],
 * [llmCostCents] — are derived sums of the five per-stage triples below
 * (score / dedup / dedup gate / compose / recap). They are written exclusively by
 * `EpisodeService.finalizeEpisode`; no other code path may update them
 * independently. Treat the five stage triples as the source of truth.
 */
@Table("episodes")
data class Episode(
    @Id val id: Long? = null,
    val podcastId: String,
    val generatedAt: String,
    /**
     * The article window this episode was generated for, as ISO-8601 instants: articles published
     * in `[windowStart, windowEnd)` are its candidates. Written once when the episode is created
     * and never recomputed, so a retry or a re-run selects from the same window the run started
     * with. Null for episodes generated before the window was recorded.
     */
    val windowStart: String? = null,
    val windowEnd: String? = null,
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
    /**
     * The dedup stage's already-covered gate, costed apart from the clustering call it relieves.
     * The gate runs a different model at different rates, so folding its charge into the dedup
     * amount left the two indistinguishable. [dedupGateCalls] is stored rather than derived: the
     * gate chunks its candidates and retries transient failures, so its request count does not
     * follow from its tokens the way the other single-call stages' does.
     */
    val dedupGateInputTokens: Int = 0,
    val dedupGateOutputTokens: Int = 0,
    val dedupGateCostCents: Int = 0,
    val dedupGateCalls: Int = 0,
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
    val dedupGateReportedCostCents: Double? = null,
    val composeReportedCostCents: Double? = null,
    val recapReportedCostCents: Double? = null,
    /**
     * The free-text focus of a focus episode, null for a regular episode. A focus episode is scored
     * against this text instead of the podcast's topic, always stops for review, and neither consumes
     * its articles nor advances the podcast's schedule.
     */
    val focus: String? = null,
    /** The most recent feedback a reviewer submitted to recompose a focus episode's script. */
    val reviewFeedback: String? = null,
    /** Why the current script was produced; see [EpisodePurpose]. */
    val purpose: EpisodePurpose = EpisodePurpose.LEGACY,
    /**
     * The resolved run configuration (models, reasoning effort, provider preferences, target words,
     * research cap, cache bypass) the current script was produced with, as JSON. Null for an episode
     * written before it was recorded.
     */
    val runConfigJson: String? = null,
    /**
     * The experiment an [EpisodePurpose.EXPERIMENT] episode belongs to: [experimentId] groups the
     * runs of one experiment request, [experimentVariant] names the variant this run composed under,
     * and [experimentSourceEpisodeId] is the episode whose article set it recomposes. Null for every
     * other episode.
     */
    val experimentId: String? = null,
    val experimentVariant: String? = null,
    val experimentSourceEpisodeId: Long? = null,
    @Version val version: Long? = null
) {
    /**
     * Aggregate LLM totals are derived sums of the five per-stage triples
     * (score / dedup / dedup gate / compose / recap). These are the single read path; callers must
     * NOT compute aggregates any other way. See the class KDoc for the invariant.
     */
    fun sumStageInputTokens(): Int =
        scoreInputTokens + dedupInputTokens + dedupGateInputTokens + composeInputTokens + recapInputTokens

    fun sumStageOutputTokens(): Int =
        scoreOutputTokens + dedupOutputTokens + dedupGateOutputTokens + composeOutputTokens + recapOutputTokens

    fun sumStageCostCents(): Int =
        scoreCostCents + dedupCostCents + dedupGateCostCents + composeCostCents + recapCostCents
}
