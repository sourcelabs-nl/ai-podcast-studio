package com.aisummarypodcast.podcast

import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeMatchDetails
import com.aisummarypodcast.store.LlmStageTotals
import com.aisummarypodcast.store.Post
import com.aisummarypodcast.store.Source

enum class ResumePoint {
    FULL_PIPELINE,
    COMPOSE,
    POST_COMPOSE
}

data class GenerateBriefingResult(
    val episode: Episode?,
    val failed: Boolean = false,
    val errorMessage: String? = null
)

data class UpcomingContent(
    val articles: List<Article>,
    val unlinkedPosts: List<Post>,
    val sources: List<Source>,
    val totalPostCount: Long,
    val effectiveArticleCount: Long,
    /** Posts each article was aggregated from, keyed by article id. Absent means one. */
    val postCounts: Map<Long, Int> = emptyMap(),
    /** What has already been spent scoring the articles standing for the next episode. */
    val scoringSpend: ScoringSpend = ScoringSpend()
)

/**
 * What scoring a set of articles cost, in the shape an episode's score stage reports.
 *
 * Computed on request from the articles themselves rather than stored: the articles carry their own
 * tokens and reported cost, and the same [CostEstimator.aggregateStageCost] totals them here and
 * for an episode, so the figure shown before generation is comparable with the one shown after.
 */
data class ScoringSpend(
    val model: String? = null,
    val calls: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val costCents: Double = 0.0
)

/**
 * Score-stage facts for an episode's cost breakdown.
 *
 * [calls] counts every article scored as a candidate for the episode, not only those that reached
 * the script. [droppedCalls] and [droppedCostCents] are the part of that same figure accounted for
 * by the candidates that did not: a breakdown of the score row, never a row of its own, so adding
 * them to the episode's total would count the same money twice.
 */
data class ScoreStageSummary(
    val calls: Int,
    val droppedCalls: Int = 0,
    val droppedCostCents: Double = 0.0
)

/**
 * The stages of one episode that may be reported from the requests it recorded, keyed by
 * [com.aisummarypodcast.store.CostStage].
 *
 * A stage is here only when the log accounts for the whole of it. A stage that is absent reports its
 * persisted column, which is the answer for every episode generated before requests named an episode
 * and for a scoring log that cannot be completed. Empty is therefore the normal state of an old
 * episode, not a failure.
 */
data class EpisodeCostProjection(val stages: Map<String, LlmStageTotals> = emptyMap()) {
    companion object {
        val NONE = EpisodeCostProjection()
    }
}

/**
 * Everything the cost breakdown needs beyond the episode row itself.
 *
 * Carried as one object rather than as four parameters: they are all answers to "where do this
 * episode's figures come from", and they grew one at a time through a signature every caller has to
 * restate.
 */
data class EpisodeCostContext(
    val scoreStage: ScoreStageSummary = ScoreStageSummary(calls = 0),
    val costFor: StageCostFn = noopStageCostFn,
    val dedupGateModel: String? = null,
    /** The stages this episode may report from its recorded requests. Empty means: use the columns. */
    val projection: EpisodeCostProjection = EpisodeCostProjection.NONE
)

/**
 * One stage row's figures, whichever source they came from.
 *
 * A projected stage counts its requests, so a stage that ran twice across a failed attempt and a
 * retry reports both, and a run that paid and then rolled back is no longer lost.
 */
internal data class StageFigures(
    val calls: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val costCents: Double
)

data class LinkedArticlesResult(
    val articles: List<Article>,
    val topicLabels: List<String>,
    val articleTopics: Map<Long, String>,
    /** Article id to the dedup follow-up context stored on its link, so a recompose keeps continuity. */
    val followUpAnnotations: Map<Long, String> = emptyMap()
)

/** An episode returned by a search, paired with the topics and titles that matched the query. */
data class EpisodeSearchHit(
    val episode: Episode,
    val matches: EpisodeMatchDetails,
    /** The spoken text around the keyword, when the script (or recap, or notes) mentions it. */
    val scriptContext: String? = null
)
