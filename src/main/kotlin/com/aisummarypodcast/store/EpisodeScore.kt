package com.aisummarypodcast.store

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table

/**
 * A judged attention score for one episode, at one scorer version.
 *
 * [scorerVersion] and [judgeModel] are part of the row's identity in practice: a judged score
 * drifts when the judge prompt or the judge model changes, so rows produced by different scorers
 * describe different quantities and are reported separately rather than averaged.
 *
 * [anchorsJson] keeps the judge's raw answer so a stored score can still be checked against the
 * script it describes, which is the whole point of asking for positions instead of a rating.
 *
 * [scriptHash] identifies the script text that was judged, so a score left behind by a rewrite is
 * recognised as stale. Null on rows written before the hash was recorded.
 */
@Table("episode_scores")
data class EpisodeScore(
    @Id val id: Long? = null,
    val episodeId: Long,
    val scorerVersion: Int,
    val judgeModel: String,
    val scoredAt: String,
    val overall: Double,
    val cliffhangerScore: Double,
    val humorScore: Double,
    val teaserScore: Double,
    val promises: Int,
    val deferredPromises: Int,
    val unpaidPromises: Int,
    val medianDeferralTurns: Int?,
    val humorBeats: Int,
    val humorSpeakerBalance: Double,
    val humorReactionRatio: Double,
    val teaserTopics: Int,
    val anchorsJson: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val costCents: Int?,
    val scriptHash: String? = null,
    @Version val version: Long? = null
)
