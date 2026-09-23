package com.aisummarypodcast.podcast

import com.aisummarypodcast.llm.RunOverrides
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.Podcast

/** One configuration an experiment composes under; a variant with no overrides is the baseline. */
data class ExperimentVariant(val name: String, val overrides: RunOverrides)

/** What an experiment request asks for: every variant, each composed [repeats] times. */
data class ExperimentPlan(val variants: List<ExperimentVariant>, val repeats: Int) {
    val runCount: Int get() = variants.size * repeats
}

/** Which experiment, variant and source episode one experiment episode belongs to. */
data class ExperimentRunIdentity(val experimentId: String, val variant: String, val sourceEpisodeId: Long)

/** A started experiment: the placeholder episodes of its runs, in the order they will run. */
data class ExperimentStarted(val experimentId: String, val sourceEpisodeId: Long, val episodeIds: List<Long>)

/**
 * The comparable figures of one experiment run. The compose figures come from the run's recorded
 * compose requests: [composeDurationMs] is the wall time from the first compose request's start to
 * the last one's end (research tool rounds included), [reasoningTokens] sums what the provider
 * reported, and [servedProviders] lists every upstream provider that answered one. [totalCostCents]
 * covers everything the sandbox run is billed for (compose, research and recap), without the judge.
 * [wordCount] counts spoken words, leaving out speaker tags and bracketed cues.
 */
data class ExperimentRunMetrics(
    val episodeId: Long,
    val status: String,
    val errorMessage: String?,
    val judgeScore: Double?,
    val judgeModel: String?,
    val totalCostCents: Double?,
    val composeCostCents: Double?,
    val composeDurationMs: Long?,
    val composeCalls: Int,
    val reasoningTokens: Int?,
    val servedProviders: List<String>,
    val wordCount: Int?
)

/** Means over a variant's completed runs; each is null when no completed run reported it. */
data class ExperimentVariantMeans(
    val judgeScore: Double?,
    val totalCostCents: Double?,
    val composeCostCents: Double?,
    val composeDurationMs: Double?,
    val composeCalls: Double?,
    val reasoningTokens: Double?,
    val wordCount: Double?
)

data class ExperimentVariantComparison(
    val name: String,
    val completedRuns: Int,
    val failedRuns: Int,
    val pendingRuns: Int,
    val runs: List<ExperimentRunMetrics>,
    val mean: ExperimentVariantMeans
)

data class ExperimentComparison(
    val experimentId: String,
    val startedAt: String,
    val variants: List<ExperimentVariantComparison>
)

/** Every experiment run against one source episode, oldest experiment first. */
data class SourceEpisodeExperiments(val sourceEpisodeId: Long, val experiments: List<ExperimentComparison>)

/** A podcast and one of its episodes, as resolved for the requesting user. */
data class OwnedEpisode(val podcast: Podcast, val episode: Episode)
